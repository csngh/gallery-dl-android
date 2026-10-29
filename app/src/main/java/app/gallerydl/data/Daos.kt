package app.gallerydl.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gallerydl.download.folderTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY id DESC")
    fun observeAll(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY id LIMIT 1")
    suspend fun nextQueued(): DownloadTask?

    /** Links that are waiting for their turn or downloading now. */
    @Query("SELECT id FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY id")
    suspend fun activeIds(): List<Long>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = 'QUEUED'")
    suspend fun countQueued(): Int

    @Insert
    suspend fun insert(task: DownloadTask): Long

    /** Moves one waiting link into the download queue. Returns 0 if it is no longer waiting. */
    @Query("UPDATE download_tasks SET status = 'QUEUED' WHERE id = :id AND status = 'PENDING'")
    suspend fun start(id: Long): Int

    /** The folder the user chose for a link; it keeps this name for every later refresh. */
    @Query("UPDATE download_tasks SET folderName = :folderName WHERE id = :id")
    suspend fun setFolderName(id: Long, folderName: String?)

    /** The other links that were given this folder name (case does not matter). */
    @Query("SELECT url FROM download_tasks WHERE folderName = :name COLLATE NOCASE AND id != :id")
    suspend fun linksWithFolderName(name: String, id: Long): List<String>

    /** Moves every waiting link into the download queue. Returns how many were started. */
    @Query("UPDATE download_tasks SET status = 'QUEUED' WHERE status = 'PENDING'")
    suspend fun startAll(): Int

    /** Changes the link of one that has not started; it stays waiting. Returns 0 if it already started. */
    @Query("UPDATE download_tasks SET url = :url, site = :site WHERE id = :id AND status = 'PENDING'")
    suspend fun replacePendingLink(id: Long, url: String, site: String?): Int

    /** Returns 0 if the task is no longer queued, e.g. the user cancelled it in the meantime. */
    @Query("UPDATE download_tasks SET status = 'DOWNLOADING', error = NULL, fileIndex = 0, totalFiles = 0, fileProgress = -1 WHERE id = :id AND status = 'QUEUED'")
    suspend fun markDownloading(id: Long): Int

    /** The worker was stopped (e.g. the network dropped): wait in the queue to be resumed. */
    @Query("UPDATE download_tasks SET status = 'QUEUED', currentFile = NULL, fileIndex = 0, totalFiles = 0, fileProgress = -1 WHERE id = :id AND status = 'DOWNLOADING'")
    suspend fun pause(id: Long)

    /** The number of files saved is what the library holds for the link, so it stays right after deletions and refreshes. */
    @Query("UPDATE download_tasks SET filesDownloaded = (SELECT COUNT(*) FROM library_items WHERE sourceUrl = download_tasks.url) WHERE id = :id")
    suspend fun fileSaved(id: Long)

    @Query("UPDATE download_tasks SET fileIndex = :index, totalFiles = :total, fileProgress = :percent, currentFile = :file WHERE id = :id AND status = 'DOWNLOADING'")
    suspend fun updateProgress(id: Long, index: Int, total: Int, percent: Int, file: String)

    @Query("SELECT filesDownloaded FROM download_tasks WHERE id = :id")
    suspend fun countSaved(id: Long): Int

    @Query("UPDATE download_tasks SET status = :status, error = :error, failedFiles = :failedFiles, currentFile = NULL, fileIndex = 0, totalFiles = 0, fileProgress = -1, finishedAt = :finishedAt WHERE id = :id")
    suspend fun finish(id: Long, status: TaskStatus, error: String?, failedFiles: Int, finishedAt: Long)

    @Query("UPDATE download_tasks SET status = 'CANCELLED', finishedAt = :finishedAt WHERE id = :id AND status = 'QUEUED'")
    suspend fun cancelIfQueued(id: Long, finishedAt: Long): Int

    @Query("UPDATE download_tasks SET status = 'QUEUED' WHERE status = 'DOWNLOADING'")
    suspend fun resetInterrupted()

    @Query("UPDATE download_tasks SET status = 'QUEUED', currentFile = NULL, error = NULL, failedFiles = 0, finishedAt = NULL, hiddenFromQueue = 0 WHERE id = :id")
    suspend fun requeue(id: Long)

    /**
     * Gives a failed or cancelled task a new link and queues it again from a clean start. Returns 0 if the
     * task is not in one of those states, so a running download is never touched.
     */
    @Query(
        """
        UPDATE download_tasks SET url = :url, site = :site, status = 'QUEUED', filesDownloaded = 0, failedFiles = 0,
            currentFile = NULL, error = NULL, fileIndex = 0, totalFiles = 0, fileProgress = -1, finishedAt = NULL, hiddenFromQueue = 0
        WHERE id = :id AND status IN ('FAILED', 'CANCELLED')
        """
    )
    suspend fun replaceLink(id: Long, url: String, site: String?): Int

    /** Failed links and links that finished with failed files or errors. Saved files are kept. */
    @Query(
        """
        UPDATE download_tasks SET status = 'QUEUED', currentFile = NULL, error = NULL, failedFiles = 0, finishedAt = NULL, hiddenFromQueue = 0
        WHERE status = 'FAILED' OR (status = 'COMPLETED' AND (failedFiles > 0 OR error IS NOT NULL))
        """
    )
    suspend fun requeueFailed()

    /**
     * Queues every link that is not already queued or running, whatever it did last: links not started yet start, and
     * finished, failed and cancelled ones check for new files. Returns how many were queued.
     */
    @Query(
        """
        UPDATE download_tasks SET status = 'QUEUED', currentFile = NULL, error = NULL, failedFiles = 0, finishedAt = NULL, hiddenFromQueue = 0
        WHERE status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')
        """
    )
    suspend fun renewAll(): Int

    @Query("SELECT id FROM download_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun finishedIds(): List<Long>

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM download_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun deleteFinished()

    /** Takes the finished links off the Queue screen. They stay in the list, on Home and in Activity. */
    @Query("UPDATE download_tasks SET hiddenFromQueue = 1 WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun hideFinished()
}

/** The library folder a link's files are in, for showing the folder's name beside the link. */
data class LinkFolder(val url: String, val name: String)

/** One row per link folder in the library, with a cover and the number of items. */
data class FolderSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val coverId: Long?,
    val coverUri: String?,
    val coverMime: String?,
    val sourceUrl: String?,
    val tag: String?,
) {
    /** What the library shows: the folder's own name, exactly as gallery-dl made it. */
    val displayName: String get() = name

    /** What the library shows: the name, then " / tag" when the folder has a tag. */
    val title: String get() = folderTitle(displayName, tag)
}

@Dao
interface LibraryDao {
    /** Only folders that still contain items. Most recently updated first. */
    @Query(
        """
        SELECT f.id AS id, f.name AS name, COUNT(i.id) AS itemCount,
               (SELECT c.id FROM library_items c WHERE c.folderId = f.id
                ORDER BY c.addedAt DESC, c.id DESC LIMIT 1) AS coverId,
               (SELECT c.contentUri FROM library_items c WHERE c.folderId = f.id
                ORDER BY c.addedAt DESC, c.id DESC LIMIT 1) AS coverUri,
               (SELECT c.mimeType FROM library_items c WHERE c.folderId = f.id
                ORDER BY c.addedAt DESC, c.id DESC LIMIT 1) AS coverMime,
               (SELECT c.sourceUrl FROM library_items c WHERE c.folderId = f.id
                ORDER BY c.addedAt DESC, c.id DESC LIMIT 1) AS sourceUrl,
               f.tag AS tag
        FROM folders f JOIN library_items i ON i.folderId = f.id
        WHERE (:query = '' OR f.name LIKE '%' || :query || '%' OR f.tag LIKE '%' || :query || '%')
        GROUP BY f.id
        ORDER BY MAX(i.addedAt) DESC
        """
    )
    fun observeFolders(query: String): Flow<List<FolderSummary>>

    @Query(
        """
        SELECT * FROM library_items
        WHERE folderId = :folderId AND (:query = '' OR displayName LIKE '%' || :query || '%')
        ORDER BY addedAt DESC, id DESC
        """
    )
    fun observeItems(folderId: Long, query: String): Flow<List<LibraryItem>>

    /** Every video that belongs to a folder, for the live previews on the folder covers. */
    @Query("SELECT * FROM library_items WHERE folderId IS NOT NULL AND mimeType LIKE 'video/%' ORDER BY addedAt DESC, id DESC")
    fun observeVideos(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM library_items")
    suspend fun allItems(): List<LibraryItem>

    /** For every link with files in the library, the name of a folder they are in. */
    @Query("SELECT i.sourceUrl AS url, f.name AS name FROM library_items i JOIN folders f ON f.id = i.folderId GROUP BY i.sourceUrl")
    fun observeLinkFolders(): Flow<List<LinkFolder>>

    /** The links whose files sit in the folder with this name (case does not matter). */
    @Query("SELECT DISTINCT i.sourceUrl FROM library_items i JOIN folders f ON f.id = i.folderId WHERE f.name = :name COLLATE NOCASE")
    suspend fun linksInFolder(name: String): List<String>

    /** The names of the files the library already has for a link. */
    @Query("SELECT displayName FROM library_items WHERE sourceUrl = :sourceUrl")
    suspend fun fileNamesFor(sourceUrl: String): List<String>

    /** The direct addresses of the files the library already has for a link. */
    @Query("SELECT fileUrl FROM library_items WHERE sourceUrl = :sourceUrl AND fileUrl IS NOT NULL AND fileUrl != ''")
    suspend fun fileUrlsFor(sourceUrl: String): List<String>

    /** Every library entry saved for a link. */
    @Query("SELECT * FROM library_items WHERE sourceUrl = :sourceUrl")
    suspend fun itemsOfLink(sourceUrl: String): List<LibraryItem>

    @Query("UPDATE library_items SET fileUrl = :fileUrl WHERE id = :id")
    suspend fun setItemFileUrl(id: Long, fileUrl: String)

    @Query("UPDATE library_items SET taskId = :taskId WHERE id = :id")
    suspend fun setItemTask(id: Long, taskId: Long)

    @Query("UPDATE library_items SET folderId = :folderId WHERE id = :id")
    suspend fun setItemFolder(id: Long, folderId: Long)

    @Query("SELECT COUNT(*) FROM library_items WHERE folderId = :folderId")
    suspend fun countInFolder(folderId: Long): Int

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun folderById(id: Long): Folder?

    @Query("SELECT * FROM library_items WHERE id IN (:ids)")
    suspend fun itemsByIds(ids: Collection<Long>): List<LibraryItem>

    @Query("SELECT * FROM library_items WHERE folderId = :folderId")
    suspend fun itemsInFolder(folderId: Long): List<LibraryItem>

    @Insert
    suspend fun insertItem(item: LibraryItem): Long

    @Delete
    suspend fun deleteItem(item: LibraryItem)

    @Query("SELECT * FROM folders WHERE name = :name")
    suspend fun folderByName(name: String): Folder?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: Folder): Long

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE folders SET tag = :tag WHERE id = :id")
    suspend fun setFolderTag(id: Long, tag: String?)

    @Query("SELECT tag FROM folders WHERE id = :id")
    fun observeFolderTag(id: Long): Flow<String?>
}
