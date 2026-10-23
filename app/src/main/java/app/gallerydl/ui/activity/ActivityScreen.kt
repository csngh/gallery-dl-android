package app.gallerydl.ui.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import app.gallerydl.R
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.TaskResult
import app.gallerydl.data.result
import app.gallerydl.download.linkFolderName
import app.gallerydl.ui.AppViewModelProvider
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.queue.label
import app.gallerydl.ui.theme.Spacing
import app.gallerydl.ui.components.EmptyState
import app.gallerydl.ui.components.IconTile
import app.gallerydl.ui.components.ScreenTitle
import app.gallerydl.ui.components.TileSize
import app.gallerydl.ui.components.tile
import app.gallerydl.ui.theme.Tone
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Flag
import app.gallerydl.ui.components.ShimmerCardList
import app.gallerydl.ui.components.softScrollEdges
import androidx.compose.foundation.lazy.rememberLazyListState
import app.gallerydl.ui.components.readableWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(viewModel: ActivityViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    Column(Modifier.readableWidth()) {
        TopAppBar(
            title = { ScreenTitle(stringResource(R.string.nav_activity)) },
            actions = {
                if (stats.retryable > 0) {
                    TextButton(
                        onClick = viewModel::retryAllFailed,
                        colors = ButtonDefaults.textButtonColors(contentColor = ActionColors.Positive),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(
                            stringResource(R.string.action_retry_failed, stats.retryable),
                            modifier = Modifier.padding(start = Spacing.Sm),
                        )
                    }
                }
            },
        )

        if (!state.loaded) {
            ShimmerCardList(count = 4)
            return@Column
        }

        if (stats.links == 0) {
            EmptyState(R.string.activity_empty_title, R.string.activity_empty_body, Icons.Filled.Insights, Tone.Teal)
            return@Column
        }

        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.softScrollEdges(listState),
            contentPadding = PaddingValues(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    // Totals first, then how the links turned out; three cards per row keep every label readable.
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        StatCard(stats.links, R.string.stat_links, Icons.Filled.Link, Tone.Blue, Modifier.weight(1f))
                        StatCard(stats.filesSaved, R.string.stat_files_saved, Icons.Filled.SaveAlt, Tone.Green, Modifier.weight(1f))
                        StatCard(stats.filesFailed, R.string.stat_files_failed, Icons.Filled.BrokenImage, Tone.Red, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        StatCard(stats.completed, R.string.stat_completed, Icons.Filled.CheckCircle, Tone.Green, Modifier.weight(1f))
                        StatCard(stats.partial, R.string.stat_partial, Icons.Filled.Warning, Tone.Amber, Modifier.weight(1f))
                        StatCard(stats.failed, R.string.stat_failed, Icons.Filled.Error, Tone.Red, Modifier.weight(1f))
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    items(ActivityFilter.entries) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(stringResource(filter.label())) },
                        )
                    }
                }
            }
            items(state.tasks, key = { it.id }) { task ->
                LinkRow(task, onRetry = { viewModel.retry(task.id) })
            }
        }
    }
}

@Composable
private fun StatCard(value: Int, label: Int, icon: ImageVector, tone: Tone, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier
                // A 3 px edge in the tone along the top; the card clips it to its rounded corners.
                .drawBehind { drawRect(tone.icon, size = Size(size.width, 3.dp.toPx())) }
                .padding(Spacing.Md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
                IconTile(icon, tone, size = TileSize.Small)
            }
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LinkRow(task: DownloadTask, onRetry: () -> Unit) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val result = task.result
    val canRetry = result == TaskResult.FAILED || result == TaskResult.PARTIAL || result == TaskResult.CANCELLED
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    val summary = buildList {
        if (task.filesDownloaded > 0) {
            add(pluralStringResource(R.plurals.files_saved, task.filesDownloaded, task.filesDownloaded))
        }
        if (task.failedFiles > 0) {
            add(pluralStringResource(R.plurals.files_failed, task.failedFiles, task.failedFiles))
        }
    }

    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.clickable { expanded = !expanded }.padding(Spacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (statusIcon, statusTone) = result.tile()
                IconTile(statusIcon, statusTone, Modifier.padding(end = Spacing.Md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                    Text(
                        task.url,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        Text(stringResource(result.label()), style = MaterialTheme.typography.labelLarge, color = result.color())
                        if (summary.isNotEmpty()) {
                            Text(
                                summary.joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    modifier = Modifier.padding(start = Spacing.Sm).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = Spacing.Md), verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailLine(Icons.Filled.Folder, Tone.Blue, R.string.detail_folder, linkFolderName(task.url))
                    DetailLine(Icons.Filled.Schedule, Tone.Violet, R.string.detail_added, formatTime(task.createdAt))
                    task.finishedAt?.let {
                        DetailLine(Icons.Filled.Flag, Tone.Teal, R.string.detail_finished, formatTime(it))
                    }
                    DetailLine(Icons.Filled.SaveAlt, Tone.Green, R.string.stat_files_saved, task.filesDownloaded.toString())
                    DetailLine(Icons.Filled.BrokenImage, Tone.Red, R.string.stat_files_failed, task.failedFiles.toString())
                    task.error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (canRetry) {
                        OutlinedButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionColors.Positive),
                            border = BorderStroke(1.dp, ActionColors.Positive),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Text(stringResource(R.string.action_retry), modifier = Modifier.padding(start = Spacing.Sm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, tone: Tone, label: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Md), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tone, size = TileSize.Small)
        Text(
            stringResource(label),
            modifier = Modifier.width(DetailLabelWidth),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

private val DetailLabelWidth = 72.dp

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
private fun TaskResult.color() = when (this) {
    TaskResult.FAILED -> MaterialTheme.colorScheme.error
    TaskResult.PARTIAL -> MaterialTheme.colorScheme.tertiary
    TaskResult.COMPLETED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ActivityFilter.label() = when (this) {
    ActivityFilter.ALL -> R.string.filter_all
    ActivityFilter.COMPLETED -> R.string.stat_completed
    ActivityFilter.PARTIAL -> R.string.stat_partial
    ActivityFilter.FAILED -> R.string.stat_failed
}
