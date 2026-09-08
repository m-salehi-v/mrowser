package net.mrowser.web

/**
 * Pure: decides whether a navigation the WebView is about to make is the user's or the page's.
 *
 * Pages that wrap their video in a click handler turn any click into a cross-site jump, which
 * replaces the page you were on — on a TV that costs you your place and a walk back through
 * history. A jump to another site that carries no user gesture and is not the continuation of
 * a redirect is such a pop-up, and is blocked.
 *
 * Same-site navigation is always allowed, so a page's own scripted routing keeps working.
 * "Same site" is a host comparison with subdomains folded in ([sameSite]); it deliberately does
 * not consult a public-suffix list, so two sites sharing a registrable domain under a multi-part
 * suffix are treated as one. That errs towards allowing, which is the safe direction here.
 */
object NavigationGuard {

    enum class Decision { Allow, Block }

    fun decide(
        currentHost: String,
        targetHost: String,
        hasGesture: Boolean,
        isRedirect: Boolean,
    ): Decision = when {
        sameSite(currentHost, targetHost) -> Decision.Allow
        // A redirect continues a navigation that was already allowed; the redirect leg itself
        // never carries a gesture, so blocking it would break ordinary login and consent flows.
        isRedirect -> Decision.Allow
        hasGesture -> Decision.Allow
        else -> Decision.Block
    }

    private fun sameSite(a: String, b: String): Boolean {
        val x = a.lowercase()
        val y = b.lowercase()
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || x.endsWith(".$y") || y.endsWith(".$x")
    }
}
