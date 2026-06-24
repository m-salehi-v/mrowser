package net.mrowser.data

import java.io.File

/** SettingsRepository backed by a JSON file; pure logic delegated to SettingsJson. */
class JsonSettingsStore(private val file: File) : SettingsRepository {

    // Volatile: StreamSniffer is documented thread-safe and may call get() off the UI thread
    // (bestRequest); writes come from SettingsView on the UI thread. The value is an immutable
    // snapshot and the reference swap is atomic, so @Volatile gives a consistent cross-thread read.
    @Volatile private var current: Settings =
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
