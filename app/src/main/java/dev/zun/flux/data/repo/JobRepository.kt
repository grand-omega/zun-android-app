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

/**
 * Single seam between the UI and the backend. UI layers depend on this
 * interface; implementations are swapped in [dev.zun.flux.FluxApp].
 */
interface JobRepository {
    suspend fun health(): HealthResponse

    /** List prompts from the server, updates [promptsState] as a side effect. */
    suspend fun listPrompts(): List<PromptDto>

    /** Last known prompts list (seeded by [listPrompts] / [syncHistory]). */
    val promptsState: StateFlow<List<PromptDto>>

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

    /** Local database flows */
    fun getJobsFlow(): Flow<List<JobSummaryDto>>

    fun getJobFlow(jobId: String): Flow<JobStatusDto?>

    suspend fun syncHistory()

    /** Anything Coil can load. Null when [inputId] is null or we have no server URL. */
    fun inputModel(inputId: Int?): Any?

    /** Anything Coil can load. */
    fun thumbModel(jobId: String): Any?

    /** Anything Coil can load. Fake echoes the input URI; real returns an HTTP URL. */
    fun resultModel(jobId: String): Any?
}
