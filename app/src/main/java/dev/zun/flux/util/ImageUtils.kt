package dev.zun.flux.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

/**
 * Preprocesses an image for upload by downscaling it to a maximum dimension
 * and compressing it as a JPEG.
 */
fun prepareImageForUpload(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2048,
    quality: Int = 90,
): File {
    val inputStream = context.contentResolver.openInputStream(uri) ?: error("Failed to open input stream")
    val originalBytes = inputStream.use { it.readBytes() }

    // 1. Get original dimensions and rotation
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)

    val exif = ExifInterface(originalBytes.inputStream())
    val rotation = getRotationDegrees(exif)

    // 2. Calculate scale
    val w = options.outWidth
    val h = options.outHeight
    val longestSide = max(w, h)

    val scale = if (longestSide > maxDimension) {
        maxDimension.toFloat() / longestSide
    } else {
        1f
    }

    // 3. Decode and scale
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
    }
    var bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
        ?: error("Failed to decode bitmap")

    val outputFile = File(context.cacheDir, "upload_preprocessed_${System.currentTimeMillis()}.jpg")
    try {
        // 4. Precise scaling and rotation
        if (scale < 1f || rotation != 0) {
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
                bitmap = scaledBitmap
            }
        }

        // 5. Save to temporary file
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    } catch (t: Throwable) {
        outputFile.delete()
        throw t
    } finally {
        bitmap.recycle()
    }

    return outputFile
}

/**
 * Copies the source URI's bytes into a private cache file in [Context.cacheDir] and
 * returns a `file://` URI to it. The filename is time-based, so each call produces a
 * distinct copy. Survives navigation and revoked PhotoPicker permissions for the
 * duration of the current selection.
 *
 * If [sourceUri] already points at a file under our own cacheDir, it is returned as-is.
 */
fun cacheInputLocally(context: Context, sourceUri: Uri): Uri {
    if (sourceUri.scheme == "file") {
        val path = sourceUri.path
        if (path != null && path.startsWith(context.cacheDir.absolutePath)) {
            return sourceUri
        }
    }
    val outFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(sourceUri).use { input ->
        requireNotNull(input) { "Failed to open input stream for $sourceUri" }
        outFile.outputStream().use { output -> input.copyTo(output) }
    }
    return Uri.fromFile(outFile)
}

/**
 * SHA-256 of the file's exact bytes, lowercase hex.
 * Must be computed on the same bytes that will be uploaded — the server re-hashes
 * and rejects submissions whose `input_sha256` doesn't match the multipart payload.
 */
fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun getRotationDegrees(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
