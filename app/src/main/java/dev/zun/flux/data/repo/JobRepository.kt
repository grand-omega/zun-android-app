package dev.zun.flux.data.repo

import android.net.Uri
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionDiagnosis {
    data object Reachable : ConnectionDiagnosis

    data object NoServerUrl : ConnectionDiagnosis

    data class InvalidUrl(val message: String) : ConnectionDiagnosis

    data class ServiceNotListening(val message: String) : ConnectionDiagnosis

    data class HostUnreachable(val message: String) : ConnectionDiagnosis

    data class Unknown(val message: String) : ConnectionDiagnosis
}

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
 * Single seam between the UI and the backend. UI layers depend on this
 * interface; implementations are swapped in [dev.zun.flux.FluxApp].
 */
interface JobRepository {
    suspend fun health(): HealthResponse

    /** Best-effort TCP diagnosis for the currently active server URL. */
    suspend fun diagnoseConnection(): ConnectionDiagnosis

    /** List prompts from the server, updates [promptsState] as a side effect. */
    suspend fun listPrompts(): List<PromptDto>

    /** Last known prompts list (seeded by [listPrompts] / [syncHistory]). */
    val promptsState: StateFlow<List<PromptDto>>

    /** Creates a new server-side prompt and refreshes [promptsState]. Returns the new row. */
    suspend fun createPrompt(label: String, text: String, workflow: String): PromptDto

    /** Soft-deletes a server-side prompt and refreshes [promptsState]. */
    suspend fun deletePrompt(promptId: Long)

    /**
     * Submit a job. Exactly one of [promptId] / [promptText] must be non-null.
     * [workflow] is required when [promptText] is set.
     *
     * Uses the v2 JSON-probe-then-multipart flow: we try a cheap JSON submit first
     * with only the sha256 hash; the server returns 409 `need_upload` if it doesn't
     * have the bytes cached, at which point we re-submit as multipart.
     */
    suspend fun submitJob(
        inputUri: Uri,
        promptId: Long? = null,
        promptText: String? = null,
        workflow: String? = null,
        onUploadProgress: ((Float) -> Unit)? = null,
    ): JobCreatedResponse

    suspend fun getJob(jobId: String): JobStatusDto

    /**
     * Lists jobs with cursor pagination. Treat [cursor] as an opaque token.
     * Pass `null` to fetch the first page.
     */
    suspend fun listJobs(
        status: String? = "done",
        limit: Int = 50,
        cursor: String? = null,
        inputId: Int? = null,
    ): JobListResponse

    suspend fun deleteJob(jobId: String)

    /** Undoes a soft delete within the server's 30-day grace window. */
    suspend fun restoreJob(jobId: String)

    /** Cancels a queued/running job (server transitions it to status=cancelled). */
    suspend fun cancelJob(jobId: String)

    /** Local database flows */
    fun getJobsFlow(): Flow<List<JobSummaryDto>>

    fun getJobFlow(jobId: String): Flow<JobStatusDto?>

    /** Job ids hidden locally because the user deleted them but server sync may still be pending. */
    fun deletedJobIds(): Flow<Set<String>>

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

    suspend fun syncHistory()

    /** Flushes locally queued server deletes. Safe to call from background workers. */
    suspend fun syncPendingDeletes()

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
