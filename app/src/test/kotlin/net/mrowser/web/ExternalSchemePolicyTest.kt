package net.mrowser.web

import net.mrowser.web.ExternalSchemePolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalSchemePolicyTest {

    @Test fun `a web page is loaded by the WebView`() {
        assertEquals(
            Decision.LetWebViewLoad,
            ExternalSchemePolicy.decide("https", isUserGesture = true)
        )
    }

    @Test fun `every scheme the WebView resolves itself stays in the WebView`() {
        listOf("http", "https", "file", "data", "blob", "about", "javascript").forEach { scheme ->
            assertEquals(
                "$scheme should stay in the WebView",
                Decision.LetWebViewLoad,
                ExternalSchemePolicy.decide(scheme, isUserGesture = false)
            )
        }
    }

    @Test fun `scheme case is ignored`() {
        assertEquals(
            Decision.LetWebViewLoad,
            ExternalSchemePolicy.decide("HTTPS", isUserGesture = true)
        )
    }

    @Test fun `a link with no scheme is left to the WebView`() {
        assertEquals(Decision.LetWebViewLoad, ExternalSchemePolicy.decide(null, isUserGesture = true))
        assertEquals(Decision.LetWebViewLoad, ExternalSchemePolicy.decide("  ", isUserGesture = true))
    }

    @Test fun `a custom scheme the user clicked goes to the system`() {
        assertEquals(
            Decision.LaunchExternalApp,
            ExternalSchemePolicy.decide("obtainium", isUserGesture = true)
        )
    }

    @Test fun `an intent scheme the user clicked goes to the system`() {
        assertEquals(
            Decision.LaunchExternalApp,
            ExternalSchemePolicy.decide("intent", isUserGesture = true)
        )
    }

    @Test fun `a custom scheme the page fired by itself is ignored`() {
        assertEquals(
            Decision.Ignore,
            ExternalSchemePolicy.decide("market", isUserGesture = false)
        )
    }
}
