package net.mrowser.web

/**
 * Pure: whether two URLs name the same main-frame document, as far as the WebView's callbacks
 * are concerned. What the app hands `loadUrl` is not always what Chromium reports back: a typed
 * `https://example.com` comes back as `https://example.com/`, a host in capitals comes back
 * lowercased, a default port is dropped. So: scheme and host compared case-insensitively, an empty
 * path is `/`, `:80`/`:443` on http/https are dropped, and the fragment is ignored. Everything
 * else (path, query) must match exactly. Null never matches.
 */
object MainFrameUrl {

    fun same(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return canonical(a) == canonical(b)
    }

    private fun canonical(url: String): String {
        val noFragment = url.substringBefore('#')
        val sep = noFragment.indexOf("://")
        if (sep < 0) return noFragment
        val scheme = noFragment.substring(0, sep).lowercase()
        val rest = noFragment.substring(sep + 3)
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?')).let { if (it < 0) rest.length else it }
        var authority = rest.substring(0, authorityEnd).lowercase()
        if (scheme == "https") authority = authority.removeSuffix(":443")
        if (scheme == "http") authority = authority.removeSuffix(":80")
        val tail = rest.substring(authorityEnd).let { if (it.startsWith("/")) it else "/$it" }
        return "$scheme://$authority$tail"
    }
}
