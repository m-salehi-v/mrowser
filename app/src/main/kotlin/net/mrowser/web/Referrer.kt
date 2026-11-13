package net.mrowser.web

import java.net.URI
import java.net.URISyntaxException

/**
 * Pure: the Referer a browser sends from a page to a URL, under its default policy,
 * `strict-origin-when-cross-origin`. Used where the app makes the request on the page's behalf:
 * a download, and a click the User-Agent gate cancelled and re-issued with `loadUrl`.
 */
object Referrer {

    /**
     * The page without its fragment and user info to its own origin, only the page's origin
     * (`scheme://host[:port]/`) anywhere else, and nothing from an https page to an http URL.
     * Null for no web page.
     */
    fun of(pageUrl: String?, targetUrl: String): String? {
        val page = parse(pageUrl) ?: return null
        val downgrade = page.scheme == "https" && targetUrl.startsWith("http://", ignoreCase = true)
        if (downgrade) return null
        val target = parse(targetUrl)
        return if (target != null && target.origin == page.origin) page.withoutFragment else page.origin + "/"
    }

    private class WebUrl(val scheme: String, val origin: String, val withoutFragment: String)

    /** http(s) only; user info and fragment dropped, default ports dropped, scheme and host lowercased. */
    private fun parse(url: String?): WebUrl? {
        if (url == null || !isWeb(url)) return null
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val port = uri.port
        val defaultPort = if (scheme == "https") 443 else 80
        val origin = "$scheme://$host" + if (port == -1 || port == defaultPort) "" else ":$port"
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return WebUrl(scheme, origin, origin + path + query)
    }

    private fun isWeb(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
