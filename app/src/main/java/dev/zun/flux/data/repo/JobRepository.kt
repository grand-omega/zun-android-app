package dev.zun.flux.data.repo

import androidx.paging.PagingData
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import kotlinx.coroutines.flow.Flow

/** Aggregate counts for the gallery tag-filter dropdown. */
data class JobTagStats(
    val totalCount: Int,
    val customCount: Int,
    val perPromptCounts: Map<Long, Int>,
)

/**
 * Job CRUD, paged history, and sync. UI layers should depend on this for
 * job-lifecycle operations only — health, prompts, uploads, and image-byte
 * resolution live on their own narrower interfaces.
 */
interface JobRepository {
    /**
     * Fetch a job's status. [waitSeconds] > 0 long-polls: the server holds
     * the response (≤30s) until status/progress changes, so callers can loop
     * on this instead of sleeping between short GETs.
     */
    suspend fun getJob(jobId: String, waitSeconds: Int? = null): JobStatusDto

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

    /**
     * Paged stream of done jobs, optionally narrowed by [promptId] or
     * [customOnly]. Pass `(null, false)` for no filter. [newestFirst]
     * controls sort direction (createdAt with id tiebreak).
     */
    fun pagedJobs(promptId: Long?, customOnly: Boolean, newestFirst: Boolean): Flow<PagingData<JobSummaryDto>>

    /** Aggregate counts used by the gallery tag-filter dropdown. */
    fun jobTagStats(): Flow<JobTagStats>

    fun getJobFlow(jobId: String): Flow<JobStatusDto?>

    /**
     * Ids of jobs not yet in a terminal state (done/failed/cancelled) and not
     * locally deleted. Backed by local Room state — no network call, so it
     * reflects whatever was last synced even while offline.
     */
    fun activeJobIds(): Flow<List<String>>

    /** Job ids hidden locally because the user deleted them but server sync may still be pending. */
    fun deletedJobIds(): Flow<Set<String>>

    suspend fun syncHistory()

    /** Flushes locally queued server deletes. Safe to call from background workers. */
    suspend fun syncPendingDeletes()
}
