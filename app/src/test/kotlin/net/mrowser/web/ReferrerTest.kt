package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferrerTest {

    @Test fun `a same-origin URL gets the page without its fragment`() = assertEquals(
        "https://site.example/watch?v=1",
        Referrer.of("https://site.example/watch?v=1#t=30", "https://site.example/a.zip"),
    )

    @Test fun `the page's user info never leaves`() {
        assertEquals(
            "https://site.example/watch",
            Referrer.of("https://user:pw@site.example/watch#x", "https://site.example/a.zip"),
        )
        assertEquals(
            "https://site.example/",
            Referrer.of("https://user:pw@site.example/watch", "https://cdn.example/a.zip"),
        )
    }

    @Test fun `default ports and case do not make a URL cross-origin`() = assertEquals(
        "https://site.example/watch",
        Referrer.of("https://Site.Example:443/watch", "https://site.example/a.zip"),
    )

    @Test fun `a cross-origin URL gets the page's origin only`() {
        assertEquals(
            "https://site.example/",
            Referrer.of("https://site.example/watch?v=1#t", "https://cdn.example/a.zip"),
        )
        assertEquals(
            "http://site.example:8080/",
            Referrer.of("http://site.example:8080/watch", "http://site.example/a.zip"),
        )
        assertEquals(
            "http://site.example/",
            Referrer.of("http://site.example/watch", "https://site.example/a.zip"),
        )
    }

    @Test fun `an https page sends no referer to an http url`() {
        assertNull(Referrer.of("https://site.example/watch", "http://cdn.example/a.zip"))
        assertNull(Referrer.of("https://site.example/watch", "http://site.example/a.zip"))
    }

    @Test fun `no page or a page that cannot be parsed sends no referer`() {
        assertNull(Referrer.of(null, "https://site.example/a.zip"))
        assertNull(Referrer.of("https://exa mple.com/x", "https://site.example/a.zip"))
        assertNull(Referrer.of("ftp://site.example/x", "https://site.example/a.zip"))
    }
}
