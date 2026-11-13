package net.mrowser.download

import java.net.URI
import java.net.URISyntaxException

/**
 * Pure: turns whatever a server or a page offers as a file name into one safe file name, and
 * cleans the other two download labels (MIME type, name shown in the list).
 */
object DownloadFileName {

    const val MAX_LENGTH = 120

    /** ext4 allows 255 bytes; 120 CJK characters are 360, so the length is capped in bytes too. */
    const val MAX_BYTES = 240
    private const val FALLBACK = "download"
    private const val FALLBACK_DISPLAY = "Download"
    private const val MAX_EXTENSION = 10

    private val RESERVED = setOf(':', '*', '?', '"', '<', '>', '|')
    private val GENERIC_MIME = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/force-download",
        "application/x-download",
        "application/unknown",
    )

    /** One safe file name, never a path: it lands directly in Downloads. */
    fun sanitize(raw: String): String {
        val last = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = StringBuilder(last.length)
        for (c in last) {
            when {
                c == '\t' || c == '\n' || c == '\r' -> cleaned.append(' ')
                c < ' ' || c == '\u007F' || isBidi(c) -> Unit
                c in RESERVED -> cleaned.append('_')
                else -> cleaned.append(c)
            }
        }
        val name = cleaned.toString().replace(Regex("\\s+"), " ").trim { it == ' ' || it == '.' }
        if (name.isEmpty()) return FALLBACK
        if (fits(name, MAX_LENGTH, MAX_BYTES)) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot >= 0 && name.length - dot - 1 in 1..MAX_EXTENSION) name.substring(dot) else ""
        val base = cut(
            name.substring(0, name.length - ext.length),
            MAX_LENGTH - ext.length,
            MAX_BYTES - utf8Length(ext),
        )
        return (base.ifEmpty { FALLBACK }) + ext
    }

    /** The longest start of [text] within both budgets, never splitting a surrogate pair. */
    private fun cut(text: String, maxChars: Int, maxBytes: Int): String {
        var end = 0
        var bytes = 0
        while (end < text.length) {
            val step = if (Character.isHighSurrogate(text[end]) && end + 1 < text.length) 2 else 1
            val size = utf8Length(text.substring(end, end + step))
            if (end + step > maxChars || bytes + size > maxBytes) break
            end += step
            bytes += size
        }
        return text.substring(0, end).trimEnd(' ', '.')
    }

    private fun fits(text: String, maxChars: Int, maxBytes: Int) =
        text.length <= maxChars && utf8Length(text) <= maxBytes

    private fun utf8Length(text: String) = text.toByteArray(Charsets.UTF_8).size

    /** The MIME type without parameters, lowercased; null for a blank or generic one. */
    fun cleanMime(mime: String?): String? {
        val type = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return if (type.isEmpty() || type in GENERIC_MIME) null else type
    }

    /**
     * The name the saved file really has (the system may have renamed it), else the title. Only a
     * `file:` URI names the file: a `content://media/…/1234` ends in a row id, and so does no name
     * worth showing when the segment is purely digits.
     */
    fun displayName(title: String?, localUri: String?): String {
        if (localUri != null) {
            val segment = try {
                val uri = URI(localUri)
                if (uri.scheme.equals("file", ignoreCase = true)) uri.path?.substringAfterLast('/') else null
            } catch (e: URISyntaxException) {
                null
            }
            if (!segment.isNullOrBlank() && !segment.all { it in '0'..'9' }) return segment
        }
        return title?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_DISPLAY
    }

    private fun isBidi(c: Char): Boolean =
        c == '\u200E' || c == '\u200F' || c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069'
}
