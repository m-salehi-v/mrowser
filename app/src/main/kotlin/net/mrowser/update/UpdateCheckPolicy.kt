package net.mrowser.update

/** Pure: when to ask GitHub, and whether the cached answer is worth showing. */
object UpdateCheckPolicy {

    /** One check a day. GitHub allows 60 unauthenticated requests an hour per IP, and carrier
     *  -grade NAT puts many users behind one address, so this stays deliberately far under. */
    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    /**
     * A timestamp in the future means the clock moved backwards (a TV that lost its time and
     * re-synced). Without this case the check would be frozen until real time caught up.
     */
    fun shouldCheck(nowMs: Long, lastCheckedAtMs: Long): Boolean =
        lastCheckedAtMs > nowMs || nowMs - lastCheckedAtMs >= CHECK_INTERVAL_MS

    /**
     * The release to advertise, or null for none. Comparing against the running version — rather
     * than storing a "seen" flag — is what makes the banner self-clearing: once the user installs
     * the new APK the cached release stops being newer and the line disappears on its own.
     */
    fun bannerFor(installedVersion: String, cached: Release?): Release? {
        if (cached == null) return null
        return if (VersionCompare.isNewer(cached.version, installedVersion)) cached else null
    }
}
