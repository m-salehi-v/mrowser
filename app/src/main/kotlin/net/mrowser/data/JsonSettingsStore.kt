package net.mrowser.data

import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** SettingsRepository backed by a JSON file; pure logic delegated to SettingsJson. */
class JsonSettingsStore(private val file: File) : SettingsRepository {

    private var current: Settings =
        if (file.exists()) SettingsJson.fromJson(file.readText()) else Settings()

    private val io = Executors.newSingleThreadExecutor()

    override fun get(): Settings = current

    override fun update(settings: Settings) {
        current = settings
        persist()
    }

    /** Serialize the (immutable) snapshot on the caller, then write off the UI thread.
     *  The single-thread executor preserves write order; failures are logged, not swallowed. */
    private fun persist() {
        val snapshot = SettingsJson.toJson(current)
        io.execute {
            runCatching { file.writeText(snapshot) }
                .onFailure { Log.w(TAG, "persist failed: ${file.name}", it) }
        }
    }

    private companion object {
        private const val TAG = "JsonSettingsStore"
    }
}
