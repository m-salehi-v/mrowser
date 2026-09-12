package net.mrowser.stream

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import net.mrowser.web.ExternalSchemePolicy

/**
 * Feeds every request URL to the StreamSniffer, and routes navigations the WebView cannot load
 * itself out to the system (see [ExternalSchemePolicy]). Web navigation is untouched.
 */
class SniffingWebViewClient(
    private val sniffer: StreamSniffer,
    private val onNavigate: (String) -> Unit = {},
    private val onLoaded: (String) -> Unit = {},
    /** Hand a non-web URL (`obtainium://`, `intent://`, `mailto:`) to the system. */
    private val onExternalScheme: (String) -> Unit = {}
) : WebViewClient() {

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

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        return consume(url.scheme, url.toString(), request.hasGesture())
    }

    @Deprecated("Kept for API < 24, which does not get the WebResourceRequest overload.")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val target = url ?: return false
        // Pre-24 there is no gesture flag at all. Treating it as user-initiated keeps app links
        // working on those devices; refusing every one of them is the bug being fixed.
        return consume(Uri.parse(target).scheme, target, isUserGesture = true)
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

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        request?.url?.toString()?.let { sniffer.onRequest(it) }
        return null
    }
}
