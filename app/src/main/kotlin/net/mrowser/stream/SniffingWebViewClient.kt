package net.mrowser.stream

import android.graphics.Bitmap
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import net.mrowser.web.NavigationGuard

/**
 * Feeds every request URL to the StreamSniffer, and blocks pop-up navigations.
 *
 * Resource loading is never altered — only top-level navigation is filtered, and only by the
 * pure [NavigationGuard]; this class just reads the request and applies the decision.
 */
class SniffingWebViewClient(
    private val sniffer: StreamSniffer,
    private val onNavigate: (String) -> Unit = {},
    private val onLoaded: (String) -> Unit = {},
    private val onBlockedNavigation: (String) -> Unit = {}
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val target = request?.url ?: return false
        // isRedirect landed in API 24; below that a redirect is indistinguishable from a
        // scripted jump, so treat it as not-a-redirect and let the gesture decide.
        val isRedirect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.isRedirect
        val decision = NavigationGuard.decide(
            currentHost = view?.url?.let { android.net.Uri.parse(it).host }.orEmpty(),
            targetHost = target.host.orEmpty(),
            hasGesture = request.hasGesture(),
            isRedirect = isRedirect
        )
        if (decision == NavigationGuard.Decision.Allow) return false
        onBlockedNavigation(target.toString())
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            sniffer.onPageStarted(url)
            onNavigate(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) onLoaded(url)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        request?.url?.toString()?.let { sniffer.onRequest(it) }
        return null
    }
}
