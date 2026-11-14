package net.mrowser.stream

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import net.mrowser.adblock.AdBlocker
import net.mrowser.web.ExternalSchemePolicy
import net.mrowser.web.MainFrameUrl
import net.mrowser.web.PageLoadEvents

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
    private val onNavigationBlocked: () -> Unit = {},
    /** Page-load signals (starting, visible, errors); see [PageLoadEvents]. */
    private val pageEvents: PageLoadEvents = object : PageLoadEvents {},
    /**
     * Asked for every top-level navigation that will go ahead. true = cancel; the app re-issues
     * this main-frame navigation with the other User-Agent. [userGesture] is true only for a
     * navigation the user's own click started, never for a server redirect. UI thread.
     */
    private val userAgentGate: (url: String, userGesture: Boolean) -> Boolean = { _, _ -> false }
) : WebViewClient() {

    /** The main-frame URL being loaded, to tell a main-frame SSL error from a sub-resource one. */
    @Volatile
    private var navigatingUrl: String? = null

    private fun starting(url: String) {
        navigatingUrl = url
        pageEvents.onStarting(url)
    }

    /**
     * The app is about to `loadUrl(url)` itself. Such a load reaches neither
     * `shouldOverrideUrlLoading` nor, necessarily, `onPageStarted` before the certificate is
     * checked (a WebView may post `onPageStarted` only at commit, which a refused certificate
     * never reaches), so without this its certificate error would not be recognised as the main
     * frame's and the user would be left on a blank page.
     */
    fun expectNavigation(url: String) {
        navigatingUrl = url
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            adBlocker.onPageStarted(url)
            sniffer.onPageStarted(url)
            onNavigate(url)
            starting(url)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        if (url != null) pageEvents.onCommitVisible(url)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request == null || error == null || !request.isForMainFrame) return
        pageEvents.onMainFrameError(request.url.toString(), error.errorCode, error.description?.toString())
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request == null || errorResponse == null || !request.isForMainFrame) return
        if (errorResponse.statusCode >= 400) {
            pageEvents.onMainFrameHttpError(
                request.url.toString(), errorResponse.statusCode, errorResponse.responseHeaders.orEmpty()
            )
        }
    }

    /** Always cancels (never proceeds past a bad certificate); only the main frame is reported. */
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
        val url = error?.url ?: return
        if (MainFrameUrl.same(url, navigatingUrl)) pageEvents.onMainFrameSslError(url)
    }

    /**
     * A reload (the bar's, the page menu's Desktop site, the User-Agent safety net) of a POST
     * result. Never re-post a form behind the user's back; the default does the same, but
     * silently, and the loading line would then wait out its timeout on a page that is there.
     */
    override fun onFormResubmission(view: WebView?, dontResend: Message?, resend: Message?) {
        dontResend?.sendToTarget()
        pageEvents.onReloadNotResent()
    }

    /** Only called on API 26+, where [RenderProcessGoneDetail.didCrash] exists. */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail?.didCrash() ?: true else true
        return pageEvents.onRendererGone(crashed)
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
        if (!request.isForMainFrame) return false
        if (refuseIfListed(target)) return true
        // A redirect keeps its click's gesture flag; counting it as a fresh click would let a
        // redirect ping-pong between a desktop and a mobile site switch (and reload) forever.
        if (userAgentGate(target, request.hasGesture() && !request.isRedirect)) return true
        // Not consumed and not refused: the page really is about to navigate. This is the
        // earliest signal of a click; onPageStarted waits for the server.
        starting(target)
        return false
    }

    @Deprecated("Kept for API < 24, which does not get the WebResourceRequest overload.")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val target = url ?: return false
        // Pre-24 there is no gesture flag at all. Treating it as user-initiated keeps app links
        // working on those devices; refusing every one of them is the bug being fixed.
        if (consume(Uri.parse(target).scheme, target, isUserGesture = true)) return true
        // No frame flag either: this overload is only called for top-level navigations.
        if (refuseIfListed(target)) return true
        // Unlike the scheme check above, no gesture here: this overload is also called for every
        // server redirect, which would then reset the User-Agent loop guard on each hop. A click
        // still resets it, through the cursor's page tap.
        if (userAgentGate(target, false)) return true
        starting(target)
        return false
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
        sniffer.onRequest(url, req.requestHeaders.orEmpty(), req.isForMainFrame)
        return adBlocker.intercept(req)
    }
}
