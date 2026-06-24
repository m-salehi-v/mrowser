package net.mrowser.home

/** Pure formatter for an elapsed-millis delta into a short "Nm ago" label. */
object RelativeTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun format(deltaMillis: Long): String = when {
        deltaMillis < MINUTE -> "just now"
        deltaMillis < HOUR -> "${deltaMillis / MINUTE}m ago"
        deltaMillis < DAY -> "${deltaMillis / HOUR}h ago"
        else -> "${deltaMillis / DAY}d ago"
    }
}
