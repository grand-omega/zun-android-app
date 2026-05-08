package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import dev.zun.flux.Tuning
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class OfflineImageCache internal constructor(
    val rootDir: File,
    private val okHttpClientProvider: () -> OkHttpClient,
    private val maxBytes: Long = Tuning.OFFLINE_IMAGE_CACHE_MAX_BYTES,
    internal val vault: EncryptedFileVault? = null,
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
        vault = EncryptedFileVault.from(context),
    )

    init {
        if (vault != null) ensureEncryptedLayout()
    }

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
            .filter { it.isFile && it.name != ENCRYPTED_SENTINEL }
            .toList()
        return OfflineCacheStats(
            bytes = files.sumOf { it.length() },
            fileCount = files.size,
        )
    }

    fun localUri(jobId: String, kind: Kind): Uri? {
        if (!isCached(jobId, kind)) return null
        return if (vault != null) {
            // Custom scheme handled by EncryptedCacheFetcher in the Coil graph.
            Uri.Builder()
                .scheme(SCHEME)
                .authority(jobId.sanitizeFileName())
                .path("/${kind.fileName}")
                .build()
        } else {
            Uri.fromFile(cacheFile(jobId, kind))
        }
    }

    /** Returns decrypted bytes for the cached file, or null if absent. */
    fun readDecrypted(safeJobId: String, kind: Kind): ByteArray? {
        val file = File(File(rootDir, safeJobId), kind.fileName)
        if (!file.exists() || file.length() == 0L) return null
        val raw = file.readBytes()
        return vault?.decrypt(raw) ?: raw
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
                    val source = response.body.byteStream()
                    val rawSink = tempFile.outputStream()
                    val sink = vault?.encryptingStream(rawSink) ?: rawSink
                    sink.use { source.use { it.copyTo(sink) } }
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

    fun clear() {
        rootDir.deleteRecursively()
        if (vault != null) ensureEncryptedLayout()
    }

    private fun isCached(jobId: String, kind: Kind): Boolean {
        val file = cacheFile(jobId, kind)
        return file.exists() && file.length() > 0L
    }

    private fun prune() {
        val files = rootDir.walkTopDown()
            .filter { it.isFile && it.name != ENCRYPTED_SENTINEL }
            .sortedBy { it.lastModified() }
            .toList()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    /**
     * One-shot wipe of any pre-existing plaintext cache files. Idempotent —
     * subsequent runs see the sentinel and exit cheaply. Drops cached images
     * (rather than re-encrypting them) because the data is replenishable from
     * the server and a wipe avoids partial-migration edge cases.
     */
    private fun ensureEncryptedLayout() {
        val sentinel = File(rootDir, ENCRYPTED_SENTINEL)
        if (sentinel.exists()) return
        rootDir.deleteRecursively()
        rootDir.mkdirs()
        sentinel.createNewFile()
    }

    private fun cacheFile(jobId: String, kind: Kind): File = File(jobDir(jobId), kind.fileName)

    private fun jobDir(jobId: String): File = File(rootDir, jobId.sanitizeFileName())

    private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val SCHEME = "flux-cache"
        private const val ENCRYPTED_SENTINEL = ".encrypted"
    }
}
