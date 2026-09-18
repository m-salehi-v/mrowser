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
    private val onCleared: () -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit
) {
    private val candidates = CopyOnWriteArrayList<StreamCandidate>()
    private val seq = AtomicInteger(0)

    @Volatile private var pageUrl: String = ""

    // onRequest runs on WebView worker threads; compareAndSet makes the "announce once"
    // gate atomic so two concurrent manifest requests can't both fire the handoff.
    private val announced = AtomicBoolean(false)

    // Bumped per page so a progressive file's pending announce, scheduled on the page that
    // saw it, cannot fire into the next one.
    private val page = AtomicInteger(0)

    fun onPageStarted(url: String) {
        pageUrl = url
        candidates.clear()
        announced.set(false)
        page.incrementAndGet()
        onCleared()
    }

    fun onRequest(url: String) {
        val kind = MediaUrlClassifier.classify(url)
        if (kind !in COLLECTED) return
        candidates.add(StreamCandidate(url, kind, seq.incrementAndGet()))
        when (kind) {
            MediaUrlClassifier.MediaKind.MANIFEST_HLS,
            MediaUrlClassifier.MediaKind.MANIFEST_DASH -> announce()
            // A bare .mp4 is the weakest signal there is — a page that also serves a manifest
            // may request a preview/poster clip first. Give the manifest a window to arrive and
            // take the handoff (announce() only fires once, so the manifest simply wins).
            MediaUrlClassifier.MediaKind.PROGRESSIVE -> {
                val seenOn = page.get()
                schedule(PROGRESSIVE_GRACE_MS) { if (page.get() == seenOn) announce() }
            }
            else -> Unit
        }
    }

    private fun announce() {
        if (hasStream() && announced.compareAndSet(false, true)) onStreamAvailable()
    }

    fun hasStream(): Boolean = candidates.any { it.kind in PLAYABLE }

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

    companion object {
        const val PROGRESSIVE_GRACE_MS = 1500L

        private val PLAYABLE = setOf(
            MediaUrlClassifier.MediaKind.MANIFEST_HLS,
            MediaUrlClassifier.MediaKind.MANIFEST_DASH,
            MediaUrlClassifier.MediaKind.PROGRESSIVE
        )

        private val COLLECTED = PLAYABLE + MediaUrlClassifier.MediaKind.SUBTITLE
    }
}
