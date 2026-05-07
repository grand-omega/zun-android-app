package dev.zun.flux.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.zun.flux.FluxApp
import java.io.File
import java.io.IOException

/**
 * Submits a previously-staged image to the server. The staged file is created
 * by [dev.zun.flux.data.repo.JobRepository.enqueueJobUpload] before the work
 * is enqueued and is deleted by this worker after submission (success or
 * failure). Transient network errors trigger a WorkManager retry.
 */
class JobUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing file path"))
        val promptId = inputData.getLong(KEY_PROMPT_ID, -1L).takeIf { it != -1L }
        val promptText = inputData.getString(KEY_PROMPT_TEXT)
        val workflow = inputData.getString(KEY_WORKFLOW)

        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(workDataOf(KEY_ERROR to "Staged file missing"))
        }

        val repository = (applicationContext as FluxApp).repository
        return try {
            val resp = repository.submitStagedJob(
                filePath = filePath,
                promptId = promptId,
                promptText = promptText,
                workflow = workflow,
                onUploadProgress = { fraction ->
                    setProgressAsync(workDataOf(KEY_PROGRESS to fraction))
                },
            )
            file.delete()
            Result.success(
                workDataOf(
                    KEY_JOB_ID to resp.job_id,
                    KEY_INPUT_ID to (resp.input_id ?: -1),
                ),
            )
        } catch (_: IOException) {
            // Retain the staged file so WorkManager can retry it.
            Result.retry()
        } catch (e: Exception) {
            file.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Upload failed")))
        }
    }

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_PROMPT_ID = "prompt_id"
        const val KEY_PROMPT_TEXT = "prompt_text"
        const val KEY_WORKFLOW = "workflow"
        const val KEY_PROGRESS = "progress"
        const val KEY_JOB_ID = "job_id"
        const val KEY_INPUT_ID = "input_id"
        const val KEY_ERROR = "error"
    }
}
