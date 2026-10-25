package app.gallerydl.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.gallerydl.GalleryDlApp
import kotlinx.coroutines.flow.first

/**
 * Makes the preview clip of every video that does not have one yet, as a background job: it carries on after the library
 * is closed or the app is left, shows a quiet notification with its progress, and waits while the battery is low. A video
 * that finishes downloading gets its clip straight away from the download itself; this catches up on the rest.
 */
class PreviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container = (context.applicationContext as GalleryDlApp).container
    private val notifications = DownloadNotifications(context)

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.previews(0, 0)

    override suspend fun doWork(): Result {
        if (!container.settingsRepository.livePreviews.first()) return Result.success()
        // Preview clips are small, but they are not worth filling the last of the storage for.
        if (container.storageMonitor.refresh()) return Result.success()
        val videos = container.database.libraryDao().observeVideos().first()
        // The notification only appears once there is something to make. Foreground promotion can be refused when the
        // job starts in the background; the work still runs.
        container.previewStripCache.makeMissing(videos) { done, total ->
            runCatching { setForeground(notifications.previews(done, total)) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "previews"

        /** Starts the job unless it is already running. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PreviewWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
