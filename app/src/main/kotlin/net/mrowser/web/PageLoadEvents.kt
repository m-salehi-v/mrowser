package net.mrowser.web

/**
 * What the WebView tells the browser about a main-frame load, in one place. Every method has an
 * empty default so a listener takes only the signals it cares about.
 */
interface PageLoadEvents {
    /** A main-frame navigation is about to start (a click, earliest) or has started. */
    fun onStarting(url: String) {}

    /** The first content of the page is visible. */
    fun onCommitVisible(url: String) {}

    /** The main frame failed to load (DNS, refused, timeout, ...). */
    fun onMainFrameError(url: String, code: Int, description: String?) {}

    /** The main frame's certificate was rejected (the load is cancelled). */
    fun onMainFrameSslError(url: String) {}

    /** The main frame loaded but the server answered [status] (>= 400). */
    fun onMainFrameHttpError(url: String, status: Int) {}

    /**
     * A reload of a page that was a form's result was not sent: the form is never posted again,
     * so the page stays as it is and no start, finish or error follows.
     */
    fun onReloadNotResent() {}

    /**
     * The renderer is gone: [crashed] false when the system killed it (to free memory) rather
     * than it crashing. @return true when this was handled and the app must not be killed.
     */
    fun onRendererGone(crashed: Boolean): Boolean = false
}
