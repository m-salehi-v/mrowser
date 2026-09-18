package net.mrowser.web

/**
 * Pure: the host of an absolute URL, with no `android.net.Uri`.
 *
 * Lowercased, without scheme, userinfo, port, path, query, fragment, or a trailing dot. An
 * IPv6 literal comes back without its brackets. `null` when there is no `://` or the host is
 * empty (`about:blank`, relative URLs).
 */
object UrlHost {

    fun of(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']').removePrefix("[")
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase().trimEnd('.').ifEmpty { null }
    }
}
