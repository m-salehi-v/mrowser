package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitlePlanTest {

    private fun sub(url: String, seq: Int) = StreamCandidate(url, MediaKind.SUBTITLE, seq)

    @Test fun `labels a track with its detected language`() {
        val tracks = SubtitlePlan.build(listOf(sub("https://cdn.x/movie.en.vtt", 1)))
        assertEquals("en", tracks[0].language)
        assertEquals("English", tracks[0].label)
        assertEquals("text/vtt", tracks[0].mimeType)
    }

    @Test fun `falls back to a generic label when language is unknown`() {
        val tracks = SubtitlePlan.build(listOf(sub("https://cdn.x/a1b2.vtt", 1)))
        assertEquals("und", tracks[0].language)
        assertEquals("Subtitle 1", tracks[0].label)
    }

    @Test fun `numbers generic labels by position`() {
        val tracks = SubtitlePlan.build(listOf(sub("https://cdn.x/a.vtt", 1), sub("https://cdn.x/b.vtt", 2)))
        assertEquals("Subtitle 1", tracks[0].label)
        assertEquals("Subtitle 2", tracks[1].label)
    }

    @Test fun `detects srt mime past a query string`() {
        val tracks = SubtitlePlan.build(listOf(sub("https://cdn.x/a.srt?token=abc", 1)))
        assertEquals("application/x-subrip", tracks[0].mimeType)
    }

    @Test fun `mixes detected and generic labels`() {
        val tracks = SubtitlePlan.build(listOf(sub("https://cdn.x/farsi.srt", 1), sub("https://cdn.x/hash.vtt", 2)))
        assertEquals("Persian", tracks[0].label)
        assertEquals("fa", tracks[0].language)
        assertEquals("Subtitle 2", tracks[1].label)
        assertEquals("und", tracks[1].language)
    }
}
