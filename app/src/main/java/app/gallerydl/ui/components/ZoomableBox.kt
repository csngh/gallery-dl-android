package app.gallerydl.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val ZOOM_ANIMATION_MS = 280

/**
 * Pinch to zoom, drag to pan and double-tap to zoom in or out, for a picture. [onZoomedChange] tells the pager to stay
 * put while the picture is zoomed in, so a drag moves the picture instead of turning the page.
 */
@Composable
fun ZoomableBox(onZoomedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val onZoom by rememberUpdatedState(onZoomedChange)
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }

    fun clamp(value: Offset, s: Float): Offset {
        val limitX = maxOf(0f, size.width * (s - 1) / 2)
        val limitY = maxOf(0f, size.height * (s - 1) / 2)
        return Offset(value.x.coerceIn(-limitX, limitX), value.y.coerceIn(-limitY, limitY))
    }

    fun setScale(value: Float) {
        scale = value.coerceIn(0.8f, MAX_ZOOM)
        offset = clamp(offset, scale)
        onZoom(scale > 1.02f)
    }

    // Double-tap zooms glide to their end instead of jumping there.
    fun animateTo(target: Float) {
        animation?.cancel()
        val fromScale = scale
        val fromOffset = offset
        animation = scope.launch {
            animate(0f, 1f, animationSpec = tween(ZOOM_ANIMATION_MS, easing = FastOutSlowInEasing)) { t, _ ->
                scale = fromScale + (target - fromScale) * t
                offset = if (target > 1f) clamp(fromOffset * (1 - t), scale) else fromOffset * (1 - t)
                onZoom(scale > 1.02f)
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { animateTo(if (scale > 1.02f) 1f else 2.5f) })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    animation?.cancel()
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.count { it.pressed }
                        // Two fingers always zoom; one finger moves the picture only once it is zoomed in, otherwise
                        // the touch is left alone so the pager can turn the page.
                        if (pressed >= 2) pinched = true
                        if (pressed >= 2 || (pressed == 1 && scale > 1.02f)) {
                            val zoom = if (pressed >= 2) event.calculateZoom() else 1f
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                if (zoom != 1f) setScale(scale * zoom)
                                offset = clamp(offset + pan, scale)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    // Letting go of a pinch near normal size glides back to normal (not after a tap, which has its own zoom).
                    if (pinched && scale < 1.05f) animateTo(1f)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
