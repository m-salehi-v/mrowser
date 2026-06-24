package net.mrowser.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleCueParserTest {

    @Test fun `parses a webvtt file`() {
        val vtt = """
            WEBVTT

            1
            00:00:01.000 --> 00:00:04.000
            Hello world

            00:00:05.500 --> 00:00:07.000 line:90%
            Second line
            continued
        """.trimIndent()
        val cues = SubtitleCueParser.parse(vtt)
        assertEquals(2, cues.size)
        assertEquals(SubtitleCue(1000, 4000, "Hello world"), cues[0])
        assertEquals(SubtitleCue(5500, 7000, "Second line\ncontinued"), cues[1])
    }

    @Test fun `parses an srt file with comma milliseconds`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,500
            Hi

            2
            01:02:03,250 --> 01:02:04,000
            Later
        """.trimIndent()
        val cues = SubtitleCueParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(SubtitleCue(1000, 2500, "Hi"), cues[0])
        assertEquals(SubtitleCue(3723250, 3724000, "Later"), cues[1])
    }

    @Test fun `accepts MM colon SS timestamps without hours`() {
        val vtt = "WEBVTT\n\n00:30.000 --> 00:32.000\nShort"
        val cues = SubtitleCueParser.parse(vtt)
        assertEquals(listOf(SubtitleCue(30000, 32000, "Short")), cues)
    }

    @Test fun `skips NOTE and STYLE blocks`() {
        val vtt = """
            WEBVTT

            NOTE this is a comment

            STYLE
            ::cue { color: yellow }

            00:00:01.000 --> 00:00:02.000
            Only cue
        """.trimIndent()
        val cues = SubtitleCueParser.parse(vtt)
        assertEquals(listOf(SubtitleCue(1000, 2000, "Only cue")), cues)
    }

    @Test fun `skips malformed blocks without crashing`() {
        val vtt = """
            WEBVTT

            garbage --> not-a-time
            broken

            00:00:01.000 --> 00:00:02.000
            Good
        """.trimIndent()
        val cues = SubtitleCueParser.parse(vtt)
        assertEquals(listOf(SubtitleCue(1000, 2000, "Good")), cues)
    }

    @Test fun `returns cues sorted by start time`() {
        val srt = "1\n00:00:05,000 --> 00:00:06,000\nB\n\n2\n00:00:01,000 --> 00:00:02,000\nA"
        val cues = SubtitleCueParser.parse(srt)
        assertEquals(listOf("A", "B"), cues.map { it.text })
    }

    @Test fun `empty input yields no cues`() {
        assertEquals(emptyList<SubtitleCue>(), SubtitleCueParser.parse(""))
    }

    @Test fun `handles CRLF line endings`() {
        val srt = "1\r\n00:00:01,000 --> 00:00:02,000\r\nWin\r\n"
        assertEquals(listOf(SubtitleCue(1000, 2000, "Win")), SubtitleCueParser.parse(srt))
    }
}
