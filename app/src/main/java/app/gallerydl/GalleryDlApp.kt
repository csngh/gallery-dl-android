package app.gallerydl

import android.app.Application
import android.content.Context
import androidx.work.WorkManager
import java.io.File
import app.gallerydl.data.AppDatabase
import app.gallerydl.data.CancelRegistry
import app.gallerydl.data.DownloadRepository
import app.gallerydl.data.LibraryRepository
import app.gallerydl.data.SettingsRepository
import app.gallerydl.download.MediaSaver
import app.gallerydl.download.PythonBridge
import app.gallerydl.download.UrlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import app.gallerydl.data.DownloadDestination
import app.gallerydl.download.GalleryDlOptions

/** Manual dependency container; small enough that a DI framework would only add build time. */
class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.create(context)
    val cancelRegistry = CancelRegistry()
    val pythonBridge = PythonBridge(context)
    val mediaSaver = MediaSaver(context)
    val settingsRepository = SettingsRepository(context)

    /**
     * Settings are read as soon as the app starts and kept up to date, so the Settings screen opens already
     * filled in instead of showing a placeholder while it loads.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val options: StateFlow<GalleryDlOptions?> = settingsRepository.options.stateIn(appScope, SharingStarted.Eagerly, null)
    val destination: StateFlow<DownloadDestination?> = settingsRepository.destination.stateIn(appScope, SharingStarted.Eagerly, null)
    val startupSound: StateFlow<Boolean> = settingsRepository.startupSound.stateIn(appScope, SharingStarted.Eagerly, true)
    val folderColumns: StateFlow<Int> = settingsRepository.folderColumns.stateIn(appScope, SharingStarted.Eagerly, 3)
    val libraryColumns: StateFlow<Int> = settingsRepository.libraryColumns.stateIn(appScope, SharingStarted.Eagerly, 2)
    val livePreviews: StateFlow<Boolean> = settingsRepository.livePreviews.stateIn(appScope, SharingStarted.Eagerly, true)
    val cookiesCount: StateFlow<Int> = settingsRepository.cookiesCount.stateIn(appScope, SharingStarted.Eagerly, 0)
    val urlValidator = UrlValidator(pythonBridge)
    val storageMonitor = app.gallerydl.data.StorageMonitor(context)
    val thumbnailCache = app.gallerydl.data.ThumbnailCache(context)
    val previewStripCache = app.gallerydl.data.PreviewStripCache(context)
    val libraryRepository = LibraryRepository(database.libraryDao(), context.contentResolver, thumbnailCache, previewStripCache)
    val downloadRepository =
        DownloadRepository(
            database.taskDao(),
            WorkManager.getInstance(context),
            cancelRegistry,
            File(context.filesDir, "archives"),
            settingsRepository,
        )

    init {
        // Looks at the storage every so often while the app runs, and starts paused downloads again once there is room.
        appScope.launch {
            while (true) {
                val full = storageMonitor.refresh()
                if (!full && storageMonitor.pausedForStorage) {
                    storageMonitor.pausedForStorage = false
                    downloadRepository.resume()
                }
                delay(STORAGE_CHECK_MS)
            }
        }
    }

    private companion object {
        const val STORAGE_CHECK_MS = 15_000L
    }
}

class GalleryDlApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
