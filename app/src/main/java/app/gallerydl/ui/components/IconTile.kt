package app.gallerydl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gallerydl.data.TaskResult
import app.gallerydl.ui.theme.Spacing
import app.gallerydl.ui.theme.Tone
import androidx.compose.material.icons.filled.PlayCircle

object TileSize {
    val Small = 28.dp
    val Default = 36.dp
    val Large = 44.dp
    val Hero = 72.dp
}

/** An icon on a tinted rounded square. Corner radius is 8 for small tiles, 12 up to 44, 16 from 48 up. */
@Composable
fun IconTile(
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    size: Dp = TileSize.Default,
    contentDescription: String? = null,
) {
    val radius = when {
        size >= 48.dp -> 16.dp
        size >= 36.dp -> 12.dp
        else -> 8.dp
    }
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(radius)).background(tone.tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.55f), tint = tone.icon)
    }
}

/** A screen title: plain text, large and bold, with no icon beside it. */
@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/** Icon and tone that always stand for a download result. */
fun TaskResult.tile(): Pair<ImageVector, Tone> = when (this) {
    TaskResult.READY -> Icons.Filled.PlayCircle to Tone.Blue
    TaskResult.IN_PROGRESS -> Icons.Filled.Schedule to Tone.Violet
    TaskResult.COMPLETED -> Icons.Filled.CheckCircle to Tone.Green
    TaskResult.PARTIAL -> Icons.Filled.Warning to Tone.Amber
    TaskResult.FAILED -> Icons.Filled.Error to Tone.Red
    TaskResult.CANCELLED -> Icons.Filled.Block to Tone.Pink
}
