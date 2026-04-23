package dev.zun.flux.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Preprocesses an image for upload by downscaling it to a maximum dimension
 * and compressing it as a JPEG.
 */
fun prepareImageForUpload(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2048,
    quality: Int = 90
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
    val outputFile = File(context.cacheDir, "upload_preprocessed_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outputFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    bitmap.recycle()
    
    return outputFile
}

private fun getRotationDegrees(exif: ExifInterface): Int {
    return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
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
