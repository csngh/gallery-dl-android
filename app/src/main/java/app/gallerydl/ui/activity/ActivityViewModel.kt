package app.gallerydl.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gallerydl.data.DownloadRepository
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.TaskResult
import app.gallerydl.data.result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityFilter(val results: Set<TaskResult>?) {
    ALL(null),
    COMPLETED(setOf(TaskResult.COMPLETED)),
    PARTIAL(setOf(TaskResult.PARTIAL)),
    FAILED(setOf(TaskResult.FAILED)),
}

data class ActivityStats(
    val links: Int = 0,
    val completed: Int = 0,
    val partial: Int = 0,
    val failed: Int = 0,
    val filesSaved: Int = 0,
    val filesFailed: Int = 0,
) {
    /** Links that can be retried with "Retry failed". */
    val retryable: Int get() = partial + failed
}

data class ActivityState(
    val stats: ActivityStats = ActivityStats(),
    val tasks: List<DownloadTask> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.ALL,
    /** False until the first database result arrives, so the screen does not flash its empty state. */
    val loaded: Boolean = false,
)

class ActivityViewModel(private val downloads: DownloadRepository) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.ALL)

    val state: StateFlow<ActivityState> = combine(downloads.observeTasks(), filter) { tasks, selected ->
        val results = tasks.map { it.result }
        ActivityState(
            stats = ActivityStats(
                links = tasks.size,
                completed = results.count { it == TaskResult.COMPLETED },
                partial = results.count { it == TaskResult.PARTIAL },
                failed = results.count { it == TaskResult.FAILED },
                filesSaved = tasks.sumOf { it.filesDownloaded },
                filesFailed = tasks.sumOf { it.failedFiles },
            ),
            tasks = tasks.filter { selected.results == null || it.result in selected.results },
            filter = selected,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityState())

    fun setFilter(value: ActivityFilter) = filter.update { value }
    fun retry(id: Long) = viewModelScope.launch { downloads.retry(id) }
    fun retryAllFailed() = viewModelScope.launch { downloads.retryAllFailed() }
}
