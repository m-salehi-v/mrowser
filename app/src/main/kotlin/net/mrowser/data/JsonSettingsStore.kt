package net.mrowser.data

import java.io.File

/** SettingsRepository backed by a JSON file; pure logic delegated to SettingsJson. */
class JsonSettingsStore(private val file: File) : SettingsRepository {

    private var current: Settings =
        if (file.exists()) SettingsJson.fromJson(file.readText()) else Settings()

    override fun get(): Settings = current

    override fun update(settings: Settings) {
        current = settings
        persist()
    }

    private fun persist() {
        runCatching { file.writeText(SettingsJson.toJson(current)) }
    }
}
