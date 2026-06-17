package net.mrowser.stream

/** Pure classification of a network URL by media role. */
object MediaUrlClassifier {

    enum class MediaKind { MANIFEST_HLS, MANIFEST_DASH, SUBTITLE, SEGMENT, OTHER }

    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "googleadservices.com", "adservice.google.com",
        "imasdk.googleapis.com", "amazon-adsystem.com", "adnxs.com", "scorecardresearch.com"
    )

    fun classify(url: String): MediaKind {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> MediaKind.MANIFEST_HLS
            path.endsWith(".mpd") -> MediaKind.MANIFEST_DASH
            path.endsWith(".vtt") || path.endsWith(".srt") -> MediaKind.SUBTITLE
            path.endsWith(".ts") || path.endsWith(".m4s") -> MediaKind.SEGMENT
            else -> MediaKind.OTHER
        }
    }

    fun isAdHost(url: String, denylist: Set<String> = AD_HOSTS): Boolean {
        val host = hostOf(url) ?: return false
        return denylist.any { host == it || host.endsWith(".$it") }
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        return authority.substringAfter('@', authority).substringBefore(':').lowercase().ifEmpty { null }
    }
}
