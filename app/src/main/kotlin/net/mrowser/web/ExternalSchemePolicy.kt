package net.mrowser.web

/**
 * Pure: what to do with a navigation the `WebView` is about to make.
 *
 * A `WebView` only resolves [WEB_SCHEMES] itself; every other scheme — `obtainium://`,
 * `intent://`, `market://`, `mailto:`, `tel:` — is an app's, and left alone it dead-ends on
 * `net::ERR_UNKNOWN_URL_SCHEME`. Turning those into an `Intent` is the app's job.
 *
 * The gesture check mirrors [PopupPolicy]: handing another app a link is something the user
 * asked for by clicking. A page that fires one by itself is not asking on the user's behalf,
 * and gets nothing — quietly, since an error page for a link nobody clicked is just noise.
 */
object ExternalSchemePolicy {

    enum class Decision {
        /** The WebView can load this itself — don't interfere. */
        LetWebViewLoad,

        /** Hand the URL to the system so the app registered for it opens. */
        LaunchExternalApp,

        /** Swallow it: a non-web link the page fired without the user. */
        Ignore,
    }

    /** Schemes a `WebView` resolves on its own. */
    private val WEB_SCHEMES = setOf("http", "https", "file", "data", "blob", "about", "javascript")

    fun decide(scheme: String?, isUserGesture: Boolean): Decision = when {
        // No scheme at all (relative or malformed): nothing to hand off, let the WebView judge.
        scheme.isNullOrBlank() || scheme.lowercase() in WEB_SCHEMES -> Decision.LetWebViewLoad
        isUserGesture -> Decision.LaunchExternalApp
        else -> Decision.Ignore
    }
}
