package net.mrowser.web

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Handles HTML5 fullscreen video: swaps the player to a fullscreen view over
 * everything, hides chrome + cursor, keeps the screen on, and restores on exit.
 */
class BrowserWebChromeClient(
    private val activity: Activity,
    private val container: ViewGroup,
    private val onEnter: () -> Unit,
    private val onExit: () -> Unit,
    private val onTitle: (url: String, title: String) -> Unit = { _, _ -> }
) : WebChromeClient() {

    private var customView: View? = null
    private var callback: CustomViewCallback? = null

    val isFullscreen: Boolean get() = customView != null

    override fun onReceivedTitle(view: WebView?, title: String?) {
        val url = view?.url ?: return
        if (!title.isNullOrBlank()) onTitle(url, title)
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
