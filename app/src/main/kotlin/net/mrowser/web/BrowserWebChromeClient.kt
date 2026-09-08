package net.mrowser.web

import android.app.Activity
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Handles HTML5 fullscreen video — swaps the player to a fullscreen view over everything, hides
 * chrome + cursor, keeps the screen on, and restores on exit — and decides what happens when a
 * page asks for a new window (see [onCreateWindow]).
 */
class BrowserWebChromeClient(
    private val activity: Activity,
    private val container: ViewGroup,
    private val onEnter: () -> Unit,
    private val onExit: () -> Unit,
    private val onTitle: (url: String, title: String) -> Unit = { _, _ -> },
    private val onPopupBlocked: () -> Unit = {},
    private val blockPopups: () -> Boolean = { true }
) : WebChromeClient() {

    private var customView: View? = null
    private var callback: CustomViewCallback? = null

    val isFullscreen: Boolean get() = customView != null

    override fun onReceivedTitle(view: WebView?, title: String?) {
        val url = view?.url ?: return
        if (!title.isNullOrBlank()) onTitle(url, title)
    }

    /**
     * Decides what to do when a page asks for a new window; see [PopupPolicy].
     *
     * mrowser has no tabs, so a user-opened window is loaded in the window the user is already
     * looking at — a poster, a trailer or an external link opens as expected and BACK returns.
     * Only a window the page opened by itself is refused.
     */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val host = view ?: return false
        return when (PopupPolicy.decide(isUserGesture, blockPopups())) {
            PopupPolicy.Decision.AllowNewWindow ->
                super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            PopupPolicy.Decision.Block -> {
                onPopupBlocked()
                false
            }
            PopupPolicy.Decision.OpenInCurrentWindow -> openInCurrentWindow(host, resultMsg)
        }
    }

    /**
     * Hands the pending navigation a throwaway WebView purely to learn its URL, then loads that
     * URL in [host]. There is no public way to read the target of a `window.open` otherwise —
     * the URL only arrives at the new window.
     */
    private fun openInCurrentWindow(host: WebView, resultMsg: Message?): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val relay = WebView(host.context)
        relay.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                adopt(view, request?.url?.toString())

            @Deprecated("Kept for API < 24, which does not get the WebResourceRequest overload.")
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                adopt(view, url)

            private fun adopt(relayView: WebView?, url: String?): Boolean {
                if (url != null) host.loadUrl(url)
                // Destroying from inside the relay's own callback is not safe; post it.
                relayView?.post { relayView.destroy() }
                return true
            }
        }
        transport.webView = relay
        resultMsg.sendToTarget()
        return true
    }

    override fun onShowCustomView(view: View, cb: CustomViewCallback) {
        if (customView != null) {
            cb.onCustomViewHidden()
            return
        }
        customView = view
        callback = cb
        container.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onEnter()
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        container.removeView(view)
        customView = null
        callback?.onCustomViewHidden()
        callback = null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onExit()
    }

    /** @return true if a fullscreen view was active and is now dismissed. */
    fun exitIfFullscreen(): Boolean {
        if (customView == null) return false
        onHideCustomView()
        return true
    }
}
