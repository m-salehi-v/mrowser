package net.mrowser.data

import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of Settings. Missing/unparseable fields → defaults. */
object SettingsJson {

    fun toJson(settings: Settings): String =
        JSONObject()
            .put("autoOpenPlayer", settings.autoOpenPlayer)
            .put("subtitleLanguage", settings.subtitleLanguage.name)
            .put("cursorSpeed", settings.cursorSpeed.name)
            .toString()

    fun fromJson(json: String): Settings {
        if (json.isBlank()) return Settings()
        return try {
            val o = JSONObject(json)
            val defaults = Settings()
            Settings(
                autoOpenPlayer = o.optBoolean("autoOpenPlayer", defaults.autoOpenPlayer),
                subtitleLanguage = enumOrDefault(o.optString("subtitleLanguage"), defaults.subtitleLanguage),
                cursorSpeed = enumOrDefault(o.optString("cursorSpeed"), defaults.cursorSpeed)
            )
        } catch (e: JSONException) {
            Settings()
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        T::class.java.enumConstants?.firstOrNull { it.name == name } ?: default
}
