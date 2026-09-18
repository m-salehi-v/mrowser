package net.mrowser.adblock

import net.mrowser.adblock.NavigationPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {

    private val list = BlockList.fromLines(sequenceOf("ads.example", "4k.to"))

    @Test fun `a navigation to a listed host is blocked`() {
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://go.ads.example/click?id=1", null, false, true, false, list))
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("http://ads.example", null, false, true, false, list))
    }

    @Test fun `a navigation to an unlisted host loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://site.example/page", null, false, true, false, list))
    }

    @Test fun `disabled or allowlisted always loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://ads.example/", null, false, false, false, list))
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://ads.example/", null, false, true, true, list))
    }

    @Test fun `non-web schemes are not this policy's business`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("intent://ads.example/#Intent;end", null, false, true, false, list))
        assertEquals(Decision.LOAD, NavigationPolicy.decide("about:blank", null, false, true, false, list))
    }

    @Test fun `a listed third-party destination is still refused, whoever sent you`() {
        // Being sent to a listed host is the thing being refused, whoever sent you.
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://ads.example/", null, false, true, false, list))
    }

    @Test fun `a same-registrable-domain navigation on a listed site loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://4k.to/movie/1", "4k.to", false, true, false, list))
    }

    @Test fun `a same-registrable-domain navigation across subdomains loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://cdn.4k.to/x", "www.4k.to", false, true, false, list))
    }

    @Test fun `a listed third-party destination is still blocked from an unlisted page`() {
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://ads.example/", "site.example", false, true, false, list))
    }

    @Test fun `a listed third-party destination is still blocked from a different listed page`() {
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://ads.example/", "4k.to", false, true, false, list))
    }

    @Test fun `userInitiated loads a listed host`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://ads.example/", null, true, true, false, list))
    }

    @Test fun `not userInitiated blocks it, all else equal`() {
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://ads.example/", null, false, true, false, list))
    }

    @Test fun `a null pageHost still applies the list`() {
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://ads.example/", null, false, true, false, list))
    }
}
