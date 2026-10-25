package app.gallerydl.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.gallerydl.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Website icons, fetched from the site itself (https only) and kept on the phone until the app is removed, so a site's
 * icon is asked for once and not again on every launch. The larger touch icon is tried first, then the classic favicon.
 * A site with neither is remembered for a week before it is asked again.
 */
object SiteIconStore {
    private val memory = LruCache<String, Bitmap>(64)
    private const val MISS_DAYS = 7L
    private const val MAX_BYTES = 1024 * 1024

    suspend fun get(context: Context, host: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(host)?.let { return@withContext it }
        val dir = File(context.applicationContext.noBackupFilesDir, "siteicons").apply { mkdirs() }
        val name = host.lowercase().replace(Regex("[^a-z0-9.-]"), "_")
        val file = File(dir, "$name.img")
        val miss = File(dir, "$name.none")
        if (file.length() > 0) {
            decode(file.readBytes())?.let { memory.put(host, it); return@withContext it }
            file.delete()
        }
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < MISS_DAYS * 24 * 3600 * 1000) return@withContext null
        for (path in listOf("apple-touch-icon.png", "favicon.ico")) {
            val bytes = download("https://$host/$path") ?: continue
            val bitmap = decode(bytes) ?: continue
            file.writeBytes(bytes)
            miss.delete()
            memory.put(host, bitmap)
            return@withContext bitmap
        }
        runCatching { miss.writeText("") }
        null
    }

    private fun decode(bytes: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private fun download(address: String): ByteArray? = runCatching {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        try {
            if (connection.responseCode != 200) return@runCatching null
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    out.write(chunk, 0, read)
                    if (out.size() > MAX_BYTES) return@runCatching null
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** Website icons are made for light backgrounds, so they sit on a soft off-white tile. */
private val IconBackground = Color(0xFFEDEFF7)

/** A website's own icon; while it loads, and if the site has none, [fallback] is shown instead. */
@Composable
fun SiteIcon(host: String, fallback: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, host) { value = SiteIconStore.get(context, host) }
    val bitmap = icon
    if (bitmap == null) {
        fallback()
    } else {
        Box(modifier) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().background(IconBackground).padding(Spacing.Sm),
            )
        }
    }
}
