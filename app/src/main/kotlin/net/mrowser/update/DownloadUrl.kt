package net.mrowser.update

import net.mrowser.web.UrlHost

/**
 * Pure: is this a URL we are willing to hand `DownloadManager`?
 *
 * The APK URL arrives in a network response, and `DownloadManager` fetches whatever it is given,
 * so the destination is vetted before it is enqueued. Only https, and only GitHub's own hosts —
 * `github.com` for the release asset URL and `*.githubusercontent.com` for the object storage it
 * redirects to. Redirects past that point belong to the platform and stay over https.
 */
object DownloadUrl {

    private const val HTTPS = "https://"
    private const val GITHUB = "github.com"
    private const val ASSET_SUFFIX = ".githubusercontent.com"

    fun isAllowed(url: String): Boolean {
        if (!url.startsWith(HTTPS, ignoreCase = true)) return false
        val host = UrlHost.of(url) ?: return false
        return host == GITHUB || host.endsWith(ASSET_SUFFIX)
    }
}
