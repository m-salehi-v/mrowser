package net.mrowser.web

/**
 * What the error screen's **Go back** (and BACK) and **Try again** do, from plain facts about the
 * WebView at the moment they are asked.
 *
 * The one thing that changes the answer is whether the failed load **ever committed**. A refused
 * certificate (`onReceivedSslError` → `cancel()`) never does: the page the link was clicked on is
 * still the current entry behind the screen, so Go back just uncovers it and Try again has to load
 * the failed URL. Every other failure (DNS, refused connection, a TLS handshake failure reported
 * through `onReceivedError`, …) commits Chromium's own error page as an entry with the failed
 * URL, so Go back is `goBack()` and Try again is `reload()`. Keying this on the error's kind is
 * wrong: `ERR_SSL_*`/`ERR_CERT_*` from `onReceivedError` are "Not secure" too, but they commit, and
 * uncovering them would leave the grey "Webpage not available" page on screen.
 */
object ErrorNavigation {

    data class Facts(
        /** The failed load never committed — only a refused certificate (`onMainFrameSslError`). */
        val neverCommitted: Boolean,
        /** Opened from home as a fresh tab whose back-stack is not cleared yet. */
        val clearHistoryOnLoad: Boolean,
        /** `WebView.url` now. */
        val currentUrl: String?,
        /** The URL that failed to load. */
        val failedUrl: String,
        /** `WebView.canGoBack()` now. */
        val canGoBack: Boolean,
    )

    enum class Back {
        /** Hide the screen: the page behind it is still the one the user was on. */
        UNCOVER,
        /** `WebView.goBack()`, past the committed error page. */
        GO_BACK,
        /** Nowhere to go back to: close to home. */
        HOME,
    }

    enum class Retry { RELOAD, LOAD_FAILED_URL }

    /**
     * Go back's target. A fresh tab has nothing behind it but the previous tab's blank page (its
     * back-stack is cleared at finish), so it goes home whether or not the load committed.
     */
    fun back(f: Facts): Back = when {
        f.neverCommitted && !f.clearHistoryOnLoad && isWeb(f.currentUrl) -> Back.UNCOVER
        f.canGoBack && !f.clearHistoryOnLoad -> Back.GO_BACK
        else -> Back.HOME
    }

    /** Go back is shown only when it goes somewhere other than home (BACK still goes home). */
    fun showGoBack(f: Facts): Boolean = back(f) != Back.HOME

    /**
     * `reload()` only when the failed URL is the committed entry on screen; a load that never
     * committed left the previous page current, and reloading that is not trying again.
     */
    fun retry(f: Facts): Retry =
        if (!f.neverCommitted && f.currentUrl == f.failedUrl) Retry.RELOAD else Retry.LOAD_FAILED_URL

    private fun isWeb(url: String?): Boolean =
        url != null && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
}
