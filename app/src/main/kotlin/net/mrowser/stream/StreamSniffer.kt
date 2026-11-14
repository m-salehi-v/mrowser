package net.mrowser.stream

import android.webkit.CookieManager
import net.mrowser.web.MainFrameUrl
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

    /**
     * A file the top-level navigation asked for, held back until that navigation commits. A link
     * to a file is requested before anyone knows whether it is a page the WebView will show or an
     * attachment the download listener gets — and the listener can report a different URL
     * (a redirect to a signed CDN link) or fire after the grace window. So it becomes a
     * candidate only when the WebView commits it as its page ([onPageStarted] for that URL).
     */
    @Volatile private var mainFrameFile: StreamCandidate? = null

    fun onPageStarted(url: String) {
        pageUrl = url
        candidates.clear()
        announced.set(false)
        page.incrementAndGet()
        onCleared()
        val held = mainFrameFile
        mainFrameFile = null
        if (held != null && MainFrameUrl.same(held.url, url)) collect(held)
    }

    /**
     * @param headers what the WebView reported sending for this request (Referer, Origin, …).
     * @param isForMainFrame a top-level navigation: a progressive file waits for its commit.
     */
    fun onRequest(url: String, headers: Map<String, String> = emptyMap(), isForMainFrame: Boolean = false) {
        val kind = MediaUrlClassifier.classify(url)
        if (kind !in COLLECTED) return
        val candidate = StreamCandidate(url, kind, seq.incrementAndGet(), headers)
        if (isForMainFrame && kind == MediaUrlClassifier.MediaKind.PROGRESSIVE) {
            mainFrameFile = candidate
            return
        }
        collect(candidate)
    }

    private fun collect(candidate: StreamCandidate) {
        val kind = candidate.kind
        candidates.add(candidate)
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

    /**
     * The page offered [url] as a download (`Content-Disposition: attachment`), so the user gets
     * the confirm dialog instead. A video file sent that way was also seen as a request, and its
     * pending grace-window announce would open the player 1.5s later; dropping its candidates
     * makes that announce find nothing (or only the page's other streams).
     */
    fun onDownload(url: String) {
        if (mainFrameFile?.url == url) mainFrameFile = null
        // The member removeAll(Collection) runs under the list's lock, so a worker thread's
        // concurrent onRequest cannot be lost; Kotlin's removeAll { } rewrites by index, and
        // removeIf needs API 24.
        candidates.removeAll(candidates.filter { it.url == url }.toSet())
    }

    private fun announce() {
        if (hasStream() && announced.compareAndSet(false, true)) onStreamAvailable()
    }

    fun hasStream(): Boolean = candidates.any { it.kind in PLAYABLE }

    fun bestRequest(): PlaybackRequest? {
        val best = StreamCandidateSelector.selectBest(candidates) ?: return null
        val cookie = CookieManager.getInstance().getCookie(best.url)
        val headers = PlaybackHeaders.build(best.url, pageUrl, userAgent(), best.headers)
            .let { if (cookie != null) it + ("Cookie" to cookie) else it }
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
