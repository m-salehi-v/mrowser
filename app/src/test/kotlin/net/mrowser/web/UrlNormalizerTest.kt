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

    private val ddg = SearchEngine.DUCKDUCKGO

    @Test fun `resolve keeps addresses`() {
        assertEquals("https://example.com", UrlNormalizer.resolve("example.com", ddg))
        assertEquals("https://localhost:8080", UrlNormalizer.resolve("localhost:8080", ddg))
        assertEquals("https://192.168.1.5/admin", UrlNormalizer.resolve("192.168.1.5/admin", ddg))
        assertEquals("https://[::1]:8080/", UrlNormalizer.resolve("[::1]:8080/", ddg))
        assertEquals("http://example.com/x", UrlNormalizer.resolve("http://example.com/x", ddg))
    }

    @Test fun `resolve keeps an explicit scheme with a dotless host`() {
        assertEquals("http://nas:5000/", UrlNormalizer.resolve("http://nas:5000/", ddg))
        assertEquals("https://intranet/", UrlNormalizer.resolve("https://intranet/", ddg))
        assertEquals("http://router", UrlNormalizer.resolve("http://router", ddg))
    }

    @Test fun `resolve searches a scheme with no host`() {
        assertEquals("https://duckduckgo.com/?q=http%3A%2F%2F", UrlNormalizer.resolve("http://", ddg))
    }

    @Test fun `resolve searches words`() {
        assertEquals("https://duckduckgo.com/?q=tarkovsky", UrlNormalizer.resolve("tarkovsky", ddg))
        assertEquals("https://duckduckgo.com/?q=solaris+1972", UrlNormalizer.resolve("  solaris 1972 ", ddg))
        assertEquals("https://duckduckgo.com/?q=example.com+is+down", UrlNormalizer.resolve("example.com is down", ddg))
        assertEquals(
            "https://duckduckgo.com/?q=%CE%A3%CE%BF%CE%BB%CE%AC%CF%81%CE%B9%CF%82",
            UrlNormalizer.resolve("Σολάρις", ddg)
        )
    }

    @Test fun `resolve returns null only for blank input`() = assertNull(UrlNormalizer.resolve("   ", ddg))
}
