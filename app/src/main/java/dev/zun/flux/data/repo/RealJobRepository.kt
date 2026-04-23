package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import dev.zun.flux.BuildConfig
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class RealJobRepository(
    private val context: Context,
    private val api: FluxApi,
) : JobRepository {

    override suspend fun health(): HealthResponse = api.health()

    override suspend fun listPrompts(): List<PromptDto> = api.listPrompts()

    override suspend fun submitJob(inputUri: Uri, promptId: String): JobCreatedResponse {
        val file = uriToFile(inputUri)
        val imagePart = MultipartBody.Part.createFormData(
            "image",
            file.name,
            file.asRequestBody("image/jpeg".toMediaType())
        )
        val promptPart = promptId.toRequestBody("text/plain".toMediaType())

        return api.submitJob(imagePart, promptPart)
    }

    override suspend fun getJob(jobId: String): JobStatusDto = api.getJob(jobId)

    override suspend fun listJobs(status: String, limit: Int, before: Long?): List<JobSummaryDto> =
        api.listJobs(status, limit, before)

    override suspend fun deleteJob(jobId: String) = api.deleteJob(jobId)

    override fun inputModel(jobId: String): Any =
        "${BuildConfig.SERVER_URL}/api/jobs/$jobId/input"

    override fun thumbModel(jobId: String): Any =
        "${BuildConfig.SERVER_URL}/api/jobs/$jobId/thumb"

    override fun resultModel(jobId: String): Any =
        "${BuildConfig.SERVER_URL}/api/jobs/$jobId/result"

    /**
     * Crude URI to File helper. In a real app we might use ImageUtils to downscale first.
     */
    private fun uriToFile(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri) ?: error("Failed to open input stream")
        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return file
    }
}
