package app.gallerydl.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gallerydl.data.DownloadRepository
import app.gallerydl.data.DownloadTask
import app.gallerydl.download.UrlCheck
import app.gallerydl.download.UrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.gallerydl.ui.components.LinkInput
import app.gallerydl.ui.components.UrlInputState

class QueueViewModel(
    private val downloads: DownloadRepository,
    private val validator: UrlValidator,
    val folderChoice: app.gallerydl.download.FolderChoice,
) : ViewModel() {

    /** null while the first query is loading. */
    val tasks: StateFlow<List<DownloadTask>?> = downloads.observeTasks()
        // Links cleared from the Queue stay on Home and in Activity.
        .map { list -> list.filterNot { it.hiddenFromQueue } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val linkInput = LinkInput(downloads, validator)
    val input: StateFlow<UrlInputState> = linkInput.state

    fun onTextChange(text: String) = linkInput.onTextChange(text)

    fun submit() {
        viewModelScope.launch { linkInput.submit() }
    }

    fun start(id: Long, folderName: String? = null) = viewModelScope.launch { downloads.start(id, folderName) }
    fun startAll() = viewModelScope.launch { downloads.startAll() }
    fun stopAll() = viewModelScope.launch { downloads.cancelAll() }
    fun cancel(id: Long) = viewModelScope.launch { downloads.cancel(id) }
    fun retry(id: Long) = viewModelScope.launch { downloads.retry(id) }
    fun remove(id: Long) = viewModelScope.launch { downloads.remove(id) }
    fun clearFinished() = viewModelScope.launch { downloads.clearFinished() }
}
