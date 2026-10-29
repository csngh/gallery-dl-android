package app.gallerydl.download

import app.gallerydl.data.LibraryDao
import app.gallerydl.data.SettingsRepository
import app.gallerydl.data.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What the "folder for this link" question needs: the name gallery-dl would use, and whether a name is already taken. */
class FolderChoice(
    private val bridge: PythonBridge,
    private val settings: SettingsRepository,
    private val tasks: TaskDao,
    private val library: LibraryDao,
) {
    /** Link -> the folder its files are in, kept up to date; links with nothing in the library are not in the map. */
    fun observeFolderNames(): kotlinx.coroutines.flow.Flow<Map<String, String>> =
        library.observeLinkFolders().map { rows -> rows.associate { it.url to it.name } }

    /** The folder gallery-dl would use for a link, or "" when it cannot be found out (no connection, unsupported ...). */
    suspend fun suggest(url: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val options = settings.options.first().toJson(settings.cookiesPath())
            bridge.previewFolder(url, options)
        }.getOrDefault("")
    }

    /** The other links that already use a folder with this name: ones saved in it, and ones given the name. */
    suspend fun linksUsing(name: String, exceptTaskId: Long): List<String> {
        val clean = cleanFolderName(name) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val own = tasks.linksWithFolderName(clean, exceptTaskId)
            val saved = library.linksInFolder(clean)
            (own + saved).distinct()
        }
    }
}
