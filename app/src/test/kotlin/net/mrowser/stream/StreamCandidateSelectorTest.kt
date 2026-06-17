package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamCandidateSelectorTest {

    private fun hls(url: String, seq: Int) = StreamCandidate(url, MediaKind.MANIFEST_HLS, seq)
    private fun sub(url: String, seq: Int) = StreamCandidate(url, MediaKind.SUBTITLE, seq)

    @Test fun `selects the newest non-ad hls manifest`() {
        val list = listOf(
            hls("https://cdn.site.net/old.m3u8", 1),
            hls("https://pubads.g.doubleclick.net/ad.m3u8", 2),
            hls("https://cdn.site.net/new.m3u8", 3)
        )
        assertEquals("https://cdn.site.net/new.m3u8", StreamCandidateSelector.selectBest(list)?.url)
    }

    @Test fun `returns null when there is no manifest`() {
        assertNull(StreamCandidateSelector.selectBest(listOf(sub("https://x/fa.vtt", 1))))
    }

    @Test fun `collects subtitles newest first and deduped`() {
        val list = listOf(
            sub("https://x/fa.vtt", 1),
            sub("https://x/fa.vtt", 2),
            sub("https://x/en.vtt", 3)
        )
        val subs = StreamCandidateSelector.selectSubtitles(list)
        assertEquals(listOf("https://x/en.vtt", "https://x/fa.vtt"), subs.map { it.url })
    }
}
