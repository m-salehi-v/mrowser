package net.mrowser.web

/**
 * Pure: what to do when a page asks for a new window.
 *
 * mrowser has no tabs, so there is nowhere to put a second window. The useful distinction is not
 * "window or no window" but *who asked for it*: a window opened from a click is something the
 * user wants — a poster, a trailer, an external link — and it should simply open in the window
 * they are already in, where BACK returns them. A window the page opened on its own is an
 * unsolicited pop-up and is refused.
 *
 * This mirrors what desktop browsers do, and it is why blocking every new window is wrong: it
 * makes ordinary `target="_blank"` links dead.
 */
object PopupPolicy {

    enum class Decision {
        /** Load the requested URL in the current WebView instead of a new one. */
        OpenInCurrentWindow,

        /** Refuse: the page opened this by itself. */
        Block,

        /** Blocking is off — let the WebView do whatever it would normally do. */
        AllowNewWindow,
    }

    fun decide(isUserGesture: Boolean, blockingEnabled: Boolean): Decision = when {
        !blockingEnabled -> Decision.AllowNewWindow
        isUserGesture -> Decision.OpenInCurrentWindow
        else -> Decision.Block
    }
}
