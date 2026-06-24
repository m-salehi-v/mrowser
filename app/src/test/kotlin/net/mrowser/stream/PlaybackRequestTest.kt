package net.mrowser.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRequestTest {

    @Test fun `round trips through json`() {
        val req = PlaybackRequest(
            url = "https://cdn.site.net/v.m3u8",
            headers = mapOf("User-Agent" to "UA", "Referer" to "https://site.net/movie", "Cookie" to "a=1"),
            subtitles = listOf(SubtitleTrack("https://site.net/fa.vtt", "text/vtt", "fa", "Persian")),
            title = "https://site.net/movie"
        )
        assertEquals(req, PlaybackRequest.fromJson(req.toJson()))
    }

    @Test fun `round trips with no subtitles`() {
        val req = PlaybackRequest("https://x/v.m3u8", mapOf("User-Agent" to "UA"), emptyList(), "t")
        assertEquals(req, PlaybackRequest.fromJson(req.toJson()))
    }
}
