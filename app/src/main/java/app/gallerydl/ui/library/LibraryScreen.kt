package app.gallerydl.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gallerydl.R
import app.gallerydl.data.FolderSummary
import app.gallerydl.download.folderSubtitle
import app.gallerydl.download.folderTitle
import app.gallerydl.data.LibraryItem
import app.gallerydl.ui.AppViewModelProvider
import app.gallerydl.download.PreviewWorker
import app.gallerydl.ui.components.ConfirmDialog
import app.gallerydl.ui.components.VideoViewer
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.theme.Spacing
import app.gallerydl.ui.components.EmptyState
import app.gallerydl.ui.components.IconTile
import app.gallerydl.ui.components.ScreenTitle
import app.gallerydl.ui.components.TileSize
import app.gallerydl.ui.theme.Tone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import app.gallerydl.ui.components.CachedThumbnail
import app.gallerydl.ui.components.GalleryImage
import app.gallerydl.ui.components.LocalThumbnailCache
import app.gallerydl.ui.components.LivePreview
import app.gallerydl.ui.components.PreviewProgressBanner
import app.gallerydl.ui.components.LocalPreviewHost
import app.gallerydl.ui.components.LocalPreviewStrips
import app.gallerydl.ui.components.rememberPreviewHost
import app.gallerydl.ui.components.ShimmerGrid
import app.gallerydl.ui.components.softScrollEdges
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.unit.Dp
import app.gallerydl.ui.components.TwoPaneMinWidth
import app.gallerydl.ui.components.readableWidth

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val openFolder by viewModel.openFolder.collectAsStateWithLifecycle()

    val livePreviews by viewModel.livePreviews.collectAsStateWithLifecycle()
    val previewHost = rememberPreviewHost(enabledBySetting = livePreviews)

    // The preview clips of the whole library are made by a background job, which carries on after the library is left;
    // the bar here shows how far it has got. Opening the library makes sure the job is running.
    val allVideos by viewModel.videosByFolder.collectAsStateWithLifecycle()
    val videoCount = allVideos.values.sumOf { it.size }
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(livePreviews, videoCount) {
        if (livePreviews) PreviewWorker.enqueue(appContext) else PreviewWorker.cancel(appContext)
    }

    // Files can be deleted in other apps while we are in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
        previewHost.refreshSystemState()
        previewHost.appVisible = true
    }
    // Previews stop as soon as the app is no longer in front, and start again when it returns.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { previewHost.appVisible = false }
    BackHandler(enabled = openFolder != null, onBack = viewModel::back)

    val folder = openFolder
    // The full-screen viewer lives here, above the choice between the one-pane and two-pane layout. Turning a phone to
    // landscape switches that layout and rebuilds the folder screen; the viewer, and the video playing in it, must not.
    val viewerItems by viewModel.items.collectAsStateWithLifecycle()
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(folder?.id) { viewerIndex = null }
    // Playing previews behind the full-screen viewer would only waste memory.
    LaunchedEffect(viewerIndex) { previewHost.viewerOpen = viewerIndex != null }
    CompositionLocalProvider(
        LocalPreviewHost provides previewHost,
        LocalThumbnailCache provides viewModel.thumbnails,
        LocalPreviewStrips provides viewModel.previewStrips,
    ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            // Wide windows: the folders stay on the left and the open folder's files fill the right.
            Row(Modifier.fillMaxSize()) {
                FoldersContent(
                    viewModel = viewModel,
                    modifier = Modifier.weight(0.2f).fillMaxHeight(),
                    folderMinSize = 140.dp,
                    selectedFolderId = folder?.id,
                    singleColumn = true,
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (folder == null) {
                    Box(Modifier.weight(0.8f).fillMaxHeight()) {
                        EmptyState(
                            R.string.library_select_title,
                            R.string.library_select_body,
                            Icons.Filled.Folder,
                            Tone.Violet,
                        )
                    }
                } else {
                    FolderContent(viewModel, folder, Modifier.weight(0.8f).fillMaxHeight(), showBack = false, onOpenViewer = { viewerIndex = it })
                }
            }
        } else if (folder == null) {
            FoldersContent(viewModel, Modifier.readableWidth())
        } else {
            FolderContent(viewModel, folder, Modifier.readableWidth(), showBack = true, onOpenViewer = { viewerIndex = it })
        }
    }
    val openIndex = viewerIndex
    val viewable = viewerItems
    if (openIndex != null && viewable != null) {
        Viewer(
            items = viewable,
            startIndex = openIndex,
            onDismiss = { viewerIndex = null },
            onDelete = viewModel::deleteItem,
        )
    }
    }
}


// region Folder list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoldersContent(
    viewModel: LibraryViewModel,
    modifier: Modifier,
    folderMinSize: Dp = 150.dp,
    selectedFolderId: Long? = null,
    /** The side list of the wide layout shows one folder per row. */
    singleColumn: Boolean = false,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val videosByFolder by viewModel.videosByFolder.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val listColumns by viewModel.libraryColumns.collectAsStateWithLifecycle()
    var folderToDelete by remember { mutableStateOf<FolderSummary?>(null) }
    var folderActions by remember { mutableStateOf<FolderSummary?>(null) }
    var folderToTag by remember { mutableStateOf<FolderSummary?>(null) }
    val foldersGridState = rememberLazyGridState()

    Column(modifier) {
        TopAppBar(
            title = { ScreenTitle(stringResource(R.string.nav_library)) },
            // The side list of the wide layout is always one column, so it has nothing to choose.
            actions = { if (!singleColumn) ColumnsMenu(columns = listColumns, onSelect = viewModel::setLibraryColumns, options = 2..3) },
        )
        PreviewProgressBanner(viewModel.previewStrips)
        // The search box is hidden for now; SearchField and the view-model search stay for when it comes back.
        if (SEARCH_VISIBLE) SearchField(query, viewModel::onQueryChange)

        val current = folders
        when {
            current == null -> ShimmerGrid(minSize = folderMinSize, count = 4, shape = MaterialTheme.shapes.medium)
            current.isEmpty() -> {
                if (query.isNotBlank()) {
                    EmptyState(R.string.library_no_results, R.string.library_no_results_body, Icons.Filled.Search, Tone.Violet)
                } else {
                    EmptyState(R.string.library_empty_title, R.string.library_empty_body, Icons.Filled.PhotoLibrary, Tone.Violet)
                }
            }
            else -> LazyVerticalGrid(
                state = foldersGridState,
                modifier = Modifier.softScrollEdges(foldersGridState),
                columns = GridCells.Fixed(if (singleColumn) 1 else listColumns),
                contentPadding = PaddingValues(Spacing.Md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            ) {
                items(current, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        videos = videosByFolder[folder.id].orEmpty(),
                        selected = folder.id == selectedFolderId,
                        onClick = { viewModel.open(folder) },
                        onLongClick = { folderActions = folder },
                    )
                }
            }
        }
    }

    folderActions?.let { folder ->
        FolderActionsDialog(
            title = folder.displayName,
            onTag = {
                folderToTag = folder
                folderActions = null
            },
            onDelete = {
                folderToDelete = folder
                folderActions = null
            },
            onDismiss = { folderActions = null },
        )
    }

    folderToTag?.let { folder ->
        TagDialog(
            initial = folder.tag,
            onSave = { tag ->
                viewModel.setFolderTag(folder.id, tag)
                folderToTag = null
            },
            onDismiss = { folderToTag = null },
        )
    }

    folderToDelete?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.delete_folder_title),
            confirmLabel = stringResource(R.string.action_delete),
            message = pluralStringResource(R.plurals.delete_folder_message, folder.itemCount, folder.itemCount),
            onConfirm = {
                viewModel.deleteFolder(folder.id)
                folderToDelete = null
            },
            onDismiss = { folderToDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: FolderSummary,
    videos: List<LibraryItem>,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box {
            val cover = folder.coverUri
            val coverId = folder.coverId
            if (cover != null && coverId != null && folder.coverMime.isVisualMime()) {
                val coverItem = remember(coverId, cover) {
                    LibraryItem(id = coverId, contentUri = cover, displayName = "", mimeType = folder.coverMime.orEmpty(), sourceUrl = "", taskId = null, addedAt = 0)
                }
                CachedThumbnail(coverItem, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                IconTile(Icons.Filled.Folder, Tone.Violet, Modifier.align(Alignment.Center), size = TileSize.Hero)
            }
            // A folder with videos plays short pieces of a few of them, picked at random, over its cover.
            LivePreview(key = "folder-${folder.id}", clips = videos, modifier = Modifier.fillMaxSize())
            // Name and count on the bottom strip. Long-pressing the card tags or deletes the folder.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(start = Spacing.Md, end = Spacing.Md, top = Spacing.Lg, bottom = Spacing.Md),
            ) {
                Text(
                    folder.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // With a tag as the title, the folder's own name follows the count.
                val count = pluralStringResource(R.plurals.folder_items, folder.itemCount, folder.itemCount)
                Text(
                    folderSubtitle(folder.displayName, folder.tag)?.let { "$count · $it" } ?: count,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// endregion

// region Folder contents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderContent(viewModel: LibraryViewModel, folder: OpenFolder, modifier: Modifier, showBack: Boolean, onOpenViewer: (Int) -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.itemQuery.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val itemsGridState = rememberLazyGridState()
    val selecting = selection.isNotEmpty()
    val columns by viewModel.folderColumns.collectAsStateWithLifecycle()
    val tag by viewModel.openFolderTag.collectAsStateWithLifecycle()
    var tagging by remember { mutableStateOf(false) }

    Column(modifier) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        if (selecting) stringResource(R.string.selected_count, selection.size) else folderTitle(folder.name, tag),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The folder's own name sits under its tag.
                    val subtitle = if (selecting) null else folderSubtitle(folder.name, tag)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                if (selecting) {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                } else if (showBack) {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            },
            actions = {
                if (selecting) {
                    IconButton(onClick = { confirmDelete = true }, colors = ActionColors.negativeIcon()) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                } else {
                    IconButton(onClick = { tagging = true }) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.action_tag_folder))
                    }
                    ColumnsMenu(columns = columns, onSelect = viewModel::setFolderColumns)
                }
            },
        )
        PreviewProgressBanner(viewModel.previewStrips)
        if (SEARCH_VISIBLE && !selecting) SearchField(query, viewModel::onItemQueryChange)

        val current = items
        when {
            current == null -> ShimmerGrid(minSize = 110.dp, count = 12, shape = MaterialTheme.shapes.small, gap = Spacing.Sm)
            current.isEmpty() -> EmptyState(R.string.library_no_results, R.string.library_no_results_body, Icons.Filled.Search, Tone.Violet)
            else -> LazyVerticalGrid(
                state = itemsGridState,
                modifier = Modifier.softScrollEdges(itemsGridState),
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(Spacing.Md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                items(current, key = { it.id }) { item ->
                    Thumbnail(
                        item = item,
                        selected = item.id in selection,
                        onClick = {
                            if (selecting) viewModel.toggleSelected(item.id) else onOpenViewer(current.indexOf(item))
                        },
                        onLongClick = { viewModel.toggleSelected(item.id) },
                    )
                }
            }
        }
    }

    if (tagging) {
        TagDialog(
            initial = tag,
            onSave = {
                viewModel.setFolderTag(folder.id, it)
                tagging = false
            },
            onDismiss = { tagging = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_selected_title),
            confirmLabel = stringResource(R.string.action_delete),
            message = pluralStringResource(R.plurals.delete_selected_message, selection.size, selection.size),
            onConfirm = {
                viewModel.deleteSelected()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** What long-pressing a folder offers: give it a tag, or delete it. */
@Composable
private fun FolderActionsDialog(title: String, onTag: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(onClick = onTag) { Text(stringResource(R.string.action_tag_folder)) }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = ActionColors.Negative)) {
                    Text(stringResource(R.string.action_delete_folder))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/** Type the tag of a folder; it shows as "name / tag". Saving an empty tag takes the tag off. */
@Composable
private fun TagDialog(initial: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_folder_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tag_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                colors = ButtonDefaults.buttonColors(containerColor = ActionColors.Positive, contentColor = ActionColors.OnPositive),
            ) { Text(stringResource(if (text.isBlank() && !initial.isNullOrBlank()) R.string.action_remove_tag else R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/** The grid button in a folder's top bar: choose how many columns the files are shown in (2 to 4). */
@Composable
private fun ColumnsMenu(columns: Int, onSelect: (Int) -> Unit, options: IntRange = 2..4) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.GridView, contentDescription = stringResource(R.string.action_columns))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { count ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.columns_option, count)) },
                    trailingIcon = {
                        if (count == columns) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        expanded = false
                        onSelect(count)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumbnail(item: LibraryItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.mimeType.isVisualMime()) {
                CachedThumbnail(item, item.displayName, Modifier.fillMaxSize())
                if (item.mimeType.startsWith("video/")) {
                    // Every video plays a silent live preview when it is on screen and has a turn.
                    LivePreview(key = "item-${item.id}", clips = listOf(item), modifier = Modifier.fillMaxSize())
                    if (LocalPreviewHost.current?.enabledBySetting == true) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.Xs).size(20.dp),
                            tint = Color.White.copy(alpha = 0.9f),
                        )
                    } else {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White)
                    }
                }
            } else {
                IconTile(Icons.AutoMirrored.Filled.InsertDriveFile, Tone.Blue, size = TileSize.Large, contentDescription = item.displayName)
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.Sm),
                    tint = Color.White,
                )
            }
        }
    }
}

// endregion

// region Full-screen viewer

@Composable
private fun Viewer(items: List<LibraryItem>, startIndex: Int, onDismiss: () -> Unit, onDelete: (Long) -> Unit) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val current = items.getOrNull(pagerState.currentPage)
    var itemToDelete by remember { mutableStateOf<LibraryItem?>(null) }
    // A zoomed-in video is dragged around, and a video is scrubbed by swiping, so the pager stays put on those pages.
    var zoomed by remember { mutableStateOf(false) }
    // The touch lock of the video player: while on, the back button and the top bar are off as well.
    var locked by remember { mutableStateOf(false) }
    val onPlayablePage = current?.mimeType?.let { it.startsWith("video/") || it.startsWith("audio/") } == true
    // Closing the viewer gives the screen back to the system's own rotation (a video may have turned it to landscape).
    androidx.compose.runtime.DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        onDispose { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !locked),
    ) {
        // Let the dialog window cover the status bar too, and drop the dim layer.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setDimAmount(0f)
                it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // Without these the window is placed inside the area between the system bars and ends up pushed down,
                // so its bottom (the video controls) runs off the screen.
                val screen = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                it.setFlags(screen, screen)
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                pagerState,
                Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed && !onPlayablePage,
                key = { items[it].id },
            ) { page ->
                ViewerPage(
                    items[page],
                    active = page == pagerState.currentPage,
                    onZoomedChange = { zoomed = it },
                    onOpen = { openExternally(context, items[page]) },
                    locked = locked,
                    onLockedChange = { locked = it },
                )
            }
            itemToDelete?.let { item ->
                ConfirmDialog(
                    title = stringResource(R.string.delete_item_title),
                    message = stringResource(R.string.delete_item_message),
                    confirmLabel = stringResource(R.string.action_delete),
                    onConfirm = {
                        onDelete(item.id)
                        itemToDelete = null
                    },
                    onDismiss = { itemToDelete = null },
                )
            }
            if (!locked) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .padding(horizontal = Spacing.Xs, vertical = Spacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    IconButton(onClick = onDismiss, colors = tint) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(
                        current?.displayName.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current != null) {
                        // Open and Delete share one menu: deleting a file is not a one-tap icon beside a harmless one.
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }, colors = tint) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_open_with)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        openExternally(context, current)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete), color = ActionColors.Negative) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = ActionColors.Negative) },
                                    onClick = {
                                        menuOpen = false
                                        itemToDelete = current
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerPage(item: LibraryItem, active: Boolean, onZoomedChange: (Boolean) -> Unit, onOpen: () -> Unit, locked: Boolean, onLockedChange: (Boolean) -> Unit) {
    if (item.mimeType.startsWith("video/") || item.mimeType.startsWith("audio/")) {
        VideoViewer(Uri.parse(item.contentUri), isAudio = item.mimeType.startsWith("audio/"), active = active, onZoomedChange = onZoomedChange, onOpen = onOpen, locked = locked, onLockedChange = onLockedChange)
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (item.mimeType.isVisualMime()) {
            GalleryImage(
                Uri.parse(item.contentUri),
                item.displayName,
                Modifier.fillMaxSize(),
                ContentScale.Fit,
                showShimmer = false,
            )
        }
        // Videos and other files are played or opened by the user's own apps.
        if (!item.mimeType.startsWith("image/")) {
            IconButton(onClick = onOpen, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (item.mimeType.startsWith("video/")) Icons.Filled.PlayCircle else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = stringResource(R.string.action_open),
                    modifier = Modifier.size(64.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

// endregion

private const val SEARCH_VISIBLE = false

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
        label = { Text(stringResource(R.string.search_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
    )
}

private fun String?.isVisualMime() = this != null && (startsWith("image/") || startsWith("video/"))

private fun openExternally(context: Context, item: LibraryItem) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(item.contentUri), item.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}
