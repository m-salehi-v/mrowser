package net.mrowser.adblock

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import net.mrowser.web.RegistrableDomain
import net.mrowser.web.UrlHost

/**
 * Android glue around the pure ad-block policies. Safe to call from WebView worker threads:
 * the list and page host are volatile, the counter atomic, settings are read through the
 * provider lambdas (immutable snapshots), and UI callbacks are posted to the main looper.
 *
 * Until [load] finishes the list is [BlockList.EMPTY] and everything passes — no request thread
 * ever waits on the load.
 */
class AdBlocker(
    private val blockAds: () -> Boolean,
    private val blockPopups: () -> Boolean,
    private val allowedSites: () -> Set<String>,
    private val onCountChanged: (Int) -> Unit
) {

    @Volatile var list: BlockList = BlockList.EMPTY
        private set

    /** Host of the page being shown; set from the main-frame request and onPageStarted. */
    @Volatile var pageHost: String? = null
        private set

    /** The user asked for the in-flight load by name (URL bar, home, favorite, incoming intent). */
    @Volatile private var userNavigation = false

    private val count = AtomicInteger(0)
    private val ui = Handler(Looper.getMainLooper())

    /** Reads and hashes the list on a background thread; a failure leaves [BlockList.EMPTY]. */
    fun load(source: () -> InputStream) {
        Thread({
            val loaded = runCatching {
                source().bufferedReader().useLines { BlockList.fromLines(it) }
            }.onFailure {
                Log.w(TAG, "block list unavailable; blocking disabled", it)
            }.getOrDefault(BlockList.EMPTY)
            list = loaded
            Log.i(TAG, "block list loaded: ${loaded.size} domains")
        }, "blocklist-load").start()
    }

    fun onPageStarted(url: String) {
        pageHost = UrlHost.of(url)
        userNavigation = false
        count.set(0)
        ui.post { onCountChanged(0) }
    }

    fun onMainFrameRequest(url: String) {
        pageHost = UrlHost.of(url)
    }

    /** The user asked for this URL by name (URL bar, home, favorite, incoming intent). */
    fun onUserNavigation() {
        userNavigation = true
    }

    /** Backstop for a load that never commits; see [onPageStarted]. */
    fun onPageLoaded() {
        userNavigation = false
    }

    fun isAllowlisted(host: String? = pageHost): Boolean =
        host != null && RegistrableDomain.of(host) in allowedSites()

    /** For [android.webkit.WebViewClient.shouldInterceptRequest]. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? =
        intercept(request.url.toString(), request.isForMainFrame, request.requestHeaders?.get("Accept"), pageHost)

    /**
     * For [android.webkit.ServiceWorkerClient.shouldInterceptRequest] (API 24+). A service
     * worker fetch has no WebView, so the page is taken from its Origin/Referer header.
     */
    fun interceptServiceWorker(request: WebResourceRequest): WebResourceResponse? {
        val headers = request.requestHeaders.orEmpty()
        val origin = headers["Origin"] ?: headers["Referer"]
        val host = origin?.let { UrlHost.of(it) } ?: pageHost
        return intercept(request.url.toString(), false, headers["Accept"], host)
    }

    private fun intercept(url: String, isMainFrame: Boolean, accept: String?, host: String?): WebResourceResponse? {
        val decision = AdBlockPolicy.decide(url, host, isMainFrame, blockAds(), isAllowlisted(host), list)
        if (decision != AdBlockPolicy.Decision.BLOCK) return null
        bump()
        val kind = BlockedResponse.kindFor(url, accept)
        return WebResourceResponse(
            kind.mimeType, "utf-8", 200, "OK",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(kind.body)
        )
    }

    /** A `window.open` destination seen through the relay. Never user-initiated. Counts when refused. */
    fun isBlockedPopup(url: String): Boolean = gate(url, userInitiated = false, blockPopups())

    /** An in-page top-level navigation. Counts when refused. */
    fun isBlockedNavigation(url: String): Boolean = gate(url, userInitiated = userNavigation, blockAds())

    private fun gate(url: String, userInitiated: Boolean, enabled: Boolean): Boolean {
        val blocked = NavigationPolicy.decide(
            url, pageHost, userInitiated, enabled, isAllowlisted(), list
        ) == NavigationPolicy.Decision.BLOCK
        if (blocked) bump()
        return blocked
    }

    private fun bump() {
        val n = count.incrementAndGet()
        ui.post { onCountChanged(n) }
    }

    private companion object {
        const val TAG = "AdBlocker"
    }
}
