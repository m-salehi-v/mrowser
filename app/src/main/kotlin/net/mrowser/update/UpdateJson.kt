package net.mrowser.update

import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of UpdateState. Missing/unparseable fields → defaults. */
object UpdateJson {

    fun toJson(state: UpdateState): String {
        val o = JSONObject().put("lastCheckedAt", state.lastCheckedAt)
        state.etag?.let { o.put("etag", it) }
        state.release?.let { r ->
            o.put(
                "release",
                JSONObject()
                    .put("tag", r.tag)
                    .put("version", r.version)
                    .put("notes", r.notes)
                    .put("apkUrl", r.apkUrl)
                    .put("apkSizeBytes", r.apkSizeBytes)
                    .put("htmlUrl", r.htmlUrl)
            )
        }
        return o.toString()
    }

    fun fromJson(json: String): UpdateState {
        if (json.isBlank()) return UpdateState()
        return try {
            val o = JSONObject(json)
            UpdateState(
                lastCheckedAt = o.optLong("lastCheckedAt", 0L),
                etag = o.optString("etag").takeIf { it.isNotBlank() },
                release = o.optJSONObject("release")?.let(::release)
            )
        } catch (e: JSONException) {
            UpdateState()
        }
    }

    /** A half-written release is no release: the fields the download path needs are required. */
    private fun release(o: JSONObject): Release? {
        val tag = o.optString("tag").takeIf { it.isNotBlank() } ?: return null
        val apkUrl = o.optString("apkUrl").takeIf { it.isNotBlank() } ?: return null
        val htmlUrl = o.optString("htmlUrl").takeIf { it.isNotBlank() } ?: return null
        return Release(
            tag = tag,
            version = o.optString("version").takeIf { it.isNotBlank() }
                ?: tag.removePrefix("v").removePrefix("V"),
            notes = o.optString("notes"),
            apkUrl = apkUrl,
            apkSizeBytes = o.optLong("apkSizeBytes", 0L),
            htmlUrl = htmlUrl
        )
    }
}
