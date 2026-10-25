package app.gallerydl.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.gallerydl.MainActivity
import app.gallerydl.R
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.TaskStatus

/**
 * Everything the user sees in the notification shade: one live notification for the running download (website,
 * progress bar, file count, a Cancel button) and one lasting notification per link that has ended.
 */
class DownloadNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // Both channels are quiet: the shade shows them, but nothing beeps or pops up for every link. The user
        // can raise either in Android's settings.
        manager.createNotificationChannel(
            NotificationChannel(PROGRESS_CHANNEL, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_progress_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL, context.getString(R.string.channel_results), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_results_description) },
        )
    }

    /** What the foreground service shows from the moment it starts, before the first file is known. */
    fun starting(): ForegroundInfo = ForegroundInfo(
        PROGRESS_ID,
        progressNotification(title = context.getString(R.string.notif_starting), text = null, percent = -1, waiting = 0, taskId = null),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    /** The notification of the background job that makes preview clips. */
    fun previews(done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.notif_previews))
            .setContentText(context.getString(R.string.notif_previews_progress, done, total))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(openApp())
            .setProgress(total.coerceAtLeast(1), done, false)
            .build()
        return ForegroundInfo(PREVIEWS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    /**
     * Updates the live notification. [percent] is 0 to 100 for the whole link when known, or -1 for an
     * indeterminate bar. Posting with the same id updates the foreground service's own notification.
     */
    fun progress(task: DownloadTask, index: Int, count: Int, percent: Int, waiting: Int) {
        val text = when {
            index <= 0 -> null
            count > 0 -> context.getString(R.string.notif_file_of, index, count) + percentSuffix(percent)
            else -> context.getString(R.string.notif_file, index) + percentSuffix(percent)
        }
        notify(PROGRESS_ID, progressNotification(siteTitle(task), text, percent, waiting, task.id))
    }

    /** A lasting notification for a link that has ended, so the result is there when the user comes back. */
    fun finished(task: DownloadTask, status: TaskStatus, filesSaved: Int, failedFiles: Int) {
        if (status == TaskStatus.CANCELLED) {
            manager.cancel(resultId(task.id))
            return
        }
        val title = when {
            status == TaskStatus.FAILED -> R.string.notif_failed
            failedFiles > 0 -> R.string.notif_partial
            else -> R.string.notif_complete
        }
        val text = buildString {
            append(siteTitle(task))
            if (filesSaved > 0) append(" · ").append(context.resources.getQuantityString(R.plurals.files_saved, filesSaved, filesSaved))
            if (failedFiles > 0) append(" · ").append(context.resources.getQuantityString(R.plurals.files_failed, failedFiles, failedFiles))
        }
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setSubText(shortLink(task.url))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notify(resultId(task.id), notification)
    }

    private fun progressNotification(title: String, text: String?, percent: Int, waiting: Int, taskId: Long?): Notification {
        val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
        text?.let { builder.setContentText(it) }
        if (waiting > 0) builder.setSubText(context.resources.getQuantityString(R.plurals.notif_waiting, waiting, waiting))
        if (taskId != null) builder.addAction(0, context.getString(R.string.action_cancel), cancelIntent(taskId))
        return builder.build()
    }

    private fun percentSuffix(percent: Int) = if (percent >= 0) " · " + context.getString(R.string.progress_percent, percent) else ""

    /** "imgur" becomes "Imgur"; a host such as "upload.wikimedia.org" is shown as it is. */
    private fun siteTitle(task: DownloadTask): String {
        val site = task.site ?: hostOf(task.url)
        return if ('.' in site) site else site.replaceFirstChar { it.uppercase() }
    }

    private fun shortLink(url: String) = url.removePrefix("https://").removePrefix("http://").take(60)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelIntent(taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, taskId.toInt(),
        Intent(context, CancelDownloadReceiver::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Without the notification permission (Android 13 and later) posting is simply skipped. */
    private fun notify(id: Int, notification: Notification) {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) manager.notify(id, notification)
    }

    companion object {
        const val PROGRESS_ID = 1
        const val PREVIEWS_ID = 2
        const val ACTION_CANCEL = "app.gallerydl.action.CANCEL_DOWNLOAD"
        const val EXTRA_TASK_ID = "task_id"
        private const val PROGRESS_CHANNEL = "downloads"
        private const val RESULT_CHANNEL = "results"
        private fun resultId(taskId: Long) = 1_000 + (taskId % 100_000).toInt()
    }
}
