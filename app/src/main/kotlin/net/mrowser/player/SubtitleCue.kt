package net.mrowser.player

/** One subtitle cue with absolute timing, in milliseconds from the start of the media. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
