package dev.zun.flux.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zun.flux.R
import dev.zun.flux.util.compositeReveal
import dev.zun.flux.util.resolveToBitmap
import dev.zun.flux.util.saveToPictures
import dev.zun.flux.util.shareImages
import dev.zun.flux.util.snapshotMask
import dev.zun.flux.util.writeToTempFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fraction of the brush radius that stays fully opaque before the soft falloff begins. */
private const val SOFT_EDGE_START = 0.45f

/** Minimum brush radius — kept above a typical fingertip contact patch (~20dp) so the brush
 *  itself isn't smaller than the finger revealing it, which would make fine control hard to
 *  judge in the moment. */
private val MIN_BRUSH_RADIUS = 24.dp
private val MAX_BRUSH_RADIUS = 80.dp

/**
 * Given two points along a drag path, returns evenly-spaced points between them (inclusive of
 * [to]) at roughly [spacing] apart — so a fast drag stamps a continuous trail rather than leaving
 * gaps between widely-spaced [onDrag] callbacks. A degenerate call ([from] == [to], e.g. a tap)
 * returns a single point, never an empty list.
 */
internal fun interpolateStampPoints(from: Offset, to: Offset, spacing: Float): List<Offset> {
    val distance = (to - from).getDistance()
    if (distance <= 0f || spacing <= 0f) return listOf(to)
    val steps = (distance / spacing).toInt().coerceAtLeast(1)
    return (1..steps).map { step ->
        val t = step / steps.toFloat()
        Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    }
}

/** How long an export result message (success or failure) stays visible before auto-dismissing. */
private const val EXPORT_MESSAGE_DISPLAY_MS = 2_500L

private enum class ExportKind { Save, Share }

/**
 * Finger-erase before/after reveal (feature 010) — the original [beforeModel] covers the
 * transformed [afterModel] beneath it; dragging erases the covering layer wherever touched,
 * revealing the transformed image in a soft, freehand area. See spec.md for the full contract.
 *
 * [maskBitmap] is owned by the caller ([CompareOverlay]), not this composable — it must survive
 * this composable being removed from composition when the user switches to slider mode and back
 * (feature 010 research.md Decision 3). [onSizeKnown] reports this composable's measured size so
 * the caller can lazily create the mask once.
 */
@Composable
fun ScratchRevealCompare(
    beforeModel: Any?,
    afterModel: Any?,
    brushRadius: Dp,
    maskBitmap: ImageBitmap?,
    onSizeKnown: (IntSize) -> Unit,
    onBrushRadiusChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped on every stamp so the before-layer's drawWithContent (which reads it) is invalidated
    // and redraws — mutating maskBitmap's pixels in place doesn't itself trigger recomposition.
    var maskVersion by remember(maskBitmap) { mutableIntStateOf(0) }
    var lastPoint by remember(maskBitmap) { mutableStateOf<Offset?>(null) }
    var strokeRadiusPx by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val stampPaint = remember {
        Paint().apply {
            blendMode = BlendMode.DstOut
            isAntiAlias = true
        }
    }

    fun stampAt(center: Offset, radiusPx: Float) {
        val bitmap = maskBitmap ?: return
        if (radiusPx <= 0f) return
        // A solid core out to SOFT_EDGE_START of the radius, then a wide feathered falloff to
        // fully transparent at the edge — a noticeably softer, more gradual blend than a plain
        // center-to-edge gradient, while still erasing effectively (not just a faint haze).
        stampPaint.shader = RadialGradientShader(
            center = center,
            radius = radiusPx,
            colors = listOf(Color.Black, Color.Black, Color.Transparent),
            colorStops = listOf(0f, SOFT_EDGE_START, 1f),
        )
        Canvas(bitmap).drawCircle(center, radiusPx, stampPaint)
        maskVersion++
    }

    val savedMessage = stringResource(R.string.compare_scratch_saved)
    val saveFailedMessage = stringResource(R.string.compare_scratch_save_failed)
    val needsNetworkMessage = stringResource(R.string.compare_scratch_save_needs_network)

    fun export(kind: ExportKind) {
        val mask = maskBitmap ?: return
        if (isExporting || containerSize == IntSize.Zero) return
        // Snapshot the mask synchronously, before any suspend call — it's exactly what the user
        // has revealed right now, and must not keep changing underneath the async pipeline below
        // while they keep dragging (see snapshotMask's kdoc).
        val maskSnapshot = snapshotMask(mask)
        val size = containerSize
        isExporting = true
        scope.launch {
            try {
                val after = resolveToBitmap(context, afterModel)
                if (after == null) {
                    exportMessage = saveFailedMessage
                    return@launch
                }
                val before = resolveToBitmap(context, beforeModel)
                if (before == null) {
                    // The "before" source is never disk-cached by design (research.md Decision 2)
                    // — this is the expected offline failure mode, not a generic error.
                    exportMessage = needsNetworkMessage
                    return@launch
                }
                val composite = compositeReveal(after, before, maskSnapshot, size)
                val uri = writeToTempFile(context, composite)
                val displayName = "flux-reveal-${System.currentTimeMillis()}.jpg"
                when (kind) {
                    ExportKind.Save -> {
                        saveToPictures(context, uri, displayName)
                        exportMessage = savedMessage
                    }

                    ExportKind.Share -> shareImages(context, listOf(uri))
                }
            } catch (e: Exception) {
                exportMessage = saveFailedMessage
            } finally {
                isExporting = false
            }
        }
    }

    LaunchedEffect(exportMessage) {
        if (exportMessage == null) return@LaunchedEffect
        delay(EXPORT_MESSAGE_DISPLAY_MS)
        exportMessage = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                containerSize = it
                onSizeKnown(it)
            }
            .pointerInput(maskBitmap) {
                detectDragGestures(
                    onDragStart = { offset ->
                        strokeRadiusPx = with(density) { brushRadius.toPx() }
                        lastPoint = offset
                        stampAt(offset, strokeRadiusPx)
                    },
                    onDrag = { change, _ ->
                        val from = lastPoint ?: change.position
                        // Spacing tighter than the radius keeps the stroke reading as one
                        // continuous soft trail rather than separated dots (research.md Decision 2).
                        val points = interpolateStampPoints(from, change.position, strokeRadiusPx / 2f)
                        points.forEach { stampAt(it, strokeRadiusPx) }
                        lastPoint = change.position
                    },
                )
            }
            .pointerInput(maskBitmap) {
                detectTapGestures(
                    onPress = { offset ->
                        stampAt(offset, with(density) { brushRadius.toPx() })
                    },
                )
            },
    ) {
        AsyncImage(
            model = afterModel,
            contentDescription = stringResource(R.string.result_after),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (maskBitmap != null) {
            AsyncImage(
                model = beforeModel,
                contentDescription = stringResource(R.string.result_before),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        maskVersion // read to establish a redraw dependency on the mask's contents
                        drawContent()
                        drawImage(maskBitmap, blendMode = BlendMode.DstIn)
                    },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            IconButton(
                onClick = { export(ExportKind.Save) },
                enabled = !isExporting,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.compare_scratch_save),
                        tint = Color.White,
                    )
                }
            }
            IconButton(
                onClick = { export(ExportKind.Share) },
                enabled = !isExporting,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.compare_scratch_share),
                    tint = Color.White,
                )
            }
        }

        exportMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 68.dp, end = 12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = stringResource(R.string.compare_scratch_hint),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .width(220.dp),
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.compare_scratch_brush_size),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                val brushSizeLabel = stringResource(R.string.compare_scratch_brush_size)
                Slider(
                    value = brushRadius.value,
                    onValueChange = { onBrushRadiusChange(it.dp) },
                    valueRange = MIN_BRUSH_RADIUS.value..MAX_BRUSH_RADIUS.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = brushSizeLabel },
                )
            }
        }
    }
}
