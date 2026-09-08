package net.mrowser.data

/** App-wide settings. Defaults are the shipped values. Immutable — update via copy(). */
data class Settings(
    val autoOpenPlayer: Boolean = true,
    val cursorSpeed: CursorSpeed = CursorSpeed.NORMAL,
    /**
     * Refuse pages that try to open a new window. On a browser with no tabs there is nowhere to
     * put one, so the page would otherwise be replaced — which is how sites turn a click on their
     * video into an unrelated page. Off restores that behaviour for links that rely on it.
     */
    val blockPopups: Boolean = true,
    /** Internal bookkeeping, not a user preference: default favorites written once. */
    val seeded: Boolean = false,
    /** Internal bookkeeping, not a user preference: hold-BACK hint shown once. */
    val navHintShown: Boolean = false
)
