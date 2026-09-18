package net.mrowser.data

/** App-wide settings. Defaults are the shipped values. Immutable — update via copy(). */
data class Settings(
    val autoOpenPlayer: Boolean = true,
    val cursorSpeed: CursorSpeed = CursorSpeed.NORMAL,
    /**
     * Refuse a window the page opens whose destination is on the block list. On a browser with
     * no tabs a new window replaces the page, which is how streaming sites turn a click on
     * their video into an ad. Off opens whatever was clicked with no list check.
     */
    val blockPopups: Boolean = true,
    /** Answer requests to listed ad hosts with an empty stand-in, and refuse in-page
     *  navigations to them. Sub-resources only; see AdBlockPolicy for what is never blocked. */
    val blockAds: Boolean = true,
    /** Registrable domains (`site.example`) on which the user has allowed ads. */
    val adsAllowedOn: Set<String> = emptySet(),
    /** Internal bookkeeping, not a user preference: default favorites written once. */
    val seeded: Boolean = false,
    /** Internal bookkeeping, not a user preference: hold-BACK hint shown once. */
    val navHintShown: Boolean = false
)
