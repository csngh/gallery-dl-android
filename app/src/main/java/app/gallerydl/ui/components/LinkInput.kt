package app.gallerydl.ui.components

import app.gallerydl.data.DownloadRepository
import app.gallerydl.download.UrlCheck
import app.gallerydl.download.UrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UrlInputState(
    val text: String = "",
    val error: UrlCheck? = null,
    val validating: Boolean = false,
)

/** The paste-a-link logic shared by every screen that has a link field. */
class LinkInput(
    private val downloads: DownloadRepository,
    private val validator: UrlValidator,
) {
    private val _state = MutableStateFlow(UrlInputState())
    val state: StateFlow<UrlInputState> = _state.asStateFlow()

    fun onTextChange(text: String) = _state.update { it.copy(text = text, error = null) }

    suspend fun submit() {
        val url = _state.value.text.trim()
        if (_state.value.validating || url.isEmpty()) return
        _state.update { it.copy(validating = true, error = null) }
        val result = validator.check(url)
        if (result.check == UrlCheck.VALID) {
            downloads.enqueue(url, result.site)
            _state.value = UrlInputState()
        } else {
            _state.update { it.copy(validating = false, error = result.check) }
        }
    }
}
