package net.mrowser.data

import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** HistoryRepository backed by a JSON file; pure logic delegated to HistoryOps/HistoryJson. */
class JsonHistoryStore(private val file: File) : HistoryRepository {

    private var items: List<HistoryEntry> =
        if (file.exists()) HistoryJson.fromJson(file.readText()) else emptyList()

    private val io = Executors.newSingleThreadExecutor()

    override fun findAll(): List<HistoryEntry> = items

    override fun record(entry: HistoryEntry) {
        items = HistoryOps.record(items, entry); persist()
    }

    override fun clear() {
        items = HistoryOps.clear(); persist()
    }

    /** Serialize the (immutable) snapshot on the caller, then write off the UI thread.
     *  The single-thread executor preserves write order; failures are logged, not swallowed. */
    private fun persist() {
        val snapshot = HistoryJson.toJson(items)
        io.execute {
            runCatching { file.writeText(snapshot) }
                .onFailure { Log.w(TAG, "persist failed: ${file.name}", it) }
        }
    }

    private companion object {
        private const val TAG = "JsonHistoryStore"
    }
}
