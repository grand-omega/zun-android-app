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
import dev.zun.flux.util.prepareImageForUpload
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class RealJobRepository(
    private val context: Context,
    private val api: FluxApi,
    private val settingsManager: SettingsManager,
) : JobRepository {
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

        return try {
            api.submitJob(imagePart, promptPart)
        } finally {
            file.delete()
        }
    }

    override suspend fun getJob(jobId: String): JobStatusDto = api.getJob(jobId)

    override suspend fun listJobs(
        status: String,
        limit: Int,
        before: Long?,
    ): List<JobSummaryDto> = api.listJobs(status, limit, before)

    override suspend fun deleteJob(jobId: String) = api.deleteJob(jobId)

    override fun inputModel(jobId: String): Any = "${settingsManager.serverUrl}/api/jobs/$jobId/input"

    override fun thumbModel(jobId: String): Any = "${settingsManager.serverUrl}/api/jobs/$jobId/thumb"

    override fun resultModel(jobId: String): Any = "${settingsManager.serverUrl}/api/jobs/$jobId/result"
}
