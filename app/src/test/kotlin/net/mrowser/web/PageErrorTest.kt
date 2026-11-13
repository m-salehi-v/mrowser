package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageErrorTest {
    @Test fun `description wins over the code`() {
        assertEquals(PageErrorKind.NO_INTERNET, PageError.classify(-2, "net::ERR_INTERNET_DISCONNECTED"))
        assertEquals(PageErrorKind.NOT_FOUND, PageError.classify(-2, "net::ERR_NAME_NOT_RESOLVED"))
        assertEquals(PageErrorKind.TIMED_OUT, PageError.classify(-1, "net::ERR_CONNECTION_TIMED_OUT"))
        assertEquals(PageErrorKind.CONNECTION, PageError.classify(-6, "net::ERR_CONNECTION_REFUSED"))
        assertEquals(PageErrorKind.CONNECTION, PageError.classify(-1, "net::ERR_CONNECTION_RESET"))
        assertEquals(PageErrorKind.NOT_SECURE, PageError.classify(-11, "net::ERR_CERT_DATE_INVALID"))
    }

    @Test fun `the code decides when the description is unknown`() {
        assertEquals(PageErrorKind.NOT_FOUND, PageError.classify(-2, null))
        assertEquals(PageErrorKind.TIMED_OUT, PageError.classify(-8, ""))
        assertEquals(PageErrorKind.CONNECTION, PageError.classify(-6, "weird"))
        assertEquals(PageErrorKind.NOT_SECURE, PageError.classify(-11, null))
        assertEquals(PageErrorKind.OTHER, PageError.classify(-7, "net::ERR_SOMETHING_NEW"))
    }

    @Test fun `cancelled navigations are not errors`() {
        assertNull(PageError.classify(-1, "net::ERR_ABORTED"))
        assertNull(PageError.classify(-10, "net::ERR_UNKNOWN_URL_SCHEME"))
        assertNull(PageError.classify(-1, "net::ERR_BLOCKED_BY_CLIENT"))
        assertNull(PageError.classify(-10, "net::ERR_FOO"))
    }

    @Test fun `a cancelled description wins over a code that would show an error`() {
        assertNull(PageError.classify(-2, "net::ERR_ABORTED"))
        assertNull(PageError.classify(-6, "net::ERR_BLOCKED_BY_CLIENT"))
    }

    @Test fun `the wider net error families map to their kind`() {
        assertEquals(PageErrorKind.NOT_FOUND, PageError.classify(-1, "net::ERR_NAME_RESOLUTION_FAILED"))
        assertEquals(PageErrorKind.NO_INTERNET, PageError.classify(-1, "net::ERR_NETWORK_CHANGED"))
        assertEquals(PageErrorKind.TIMED_OUT, PageError.classify(-1, "net::ERR_TIMED_OUT"))
        assertEquals(PageErrorKind.NOT_SECURE, PageError.classify(-1, "net::ERR_SSL_PROTOCOL_ERROR"))
        assertEquals(PageErrorKind.NOT_SECURE, PageError.classify(-1, "net::ERR_CERT_AUTHORITY_INVALID"))
        assertEquals(PageErrorKind.CONNECTION, PageError.classify(-1, "net::ERR_CONNECTION_CLOSED"))
        assertEquals(PageErrorKind.CONNECTION, PageError.classify(-1, "net::ERR_CONNECTION_FAILED"))
    }

    @Test fun `an empty-bodied http error status is a server error whatever the code`() {
        assertEquals(PageErrorKind.SERVER, PageError.classify(-1, "net::ERR_HTTP_RESPONSE_CODE_FAILURE"))
        assertEquals(PageErrorKind.SERVER, PageError.classify(-7, "net::ERR_HTTP_RESPONSE_CODE_FAILURE"))
    }

    @Test fun `code label`() = assertEquals("ERR_NAME_NOT_RESOLVED", PageError.codeLabel("net::ERR_NAME_NOT_RESOLVED"))

    @Test fun `code label of a missing description is empty and a bare one is kept`() {
        assertEquals("", PageError.codeLabel(null))
        assertEquals("weird", PageError.codeLabel("weird"))
    }

    @Test fun `empty page result`() {
        assertTrue(PageError.isEmptyPage("true"))
        assertFalse(PageError.isEmptyPage("false"))
        assertFalse(PageError.isEmptyPage(null))
    }

    @Test fun `an empty page needs two empty answers in a row`() {
        assertEquals(PageError.EmptyVerdict.CHECK_AGAIN, PageError.emptyVerdict(1, "true"))
        assertEquals(PageError.EmptyVerdict.EMPTY, PageError.emptyVerdict(2, "true"))
    }

    @Test fun `a page that has content at either check is left alone`() {
        assertEquals(PageError.EmptyVerdict.NOT_EMPTY, PageError.emptyVerdict(1, "false"))
        assertEquals(PageError.EmptyVerdict.NOT_EMPTY, PageError.emptyVerdict(2, "false"))
        assertEquals(PageError.EmptyVerdict.NOT_EMPTY, PageError.emptyVerdict(2, null))
    }

    @Test fun `the empty-page script recognises Chromium's own error page`() {
        // an empty-bodied 503 clicked from a page commits "Webpage not available", which has text
        assertTrue(PageError.EMPTY_PAGE_SCRIPT.contains("getElementById('main-frame-error')"))
        assertTrue(PageError.EMPTY_PAGE_SCRIPT.contains("neterror(\\s|$)/.test(b.className"))
        assertTrue(PageError.EMPTY_PAGE_SCRIPT.contains("return '${PageError.NET_ERROR_PAGE}'"))
        // and the check for it comes before the text check, which would call that page full
        assertTrue(
            PageError.EMPTY_PAGE_SCRIPT.indexOf("main-frame-error") < PageError.EMPTY_PAGE_SCRIPT.indexOf("innerText")
        )
    }

    @Test fun `Chromium's error page is empty at the first check`() {
        assertTrue(PageError.isEmptyPage("\"neterror\""))
        assertEquals(PageError.EmptyVerdict.EMPTY, PageError.emptyVerdict(1, "\"neterror\""))
        assertEquals(PageError.EmptyVerdict.NOT_EMPTY, PageError.emptyVerdict(1, "\"something else\""))
    }

    @Test fun `an empty-bodied error reported through onReceivedError is still a server error`() =
        assertEquals(PageErrorKind.SERVER, PageError.classify(-1, "net::ERR_HTTP_RESPONSE_CODE_FAILURE"))
}
