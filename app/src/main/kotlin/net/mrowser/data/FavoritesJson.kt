package net.mrowser.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of the favorites list. */
object FavoritesJson {

    fun toJson(items: List<Favorite>): String {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
        return arr.toString()
    }

    fun fromJson(json: String): List<Favorite> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Favorite(o.getString("title"), o.getString("url"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
