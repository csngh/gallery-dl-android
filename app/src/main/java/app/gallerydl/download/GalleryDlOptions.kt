package app.gallerydl.download

import org.json.JSONArray
import org.json.JSONObject

/** Seconds to wait, written as one number ("2") or a range ("1.0-3.0"). */
object DurationInput {
    private val pattern = Regex("""^\s*(\d+(?:\.\d+)?)\s*(?:-\s*(\d+(?:\.\d+)?))?\s*$""")
    private const val MAX_SECONDS = 300.0

    /** The lower and upper bound (equal for a single number), or null if the text is not valid. */
    fun parse(text: String): List<Double>? {
        val match = pattern.matchEntire(text) ?: return null
        val lower = match.groupValues[1].toDouble()
        val upper = match.groupValues[2].ifEmpty { null }?.toDouble() ?: lower
        return if (lower <= upper && upper <= MAX_SECONDS) listOf(lower, upper) else null
    }
}

/** Whole numbers and single seconds values, with the limits the app allows. */
object NumberInput {
    fun parseInt(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

    fun parseSeconds(text: String, range: ClosedFloatingPointRange<Double>): Double? =
        text.trim().toDoubleOrNull()?.takeIf { it in range }
}

val RETRIES_RANGE = 0..20
val TIMEOUT_RANGE = 1.0..300.0

/**
 * The gallery-dl options the user can switch on or off. Each value is kept even while its switch is off, so
 * turning an option back on restores what was typed before.
 */
data class GalleryDlOptions(
    val apiRest: Boolean = false,
    val sleepRequestEnabled: Boolean = false,
    val sleepRequest: String = DEFAULT_SLEEP_REQUEST,
    val sleepEnabled: Boolean = false,
    val sleep: String = DEFAULT_SLEEP,
    val retriesEnabled: Boolean = false,
    val retries: String = DEFAULT_RETRIES,
    val timeoutEnabled: Boolean = false,
    val timeout: String = DEFAULT_TIMEOUT,
    /** On by default. Off downloads files again even if they were saved before, which can create duplicates. */
    val skipExisting: Boolean = true,
    val wifiOnly: Boolean = false,
    val cookiesEnabled: Boolean = false,
) {
    /** Only the switched-on options, under gallery-dl's own option names, as JSON for the Python side. */
    fun toJson(cookiesPath: String? = null): String {
        val json = JSONObject()
        if (retriesEnabled) NumberInput.parseInt(retries, RETRIES_RANGE)?.let { json.put("retries", it) }
        if (timeoutEnabled) NumberInput.parseSeconds(timeout, TIMEOUT_RANGE)?.let { json.put("timeout", it) }
        if (!skipExisting) json.put("skip", false)
        if (cookiesEnabled && cookiesPath != null) json.put("cookies", cookiesPath)
        if (apiRest) json.put("api", "rest")
        if (sleepRequestEnabled) DurationInput.parse(sleepRequest)?.let { json.put("sleep-request", it.toSeconds()) }
        if (sleepEnabled) DurationInput.parse(sleep)?.let { json.put("sleep", it.toSeconds()) }
        return json.toString()
    }

    /** gallery-dl takes a plain number, or a two-item list for a random wait in that range. */
    private fun List<Double>.toSeconds(): Any =
        if (this[0] == this[1]) this[0] else JSONArray().put(this[0]).put(this[1])

    companion object {
        const val DEFAULT_SLEEP_REQUEST = "4.0-8.0"
        const val DEFAULT_SLEEP = "1.0-3.0"
        const val DEFAULT_RETRIES = "4"
        const val DEFAULT_TIMEOUT = "30"
    }
}
