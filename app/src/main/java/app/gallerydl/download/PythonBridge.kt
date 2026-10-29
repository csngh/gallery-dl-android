package app.gallerydl.download

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

/** Called from the Python side while gallery-dl runs. Methods are invoked on the worker's IO thread. */
interface DownloadListener {
    fun isCancelled(): Boolean
    fun onFile(path: String, sourceUrl: String)

    /** A file gallery-dl already had (so nothing was saved): its [name] and the address it has now. */
    fun onSkipped(name: String, fileUrl: String, folder: String)

    /** [count] is 0 when the number of files is unknown; [percent] is -1 when the file size is unknown. */
    fun onProgress(index: Int, count: Int, percent: Int, fileName: String)
}

data class DownloadOutcome(val status: Int, val failed: Int, val errors: List<String>)

/** Thin wrapper around the embedded gallery-dl (see `python/gdl_bridge.py`). */
class PythonBridge(private val context: Context) {

    private val module by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        Python.getInstance().getModule("gdl_bridge")
    }

    fun isSupported(url: String): Boolean = module.callAttr("is_supported", url).toBoolean()

    fun galleryDlVersion(): String = module.callAttr("gallery_dl_version").toString()

    fun siteOf(url: String): String = module.callAttr("site_of", url).toString()

    /** The folder gallery-dl would save the first file of a link in; "" when it cannot tell. May take a few seconds. */
    fun previewFolder(url: String, optionsJson: String): String = module.callAttr("preview_folder", url, optionsJson).toString()

    fun download(
        url: String,
        baseDir: String,
        archivePath: String,
        optionsJson: String,
        listener: DownloadListener,
        knownFiles: List<String> = emptyList(),
        knownUrls: List<String> = emptyList(),
    ): DownloadOutcome {
        val known = JSONObject().put("names", org.json.JSONArray(knownFiles)).put("urls", org.json.JSONArray(knownUrls)).toString()
        val json = JSONObject(module.callAttr("download", url, baseDir, archivePath, optionsJson, listener, known).toString())
        val errors = json.getJSONArray("errors")
        return DownloadOutcome(
            status = json.getInt("status"),
            failed = json.getInt("failed"),
            errors = List(errors.length()) { errors.getString(it) },
        )
    }
}
