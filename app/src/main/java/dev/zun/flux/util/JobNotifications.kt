package dev.zun.flux.util

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.zun.flux.MainActivity
import dev.zun.flux.R

/**
 * "Your image is ready" notifications, posted by [dev.zun.flux.data.worker.JobWatchWorker]
 * when a generation reaches a terminal state while the app is backgrounded.
 * Tapping deep-links to the result screen via [ACTION_VIEW_RESULT].
 */
object JobNotifications {
    private const val CHANNEL_ID = "job_results"
    const val ACTION_VIEW_RESULT = "dev.zun.flux.action.VIEW_RESULT"
    const val EXTRA_JOB_ID = "job_id"

    fun canNotify(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    /** True when our own process is foregrounded — the user is already looking at the app. */
    private fun isAppForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    fun notifyJobFinished(context: Context, jobId: String, succeeded: Boolean) {
        // Inline permission check (not via canNotify) so lint can verify it.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (isAppForeground()) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_RESULT
            putExtra(EXTRA_JOB_ID, jobId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            jobId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(
            if (succeeded) R.string.notify_job_done_title else R.string.notify_job_failed_title,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_edit)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notify_job_tap_to_view))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(jobId.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_job_results),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
