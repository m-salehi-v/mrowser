package net.mrowser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `cookie, the listener's user agent and the page as referer`() = assertEquals(
        mapOf("Cookie" to "a=1", "User-Agent" to "UA/1", "Referer" to "https://site.example/watch"),
        DownloadRequest.headers("a=1", "UA/1", "https://site.example/watch"),
    )

    @Test fun `blank values and a non-web page are left out`() =
        assertEquals(mapOf("User-Agent" to "UA/1"), DownloadRequest.headers("", "UA/1", "about:blank"))
}
