package net.mrowser.data

import java.io.File

/** HistoryRepository backed by a JSON file; pure logic delegated to HistoryOps/HistoryJson. */
class JsonHistoryStore(private val file: File) : HistoryRepository {

    private var items: List<HistoryEntry> =
        if (file.exists()) HistoryJson.fromJson(file.readText()) else emptyList()

    override fun findAll(): List<HistoryEntry> = items

    override fun record(entry: HistoryEntry) {
        items = HistoryOps.record(items, entry); persist()
    }

    override fun clear() {
        items = HistoryOps.clear(); persist()
    }

    private fun persist() {
        runCatching { file.writeText(HistoryJson.toJson(items)) }
    }
}
