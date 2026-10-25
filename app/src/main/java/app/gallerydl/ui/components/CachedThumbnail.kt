package app.gallerydl.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.RectangleShape
import app.gallerydl.data.LibraryItem
import app.gallerydl.data.ThumbnailCache
import app.gallerydl.ui.theme.Tone
import coil3.compose.SubcomposeAsyncImage
import java.io.File

val LocalThumbnailCache = compositionLocalOf<ThumbnailCache?> { null }

/**
 * A library picture or video as a still thumbnail from the persistent [ThumbnailCache]. The first time a file is
 * seen its thumbnail is made and saved; every time after that it is a quick read of a small file.
 */
@Composable
fun CachedThumbnail(item: LibraryItem, contentDescription: String?, modifier: Modifier = Modifier) {
    val cache = LocalThumbnailCache.current
    if (cache == null) {
        // No cache available (for example in a preview): fall back to decoding the original.
        GalleryImage(android.net.Uri.parse(item.contentUri), contentDescription, modifier)
        return
    }
    val state by produceState<ThumbnailState>(ThumbnailState.Loading, item.id) {
        value = cache.obtain(item)?.let { ThumbnailState.Ready(it) } ?: ThumbnailState.Failed
    }
    when (val current = state) {
        ThumbnailState.Loading -> ShimmerBox(modifier.fillMaxSize(), RectangleShape)
        ThumbnailState.Failed -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            IconTile(Icons.Filled.BrokenImage, Tone.Amber, size = TileSize.Large)
        }
        is ThumbnailState.Ready -> SubcomposeAsyncImage(
            model = current.file,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private sealed interface ThumbnailState {
    data object Loading : ThumbnailState
    data object Failed : ThumbnailState
    data class Ready(val file: File) : ThumbnailState
}
