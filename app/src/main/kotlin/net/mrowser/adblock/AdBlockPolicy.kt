package net.mrowser.adblock

import net.mrowser.stream.MediaUrlClassifier
import net.mrowser.stream.MediaUrlClassifier.MediaKind
import net.mrowser.web.RegistrableDomain
import net.mrowser.web.UrlHost

/**
 * Pure: whether a sub-resource request should be answered with a stand-in instead of fetched.
 *
 * Checked in order, first hit wins:
 * 1. blocking off → allow
 * 2. page allowlisted → allow
 * 3. main frame → allow (an empty body here blanks the page; navigations are gated by
 *    [NavigationPolicy] instead)
 * 4. anything the sniffer cares about (manifest, segment, subtitle) → allow, so the list can
 *    never cost the user the stream
 * 5. first-party (same registrable domain as the page) → allow
 * 6. host on the list → block
 */
object AdBlockPolicy {

    enum class Decision { ALLOW, BLOCK }

    private val PROTECTED_MEDIA = setOf(
        MediaKind.MANIFEST_HLS, MediaKind.MANIFEST_DASH, MediaKind.SEGMENT, MediaKind.SUBTITLE
    )

    fun decide(
        requestUrl: String,
        pageHost: String?,
        isMainFrame: Boolean,
        enabled: Boolean,
        allowlisted: Boolean,
        list: BlockList
    ): Decision {
        if (!enabled || allowlisted || isMainFrame) return Decision.ALLOW
        if (MediaUrlClassifier.classify(requestUrl) in PROTECTED_MEDIA) return Decision.ALLOW
        val host = UrlHost.of(requestUrl) ?: return Decision.ALLOW
        if (pageHost != null && RegistrableDomain.of(host) == RegistrableDomain.of(pageHost)) {
            return Decision.ALLOW
        }
        return if (list.contains(host)) Decision.BLOCK else Decision.ALLOW
    }
}
