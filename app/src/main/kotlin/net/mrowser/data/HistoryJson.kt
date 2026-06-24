package net.mrowser.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of the history list. */
object HistoryJson {

    fun toJson(items: List<HistoryEntry>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("visitedAt", it.visitedAt)
            )
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<HistoryEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HistoryEntry(o.getString("title"), o.getString("url"), o.getLong("visitedAt"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
