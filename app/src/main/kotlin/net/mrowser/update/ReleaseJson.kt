package net.mrowser.update

import org.json.JSONException
import org.json.JSONObject

/**
 * Pure parse of one GitHub `/releases/latest` payload.
 *
 * Anything unexpected is `null`, and the caller treats `null` as "no update": a silent nothing
 * is always the right answer for a check the user did not ask for. `draft`/`prerelease` should
 * never come back from this endpoint — if one does, the response is not what we think it is and
 * is refused rather than trusted. Exactly one `.apk` asset is required: the release workflow
 * attaches one (`app-release.apk`), so zero or several means something changed upstream and
 * guessing which file to hand `DownloadManager` is not the job of a background check.
 */
object ReleaseJson {

    fun parse(body: String): Release? = try {
        val o = JSONObject(body)
        when {
            o.optBoolean("draft", false) -> null
            o.optBoolean("prerelease", false) -> null
            else -> build(o)
        }
    } catch (e: JSONException) {
        null
    }

    private fun build(o: JSONObject): Release? {
        val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val htmlUrl = o.optString("html_url").takeIf { it.isNotBlank() } ?: return null

        var apkUrl: String? = null
        var apkSize = 0L
        var apkCount = 0
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (!a.optString("name").endsWith(".apk", ignoreCase = true)) continue
                apkCount++
                apkUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                apkSize = a.optLong("size", 0L)
            }
        }
        if (apkCount != 1) return null
        val url = apkUrl ?: return null

        return Release(
            tag = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            notes = o.optString("body"),
            apkUrl = url,
            apkSizeBytes = apkSize,
            htmlUrl = htmlUrl
        )
    }
}
