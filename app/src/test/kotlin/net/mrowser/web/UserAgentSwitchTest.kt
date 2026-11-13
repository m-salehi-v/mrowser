package net.mrowser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentSwitchTest {
    @Test fun `no switch when the user agent already fits`() = assertFalse(UserAgentSwitch().decide("a.example", true, true))

    @Test fun `switches once per site`() {
        val g = UserAgentSwitch()
        assertTrue(g.decide("a.example", uaDesktop = false, wantsDesktop = true))
        assertFalse(g.decide("a.example", uaDesktop = false, wantsDesktop = true))
    }

    @Test fun `a redirect ping-pong between two sites ends`() {
        val g = UserAgentSwitch()
        assertTrue(g.decide("a.example", uaDesktop = false, wantsDesktop = true))
        assertTrue(g.decide("b.example", uaDesktop = true, wantsDesktop = false))
        assertFalse(g.decide("a.example", uaDesktop = false, wantsDesktop = true))
    }

    @Test fun `a user navigation allows switching again`() {
        val g = UserAgentSwitch(); g.decide("a.example", false, true); g.onUserNavigation()
        assertTrue(g.decide("a.example", false, true))
    }

    @Test fun `no site, no switch`() = assertFalse(UserAgentSwitch().decide(null, false, true))
}
