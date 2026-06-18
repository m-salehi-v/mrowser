package net.mrowser.stream

import android.webkit.CookieManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Collects media candidates for the current page (safe to call off the UI thread)
 * and assembles a PlaybackRequest. Pure selection lives in MediaUrlClassifier /
 * StreamCandidateSelector.
 */
class StreamSniffer(
    private val userAgent: () -> String,
    private val onStreamAvailable: () -> Unit,
    private val onCleared: () -> Unit
) {
    private val candidates = CopyOnWriteArrayList<StreamCandidate>()
    private val seq = AtomicInteger(0)

    @Volatile private var pageUrl: String = ""
    @Volatile private var announced = false

    fun onPageStarted(url: String) {
        pageUrl = url
        candidates.clear()
        announced = false
        onCleared()
    }

    fun onRequest(url: String) {
        val kind = MediaUrlClassifier.classify(url)
        if (kind == MediaUrlClassifier.MediaKind.MANIFEST_HLS || kind == MediaUrlClassifier.MediaKind.SUBTITLE) {
            candidates.add(StreamCandidate(url, kind, seq.incrementAndGet()))
            if (!announced && hasStream()) {
                announced = true
                onStreamAvailable()
            }
        }
    }

    fun hasStream(): Boolean =
        candidates.any { it.kind == MediaUrlClassifier.MediaKind.MANIFEST_HLS }

    fun bestRequest(): PlaybackRequest? {
        val best = StreamCandidateSelector.selectBest(candidates) ?: return null
        val headers = buildMap {
            put("User-Agent", userAgent())
            if (pageUrl.isNotEmpty()) put("Referer", pageUrl)
            CookieManager.getInstance().getCookie(best.url)?.let { put("Cookie", it) }
        }
        val subs = StreamCandidateSelector.selectSubtitles(candidates).mapIndexed { idx, c ->
            val isSrt = c.url.substringBefore('?').lowercase().endsWith(".srt")
            val mime = if (isSrt) "application/x-subrip" else "text/vtt"
            // The first subtitle on a Persian-default page is Persian; the rest get distinct generic labels.
            if (idx == 0) SubtitleTrack(c.url, mime, "fa", "Persian")
            else SubtitleTrack(c.url, mime, "und", "Subtitle ${idx + 1}")
        }
        return PlaybackRequest(best.url, headers, subs, pageUrl)
    }
}
