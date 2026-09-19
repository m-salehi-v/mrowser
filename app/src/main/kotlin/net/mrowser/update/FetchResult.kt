package net.mrowser.update

/**
 * The outcome of one conditional GET against the releases API. Kept free of Android imports so
 * [UpdateController] — which branches on it — stays unit-testable.
 */
sealed class FetchResult {
    /** 200: a body to parse, and the ETag to send next time (null when the server sent none). */
    data class Body(val text: String, val etag: String?) : FetchResult()

    /** 304: the cached release is still current, and this cost no rate-limit quota. */
    object NotModified : FetchResult()

    /** Anything else: no network, a timeout, a rate limit, an unexpected status. */
    object Failed : FetchResult()
}
