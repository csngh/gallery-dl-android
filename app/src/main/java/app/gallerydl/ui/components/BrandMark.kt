package app.gallerydl.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gallerydl.R
import app.gallerydl.ui.theme.BrandColors
import app.gallerydl.ui.theme.Spacing

/** The app's mark: the launcher icon artwork on the brand gradient. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = TileSize.Large) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(BrandColors.Violet, BrandColors.Teal))),
        contentAlignment = Alignment.Center,
    ) {
        // The artwork sits inside the adaptive-icon safe zone, so it is drawn larger than the tile and clipped.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.3f),
        )
    }
}
