package dev.zun.flux.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zun.flux.FluxApp
import kotlinx.coroutines.delay

class PollWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val repository = (applicationContext as FluxApp).repository

        while (true) {
            try {
                val job = repository.getJob(jobId)
                if (job.status == "done" || job.status == "failed") {
                    return Result.success()
                }
            } catch (_: Exception) {
                // Ignore transient network errors during background polling
            }
            delay(5000)
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
    }
}
