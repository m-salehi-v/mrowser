package net.mrowser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestTest {

    @Test fun `only web links can be downloaded`() {
        assertTrue(DownloadRequest.isSupported("https://example.com/a.apk"))
        assertTrue(DownloadRequest.isSupported("HTTP://example.com/a"))
        assertFalse(DownloadRequest.isSupported("blob:https://example.com/0b1c"))
        assertFalse(DownloadRequest.isSupported("data:application/pdf;base64,AAAA"))
        assertFalse(DownloadRequest.isSupported("ftp://example.com/a"))
    }

    @Test fun `cookie, the listener's user agent and a same-origin page as referer`() = assertEquals(
        mapOf("Cookie" to "a=1", "User-Agent" to "UA/1", "Referer" to "https://site.example/watch?v=1"),
        DownloadRequest.headers("a=1", "UA/1", "https://site.example/watch?v=1", "https://site.example/files/a.zip"),
    )

    @Test fun `blank values and a non-web page are left out`() = assertEquals(
        mapOf("User-Agent" to "UA/1"),
        DownloadRequest.headers("", "UA/1", "about:blank", "https://site.example/a.zip"),
    )

    @Test fun `a same-origin download gets the page without its fragment`() = assertEquals(
        "https://site.example/watch?v=1",
        DownloadRequest.referrer("https://site.example/watch?v=1#t=30", "https://site.example/a.zip"),
    )

    @Test fun `the page's user info never leaves`() {
        assertEquals(
            "https://site.example/watch",
            DownloadRequest.referrer("https://user:pw@site.example/watch#x", "https://site.example/a.zip"),
        )
        assertEquals(
            "https://site.example/",
            DownloadRequest.referrer("https://user:pw@site.example/watch", "https://cdn.example/a.zip"),
        )
    }

    @Test fun `default ports and case do not make a download cross-origin`() = assertEquals(
        "https://site.example/watch",
        DownloadRequest.referrer("https://Site.Example:443/watch", "https://site.example/a.zip"),
    )

    @Test fun `a cross-origin download gets the page's origin only`() {
        assertEquals(
            "https://site.example/",
            DownloadRequest.referrer("https://site.example/watch?v=1#t", "https://cdn.example/a.zip"),
        )
        assertEquals(
            "http://site.example:8080/",
            DownloadRequest.referrer("http://site.example:8080/watch", "http://site.example/a.zip"),
        )
        assertEquals(
            "http://site.example/",
            DownloadRequest.referrer("http://site.example/watch", "https://site.example/a.zip"),
        )
    }

    @Test fun `an https page sends no referer to an http download`() {
        assertNull(DownloadRequest.referrer("https://site.example/watch", "http://cdn.example/a.zip"))
        assertNull(DownloadRequest.referrer("https://site.example/watch", "http://site.example/a.zip"))
        assertEquals(
            mapOf("User-Agent" to "UA/1"),
            DownloadRequest.headers(null, "UA/1", "https://site.example/watch", "http://cdn.example/a.zip"),
        )
    }

    @Test fun `no page or a page that cannot be parsed sends no referer`() {
        assertNull(DownloadRequest.referrer(null, "https://site.example/a.zip"))
        assertNull(DownloadRequest.referrer("https://exa mple.com/x", "https://site.example/a.zip"))
        assertNull(DownloadRequest.referrer("ftp://site.example/x", "https://site.example/a.zip"))
    }
}
