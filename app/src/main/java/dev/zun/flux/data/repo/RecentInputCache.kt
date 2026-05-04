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
            outFile.parentFile?.mkdirs()
            val tempFile = java.io.File(outFile.parentFile, "${outFile.name}.tmp")
            val body = api.downloadInputFile(inputId)
            try {
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (tempFile.length() == 0L) error("Downloaded input was empty")
                if (!tempFile.renameTo(outFile)) {
                    tempFile.copyTo(outFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (t: Throwable) {
                tempFile.delete()
                throw t
            }
        }
        return Uri.fromFile(outFile)
    }

    fun uri(inputId: Int): Uri = Uri.fromFile(cacheFile(inputId))

    private fun cacheFile(inputId: Int): java.io.File = java.io.File(context.cacheDir, "input_recent_$inputId.jpg")
}
