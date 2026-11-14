package net.mrowser.download

import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** DownloadsRepository backed by a JSON file; pure logic delegated to DownloadsJson. */
class JsonDownloadsStore(private val file: File) : DownloadsRepository {

    // Volatile: read from the UI thread and from poll callbacks, written via update().
    @Volatile
    private var current: DownloadsState =
        runCatching { if (file.exists()) DownloadsJson.fromJson(file.readText()) else DownloadsState() }
            .getOrDefault(DownloadsState())

    private val io = Executors.newSingleThreadExecutor()

    override fun get(): DownloadsState = current

    override fun update(state: DownloadsState) {
        current = state
        persist()
    }

    /** Serialize the (immutable) snapshot on the caller, then write off the UI thread.
     *  The single-thread executor preserves write order; failures are logged, not swallowed. */
    private fun persist() {
        val snapshot = DownloadsJson.toJson(current)
        io.execute {
            runCatching { file.writeText(snapshot) }
                .onFailure { Log.w(TAG, "persist failed: ${file.name}", it) }
        }
    }

    private companion object {
        private const val TAG = "JsonDownloadsStore"
    }
}
