package app.gallerydl.data

import app.gallerydl.share.firstLinkIn

/**
 * The plain-text list of links that Home exports and imports: one link per line, nothing else, so a list written by any
 * other tool or typed by hand works too.
 */
object LinkList {
    /** What a line of an imported file turned out to be. */
    sealed interface Line {
        data class Link(val url: String) : Line
        /** A line with text but no http or https link in it. */
        data object NotALink : Line
    }

    /**
     * The lines of [text] that matter: blank lines and lines starting with # (comments) are ignored, and a line with
     * words around its link ("My album https://example.com/a/1") counts as that link.
     */
    fun parse(text: String): List<Line> = text.lineSequence()
        .map { it.trim().removePrefix("\uFEFF") }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line -> firstLinkIn(line)?.let { Line.Link(it) } ?: Line.NotALink }
        .toList()

    /** The file written by an export: every link once, in the order they were added. */
    fun format(urls: List<String>): String = urls.distinct().joinToString("\n", postfix = "\n")

    /**
     * The sheet written by the Favorites export, a CSV that Excel opens: one row per video, with the link it came from
     * and the video's own link. A row repeated by two copies of the same video is written once.
     */
    fun formatFavoriteVideos(rows: List<Pair<String, String>>): String {
        fun cell(text: String) = "\"" + text.replace("\"", "\"\"") + "\""
        return rows.distinct().joinToString("\r\n", prefix = "\uFEFF\"Parent link\",\"Video link\"\r\n", postfix = "\r\n") {
            cell(it.first) + "," + cell(it.second)
        }
    }
}

/** What an import did with the links it found. */
data class ImportResult(
    val added: Int = 0,
    /** Already in the list (or repeated in the file), so left alone. */
    val duplicates: Int = 0,
    /** Links to websites gallery-dl does not support. */
    val unsupported: Int = 0,
    /** Lines that were not links at all. */
    val notLinks: Int = 0,
) {
    val found get() = added + duplicates + unsupported + notLinks
}
