package net.mrowser.web

import net.mrowser.web.ErrorNavigation.Back
import net.mrowser.web.ErrorNavigation.Facts
import net.mrowser.web.ErrorNavigation.Retry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorNavigationTest {
    private val pageA = "https://a.example/watch"
    private val failed = "https://bad.example/"

    @Test fun `refused certificate from page A keeps A and offers Go back`() {
        // cancelled before commit: A is still the current entry
        val f = Facts(neverCommitted = true, freshTab = false, currentUrl = pageA, failedUrl = failed, canGoBack = false)
        assertEquals(Back.UNCOVER, ErrorNavigation.back(f))
        assertTrue(ErrorNavigation.showGoBack(f))
        assertEquals(Retry.LOAD_FAILED_URL, ErrorNavigation.retry(f))
    }

    @Test fun `refused certificate from page A with history still uncovers A, never goBack past it`() {
        val f = Facts(neverCommitted = true, freshTab = false, currentUrl = pageA, failedUrl = failed, canGoBack = true)
        assertEquals(Back.UNCOVER, ErrorNavigation.back(f))
    }

    @Test fun `refused certificate from home has no Go back and BACK goes home`() {
        val fresh = Facts(neverCommitted = true, freshTab = true, currentUrl = "about:blank", failedUrl = failed, canGoBack = true)
        assertEquals(Back.HOME, ErrorNavigation.back(fresh))
        assertFalse(ErrorNavigation.showGoBack(fresh))
        assertEquals(Retry.LOAD_FAILED_URL, ErrorNavigation.retry(fresh))
    }

    @Test fun `refused certificate on a cold start (no page at all) goes home`() {
        val f = Facts(neverCommitted = true, freshTab = true, currentUrl = null, failedUrl = failed, canGoBack = false)
        assertEquals(Back.HOME, ErrorNavigation.back(f))
    }

    @Test fun `refused certificate on a fresh tab stays home after the abort's finish`() {
        // On the TV: cold start, type a self-signed URL into home. The cancelled load's late
        // onPageFinished clears clearHistoryOnLoad and webView.url then reports the failed URL;
        // the latched fresh-tab fact must still say home, never uncover a blank page.
        val f = Facts(neverCommitted = true, freshTab = true, currentUrl = failed, failedUrl = failed, canGoBack = false)
        assertEquals(Back.HOME, ErrorNavigation.back(f))
        assertFalse(ErrorNavigation.showGoBack(f))
        assertEquals(Retry.LOAD_FAILED_URL, ErrorNavigation.retry(f))
        // whatever the history says
        assertEquals(Back.HOME, ErrorNavigation.back(f.copy(canGoBack = true, currentUrl = "about:blank")))
    }

    @Test fun `a fresh tab is opened from home or has no web page behind it`() {
        assertTrue(ErrorNavigation.isFreshTab(clearHistoryOnLoad = true, currentUrl = pageA))
        assertTrue(ErrorNavigation.isFreshTab(clearHistoryOnLoad = false, currentUrl = null))
        assertTrue(ErrorNavigation.isFreshTab(clearHistoryOnLoad = false, currentUrl = "about:blank"))
        assertFalse(ErrorNavigation.isFreshTab(clearHistoryOnLoad = false, currentUrl = pageA))
    }

    @Test fun `committed TLS or other error from page A goes back and reloads`() {
        // the error page committed as an entry with the failed URL
        val f = Facts(neverCommitted = false, freshTab = false, currentUrl = failed, failedUrl = failed, canGoBack = true)
        assertEquals(Back.GO_BACK, ErrorNavigation.back(f))
        assertTrue(ErrorNavigation.showGoBack(f))
        assertEquals(Retry.RELOAD, ErrorNavigation.retry(f))
    }

    @Test fun `committed error is never uncovered, even with a web url current`() {
        // the old kind-based rule uncovered ERR_SSL_* and left Chromium's grey page showing
        val f = Facts(neverCommitted = false, freshTab = false, currentUrl = failed, failedUrl = failed, canGoBack = false)
        assertEquals(Back.HOME, ErrorNavigation.back(f))
        assertFalse(ErrorNavigation.showGoBack(f))
    }

    @Test fun `committed error from a fresh tab has no Go back`() {
        // latched at the error: before the finish
        val before = Facts(neverCommitted = false, freshTab = true, currentUrl = pageA, failedUrl = failed, canGoBack = true)
        assertFalse(ErrorNavigation.showGoBack(before))
        assertEquals(Back.HOME, ErrorNavigation.back(before))
        // and after it, when clearHistory() left the error page as the lone entry
        val after = Facts(neverCommitted = false, freshTab = true, currentUrl = failed, failedUrl = failed, canGoBack = false)
        assertFalse(ErrorNavigation.showGoBack(after))
        assertEquals(Back.HOME, ErrorNavigation.back(after))
    }

    @Test fun `a committed error not yet on screen loads the failed url`() {
        val f = Facts(neverCommitted = false, freshTab = false, currentUrl = pageA, failedUrl = failed, canGoBack = true)
        assertEquals(Retry.LOAD_FAILED_URL, ErrorNavigation.retry(f))
    }

    @Test fun `a refused certificate for the page on screen still loads rather than reloads`() {
        val f = Facts(neverCommitted = true, freshTab = false, currentUrl = failed, failedUrl = failed, canGoBack = false)
        assertEquals(Retry.LOAD_FAILED_URL, ErrorNavigation.retry(f))
    }
}
