package net.mrowser.data

import net.mrowser.web.IncomingUrl
import net.mrowser.web.UrlHost

/**
 * Pure: reads the Netscape bookmark format every desktop browser exports as `bookmarks.html`.
 *
 * Folders are flattened — the TV grid has no folders — so the result is every web link in file
 * order. Only `http`/`https` survive (a favorite's url goes straight to `loadUrl`); other links
 * count as skipped. The scan is a hand-written walk rather than one regex: Firefox puts a
 * multi-KB `ICON="data:…"` on every anchor, and a backtracking pattern over that is a cliff.
 */
object BookmarksHtml {

    private val dtAnchor = Regex("<dt>\\s*<a\\s", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^<>]*>")
    private val spaces = Regex("\\s+")
    private val entity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,6});")

    /** True for an export: the Netscape doctype, or a `<dt>` straight into an `<a `. */
    fun looksLike(text: String): Boolean =
        text.contains("NETSCAPE-Bookmark-file-1", ignoreCase = true) || dtAnchor.containsMatchIn(text)

    fun parse(text: String): FavoritesBackup.Parsed {
        val favorites = ArrayList<Favorite>()
        var skipped = 0
        var pos = 0
        while (true) {
            val open = indexOfAnchor(text, pos)
            if (open < 0) break
            val tagEnd = tagEnd(text, open + 2)
            if (tagEnd < 0) break
            val close = text.indexOf("</a", tagEnd + 1, ignoreCase = true)
            val innerEnd = if (close < 0) text.length else close
            pos = if (close < 0) text.length else close + 3

            val href = attribute(text, open + 2, tagEnd, "href") ?: continue
            val url = IncomingUrl.webUrlOrNull(decode(href))
            if (url == null) {
                skipped++
                continue
            }
            val title = decode(text.substring(tagEnd + 1, innerEnd).replace(tag, ""))
                .replace(spaces, " ").trim()
                .ifEmpty { UrlHost.of(url) ?: url }
            favorites += Favorite(title, url)
        }
        return FavoritesBackup.Parsed(favorites, skipped, FavoritesBackup.Source.BOOKMARKS_HTML)
    }

    /** Index of the next `<a` followed by whitespace (not `<abbr>`, `<address>`), or -1. */
    private fun indexOfAnchor(text: String, from: Int): Int {
        var i = from
        while (true) {
            i = text.indexOf("<a", i, ignoreCase = true)
            if (i < 0) return -1
            if (i + 2 < text.length && text[i + 2].isWhitespace()) return i
            i += 2
        }
    }

    /** Index of the `>` closing the tag whose attributes start at [from], quotes respected; -1 if
     *  the tag never ends. */
    private fun tagEnd(text: String, from: Int): Int {
        var i = from
        var quote = '\u0000'
        while (i < text.length) {
            val c = text[i]
            if (quote != '\u0000') {
                if (c == quote) quote = '\u0000'
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        return -1
    }

    /** The raw value of attribute [name] in `text[from, to)`, or null. One forward pass. */
    private fun attribute(text: String, from: Int, to: Int, name: String): String? {
        var i = from
        while (i < to) {
            while (i < to && (text[i].isWhitespace() || text[i] == '/')) i++
            val nameStart = i
            while (i < to && !text[i].isWhitespace() && text[i] != '=' && text[i] != '/') i++
            val attr = text.substring(nameStart, i)
            while (i < to && text[i].isWhitespace()) i++
            var value: String? = null
            if (i < to && text[i] == '=') {
                i++
                while (i < to && text[i].isWhitespace()) i++
                if (i < to && (text[i] == '"' || text[i] == '\'')) {
                    val q = text[i]
                    val end = text.indexOf(q, i + 1).let { if (it < 0 || it > to) to else it }
                    value = text.substring(i + 1, end)
                    i = end + 1
                } else {
                    val start = i
                    while (i < to && !text[i].isWhitespace()) i++
                    value = text.substring(start, i)
                }
            }
            if (attr.equals(name, ignoreCase = true) && value != null) return value
            if (i == nameStart) i++
        }
        return null
    }

    private fun decode(s: String): String {
        if (s.indexOf('&') < 0) return s
        return entity.replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body[0] == '#' -> {
                    val code = if (body[1] == 'x' || body[1] == 'X') body.substring(2).toIntOrNull(16)
                    else body.substring(1).toIntOrNull()
                    if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF && code != 0)
                        String(Character.toChars(code))
                    else m.value
                }
                else -> when (body.lowercase()) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "nbsp" -> " "
                    else -> m.value
                }
            }
        }
    }
}
