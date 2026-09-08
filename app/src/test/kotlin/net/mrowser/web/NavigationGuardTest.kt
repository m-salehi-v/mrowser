package net.mrowser.web

import net.mrowser.web.NavigationGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGuardTest {

    @Test fun `the page navigating itself is allowed without a gesture`() {
        assertEquals(
            Decision.Allow,
            NavigationGuard.decide("example.com", "example.com", hasGesture = false, isRedirect = false)
        )
    }

    @Test fun `a subdomain of the current site is the same site`() {
        assertEquals(
            Decision.Allow,
            NavigationGuard.decide("example.com", "cdn.example.com", hasGesture = false, isRedirect = false)
        )
        assertEquals(
            Decision.Allow,
            NavigationGuard.decide("www.example.com", "example.com", hasGesture = false, isRedirect = false)
        )
    }

    @Test fun `clicking a link to another site is allowed`() {
        assertEquals(
            Decision.Allow,
            NavigationGuard.decide("example.com", "other.com", hasGesture = true, isRedirect = false)
        )
    }

    @Test fun `a cross-site jump with no gesture is a pop-up and is blocked`() {
        assertEquals(
            Decision.Block,
            NavigationGuard.decide("example.com", "ads.other.com", hasGesture = false, isRedirect = false)
        )
    }

    @Test fun `a server redirect continues a navigation that was already allowed`() {
        assertEquals(
            Decision.Allow,
            NavigationGuard.decide("example.com", "login.other.com", hasGesture = false, isRedirect = true)
        )
    }

    @Test fun `a gestureless jump to a schemeless or blank target is blocked`() {
        assertEquals(
            Decision.Block,
            NavigationGuard.decide("example.com", "", hasGesture = false, isRedirect = false)
        )
    }

    @Test fun `a lookalike host is not the same site`() {
        assertEquals(
            Decision.Block,
            NavigationGuard.decide("example.com", "evil-example.com", hasGesture = false, isRedirect = false)
        )
    }

    @Test fun `host comparison ignores case`() {
        assertEquals(
            Decision.Allow,
            NavigationGuard.decide("Example.com", "CDN.EXAMPLE.com", hasGesture = false, isRedirect = false)
        )
    }
}
