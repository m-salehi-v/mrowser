package net.mrowser.player

/**
 * Parses WebVTT and SubRip (.srt) subtitle text into [SubtitleCue]s.
 *
 * One unified parser handles both: it splits on blank lines, finds the `-->` line in
 * each block, and normalizes `,` decimal separators (SRT) to `.` (VTT). Lines before the
 * arrow (SRT index, VTT cue identifier) are ignored; lines after it are the cue text.
 * Header (`WEBVTT`), `NOTE`/`STYLE`/`REGION`, and malformed blocks are skipped, never fatal.
 */
object SubtitleCueParser {

    fun parse(content: String): List<SubtitleCue> {
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split(Regex("\n[ \t]*\n"))
        val cues = mutableListOf<SubtitleCue>()
        for (block in blocks) {
            val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val first = lines[0]
            if (first.startsWith("WEBVTT") || first.startsWith("NOTE") ||
                first.startsWith("STYLE") || first.startsWith("REGION")
            ) continue
            val arrowIdx = lines.indexOfFirst { it.contains("-->") }
            if (arrowIdx < 0) continue
            val times = parseArrow(lines[arrowIdx]) ?: continue
            val text = lines.drop(arrowIdx + 1).joinToString("\n")
            if (text.isEmpty()) continue
            cues.add(SubtitleCue(times.first, times.second, text))
        }
        return cues.sortedBy { it.startMs }
    }

    private fun parseArrow(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size < 2) return null
        val start = parseTimeMs(parts[0].trim()) ?: return null
        // The end side may carry VTT cue settings after the timestamp: "00:00:07.000 line:90%".
        val endToken = parts[1].trim().split(Regex("\\s+")).firstOrNull() ?: return null
        val end = parseTimeMs(endToken) ?: return null
        return start to end
    }

    private fun parseTimeMs(token: String): Long? {
        val t = token.replace(',', '.')
        val fracParts = t.split('.')
        val (hmsStr, msStr) = when (fracParts.size) {
            2 -> fracParts[0] to fracParts[1]
            1 -> fracParts[0] to "000"   // no fractional part — treat as .000
            else -> return null
        }
        val ms = msStr.padEnd(3, '0').take(3).toLongOrNull() ?: return null  // truncates sub-ms precision (fine for subtitles)
        val hms = hmsStr.split(':').map { it.toLongOrNull() ?: return null }  // non-local return from parseTimeMs, intentional
        val seconds = when (hms.size) {
            3 -> hms[0] * 3600 + hms[1] * 60 + hms[2]
            2 -> hms[0] * 60 + hms[1]
            else -> return null
        }
        return seconds * 1000 + ms
    }
}
