package net.mrowser.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMimeTypeTest {

    @Test fun `maps an hls manifest`() {
        assertEquals("application/x-mpegURL", MediaMimeType.of("https://x.net/master.m3u8?k=1"))
    }

    @Test fun `maps a dash manifest`() {
        assertEquals("application/dash+xml", MediaMimeType.of("https://x.net/stream.mpd"))
    }

    @Test fun `leaves a progressive file unset so the player infers the container`() {
        assertNull(MediaMimeType.of("https://x.net/movie.mp4"))
        assertNull(MediaMimeType.of("https://x.net/movie.mkv"))
    }

    @Test fun `leaves an unknown url unset`() {
        assertNull(MediaMimeType.of("https://x.net/play?id=7"))
    }
}
