package net.mrowser.data

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
                seeded = o.optBoolean("seeded", defaults.seeded),
                navHintShown = o.optBoolean("navHintShown", defaults.navHintShown)
            )
        } catch (e: JSONException) {
            Settings()
        }
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
