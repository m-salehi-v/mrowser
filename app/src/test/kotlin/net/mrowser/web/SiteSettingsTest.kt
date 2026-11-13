package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSettingsTest {
    @Test fun `a site is the registrable domain of a web page`() {
        assertEquals("example.co.uk", SiteSettings.site("https://m.example.co.uk/x"))
        assertNull(SiteSettings.site("about:blank")); assertNull(SiteSettings.site("data:text/html,hi")); assertNull(SiteSettings.site(null))
    }

    @Test fun `zoom defaults to 100 and 100 removes the entry`() {
        assertEquals(100, SiteSettings.zoomFor(emptyMap(), "a.example")); assertEquals(100, SiteSettings.zoomFor(mapOf("a.example" to 125), null))
        assertEquals(mapOf("a.example" to 125), SiteSettings.withZoom(emptyMap(), "a.example", 125))
        assertEquals(emptyMap<String, Int>(), SiteSettings.withZoom(mapOf("a.example" to 125), "a.example", 100))
    }

    @Test fun `desktop follows the site's override, else the default`() {
        assertTrue(SiteSettings.wantsDesktop("a.example", byDefault = true, overrides = emptyMap()))
        assertFalse(SiteSettings.wantsDesktop("a.example", byDefault = true, overrides = mapOf("a.example" to false)))
        assertTrue(SiteSettings.wantsDesktop("a.example", byDefault = false, overrides = mapOf("a.example" to true)))
        assertTrue(SiteSettings.wantsDesktop(null, byDefault = true, overrides = mapOf("a.example" to false)))
    }

    @Test fun `an override equal to the default is removed`() {
        assertEquals(mapOf("a.example" to true), SiteSettings.withDesktop(emptyMap(), "a.example", desktop = true, byDefault = false))
        assertEquals(emptyMap<String, Boolean>(), SiteSettings.withDesktop(mapOf("a.example" to true), "a.example", desktop = false, byDefault = false))
        assertEquals(mapOf("a.example" to false), SiteSettings.withDesktop(emptyMap(), "a.example", desktop = false, byDefault = true))
    }

    @Test fun `a new default drops the overrides that now match it`() = assertEquals(mapOf("b.example" to false),
        SiteSettings.pruneDesktop(mapOf("a.example" to true, "b.example" to false), byDefault = true))
}
