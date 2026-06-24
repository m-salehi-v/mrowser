package net.mrowser.data

/** Pure list operations on history, keyed by url. */
object HistoryOps {

    const val CAP = 50

    /** Remove any entry with the same url, prepend the new one, keep only the newest [cap]. */
    fun record(list: List<HistoryEntry>, entry: HistoryEntry, cap: Int = CAP): List<HistoryEntry> =
        (listOf(entry) + list.filterNot { it.url == entry.url }).take(cap)

    fun clear(): List<HistoryEntry> = emptyList()
}
