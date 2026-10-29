package app.gallerydl.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** PENDING is a link that was added but not started yet; nothing downloads until the user starts it. */
enum class TaskStatus { PENDING, QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

/** What the user cares about: a COMPLETED task with failed files or errors is only partly successful. */
enum class TaskResult { READY, IN_PROGRESS, COMPLETED, PARTIAL, FAILED, CANCELLED }

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val filesDownloaded: Int = 0,
    val currentFile: String? = null,
    val error: String? = null,
    @ColumnInfo(defaultValue = "0") val failedFiles: Int = 0,
    /** 1-based position of the file being downloaded in this run; 0 if none yet. */
    @ColumnInfo(defaultValue = "0") val fileIndex: Int = 0,
    /** Number of files in the link when the site reports it reliably; 0 if unknown. */
    @ColumnInfo(defaultValue = "0") val totalFiles: Int = 0,
    /** Percent (0-100) of the current file, or -1 when its size is unknown. */
    @ColumnInfo(defaultValue = "-1") val fileProgress: Int = -1,
    /** The website of the link (gallery-dl category, or the host); null for tasks saved before this existed. */
    val site: String? = null,
    val createdAt: Long,
    val finishedAt: Long? = null,
    /** Set by "Clear finished" on the Queue: the link no longer shows there, but stays on Home and in Activity. */
    @ColumnInfo(defaultValue = "0") val hiddenFromQueue: Boolean = false,
    /** The library folder the user chose for this link before it started; null means the folder gallery-dl names. */
    val folderName: String? = null,
)

@Entity(tableName = "folders", indices = [Index("name", unique = true)])
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** A label the user gave the folder; it shows as "name / tag". */
    val tag: String? = null,
)

@Entity(
    tableName = "library_items",
    foreignKeys = [
        ForeignKey(Folder::class, ["id"], ["folderId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(DownloadTask::class, ["id"], ["taskId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("folderId"), Index("taskId")],
)
data class LibraryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val sourceUrl: String,
    val taskId: Long?,
    val folderId: Long? = null,
    val addedAt: Long,
    /** The direct address this file was downloaded from; null for files saved before it was kept. */
    val fileUrl: String? = null,
)

val DownloadTask.result: TaskResult
    get() = when (status) {
        TaskStatus.PENDING -> TaskResult.READY
        TaskStatus.QUEUED, TaskStatus.DOWNLOADING -> TaskResult.IN_PROGRESS
        TaskStatus.COMPLETED -> if (failedFiles > 0 || error != null) TaskResult.PARTIAL else TaskResult.COMPLETED
        TaskStatus.FAILED -> TaskResult.FAILED
        TaskStatus.CANCELLED -> TaskResult.CANCELLED
    }

/** Overall progress of a running download in 0..1, or null if it cannot be determined. */
val DownloadTask.overallProgress: Float?
    get() {
        val file = if (fileProgress >= 0) fileProgress / 100f else 0f
        val value = when {
            totalFiles > 0 && fileIndex > 0 -> ((fileIndex - 1) + file) / totalFiles
            fileProgress >= 0 -> file
            else -> return null
        }
        return value.coerceIn(0f, 1f)
    }
