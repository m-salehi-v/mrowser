package net.mrowser.update

/**
 * What the update channel remembers between launches.
 *
 * [release] is cached so the home screen can draw its line on the first frame without waiting
 * for — or, when the check is throttled, without making — a network call. [etag] turns the next
 * check into a conditional GET that costs no rate-limit quota.
 */
data class UpdateState(
    val lastCheckedAt: Long = 0L,
    val etag: String? = null,
    val release: Release? = null
)
