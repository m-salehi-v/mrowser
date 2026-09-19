package net.mrowser.update

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the newest stable release. Modelled on [net.mrowser.player.SubtitleFetcher]:
 * one blocking call on a caller-supplied thread, every failure logged and swallowed.
 *
 * `/releases/latest` is GitHub's definition of the newest non-draft, non-prerelease release, so
 * prereleases published from this repository never reach users.
 */
object ReleaseFetcher {

    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/m-salehi-v/mrowser/releases/latest"

    private const val TAG = "ReleaseFetcher"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_BODY_BYTES = 256 * 1024

    fun fetch(installedVersion: String, etag: String?): FetchResult = try {
        val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            // Not optional: GitHub answers 403 to an API request that sends no User-Agent.
            setRequestProperty("User-Agent", "mrowser/$installedVersion")
            setRequestProperty("Accept", "application/vnd.github+json")
            // A 304 costs no rate-limit quota, which is what keeps a whole NAT'd neighbourhood
            // of users under the 60-per-hour unauthenticated limit.
            if (etag != null) setRequestProperty("If-None-Match", etag)
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> FetchResult.NotModified
                HttpURLConnection.HTTP_OK ->
                    FetchResult.Body(readCapped(conn), conn.getHeaderField("ETag"))
                else -> {
                    Log.w(TAG, "releases/latest returned $code")
                    FetchResult.Failed
                }
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "fetch failed", e)
        FetchResult.Failed
    }

    /** Reads at most [MAX_BODY_BYTES], so an unexpected or hostile response cannot balloon memory. */
    private fun readCapped(conn: HttpURLConnection): String {
        val out = ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8 * 1024)
            while (out.size() < MAX_BODY_BYTES) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, minOf(n, MAX_BODY_BYTES - out.size()))
            }
        }
        return out.toString("UTF-8")
    }
}
