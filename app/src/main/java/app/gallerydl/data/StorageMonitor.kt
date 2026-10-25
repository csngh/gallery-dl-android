package app.gallerydl.data

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watches how full the phone's storage is. At 99% or more downloads pause and the app says so; they carry on by
 * themselves once the storage is below 98.5% again (the gap keeps it from flipping back and forth near the limit).
 */
class StorageMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("storage", Context.MODE_PRIVATE)

    private val _full = MutableStateFlow(false)
    /** True while the storage is too full to download. */
    val full: StateFlow<Boolean> = _full

    private val _usedPercent = MutableStateFlow(0)
    val usedPercent: StateFlow<Int> = _usedPercent

    /** Set when downloads were stopped for lack of space, so they are started again once there is room, even after a restart. */
    var pausedForStorage: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) = prefs.edit().putBoolean("paused", value).apply()

    /** Looks at the storage now and returns whether it is too full. */
    fun refresh(): Boolean {
        val used = usedFraction()
        _usedPercent.value = Math.round(used * 100)
        _full.value = if (_full.value) used >= RESUME_BELOW else used >= LIMIT
        return _full.value
    }

    private fun usedFraction(): Float = runCatching {
        // The app's own files live on the same volume as the shared folders downloads are saved to.
        val stat = StatFs(appContext.filesDir.path)
        val total = stat.totalBytes
        if (total <= 0) 0f else 1f - stat.availableBytes.toFloat() / total
    }.getOrDefault(0f)

    private companion object {
        const val LIMIT = 0.99f
        const val RESUME_BELOW = 0.985f
    }
}
