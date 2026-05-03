package dev.zun.flux.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.zun.flux.FluxApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URL

/**
 * Writes a copy of [source] into the phone's public Pictures/FluxEdit album.
 * Returns the MediaStore content Uri of the newly created entry, or throws.
 * Supports both local Uri and remote URL strings.
 */
suspend fun saveToPictures(
    context: Context,
    source: Any,
    displayName: String,
): Uri = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val okHttpClient = (context.applicationContext as? FluxApp)?.okHttpClient

    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
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
        when (source) {
            is Uri -> {
                resolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Could not read source $source" }
                    input.copyTo(out)
                }
            }

            is String -> {
                if (okHttpClient != null) {
                    val request = Request.Builder().url(source).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("Failed to download image: ${response.code}")
                        response.body.byteStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                } else {
                    URL(source).openStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }

            else -> error("Unsupported source type: ${source::class.java}")
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(dest, values, null, null)
    }
    dest
}
