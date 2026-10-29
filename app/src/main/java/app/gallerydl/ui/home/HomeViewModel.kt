package app.gallerydl.ui.home

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gallerydl.data.DownloadRepository
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.ImportResult
import app.gallerydl.data.LinkList
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
import kotlinx.coroutines.withContext
import app.gallerydl.download.UrlCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    val folderChoice: app.gallerydl.download.FolderChoice,
) : ViewModel() {

    /** Link -> the library folder its files are in, so a link can be shown by its folder's name. */
    val folderNames: StateFlow<Map<String, String>> = folderChoice.observeFolderNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    // region Export and import

    /** What the export and import dialogs show. */
    sealed interface Transfer {
        data class Exported(val count: Int) : Transfer
        data class Importing(val done: Int, val total: Int) : Transfer
        data class Imported(val result: ImportResult) : Transfer
        /** The file could not be written or read. */
        data object Failed : Transfer
    }

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    fun dismissTransfer() {
        _transfer.value = null
    }

    /** Writes every link on Home, one per line, to [uri]. */
    fun exportTo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val urls = downloads.observeTasks().first().sortedBy { it.id }.map { it.url }.distinct()
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(LinkList.format(urls).toByteArray()) } != null
                }.getOrDefault(false)
            }
            _transfer.value = if (written) Transfer.Exported(urls.size) else Transfer.Failed
        }
    }

    /**
     * Reads the links in the file at [uri] and adds the new, supported ones to Home as "ready to start". Links already on
     * Home are skipped, and every link is checked like one pasted by hand.
     */
    fun importFrom(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readSmallText(resolver, uri) }
            if (text == null) {
                _transfer.value = Transfer.Failed
                return@launch
            }
            val lines = LinkList.parse(text)
            val known = downloads.observeTasks().first().map { it.url }.toHashSet()
            val links = lines.filterIsInstance<LinkList.Line.Link>().map { it.url }
            var result = ImportResult(notLinks = lines.size - links.size)
            _transfer.value = Transfer.Importing(0, links.size)
            links.forEachIndexed { index, url ->
                result = if (!known.add(url)) {
                    result.copy(duplicates = result.duplicates + 1)
                } else {
                    val check = validator.check(url)
                    if (check.check == UrlCheck.VALID) {
                        downloads.enqueue(url, check.site)
                        result.copy(added = result.added + 1)
                    } else {
                        result.copy(unsupported = result.unsupported + 1)
                    }
                }
                _transfer.value = Transfer.Importing(index + 1, links.size)
            }
            _transfer.value = Transfer.Imported(result)
        }
    }

    /** The text of a file, or null if it cannot be read or is not small (a list of links is never large). */
    private fun readSmallText(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                out.write(chunk, 0, read)
                if (out.size() > MAX_LIST_BYTES) return@runCatching null
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    // endregion

    fun start(id: Long, folderName: String? = null) = viewModelScope.launch { downloads.start(id, folderName) }
    fun startAll() = viewModelScope.launch { downloads.startAll() }

    fun stopAll() = viewModelScope.launch { downloads.cancelAll() }

    fun renewAll() = viewModelScope.launch { downloads.renewAll() }
    fun cancel(id: Long) = viewModelScope.launch { downloads.cancel(id) }
    fun retry(id: Long) = viewModelScope.launch { downloads.retry(id) }
    fun remove(id: Long) = viewModelScope.launch { downloads.remove(id) }
}

private const val MAX_LIST_BYTES = 5 * 1024 * 1024
