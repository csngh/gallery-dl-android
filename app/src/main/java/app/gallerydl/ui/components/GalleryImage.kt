package app.gallerydl.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import app.gallerydl.ui.theme.Tone
import coil3.compose.SubcomposeAsyncImage

/** An image from shared storage: a shimmer while it loads and a tile if it cannot be decoded. */
@Composable
fun GalleryImage(
    uri: Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showShimmer: Boolean = true,
) {
    SubcomposeAsyncImage(
        model = uri,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { if (showShimmer) ShimmerBox(Modifier.fillMaxSize(), RectangleShape) },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IconTile(Icons.Filled.BrokenImage, Tone.Amber, size = TileSize.Large)
            }
        },
    )
}
