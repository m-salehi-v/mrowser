package net.mrowser.adblock

import net.mrowser.web.RegistrableDomain
import net.mrowser.web.UrlHost

/**
 * Pure: whether a top-level navigation — a `window.open` destination seen through the relay, an
 * in-page `location` change, or a redirect hop from a URL the user asked for by name — may load.
 *
 * A pop-up's own gesture is no signal: Chromium already refused gestureless opens before the app
 * sees them, and a click-hijack script fires inside the user's click. What *is* checkable is
 * whether this navigation traces back to something the user typed/tapped (`userInitiated`, set
 * by the caller — see `AdBlocker`) and whether it stays on the site already being shown
 * (same registrable domain as `pageHost`). Neither exempts a *third-party* destination: being
 * sent to a listed host from elsewhere is what stays refused, whoever sent you.
 */
object NavigationPolicy {

    enum class Decision { LOAD, BLOCK }

    fun decide(
        targetUrl: String,
        pageHost: String?,
        userInitiated: Boolean,
        enabled: Boolean,
        allowlisted: Boolean,
        list: BlockList
    ): Decision {
        if (!enabled || allowlisted || userInitiated) return Decision.LOAD
        val scheme = targetUrl.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return Decision.LOAD
        val host = UrlHost.of(targetUrl) ?: return Decision.LOAD
        if (pageHost != null && RegistrableDomain.of(host) == RegistrableDomain.of(pageHost)) return Decision.LOAD
        return if (list.contains(host)) Decision.BLOCK else Decision.LOAD
    }
}
