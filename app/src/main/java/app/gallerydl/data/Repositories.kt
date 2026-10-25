package app.gallerydl.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.gallerydl.download.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first

/** In-memory set of running tasks the user asked to cancel; polled by the Python listener. */
class CancelRegistry {
    private val ids = ConcurrentHashMap.newKeySet<Long>()
    fun request(id: Long) = ids.add(id)
    fun isRequested(id: Long) = id in ids
    fun clear(id: Long) = ids.remove(id)
}

class DownloadRepository(
    private val dao: TaskDao,
    private val workManager: WorkManager,
    private val cancelRegistry: CancelRegistry,
    private val archiveDir: File,
    private val settings: SettingsRepository,
) {
    fun observeTasks(): Flow<List<DownloadTask>> = dao.observeAll()

    /** Saves a link as "ready to start". Nothing downloads until [start] or [startAll] is called. */
    suspend fun enqueue(url: String, site: String?) {
        dao.insert(DownloadTask(url = url, site = site, status = TaskStatus.PENDING, createdAt = System.currentTimeMillis()))
    }

    suspend fun start(id: Long) {
        if (dao.start(id) > 0) scheduleDrain()
    }

    suspend fun startAll() {
        if (dao.startAll() > 0) scheduleDrain()
    }

    suspend fun cancel(id: Long) {
        if (dao.cancelIfQueued(id, System.currentTimeMillis()) == 0) cancelRegistry.request(id)
    }

    suspend fun retry(id: Long) {
        dao.requeue(id)
        scheduleDrain()
    }

    /** Replaces the link of a failed or cancelled task and queues it again. The old archive no longer applies. */
    suspend fun editLink(id: Long, url: String, site: String?) {
        // A link that has not started just changes; it keeps waiting for the user to start it.
        if (dao.replacePendingLink(id, url, site) > 0) return
        deleteArchive(id)
        if (dao.replaceLink(id, url, site) > 0) scheduleDrain()
    }

    /** The Renew button: every link checks for new files, and links not yet started begin. */
    suspend fun renewAll() {
        if (dao.renewAll() > 0) scheduleDrain()
    }

    suspend fun retryAllFailed() {
        dao.requeueFailed()
        scheduleDrain()
    }

    suspend fun remove(id: Long) {
        deleteArchive(id)
        dao.delete(id)
    }

    suspend fun clearFinished() {
        dao.finishedIds().forEach { deleteArchive(it) }
        dao.deleteFinished()
    }

    private suspend fun deleteArchive(id: Long) = withContext(Dispatchers.IO) {
        archiveDir.listFiles { f -> f.name.startsWith("$id.sqlite") }?.forEach { it.delete() }
    }

    /**
     * One worker drains the queue sequentially. APPEND_OR_REPLACE guarantees a task inserted just as a
     * worker is finishing still gets picked up by the next run.
     */
    private suspend fun scheduleDrain(policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE) {
        // Wi-Fi only waits for an unmetered connection; otherwise any connection will do.
        val network = if (settings.options.first().wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        workManager.enqueueUniqueWork(DownloadWorker.UNIQUE_NAME, policy, request)
    }

    /**
     * Applies a change of the Wi-Fi only setting to work that is already waiting. A download in progress is
     * paused and carries on from its archive, so no file is fetched twice.
     */
    suspend fun applyNetworkSetting() {
        if (dao.nextQueued() != null) scheduleDrain(ExistingWorkPolicy.REPLACE)
    }

    suspend fun clearFinishedHistory() = clearFinished()
}

class LibraryRepository(
    private val dao: LibraryDao,
    private val resolver: ContentResolver,
    private val thumbnails: ThumbnailCache,
    private val previews: PreviewStripCache,
) {
    fun observeFolders(query: String) = dao.observeFolders(query)
    fun observeItems(folderId: Long, query: String) = dao.observeItems(folderId, query)
    fun observeVideos() = dao.observeVideos()

    suspend fun addItem(item: LibraryItem) = dao.insertItem(item)

    fun observeFolderTag(folderId: Long) = dao.observeFolderTag(folderId)

    /** Gives a folder a tag, or takes it off when [tag] is blank. Long tags are cut to 40 characters. */
    suspend fun setFolderTag(folderId: Long, tag: String) = dao.setFolderTag(folderId, tag.trim().take(40).ifEmpty { null })

    /** Returns the id of the folder called [name], creating it if needed. */
    suspend fun ensureFolder(name: String): Long {
        val inserted = dao.insertFolder(Folder(name = name))
        return if (inserted != -1L) inserted else dao.folderByName(name)!!.id
    }

    suspend fun deleteItems(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) dao.itemsByIds(ids).forEach { remove(it) }
    }

    /** Deletes every file saved for a link, then the folder itself. */
    suspend fun deleteFolder(folderId: Long) = withContext(Dispatchers.IO) {
        dao.itemsInFolder(folderId).forEach { remove(it) }
        dao.deleteFolder(folderId)
    }

    /** Drops library entries whose file was deleted outside the app. */
    suspend fun pruneMissing() = withContext(Dispatchers.IO) {
        val all = dao.allItems()
        all.filterNot { exists(Uri.parse(it.contentUri)) }.forEach {
            thumbnails.delete(listOf(it.id))
            previews.delete(listOf(it.id))
            dao.deleteItem(it)
        }
        // Thumbnails whose item is gone (for example after a reinstall) are cleaned up here too.
        val remaining = dao.allItems().map { it.id }.toSet()
        thumbnails.prune(remaining)
        previews.prune(remaining)
    }

    /** Emits whenever MediaStore reports a change, e.g. a file removed in another app. */
    fun mediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** Removes the file from shared storage as well as the library entry. */
    private suspend fun remove(item: LibraryItem) {
        runCatching {
            val uri = Uri.parse(item.contentUri)
            if (uri.authority == MediaStore.AUTHORITY) {
                resolver.delete(uri, null, null)
            } else {
                DocumentsContract.deleteDocument(resolver, uri)
            }
        }
        thumbnails.delete(listOf(item.id))
        previews.delete(listOf(item.id))
        dao.deleteItem(item)
    }

    /**
     * A lost folder permission is not evidence the file is gone, so only a definite
     * "not found" counts as missing.
     */
    private fun exists(uri: Uri): Boolean = try {
        resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: FileNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: Exception) {
        true
    }
}
