package app.gallerydl.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import app.gallerydl.download.GalleryDlOptions
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.File

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** A folder the user picked through the system folder picker (Storage Access Framework). */
data class DownloadDestination(val treeUri: Uri, val name: String)

class SettingsRepository(private val context: Context) {

    /** null means the default: MediaStore collections under a "GalleryDl" folder. */
    val destination: Flow<DownloadDestination?> = context.settingsStore.data.map { prefs ->
        prefs[TREE_URI]?.let { DownloadDestination(Uri.parse(it), prefs[TREE_NAME] ?: it) }
    }

    suspend fun setDestination(treeUri: Uri) = withContext(Dispatchers.IO) {
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!granted) return@withContext
        releasePrevious(except = treeUri)
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment.orEmpty()
        context.settingsStore.edit {
            it[TREE_URI] = treeUri.toString()
            it[TREE_NAME] = name
        }
    }

    suspend fun resetToDefault() = withContext(Dispatchers.IO) {
        releasePrevious(except = null)
        context.settingsStore.edit {
            it.remove(TREE_URI)
            it.remove(TREE_NAME)
        }
    }

    private suspend fun releasePrevious(except: Uri?) {
        val previous = destination.first()?.treeUri ?: return
        if (previous == except) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                previous,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** The gallery-dl options, with their defaults until the user changes them. */
    val options: Flow<GalleryDlOptions> = context.settingsStore.data.map { prefs ->
        val defaults = GalleryDlOptions()
        GalleryDlOptions(
            apiRest = prefs[API_REST] ?: defaults.apiRest,
            sleepRequestEnabled = prefs[SLEEP_REQUEST_ON] ?: defaults.sleepRequestEnabled,
            sleepRequest = prefs[SLEEP_REQUEST] ?: defaults.sleepRequest,
            sleepEnabled = prefs[SLEEP_ON] ?: defaults.sleepEnabled,
            sleep = prefs[SLEEP] ?: defaults.sleep,
            retriesEnabled = prefs[RETRIES_ON] ?: defaults.retriesEnabled,
            retries = prefs[RETRIES] ?: defaults.retries,
            timeoutEnabled = prefs[TIMEOUT_ON] ?: defaults.timeoutEnabled,
            timeout = prefs[TIMEOUT] ?: defaults.timeout,
            skipExisting = prefs[SKIP_EXISTING] ?: defaults.skipExisting,
            wifiOnly = prefs[WIFI_ONLY] ?: defaults.wifiOnly,
            cookiesEnabled = prefs[COOKIES_ON] ?: defaults.cookiesEnabled,
        )
    }

    /** The short sound played with the startup logo. On by default. */
    val startupSound: Flow<Boolean> = context.settingsStore.data.map { it[STARTUP_SOUND] ?: true }

    suspend fun setStartupSound(on: Boolean) = context.settingsStore.edit { it[STARTUP_SOUND] = on }

    /** How many cookies the imported cookies.txt holds; 0 when none is imported. */
    val cookiesCount: Flow<Int> = context.settingsStore.data.map { it[COOKIES_COUNT] ?: 0 }

    suspend fun setRetriesEnabled(on: Boolean) = context.settingsStore.edit { it[RETRIES_ON] = on }
    suspend fun setRetries(value: String) = context.settingsStore.edit { it[RETRIES] = value }
    suspend fun setTimeoutEnabled(on: Boolean) = context.settingsStore.edit { it[TIMEOUT_ON] = on }
    suspend fun setTimeout(value: String) = context.settingsStore.edit { it[TIMEOUT] = value }
    suspend fun setSkipExisting(on: Boolean) = context.settingsStore.edit { it[SKIP_EXISTING] = on }
    suspend fun setWifiOnly(on: Boolean) = context.settingsStore.edit { it[WIFI_ONLY] = on }
    suspend fun setCookiesEnabled(on: Boolean) = context.settingsStore.edit { it[COOKIES_ON] = on }

    private val cookiesFile get() = File(context.filesDir, "cookies.txt")

    /** The imported cookies file, or null if there is none. The path is private to the app. */
    fun cookiesPath(): String? = cookiesFile.takeIf { it.isFile }?.path

    /**
     * Copies a cookies.txt (Netscape format) into private storage. Returns the number of cookies, or null if
     * the file is not a readable cookies.txt. Cookie values are never logged or shown.
     */
    suspend fun importCookies(uri: Uri): Int? = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytes(MAX_COOKIES_BYTES + 1)
                if (bytes.size > MAX_COOKIES_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return@withContext null
        val count = text.lineSequence().count { line ->
            val entry = line.removePrefix("#HttpOnly_")
            entry.isNotBlank() && !entry.startsWith("#") && entry.split('\t').size >= 7
        }
        if (count == 0) return@withContext null
        cookiesFile.writeText(text)
        context.settingsStore.edit {
            it[COOKIES_COUNT] = count
            it[COOKIES_ON] = true
        }
        count
    }

    suspend fun removeCookies() = withContext(Dispatchers.IO) {
        cookiesFile.delete()
        context.settingsStore.edit {
            it.remove(COOKIES_COUNT)
            it[COOKIES_ON] = false
        }
    }

    /** Space used by the app's records (database and per-link download memory), in bytes. */
    suspend fun recordsBytes(): Long = withContext(Dispatchers.IO) {
        listOf(File(context.dataDir, "databases"), File(context.filesDir, "archives"))
            .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    /** Returns every option and the download folder to their defaults. The imported cookies are kept. */
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        releasePrevious(except = null)
        context.settingsStore.edit { prefs ->
            val keepCookies = prefs[COOKIES_COUNT]
            val keepCookiesOn = prefs[COOKIES_ON]
            prefs.clear()
            keepCookies?.let { prefs[COOKIES_COUNT] = it }
            keepCookiesOn?.let { prefs[COOKIES_ON] = it }
        }
    }

    suspend fun setApiRest(on: Boolean) = context.settingsStore.edit { it[API_REST] = on }
    suspend fun setSleepRequestEnabled(on: Boolean) = context.settingsStore.edit { it[SLEEP_REQUEST_ON] = on }
    suspend fun setSleepRequest(value: String) = context.settingsStore.edit { it[SLEEP_REQUEST] = value }
    suspend fun setSleepEnabled(on: Boolean) = context.settingsStore.edit { it[SLEEP_ON] = on }
    suspend fun setSleep(value: String) = context.settingsStore.edit { it[SLEEP] = value }

    private companion object {
        val TREE_URI = stringPreferencesKey("download_tree_uri")
        val TREE_NAME = stringPreferencesKey("download_tree_name")
        val API_REST = booleanPreferencesKey("opt_api_rest")
        val SLEEP_REQUEST_ON = booleanPreferencesKey("opt_sleep_request_on")
        val SLEEP_REQUEST = stringPreferencesKey("opt_sleep_request")
        val SLEEP_ON = booleanPreferencesKey("opt_sleep_on")
        val SLEEP = stringPreferencesKey("opt_sleep")
        val RETRIES_ON = booleanPreferencesKey("opt_retries_on")
        val RETRIES = stringPreferencesKey("opt_retries")
        val TIMEOUT_ON = booleanPreferencesKey("opt_timeout_on")
        val TIMEOUT = stringPreferencesKey("opt_timeout")
        val SKIP_EXISTING = booleanPreferencesKey("opt_skip_existing")
        val WIFI_ONLY = booleanPreferencesKey("opt_wifi_only")
        val COOKIES_ON = booleanPreferencesKey("opt_cookies_on")
        val STARTUP_SOUND = booleanPreferencesKey("startup_sound")
        val COOKIES_COUNT = intPreferencesKey("cookies_count")
        const val MAX_COOKIES_BYTES = 5 * 1024 * 1024
    }
}
