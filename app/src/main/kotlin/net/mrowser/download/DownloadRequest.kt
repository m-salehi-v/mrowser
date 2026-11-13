package net.mrowser.download

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

    /** Cookie and User-Agent as the page's WebView had them; the page itself as Referer. */
    fun headers(cookie: String?, userAgent: String?, pageUrl: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (!cookie.isNullOrBlank()) out["Cookie"] = cookie
        if (!userAgent.isNullOrBlank()) out["User-Agent"] = userAgent
        if (pageUrl != null && isWeb(pageUrl)) out["Referer"] = pageUrl
        return out
    }

    private fun isWeb(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
