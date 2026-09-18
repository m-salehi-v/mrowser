package net.mrowser.adblock

import net.mrowser.adblock.AdBlockPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class AdBlockPolicyTest {

    private val list = BlockList.fromLines(sequenceOf("ads.example", "video.example"))

    private fun decide(
        url: String,
        pageHost: String? = "www.site.example",
        isMainFrame: Boolean = false,
        enabled: Boolean = true,
        allowlisted: Boolean = false
    ) = AdBlockPolicy.decide(url, pageHost, isMainFrame, enabled, allowlisted, list)

    @Test fun `a listed third-party request is blocked`() {
        assertEquals(Decision.BLOCK, decide("https://cdn.ads.example/tag.js"))
    }

    @Test fun `an unlisted request is allowed`() {
        assertEquals(Decision.ALLOW, decide("https://cdn.other.example/app.js"))
    }

    @Test fun `disabled allows everything`() {
        assertEquals(Decision.ALLOW, decide("https://ads.example/tag.js", enabled = false))
    }

    @Test fun `an allowlisted page allows everything`() {
        assertEquals(Decision.ALLOW, decide("https://ads.example/tag.js", allowlisted = true))
    }

    @Test fun `the main frame is never blocked here`() {
        assertEquals(Decision.ALLOW, decide("https://ads.example/landing", isMainFrame = true))
    }

    @Test fun `media the sniffer cares about is never blocked`() {
        assertEquals(Decision.ALLOW, decide("https://video.example/master.m3u8?token=1"))
        assertEquals(Decision.ALLOW, decide("https://video.example/stream.mpd"))
        assertEquals(Decision.ALLOW, decide("https://video.example/seg-01.ts"))
        assertEquals(Decision.ALLOW, decide("https://video.example/seg-01.m4s"))
        assertEquals(Decision.ALLOW, decide("https://video.example/en.vtt"))
        assertEquals(Decision.ALLOW, decide("https://video.example/en.srt"))
    }

    @Test fun `a first-party request is never blocked even when listed`() {
        val firstParty = BlockList.fromLines(sequenceOf("site.example"))
        assertEquals(
            Decision.ALLOW,
            AdBlockPolicy.decide("https://static.site.example/ad.js", "www.site.example", false, true, false, firstParty)
        )
    }

    @Test fun `first-party is judged by registrable domain`() {
        val l = BlockList.fromLines(sequenceOf("cdn.site.example"))
        assertEquals(
            Decision.ALLOW,
            AdBlockPolicy.decide("https://cdn.site.example/x.js", "www.site.example", false, true, false, l)
        )
    }

    @Test fun `with no page host the list still applies`() {
        assertEquals(Decision.BLOCK, decide("https://ads.example/tag.js", pageHost = null))
    }

    @Test fun `a url without a host is allowed`() {
        assertEquals(Decision.ALLOW, decide("data:text/plain,hello"))
    }

    @Test fun `an empty list blocks nothing`() {
        assertEquals(
            Decision.ALLOW,
            AdBlockPolicy.decide("https://ads.example/tag.js", "www.site.example", false, true, false, BlockList.EMPTY)
        )
    }
}
