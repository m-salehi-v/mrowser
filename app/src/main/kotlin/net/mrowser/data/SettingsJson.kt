package net.mrowser.data

import net.mrowser.web.PageZoom
import net.mrowser.web.SearchEngine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of Settings. Missing/unparseable fields → defaults. */
object SettingsJson {

    fun toJson(settings: Settings): String =
        JSONObject()
            .put("autoOpenPlayer", settings.autoOpenPlayer)
            .put("cursorSpeed", settings.cursorSpeed.name)
            .put("blockPopups", settings.blockPopups)
            .put("blockAds", settings.blockAds)
            .put("adsAllowedOn", JSONArray(settings.adsAllowedOn.sorted()))
            .put("matchFrameRate", settings.matchFrameRate)
            .put("matchResolution", settings.matchResolution)
            .put("searchEngine", settings.searchEngine.id)
            .put("desktopByDefault", settings.desktopByDefault)
            .put("desktopOverride", JSONObject(settings.desktopOverride.toSortedMap()))
            .put("zoomBySite", JSONObject(settings.zoomBySite.toSortedMap()))
            .put("seeded", settings.seeded)
            .put("navHintShown", settings.navHintShown)
            .toString()

    fun fromJson(json: String): Settings {
        if (json.isBlank()) return Settings()
        return try {
            val o = JSONObject(json)
            val defaults = Settings()
            Settings(
                autoOpenPlayer = o.optBoolean("autoOpenPlayer", defaults.autoOpenPlayer),
                cursorSpeed = enumOrDefault(o.optString("cursorSpeed"), defaults.cursorSpeed),
                blockPopups = o.optBoolean("blockPopups", defaults.blockPopups),
                blockAds = o.optBoolean("blockAds", defaults.blockAds),
                adsAllowedOn = stringSet(o.optJSONArray("adsAllowedOn")) ?: defaults.adsAllowedOn,
                matchFrameRate = o.optBoolean("matchFrameRate", defaults.matchFrameRate),
                matchResolution = o.optBoolean("matchResolution", defaults.matchResolution),
                searchEngine = SearchEngine.fromId(o.optString("searchEngine", null)),
                desktopByDefault = o.optBoolean("desktopByDefault", defaults.desktopByDefault),
                desktopOverride = desktopMap(o.optJSONObject("desktopOverride")),
                zoomBySite = zoomMap(o.optJSONObject("zoomBySite")),
                seeded = o.optBoolean("seeded", defaults.seeded),
                navHintShown = o.optBoolean("navHintShown", defaults.navHintShown)
            )
        } catch (e: JSONException) {
            Settings()
        }
    }

    /** Entries whose value is not a boolean, or whose key is blank, are skipped. */
    private fun desktopMap(obj: JSONObject?): Map<String, Boolean> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, Boolean>()
        for (key in obj.keys()) {
            val v = obj.opt(key) as? Boolean ?: continue
            if (key.isNotBlank()) out[key] = v
        }
        return out
    }

    /** A non-number value or blank key is skipped; the rest snap to a zoom step, and 100 is dropped. */
    private fun zoomMap(obj: JSONObject?): Map<String, Int> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (key in obj.keys()) {
            val v = (obj.opt(key) as? Number)?.toInt() ?: continue
            val z = PageZoom.snap(v)
            if (key.isNotBlank() && z != PageZoom.DEFAULT) out[key] = z
        }
        return out
    }

    /** Non-string and blank elements are skipped; a missing/non-array value is null. */
    private fun stringSet(arr: JSONArray?): Set<String>? {
        if (arr == null) return null
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i) as? String ?: continue
            if (v.isNotBlank()) out.add(v)
        }
        return out
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        T::class.java.enumConstants?.firstOrNull { it.name == name } ?: default
}
