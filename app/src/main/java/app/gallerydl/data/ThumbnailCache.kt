package app.gallerydl.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Small still images of the library's pictures and videos, made once and kept on disk, so the library does not
 * decode every file again each time it opens or scrolls.
 *
 * The files live in the app's private, non-backed-up files folder rather than the system cache folder, because
 * Android may empty the cache folder at any time. They are removed only when their file or folder is deleted, or
 * when the user clears them.
 */
class ThumbnailCache(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.noBackupFilesDir, "thumbnails").apply { mkdirs() }

    /** At most two thumbnails are made at the same time, so scrolling a big folder never floods memory. */
    private val workers = Semaphore(2)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<Long, Deferred<File?>>()

    private fun fileFor(id: Long) = File(dir, "$id.jpg")

    /** The thumbnail of [item], made first if it does not exist yet. Null when the file cannot be decoded. */
    suspend fun obtain(item: LibraryItem): File? {
        val file = fileFor(item.id)
        if (file.length() > 0) return file
        // The work runs in the cache's own scope, shared by everyone who asks for the same thumbnail. A tile scrolling off
        // screen cancels only its own wait, never the work the other waiters are relying on.
        val created = scope.async(start = CoroutineStart.LAZY) { workers.withPermit { make(item, file) } }
        val job = inFlight.putIfAbsent(item.id, created)
        if (job != null) {
            created.cancel()
            return job.await()
        }
        created.invokeOnCompletion { inFlight.remove(item.id, created) }
        created.start()
        return created.await()
    }

    private fun make(item: LibraryItem, target: File): File? {
        val bitmap = runCatching {
            if (item.mimeType.startsWith("video/")) videoFrame(item) else imageBitmap(item)
        }.getOrNull() ?: return null
        val temp = File(dir, "${item.id}.tmp")
        val saved = runCatching {
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            temp.renameTo(target)
        }.getOrDefault(false)
        bitmap.recycle()
        if (!saved) temp.delete()
        return target.takeIf { saved && it.length() > 0 }
    }

    private fun imageBitmap(item: LibraryItem): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, item.contentUri.toUri())
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Decoded straight to thumbnail size, so a large photo is never held in memory at full size.
            val scale = EDGE.toFloat() / max(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun videoFrame(item: LibraryItem): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, item.contentUri.toUri())
            // Asked for in the video's own proportions: a square request would squash a wide or tall video.
            fun meta(key: Int) = retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
            var width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            var height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            if (meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180L != 0L) width = height.also { height = width }
            if (width <= 0 || height <= 0) return null
            val scale = EDGE.toFloat() / max(width, height)
            val outW = (width * scale).toInt().coerceAtLeast(1)
            val outH = (height * scale).toInt().coerceAtLeast(1)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            // A little way in, to skip a black first frame.
            val atUs = minOf(1_000L, durationMs / 2) * 1_000
            return retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, outW, outH)
        } finally {
            retriever.release()
        }
    }

    /** Removes the thumbnails of deleted files. */
    fun delete(ids: Collection<Long>) {
        ids.forEach { fileFor(it).delete() }
    }

    /** Removes every thumbnail that no longer has a library item. */
    fun prune(existingIds: Set<Long>) {
        dir.listFiles()?.forEach { file ->
            val id = file.nameWithoutExtension.toLongOrNull()
            if (id == null || id !in existingIds) file.delete()
        }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** The "Clear thumbnail cache" button. They are made again the next time the library needs them. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val EDGE = 480
    }
}
