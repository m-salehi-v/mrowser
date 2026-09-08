package net.mrowser.web

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.os.Message
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
    private val onTitle: (url: String, title: String) -> Unit = { _, _ -> },
    private val onPopupBlocked: () -> Unit = {},
    private val blockPopups: () -> Boolean = { true }
) : WebChromeClient() {

    /**
     * Refuses every new window. Pages that wrap their video in a click handler use
     * `window.open` / `target="_blank"` to turn a play click into an unrelated page; because the
     * click is a real user gesture, a gesture-based filter cannot tell it apart from a link.
     * Refusing the window is the precise fix, and it leaves ordinary same-window navigation alone.
     *
     * Returning false without acting on [resultMsg] means no window is created and the current
     * page is left exactly where it was.
     */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (!blockPopups()) return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
        onPopupBlocked()
        return false
    }

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
