package net.mrowser.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.net.URISyntaxException

/**
 * Android glue: hands a non-web URL to whichever app registered for it — the half of
 * [ExternalSchemePolicy] that needs a `Context`.
 *
 * `intent://` URLs are parsed with [Intent.parseUri], which also covers every other custom
 * scheme (`obtainium://`, `market://`, `mailto:`) by turning it into a plain `ACTION_VIEW`.
 * When nothing can open the link, an `intent://` URL's `browser_fallback_url` is loaded in the
 * page instead — that is what sites put it there for.
 */
class ExternalIntentLauncher(
    private val context: Context,
    private val onFallback: (String) -> Unit,
    private val onNoApp: () -> Unit
) {

    fun launch(url: String) {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: URISyntaxException) {
            onNoApp()
            return
        }

        // The page chooses the URL, so it must not also get to choose the target. An `intent://`
        // link can name an explicit component or selector, which would let a page use the browser
        // as the caller to reach components other apps never exported for links. Clearing both
        // and demanding BROWSABLE leaves exactly the surface apps opted into for browser links.
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        // Nor may it pass on read/write access to anything of ours.
        intent.flags = intent.flags and URI_PERMISSION_FLAGS.inv()

        // Vet the fallback before it becomes a navigation: it is page-supplied too, so an
        // `intent:`/`javascript:` one here would walk straight back around the check above.
        val fallback = IncomingUrl.webUrlOrNull(intent.getStringExtra(EXTRA_FALLBACK_URL))
        intent.removeExtra(EXTRA_FALLBACK_URL)

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) onFallback(fallback) else onNoApp()
        } catch (e: SecurityException) {
            onNoApp()
        }
    }

    companion object {
        private const val EXTRA_FALLBACK_URL = "browser_fallback_url"

        private const val URI_PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
