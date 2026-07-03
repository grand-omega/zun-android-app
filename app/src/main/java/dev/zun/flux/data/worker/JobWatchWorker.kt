package dev.zun.flux.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.zun.flux.FluxApp
import dev.zun.flux.util.JobNotifications
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/**
 * Watches a submitted job to a terminal state via the server's long-poll
 * (`GET /jobs/{id}?wait=25`) and posts a completion notification if the app
 * is backgrounded when it finishes. Foreground viewers already see the
 * progress screen update, so the notification helper suppresses itself.
 */
class JobWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val jobs = (applicationContext as FluxApp).repositories.jobs

        // Stay well under WorkManager's ~10 min execution cap; a still-running
        // job re-enqueues via retry so long generations are still caught.
        val deadline = System.currentTimeMillis() + MAX_WATCH_MS
        while (System.currentTimeMillis() < deadline) {
            val iterationStart = System.currentTimeMillis()
            val job = try {
                jobs.getJob(jobId, waitSeconds = WAIT_SECONDS)
            } catch (e: HttpException) {
                return if (e.code() == 429 || e.code() >= 500) Result.retry() else Result.failure()
            } catch (_: IOException) {
                return Result.retry()
            } catch (_: Exception) {
                // Deleted locally or other terminal state — nothing to notify.
                return Result.failure()
            }
            when (job.status) {
                "done" -> {
                    JobNotifications.notifyJobFinished(applicationContext, jobId, succeeded = true)
                    return Result.success()
                }

                "failed" -> {
                    JobNotifications.notifyJobFinished(applicationContext, jobId, succeeded = false)
                    return Result.success()
                }

                "cancelled" -> return Result.success()
            }
            // A server without long-poll support answers instantly; pace the
            // loop so that degrades to ~one poll per window instead of a
            // tight request storm.
            val elapsed = System.currentTimeMillis() - iterationStart
            val minIntervalMs = MIN_POLL_INTERVAL_SECONDS * 1_000L
            if (elapsed < minIntervalMs) delay(minIntervalMs - elapsed)
        }
        return Result.retry()
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val WAIT_SECONDS = 25
        private const val MIN_POLL_INTERVAL_SECONDS = 5
        private const val MAX_WATCH_MS = 8L * 60L * 1000L

        /** Idempotent per job: re-enqueueing an already-watched job is a no-op. */
        fun enqueue(context: Context, jobId: String) {
            val request = OneTimeWorkRequestBuilder<JobWatchWorker>()
                .setInputData(workDataOf(KEY_JOB_ID to jobId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("watch_$jobId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
