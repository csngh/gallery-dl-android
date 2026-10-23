package app.gallerydl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.gallerydl.ui.theme.Spacing
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import java.util.concurrent.ConcurrentHashMap

/** Icon addresses that did not work during this session, so a site without an icon is not asked again. */
private object IconMisses {
    private val urls = ConcurrentHashMap.newKeySet<String>()
    fun add(url: String) = urls.add(url)
    fun firstWorkingIndex(candidates: List<String>): Int =
        candidates.indexOfFirst { it !in urls }.let { if (it < 0) candidates.size else it }
}

/** Website icons are made for light backgrounds, so they sit on a soft off-white tile. */
private val IconBackground = Color(0xFFEDEFF7)

/**
 * A website's own icon, fetched from the site itself (https only) and cached on the phone by the image loader.
 * It tries the larger touch icon first and then the classic favicon; while loading, and if neither exists,
 * [fallback] is shown instead.
 */
@Composable
fun SiteIcon(host: String, fallback: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val candidates = remember(host) { listOf("https://$host/apple-touch-icon.png", "https://$host/favicon.ico") }
    var attempt by remember(host) { mutableIntStateOf(IconMisses.firstWorkingIndex(candidates)) }
    if (attempt >= candidates.size) {
        fallback()
        return
    }
    SubcomposeAsyncImage(
        model = candidates[attempt],
        contentDescription = null,
        modifier = modifier,
        onError = {
            IconMisses.add(candidates[attempt])
            attempt++
        },
        loading = { fallback() },
        error = { fallback() },
        success = {
            SubcomposeAsyncImageContent(Modifier.fillMaxSize().background(IconBackground).padding(Spacing.Sm))
        },
    )
}
