package net.mrowser.data

import net.mrowser.web.IncomingUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Pure: the export/import file format for favorites (issue #48).
 *
 * The export wraps [FavoritesJson]'s array in `{"app":"mrowser","version":1,"favorites":[…]}` so
 * a wrong file is detectable; import also takes a bare array, which is exactly the app's own
 * `favorites.json`. Unlike [FavoritesJson.fromJson], parsing is per entry — one bad entry is
 * skipped, not the whole file — and only `http`/`https` urls survive: the file came from outside
 * the app, and a favorite's url goes straight to `loadUrl`, which would also run `javascript:` or
 * read the app's private `file:`s.
 */
object FavoritesBackup {

    const val FILE_NAME = "mrowser-favorites.json"
    const val MIME = "application/json"

    private const val APP = "mrowser"
    private const val VERSION = 1

    /** Where an imported file came from: this app's own export, or a browser's bookmarks.html. */
    enum class Source { MROWSER, BOOKMARKS_HTML }

    /** [skipped] counts entries dropped as malformed or not a web page, for the import message. */
    data class Parsed(val favorites: List<Favorite>, val skipped: Int, val source: Source = Source.MROWSER)

    data class Summary(val added: Int, val alreadyPresent: Int)

    fun toJson(items: List<Favorite>): String =
        JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("favorites", JSONArray(FavoritesJson.toJson(items)))
            .toString(2)

    /** The favorites in [text], possibly none; null when it is not a favorites file at all. JSON
     *  first, then a browser's bookmarks.html. */
    fun parse(text: String): Parsed? =
        parseJson(text) ?: if (BookmarksHtml.looksLike(text)) BookmarksHtml.parse(text) else null

    private fun parseJson(text: String): Parsed? {
        val root = try {
            JSONTokener(text).nextValue()
        } catch (e: JSONException) {
            return null
        }
        val entries = when (root) {
            is JSONArray -> root
            is JSONObject ->
                if (root.opt("app") == APP) root.opt("favorites") as? JSONArray ?: return null
                else return null
            else -> return null
        }
        val favorites = (0 until entries.length()).mapNotNull { entry(entries.opt(it)) }
        return Parsed(favorites, skipped = entries.length() - favorites.size)
    }

    /** What [FavoritesOps.merge] of [imported] into [existing] adds, and what it skips. */
    fun summarize(existing: List<Favorite>, imported: List<Favorite>): Summary {
        val have = existing.mapTo(HashSet()) { it.url }
        val urls = imported.mapTo(LinkedHashSet()) { it.url }
        val present = urls.count { it in have }
        return Summary(added = urls.size - present, alreadyPresent = present)
    }

    private fun entry(value: Any?): Favorite? {
        val o = value as? JSONObject ?: return null
        val url = IncomingUrl.webUrlOrNull(o.opt("url") as? String) ?: return null
        val title = (o.opt("title") as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: url
        return Favorite(title, url)
    }
}
