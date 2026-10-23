package app.gallerydl.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gallerydl.data.DownloadRepository
import app.gallerydl.download.UrlCheck
import app.gallerydl.download.UrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the share popup is showing. */
sealed interface ShareState {
    data object Checking : ShareState
    data object NoLink : ShareState
    data class Ready(val url: String, val site: String) : ShareState
    data class Rejected(val url: String, val reason: UrlCheck) : ShareState
}

class ShareViewModel(
    private val downloads: DownloadRepository,
    private val validator: UrlValidator,
) : ViewModel() {

    private val _state = MutableStateFlow<ShareState>(ShareState.Checking)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private var started = false

    /** Checks the shared text once; a rotation or recreation does not check it again. */
    fun start(sharedText: String?) {
        if (started) return
        started = true
        val url = firstLinkIn(sharedText)
        if (url == null) {
            _state.value = ShareState.NoLink
            return
        }
        viewModelScope.launch {
            val result = validator.check(url)
            _state.value = if (result.check == UrlCheck.VALID) {
                ShareState.Ready(url, result.site.orEmpty())
            } else {
                ShareState.Rejected(url, result.check)
            }
        }
    }

    /** Queues the link and reports back once it is saved. */
    fun add(onAdded: () -> Unit) {
        val ready = _state.value as? ShareState.Ready ?: return
        viewModelScope.launch {
            downloads.enqueue(ready.url, ready.site)
            onAdded()
        }
    }
}
