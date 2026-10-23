package app.gallerydl.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gallerydl.data.FolderSummary
import app.gallerydl.data.LibraryItem
import app.gallerydl.data.LibraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OpenFolder(val id: Long, val name: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {

    /** Search term for the folder list; the files of an open folder have their own, so two panes can coexist. */
    val query = MutableStateFlow("")
    val itemQuery = MutableStateFlow("")

    private val _openFolder = MutableStateFlow<OpenFolder?>(null)
    val openFolder: StateFlow<OpenFolder?> = _openFolder.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val debouncedQuery = query.debounce { if (it.isEmpty()) 0L else 250L }
    private val debouncedItemQuery = itemQuery.debounce { if (it.isEmpty()) 0L else 250L }

    /** null while the first query is loading. */
    val folders: StateFlow<List<FolderSummary>?> = debouncedQuery
        .flatMapLatest { library.observeFolders(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Items of the open folder; null while loading. */
    val items: StateFlow<List<LibraryItem>?> = _openFolder
        .flatMapLatest { folder ->
            if (folder == null) {
                flowOf(emptyList())
            } else {
                debouncedItemQuery.flatMapLatest { library.observeItems(folder.id, it.trim()) }
            }
        }
        .onEach { list ->
            // Leave a folder once everything in it is gone (deleted here or in another app).
            if (list.isEmpty() && itemQuery.value.isBlank() && _openFolder.value != null) closeFolder()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            library.mediaChanges().debounce(1_000).collect { library.pruneMissing() }
        }
    }

    fun refresh() {
        viewModelScope.launch { library.pruneMissing() }
    }

    fun onQueryChange(value: String) = query.update { value }
    fun onItemQueryChange(value: String) = itemQuery.update { value }

    fun open(folder: FolderSummary) {
        itemQuery.value = ""
        _selection.value = emptySet()
        _openFolder.value = OpenFolder(folder.id, folder.displayName)
    }

    /** Back steps out of selection first, then out of the folder. */
    fun back() {
        if (_selection.value.isNotEmpty()) clearSelection() else closeFolder()
    }

    private fun closeFolder() {
        itemQuery.value = ""
        _selection.value = emptySet()
        _openFolder.value = null
    }

    fun toggleSelected(id: Long) = _selection.update { if (id in it) it - id else it + id }
    fun clearSelection() = _selection.update { emptySet() }

    fun deleteSelected() {
        val ids = _selection.value
        _selection.value = emptySet()
        viewModelScope.launch { library.deleteItems(ids) }
    }

    fun deleteItem(id: Long) = viewModelScope.launch { library.deleteItems(listOf(id)) }

    fun deleteFolder(id: Long) = viewModelScope.launch { library.deleteFolder(id) }
}
