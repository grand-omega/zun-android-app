package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val MAX_OFFLINE_IMAGE_CACHE_BYTES = 1_000L * 1024L * 1024L
private const val PREFETCH_CONCURRENCY = 3

class OfflineImageCache internal constructor(
    private val rootDir: File,
    private val okHttpClient: OkHttpClient,
    private val maxBytes: Long = MAX_OFFLINE_IMAGE_CACHE_BYTES,
) {
    constructor(
        context: Context,
        okHttpClient: OkHttpClient,
        maxBytes: Long = MAX_OFFLINE_IMAGE_CACHE_BYTES,
    ) : this(File(context.filesDir, "offline_images"), okHttpClient, maxBytes)

    enum class Kind(val fileName: String) {
        Thumb("thumb.jpg"),
        Preview("preview.jpg"),
    }

    private val semaphore = Semaphore(PREFETCH_CONCURRENCY)

    fun localUri(jobId: String, kind: Kind): Uri? {
        val file = cacheFile(jobId, kind)
        return if (file.exists() && file.length() > 0L) Uri.fromFile(file) else null
    }

    suspend fun prefetch(jobId: String, kind: Kind, url: String) {
        val outFile = cacheFile(jobId, kind)
        if (outFile.exists() && outFile.length() > 0L) {
            outFile.setLastModified(System.currentTimeMillis())
            return
        }

        semaphore.withPermit {
            if (outFile.exists() && outFile.length() > 0L) return@withPermit
            outFile.parentFile?.mkdirs()
            val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")
            runCatching {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Failed to cache image: ${response.code}")
                    response.body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (tempFile.length() > 0L) {
                    if (!tempFile.renameTo(outFile)) {
                        tempFile.copyTo(outFile, overwrite = true)
                        tempFile.delete()
                    }
                    outFile.setLastModified(System.currentTimeMillis())
                    prune()
                } else {
                    tempFile.delete()
                }
            }.onFailure {
                tempFile.delete()
            }
        }
    }

    fun delete(jobId: String) {
        jobDir(jobId).deleteRecursively()
    }

    private fun prune() {
        val files = rootDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun cacheFile(jobId: String, kind: Kind): File = File(jobDir(jobId), kind.fileName)

    private fun jobDir(jobId: String): File = File(rootDir, jobId.sanitizeFileName())

    private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
