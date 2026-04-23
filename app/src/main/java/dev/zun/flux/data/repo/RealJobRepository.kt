package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.ProgressRequestBody
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.local.AppDatabase
import dev.zun.flux.data.local.toEntity
import dev.zun.flux.data.local.toStatusDto
import dev.zun.flux.data.local.toSummaryDto
import dev.zun.flux.util.prepareImageForUpload
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class RealJobRepository(
    private val context: Context,
    private val api: FluxApi,
    private val settingsManager: SettingsManager,
) : JobRepository {
    private val dao = AppDatabase.getDatabase(context).jobDao()

    override suspend fun health(): HealthResponse = api.health()

    override suspend fun listPrompts(): List<PromptDto> = api.listPrompts()

    override suspend fun submitJob(
        inputUri: Uri,
        promptId: String,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse {
        val file = prepareImageForUpload(context, inputUri)
        val rawBody = file.asRequestBody("image/jpeg".toMediaType())
        val finalBody =
            if (onUploadProgress != null) {
                ProgressRequestBody(rawBody, onUploadProgress)
            } else {
                rawBody
            }

        val imagePart =
            MultipartBody.Part.createFormData(
                "image",
                file.name,
                finalBody,
            )
        val promptPart = promptId.toRequestBody("text/plain".toMediaType())

        var attempt = 0
        val maxAttempts = 3
        var lastError: Exception? = null

        try {
            while (attempt < maxAttempts) {
                try {
                    val response = api.submitJob(imagePart, promptPart)
                    return response
                } catch (e: IOException) {
                    attempt++
                    lastError = e
                    if (attempt < maxAttempts) {
                        delay(1000L * (1 shl (attempt - 1))) // 1s, 2s, 4s...
                    }
                }
            }
            throw lastError ?: IOException("Failed to submit job after $maxAttempts attempts")
        } finally {
            file.delete()
        }
    }

    override suspend fun getJob(jobId: String): JobStatusDto {
        val job = api.getJob(jobId)
        dao.insertJob(job.toEntity())
        return job
    }

    override suspend fun listJobs(
        status: String,
        limit: Int,
        before: Long?,
    ): List<JobSummaryDto> {
        val jobs = api.listJobs(status, limit, before)
        dao.insertJobs(jobs.map { it.toEntity() })
        return jobs
    }

    override suspend fun deleteJob(jobId: String) {
        api.deleteJob(jobId)
        dao.deleteJobById(jobId)
    }

    override fun getJobsFlow(): Flow<List<JobSummaryDto>> = dao.getAllJobs().map { entities ->
        entities.map { it.toSummaryDto() }
    }

    override fun getJobFlow(jobId: String): Flow<JobStatusDto?> = dao.getJobByIdFlow(jobId).map { it?.toStatusDto() }

    override suspend fun syncHistory() {
        try {
            val remoteJobs = api.listJobs(status = "done", limit = 100)
            dao.insertJobs(remoteJobs.map { it.toEntity() })
        } catch (_: Exception) {
            // Ignore background sync errors
        }
    }

    override fun inputModel(jobId: String): Any = "${settingsManager.serverUrl}/api/jobs/$jobId/input"

    override fun thumbModel(jobId: String): Any = "${settingsManager.serverUrl}/api/jobs/$jobId/thumb"

    override fun resultModel(jobId: String): Any = "${settingsManager.serverUrl}/api/jobs/$jobId/result"
}
