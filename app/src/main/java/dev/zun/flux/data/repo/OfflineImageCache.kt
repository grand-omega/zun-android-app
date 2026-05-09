package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.zun.flux.Tuning
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Offline cache for server-served job images. Files are stored as plain JPEG
 * bytes inside the app's private storage, which Android already protects via
 * file-based encryption tied to the user lock. Coil loads them via its
 * built-in `file://` fetcher.
 */
class OfflineImageCache internal constructor(
    val rootDir: File,
    private val okHttpClientProvider: () -> OkHttpClient,
    private val maxBytes: Long = Tuning.OFFLINE_IMAGE_CACHE_MAX_BYTES,
) {
    /**
     * Resolves the OkHttpClient at call time so that cert-pin / interceptor
     * changes via FluxApp.rebuildOkHttp() take effect on subsequent prefetches
     * without rebuilding this cache (and dropping its in-flight state).
     */
    constructor(
        context: Context,
        okHttpClientProvider: () -> OkHttpClient,
        maxBytes: Long = Tuning.OFFLINE_IMAGE_CACHE_MAX_BYTES,
    ) : this(
        rootDir = File(context.filesDir, "offline_images"),
        okHttpClientProvider = okHttpClientProvider,
        maxBytes = maxBytes,
    )

    enum class Kind(val fileName: String) {
        Thumb("thumb.jpg"),
        Preview("preview.jpg"),
        Result("result.jpg"),
    }

    private val semaphore = Semaphore(Tuning.OFFLINE_PREFETCH_CONCURRENCY)

    fun availability(jobId: String): OfflineImageAvailability = OfflineImageAvailability(
        thumbCached = isCached(jobId, Kind.Thumb),
        previewCached = isCached(jobId, Kind.Preview),
        resultCached = isCached(jobId, Kind.Result),
    )

    fun stats(): OfflineCacheStats {
        val files = rootDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .toList()
        return OfflineCacheStats(
            bytes = files.sumOf { it.length() },
            fileCount = files.size,
        )
    }

    fun localUri(jobId: String, kind: Kind): Uri? {
        if (!isCached(jobId, kind)) return null
        return Uri.fromFile(cacheFile(jobId, kind))
    }

    suspend fun prefetch(jobId: String, kind: Kind, url: String) {
        val outFile = cacheFile(jobId, kind)
        if (isCached(jobId, kind)) {
            outFile.setLastModified(System.currentTimeMillis())
            return
        }

        semaphore.withPermit {
            if (isCached(jobId, kind)) return@withPermit
            outFile.parentFile?.mkdirs()
            val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")
            runCatching {
                val request = Request.Builder().url(url).build()
                okHttpClientProvider().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Failed to cache image: ${response.code}")
                    tempFile.outputStream().use { sink ->
                        response.body.byteStream().use { it.copyTo(sink) }
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
            }.onFailure { t ->
                Log.w(TAG, "prefetch failed for $jobId/${kind.fileName} <- $url", t)
                tempFile.delete()
            }
        }
    }

    fun delete(jobId: String) {
        jobDir(jobId).deleteRecursively()
    }

    fun clear() {
        rootDir.deleteRecursively()
    }

    private fun isCached(jobId: String, kind: Kind): Boolean {
        val file = cacheFile(jobId, kind)
        return file.exists() && file.length() > 0L
    }

    private fun prune() {
        val files = rootDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
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

    companion object {
        private const val TAG = "OfflineImageCache"
    }
}
