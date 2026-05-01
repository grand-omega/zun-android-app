package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSubmitRequest
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.NeedUploadResponse
import dev.zun.flux.data.api.ProgressRequestBody
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.local.AppDatabase
import dev.zun.flux.data.local.PendingDeleteEntity
import dev.zun.flux.data.local.toEntity
import dev.zun.flux.data.local.toStatusDto
import dev.zun.flux.data.local.toSummaryDto
import dev.zun.flux.data.worker.DeleteSyncWorker
import dev.zun.flux.util.prepareImageForUpload
import dev.zun.flux.util.sha256Hex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException

class RealJobRepository(
    private val context: Context,
    private val api: FluxApi,
    private val settingsManager: SettingsManager,
) : JobRepository {
    private val dao = AppDatabase.getDatabase(context).jobDao()
    private val json = Json { ignoreUnknownKeys = true }

    private val _promptsState = MutableStateFlow<List<PromptDto>>(emptyList())
    override val promptsState: StateFlow<List<PromptDto>> = _promptsState.asStateFlow()

    override suspend fun health(): HealthResponse = api.health()

    override suspend fun listPrompts(): List<PromptDto> {
        val fetched = api.listPrompts().items
        _promptsState.value = fetched
        return fetched
    }

    override suspend fun createPrompt(label: String, text: String, workflow: String): PromptDto {
        val created = api.createPrompt(
            PromptDto(id = 0L, label = label, text = text, workflow = workflow),
        )
        runCatching { listPrompts() }
        return created
    }

    override suspend fun deletePrompt(promptId: Long) {
        api.deletePrompt(promptId)
        runCatching { listPrompts() }
    }

    override suspend fun submitJob(
        inputUri: Uri,
        promptId: Long?,
        promptText: String?,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse {
        require((promptId == null) xor (promptText == null)) {
            "Exactly one of promptId / promptText must be set"
        }
        require(promptText == null || !workflow.isNullOrBlank()) {
            "workflow is required when promptText is set"
        }

        val file = prepareImageForUpload(context, inputUri)
        try {
            val sha = sha256Hex(file)
            val inputName = file.name

            var attempt = 0
            val maxAttempts = 3
            var lastError: Exception? = null

            while (attempt < maxAttempts) {
                try {
                    // Step 1 — cheap JSON probe.
                    val jsonResp = api.submitJobJson(
                        JobSubmitRequest(
                            input_sha256 = sha,
                            input_name = inputName,
                            prompt_id = promptId,
                            prompt_text = promptText,
                            workflow = workflow,
                        ),
                    )
                    if (jsonResp.isSuccessful) {
                        val body = jsonResp.body() ?: throw IOException("Empty submit response")
                        onUploadProgress?.invoke(1f)
                        return body
                    }
                    if (jsonResp.code() != 409) {
                        throw IOException("Submit failed: HTTP ${jsonResp.code()}")
                    }
                    // 409 → server wants the bytes. Fall through to multipart.
                    val errText = jsonResp.errorBody()?.string().orEmpty()
                    val needUpload = runCatching {
                        json.decodeFromString(NeedUploadResponse.serializer(), errText)
                    }.getOrNull()
                    if (needUpload?.need_upload != true && needUpload?.code != "need_upload") {
                        throw IOException("Unexpected 409 body: $errText")
                    }

                    // Step 2 — multipart with bytes.
                    val rawBody = file.asRequestBody("image/jpeg".toMediaType())
                    val finalBody = if (onUploadProgress != null) {
                        ProgressRequestBody(rawBody, onUploadProgress)
                    } else {
                        rawBody
                    }
                    val imagePart = MultipartBody.Part.createFormData("image", inputName, finalBody)
                    return api.submitJobMultipart(
                        image = imagePart,
                        sha256 = sha.toRequestBody(TEXT_PLAIN),
                        inputName = inputName.toRequestBody(TEXT_PLAIN),
                        promptId = promptId?.toString()?.toRequestBody(TEXT_PLAIN),
                        promptText = promptText?.toRequestBody(TEXT_PLAIN),
                        workflow = workflow?.toRequestBody(TEXT_PLAIN),
                    )
                } catch (e: IOException) {
                    attempt++
                    lastError = e
                    if (attempt < maxAttempts) {
                        delay(1000L * (1 shl (attempt - 1))) // 1s, 2s, 4s
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
        status: String?,
        limit: Int,
        cursor: String?,
        inputId: Int?,
    ): JobListResponse {
        val resp = api.listJobs(status, limit, cursor, inputId)
        val pendingDeleteIds = dao.getPendingDeleteIds().toSet()
        dao.insertJobs(resp.items.filterNot { it.id in pendingDeleteIds }.map { it.toEntity() })
        return resp
    }

    override suspend fun deleteJob(jobId: String) {
        dao.insertPendingDelete(PendingDeleteEntity(jobId = jobId, createdAt = System.currentTimeMillis()))
        DeleteSyncWorker.enqueue(context)
    }

    override suspend fun restoreJob(jobId: String) {
        dao.deletePendingDelete(jobId)
        runCatching {
            api.restoreJob(jobId)
            // Repopulate local copy from server.
            getJob(jobId)
        }
    }

    override suspend fun cancelJob(jobId: String) {
        api.cancelJob(jobId)
        // Sync the new status into Room so any open Progress screen flips to Cancelled.
        runCatching { getJob(jobId) }
    }

    override fun getJobsFlow(): Flow<List<JobSummaryDto>> = dao.getVisibleJobs().map { entities ->
        entities.filter { it.status == "done" }.map { it.toSummaryDto() }
    }

    override fun getJobFlow(jobId: String): Flow<JobStatusDto?> = dao.getVisibleJobByIdFlow(jobId).map { it?.toStatusDto() }

    override fun recentInputIds(limit: Int): Flow<List<Int>> = dao.getVisibleJobs().map { entities ->
        entities.asSequence()
            .mapNotNull { it.inputId }
            .distinct()
            .take(limit)
            .toList()
    }

    override suspend fun downloadInputToCache(inputId: Int): Uri {
        val outFile = recentInputCacheFile(inputId)
        if (!outFile.exists() || outFile.length() == 0L) {
            val body = api.downloadInputFile(inputId)
            body.byteStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Uri.fromFile(outFile)
    }

    override fun recentInputUri(inputId: Int): Uri = Uri.fromFile(recentInputCacheFile(inputId))

    private fun recentInputCacheFile(inputId: Int): java.io.File = java.io.File(context.cacheDir, "input_recent_$inputId.jpg")

    override suspend fun syncHistory() {
        runCatching { syncPendingDeletes() }
        try {
            val resp = api.listJobs(status = "done", limit = 100)
            val pendingDeleteIds = dao.getPendingDeleteIds().toSet()
            dao.insertJobs(resp.items.filterNot { it.id in pendingDeleteIds }.map { it.toEntity() })
        } catch (_: Exception) {
            // Ignore background sync errors
        }
        // Opportunistically refresh prompt cache so label lookup works in the gallery.
        try {
            _promptsState.value = api.listPrompts().items
        } catch (_: Exception) {
            // Ignore
        }
    }

    override suspend fun syncPendingDeletes() {
        dao.getPendingDeletes().forEach { pendingDelete ->
            try {
                api.deleteJob(pendingDelete.jobId)
                dao.deleteJobById(pendingDelete.jobId)
                dao.deletePendingDelete(pendingDelete.jobId)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    dao.deleteJobById(pendingDelete.jobId)
                    dao.deletePendingDelete(pendingDelete.jobId)
                } else {
                    throw e
                }
            }
        }
    }

    override fun inputModel(inputId: Int?): Any? {
        if (inputId == null) return null
        return buildUrlOrNull("/api/v1/inputs/$inputId/file")
    }

    override fun thumbModel(jobId: String): Any? = buildUrlOrNull("/api/v1/jobs/$jobId/thumb")

    override fun previewModel(jobId: String): Any? = buildUrlOrNull("/api/v1/jobs/$jobId/preview")

    override fun resultModel(jobId: String): Any? = buildUrlOrNull("/api/v1/jobs/$jobId/result")

    private fun buildUrlOrNull(path: String): String? = settingsManager.serverUrl?.takeUnless { it.isBlank() }?.let { "$it$path" }

    private companion object {
        val TEXT_PLAIN = "text/plain".toMediaType()
    }
}
