package net.mrowser.adblock

import net.mrowser.adblock.NavigationPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {

    private val list = BlockList.fromLines(sequenceOf("ads.example"))

    @Test fun `a navigation to a listed host is blocked`() {
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://go.ads.example/click?id=1", true, false, list))
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("http://ads.example", true, false, list))
    }

    @Test fun `a navigation to an unlisted host loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://site.example/page", true, false, list))
    }

    @Test fun `disabled or allowlisted always loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://ads.example/", false, false, list))
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://ads.example/", true, true, list))
    }

    @Test fun `non-web schemes are not this policy's business`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("intent://ads.example/#Intent;end", true, false, list))
        assertEquals(Decision.LOAD, NavigationPolicy.decide("about:blank", true, false, list))
    }

    @Test fun `there is no first-party exemption for navigations`() {
        // Being sent to a listed host is the thing being refused, whoever sent you.
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://ads.example/", true, false, list))
    }
}
