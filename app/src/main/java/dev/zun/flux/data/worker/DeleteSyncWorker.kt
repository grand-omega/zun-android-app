package dev.zun.flux.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zun.flux.FluxApp
import retrofit2.HttpException
import java.io.IOException

class DeleteSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobs = (applicationContext as FluxApp).repositories.jobs
        return try {
            jobs.syncPendingDeletes()
            Result.success()
        } catch (e: HttpException) {
            // 429/5xx may resolve on their own; other 4xx (bad token, forbidden)
            // won't, so don't burn WorkManager backoff on them. Terminal failures
            // keep the pending_deletes rows in Room — the next deleteJob() or
            // syncHistory() re-enqueues this worker, so nothing is lost.
            if (e.code() == 429 || e.code() >= 500) Result.retry() else Result.failure()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pending_delete_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DeleteSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
