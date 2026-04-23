package dev.zun.flux.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.zun.flux.FluxApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.net.URL

suspend fun shareImage(context: Context, source: Any) {
    val okHttpClient = (context.applicationContext as? FluxApp)?.okHttpClient

    val contentUri = when (source) {
        is Uri -> {
            if (source.scheme == "file") {
                val file = File(source.path ?: return)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                source
            }
        }
        is String -> {
            // Download remote image to cache first to share it
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.jpg")
                if (okHttpClient != null) {
                    val request = Request.Builder().url(source).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("Failed to download image: ${response.code}")
                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        } ?: error("Empty response body")
                    }
                } else {
                    URL(source).openStream().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
        else -> return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Image"))
}
