package dev.zun.flux.data.repo

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class OfflineImageAvailability(
    val thumbCached: Boolean,
    val previewCached: Boolean,
    val resultCached: Boolean,
)

data class OfflineCacheStats(
    val bytes: Long,
    val fileCount: Int,
)

/**
 * Image-byte sources for Coil and the offline cache. Lets UI ask "what should I
 * load for this job/input?" without knowing whether the answer is a server URL,
 * an authenticated request, or a Keystore-decrypted local blob.
 */
interface ImageSourceRepository {
    /** Most-recent-first distinct input_ids derived from local job history. */
    fun recentInputIds(limit: Int): Flow<List<Int>>

    /**
     * Downloads an existing server-side input into private cache and returns a
     * `file://` Uri. Used to re-pick a recently-uploaded image without going
     * through the system photo picker. Filename is deterministic per input id,
     * so repeated calls return the same Uri (no duplicate downloads, and
     * caller-side equality checks naturally dedupe).
     */
    suspend fun downloadInputToCache(inputId: Int): Uri

    /**
     * The Uri that [downloadInputToCache] would return for [inputId], without
     * actually downloading. Lets the UI show "already selected" state without
     * triggering a fetch.
     */
    fun recentInputUri(inputId: Int): Uri

    /** Anything Coil can load. Null when [inputId] is null or we have no server URL. */
    fun inputModel(inputId: Int?): Any?

    /** Anything Coil can load. */
    fun thumbModel(jobId: String): Any?

    /** ~1280px JPEG, ideal for full-screen viewers. */
    fun previewModel(jobId: String): Any?

    /** Original PNG. Use only for save-to-gallery / share / explicit zoom. */
    fun resultModel(jobId: String): Any?

    /** Current private offline image cache state for a job. */
    fun offlineAvailability(jobId: String): OfflineImageAvailability

    /** Current private offline image cache size. */
    fun offlineCacheStats(): OfflineCacheStats

    /** Drops cached generated images. Server history remains untouched. */
    fun clearOfflineImageCache()
}
