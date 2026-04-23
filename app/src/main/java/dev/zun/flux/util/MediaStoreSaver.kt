package dev.zun.flux.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a copy of [source] into the phone's public Pictures/FluxEdit album.
 * Returns the MediaStore content Uri of the newly created entry, or throws.
 */
suspend fun saveToPictures(
    context: Context,
    source: Uri,
    displayName: String,
): Uri = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(source) ?: "image/jpeg"
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/FluxEdit",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    val dest =
        resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: error("MediaStore insert returned null")

    resolver.openOutputStream(dest).use { out ->
        requireNotNull(out) { "Could not open output stream for $dest" }
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Could not read source $source" }
            input.copyTo(out)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(dest, values, null, null)
    }
    dest
}
