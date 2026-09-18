package net.mrowser.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSnifferTest {

    private var announced = 0
    private val pending = mutableListOf<Pair<Long, () -> Unit>>()

    private fun sniffer() = StreamSniffer(
        userAgent = { "ua" },
        onStreamAvailable = { announced++ },
        onCleared = {},
        schedule = { delayMs, task -> pending.add(delayMs to task) }
    )

    private fun runPending() {
        val due = pending.toList()
        pending.clear()
        due.forEach { it.second() }
    }

    @Test fun `announces an hls manifest at once`() {
        val s = sniffer()
        s.onPageStarted("https://site.net/watch")
        s.onRequest("https://cdn.net/master.m3u8")
        assertEquals(1, announced)
    }

    @Test fun `announces a dash manifest at once`() {
        val s = sniffer()
        s.onPageStarted("https://site.net/watch")
        s.onRequest("https://cdn.net/stream.mpd")
        assertEquals(1, announced)
        assertTrue(s.hasStream())
    }

    @Test fun `holds a progressive file until its grace window elapses`() {
        val s = sniffer()
        s.onPageStarted("https://site.net/watch")
        s.onRequest("https://cdn.net/movie.mp4")
        assertEquals(0, announced)
        assertTrue(s.hasStream())
        runPending()
        assertEquals(1, announced)
    }

    @Test fun `lets a manifest inside the grace window win the handoff`() {
        val s = sniffer()
        s.onPageStarted("https://site.net/watch")
        s.onRequest("https://cdn.net/preview.mp4")
        s.onRequest("https://cdn.net/master.m3u8")
        assertEquals(1, announced)
        runPending()
        assertEquals(1, announced)
    }

    @Test fun `drops a progressive file left pending by the previous page`() {
        val s = sniffer()
        s.onPageStarted("https://site.net/watch")
        s.onRequest("https://cdn.net/movie.mp4")
        s.onPageStarted("https://site.net/other")
        runPending()
        assertEquals(0, announced)
    }

    @Test fun `ignores segments and subtitles as a reason to hand off`() {
        val s = sniffer()
        s.onPageStarted("https://site.net/watch")
        s.onRequest("https://cdn.net/init.mp4")
        s.onRequest("https://cdn.net/fa.vtt")
        runPending()
        assertEquals(0, announced)
        assertTrue(!s.hasStream())
    }
}
