package net.mrowser.stream

/**
 * Pure: turns the selected subtitle candidates into the player's subtitle tracks. Each track
 * is labeled with its real language (guessed from the URL via [SubtitleLang]) when possible,
 * otherwise a generic "Subtitle N". Side-loaded subtitles carry no language metadata, so the
 * label is best-effort; the player auto-shows the first track and the rest are CC-selectable.
 */
object SubtitlePlan {

    fun build(subs: List<StreamCandidate>): List<SubtitleTrack> =
        subs.mapIndexed { idx, c ->
            val isSrt = c.url.substringBefore('?').lowercase().endsWith(".srt")
            val mime = if (isSrt) "application/x-subrip" else "text/vtt"
            val detected = SubtitleLang.detect(c.url)
            SubtitleTrack(
                url = c.url,
                mimeType = mime,
                language = detected?.code ?: "und",
                label = detected?.label ?: "Subtitle ${idx + 1}"
            )
        }
}
