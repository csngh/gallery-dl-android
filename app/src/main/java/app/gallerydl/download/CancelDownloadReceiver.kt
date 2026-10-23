package app.gallerydl.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.gallerydl.GalleryDlApp

/** The Cancel button of the download notification: asks the running download to stop after its current file. */
class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadNotifications.ACTION_CANCEL) return
        val taskId = intent.getLongExtra(DownloadNotifications.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        (context.applicationContext as GalleryDlApp).container.cancelRegistry.request(taskId)
    }
}
