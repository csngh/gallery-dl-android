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
import app.gallerydl.download.FAVORITES_FOLDER
import app.gallerydl.download.favoriteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.text.Normalizer
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

    /** [folderName] is the folder the user chose for the link; null keeps the folder gallery-dl names. */
    suspend fun start(id: Long, folderName: String? = null) {
        if (folderName != null) dao.setFolderName(id, folderName)
        if (dao.start(id) > 0) scheduleDrain()
    }

    suspend fun startAll() {
        if (dao.startAll() > 0) scheduleDrain()
    }

    /**
     * Stops everything at once: the links waiting for their turn are cancelled first, so the queue cannot move on to
     * the next one, and then the download that is running. Each link keeps its record of what was saved, so a retry only
     * fetches the rest.
     */
    suspend fun cancelAll() {
        val ids = dao.activeIds()
        val now = System.currentTimeMillis()
        // A waiting link is cancelled directly; whatever is left is the one that is running.
        ids.filter { dao.cancelIfQueued(it, now) == 0 }.forEach { cancelRegistry.request(it) }
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

    /**
     * The Queue's "Clear finished": the links leave the Queue but stay on Home, which is the collection of links to
     * refresh. A later Renew fetches only what the library does not have.
     */
    suspend fun clearFinished() = dao.hideFinished()

    /** Settings > Clear history: really deletes the finished links, and the memory of what each had saved. */
    private suspend fun deleteFinishedLinks() {
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
     * paused and carries on from the files the library already has, so no file is fetched twice.
     */
    suspend fun applyNetworkSetting() {
        if (dao.nextQueued() != null) scheduleDrain(ExistingWorkPolicy.REPLACE)
    }

    /** Starts the queue again after it was paused for lack of storage. */
    suspend fun resume() {
        if (dao.nextQueued() != null) scheduleDrain()
    }

    suspend fun clearFinishedHistory() = deleteFinishedLinks()
}

class LibraryRepository(
    private val dao: LibraryDao,
    private val resolver: ContentResolver,
    private val thumbnails: ThumbnailCache,
    private val previews: PreviewStripCache,
    /** Where favourites are kept: inside the app, not in shared storage. */
    private val favoritesDir: File,
) {
    fun observeFolders(query: String) = dao.observeFolders(query)
    fun observeItems(folderId: Long, query: String) = dao.observeItems(folderId, query)
    fun observeVideos() = dao.observeVideos()

    suspend fun addItem(item: LibraryItem) = dao.insertItem(item)

    suspend fun fileNamesFor(sourceUrl: String) = dao.fileNamesFor(sourceUrl)

    suspend fun fileUrlsFor(sourceUrl: String) = dao.fileUrlsFor(sourceUrl)

    /**
     * A file the link already had is not fetched again: it gets the address it has now and, if it was saved by an earlier
     * download of the same link (the link was removed and added again), it now belongs to this one. Returns true when
     * it was handed over, so the download can count it.
     */
    suspend fun adoptFile(sourceUrl: String, name: String, fileUrl: String, taskId: Long): Boolean {
        var handedOver = false
        for (item in dao.itemsOfLink(sourceUrl).filter { isSameFile(it, name, fileUrl) }) {
            // A site that gives no address this time must not wipe the one the file already has.
            if (fileUrl.isNotBlank() && item.fileUrl != fileUrl) dao.setItemFileUrl(item.id, fileUrl)
            if (item.taskId != taskId) {
                dao.setItemTask(item.id, taskId)
                handedOver = true
            }
        }
        return handedOver
    }

    /**
     * The same file of the same link: by its name (in one fixed spelling, so accents compare equal however they were stored)
     * or by its direct address, which stays the same when a name that carries a position number changes.
     */
    private fun isSameFile(item: LibraryItem, name: String, fileUrl: String): Boolean =
        Normalizer.normalize(item.displayName, Normalizer.Form.NFC) == Normalizer.normalize(name, Normalizer.Form.NFC) ||
            (fileUrl.isNotBlank() && item.fileUrl == fileUrl)

    /**
     * A file the library already has, met again by a refreshed link: it moves into the folder gallery-dl names for it now.
     * [moveFile] moves the file itself where that is possible (it is best effort); the library entry moves either way. A
     * folder this empties is removed, and its tag goes to the new folder when that has none.
     */
    suspend fun moveToFolder(sourceUrl: String, name: String, fileUrl: String, folder: String, moveFile: (LibraryItem) -> Unit) {
        val items = dao.itemsOfLink(sourceUrl).filter { isSameFile(it, name, fileUrl) }
        val existing = dao.folderByName(folder)
        val pending = items.filter { it.folderId != existing?.id }
        if (pending.isEmpty()) return
        val target = ensureFolder(folder)
        for (item in pending) {
            val old = item.folderId
            runCatching { moveFile(item) }
            dao.setItemFolder(item.id, target)
            if (old != null && dao.countInFolder(old) == 0) {
                val tag = dao.folderById(old)?.tag
                if (tag != null && dao.folderById(target)?.tag == null) dao.setFolderTag(target, tag)
                dao.deleteFolder(old)
            }
        }
    }

    fun observeFolderTag(folderId: Long) = dao.observeFolderTag(folderId)

    /** Gives a folder a tag, or takes it off when [tag] is blank. Long tags are cut to 40 characters. */
    suspend fun setFolderTag(folderId: Long, tag: String) = dao.setFolderTag(folderId, tag.trim().take(40).ifEmpty { null })

    /**
     * Copies files into the Favorites folder. The copies live inside the app, so they stay when the link or the folder
     * they came from is deleted, and go only when deleted here. Each copy keeps the link and the file's own address.
     * Returns how many were added; a file already in Favorites is skipped.
     */
    suspend fun addFavorites(items: List<LibraryItem>): Int = withContext(Dispatchers.IO) {
        favoritesDir.mkdirs()
        var added = 0
        for (item in items) {
            if (isFavoriteSource(item)) continue
            val source = favoriteSource(item.sourceUrl)
            val have = dao.itemsOfLink(source)
            if (have.any { isSameFile(it, item.displayName, item.fileUrl.orEmpty()) }) continue
            val target = uniqueFile(item.displayName)
            val copied = runCatching {
                resolver.openInputStream(Uri.parse(item.contentUri))?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
            }.getOrDefault(false)
            if (!copied) {
                target.delete()
                continue
            }
            val copy = LibraryItem(
                contentUri = Uri.fromFile(target).toString(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                sourceUrl = source,
                taskId = null,
                folderId = ensureFolder(FAVORITES_FOLDER),
                addedAt = System.currentTimeMillis(),
                fileUrl = item.fileUrl,
            )
            val id = dao.insertItem(copy)
            previews.requestMake(copy.copy(id = id))
            added++
        }
        added
    }

    private fun isFavoriteSource(item: LibraryItem) = item.sourceUrl.startsWith("favorite:")

    /** A free file name in the favourites folder: the original name, or with a number when it is taken. */
    private fun uniqueFile(name: String): File {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        val base = clean.substringBeforeLast('.', clean)
        val ext = clean.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var file = File(favoritesDir, clean)
        var counter = 1
        while (file.exists()) file = File(favoritesDir, "$base ($counter)$ext").also { counter++ }
        return file
    }

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
            if (uri.scheme == "file") {
                // A favourite: the copy kept inside the app.
                File(requireNotNull(uri.path)).delete()
            } else if (uri.authority == MediaStore.AUTHORITY) {
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
