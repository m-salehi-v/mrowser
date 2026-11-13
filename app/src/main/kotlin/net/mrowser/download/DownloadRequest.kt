package net.mrowser.download

import java.net.URI
import java.net.URISyntaxException

/** What to download: the link, the name to save it as, and the headers the request carries. */
data class DownloadSpec(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val title: String = fileName,
)

/** Pure: which links a download can take, and the headers that go with one. */
object DownloadRequest {

    /** `blob:`, `data:` and the rest are a page's own memory, which DownloadManager cannot fetch. */
    fun isSupported(url: String): Boolean = isWeb(url)

    /** Cookie and User-Agent as the page's WebView had them; a Referer as a browser would send it. */
    fun headers(cookie: String?, userAgent: String?, pageUrl: String?, downloadUrl: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (!cookie.isNullOrBlank()) out["Cookie"] = cookie
        if (!userAgent.isNullOrBlank()) out["User-Agent"] = userAgent
        referrer(pageUrl, downloadUrl)?.let { out["Referer"] = it }
        return out
    }

    /**
     * The Referer under the browser default, `strict-origin-when-cross-origin`: the page without
     * its fragment and user info to its own origin, only the page's origin (`scheme://host[:port]/`)
     * anywhere else, and nothing from an https page to an http link. Null for no web page.
     */
    fun referrer(pageUrl: String?, downloadUrl: String): String? {
        val page = parse(pageUrl) ?: return null
        val downgrade = page.scheme == "https" && downloadUrl.startsWith("http://", ignoreCase = true)
        if (downgrade) return null
        val target = parse(downloadUrl)
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
