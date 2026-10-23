package app.gallerydl.share

private val LINK = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
private const val TRAILING_PUNCTUATION = ".,;:!?)]}"

/**
 * The first http or https link in shared text. Browsers and other apps often add a title before the link
 * ("Cool album https://example.com/a/1"), and punctuation can follow it, so both are dropped.
 */
fun firstLinkIn(text: String?): String? =
    text?.let { LINK.find(it)?.value?.trimEnd { c -> c in TRAILING_PUNCTUATION } }?.takeIf { it.length > "https://".length }
