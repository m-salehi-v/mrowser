package net.mrowser.download

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of [DownloadsState]: `{"pages":{"42":"https://…"},"hidden":[7,9]}`. */
object DownloadsJson {

    fun toJson(s: DownloadsState): String {
        val pages = JSONObject()
        s.pages.toSortedMap().forEach { (id, url) -> pages.put(id.toString(), url) }
        return JSONObject()
            .put("pages", pages)
            .put("hidden", JSONArray(s.hidden.sorted()))
            .toString()
    }

    /** Blank, corrupt or half-written text reads as empty; a bad entry is skipped on its own. */
    fun fromJson(json: String): DownloadsState {
        if (json.isBlank()) return DownloadsState()
        return try {
            val o = JSONObject(json)
            val pages = HashMap<Long, String>()
            o.optJSONObject("pages")?.let { p ->
                for (key in p.keys()) {
                    val id = key.toLongOrNull() ?: continue
                    val url = p.opt(key) as? String ?: continue
                    pages[id] = url
                }
            }
            val hidden = HashSet<Long>()
            o.optJSONArray("hidden")?.let { a ->
                for (i in 0 until a.length()) {
                    ((a.opt(i) as? Number)?.toLong())?.let { hidden.add(it) }
                }
            }
            DownloadsState(pages = pages, hidden = hidden)
        } catch (e: JSONException) {
            DownloadsState()
        }
    }
}
