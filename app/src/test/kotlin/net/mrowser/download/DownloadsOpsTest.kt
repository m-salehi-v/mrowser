package net.mrowser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DownloadsOpsTest {

    @Test fun `records the page only for web pages`() {
        assertEquals(
            mapOf(1L to "https://site.example/p"),
            DownloadsOps.recordPage(DownloadsState(), 1, "https://site.example/p").pages,
        )
        assertEquals(DownloadsState(), DownloadsOps.recordPage(DownloadsState(), 1, "about:blank"))
        assertEquals(DownloadsState(), DownloadsOps.recordPage(DownloadsState(), 1, null))
    }

    @Test fun `hide adds ids`() =
        assertEquals(
            setOf(1L, 2L, 3L),
            DownloadsOps.hide(DownloadsState(hidden = setOf(1L)), setOf(2L, 3L)).hidden,
        )

    @Test fun `retry carries the page to the new id and hides the old row`() {
        val s = DownloadsOps.retried(
            DownloadsState(pages = mapOf(1L to "https://p.example/")), oldId = 1, newId = 2,
        )
        assertEquals(DownloadsState(pages = mapOf(2L to "https://p.example/"), hidden = setOf(1L)), s)
    }

    @Test fun `prune forgets ids DownloadManager no longer has`() {
        val s = DownloadsState(
            pages = mapOf(1L to "https://a.example/", 2L to "https://b.example/"),
            hidden = setOf(2L, 3L),
        )
        assertEquals(
            DownloadsState(pages = mapOf(2L to "https://b.example/"), hidden = setOf(2L)),
            DownloadsOps.prune(s, live = setOf(2L, 4L)),
        )
    }

    @Test fun `prune with nothing to drop is the same state`() {
        val s = DownloadsState(pages = mapOf(1L to "https://a.example/"), hidden = setOf(1L))
        assertSame(s, DownloadsOps.prune(s, live = setOf(1L, 2L)))
    }
}
