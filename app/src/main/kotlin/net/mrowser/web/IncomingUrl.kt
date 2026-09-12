package net.mrowser.web

/**
 * Pure: vets a URL that reached mrowser from outside — another app's `ACTION_VIEW` intent, or
 * the `browser_fallback_url` an `intent://` link carries.
 *
 * Only `http`/`https` survive. The WebView also resolves `file:`, `content:`, `data:` and
 * `javascript:`, so loading whatever arrives would let any app on the box read the app's private
 * files or run script in the page — an incoming URL is untrusted input, not a navigation.
 */
object IncomingUrl {

    /** The action string of `Intent.ACTION_VIEW`, kept as a literal so this stays Android-free. */
    const val ACTION_VIEW = "android.intent.action.VIEW"

    /** @return [url] if it is a web page, else null. */
    fun webUrlOrNull(url: String?): String? {
        val trimmed = url?.trim() ?: return null
        val scheme = trimmed.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        return trimmed.takeIf { it.substringAfter("://").isNotBlank() }
    }

    /** @return the page a VIEW intent asks mrowser to open, or null if it asks for nothing
     *  openable (the launcher's own MAIN intent, no data, or a non-web URL). */
    fun fromViewIntent(action: String?, data: String?): String? =
        if (action == ACTION_VIEW) webUrlOrNull(data) else null
}
