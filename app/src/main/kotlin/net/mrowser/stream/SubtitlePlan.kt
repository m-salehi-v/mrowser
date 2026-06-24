package net.mrowser.stream

import net.mrowser.data.SubtitleLanguagePref

/**
 * Pure: turns the selected subtitle candidates + a language preference into the player's
 * subtitle tracks and the preferred-language hint. The first track carries the preferred
 * language + label when a preference is set; every other track — and all tracks when the
 * preference is OFF — gets a generic "Subtitle N" label with an undetermined language.
 */
object SubtitlePlan {

    data class Plan(val tracks: List<SubtitleTrack>, val preferredLanguage: String?)

    fun build(subs: List<StreamCandidate>, pref: SubtitleLanguagePref): Plan {
        val code = pref.code
        val tracks = subs.mapIndexed { idx, c ->
            val isSrt = c.url.substringBefore('?').lowercase().endsWith(".srt")
            val mime = if (isSrt) "application/x-subrip" else "text/vtt"
            // code != null implies trackLabel != null (PERSIAN/ENGLISH set both, OFF nulls both),
            // so the "Subtitle 1" fallback is unreachable today — kept defensive for new enum values.
            if (idx == 0 && code != null) {
                SubtitleTrack(c.url, mime, code, pref.trackLabel ?: "Subtitle 1")
            } else {
                SubtitleTrack(c.url, mime, "und", "Subtitle ${idx + 1}")
            }
        }
        return Plan(tracks, code)
    }
}
