package net.mrowser.download

import net.mrowser.web.IncomingUrl

/**
 * What mrowser remembers about downloads beyond what DownloadManager keeps: the page each one
 * came from (for Retry, which needs the Referer) and the rows the user removed from the list.
 * Keyed by DownloadManager id, which is a `long`.
 */
data class DownloadsState(
    val pages: Map<Long, String> = emptyMap(),
    val hidden: Set<Long> = emptySet(),
)

/** Pure transforms over [DownloadsState]. */
object DownloadsOps {

    /** Remembers [pageUrl] for [id] if it is a web page; anything else leaves [s] as it was. */
    fun recordPage(s: DownloadsState, id: Long, pageUrl: String?): DownloadsState {
        val page = IncomingUrl.webUrlOrNull(pageUrl) ?: return s
        return s.copy(pages = s.pages + (id to page))
    }

    fun hide(s: DownloadsState, ids: Set<Long>): DownloadsState = s.copy(hidden = s.hidden + ids)

    /** A retry is a new download: it inherits the page, and the failed row goes from the list. */
    fun retried(s: DownloadsState, oldId: Long, newId: Long): DownloadsState {
        val page = s.pages[oldId]
        val pages = s.pages - oldId + listOfNotNull(page?.let { newId to it })
        return DownloadsState(pages = pages, hidden = s.hidden + oldId)
    }

    /** Forgets ids DownloadManager no longer has. Returns [s] itself when nothing is dropped. */
    fun prune(s: DownloadsState, live: Set<Long>): DownloadsState {
        val pages = s.pages.filterKeys { it in live }
        val hidden = s.hidden.filterTo(HashSet()) { it in live }
        if (pages.size == s.pages.size && hidden.size == s.hidden.size) return s
        return DownloadsState(pages = pages, hidden = hidden)
    }
}
