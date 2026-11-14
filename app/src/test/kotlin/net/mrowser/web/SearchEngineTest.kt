package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEngineTest {

    @Test fun `duckduckgo is the default`() = assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.DEFAULT)

    @Test fun `ids are stable`() = assertEquals(
        listOf("duckduckgo", "google", "bing", "brave", "startpage"), SearchEngine.values().map { it.id }
    )

    @Test fun `unknown or missing id falls back to the default`() {
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId(null))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId("yahoo"))
        assertEquals(SearchEngine.BRAVE, SearchEngine.fromId("brave"))
    }

    @Test fun `builds each engine's results url`() {
        assertEquals("https://duckduckgo.com/?q=solaris+1972", SearchEngine.DUCKDUCKGO.searchUrl("solaris 1972"))
        assertEquals("https://www.google.com/search?q=solaris+1972", SearchEngine.GOOGLE.searchUrl("solaris 1972"))
        assertEquals("https://www.bing.com/search?q=a%26b", SearchEngine.BING.searchUrl("a&b"))
        assertEquals("https://search.brave.com/search?q=x", SearchEngine.BRAVE.searchUrl("x"))
        assertEquals("https://www.startpage.com/do/search?query=x", SearchEngine.STARTPAGE.searchUrl("x"))
    }

    @Test fun `encodes non-latin queries as utf-8`() = assertEquals(
        "https://duckduckgo.com/?q=%D8%B3%D9%88%D9%84%D8%A7%D8%B1%DB%8C%D8%B3",
        SearchEngine.DUCKDUCKGO.searchUrl("سولاریس")
    )
}
