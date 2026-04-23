package dev.zun.flux.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

suspend fun shareImage(context: Context, source: Any) {
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
                URL(source).openStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
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
