package app.gallerydl.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gallerydl.data.DownloadDestination
import app.gallerydl.data.DownloadRepository
import app.gallerydl.data.SettingsRepository
import app.gallerydl.download.GalleryDlOptions
import app.gallerydl.download.PythonBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the app and its parts are, for the About section. */
data class AboutInfo(val appVersion: String, val galleryDlVersion: String)

enum class CookieImport { INVALID }

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
    private val bridge: PythonBridge,
    context: Context,
    /** Already-loaded settings from the app container, so this screen never starts empty. */
    val options: StateFlow<GalleryDlOptions?>,
    val destination: StateFlow<DownloadDestination?>,
    val cookiesCount: StateFlow<Int>,
    val startupSound: StateFlow<Boolean>,
) : ViewModel() {

    private val _cookieError = MutableStateFlow<CookieImport?>(null)
    val cookieError: StateFlow<CookieImport?> = _cookieError.asStateFlow()

    private val _recordsBytes = MutableStateFlow<Long?>(null)
    val recordsBytes: StateFlow<Long?> = _recordsBytes.asStateFlow()

    private val _about = MutableStateFlow<AboutInfo?>(null)
    val about: StateFlow<AboutInfo?> = _about.asStateFlow()

    init {
        refreshRecords()
        viewModelScope.launch {
            val appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
            val galleryDl = withContext(Dispatchers.Default) { runCatching { bridge.galleryDlVersion() }.getOrDefault("") }
            _about.value = AboutInfo(appVersion, galleryDl)
        }
    }

    private fun refreshRecords() {
        viewModelScope.launch { _recordsBytes.value = settings.recordsBytes() }
    }

    fun setStartupSound(on: Boolean) = viewModelScope.launch { settings.setStartupSound(on) }
    fun setApiRest(on: Boolean) = viewModelScope.launch { settings.setApiRest(on) }
    fun setSleepRequestEnabled(on: Boolean) = viewModelScope.launch { settings.setSleepRequestEnabled(on) }
    fun setSleepRequest(value: String) = viewModelScope.launch { settings.setSleepRequest(value) }
    fun setSleepEnabled(on: Boolean) = viewModelScope.launch { settings.setSleepEnabled(on) }
    fun setSleep(value: String) = viewModelScope.launch { settings.setSleep(value) }
    fun setRetriesEnabled(on: Boolean) = viewModelScope.launch { settings.setRetriesEnabled(on) }
    fun setRetries(value: String) = viewModelScope.launch { settings.setRetries(value) }
    fun setTimeoutEnabled(on: Boolean) = viewModelScope.launch { settings.setTimeoutEnabled(on) }
    fun setTimeout(value: String) = viewModelScope.launch { settings.setTimeout(value) }
    fun setSkipExisting(on: Boolean) = viewModelScope.launch { settings.setSkipExisting(on) }

    fun setWifiOnly(on: Boolean) = viewModelScope.launch {
        settings.setWifiOnly(on)
        downloads.applyNetworkSetting()
    }

    fun setDestination(treeUri: Uri) = viewModelScope.launch { settings.setDestination(treeUri) }
    fun resetDestination() = viewModelScope.launch { settings.resetToDefault() }

    fun setCookiesEnabled(on: Boolean) = viewModelScope.launch { settings.setCookiesEnabled(on) }
    fun importCookies(uri: Uri) = viewModelScope.launch {
        _cookieError.value = if (settings.importCookies(uri) == null) CookieImport.INVALID else null
    }
    fun removeCookies() = viewModelScope.launch {
        settings.removeCookies()
        _cookieError.value = null
    }
    fun dismissCookieError() = _cookieError.update { null }

    fun clearFinishedHistory() = viewModelScope.launch {
        downloads.clearFinishedHistory()
        refreshRecords()
    }

    fun resetAllSettings() = viewModelScope.launch {
        settings.resetAll()
        downloads.applyNetworkSetting()
    }
}
