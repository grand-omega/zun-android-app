package dev.zun.flux.data.repo

import android.net.Uri
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import kotlinx.coroutines.flow.Flow

/**
 * Single seam between the UI and the backend. UI layers depend on this
 * interface; implementations are swapped in [dev.zun.flux.FluxApp].
 */
interface JobRepository {
    suspend fun health(): HealthResponse

    suspend fun listPrompts(): List<dev.zun.flux.data.api.PromptDto>

    suspend fun submitJob(
        inputUri: Uri,
        promptId: String,
        customPrompt: String? = null,
        onUploadProgress: ((Float) -> Unit)? = null,
    ): JobCreatedResponse

    suspend fun getJob(jobId: String): JobStatusDto

    /**
     * Lists completed jobs.
     * @param before Unix timestamp in SECONDS.
     */
    suspend fun listJobs(
        status: String = "done",
        limit: Int = 30,
        before: Long? = null,
    ): List<JobSummaryDto>

    suspend fun deleteJob(jobId: String)

    /** Local database flows */
    fun getJobsFlow(): Flow<List<JobSummaryDto>>

    fun getJobFlow(jobId: String): Flow<JobStatusDto?>

    suspend fun syncHistory()

    /** Anything Coil can load. */
    fun inputModel(jobId: String): Any?

    /** Anything Coil can load. */
    fun thumbModel(jobId: String): Any?

    /** Anything Coil can load. Fake echoes the input URI; real returns an HTTP URL. */
    fun resultModel(jobId: String): Any?
}
