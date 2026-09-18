package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrableDomainTest {

    @Test fun `drops subdomains down to two labels`() {
        assertEquals("example.com", RegistrableDomain.of("www.example.com"))
        assertEquals("example.com", RegistrableDomain.of("a.b.c.example.com"))
    }

    @Test fun `keeps three labels for a two-label public suffix`() {
        assertEquals("example.co.uk", RegistrableDomain.of("a.b.example.co.uk"))
        assertEquals("example.com.au", RegistrableDomain.of("cdn.example.com.au"))
    }

    @Test fun `two labels or fewer are returned as is`() {
        assertEquals("example.com", RegistrableDomain.of("example.com"))
        assertEquals("localhost", RegistrableDomain.of("localhost"))
    }

    @Test fun `a bare public suffix is returned as is`() {
        assertEquals("co.uk", RegistrableDomain.of("co.uk"))
    }

    @Test fun `ip literals are returned unchanged`() {
        assertEquals("10.0.0.1", RegistrableDomain.of("10.0.0.1"))
        assertEquals("::1", RegistrableDomain.of("::1"))
    }

    @Test fun `lowercases and strips a trailing dot`() {
        assertEquals("example.com", RegistrableDomain.of("WWW.Example.COM."))
    }
}
