package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlClassifierTest {

    @Test fun `classifies hls manifest ignoring query`() {
        assertEquals(MediaKind.MANIFEST_HLS, MediaUrlClassifier.classify("https://x.net/a/index.m3u8?h=1080"))
    }

    @Test fun `classifies dash subtitle and segment`() {
        assertEquals(MediaKind.MANIFEST_DASH, MediaUrlClassifier.classify("https://x.net/a.mpd"))
        assertEquals(MediaKind.SUBTITLE, MediaUrlClassifier.classify("https://x.net/fa.vtt"))
        assertEquals(MediaKind.SUBTITLE, MediaUrlClassifier.classify("https://x.net/fa.SRT"))
        assertEquals(MediaKind.SEGMENT, MediaUrlClassifier.classify("https://x.net/seg1.ts"))
        assertEquals(MediaKind.SEGMENT, MediaUrlClassifier.classify("https://x.net/seg1.m4s"))
    }

    @Test fun `classifies everything else as other`() {
        assertEquals(MediaKind.OTHER, MediaUrlClassifier.classify("https://x.net/page.html"))
    }

    @Test fun `flags ad hosts including subdomains`() {
        assertTrue(MediaUrlClassifier.isAdHost("https://pubads.g.doubleclick.net/x.m3u8"))
        assertTrue(MediaUrlClassifier.isAdHost("https://googlesyndication.com/a"))
        assertFalse(MediaUrlClassifier.isAdHost("https://cdn.andrei-tarkovsky.net/a.m3u8"))
    }
}
