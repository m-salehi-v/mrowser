package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamCandidateSelectorTest {

    private fun hls(url: String, seq: Int) = StreamCandidate(url, MediaKind.MANIFEST_HLS, seq)
    private fun sub(url: String, seq: Int) = StreamCandidate(url, MediaKind.SUBTITLE, seq)

    @Test fun `prefers the master playlist over variants`() {
        val list = listOf(
            hls("https://site.net/master.m3u8?key=1", 1),
            hls("https://cdn.net/halt-video/i.m3u8", 2),
            hls("https://cdn.net/halt-audio/i.m3u8", 3)
        )
        assertEquals("https://site.net/master.m3u8?key=1", StreamCandidateSelector.selectBest(list)?.url)
    }

    @Test fun `prefers the newest master when several are seen`() {
        val list = listOf(
            hls("https://site.net/master.m3u8?k=old", 1),
            hls("https://cdn.net/i.m3u8", 2),
            hls("https://site.net/master.m3u8?k=new", 5)
        )
        assertEquals("https://site.net/master.m3u8?k=new", StreamCandidateSelector.selectBest(list)?.url)
    }

    @Test fun `falls back to the earliest manifest when none is named master`() {
        val list = listOf(
            hls("https://cdn.net/a/i.m3u8", 2),
            hls("https://cdn.net/b/i.m3u8", 1),
            hls("https://cdn.net/c/i.m3u8", 3)
        )
        assertEquals("https://cdn.net/b/i.m3u8", StreamCandidateSelector.selectBest(list)?.url)
    }

    @Test fun `ignores ad-host manifests`() {
        val list = listOf(
            hls("https://pubads.g.doubleclick.net/ad.m3u8", 1),
            hls("https://cdn.net/real/i.m3u8", 2)
        )
        assertEquals("https://cdn.net/real/i.m3u8", StreamCandidateSelector.selectBest(list)?.url)
    }

    @Test fun `returns null when there is no manifest`() {
        assertNull(StreamCandidateSelector.selectBest(listOf(sub("https://x/fa.vtt", 1))))
    }

    @Test fun `collects subtitles in sniff order and deduped`() {
        val list = listOf(
            sub("https://x/fa.vtt", 1),
            sub("https://x/en.vtt", 3),
            sub("https://x/fa.vtt", 2)
        )
        val subs = StreamCandidateSelector.selectSubtitles(list)
        assertEquals(listOf("https://x/fa.vtt", "https://x/en.vtt"), subs.map { it.url })
    }
}
