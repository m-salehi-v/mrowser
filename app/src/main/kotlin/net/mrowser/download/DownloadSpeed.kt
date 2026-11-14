package net.mrowser.download

/**
 * Pure: a smoothed bytes-per-second per download, from the polled byte counts. Null until two
 * samples a moment apart exist, and again once a stall has decayed it below 1 KB/s.
 */
class DownloadSpeed(private val alpha: Double = 0.3) {

    private class State(var bytes: Long, var atMs: Long, var speed: Long?) {
        /** A decayed-away speed (below the floor after earlier samples) reads as no speed. */
        var stalled = false
        val reported: Long? get() = if (stalled) null else speed
    }

    private val states = HashMap<Long, State>()

    /** Feeds one poll; returns the smoothed speed so far, or null while unknown. */
    @Synchronized
    fun sample(id: Long, bytes: Long, atMs: Long): Long? {
        if (bytes < 0) return states[id]?.reported // DownloadManager's -1: size not known yet
        val last = states[id]
        if (last == null) {
            states[id] = State(bytes, atMs, null)
            return null
        }
        if (atMs <= last.atMs) return last.reported
        if (bytes < last.bytes) {
            states[id] = State(bytes, atMs, null)
            return null
        }
        val raw = (bytes - last.bytes) * 1000.0 / (atMs - last.atMs)
        val previous = last.speed
        val smoothed = if (previous == null) Math.round(raw) else Math.round(alpha * raw + (1 - alpha) * previous)
        last.bytes = bytes
        last.atMs = atMs
        last.speed = smoothed
        last.stalled = previous != null && smoothed < DownloadText.MIN_SPEED
        return last.reported
    }

    /** Drops every id not in [keep], so a finished or removed download starts over if it returns. */
    @Synchronized
    fun forget(keep: Set<Long>) {
        states.keys.retainAll(keep)
    }
}
