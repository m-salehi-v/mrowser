package net.mrowser.web

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import net.mrowser.R

/**
 * Why a page didn't load: a native screen over the web area, never HTML in the WebView. An icon,
 * "This page didn't load", a one-line reason naming the host, a dim error code, and **Try again**
 * (focused), **Go back** (only with history), **Home**.
 *
 * It sits outside [CursorLayout] (whose key routing would otherwise take the D-pad) and is a
 * focus zone of its own in [net.mrowser.home.OverlayFocus], below the overlays and above the page.
 * What the buttons do is the Activity's business; see [bind].
 */
class PageErrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val message: TextView
    private val code: TextView
    private val retryButton: View
    private val backButton: View

    private var onRetry: () -> Unit = {}
    private var onBack: () -> Unit = {}
    private var onHome: () -> Unit = {}

    init {
        LayoutInflater.from(context).inflate(R.layout.page_error_view, this, true)
        message = findViewById(R.id.pageErrorMessage)
        code = findViewById(R.id.pageErrorCode)
        retryButton = findViewById(R.id.pageErrorRetry)
        backButton = findViewById(R.id.pageErrorBack)
        retryButton.setOnClickListener { onRetry() }
        backButton.setOnClickListener { onBack() }
        findViewById<View>(R.id.pageErrorHome).setOnClickListener { onHome() }
    }

    fun bind(onRetry: () -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
        this.onRetry = onRetry
        this.onBack = onBack
        this.onHome = onHome
    }

    /**
     * Shows [kind] for [url]; [code] is the dim technical line (hidden when blank). With
     * [takeFocus] false the screen comes up without pulling focus — an overlay is in front of it
     * and keeps the D-pad until it closes.
     */
    fun show(kind: PageErrorKind, url: String, code: String, canGoBack: Boolean, takeFocus: Boolean = true) {
        val host = UrlHost.of(url) ?: url
        message.text = context.getString(messageRes(kind), host)
        this.code.text = code
        this.code.visibility = if (code.isBlank()) View.GONE else View.VISIBLE
        backButton.visibility = if (canGoBack) View.VISIBLE else View.GONE
        visibility = View.VISIBLE
        // Post: a synchronous requestFocus right after VISIBLE can no-op before the layout pass
        // (matches the overlays).
        if (takeFocus) post { restoreFocus() }
    }

    fun hide() {
        visibility = View.GONE
    }

    /** Re-seat D-pad focus on Try again. Returns false if it couldn't take focus. */
    fun restoreFocus(): Boolean = visibility == View.VISIBLE && retryButton.requestFocus()

    private fun messageRes(kind: PageErrorKind): Int = when (kind) {
        PageErrorKind.NO_INTERNET -> R.string.page_error_no_internet
        PageErrorKind.NOT_FOUND -> R.string.page_error_not_found
        PageErrorKind.TIMED_OUT -> R.string.page_error_timed_out
        PageErrorKind.CONNECTION -> R.string.page_error_connection
        PageErrorKind.NOT_SECURE -> R.string.page_error_not_secure
        PageErrorKind.SERVER -> R.string.page_error_server
        PageErrorKind.CRASHED -> R.string.page_error_crashed
        PageErrorKind.OTHER -> R.string.page_error_other
    }
}
