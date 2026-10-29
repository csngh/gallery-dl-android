package app.gallerydl.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.gallerydl.GalleryDlApp
import app.gallerydl.ui.activity.ActivityViewModel
import app.gallerydl.ui.library.LibraryViewModel
import app.gallerydl.ui.queue.QueueViewModel
import app.gallerydl.ui.home.HomeViewModel
import app.gallerydl.ui.settings.SettingsViewModel
import app.gallerydl.share.ShareViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val c = app().container
            QueueViewModel(c.downloadRepository, c.urlValidator, c.folderChoice)
        }
        initializer {
            val c = app().container
            HomeViewModel(c.downloadRepository, c.urlValidator, c.folderChoice)
        }
        initializer {
            val c = app().container
            LibraryViewModel(c.libraryRepository, c.livePreviews, c.thumbnailCache, c.previewStripCache, c.folderColumns, c.libraryColumns, c.libraryNewestFirst, c.folderNewestFirst, c.settingsRepository)
        }
        initializer { ActivityViewModel(app().container.downloadRepository) }
        initializer {
            val c = app().container
            ShareViewModel(c.downloadRepository, c.urlValidator)
        }
        initializer {
            val c = app().container
            SettingsViewModel(
                c.settingsRepository, c.downloadRepository, c.pythonBridge, c.applicationContext,
                c.options, c.destination, c.cookiesCount, c.startupSound, c.livePreviews, c.thumbnailCache, c.previewStripCache,
            )
        }
    }
}

private fun CreationExtras.app(): GalleryDlApp = this[APPLICATION_KEY] as GalleryDlApp
