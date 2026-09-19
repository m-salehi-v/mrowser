package net.mrowser.update

import java.util.concurrent.Executor

/**
 * The update channel's decision path: throttle, fetch, cache, report.
 *
 * No Android imports — the network call, both thread hops and the clock are constructor
 * parameters, the same shape as [net.mrowser.stream.StreamSniffer], so every branch here is
 * unit-tested.
 */
class UpdateController(
    private val store: UpdateStateRepository,
    private val installedVersion: String,
    private val fetch: (installedVersion: String, etag: String?) -> FetchResult,
    private val io: Executor,
    private val main: (Runnable) -> Unit,
    private val now: () -> Long
) {

    /** Cache only — no network, no blocking. Safe to call while laying out the first frame. */
    fun cachedBanner(): Release? =
        UpdateCheckPolicy.bannerFor(installedVersion, store.get().release)

    /**
     * Fires at most once per [UpdateCheckPolicy.CHECK_INTERVAL_MS]. When the check is throttled
     * [onResult] is not called at all — the caller has already drawn [cachedBanner], and calling
     * back with the same answer would only make the line flicker.
     *
     * A failed check deliberately does not bump the timestamp, so a transient outage costs one
     * retry on the next launch rather than a day of silence.
     */
    fun checkIfDue(onResult: (Release?) -> Unit) {
        val state = store.get()
        if (!UpdateCheckPolicy.shouldCheck(now(), state.lastCheckedAt)) return
        io.execute {
            val updated = when (val result = fetch(installedVersion, state.etag)) {
                FetchResult.Failed -> null
                FetchResult.NotModified -> state.copy(lastCheckedAt = now())
                is FetchResult.Body -> state.copy(
                    lastCheckedAt = now(),
                    etag = result.etag ?: state.etag,
                    // A body we cannot read leaves the cache as it was: the last release we did
                    // understand is better than none.
                    release = ReleaseJson.parse(result.text) ?: state.release
                )
            }
            if (updated != null) store.update(updated)
            val banner = UpdateCheckPolicy.bannerFor(installedVersion, (updated ?: state).release)
            main(Runnable { onResult(banner) })
        }
    }
}
