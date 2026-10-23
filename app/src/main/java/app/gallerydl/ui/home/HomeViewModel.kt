package app.gallerydl.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gallerydl.data.DownloadRepository
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.TaskResult
import app.gallerydl.data.TaskStatus
import app.gallerydl.data.result
import app.gallerydl.download.UrlValidator
import app.gallerydl.download.siteKey
import app.gallerydl.ui.components.LinkInput
import app.gallerydl.ui.components.UrlInputState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.gallerydl.download.UrlCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import app.gallerydl.download.iconHostOf

/** All the links of one website. */
data class SiteGroup(val site: String, val tasks: List<DownloadTask>) {
    val filesSaved: Int get() = tasks.sumOf { it.filesDownloaded }

    /** Links that failed or finished with errors. */
    val needAttention: Int get() = tasks.count { it.result == TaskResult.FAILED || it.result == TaskResult.PARTIAL }
    val inProgress: Int get() = tasks.count { it.result == TaskResult.IN_PROGRESS }
    val ready: Int get() = tasks.count { it.result == TaskResult.READY }
    val lastActivity: Long get() = tasks.maxOf { it.createdAt }

    /** The host to ask for this website's icon, taken from its newest link that has one. */
    val iconHost: String? get() = tasks.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { iconHostOf(it.url) }
}

/** The "Edit link" dialog: which link is being edited, the text so far, and what the check said. */
data class EditLinkState(
    val taskId: Long,
    val text: String,
    val error: UrlCheck? = null,
    val busy: Boolean = false,
    /** True for a link that has not started: saving only corrects it and does not start the download. */
    val notStarted: Boolean = false,
)

class HomeViewModel(
    private val downloads: DownloadRepository,
    private val validator: UrlValidator,
) : ViewModel() {

    private val linkInput = LinkInput(downloads, validator)
    val input: StateFlow<UrlInputState> = linkInput.state

    fun onTextChange(text: String) = linkInput.onTextChange(text)

    fun submit() {
        viewModelScope.launch { linkInput.submit() }
    }

    /** Websites with their links, the most recently used website first. null while loading. */
    val groups: StateFlow<List<SiteGroup>?> = downloads.observeTasks()
        .map { tasks ->
            tasks.groupBy { it.siteKey }
                .map { (site, siteTasks) -> SiteGroup(site, siteTasks) }
                .sortedByDescending { it.lastActivity }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _edit = MutableStateFlow<EditLinkState?>(null)
    val edit: StateFlow<EditLinkState?> = _edit.asStateFlow()

    fun startEdit(task: DownloadTask) {
        _edit.value = EditLinkState(task.id, task.url, notStarted = task.status == TaskStatus.PENDING)
    }

    fun onEditTextChange(text: String) = _edit.update { it?.copy(text = text, error = null) }

    fun dismissEdit() {
        _edit.value = null
    }

    /** Checks the new link like the paste field does; on success the link is replaced and queued again. */
    fun saveEdit() {
        val state = _edit.value ?: return
        val url = state.text.trim()
        if (state.busy || url.isEmpty()) return
        viewModelScope.launch {
            _edit.update { it?.copy(busy = true, error = null) }
            val result = validator.check(url)
            if (result.check == UrlCheck.VALID) {
                downloads.editLink(state.taskId, url, result.site)
                _edit.value = null
            } else {
                _edit.update { it?.copy(busy = false, error = result.check) }
            }
        }
    }

    fun start(id: Long) = viewModelScope.launch { downloads.start(id) }
    fun startAll() = viewModelScope.launch { downloads.startAll() }
    fun cancel(id: Long) = viewModelScope.launch { downloads.cancel(id) }
    fun retry(id: Long) = viewModelScope.launch { downloads.retry(id) }
    fun remove(id: Long) = viewModelScope.launch { downloads.remove(id) }
}
