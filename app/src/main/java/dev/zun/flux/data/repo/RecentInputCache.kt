package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import dev.zun.flux.data.api.FluxApi

class RecentInputCache(
    private val context: Context,
    private val api: FluxApi,
) {
    suspend fun downloadInputToCache(inputId: Int): Uri {
        val outFile = cacheFile(inputId)
        if (!outFile.exists() || outFile.length() == 0L) {
            val body = api.downloadInputFile(inputId)
            body.byteStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Uri.fromFile(outFile)
    }

    fun uri(inputId: Int): Uri = Uri.fromFile(cacheFile(inputId))

    private fun cacheFile(inputId: Int): java.io.File = java.io.File(context.cacheDir, "input_recent_$inputId.jpg")
}
