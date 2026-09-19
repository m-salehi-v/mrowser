package net.mrowser.update

import java.util.Locale

/**
 * Pure: a download size for a TV screen. Kilobytes (whole numbers) below 100 KB so a small
 * file doesn't read as "0.0 MB", megabytes to one decimal above that; unknown reads as blank.
 */
object ByteSize {

    private const val KB = 1_000.0
    private const val MB = 1_000_000.0
    private const val KB_MB_BOUNDARY = 100_000L

    fun format(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < KB_MB_BOUNDARY -> String.format(Locale.US, "%.0f KB", bytes / KB)
        else -> String.format(Locale.US, "%.1f MB", bytes / MB)
    }
}
