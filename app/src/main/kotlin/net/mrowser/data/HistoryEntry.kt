package net.mrowser.data

/** A visited site. Identity is the url; visitedAt is epoch millis. */
data class HistoryEntry(val title: String, val url: String, val visitedAt: Long)
