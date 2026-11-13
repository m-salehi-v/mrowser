package net.mrowser.download

import java.net.URI
import java.net.URISyntaxException

/**
 * Pure: turns whatever a server or a page offers as a file name into one safe file name, and
 * cleans the other two download labels (MIME type, name shown in the list).
 */
object DownloadFileName {

    const val MAX_LENGTH = 120
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
        if (name.length <= MAX_LENGTH) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot >= 0) name.substring(dot) else ""
        return if (ext.length - 1 in 1..MAX_EXTENSION) {
            name.substring(0, MAX_LENGTH - ext.length) + ext
        } else {
            name.substring(0, MAX_LENGTH)
        }
    }

    /** The MIME type without parameters, lowercased; null for a blank or generic one. */
    fun cleanMime(mime: String?): String? {
        val type = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return if (type.isEmpty() || type in GENERIC_MIME) null else type
    }

    /** The name the saved file really has (the system may have renamed it), else the title. */
    fun displayName(title: String?, localUri: String?): String {
        if (localUri != null) {
            val segment = try {
                URI(localUri).path?.substringAfterLast('/')
            } catch (e: URISyntaxException) {
                null
            }
            if (!segment.isNullOrBlank()) return segment
        }
        return title?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_DISPLAY
    }

    private fun isBidi(c: Char): Boolean =
        c == '‎' || c == '‏' || c in '‪'..'‮' || c in '⁦'..'⁩'
}
