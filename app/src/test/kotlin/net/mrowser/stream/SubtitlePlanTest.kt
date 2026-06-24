package net.mrowser.stream

import net.mrowser.data.SubtitleLanguagePref
import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitlePlanTest {

    private fun sub(url: String, seq: Int) = StreamCandidate(url, MediaKind.SUBTITLE, seq)

    @Test fun `english tags first track en and prefers en`() {
        val plan = SubtitlePlan.build(listOf(sub("https://x/a.vtt", 1)), SubtitleLanguagePref.ENGLISH)
        assertEquals("en", plan.preferredLanguage)
        assertEquals("en", plan.tracks[0].language)
        assertEquals("English", plan.tracks[0].label)
    }

    @Test fun `persian tags first track fa and prefers fa`() {
        val plan = SubtitlePlan.build(listOf(sub("https://x/a.vtt", 1)), SubtitleLanguagePref.PERSIAN)
        assertEquals("fa", plan.preferredLanguage)
        assertEquals("fa", plan.tracks[0].language)
        assertEquals("Persian", plan.tracks[0].label)
    }

    @Test fun `off marks no preference and uses a generic label`() {
        val plan = SubtitlePlan.build(listOf(sub("https://x/a.vtt", 1)), SubtitleLanguagePref.OFF)
        assertNull(plan.preferredLanguage)
        assertEquals("und", plan.tracks[0].language)
        assertEquals("Subtitle 1", plan.tracks[0].label)
    }

    @Test fun `extra tracks get generic labels, und language, and srt mime`() {
        val plan = SubtitlePlan.build(
            listOf(sub("https://x/a.vtt", 1), sub("https://x/b.srt", 2)),
            SubtitleLanguagePref.ENGLISH
        )
        assertEquals("application/x-subrip", plan.tracks[1].mimeType)
        assertEquals("und", plan.tracks[1].language)
        assertEquals("Subtitle 2", plan.tracks[1].label)
    }
}
