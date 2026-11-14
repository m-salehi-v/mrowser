package net.mrowser.web

/**
 * Pure: the guard against User-Agent reload loops.
 *
 * A page whose preferred agent differs from the WebView's current one needs a reload with the
 * other agent. A redirect or script bouncing between a desktop-preference site and a
 * mobile-preference site would otherwise reload forever, so each site may trigger **one** switch
 * between user navigations (URL bar, home, favorite, incoming link, reload from the menu).
 * `decide` is not idempotent: a `true` records the site.
 */
class UserAgentSwitch {
    private val switched = HashSet<String>()

    fun onUserNavigation() = switched.clear()

    fun decide(site: String?, uaDesktop: Boolean, wantsDesktop: Boolean): Boolean {
        if (site == null || uaDesktop == wantsDesktop) return false
        return switched.add(site)
    }
}
