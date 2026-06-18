package net.mrowser.stream

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** Feeds every request URL to the StreamSniffer; never alters loading. */
class SniffingWebViewClient(
    private val sniffer: StreamSniffer,
    private val onNavigate: (String) -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            sniffer.onPageStarted(url)
            onNavigate(url)
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        request?.url?.toString()?.let { sniffer.onRequest(it) }
        return null
    }
}
