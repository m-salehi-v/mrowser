package net.mrowser.stream

import android.webkit.CookieManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
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

    // onRequest runs on WebView worker threads; compareAndSet makes the "announce once"
    // gate atomic so two concurrent manifest requests can't both fire the handoff.
    private val announced = AtomicBoolean(false)

    fun onPageStarted(url: String) {
        pageUrl = url
        candidates.clear()
        announced.set(false)
        onCleared()
    }

    fun onRequest(url: String) {
        val kind = MediaUrlClassifier.classify(url)
        if (kind == MediaUrlClassifier.MediaKind.MANIFEST_HLS || kind == MediaUrlClassifier.MediaKind.SUBTITLE) {
            candidates.add(StreamCandidate(url, kind, seq.incrementAndGet()))
            if (hasStream() && announced.compareAndSet(false, true)) {
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
        val subtitles = SubtitlePlan.build(StreamCandidateSelector.selectSubtitles(candidates))
        return PlaybackRequest(best.url, headers, subtitles, pageUrl)
    }
}
