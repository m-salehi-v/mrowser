package net.mrowser.stream

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import net.mrowser.adblock.AdBlocker
import net.mrowser.web.ExternalSchemePolicy

/**
 * Feeds every request URL to the StreamSniffer, answers listed ad requests with a stand-in
 * (see [AdBlocker]), and routes navigations the WebView cannot or should not load out: non-web
 * schemes to the system (see [ExternalSchemePolicy]), listed ad hosts to nowhere.
 */
class SniffingWebViewClient(
    private val sniffer: StreamSniffer,
    private val adBlocker: AdBlocker,
    private val onNavigate: (String) -> Unit = {},
    private val onLoaded: (String) -> Unit = {},
    /** Hand a non-web URL (`obtainium://`, `intent://`, `mailto:`) to the system. */
    private val onExternalScheme: (String) -> Unit = {},
    /** A top-level navigation to a listed host was refused; the page stays. */
    private val onNavigationBlocked: () -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            adBlocker.onPageStarted(url)
            sniffer.onPageStarted(url)
            onNavigate(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        adBlocker.onPageLoaded()
        if (url != null) onLoaded(url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        val target = url.toString()
        if (consume(url.scheme, target, request.hasGesture())) return true
        return request.isForMainFrame && refuseIfListed(target)
    }

    @Deprecated("Kept for API < 24, which does not get the WebResourceRequest overload.")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val target = url ?: return false
        // Pre-24 there is no gesture flag at all. Treating it as user-initiated keeps app links
        // working on those devices; refusing every one of them is the bug being fixed.
        if (consume(Uri.parse(target).scheme, target, isUserGesture = true)) return true
        // No frame flag either: this overload is only called for top-level navigations.
        return refuseIfListed(target)
    }

    /** @return true when mrowser took the navigation and the WebView must not load it. */
    private fun consume(scheme: String?, url: String, isUserGesture: Boolean): Boolean =
        when (ExternalSchemePolicy.decide(scheme, isUserGesture)) {
            ExternalSchemePolicy.Decision.LetWebViewLoad -> false
            ExternalSchemePolicy.Decision.LaunchExternalApp -> {
                onExternalScheme(url)
                true
            }
            // Swallowed: a non-web link the page fired on its own. Returning true keeps the
            // ERR_UNKNOWN_URL_SCHEME error page off a navigation the user never asked for.
            ExternalSchemePolicy.Decision.Ignore -> true
        }

    /** Cancelling here leaves the current page in place — never an empty body for a page. */
    private fun refuseIfListed(url: String): Boolean {
        if (!adBlocker.isBlockedNavigation(url)) return false
        onNavigationBlocked()
        return true
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url?.toString() ?: return null
        if (req.isForMainFrame) adBlocker.onMainFrameRequest(url)
        sniffer.onRequest(url)
        return adBlocker.intercept(req)
    }
}
