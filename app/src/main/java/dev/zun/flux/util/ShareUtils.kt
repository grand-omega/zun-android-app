package dev.zun.flux.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun shareImage(context: Context, uri: Uri) {
    // If it's a file URI, we need to convert to content URI via FileProvider
    val contentUri = if (uri.scheme == "file") {
        val file = File(uri.path ?: return)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } else {
        uri
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Image"))
}
