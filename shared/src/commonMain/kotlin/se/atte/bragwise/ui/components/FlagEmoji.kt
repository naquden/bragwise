package se.atte.bragwise.ui.components

/**
 * Converts an ISO-3166 alpha-2 country code to the corresponding flag emoji
 * by combining two Unicode regional-indicator symbols (U+1F1E6 .. U+1F1FF).
 * Both Android and iOS render the pair natively as a colour flag — no image
 * assets or network calls required.
 *
 * Returns an empty string for any input that is not exactly two ASCII letters.
 */
fun flagEmoji(iso2: String): String {
    val upper = iso2.uppercase()
    if (upper.length != 2 || !upper.all { it in 'A'..'Z' }) return ""
    val base = 0x1F1E6 - 'A'.code
    val sb = StringBuilder(4)
    for (char in upper) {
        // Each regional indicator (U+1F1E6..U+1F1FF) is above U+FFFF, so it
        // needs a UTF-16 surrogate pair. `Character.toChars` (JVM-only) is
        // not available in common code — encode manually.
        val codepoint = base + char.code
        val offset = codepoint - 0x10000
        val high = 0xD800 + (offset ushr 10)
        val low = 0xDC00 + (offset and 0x3FF)
        sb.append(high.toChar())
        sb.append(low.toChar())
    }
    return sb.toString()
}
