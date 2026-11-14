package net.mrowser.home

/**
 * Pure focus-recovery rule for the four overlays, the page-error screen and the
 * page behind them.
 *
 * D-pad focus search is window-global and the root layout's first focusable child
 * is [net.mrowser.web.CursorLayout], so whenever something destroys the focused
 * view — rebuilding the favorites grid after an edit, say — the window's fallback
 * drops focus onto the page *behind* a still-visible overlay. Focus is not null
 * then, just in the wrong subtree, and the arrows drive the hidden cursor instead
 * of the overlay. This states where focus belongs; `MainActivity` maps its views
 * to [Zone]s and re-seats it.
 */
object OverlayFocus {

    /** A view tree D-pad focus can sit in. [NONE] is "nothing focused at all". */
    enum class Zone { SETTINGS, HISTORY, DOWNLOADS, HOME, ERROR, PAGE, NONE }

    /**
     * The zone that should own focus for what is on screen, or null when [focus]
     * already sits there and nothing needs re-seating.
     *
     * Overlays are focus-modal (only one is ever visible), but the flags are ranked
     * so a transient double-visible state still resolves the same way every time:
     * settings, history, downloads, home.
     * The error screen ([net.mrowser.web.PageErrorView]) stands in for the page, so
     * it ranks below every overlay and above the page.
     */
    fun recoveryTarget(
        settingsVisible: Boolean,
        historyVisible: Boolean,
        downloadsVisible: Boolean,
        homeVisible: Boolean,
        errorVisible: Boolean,
        focus: Zone
    ): Zone? {
        val owner = when {
            settingsVisible -> Zone.SETTINGS
            historyVisible -> Zone.HISTORY
            downloadsVisible -> Zone.DOWNLOADS
            homeVisible -> Zone.HOME
            errorVisible -> Zone.ERROR
            else -> Zone.PAGE
        }
        return owner.takeIf { it != focus }
    }
}
