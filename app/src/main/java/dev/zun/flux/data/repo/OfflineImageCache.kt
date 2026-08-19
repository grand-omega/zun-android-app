package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.zun.flux.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
    private val pruneEvery: Int = PRUNE_EVERY,
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

    /** One job's entry in the cache-cleanup preview (feature 009) — see [listCachedJobs]. */
    data class CachedJobSummary(val jobId: String, val cachedKinds: Set<Kind>, val bytes: Long)

    private val semaphore = Semaphore(Tuning.OFFLINE_PREFETCH_CONCURRENCY)

    /** Bumps whenever cache contents change; UI keys availability re-reads off it. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    /** Prefetches since the last prune; pruning walks the whole cache dir,
     *  so batch it rather than paying the walk per file. The budget can
     *  overshoot by at most [pruneEvery] files between prunes. */
    private val prefetchesSincePrune = AtomicInteger(0)

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

    /**
     * One entry per job with something cached, for the cache-cleanup preview (feature 009).
     * Job ids are server-issued UUIDs, which [sanitizeFileName] passes through unchanged, so the
     * per-job directory name recovered here matches the original [jobId].
     */
    fun listCachedJobs(): List<CachedJobSummary> {
        val dirs = rootDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val kinds = Kind.entries.filter { kind ->
                File(dir, kind.fileName).let { it.isFile && it.length() > 0L }
            }.toSet()
            if (kinds.isEmpty()) return@mapNotNull null
            val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            CachedJobSummary(jobId = dir.name, cachedKinds = kinds, bytes = bytes)
        }
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
                    if (prefetchesSincePrune.incrementAndGet() >= pruneEvery) {
                        prefetchesSincePrune.set(0)
                        prune()
                    }
                    _version.value++
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
        _version.value++
    }

    fun clear() {
        rootDir.deleteRecursively()
        _version.value++
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
        private const val PRUNE_EVERY = 16
    }
}
