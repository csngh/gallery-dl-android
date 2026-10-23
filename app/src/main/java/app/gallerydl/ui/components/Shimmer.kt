package app.gallerydl.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gallerydl.ui.theme.Spacing

/**
 * A soft placeholder that stands in for content that is still loading. The sweep is skipped, leaving a
 * still block, when the system animation scale is set to zero.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, Color.White, 0.08f)
    val progress = if (animate) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmerProgress",
        )
    } else {
        null
    }
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                // The state is read here, in the draw phase, so each frame redraws without recomposing.
                val width = size.width.coerceAtLeast(1f)
                val start = -width + (progress?.value ?: 0.5f) * 3f * width
                drawRect(
                    if (progress == null) {
                        Brush.horizontalGradient(listOf(base, base))
                    } else {
                        Brush.horizontalGradient(listOf(base, highlight, base), startX = start, endX = start + width)
                    },
                )
            },
    )
}

/** A few placeholder rows for list screens. */
@Composable
fun ShimmerCardList(count: Int = 3, cardHeight: Dp = 96.dp) {
    Column(Modifier.padding(Spacing.Md), verticalArrangement = Arrangement.spacedBy(Spacing.Md)) {
        repeat(count) { ShimmerBox(Modifier.fillMaxWidth().height(cardHeight)) }
    }
}

/** Placeholder squares for grid screens. */
@Composable
fun ShimmerGrid(minSize: Dp, count: Int, shape: Shape = RoundedCornerShape(16.dp), gap: Dp = Spacing.Md) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        contentPadding = PaddingValues(Spacing.Md),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        userScrollEnabled = false,
    ) {
        items(List(count) { it }) { ShimmerBox(Modifier.aspectRatio(1f), shape) }
    }
}
