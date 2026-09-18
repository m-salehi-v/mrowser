package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlHostTest {

    @Test fun `extracts a plain host`() {
        assertEquals("example.com", UrlHost.of("https://example.com/path?q=1#f"))
    }

    @Test fun `lowercases and strips port userinfo and trailing dot`() {
        assertEquals("cdn.example.com", UrlHost.of("HTTP://user:pw@CDN.Example.com.:8080/a"))
    }

    @Test fun `keeps an ipv6 literal without brackets`() {
        assertEquals("::1", UrlHost.of("http://[::1]:8080/x"))
    }

    @Test fun `host only url without a path`() {
        assertEquals("example.com", UrlHost.of("https://example.com"))
    }

    @Test fun `query directly after the host`() {
        assertEquals("example.com", UrlHost.of("https://example.com?x=1"))
    }

    @Test fun `no scheme separator is null`() {
        assertNull(UrlHost.of("example.com/path"))
        assertNull(UrlHost.of(""))
    }

    @Test fun `empty host is null`() {
        assertNull(UrlHost.of("about:blank"))
        assertNull(UrlHost.of("https:///path"))
    }
}
