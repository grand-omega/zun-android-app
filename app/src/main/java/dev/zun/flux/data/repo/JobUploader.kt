package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobSubmitRequest
import dev.zun.flux.data.api.NeedUploadResponse
import dev.zun.flux.data.api.ProgressRequestBody
import dev.zun.flux.data.local.JobDao
import dev.zun.flux.data.local.JobEntity
import dev.zun.flux.util.prepareImageForUpload
import dev.zun.flux.util.sha256Hex
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException

class JobUploader(
    private val context: Context,
    private val api: FluxApi,
    private val dao: JobDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Down-scale + EXIF-rotate the user's image into a private cache file
     * suitable for upload. Caller owns the returned [File] and must delete it.
     */
    suspend fun stageImage(uri: Uri): File = prepareImageForUpload(context, uri)

    /**
     * Submits an already-staged image. Use this when the upload is being
     * driven by something with its own retry policy (WorkManager). [file] is
     * not deleted; that's the caller's responsibility.
     */
    suspend fun submitStagedJob(
        file: File,
        selection: PromptSelection,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
        knownSourceInputId: Int? = null,
    ): JobCreatedResponse {
        val promptId = (selection as? PromptSelection.Saved)?.promptId
        val promptText = (selection as? PromptSelection.Custom)?.text
        require(promptText == null || !workflow.isNullOrBlank()) {
            "workflow is required for custom prompts"
        }

        val sha = sha256Hex(file)
        val response = submitWithRetry(file, sha, promptId, promptText, workflow, onUploadProgress)
        recordSourceLineage(response.job_id, sha, knownSourceInputId)
        return response
    }

    private suspend fun submitWithRetry(
        file: File,
        sha: String,
        promptId: Long?,
        promptText: String?,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse {
        val inputName = file.name
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < MAX_ATTEMPTS) {
            try {
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
                    throw HttpException(jsonResp)
                }

                val errText = jsonResp.errorBody()?.string().orEmpty()
                val needUpload = runCatching {
                    json.decodeFromString(NeedUploadResponse.serializer(), errText)
                }.getOrNull()
                if (needUpload?.need_upload != true && needUpload?.code != "need_upload") {
                    // A 409 we can't interpret is a server-contract problem, not a
                    // transient fault — fail fast instead of looping the retries.
                    error("Unexpected 409 body: $errText")
                }

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
                if (attempt < MAX_ATTEMPTS) {
                    delay(1000L * (1 shl (attempt - 1)))
                }
            } catch (e: HttpException) {
                // Only 429/5xx are worth retrying; auth and validation errors are
                // terminal no matter how many times we resend the same request.
                if (e.code() != 429 && e.code() < 500) throw e
                attempt++
                lastError = e
                if (attempt < MAX_ATTEMPTS) {
                    delay(1000L * (1 shl (attempt - 1)))
                }
            }
        }
        throw lastError ?: IOException("Failed to submit job after $MAX_ATTEMPTS attempts")
    }

    /**
     * Persists this job's source-image hash and assigns it to a lineage
     * group (a new one, or an existing one if [sha] matches a prior
     * successfully-completed job's source or result). Best-effort — a
     * failure here must never affect the job that was already submitted
     * successfully.
     *
     * When [knownSourceInputId] is set (the source came from re-picking an
     * existing recent photo, not a fresh gallery pick), the match is found
     * by that inputId instead of by [sha] and the match's own recorded hash
     * is reused verbatim: re-downloading and re-staging an image doesn't
     * reliably reproduce the exact same bytes as the original upload (JPEG
     * re-encoding isn't guaranteed byte-identical), so [sha] alone can't be
     * trusted to match here even though it's definitely the same photo.
     *
     * Checks for an existing row rather than blindly inserting: the first
     * real status poll (`RealJobRepository.getJob`) may already have
     * created this job's row by the time this runs, and a blind
     * `REPLACE` insert here would clobber that real data with a stale
     * placeholder.
     */
    private suspend fun recordSourceLineage(jobId: String, sha: String, knownSourceInputId: Int? = null) {
        runCatching {
            val match = knownSourceInputId?.let { dao.findDoneJobByInputId(it) } ?: dao.findDoneJobByHash(sha)
            val recordedSha = match?.sourceSha256 ?: sha
            val rootId = assignLineageRoot(jobId, match)
            if (dao.getJobById(jobId) != null) {
                dao.updateSourceLineage(jobId, recordedSha, rootId)
            } else {
                dao.insertJob(
                    JobEntity(
                        id = jobId,
                        status = "queued",
                        inputId = null,
                        promptId = null,
                        promptText = null,
                        workflow = null,
                        seed = null,
                        progress = null,
                        error = null,
                        createdAt = System.currentTimeMillis(),
                        startedAt = null,
                        completedAt = null,
                        durationSeconds = null,
                        width = null,
                        height = null,
                        sourceSha256 = recordedSha,
                        resultSha256 = null,
                        lineageRootId = rootId,
                    ),
                )
            }
        }.onFailure { Log.w(TAG, "Failed to record source lineage for $jobId", it) }
    }

    suspend fun submitJob(
        inputUri: Uri,
        selection: PromptSelection,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse {
        val file = stageImage(inputUri)
        try {
            return submitStagedJob(file, selection, workflow, onUploadProgress)
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val TAG = "JobUploader"
        val TEXT_PLAIN = "text/plain".toMediaType()
        const val MAX_ATTEMPTS = 3
    }
}
