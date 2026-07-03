package dev.zun.flux.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.zun.flux.FluxApp
import dev.zun.flux.Tuning
import dev.zun.flux.data.repo.PromptSelection
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/**
 * Submits a previously-staged image to the server. The staged file is created
 * by [dev.zun.flux.data.repo.JobRepository.enqueueJobUpload] before the work
 * is enqueued and is deleted by this worker after submission (success or
 * terminal failure). Transient network errors trigger a WorkManager retry up
 * to [Tuning.MAX_UPLOAD_RETRIES] times; after that the staged file is removed so
 * persistent failures don't leak cache space.
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
        val selection = when {
            promptId != null -> PromptSelection.Saved(promptId)
            promptText != null -> PromptSelection.Custom(promptText)
            else -> return Result.failure(workDataOf(KEY_ERROR to "Missing prompt"))
        }

        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(workDataOf(KEY_ERROR to "Staged file missing"))
        }

        val uploads = (applicationContext as FluxApp).repositories.uploads
        return try {
            val resp = uploads.submitStagedJob(
                filePath = filePath,
                selection = selection,
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
            if (runAttemptCount < Tuning.MAX_UPLOAD_RETRIES) {
                Result.retry()
            } else {
                file.delete()
                Result.failure(
                    workDataOf(
                        KEY_ERROR to "Upload failed after ${Tuning.MAX_UPLOAD_RETRIES + 1} attempts",
                    ),
                )
            }
        } catch (e: HttpException) {
            // 429/5xx are worth another WorkManager attempt; other HTTP errors
            // (bad token, rejected input) won't fix themselves by resending.
            if ((e.code() == 429 || e.code() >= 500) && runAttemptCount < Tuning.MAX_UPLOAD_RETRIES) {
                Result.retry()
            } else {
                file.delete()
                Result.failure(
                    workDataOf(KEY_ERROR to "Server rejected upload (HTTP ${e.code()})"),
                )
            }
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

        /** Tag applied to every job upload, so callers can query queue depth via
         *  [WorkManager.getWorkInfosByTagFlow]. */
        const val TAG = "job_upload"
    }
}
