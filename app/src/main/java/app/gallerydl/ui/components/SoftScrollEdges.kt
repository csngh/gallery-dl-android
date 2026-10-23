package app.gallerydl.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the top and bottom edges of a scrolling area to transparent, so content never ends in a hard
 * cut-off line. An edge only fades while there is more content in that direction, and eases in and out.
 */
@Composable
fun Modifier.softScrollEdges(state: ScrollableState, fade: Dp = 28.dp): Modifier {
    val top by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(200), label = "fadeTop")
    val bottom by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(200), label = "fadeBottom")
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val height = fade.toPx()
            if (top > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 1f - top), Color.Black),
                        startY = 0f,
                        endY = height,
                    ),
                    size = Size(size.width, height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottom > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black, Color.Black.copy(alpha = 1f - bottom)),
                        startY = size.height - height,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - height),
                    size = Size(size.width, height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
