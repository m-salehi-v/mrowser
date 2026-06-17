package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {

    @Test fun `prepends https when scheme is missing`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com"))
    }

    @Test fun `keeps an existing http scheme`() {
        assertEquals("http://example.com/x", UrlNormalizer.normalize("http://example.com/x"))
    }

    @Test fun `trims surrounding whitespace`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("  example.com  "))
    }

    @Test fun `keeps full path and query`() {
        assertEquals(
            "https://meghan.andrei-tarkovsky.net/stream/1/2/?h=1080",
            UrlNormalizer.normalize("meghan.andrei-tarkovsky.net/stream/1/2/?h=1080")
        )
    }

    @Test fun `allows localhost without a dot`() {
        assertEquals("https://localhost", UrlNormalizer.normalize("localhost"))
    }

    @Test fun `returns null for blank input`() {
        assertNull(UrlNormalizer.normalize("   "))
    }

    @Test fun `returns null for a bare word that is not a host`() {
        assertNull(UrlNormalizer.normalize("notaurl"))
    }

    @Test fun `returns null when input contains spaces`() {
        assertNull(UrlNormalizer.normalize("foo bar baz"))
    }
}
