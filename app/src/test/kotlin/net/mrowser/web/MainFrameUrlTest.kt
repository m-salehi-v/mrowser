package net.mrowser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainFrameUrlTest {
    @Test fun `a typed url matches what Chromium reports back`() {
        assertTrue(MainFrameUrl.same("https://expired.badssl.com", "https://expired.badssl.com/"))
        assertTrue(MainFrameUrl.same("HTTPS://Expired.BadSSL.com", "https://expired.badssl.com/"))
        assertTrue(MainFrameUrl.same("https://a.example?q=1", "https://a.example/?q=1"))
    }

    @Test fun `default ports and fragments are ignored`() {
        assertTrue(MainFrameUrl.same("https://a.example:443/x", "https://a.example/x"))
        assertTrue(MainFrameUrl.same("http://a.example:80/x", "http://a.example/x"))
        assertTrue(MainFrameUrl.same("https://a.example/x#top", "https://a.example/x"))
    }

    @Test fun `different documents do not match`() {
        assertFalse(MainFrameUrl.same("https://a.example/x", "https://a.example/y"))
        assertFalse(MainFrameUrl.same("https://a.example/x?q=1", "https://a.example/x?q=2"))
        assertFalse(MainFrameUrl.same("http://a.example/", "https://a.example/"))
        assertFalse(MainFrameUrl.same("https://a.example:8443/", "https://a.example/"))
        assertFalse(MainFrameUrl.same("https://a.example/Path", "https://a.example/path"))
    }

    @Test fun `null never matches`() {
        assertFalse(MainFrameUrl.same(null, null))
        assertFalse(MainFrameUrl.same("https://a.example/", null))
    }
}
