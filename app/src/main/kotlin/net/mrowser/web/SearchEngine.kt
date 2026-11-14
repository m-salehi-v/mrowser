package net.mrowser.web

import java.net.URLEncoder

/** A web search engine the URL bar can send free text to. */
enum class SearchEngine(val id: String, val label: String, private val prefix: String) {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("google", "Google", "https://www.google.com/search?q="),
    BING("bing", "Bing", "https://www.bing.com/search?q="),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q="),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/do/search?query=");

    /** The engine's results page for [query], percent-encoded as UTF-8. */
    fun searchUrl(query: String): String = prefix + URLEncoder.encode(query, "UTF-8")

    companion object {
        val DEFAULT = DUCKDUCKGO

        /** The engine with this [id]; the default for null or an id no longer known. */
        fun fromId(id: String?): SearchEngine = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
