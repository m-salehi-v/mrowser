package net.mrowser.web

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText

/**
 * Drives the address-bar overlay: visibility state, slide/fade animation,
 * idle auto-hide, and focus handoff. Visibility decisions delegate to ChromeVisibility.
 */
class ChromeController(
    private val bar: View,
    private val urlInput: EditText,
    private val webView: View
) {
    private val handler = Handler(Looper.getMainLooper())
    private var state = ChromeVisibility.State.HIDDEN
    private val hideRunnable = Runnable { dispatch(ChromeVisibility.Event.IdleElapsed) }

    companion object { const val IDLE_MS = 4000L }

    val isVisible: Boolean get() = state == ChromeVisibility.State.VISIBLE

    private var pendingFocus = true

    fun requestReveal(atTop: Boolean, focusInput: Boolean = true) {
        pendingFocus = focusInput
        dispatch(ChromeVisibility.Event.RevealRequested(atTop))
    }
    fun onInteracted() = dispatch(ChromeVisibility.Event.Interacted)
    fun onPageInteracted() = dispatch(ChromeVisibility.Event.PageInteracted)

    private fun dispatch(event: ChromeVisibility.Event) {
        val next = ChromeVisibility.reduce(state, event)
        if (next != state) {
            state = next
            if (next == ChromeVisibility.State.VISIBLE) animateIn() else animateOut()
        }
        if (state == ChromeVisibility.State.VISIBLE) armIdle()
    }

    private fun armIdle() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, IDLE_MS)
    }

    private fun animateIn() {
        bar.visibility = View.VISIBLE
        bar.translationY = -bar.height.toFloat()
        bar.alpha = 0f
        bar.animate().translationY(0f).alpha(1f).setDuration(180).start()
        if (pendingFocus) urlInput.requestFocus()
    }

    private fun animateOut() {
        handler.removeCallbacks(hideRunnable)
        bar.animate().translationY(-bar.height.toFloat()).alpha(0f).setDuration(160)
            .withEndAction { bar.visibility = View.GONE }.start()
        webView.requestFocus()
    }
}
