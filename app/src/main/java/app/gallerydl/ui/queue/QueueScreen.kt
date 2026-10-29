package app.gallerydl.ui.queue

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gallerydl.R
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.TaskResult
import app.gallerydl.data.TaskStatus
import app.gallerydl.data.overallProgress
import app.gallerydl.data.result
import app.gallerydl.download.UrlCheck
import app.gallerydl.ui.AppViewModelProvider
import app.gallerydl.ui.components.ConfirmDialog
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.theme.Spacing
import app.gallerydl.ui.components.EmptyState
import app.gallerydl.ui.components.IconTile
import app.gallerydl.ui.components.ScreenTitle
import app.gallerydl.ui.components.tile
import app.gallerydl.ui.theme.Tone
import app.gallerydl.ui.components.ShimmerCardList
import app.gallerydl.ui.components.softScrollEdges
import androidx.compose.foundation.lazy.rememberLazyListState
import app.gallerydl.ui.components.readableWidth
import app.gallerydl.ui.components.LinkActionsMenu
import app.gallerydl.ui.components.LinkField
import app.gallerydl.ui.components.StorageWarningBanner
import app.gallerydl.ui.components.PendingAction
import app.gallerydl.ui.components.PendingActionDialog
import app.gallerydl.ui.components.FullLinkDialog
import androidx.compose.foundation.clickable
import app.gallerydl.ui.components.StartButton
import app.gallerydl.ui.components.StartAllButton
import app.gallerydl.ui.components.StopAllButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: QueueViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val hasFinished = tasks?.any { it.status.isFinished() } == true
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var fullLink by remember { mutableStateOf<String?>(null) }
    var folderAsk by remember { mutableStateOf<app.gallerydl.data.DownloadTask?>(null) }

    Column(Modifier.readableWidth()) {
        TopAppBar(
            title = { ScreenTitle(stringResource(R.string.nav_queue)) },
            actions = {
                if (hasFinished) {
                    TextButton(
                        onClick = { pending = PendingAction.ClearFinished },
                        colors = ButtonDefaults.textButtonColors(contentColor = ActionColors.Negative),
                    ) {
                        Text(stringResource(R.string.action_clear_finished))
                    }
                }
            },
        )
        StorageWarningBanner(Modifier.padding(horizontal = Spacing.Md))
        LinkField(
            state = input,
            onTextChange = viewModel::onTextChange,
            onSubmit = viewModel::submit,
            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        )
        val loaded = tasks
        StopAllButton(
            count = loaded?.count { it.status == TaskStatus.QUEUED || it.status == TaskStatus.DOWNLOADING } ?: 0,
            onClick = viewModel::stopAll,
            modifier = Modifier.padding(horizontal = Spacing.Md),
        )
        StartAllButton(
            count = loaded?.count { it.status == TaskStatus.PENDING } ?: 0,
            onClick = viewModel::startAll,
            modifier = Modifier.padding(horizontal = Spacing.Md),
        )
        if (loaded == null) {
            ShimmerCardList()
        } else if (loaded.isEmpty()) {
            EmptyState(R.string.queue_empty_title, R.string.queue_empty_body, Icons.Filled.Download, Tone.Blue)
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.softScrollEdges(listState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            ) {
                items(loaded, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onCancel = {
                            // A running download is confirmed first; a queued one can simply be retried later.
                            if (task.status == TaskStatus.DOWNLOADING) {
                                pending = PendingAction.CancelDownload(task.id)
                            } else {
                                viewModel.cancel(task.id)
                            }
                        },
                        onRetry = { viewModel.retry(task.id) },
                        onStart = { folderAsk = task },
                        onViewLink = { fullLink = task.url },
                        onRemove = { pending = PendingAction.Remove(task.id) },
                    )
                }
            }
        }
    }

    PendingActionDialog(
        action = pending,
        onClearFinished = viewModel::clearFinished,
        onRemove = viewModel::remove,
        onCancelDownload = viewModel::cancel,
        onDismiss = { pending = null },
    )
    fullLink?.let { url -> FullLinkDialog(url = url, onDismiss = { fullLink = null }) }
    folderAsk?.let { task ->
        app.gallerydl.ui.components.StartFolderDialog(
            task = task,
            choice = viewModel.folderChoice,
            onStart = { name ->
                viewModel.start(task.id, name)
                folderAsk = null
            },
            onDismiss = { folderAsk = null },
        )
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit,
    onRemove: () -> Unit,
    onViewLink: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Md), verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (statusIcon, statusTone) = task.result.tile()
                IconTile(statusIcon, statusTone, Modifier.padding(end = Spacing.Md))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.url,
                        modifier = Modifier.clickable(onClick = onViewLink),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(task.statusLabel()),
                        style = MaterialTheme.typography.labelLarge,
                        color = task.result.color(),
                    )
                    if (task.status == TaskStatus.PENDING) {
                        StartButton(onClick = onStart, modifier = Modifier.padding(top = Spacing.Sm))
                    }
                }
                when (task.status) {
                    // A link that was only added: View full link and Remove sit in the menu; Start is below the status.
                    TaskStatus.PENDING ->
                        LinkActionsMenu(showRetry = false, onRetry = onRetry, onRemove = onRemove, onViewLink = onViewLink)
                    // Stopping a download is urgent, so it stays one tap away.
                    TaskStatus.QUEUED, TaskStatus.DOWNLOADING ->
                        IconButton(onClick = onCancel, colors = ActionColors.negativeIcon()) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    // Retry and Remove are occasional actions, so they sit behind the three-dot menu.
                    TaskStatus.FAILED, TaskStatus.CANCELLED ->
                        LinkActionsMenu(showRetry = true, onRetry = onRetry, onRemove = onRemove, onViewLink = onViewLink)
                    TaskStatus.COMPLETED ->
                        // A finished link can be run again to pick up anything new; only new files are fetched.
                        LinkActionsMenu(
                            showRetry = true,
                            retryLabel = if (task.result == TaskResult.PARTIAL) R.string.action_retry else R.string.action_check_new,
                            onRetry = onRetry,
                            onRemove = onRemove,
                            onViewLink = onViewLink,
                        )
                }
            }

            when (task.status) {
                TaskStatus.DOWNLOADING -> DownloadProgress(task)
                TaskStatus.COMPLETED -> LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
                else -> Unit
            }

            val summary = buildList {
                if (task.filesDownloaded > 0) {
                    add(pluralStringResource(R.plurals.files_saved, task.filesDownloaded, task.filesDownloaded))
                }
                if (task.failedFiles > 0) {
                    add(pluralStringResource(R.plurals.files_failed, task.failedFiles, task.failedFiles))
                }
            }
            if (summary.isNotEmpty()) {
                Text(summary.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            task.currentFile?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            task.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun TaskStatus.isFinished() =
    this == TaskStatus.COMPLETED || this == TaskStatus.FAILED || this == TaskStatus.CANCELLED

/** A link waiting in the download queue reads "Queued"; the other states read as their result. */
internal fun DownloadTask.statusLabel() = if (status == TaskStatus.QUEUED) R.string.status_queued else result.label()

internal fun TaskResult.label() = when (this) {
    TaskResult.READY -> R.string.status_ready
    TaskResult.IN_PROGRESS -> R.string.status_downloading
    TaskResult.COMPLETED -> R.string.status_completed
    TaskResult.PARTIAL -> R.string.status_partial
    TaskResult.FAILED -> R.string.status_failed
    TaskResult.CANCELLED -> R.string.status_cancelled
}

@Composable
private fun TaskResult.color() = when (this) {
    TaskResult.READY -> Tone.Blue.icon
    TaskResult.FAILED -> MaterialTheme.colorScheme.error
    TaskResult.PARTIAL -> MaterialTheme.colorScheme.tertiary
    TaskResult.COMPLETED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Determinate when gallery-dl reports sizes or an album count; indeterminate until then. */
@Composable
private fun DownloadProgress(task: DownloadTask) {
    val target = task.overallProgress
    if (target == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    val progress by animateFloatAsState(target, label = "progress")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.progress_percent, (progress * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            if (task.fileIndex > 0) {
                Text(
                    if (task.totalFiles > 0) {
                        stringResource(R.string.progress_file_of, task.fileIndex, task.totalFiles)
                    } else {
                        stringResource(R.string.progress_file, task.fileIndex)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
