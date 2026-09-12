package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingUrlTest {

    private val view = "android.intent.action.VIEW"

    @Test fun `a VIEW intent for an https link opens it`() {
        assertEquals("https://example.com/a", IncomingUrl.fromViewIntent(view, "https://example.com/a"))
    }

    @Test fun `plain http is opened too`() {
        assertEquals("http://example.com", IncomingUrl.fromViewIntent(view, "http://example.com"))
    }

    @Test fun `scheme case is ignored`() {
        assertEquals("HTTPS://example.com", IncomingUrl.fromViewIntent(view, "HTTPS://example.com"))
    }

    @Test fun `the launcher intent opens no link`() {
        assertNull(IncomingUrl.fromViewIntent("android.intent.action.MAIN", null))
    }

    @Test fun `a VIEW intent with no data opens no link`() {
        assertNull(IncomingUrl.fromViewIntent(view, null))
        assertNull(IncomingUrl.fromViewIntent(view, "   "))
    }

    @Test fun `a file url from another app is refused`() {
        assertNull(IncomingUrl.fromViewIntent(view, "file:///data/data/net.mrowser/files/favorites.json"))
    }

    @Test fun `a javascript url from another app is refused`() {
        assertNull(IncomingUrl.fromViewIntent(view, "javascript:alert(1)"))
    }

    @Test fun `a content url from another app is refused`() {
        assertNull(IncomingUrl.fromViewIntent(view, "content://media/external/file/1"))
    }

    @Test fun `a scheme-only url is refused`() {
        assertNull(IncomingUrl.fromViewIntent(view, "https://"))
    }

    @Test fun `a bare host without a scheme is refused`() {
        assertNull(IncomingUrl.fromViewIntent(view, "example.com"))
    }

    @Test fun `a web url is accepted on its own`() {
        assertEquals("https://example.com/x", IncomingUrl.webUrlOrNull("https://example.com/x"))
    }

    @Test fun `a non-web fallback url is refused`() {
        assertNull(IncomingUrl.webUrlOrNull("intent://evil#Intent;end"))
        assertNull(IncomingUrl.webUrlOrNull(null))
    }
}
