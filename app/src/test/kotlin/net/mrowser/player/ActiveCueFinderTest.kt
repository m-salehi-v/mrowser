package net.mrowser.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveCueFinderTest {

    private val cues = listOf(
        SubtitleCue(1000, 2000, "A"),
        SubtitleCue(1500, 2500, "B"),
        SubtitleCue(5000, 6000, "C"),
    )

    @Test fun `start is inclusive`() {
        assertEquals(listOf("A"), ActiveCueFinder.activeAt(cues, 1000).map { it.text })
    }

    @Test fun `end is exclusive`() {
        assertEquals(emptyList<String>(), ActiveCueFinder.activeAt(cues, 6000).map { it.text })
    }

    @Test fun `returns all overlapping cues`() {
        assertEquals(listOf("A", "B"), ActiveCueFinder.activeAt(cues, 1750).map { it.text })
    }

    @Test fun `gap between cues yields nothing`() {
        assertEquals(emptyList<String>(), ActiveCueFinder.activeAt(cues, 3000).map { it.text })
    }

    @Test fun `negative effective time yields nothing`() {
        assertEquals(emptyList<String>(), ActiveCueFinder.activeAt(cues, -500).map { it.text })
    }

    @Test fun `empty cue list yields nothing`() {
        assertEquals(emptyList<SubtitleCue>(), ActiveCueFinder.activeAt(emptyList(), 1000))
    }
}
