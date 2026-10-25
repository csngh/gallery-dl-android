package app.gallerydl.ui.components

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import app.gallerydl.R
import app.gallerydl.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Owns the player of the full-screen viewer: play and pause, seeking, and the playback speed. It behaves like the iOS
 * app's player. The speed is kept between videos and app launches.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Stable
class VideoController(context: Context) {
    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    var isPlaying by mutableStateOf(false); private set
    var current by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f); private set
    var unplayable by mutableStateOf(false); private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN); private set
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f).takeIf { it in SPEEDS } ?: 1f); private set
    var scrubbing = false
    private var lastScrub = 0L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && player.duration > 0) duration = player.duration / 1000f
            if (state == Player.STATE_ENDED) isPlaying = false
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) { this@VideoController.videoSize = videoSize }
        override fun onPlayerError(error: PlaybackException) { unplayable = true }
    }

    init { player.addListener(listener) }

    fun load(uri: Uri) {
        if (player.currentMediaItem != null) return
        player.setMediaItem(MediaItem.fromUri(uri))
        player.setPlaybackSpeed(speed)
        player.prepare()
    }

    /** Called often, to move the seek bar while playing. */
    fun tick() {
        if (!scrubbing && player.isPlaying) current = player.currentPosition / 1000f
    }

    fun play() {
        if (duration > 0 && current >= duration - 0.25f) seek(0f)
        player.play()
    }

    fun pause() = player.pause()

    fun toggle() = if (player.isPlaying) pause() else play()

    fun seek(seconds: Float) {
        current = seconds
        player.setSeekParameters(SeekParameters.EXACT)
        player.seekTo((seconds * 1000).toLong())
    }

    /** Moves the picture while a finger drags. The tolerance keeps it quick; the final seek is exact. */
    fun scrub(seconds: Float) {
        current = seconds
        // One seek every 40 ms is plenty for a smooth picture; more would queue up behind the decoder.
        val now = SystemClock.elapsedRealtime()
        if (now - lastScrub < 40) return
        lastScrub = now
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        player.seekTo((seconds * 1000).toLong())
    }

    fun chooseSpeed(value: Float) {
        speed = value
        prefs.edit().putFloat("speed", value).apply()
        player.setPlaybackSpeed(value)
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}

private const val MAX_SCALE = 5f

/**
 * The viewer's video (or audio) player: pinch to zoom, drag to pan, double-tap to zoom in or out, a sideways swipe or
 * the seek bar to scrub with a live picture, a speed menu, and play and pause. [onZoomedChange] tells the pager to stay
 * put while the picture is zoomed in. Formats the system cannot play fall back to the open button.
 */
@Composable
fun VideoViewer(
    uri: Uri,
    isAudio: Boolean,
    active: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!active) {
        // Pages next to the one on screen only show black; nothing is decoded until the page is the current one.
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }
    val context = LocalContext.current
    val controller = remember { VideoController(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val onZoom by rememberUpdatedState(onZoomedChange)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubStart by remember { mutableStateOf<Float?>(null) }
    var scrubDelta by remember { mutableFloatStateOf(0f) }
    var resumeAfterScrub by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        controller.load(uri)
        controller.play()
        // Playing on while another app has the screen would be a surprise, so it pauses.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.keepScreenOn = false
            onZoom(false)
            controller.release()
        }
    }
    DisposableEffect(controller.isPlaying) {
        view.keepScreenOn = controller.isPlaying
        onDispose { }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller.tick()
            delay(200)
        }
    }

    fun clamp(value: Offset, s: Float): Offset {
        val limitX = maxOf(0f, size.width * (s - 1) / 2)
        val limitY = maxOf(0f, size.height * (s - 1) / 2)
        return Offset(value.x.coerceIn(-limitX, limitX), value.y.coerceIn(-limitY, limitY))
    }

    fun setScale(value: Float) {
        scale = value.coerceIn(0.8f, MAX_SCALE)
        offset = clamp(offset, scale)
        onZoom(scale > 1.02f)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
        onZoom(false)
    }

    fun beginScrub() {
        if (scrubStart != null) return
        scrubStart = controller.current
        scrubDelta = 0f
        resumeAfterScrub = controller.isPlaying
        controller.scrubbing = true
        controller.pause()
    }

    fun endScrub() {
        if (scrubStart == null) return
        scrubStart = null
        controller.scrubbing = false
        controller.seek(controller.current)
        if (resumeAfterScrub) controller.play()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { size = it },
    ) {
        if (controller.unplayable) {
            IconButton(onClick = onOpen, modifier = Modifier.align(Alignment.Center).size(72.dp)) {
                Icon(
                    if (isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayCircle,
                    contentDescription = stringResource(R.string.action_open),
                    modifier = Modifier.size(64.dp),
                    tint = Color.White,
                )
            }
            return@Box
        }

        // The picture, as large as fits, which is then zoomed and moved.
        val videoSize = controller.videoSize
        val ratio = if (videoSize.width > 0 && videoSize.height > 0) videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height else 16f / 9f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isAudio) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.6f))
            } else {
                AndroidView(
                    factory = { TextureView(it).also { texture -> controller.player.setVideoTextureView(texture) } },
                    modifier = Modifier.aspectRatio(ratio),
                )
            }
        }

        // Taps and gestures sit in a layer of their own above the picture.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { if (scale > 1.02f) reset() else setScale(2.5f) },
                    )
                }
                .pointerInput(isAudio) {
                    if (isAudio) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moved = Offset.Zero
                        var dragging = false
                        var scrubbingNow = false
                        var startTime = 0f
                        val slop = viewConfiguration.touchSlop
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                // Two fingers: pinch to zoom, and move the picture along with them.
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    setScale(scale * zoom)
                                    offset = clamp(offset + pan, scale)
                                    event.changes.forEach { it.consume() }
                                }
                                dragging = true
                            } else if (pressed == 1) {
                                val change = event.changes.first { it.pressed }
                                val delta = change.positionChange()
                                moved += delta
                                if (!dragging && (abs(moved.x) > slop || abs(moved.y) > slop)) {
                                    dragging = true
                                    // A drag across a picture that is not zoomed is a scrub, when it is mostly sideways.
                                    if (scale <= 1.02f && abs(moved.x) > abs(moved.y) && controller.duration > 0) {
                                        scrubbingNow = true
                                        beginScrub()
                                        startTime = scrubStart ?: controller.current
                                        moved = Offset(moved.x, 0f)
                                    }
                                }
                                if (dragging && delta != Offset.Zero) {
                                    if (scrubbingNow) {
                                        // Across the whole screen covers up to 90 seconds, or the whole video if shorter.
                                        val span = controller.duration.coerceIn(1f, 90f)
                                        val target = (startTime + moved.x / size.width.coerceAtLeast(1) * span).coerceIn(0f, controller.duration)
                                        scrubDelta = target - startTime
                                        controller.scrub(target)
                                        change.consume()
                                    } else if (scale > 1.02f) {
                                        offset = clamp(offset + delta, scale)
                                        change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (scrubbingNow) endScrub()
                        // Letting go of a pinch near normal size springs back to normal.
                        if (scale < 1.05f) reset()
                    }
                },
        )

        if (scrubStart != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    (if (scrubDelta >= 0) "+" else "-") + formatTime(abs(scrubDelta)),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    formatTime(controller.current) + " / " + formatTime(controller.duration),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (controlsVisible) {
            Controls(
                controller = controller,
                onScrubStart = { beginScrub() },
                onScrub = { value ->
                    scrubDelta = value - (scrubStart ?: value)
                    controller.scrub(value)
                },
                onScrubEnd = { endScrub() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(
    controller: VideoController,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .navigationBarsPadding()
            .padding(horizontal = Spacing.Sm)
            .padding(top = Spacing.Md, bottom = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        IconButton(onClick = { controller.toggle() }) {
            Icon(
                if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (controller.isPlaying) R.string.action_pause else R.string.action_play),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(formatTime(controller.current), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 36.dp))
        var dragging by remember { mutableStateOf(false) }
        val end = maxOf(controller.duration, 0.1f)
        Slider(
            value = controller.current.coerceIn(0f, end),
            onValueChange = { value ->
                if (!dragging) {
                    dragging = true
                    onScrubStart()
                }
                onScrub(value)
            },
            onValueChangeFinished = {
                dragging = false
                onScrubEnd()
            },
            valueRange = 0f..end,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
        Text(formatTime(controller.duration), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 36.dp))
        SpeedMenu(controller)
    }
}

@Composable
private fun SpeedMenu(controller: VideoController) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .height(32.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(speedLabel(controller.speed), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VideoController.SPEEDS.forEach { value ->
                DropdownMenuItem(
                    text = { Text(speedLabel(value)) },
                    leadingIcon = { if (value == controller.speed) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        controller.chooseSpeed(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun speedLabel(value: Float) = (if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString().trimEnd('0')) + "×"

private fun formatTime(seconds: Float): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00"
    val total = Math.round(seconds)
    return "%d:%02d".format(total / 60, total % 60)
}
