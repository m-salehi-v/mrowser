package net.mrowser.update

import java.util.Locale

/** Pure: a download size for a TV screen. Megabytes to one decimal; unknown reads as blank. */
object ByteSize {

    private const val MB = 1_000_000.0

    fun format(bytes: Long): String =
        if (bytes <= 0L) "" else String.format(Locale.US, "%.1f MB", bytes / MB)
}
