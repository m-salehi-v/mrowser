package net.mrowser.web

/**
 * Turns raw URL-bar input into something loadable. [normalize] gives an address or null when the
 * input is not one; [resolve] gives an address or else a search URL, and is null only for blank input.
 */
object UrlNormalizer {

    private val SCHEME = Regex("(?i)^[a-z][a-z0-9+.-]*://")

    /**
     * What the URL bar should load for [input]: the address when it is one, otherwise [engine]'s
     * results page for it. Null only for blank input. An explicit scheme with a host is always an
     * address, even a dotless one (`http://nas:5000/`), which [normalize] alone would reject.
     */
    fun resolve(input: String, engine: SearchEngine): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (SCHEME.containsMatchIn(trimmed) && trimmed.none { it.isWhitespace() } && hostOf(trimmed) != null) {
            return trimmed
        }
        return normalize(trimmed) ?: engine.searchUrl(trimmed)
    }

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null

        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val host = hostOf(withScheme) ?: return null
        val isValidHost = host == "localhost" || host.contains('.') || isBracketedIpv6(host)
        return if (isValidHost) withScheme else null
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        val hostPort = authority.substringAfter('@', authority)
        // A bracketed IPv6 literal holds colons itself, so the port colon is the one after the ']'.
        val host = if (hostPort.startsWith("[") && ']' in hostPort) {
            hostPort.substringBefore(']') + "]"
        } else {
            hostPort.substringBefore(':')
        }
        return host.ifEmpty { null }
    }

    private fun isBracketedIpv6(host: String): Boolean =
        host.length > 2 && host.startsWith("[") && host.endsWith("]") && host.contains(':')
}
