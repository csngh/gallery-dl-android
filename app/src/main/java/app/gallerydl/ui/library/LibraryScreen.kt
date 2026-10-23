package app.gallerydl.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
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
import app.gallerydl.data.LibraryItem
import app.gallerydl.ui.AppViewModelProvider
import app.gallerydl.ui.components.ConfirmDialog
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.theme.Spacing
import app.gallerydl.ui.components.EmptyState
import app.gallerydl.ui.components.IconTile
import app.gallerydl.ui.components.ScreenTitle
import app.gallerydl.ui.components.TileSize
import app.gallerydl.ui.theme.Tone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import app.gallerydl.ui.components.GalleryImage
import app.gallerydl.ui.components.ShimmerGrid
import app.gallerydl.ui.components.softScrollEdges
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.unit.Dp
import app.gallerydl.ui.components.TwoPaneMinWidth
import app.gallerydl.ui.components.readableWidth

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val openFolder by viewModel.openFolder.collectAsStateWithLifecycle()

    // Files can be deleted in other apps while we are in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    BackHandler(enabled = openFolder != null, onBack = viewModel::back)

    val folder = openFolder
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            // Wide windows: the folders stay on the left and the open folder's files fill the right.
            Row(Modifier.fillMaxSize()) {
                FoldersContent(
                    viewModel = viewModel,
                    modifier = Modifier.width(TwoPaneListWidth).fillMaxHeight(),
                    folderMinSize = 140.dp,
                    selectedFolderId = folder?.id,
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (folder == null) {
                    EmptyState(
                        R.string.library_select_title,
                        R.string.library_select_body,
                        Icons.Filled.Folder,
                        Tone.Violet,
                    )
                } else {
                    FolderContent(viewModel, folder, Modifier.weight(1f).fillMaxHeight(), showBack = false)
                }
            }
        } else if (folder == null) {
            FoldersContent(viewModel, Modifier.readableWidth())
        } else {
            FolderContent(viewModel, folder, Modifier.readableWidth(), showBack = true)
        }
    }
}

private val TwoPaneListWidth = 340.dp

// region Folder list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoldersContent(
    viewModel: LibraryViewModel,
    modifier: Modifier,
    folderMinSize: Dp = 150.dp,
    selectedFolderId: Long? = null,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var folderToDelete by remember { mutableStateOf<FolderSummary?>(null) }
    val foldersGridState = rememberLazyGridState()

    Column(modifier) {
        TopAppBar(title = { ScreenTitle(stringResource(R.string.nav_library)) })
        SearchField(query, viewModel::onQueryChange)

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
                columns = GridCells.Adaptive(folderMinSize),
                contentPadding = PaddingValues(Spacing.Md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            ) {
                items(current, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        selected = folder.id == selectedFolderId,
                        onClick = { viewModel.open(folder) },
                        onLongClick = { folderToDelete = folder },
                    )
                }
            }
        }
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
private fun FolderCard(folder: FolderSummary, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            if (cover != null && folder.coverMime.isVisualMime()) {
                GalleryImage(Uri.parse(cover), contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                IconTile(Icons.Filled.Folder, Tone.Violet, Modifier.align(Alignment.Center), size = TileSize.Hero)
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(start = Spacing.Md, end = Spacing.Md, top = Spacing.Lg, bottom = Spacing.Md),
            ) {
                Text(
                    folder.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.folder_items, folder.itemCount, folder.itemCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// endregion

// region Folder contents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderContent(viewModel: LibraryViewModel, folder: OpenFolder, modifier: Modifier, showBack: Boolean) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.itemQuery.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val itemsGridState = rememberLazyGridState()
    val selecting = selection.isNotEmpty()

    Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    if (selecting) stringResource(R.string.selected_count, selection.size) else folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                }
            },
        )
        if (!selecting) SearchField(query, viewModel::onItemQueryChange)

        val current = items
        when {
            current == null -> ShimmerGrid(minSize = 110.dp, count = 12, shape = MaterialTheme.shapes.small, gap = Spacing.Sm)
            current.isEmpty() -> EmptyState(R.string.library_no_results, R.string.library_no_results_body, Icons.Filled.Search, Tone.Violet)
            else -> LazyVerticalGrid(
                state = itemsGridState,
                modifier = Modifier.softScrollEdges(itemsGridState),
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(Spacing.Md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                items(current, key = { it.id }) { item ->
                    Thumbnail(
                        item = item,
                        selected = item.id in selection,
                        onClick = {
                            if (selecting) viewModel.toggleSelected(item.id) else viewerIndex = current.indexOf(item)
                        },
                        onLongClick = { viewModel.toggleSelected(item.id) },
                    )
                }
            }
        }

        val index = viewerIndex
        if (index != null && current != null) {
            Viewer(
                items = current,
                startIndex = index,
                onDismiss = { viewerIndex = null },
                onDelete = viewModel::deleteItem,
            )
        }
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
                GalleryImage(Uri.parse(item.contentUri), item.displayName, Modifier.fillMaxSize())
                if (item.mimeType.startsWith("video/")) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White)
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Let the dialog window cover the status bar too, and drop the dim layer.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setDimAmount(0f)
                it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pagerState, Modifier.fillMaxSize(), key = { items[it].id }) { page ->
                ViewerPage(items[page], onOpen = { openExternally(context, items[page]) })
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
                    IconButton(onClick = { openExternally(context, current) }, colors = tint) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.action_open))
                    }
                    IconButton(onClick = { itemToDelete = current }, colors = ActionColors.negativeIcon()) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerPage(item: LibraryItem, onOpen: () -> Unit) {
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
