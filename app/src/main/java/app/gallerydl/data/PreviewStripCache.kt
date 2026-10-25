package app.gallerydl.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.random.Random

/**
 * A short silent preview of a video, kept as a row of small JPEG frames in one file: `GDLS`, a version, the frame rate,
 * the frame count, a table of where each frame starts, then the frames. Playing it means decoding one small frame at a
 * time, so it needs no video decoder and only a megabyte or two of memory.
 */
class Strip private constructor(
    private val file: RandomAccessFile,
    val frameCount: Int,
    val fps: Int,
    private val offsets: LongArray,
) : Closeable {

    @Synchronized
    private fun readFrame(index: Int): ByteArray {
        file.seek(offsets[index])
        return ByteArray((offsets[index + 1] - offsets[index]).toInt()).also { file.readFully(it) }
    }

    /** Decodes one frame, reusing [reuse]'s memory when it fits, so playing a strip creates almost no garbage. */
    fun decode(index: Int, reuse: Bitmap?): Bitmap? {
        if (index !in 0 until frameCount) return null
        val bytes = runCatching { readFrame(index) }.getOrNull() ?: return null
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inBitmap = reuse
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: IllegalArgumentException) {
            // The old bitmap did not fit this frame (a different strip); decode into a new one.
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inMutable = true })
        }
    }

    @Synchronized
    override fun close() {
        runCatching { file.close() }
    }

    companion object {
        fun open(source: File): Strip? = runCatching {
            val file = RandomAccessFile(source, "r")
            try {
                val head = ByteArray(8).also { file.readFully(it) }
                if (String(head, 0, 4, Charsets.US_ASCII) != "GDLS" || head[4].toInt() != 1) error("not a strip")
                val count = (head[6].toInt() and 0xFF) or ((head[7].toInt() and 0xFF) shl 8)
                require(count > 0)
                val table = ByteArray((count + 1) * 4).also { file.readFully(it) }
                val offsets = LongArray(count + 1) { i ->
                    (0 until 4).fold(0L) { acc, b -> acc or ((table[i * 4 + b].toLong() and 0xFF) shl (8 * b)) }
                }
                Strip(file, count, head[5].toInt() and 0xFF, offsets)
            } catch (e: Throwable) {
                file.close()
                throw e
            }
        }.getOrNull()
    }
}

/**
 * Makes and keeps the preview strips. One is made for each video when it is downloaded (and the first time an older
 * video is shown), from a random 3 second window of the video, in the background one at a time.
 *
 * They live in the app's private, non-backed-up files folder, not the system cache folder that Android may empty at any
 * time, and are removed only when their video or folder is deleted, or when the user clears the cache.
 */
class PreviewStripCache(context: Context) {
    private val appContext = context.applicationContext
    // The first version kept 12 frames a second, which looked choppy; those clips are discarded and made again.
    private val dir = File(appContext.noBackupFilesDir, "previews2").apply {
        File(appContext.noBackupFilesDir, "previews").takeIf { it.exists() }?.deleteRecursively()
        mkdirs()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workers = Semaphore(1)
    private val pending = ConcurrentHashMap.newKeySet<Long>()

    private val _ready = MutableSharedFlow<Long>(extraBufferCapacity = 64)

    /** How many strips are waiting to be made and how many are done, for the progress bar in the library. */
    data class Progress(val total: Int = 0, val done: Int = 0)

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    /** Videos that could not be read, so they are not tried again on every visit. */
    private val failed = ConcurrentHashMap.newKeySet<Long>()

    private fun bump(total: Int = 0, done: Int = 0) {
        _progress.update { Progress((it.total + total).coerceAtLeast(0), it.done + done) }
        val now = _progress.value
        if (now.total == 0) _progress.value = Progress()
        // Started again from zero a moment after everything is finished.
        if (now.total > 0 && now.done >= now.total) {
            scope.launch {
                delay(1_200)
                _progress.update { if (it.total > 0 && it.done >= it.total) Progress() else it }
            }
        }
    }

    /** Emits the id of a video whenever its strip has just been made. */
    val ready: SharedFlow<Long> = _ready

    private fun fileFor(id: Long) = File(dir, "$id.strip")

    /** The strip of a video, if it has been made. */
    fun open(id: Long): Strip? {
        val file = fileFor(id).takeIf { it.length() > 0 } ?: return null
        // A file that cannot be read (cut short, or from an older version) is removed so it is made again.
        return Strip.open(file) ?: run {
            file.delete()
            null
        }
    }

    /** Makes the strip of a video in the background, unless it exists or is already being made. */
    fun requestMake(item: LibraryItem) {
        if (!item.mimeType.startsWith("video/") || fileFor(item.id).length() > 0 || item.id in failed || !pending.add(item.id)) return
        bump(total = 1)
        scope.launch {
            try {
                makeAndAnnounce(item)
            } finally {
                pending.remove(item.id)
                bump(done = 1)
            }
        }
    }

    private suspend fun makeAndAnnounce(item: LibraryItem) {
        val made = workers.withPermit { runCatching { make(item) }.getOrDefault(false) }
        if (made) _ready.emit(item.id) else failed.add(item.id)
    }

    /**
     * Makes the strip of every video that does not have one yet, one after the other, and returns when all are done (or
     * the caller is cancelled). [onProgress] is told how far it has got. This is the work of the background job. A video
     * on screen asks for itself through [requestMake] and only waits for the one being made right now.
     */
    suspend fun makeMissing(items: List<LibraryItem>, onProgress: suspend (done: Int, total: Int) -> Unit) {
        val missing = items.filter { it.mimeType.startsWith("video/") && fileFor(it.id).length() == 0L && it.id !in failed && it.id !in pending }
        if (missing.isEmpty()) return
        var finished = 0
        bump(total = missing.size)
        try {
            onProgress(0, missing.size)
            for (item in missing) {
                try {
                    // Made meanwhile, or being made because a tile on screen asked: nothing left to do for it.
                    if (fileFor(item.id).length() == 0L && pending.add(item.id)) {
                        try {
                            makeAndAnnounce(item)
                        } finally {
                            pending.remove(item.id)
                        }
                    }
                } finally {
                    finished++
                    bump(done = 1)
                }
                onProgress(finished, missing.size)
            }
        } finally {
            // Stopped early: what was not done is taken back off the bar.
            if (finished < missing.size) bump(total = -(missing.size - finished))
        }
    }

    private fun make(item: LibraryItem): Boolean {
        val retriever = MediaMetadataRetriever()
        var fps = MAX_FPS
        val frames = ArrayList<ByteArray>(WINDOW_SECONDS * MAX_FPS)
        try {
            retriever.setDataSource(appContext, item.contentUri.toUri())
            fun meta(key: Int) = retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationMs < 200) return false
            var width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            var height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            val rotation = ((meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 360L).toInt() + 360) % 360
            if (rotation % 180 != 0) width = height.also { height = width }
            if (width <= 0 || height <= 0) return false
            val scale = EDGE.toFloat() / max(width, height)
            val outW = (width * scale).toInt().coerceAtLeast(1)
            val outH = (height * scale).toInt().coerceAtLeast(1)

            // The strip keeps the video's own frame rate, up to 30, so motion looks as it does in the video.
            val frameCount = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            val sourceFps = if (frameCount > 0) frameCount * 1000.0 / durationMs else 0.0
            fps = if (sourceFps >= 8) minOf(MAX_FPS.toDouble(), sourceFps).toInt().coerceAtLeast(8) else MAX_FPS
            // Shorter videos give shorter strips; longer ones give a random window, away from the very start and end.
            val count = minOf(WINDOW_SECONDS * fps, max(6, (durationMs / 1000.0 * fps).toInt()))
            val spanMs = (count * 1000L) / fps
            val latestStartMs = max(0L, durationMs - spanMs - 150)
            val startMs = if (latestStartMs > 300) Random.nextLong(150, latestStartMs) else 0L

            // Decoding the window once, front to back, is many times faster than asking for each frame on its own
            // (every such request restarts from the previous keyframe). Where a device cannot do it, the slow way is used.
            val decoded = runCatching { decodeWindow(item, startMs * 1000, count, fps, rotation) }.getOrNull()
            if (decoded != null && decoded.size >= max(4, count / 2)) {
                frames.addAll(decoded)
            } else {
                val out = ByteArrayOutputStream()
                for (i in 0 until count) {
                    val atUs = (startMs + i * 1000L / fps) * 1000
                    val frame = retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST, outW, outH) ?: continue
                    out.reset()
                    frame.compress(Bitmap.CompressFormat.JPEG, 72, out)
                    frame.recycle()
                    frames.add(out.toByteArray())
                }
            }
        } finally {
            retriever.release()
        }
        if (frames.size < 4) return false

        val headerSize = 8 + (frames.size + 1) * 4
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf('G'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 'S'.code.toByte(), 1, fps.toByte(), (frames.size and 0xFF).toByte(), (frames.size shr 8).toByte()))
        var position = headerSize.toLong()
        val offsets = LongArray(frames.size + 1)
        frames.forEachIndexed { i, jpeg -> offsets[i] = position; position += jpeg.size }
        offsets[frames.size] = position
        offsets.forEach { o -> repeat(4) { b -> body.write(((o shr (8 * b)) and 0xFF).toInt()) } }
        frames.forEach { body.write(it) }

        val temp = File(dir, "${item.id}.tmp")
        temp.writeBytes(body.toByteArray())
        val target = fileFor(item.id)
        target.delete()
        return temp.renameTo(target).also { if (!it) temp.delete() }
    }

    /**
     * Decodes [count] frames, [fps] a second, starting at [startUs], reading the video once from the keyframe before the
     * start. Each frame is shrunk while it is copied out of the decoder and saved as a small JPEG. Returns null when the
     * device's decoder cannot give frames this way.
     */
    private fun decodeWindow(item: LibraryItem, startUs: Long, count: Int, fps: Int, rotation: Int): List<ByteArray>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(appContext, item.contentUri.toUri(), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }
            val frames = ArrayList<ByteArray>(count)
            val info = MediaCodec.BufferInfo()
            val stepUs = 1_000_000.0 / fps
            var nextUs = startUs.toDouble()
            var inputDone = false
            val deadline = SystemClock.elapsedRealtime() + DECODE_TIMEOUT_MS
            while (frames.size < count && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (out < 0) continue
                try {
                    // Frames before the start (from the keyframe) are decoded and dropped.
                    if (info.size > 0 && info.presentationTimeUs + 1000 >= nextUs) {
                        val image = codec.getOutputImage(out) ?: return null
                        frames.add(jpegOf(image, rotation) ?: return null)
                        nextUs += stepUs
                        // A video with fewer frames than asked for (or a slower rate) must not repeat a frame.
                        while (info.presentationTimeUs >= nextUs + stepUs) nextUs += stepUs
                    }
                } finally {
                    codec.releaseOutputBuffer(out, false)
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            return frames
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** A decoded frame as a JPEG of at most [EDGE] pixels, turned upright by [rotation] degrees. */
    private fun jpegOf(image: Image, rotation: Int): ByteArray? {
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        // Only plain 8-bit frames are read here; 10-bit video uses the slower way that the platform converts for us.
        if (width <= 0 || height <= 0 || image.planes.size < 3 || image.format != ImageFormat.YUV_420_888) return null
        if (image.planes.any { it.pixelStride > 2 }) return null
        val step = max(1, (max(width, height) + EDGE - 1) / EDGE)
        val outW = (width / step) and 1.inv()
        val outH = (height / step) and 1.inv()
        if (outW < 2 || outH < 2) return null
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val nv21 = ByteArray(outW * outH * 3 / 2)
        // Each output pixel averages a few samples spread over the block it stands for, so shrinking stays smooth.
        val half = step / 2
        for (y in 0 until outH) {
            val sy = crop.top + y * step
            val sy2 = minOf(sy + half, crop.bottom - 1)
            for (x in 0 until outW) {
                val sx = crop.left + x * step
                val sx2 = minOf(sx + half, crop.right - 1)
                val a = yBuf.get(sy * yPlane.rowStride + sx * yPlane.pixelStride).toInt() and 0xFF
                val b = yBuf.get(sy * yPlane.rowStride + sx2 * yPlane.pixelStride).toInt() and 0xFF
                val c = yBuf.get(sy2 * yPlane.rowStride + sx * yPlane.pixelStride).toInt() and 0xFF
                val d = yBuf.get(sy2 * yPlane.rowStride + sx2 * yPlane.pixelStride).toInt() and 0xFF
                nv21[y * outW + x] = ((a + b + c + d) shr 2).toByte()
            }
        }
        var o = outW * outH
        for (cy in 0 until outH / 2) {
            val row = (crop.top / 2) + cy * step
            for (cx in 0 until outW / 2) {
                val col = (crop.left / 2) + cx * step
                nv21[o++] = vBuf.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                nv21[o++] = uBuf.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
        }
        val jpeg = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, outW, outH, null).compressToJpeg(Rect(0, 0, outW, outH), 72, jpeg)
        if (rotation == 0) return jpeg.toByteArray()
        // Phone videos are often stored sideways with a rotation flag; the picture is small now, so turning it is cheap.
        val raw = jpeg.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (turned !== bitmap) bitmap.recycle()
        jpeg.reset()
        turned.compress(Bitmap.CompressFormat.JPEG, 72, jpeg)
        turned.recycle()
        return jpeg.toByteArray()
    }

    /** Removes the strips of deleted videos. */
    fun delete(ids: Collection<Long>) {
        ids.forEach { fileFor(it).delete() }
    }

    /** Removes every strip that no longer has a library item. */
    fun prune(existingIds: Set<Long>) {
        dir.listFiles()?.forEach { file ->
            val id = file.nameWithoutExtension.toLongOrNull()
            if (id == null || id !in existingIds) file.delete()
        }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        // Videos that failed once get another chance after the cache is cleared.
        failed.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_FPS = 30
        const val WINDOW_SECONDS = 3
        const val EDGE = 360
        const val TIMEOUT_US = 10_000L
        const val DECODE_TIMEOUT_MS = 20_000L
    }
}
