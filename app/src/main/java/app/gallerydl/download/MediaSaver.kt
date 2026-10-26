package app.gallerydl.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

data class SavedMedia(val uri: Uri, val displayName: String, val mimeType: String)

/**
 * Copies downloaded files into shared storage: either a folder the user picked (SAF), or, by default,
 * the matching MediaStore collection so any gallery or file manager can see them.
 */
class MediaSaver(private val context: Context) {

    /** [subDir] is a relative path ("/"-separated) created under the destination root. */
    fun save(file: File, subDir: String, tree: Uri?): SavedMedia {
        val mime = mimeTypeOf(file)
        return if (tree != null) saveToTree(file, subDir, tree, mime) else saveToMediaStore(file, subDir, mime)
    }

    /** True if [tree] is still granted and writable. */
    fun isTreeWritable(tree: Uri): Boolean = DocumentFile.fromTreeUri(context, tree)?.canWrite() == true

    private fun saveToTree(file: File, subDir: String, tree: Uri, mime: String): SavedMedia {
        var dir = DocumentFile.fromTreeUri(context, tree)?.takeIf { it.canWrite() }
            ?: throw IOException("Download folder is not accessible")
        for (name in subDir.split('/').filter { it.isNotEmpty() }) {
            dir = dir.findFile(name)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(name)
                ?: throw IOException("Cannot create folder $name")
        }
        val doc = dir.createFile(mime, file.name) ?: throw IOException("Cannot create ${file.name}")
        try {
            val out = context.contentResolver.openOutputStream(doc.uri) ?: throw IOException("Cannot open ${doc.uri}")
            out.use { sink -> file.inputStream().use { it.copyTo(sink) } }
        } catch (e: Exception) {
            doc.delete()
            throw e
        }
        return SavedMedia(doc.uri, doc.name ?: file.name, mime)
    }

    private fun saveToMediaStore(file: File, subDir: String, mime: String): SavedMedia {
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val (collection, root) = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(volume) to Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(volume) to Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(volume) to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.getContentUri(volume) to Environment.DIRECTORY_DOWNLOADS
        }
        val relativePath = listOf(root, APP_DIR, subDir).filter { it.isNotEmpty() }.joinToString("/")

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri")
            out.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SavedMedia(uri, file.name, mime)
    }

    private fun mimeTypeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    private companion object {
        const val APP_DIR = "GalleryDl"
    }
}

/** Short, readable name for a link folder: the last path segment, e.g. "AbC123" or "My Photo". */
fun linkDisplayName(url: String?, fallback: String): String {
    val uri = url?.let { Uri.parse(it) } ?: return fallback
    val segment = uri.lastPathSegment ?: uri.host.orEmpty().removePrefix("www.")
    val extension = segment.substringAfterLast('.', "")
    val withoutExtension = if (extension.length in 2..4) segment.substringBeforeLast('.') else segment
    return withoutExtension.replace('_', ' ').trim().ifEmpty { fallback }
}

/** The title a folder shows: its tag when the user gave it one (the name moves to the line below), its name otherwise. */
fun folderTitle(displayName: String, tag: String?): String =
    if (tag.isNullOrBlank()) displayName else tag.trim()

/** The folder's own name, shown beside the item count while its tag is the title; null when the name is the title. */
fun folderSubtitle(displayName: String, tag: String?): String? =
    if (tag.isNullOrBlank()) null else displayName

/** Folder name for one link: host plus the last two path segments, e.g. "imgur.com_a_AbC123". */
fun linkFolderName(url: String): String {
    val uri = Uri.parse(url)
    val host = uri.host.orEmpty().removePrefix("www.")
    return (listOf(host) + uri.pathSegments.takeLast(2))
        .filter { it.isNotEmpty() }
        .joinToString("_")
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.')
        .take(60)
        .ifEmpty { "download" }
}
