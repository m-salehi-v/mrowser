package net.mrowser.stream

import org.json.JSONArray
import org.json.JSONObject

data class SubtitleTrack(
    val url: String,
    val mimeType: String,
    val language: String,
    val label: String
)

data class PlaybackRequest(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleTrack>,
    val title: String,
    val preferredTextLanguage: String? = null
) {
    fun toJson(): String {
        val h = JSONObject()
        headers.forEach { (k, v) -> h.put(k, v) }
        val subs = JSONArray()
        subtitles.forEach {
            subs.put(
                JSONObject()
                    .put("url", it.url).put("mimeType", it.mimeType)
                    .put("language", it.language).put("label", it.label)
            )
        }
        val o = JSONObject()
            .put("url", url).put("headers", h).put("subtitles", subs).put("title", title)
        preferredTextLanguage?.let { o.put("preferredTextLanguage", it) }
        return o.toString()
    }

    companion object {
        fun fromJson(json: String): PlaybackRequest {
            val o = JSONObject(json)
            val h = o.getJSONObject("headers")
            val headers = h.keys().asSequence().associateWith { h.getString(it) }
            val arr = o.getJSONArray("subtitles")
            val subs = (0 until arr.length()).map {
                val s = arr.getJSONObject(it)
                SubtitleTrack(s.getString("url"), s.getString("mimeType"), s.getString("language"), s.getString("label"))
            }
            val pref = if (o.has("preferredTextLanguage")) o.getString("preferredTextLanguage") else null
            return PlaybackRequest(o.getString("url"), headers, subs, o.getString("title"), pref)
        }
    }
}
