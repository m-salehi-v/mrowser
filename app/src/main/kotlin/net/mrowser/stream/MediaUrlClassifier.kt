package net.mrowser.stream

import net.mrowser.web.UrlHost

/** Pure classification of a network URL by media role. */
object MediaUrlClassifier {

    enum class MediaKind { MANIFEST_HLS, MANIFEST_DASH, PROGRESSIVE, SUBTITLE, SEGMENT, OTHER }

    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "googleadservices.com", "adservice.google.com",
        "imasdk.googleapis.com", "amazon-adsystem.com", "adnxs.com", "scorecardresearch.com"
    )

    private val PROGRESSIVE_EXTENSIONS = listOf(".mp4", ".m4v", ".webm", ".mkv")

    /** Filename words that mark a piece of a stream rather than a whole file. */
    private val SEGMENT_WORDS = setOf("init", "dashinit", "seg", "segment", "chunk", "frag", "fragment")

    /** Same words glued to a number, as in "seg12" / "chunk0001". */
    private val SEGMENT_PREFIX = Regex("^(init|seg|segment|chunk|frag|fragment)\\d+$")

    fun classify(url: String): MediaKind {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> MediaKind.MANIFEST_HLS
            path.endsWith(".mpd") -> MediaKind.MANIFEST_DASH
            path.endsWith(".vtt") || path.endsWith(".srt") -> MediaKind.SUBTITLE
            path.endsWith(".ts") || path.endsWith(".m4s") -> MediaKind.SEGMENT
            PROGRESSIVE_EXTENSIONS.any { path.endsWith(it) } ->
                if (isSegmentLike(path)) MediaKind.SEGMENT else MediaKind.PROGRESSIVE
            else -> MediaKind.OTHER
        }
    }

    /**
     * .mp4 is both a whole file and the segment container of fMP4 HLS/DASH, and a request
     * carries no Content-Type to tell them apart. Judge the filename: a segment is named
     * after its role ("init.mp4", "chunk-0001.mp4") or is a bare/zero-padded number, while
     * a whole file is named after its content ("Solaris.1972.1080p.mp4").
     */
    private fun isSegmentLike(path: String): Boolean {
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        if (name.isEmpty()) return false
        if (name.all { it.isDigit() }) return true
        val words = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (words.any { it in SEGMENT_WORDS || SEGMENT_PREFIX.matches(it) }) return true
        // A zero-padded trailing number is a segment index; a plain one could be a year or a height.
        val last = words.lastOrNull() ?: return false
        return words.size > 1 && last.length > 1 && last.startsWith("0") && last.all { it.isDigit() }
    }

    fun isAdHost(url: String, denylist: Set<String> = AD_HOSTS): Boolean {
        val host = hostOf(url) ?: return false
        return denylist.any { host == it || host.endsWith(".$it") }
    }

    private fun hostOf(url: String): String? = UrlHost.of(url)
}
