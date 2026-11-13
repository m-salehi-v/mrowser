package net.mrowser.web

/**
 * Pure: the per-site choices behind the page menu (zoom, Desktop site), keyed by registrable
 * domain, so `m.example.com` and `www.example.com` share one choice.
 *
 * Maps hold only what differs from the default: a zoom of 100 or a desktop choice equal to the
 * global default is removed, so changing the default later is not shadowed by stale entries.
 */
object SiteSettings {

    /** The registrable domain of an http(s) page, else null (`about:blank`, `data:`, no page). */
    fun site(url: String?): String? {
        if (url == null) return null
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
        val host = UrlHost.of(url) ?: return null
        return RegistrableDomain.of(host)
    }

    fun zoomFor(zoomBySite: Map<String, Int>, site: String?): Int =
        if (site == null) PageZoom.DEFAULT else zoomBySite[site] ?: PageZoom.DEFAULT

    fun withZoom(zoomBySite: Map<String, Int>, site: String, percent: Int): Map<String, Int> =
        if (percent == PageZoom.DEFAULT) zoomBySite - site else zoomBySite + (site to percent)

    fun wantsDesktop(site: String?, byDefault: Boolean, overrides: Map<String, Boolean>): Boolean =
        if (site == null) byDefault else overrides[site] ?: byDefault

    fun withDesktop(
        overrides: Map<String, Boolean>,
        site: String,
        desktop: Boolean,
        byDefault: Boolean
    ): Map<String, Boolean> =
        if (desktop == byDefault) overrides - site else overrides + (site to desktop)

    fun pruneDesktop(overrides: Map<String, Boolean>, byDefault: Boolean): Map<String, Boolean> =
        overrides.filterValues { it != byDefault }
}
