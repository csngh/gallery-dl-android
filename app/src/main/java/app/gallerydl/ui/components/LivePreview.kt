package app.gallerydl.ui.components

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.gallerydl.data.LibraryItem
import app.gallerydl.data.PreviewStripCache
import app.gallerydl.data.Strip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Random videos a folder cover cycles through, and how long one clip fades into the next. */
private const val FOLDER_CLIPS = 6
private const val BLEND_MS = 400

/**
 * Decides when the library's video previews may animate, and counts the tiles that are on screen.
 *
 * A preview is a short strip of small JPEG frames saved once for each video (see [PreviewStripCache]), so playing one
 * needs no video decoder and very little memory: every visible tile can animate at once. Everything stops while the
 * app is in the background, the viewer is open, battery saver is on, animations are switched off, or the system is
 * short of memory.
 */
@Stable
class PreviewHost(private val context: Context) {

    /**
     * Tiles on screen, with how many composables currently ask for each. Counted, because the same tile can briefly exist
     * twice and one going away must not cancel the other.
     */
    private val requests = mutableStateMapOf<String, Int>()

    /** The order tiles first asked in, oldest first, so that when there are too many the newest ones win. */
    private val order = ArrayList<String>()

    /** The Live previews setting. */
    var enabledBySetting by mutableStateOf(true)
    var appVisible by mutableStateOf(true)
    var viewerOpen by mutableStateOf(false)
    private var batterySaver by mutableStateOf(false)
    private var lowMemory by mutableStateOf(false)
    private var animationsOn by mutableStateOf(true)

    private val handler = Handler(Looper.getMainLooper())

    private val callbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            // Only real memory pressure while the app is in front counts. Going to the background (or locking the screen)
            // also sends a trim signal, TRIM_MEMORY_UI_HIDDEN and the levels above it, which must not switch previews off:
            // that would keep them off for half a minute after coming back.
            if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
            // Off for a while, not for good: the previews come back by themselves once the pressure has passed.
            lowMemory = true
            handler.removeCallbacks(clearLowMemory)
            handler.postDelayed(clearLowMemory, 30_000)
        }
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        @Deprecated("Replaced by onTrimMemory")
        override fun onLowMemory() {
            lowMemory = true
        }
    }

    private val clearLowMemory = Runnable { lowMemory = false }

    /** Turning battery saver on or off while the library is open takes effect at once. */
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            batterySaver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }
    }

    init {
        context.registerComponentCallbacks(callbacks)
        ContextCompat.registerReceiver(context, powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshSystemState()
    }

    /** Re-reads battery saver and the animation scale; called whenever the screen comes back. */
    fun refreshSystemState() {
        batterySaver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        animationsOn = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    private val allowed get() = enabledBySetting && appVisible && !viewerOpen && !batterySaver && !lowMemory && animationsOn

    fun request(key: String) {
        if (key !in requests) order.add(key)
        requests[key] = (requests[key] ?: 0) + 1
    }

    fun cancel(key: String) {
        val count = requests[key] ?: return
        if (count <= 1) {
            requests.remove(key)
            order.remove(key)
        } else {
            requests[key] = count - 1
        }
    }

    /** A safety limit on tiles animating together; past it the oldest tiles stop (the ones scrolled furthest away), not all. */
    fun isActive(key: String): Boolean = allowed && key in requests && order.takeLast(MAX_ACTIVE).contains(key)

    /** Called when the library leaves the screen. */
    fun dispose() {
        context.unregisterComponentCallbacks(callbacks)
        runCatching { context.unregisterReceiver(powerReceiver) }
        handler.removeCallbacks(clearLowMemory)
        requests.clear()
        order.clear()
    }

    private companion object {
        // Every playing tile decodes a JPEG about 30 times a second, which is real work on a phone: 16 is enough for any
        // screenful of tiles, and past it the oldest stop.
        const val MAX_ACTIVE = 16
    }
}

val LocalPreviewHost = compositionLocalOf<PreviewHost?> { null }
val LocalPreviewStrips = compositionLocalOf<PreviewStripCache?> { null }

/**
 * A silent live preview laid over a tile's still image. It plays only while the tile is on screen and previews are
 * allowed; otherwise it draws nothing, so the still image underneath shows.
 *
 * A single video loops its own strip. With several (a folder cover) it plays the strips of a few random videos in
 * turn, fading from one into the next. Strips that do not exist yet are requested, and the still image shows until the
 * first one is ready.
 */
@Composable
fun LivePreview(key: String, clips: List<LibraryItem>, modifier: Modifier = Modifier) {
    val host = LocalPreviewHost.current ?: return
    val strips = LocalPreviewStrips.current ?: return
    if (clips.isEmpty()) return
    DisposableEffect(key) {
        host.request(key)
        onDispose { host.cancel(key) }
    }
    if (host.isActive(key)) StripSurface(strips, clips, modifier)
}

@Composable
private fun StripSurface(cache: PreviewStripCache, clips: List<LibraryItem>, modifier: Modifier) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val alpha by animateFloatAsState(if (frame != null) 1f else 0f, tween(250), label = "previewFade")
    // The last frame of the strip that just ended, fading out while the next one starts.
    var held by remember { mutableStateOf<ImageBitmap?>(null) }
    val heldAlpha = remember { Animatable(0f) }

    LaunchedEffect(clips) {
        val candidates = if (clips.size > 1) clips.shuffled().take(FOLDER_CLIPS) else clips
        val open = HashMap<Long, Strip>()
        // Two bitmaps take turns: one is on screen while the next frame is decoded into the other.
        val bitmaps = arrayOfNulls<Bitmap>(2)
        try {
            val order = ArrayDeque<Long>()
            var current: Long? = null
            while (isActive) {
                for (item in candidates) {
                    if (item.id !in open) cache.open(item.id)?.let { open[item.id] = it } ?: cache.requestMake(item)
                }
                if (open.isEmpty()) {
                    // Nothing made yet: wait for the first strip, then look again.
                    // A signal can be missed (it may arrive just before this starts waiting), so look again regularly too.
                    withTimeoutOrNull(2_000) { cache.ready.first { id -> candidates.any { it.id == id } } }
                    continue
                }
                if (order.isEmpty()) order.addAll(open.keys.shuffled())
                // Never the same strip twice in a row, unless it is the only one.
                if (order.size > 1 && order.first() == current) order.addLast(order.removeFirst())
                current = order.removeFirst()
                val strip = open.getValue(current)

                // Paced by the clock, not by counting frames, so a late frame is skipped instead of slowing everything,
                // and a strip made from a 24 fps video plays at 24 fps.
                var shown: Bitmap? = null
                val began = SystemClock.elapsedRealtimeNanos()
                var last = -1
                var slot = 0
                while (true) {
                    val index = ((SystemClock.elapsedRealtimeNanos() - began) / 1e9 * strip.fps).toInt()
                    if (index >= strip.frameCount) break
                    if (index != last) {
                        last = index
                        val decoded = withContext(Dispatchers.IO) { strip.decode(index, bitmaps[slot]) }
                        if (decoded != null) {
                            bitmaps[slot] = decoded
                            slot = 1 - slot
                            shown = decoded
                            frame = decoded.asImageBitmap()
                        }
                    }
                    withFrameNanos { }
                }
                // Hold the last frame and fade it out over the start of the next strip.
                shown?.copy(Bitmap.Config.ARGB_8888, false)?.let { copy ->
                    held = copy.asImageBitmap()
                    launch {
                        heldAlpha.snapTo(1f)
                        heldAlpha.animateTo(0f, tween(BLEND_MS))
                        held = null
                    }
                }
            }
        } finally {
            open.values.forEach { it.close() }
        }
    }

    Box(modifier) {
        frame?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop)
        }
        held?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(heldAlpha.value), contentScale = ContentScale.Crop)
        }
    }
}

/** Creates the host for the library screen and frees it when the screen goes away. */
@Composable
fun rememberPreviewHost(enabledBySetting: Boolean): PreviewHost {
    val context = LocalContext.current
    val host = remember { PreviewHost(context.applicationContext) }
    host.enabledBySetting = enabledBySetting
    DisposableEffect(host) { onDispose { host.dispose() } }
    return host
}
