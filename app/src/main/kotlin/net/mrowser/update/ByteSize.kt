package net.mrowser.update

import java.util.Locale

/**
 * Pure: a download size for a TV screen. Kilobytes (whole numbers) below 100 KB so a small
 * file doesn't read as "0.0 MB", megabytes to one decimal above that, gigabytes to one decimal
 * from the first size that would otherwise round to "1000.0 MB"; unknown reads as blank.
 */
object ByteSize {

    private const val KB = 1_000.0
    private const val MB = 1_000_000.0
    private const val GB = 1_000_000_000.0
    private const val KB_MB_BOUNDARY = 100_000L
    private const val MB_GB_BOUNDARY = 999_950_000L

    fun format(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < KB_MB_BOUNDARY -> String.format(Locale.US, "%.0f KB", bytes / KB)
        bytes < MB_GB_BOUNDARY -> String.format(Locale.US, "%.1f MB", bytes / MB)
        else -> String.format(Locale.US, "%.1f GB", bytes / GB)
    }
}
