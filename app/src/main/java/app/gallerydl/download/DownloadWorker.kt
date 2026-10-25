package app.gallerydl.download

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.gallerydl.GalleryDlApp
import app.gallerydl.R
import app.gallerydl.data.DownloadTask
import app.gallerydl.data.LibraryItem
import app.gallerydl.data.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/** Drains the download queue one task at a time, running gallery-dl in-process. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container = (context.applicationContext as GalleryDlApp).container
    private val notifications = DownloadNotifications(context)

    override suspend fun doWork(): Result {
        val tasks = container.database.taskDao()
        tasks.resetInterrupted()
        // Foreground promotion can be refused when the worker starts in the background; downloading still works.
        runCatching { setForeground(notifications.starting()) }

        while (currentCoroutineContext().isActive) {
            val task = tasks.nextQueued() ?: break
            process(task)
        }
        return Result.success()
    }

    private suspend fun process(task: DownloadTask) {
        val tasks = container.database.taskDao()
        val registry = container.cancelRegistry
        registry.clear(task.id) // drop a stale request left by a cancel that arrived as the task finished
        if (tasks.markDownloading(task.id) == 0) return // cancelled while waiting its turn

        val outDir = File(applicationContext.cacheDir, "gdl/${task.id}").apply {
            deleteRecursively()
            mkdirs()
        }
        val coroutineJob = currentCoroutineContext().job
        val destination = container.settingsRepository.destination.first()
        val linkFolder = linkFolderName(task.url)

        if (destination != null && !container.mediaSaver.isTreeWritable(destination.treeUri)) {
            outDir.deleteRecursively()
            tasks.finish(
                task.id, TaskStatus.FAILED,
                applicationContext.getString(R.string.error_destination_unavailable), 0, System.currentTimeMillis(),
            )
            return
        }

        var importError: String? = null
        var importFailures = 0
        val listener = object : DownloadListener {
            override fun isCancelled() = coroutineJob.isCancelled || registry.isRequested(task.id)

            private var lastNotified = 0L
            private var lastIndex = -1

            override fun onProgress(index: Int, count: Int, percent: Int, fileName: String) {
                // Called a few times per second at most; a failed progress write must never stop a download.
                runBlocking(Dispatchers.IO) {
                    runCatching { tasks.updateProgress(task.id, index, count, percent, fileName) }
                    // The shade is refreshed about once a second, or at once when the next file starts.
                    val now = System.currentTimeMillis()
                    if (index != lastIndex || now - lastNotified >= NOTIFICATION_INTERVAL_MS) {
                        lastIndex = index
                        lastNotified = now
                        runCatching {
                            notifications.progress(task, index, count, overallPercent(index, count, percent), tasks.countQueued())
                        }
                    }
                }
            }

            override fun onFile(path: String, sourceUrl: String) {
                runBlocking(Dispatchers.IO) {
                    runCatching { importFile(task, File(path), outDir, linkFolder, destination?.treeUri) }
                        .onFailure {
                            importError = it.message
                            importFailures++
                        }
                }
            }
        }

        // The options as they are when this download starts; later changes apply to the next one.
        val optionsJson = container.settingsRepository.options.first().toJson(container.settingsRepository.cookiesPath())

        // One archive per link: a retry only fetches the files that are still missing.
        val archive = File(applicationContext.filesDir, "archives/${task.id}.sqlite")
        archive.parentFile?.mkdirs()
        val outcome = withContext(Dispatchers.IO) {
            runCatching { container.pythonBridge.download(task.url, outDir.path, archive.path, optionsJson, listener) }
        }
        val userCancelled = registry.isRequested(task.id)
        registry.clear(task.id)
        outDir.deleteRecursively()

        if (!userCancelled && coroutineJob.isCancelled) {
            // The system stopped the worker (network lost, ...). Keep the task queued; the archive makes
            // the resumed run skip the files that were already saved.
            withContext(NonCancellable) { tasks.pause(task.id) }
            throw CancellationException("Worker stopped")
        }
        val cancelled = userCancelled

        val saved = tasks.countSaved(task.id)
        val result = outcome.getOrNull()
        val failedFiles = (result?.failed ?: 0) + importFailures
        val messages = (listOfNotNull(importError) + result?.errors.orEmpty())
            .distinct()
            .joinToString("\n")
            .take(MAX_ERROR_CHARS)
            .ifEmpty { null }
        val (status, error) = when {
            cancelled -> TaskStatus.CANCELLED to null
            outcome.isFailure -> TaskStatus.FAILED to outcome.exceptionOrNull()?.message
            saved == 0 -> TaskStatus.FAILED to (messages ?: applicationContext.getString(R.string.error_no_files))
            failedFiles > 0 || result?.status != 0 ->
                TaskStatus.COMPLETED to (messages ?: applicationContext.getString(R.string.partial_errors))
            else -> TaskStatus.COMPLETED to null
        }
        tasks.finish(task.id, status, error, failedFiles, System.currentTimeMillis())
        runCatching { notifications.finished(task, status, saved, failedFiles) }
    }

    /** Progress of the whole link in percent, or -1 when it cannot be known yet. */
    private fun overallPercent(index: Int, count: Int, percent: Int): Int {
        val file = if (percent >= 0) percent / 100f else 0f
        val fraction = when {
            count > 0 && index > 0 -> ((index - 1) + file) / count
            percent >= 0 -> file
            else -> return -1
        }
        return (fraction.coerceIn(0f, 1f) * 100).toInt()
    }

    private suspend fun importFile(
        task: DownloadTask,
        file: File,
        root: File,
        linkFolder: String,
        tree: Uri?,
    ) {
        // Every link gets its own folder; anything gallery-dl nests deeper stays below it.
        val nested = file.parentFile?.relativeTo(root)?.path.orEmpty().replace(File.separatorChar, '/')
        val subDir = listOf(linkFolder, nested).filter { it.isNotEmpty() }.joinToString("/")
        val saved = container.mediaSaver.save(file, subDir, tree)
        file.delete()
        // Looked up per file, so deleting the folder in the library mid-download cannot leave a dangling id.
        val folderId = container.libraryRepository.ensureFolder(linkFolder)
        val item = LibraryItem(
            contentUri = saved.uri.toString(),
            displayName = saved.displayName,
            mimeType = saved.mimeType,
            sourceUrl = task.url,
            taskId = task.id,
            folderId = folderId,
            addedAt = System.currentTimeMillis(),
        )
        val id = container.libraryRepository.addItem(item)
        // The silent preview of a new video is made right away, in the background, one at a time.
        container.previewStripCache.requestMake(item.copy(id = id))
        container.database.taskDao().fileSaved(task.id)
    }

    companion object {
        const val UNIQUE_NAME = "download-queue"
        private const val NOTIFICATION_INTERVAL_MS = 1_000L
        private const val MAX_ERROR_CHARS = 2_000
    }
}
