package net.mrowser.web

/** Turns raw URL-bar input into a loadable URL, or null if it is not one. */
object UrlNormalizer {

    private val SCHEME = Regex("(?i)^[a-z][a-z0-9+.-]*://")

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null

        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val host = hostOf(withScheme) ?: return null
        val isValidHost = host == "localhost" || host.contains('.')
        return if (isValidHost) withScheme else null
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        val host = authority.substringAfter('@', authority).substringBefore(':')
        return host.ifEmpty { null }
    }
}
