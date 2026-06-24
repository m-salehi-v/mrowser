package net.mrowser.player

/** Selects the cues that should be on screen at a given media time (start inclusive, end exclusive). */
object ActiveCueFinder {
    fun activeAt(cues: List<SubtitleCue>, timeMs: Long): List<SubtitleCue> =
        cues.filter { timeMs >= it.startMs && timeMs < it.endMs }
}
