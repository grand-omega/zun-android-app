package dev.zun.flux.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.geometry.Rect as ComposeRect

/** Output canvas cap — matches [prepareImageForUpload]'s existing `maxDimension` convention, so an
 *  uncapped-resolution result PNG can't produce an unbounded in-memory composite. */
private const val MAX_COMPOSITE_DIMENSION = 2048

/**
 * Resolves a Coil model (the same `Any?` type `AsyncImage`'s own `model` param already accepts)
 * to a decoded [Bitmap], or `null` if it can't be resolved (offline + not cached, decode failure,
 * etc.). Unlike [saveToPictures]/[shareImages], which only byte-copy an already-materialized
 * source, this actually decodes through Coil's own `ImageLoader` — the same mechanism
 * `PhotoViewerScreen`'s full-res pre-warm already uses, just also extracting the resulting bitmap
 * rather than only checking success. Requests `allowHardware(false)` — Coil defaults to
 * `HARDWARE`-config bitmaps, which [compositeReveal]'s software `Canvas` cannot draw (throws
 * `Software rendering doesn't support hardware bitmaps`).
 */
suspend fun resolveToBitmap(context: Context, model: Any?): Bitmap? = withContext(Dispatchers.IO) {
    if (model == null) return@withContext null
    val request = ImageRequest.Builder(context).data(model).allowHardware(false).build()
    val result = SingletonImageLoader.get(context).execute(request)
    ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap
}

/**
 * Copies [mask]'s current pixels into a new, independent [ImageBitmap]. Callers MUST invoke this
 * synchronously, before any `suspend` call — [mask] is a live object the user keeps drawing into
 * while dragging, and a save/share must reflect its state at the instant it was triggered, not
 * whatever it looks like whenever the rest of the (asynchronous) export pipeline gets around to
 * reading it.
 */
fun snapshotMask(mask: ImageBitmap): ImageBitmap {
    val source = mask.asAndroidBitmap()
    return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false).asImageBitmap()
}

/**
 * The rect, in container coordinates, that [imageIntrinsicSize] occupies when rendered into
 * [containerSize] via `ContentScale.Fit` (uniform scale, centered — matching how both
 * `ScratchRevealCompare`'s `AsyncImage`s are actually displayed).
 */
internal fun fitRectInContainer(containerSize: IntSize, imageIntrinsicSize: IntSize): ComposeRect {
    if (containerSize.width <= 0 || containerSize.height <= 0 ||
        imageIntrinsicSize.width <= 0 || imageIntrinsicSize.height <= 0
    ) {
        return ComposeRect(0f, 0f, containerSize.width.toFloat(), containerSize.height.toFloat())
    }
    val scale = min(
        containerSize.width.toFloat() / imageIntrinsicSize.width,
        containerSize.height.toFloat() / imageIntrinsicSize.height,
    )
    val scaledWidth = imageIntrinsicSize.width * scale
    val scaledHeight = imageIntrinsicSize.height * scale
    val left = (containerSize.width - scaledWidth) / 2f
    val top = (containerSize.height - scaledHeight) / 2f
    return ComposeRect(left, top, left + scaledWidth, top + scaledHeight)
}

/**
 * Maps [rectInContainer] into [imageIntrinsicSize]'s own pixel space, given that image is
 * displayed within [containerSize] via `ContentScale.Fit` — the inverse of [fitRectInContainer].
 * A point in a letterboxed area of the container maps outside the image's own `0..width`/
 * `0..height` bounds (negative, or beyond the far edge) rather than being silently clamped —
 * callers that need a valid crop rect are responsible for clamping themselves (see
 * [compositeReveal]).
 */
internal fun mapRectToSource(containerSize: IntSize, imageIntrinsicSize: IntSize, rectInContainer: ComposeRect): ComposeRect {
    val fit = fitRectInContainer(containerSize, imageIntrinsicSize)
    if (fit.width <= 0f || fit.height <= 0f) return rectInContainer
    val scaleX = imageIntrinsicSize.width / fit.width
    val scaleY = imageIntrinsicSize.height / fit.height
    return ComposeRect(
        left = (rectInContainer.left - fit.left) * scaleX,
        top = (rectInContainer.top - fit.top) * scaleY,
        right = (rectInContainer.right - fit.left) * scaleX,
        bottom = (rectInContainer.bottom - fit.top) * scaleY,
    )
}

private fun ComposeRect.toClampedAndroidRect(boundsWidth: Int, boundsHeight: Int): Rect = Rect(
    left.toInt().coerceIn(0, boundsWidth),
    top.toInt().coerceIn(0, boundsHeight),
    right.toInt().coerceIn(0, boundsWidth),
    bottom.toInt().coerceIn(0, boundsHeight),
)

/**
 * Produces the flattened before/after composite: the output canvas is [after]'s own resolution
 * (capped at [MAX_COMPOSITE_DIMENSION]), with [before] masked by [mask] on top — both remapped
 * from their shared on-screen container into [after]'s own space via [fitRectInContainer]/
 * [mapRectToSource], so the result is correct even if [before]/[after] don't share an aspect
 * ratio (an edit that changed output dimensions), not just in the common same-ratio case.
 * [mask] MUST already be a [snapshotMask] result, never the live mask a drag is still writing to.
 */
fun compositeReveal(after: Bitmap, before: Bitmap, mask: ImageBitmap, containerSize: IntSize): Bitmap {
    val scaleDown = min(1f, MAX_COMPOSITE_DIMENSION.toFloat() / max(after.width, after.height).toFloat())
    val outputWidth = max(1, (after.width * scaleDown).toInt())
    val outputHeight = max(1, (after.height * scaleDown).toInt())
    val dst = RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat())

    val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(after, null, dst, null)

    // after's own full bounds, expressed in container space — this is the region of both the
    // mask and `before` that corresponds to what's actually on screen for `after`.
    val afterFit = fitRectInContainer(containerSize, IntSize(after.width, after.height))
    val beforeSrc = mapRectToSource(containerSize, IntSize(before.width, before.height), afterFit)
        .toClampedAndroidRect(before.width, before.height)
    val maskAndroidBitmap = mask.asAndroidBitmap()
    val maskSrc = afterFit.toClampedAndroidRect(maskAndroidBitmap.width, maskAndroidBitmap.height)

    if (beforeSrc.width() > 0 && beforeSrc.height() > 0 && maskSrc.width() > 0 && maskSrc.height() > 0) {
        val beforeLayer = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        Canvas(beforeLayer).drawBitmap(before, beforeSrc, dst, null)

        val maskLayer = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        Canvas(maskLayer).drawBitmap(maskAndroidBitmap, maskSrc, dst, null)

        val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        Canvas(beforeLayer).drawBitmap(maskLayer, 0f, 0f, maskPaint)

        canvas.drawBitmap(beforeLayer, 0f, 0f, null)
    }

    return output
}

/**
 * Writes [bitmap] as a JPEG into the app's cache directory and returns a plain `file://` [Uri].
 * JPEG (not PNG) specifically because [saveToPictures] hardcodes `MIME_TYPE = "image/jpeg"`
 * regardless of the source's actual format — writing a PNG here would silently mismatch that
 * declared MIME type. Deliberately handed to the *existing, unmodified* [saveToPictures]/
 * [shareImages] via their existing `is Uri ->` branch — no changes needed to either utility.
 */
suspend fun writeToTempFile(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "reveal-export-${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
    Uri.fromFile(file)
}
