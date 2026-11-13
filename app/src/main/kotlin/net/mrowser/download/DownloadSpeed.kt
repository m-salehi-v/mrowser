package net.mrowser.download

/**
 * Pure: a smoothed bytes-per-second per download, from the polled byte counts. Null until two
 * samples a moment apart exist.
 */
class DownloadSpeed(private val alpha: Double = 0.3) {

    private class State(var bytes: Long, var atMs: Long, var speed: Long?)

    private val states = HashMap<Long, State>()

    /** Feeds one poll; returns the smoothed speed so far, or null while unknown. */
    @Synchronized
    fun sample(id: Long, bytes: Long, atMs: Long): Long? {
        val last = states[id]
        if (last == null) {
            states[id] = State(bytes, atMs, null)
            return null
        }
        if (atMs <= last.atMs) return last.speed
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
        return smoothed
    }

    /** Drops every id not in [keep], so a finished or removed download starts over if it returns. */
    @Synchronized
    fun forget(keep: Set<Long>) {
        states.keys.retainAll(keep)
    }
}
