package app.gallerydl.download

import android.net.Uri
import app.gallerydl.data.DownloadTask

/** gallery-dl categories that describe a kind of link rather than a website; these are grouped by host. */
private val HOST_BASED_CATEGORIES = setOf("", "directlink", "generic", "recursive", "ytdl", "noop", "urls", "text")

/** The host of a link without "www.", for example "upload.wikimedia.org". */
fun hostOf(url: String): String =
    Uri.parse(url).host?.removePrefix("www.")?.lowercase().orEmpty().ifEmpty { "other" }

/**
 * The website a link belongs to. gallery-dl knows the site behind a link (for example "imgur" for both
 * imgur.com and i.imgur.com), so its category is used where it names a website, and the host otherwise.
 */
fun siteKeyFor(category: String?, url: String): String =
    category?.lowercase()?.takeIf { it !in HOST_BASED_CATEGORIES } ?: hostOf(url)

/** Tasks saved before sites were recorded fall back to the host of their link. */
val DownloadTask.siteKey: String get() = site ?: hostOf(url)

/** Second-level labels such as the "co" in example.co.uk, so those sites keep three labels. */
private val SECOND_LEVEL_LABELS = setOf("co", "com", "org", "net", "gov", "edu", "ac", "ne", "or")

/**
 * The host to ask for a website icon: the site's main domain (i.imgur.com becomes imgur.com). Null for
 * addresses that have no public icon, such as localhost and IP addresses.
 */
fun iconHostOf(url: String): String? {
    val host = Uri.parse(url).host?.lowercase() ?: return null
    if ('.' !in host || ':' in host || host.all { it.isDigit() || it == '.' }) return null
    val labels = host.split('.')
    if (labels.size <= 2) return host
    val keep = if (labels[labels.size - 2] in SECOND_LEVEL_LABELS && labels.last().length == 2) 3 else 2
    return labels.takeLast(keep).joinToString(".")
}
