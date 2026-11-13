package net.mrowser.download

import java.math.BigInteger

/** What the chip and the download ring show; [name] is set only when exactly one download is active. */
data class DownloadSummary(val active: Int, val percent: Int?, val name: String?)

/** Ordering, summary and finish detection for the download list. Pure. */
object DownloadList {

    /** Hidden ids dropped; active rows first, then the rest, each newest first (ids only grow). */
    fun visible(all: List<DownloadInfo>, hidden: Set<Long>): List<DownloadInfo> {
        val (active, rest) = all.filter { it.id !in hidden }.partition { it.isActive }
        return active.sortedByDescending { it.id } + rest.sortedByDescending { it.id }
    }

    fun summary(visible: List<DownloadInfo>): DownloadSummary {
        val active = visible.filter { it.isActive }
        if (active.isEmpty()) return DownloadSummary(0, null, null)
        val sized = active.filter { it.totalBytes > 0 }
        val soFar = sized.sumOf { it.bytesSoFar.coerceAtLeast(0L) }
        val total = sized.sumOf { it.totalBytes }
        return DownloadSummary(
            active = active.size,
            percent = percent(soFar, total),
            name = if (active.size == 1) active[0].name else null,
        )
    }

    /** Downloads that were active at the last poll and have succeeded since. */
    fun justFinished(previouslyActive: Set<Long>, now: List<DownloadInfo>): List<DownloadInfo> =
        now.filter { it.id in previouslyActive && it.status == DownloadStatus.SUCCESSFUL }

    /** Floored, clamped to 0..100; null when the total is unknown (<= 0). Never overflows. */
    fun percent(soFar: Long, total: Long): Int? {
        if (total <= 0) return null
        if (soFar <= 0) return 0
        if (soFar >= total) return 100
        return BigInteger.valueOf(soFar).multiply(BigInteger.valueOf(100))
            .divide(BigInteger.valueOf(total)).toInt().coerceIn(0, 100)
    }
}
