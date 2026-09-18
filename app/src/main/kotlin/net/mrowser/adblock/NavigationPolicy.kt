package net.mrowser.adblock

import net.mrowser.web.UrlHost

/**
 * Pure: whether a renderer-initiated top-level navigation — a `window.open` destination seen
 * through the relay, or an in-page `location` change — may load.
 *
 * The user gesture is no signal here: Chromium already refused gestureless opens before the
 * app sees them, and a click-hijack script fires inside the user's click. What is checkable is
 * the destination. There is deliberately no first-party exemption: being sent to a listed host
 * is what is refused, whoever sent you. User-typed URLs go through `loadUrl` and never reach
 * this policy.
 */
object NavigationPolicy {

    enum class Decision { LOAD, BLOCK }

    fun decide(targetUrl: String, enabled: Boolean, allowlisted: Boolean, list: BlockList): Decision {
        if (!enabled || allowlisted) return Decision.LOAD
        val scheme = targetUrl.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return Decision.LOAD
        val host = UrlHost.of(targetUrl) ?: return Decision.LOAD
        return if (list.contains(host)) Decision.BLOCK else Decision.LOAD
    }
}
