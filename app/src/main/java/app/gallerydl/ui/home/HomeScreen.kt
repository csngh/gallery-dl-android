package app.gallerydl.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gallerydl.R
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.TaskResult
import app.gallerydl.data.TaskStatus
import app.gallerydl.data.overallProgress
import app.gallerydl.data.result
import app.gallerydl.ui.AppViewModelProvider
import app.gallerydl.ui.components.ScreenTitle
import app.gallerydl.ui.components.EmptyState
import app.gallerydl.ui.components.IconTile
import app.gallerydl.ui.components.LinkActionsMenu
import app.gallerydl.ui.components.LinkField
import app.gallerydl.ui.components.StorageWarningBanner
import app.gallerydl.ui.components.PendingAction
import app.gallerydl.ui.components.PendingActionDialog
import app.gallerydl.ui.components.ShimmerCardList
import app.gallerydl.ui.components.TileSize
import app.gallerydl.ui.components.readableWidth
import app.gallerydl.ui.components.softScrollEdges
import app.gallerydl.ui.components.tile
import app.gallerydl.ui.queue.label
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.theme.Spacing
import app.gallerydl.ui.theme.Tone
import androidx.compose.foundation.background
import app.gallerydl.ui.components.FullLinkDialog
import app.gallerydl.ui.components.SiteIcon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.Dp
import app.gallerydl.ui.components.StartButton
import app.gallerydl.ui.components.StartAllButton
import app.gallerydl.ui.components.StopAllButton
import app.gallerydl.ui.queue.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var fullLink by remember { mutableStateOf<String?>(null) }
    val folderNames by viewModel.folderNames.collectAsStateWithLifecycle()
    var folderAsk by remember { mutableStateOf<app.gallerydl.data.DownloadTask?>(null) }

    val loaded = groups
    val context = LocalContext.current
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportTo(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(context.contentResolver, uri)
    }
    val exportName = stringResource(R.string.export_file_name)
    val nothingToExport = stringResource(R.string.export_empty)

    Column(Modifier.readableWidth()) {
        TopAppBar(
            title = { ScreenTitle(stringResource(R.string.nav_home)) },
            actions = {
                IconButton(onClick = viewModel::renewAll) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_renew))
                }
                LinksMenu(
                    onExport = {
                        if (loaded.isNullOrEmpty()) {
                            Toast.makeText(context, nothingToExport, Toast.LENGTH_SHORT).show()
                        } else {
                            exportLauncher.launch(exportName)
                        }
                    },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
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

        StopAllButton(
            count = loaded?.sumOf { it.inProgress } ?: 0,
            onClick = viewModel::stopAll,
            modifier = Modifier.padding(horizontal = Spacing.Md),
        )
        StartAllButton(
            count = loaded?.sumOf { it.ready } ?: 0,
            onClick = viewModel::startAll,
            modifier = Modifier.padding(horizontal = Spacing.Md),
        )
        if (loaded == null) {
            ShimmerCardList()
        } else if (loaded.isEmpty()) {
            EmptyState(R.string.home_empty_title, R.string.home_empty_body, Icons.Filled.Language, Tone.Blue)
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.softScrollEdges(listState),
                contentPadding = PaddingValues(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            ) {
                items(loaded, key = { it.site }) { group ->
                    SiteGroupCard(
                        group = group,
                        folderNames = folderNames,
                        onCancel = { task ->
                            // A running download is confirmed first; a queued one can simply be retried later.
                            if (task.status == TaskStatus.DOWNLOADING) {
                                pending = PendingAction.CancelDownload(task.id)
                            } else {
                                viewModel.cancel(task.id)
                            }
                        },
                        onRetry = { viewModel.retry(it.id) },
                        onRemove = { pending = PendingAction.Remove(it.id) },
                        onEdit = viewModel::startEdit,
                        onStart = { folderAsk = it },
                        onViewLink = { fullLink = it.url },
                    )
                }
            }
        }
    }

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

    transfer?.let { TransferDialog(it, onDismiss = viewModel::dismissTransfer) }

    edit?.let { state ->
        EditLinkDialog(
            state = state,
            onTextChange = viewModel::onEditTextChange,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::dismissEdit,
        )
    }

    PendingActionDialog(
        action = pending,
        onClearFinished = {},
        onRemove = viewModel::remove,
        onCancelDownload = viewModel::cancel,
        onDismiss = { pending = null },
    )
}

@Composable
private fun SiteGroupCard(
    group: SiteGroup,
    folderNames: Map<String, String>,
    onCancel: (DownloadTask) -> Unit,
    onRetry: (DownloadTask) -> Unit,
    onRemove: (DownloadTask) -> Unit,
    onEdit: (DownloadTask) -> Unit,
    onViewLink: (DownloadTask) -> Unit,
    onStart: (DownloadTask) -> Unit,
) {
    var expanded by rememberSaveable(group.site) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(Spacing.Md)) {
            Row(
                Modifier.clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteTile(group.site, group.iconHost, Modifier.padding(end = Spacing.Md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                    Text(
                        group.site.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GroupSummary(group)
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    modifier = Modifier.padding(start = Spacing.Sm).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = Spacing.Sm)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    group.tasks.forEach { task ->
                        SiteLinkRow(task, task.folderName ?: folderNames[task.url], { onCancel(task) }, { onRetry(task) }, { onRemove(task) }, { onEdit(task) }, { onViewLink(task) }, { onStart(task) })
                    }
                }
            }
        }
    }
}

/** How many links, how many files, and what needs attention, in one quiet line. */
@Composable
private fun GroupSummary(group: SiteGroup) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val parts = buildList<Pair<String, Color>> {
            add(pluralStringResource(R.plurals.site_links, group.tasks.size, group.tasks.size) to muted)
            if (group.filesSaved > 0) {
                add(pluralStringResource(R.plurals.files_saved, group.filesSaved, group.filesSaved) to muted)
            }
            if (group.ready > 0) add(pluralStringResource(R.plurals.site_ready, group.ready, group.ready) to Tone.Blue.icon)
            if (group.inProgress > 0) add(stringResource(R.string.site_in_progress, group.inProgress) to Tone.Violet.icon)
            if (group.needAttention > 0) add(pluralStringResource(R.plurals.site_attention, group.needAttention, group.needAttention) to ActionColors.Negative)
        }
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                parts.forEachIndexed { index, (text, color) ->
                    if (index > 0) append(" · ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
                    append(text)
                    pop()
                }
            },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SiteLinkRow(
    task: DownloadTask,
    /** The library folder this link saves in; shown as the main text, with the link below it. */
    folderName: String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onViewLink: () -> Unit,
    onStart: () -> Unit,
) {
    val (icon, tone) = task.result.tile()
    val progress = task.overallProgress
    val status = if (task.status == TaskStatus.DOWNLOADING && progress != null) {
        stringResource(R.string.site_downloading_percent, (progress * 100).toInt())
    } else {
        stringResource(task.statusLabel())
    }
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tone, Modifier.padding(end = Spacing.Md), size = TileSize.Small)
        Column(Modifier.weight(1f)) {
            val link = task.url.removePrefix("https://").removePrefix("http://")
            // The folder's name tells which folder the menu on this row acts on; the link sits below it. Until a link has a
            // folder, the link is the main text.
            Text(
                folderName ?: link,
                modifier = Modifier.clickable(onClick = onViewLink),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (folderName != null) {
                Text(
                    link,
                    modifier = Modifier.clickable(onClick = onViewLink),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = tone.icon)
            if (task.status == TaskStatus.PENDING) StartButton(onClick = onStart, modifier = Modifier.padding(top = Spacing.Xs))
        }
        when (task.status) {
            // Not started yet: view the link, correct it, or remove it; Start sits under the status.
            TaskStatus.PENDING ->
                LinkActionsMenu(showRetry = false, onRetry = onRetry, onRemove = onRemove, onEdit = onEdit, onViewLink = onViewLink)
            TaskStatus.QUEUED, TaskStatus.DOWNLOADING ->
                IconButton(onClick = onCancel, colors = ActionColors.negativeIcon()) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            // A link that did not work can be corrected; finished links are left as they are.
            TaskStatus.FAILED, TaskStatus.CANCELLED ->
                LinkActionsMenu(showRetry = true, onRetry = onRetry, onRemove = onRemove, onEdit = onEdit, onViewLink = onViewLink)
            TaskStatus.COMPLETED ->
                // A finished link can be run again to pick up anything new; its memory of saved files means only new
                // files are fetched.
                LinkActionsMenu(
                    showRetry = true,
                    retryLabel = if (task.result == TaskResult.PARTIAL) R.string.action_retry else R.string.action_check_new,
                    onRetry = onRetry,
                    onRemove = onRemove,
                    onViewLink = onViewLink,
                )
        }
    }
}

/**
 * The website's own icon when it can be loaded; otherwise its coloured initial, where the same site always
 * gets the same colour.
 */
@Composable
internal fun SiteTile(site: String, iconHost: String?, modifier: Modifier = Modifier, size: Dp = TileSize.Large) {
    val tone = SiteTones[(site.hashCode() and Int.MAX_VALUE) % SiteTones.size]
    Box(
        modifier = modifier.size(size).clip(MaterialTheme.shapes.small).background(tone.tint),
        contentAlignment = Alignment.Center,
    ) {
        val initial: @Composable () -> Unit = {
            Text(
                site.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = tone.icon,
            )
        }
        if (iconHost == null) initial() else SiteIcon(iconHost, fallback = initial, modifier = Modifier.fillMaxSize())
    }
}

/** Tones with no fixed meaning, so a website never looks like a success or a failure. */
private val SiteTones = listOf(Tone.Blue, Tone.Violet, Tone.Teal, Tone.Pink)

/** "imgur" becomes "Imgur"; a host such as "upload.wikimedia.org" is shown as it is. */
internal fun String.displayName(): String =
    if ('.' in this) this else replaceFirstChar { it.uppercase() }

/** The three-dot button on Home: export every link to a text file, or import links from one. */
@Composable
private fun LinksMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_links_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_export_links)) },
                leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                onClick = {
                    expanded = false
                    onExport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_import_links)) },
                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                onClick = {
                    expanded = false
                    onImport()
                },
            )
        }
    }
}

/** Progress and the outcome of an export or an import. */
@Composable
private fun TransferDialog(state: HomeViewModel.Transfer, onDismiss: () -> Unit) {
    when (state) {
        is HomeViewModel.Transfer.Importing -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    Text(stringResource(R.string.import_progress, state.done, state.total))
                    LinearProgressIndicator(progress = { if (state.total == 0) 1f else state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )
        is HomeViewModel.Transfer.Imported -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_done_title)) },
            text = {
                val result = state.result
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    if (result.found == 0) Text(stringResource(R.string.import_none))
                    if (result.added > 0) Text(pluralStringResource(R.plurals.import_added, result.added, result.added))
                    if (result.duplicates > 0) Text(stringResource(R.string.import_duplicates, result.duplicates))
                    if (result.unsupported > 0) Text(stringResource(R.string.import_unsupported, result.unsupported))
                    if (result.notLinks > 0) Text(stringResource(R.string.import_not_links, result.notLinks))
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
        is HomeViewModel.Transfer.Exported -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.action_export_links)) },
            text = { Text(pluralStringResource(R.plurals.export_done, state.count, state.count)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
        HomeViewModel.Transfer.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.transfer_failed)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
