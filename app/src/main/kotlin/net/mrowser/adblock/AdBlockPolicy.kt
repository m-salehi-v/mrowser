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
 *
 * Accepted trade-off: check 4 is extension-based and runs before the host lookup, so a listed
 * host can dodge the blocker entirely by serving its payload at a `.m3u8`/`.ts`/`.vtt`/`.mp4` path.
 * That is the deliberate price of never costing the user their stream, not an oversight.
 */
object AdBlockPolicy {

    enum class Decision { ALLOW, BLOCK }

    private val PROTECTED_MEDIA = setOf(
        MediaKind.MANIFEST_HLS, MediaKind.MANIFEST_DASH, MediaKind.PROGRESSIVE,
        MediaKind.SEGMENT, MediaKind.SUBTITLE
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
