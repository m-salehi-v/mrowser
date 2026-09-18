package net.mrowser.data

import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** SettingsRepository backed by a JSON file; pure logic delegated to SettingsJson. */
class JsonSettingsStore(private val file: File) : SettingsRepository {

    // Volatile: AdBlocker's settings-provider lambdas call get() from WebView worker threads on
    // every intercepted request, while the UI thread writes via update() (SettingsView,
    // toggleAdsForSite). The Settings record itself is an immutable snapshot, but the *reference*
    // still needs a JMM guarantee to be visible across threads, or a toggle could go unseen
    // until relaunch.
    @Volatile
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
