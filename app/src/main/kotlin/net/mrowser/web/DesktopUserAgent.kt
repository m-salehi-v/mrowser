package net.mrowser.web

/**
 * The "Desktop site" User-Agent, derived from the WebView's own default string.
 *
 * The platform is replaced with desktop Linux, and the markers a site reads as "mobile" or "embedded
 * WebView" are dropped: `; wv`, `Mobile `, and `Version/4.0` (which also marks a WebView). The
 * Chrome version is the WebView's real one, so feature detection keyed on it stays truthful.
 */
object DesktopUserAgent {
    const val FALLBACK_CHROME = "120.0.0.0"
    private const val FALLBACK_WEBKIT = "537.36"

    private val CHROME = Regex("""Chrome/([\d.]+)""")
    private val WEBKIT = Regex("""AppleWebKit/([\d.]+)""")

    fun from(defaultUa: String): String {
        val chrome = CHROME.find(defaultUa)?.groupValues?.get(1) ?: FALLBACK_CHROME
        val webkit = WEBKIT.find(defaultUa)?.groupValues?.get(1) ?: FALLBACK_WEBKIT
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/$webkit (KHTML, like Gecko) Chrome/$chrome Safari/$webkit"
    }
}
