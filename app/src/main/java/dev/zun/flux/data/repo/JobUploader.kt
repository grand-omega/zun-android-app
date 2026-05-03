package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobSubmitRequest
import dev.zun.flux.data.api.NeedUploadResponse
import dev.zun.flux.data.api.ProgressRequestBody
import dev.zun.flux.util.prepareImageForUpload
import dev.zun.flux.util.sha256Hex
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class JobUploader(
    private val context: Context,
    private val api: FluxApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun submitJob(
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
                        throw IOException("Submit failed: HTTP ${jsonResp.code()}")
                    }

                    val errText = jsonResp.errorBody()?.string().orEmpty()
                    val needUpload = runCatching {
                        json.decodeFromString(NeedUploadResponse.serializer(), errText)
                    }.getOrNull()
                    if (needUpload?.need_upload != true && needUpload?.code != "need_upload") {
                        throw IOException("Unexpected 409 body: $errText")
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
                }
            }
            throw lastError ?: IOException("Failed to submit job after $MAX_ATTEMPTS attempts")
        } finally {
            file.delete()
        }
    }

    private companion object {
        val TEXT_PLAIN = "text/plain".toMediaType()
        const val MAX_ATTEMPTS = 3
    }
}
