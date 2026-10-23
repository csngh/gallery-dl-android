package app.gallerydl.download

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class UrlCheck { VALID, MALFORMED, UNSUPPORTED }

/** [site] is only set for a valid link. */
data class UrlResult(val check: UrlCheck, val site: String? = null)

class UrlValidator(private val bridge: PythonBridge) {

    suspend fun check(raw: String): UrlResult = withContext(Dispatchers.Default) {
        val uri = Uri.parse(raw)
        val wellFormed = raw.none { it.isWhitespace() } &&
            uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank()
        when {
            !wellFormed -> UrlResult(UrlCheck.MALFORMED)
            !bridge.isSupported(raw) -> UrlResult(UrlCheck.UNSUPPORTED)
            else -> UrlResult(UrlCheck.VALID, siteKeyFor(bridge.siteOf(raw), raw))
        }
    }
}
