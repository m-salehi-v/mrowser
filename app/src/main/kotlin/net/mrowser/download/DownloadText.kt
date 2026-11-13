package net.mrowser.download

import net.mrowser.home.RelativeTime
import net.mrowser.update.ByteSize
import net.mrowser.web.UrlHost

/** What the downloads chip shows: [label], then an optional dim [trailing] ("· 42%"). */
data class ChipText(val label: String, val trailing: String?)

/** Pure: the English text of the download list and the chip. */
object DownloadText {

    private const val SEP = " · "

    /** Below 1 KB/s a speed is a stall, not a rate: it reads "0 KB/s" and its ETA is absurd. */
    const val MIN_SPEED = 1_024L
    private const val MAX_ETA_SECONDS = 99L * 3_600

    /**
     * "42% · 1.0 GB of 2.4 GB · 3.1 MB/s · about 8 min left". Nothing arrived yet is "Starting…".
     * A server that never announced a size, or sent more than it announced, gets no percent and
     * no time left; a speed below [MIN_SPEED] is left out, so no time is ever computed from it.
     */
    fun progress(soFar: Long, total: Long, speedBps: Long?): String {
        if (soFar <= 0) return "Starting…"
        val sized = total > 0 && soFar <= total
        val speed = speedBps?.takeIf { it >= MIN_SPEED }
        val parts = ArrayList<String>(4)
        if (sized) {
            parts += "${DownloadList.percent(soFar, total)}%"
            parts += "${ByteSize.format(soFar)} of ${ByteSize.format(total)}"
        } else {
            parts += ByteSize.format(soFar)
        }
        if (speed != null) parts += "${ByteSize.format(speed)}/s"
        if (sized && speed != null) timeLeft(total - soFar, speed)?.let { parts += it }
        return parts.joinToString(SEP)
    }

    fun timeLeft(remainingBytes: Long, speedBps: Long): String? {
        if (speedBps <= 0) return null
        val secs = ceilDiv(remainingBytes.coerceAtLeast(0L), speedBps)
        if (secs > MAX_ETA_SECONDS) return null
        if (secs < 60) return "less than a minute left"
        val mins = ceilDiv(secs, 60)
        if (mins < 60) return "about $mins min left"
        val hours = mins / 60
        val rest = mins % 60
        return if (rest == 0L) "about $hours h left" else "about $hours h $rest min left"
    }

    private fun ceilDiv(a: Long, b: Long): Long = a / b + if (a % b != 0L) 1 else 0

    fun failure(reason: Int): String = when (reason) {
        1001 -> "couldn't save the file"
        1002 -> "the server refused it"
        1004 -> "the connection broke"
        1005 -> "too many redirects"
        1006 -> "not enough space"
        1007 -> "no storage found"
        1008 -> "it couldn't resume"
        1009 -> "the file already exists"
        in 400..599 -> "the server answered $reason"
        else -> "something went wrong"
    }

    fun detail(info: DownloadInfo, host: String?, speedBps: Long?, fileMissing: Boolean, nowMs: Long): String = when {
        fileMissing -> "File removed"
        info.status == DownloadStatus.FAILED -> "Failed · ${failure(info.reason)}"
        info.status == DownloadStatus.PAUSED -> when (info.reason) {
            2, 3 -> "Waiting for network"
            1 -> "Waiting to retry"
            else -> "Paused"
        }
        info.status == DownloadStatus.PENDING -> "Waiting to start"
        info.status == DownloadStatus.RUNNING -> progress(info.bytesSoFar, info.totalBytes, speedBps)
        else -> listOf(
            ByteSize.format(if (info.totalBytes > 0) info.totalBytes else info.bytesSoFar),
            host.orEmpty(),
            if (info.modifiedAt > 0) RelativeTime.format(nowMs - info.modifiedAt) else "",
        ).filter { it.isNotBlank() }.joinToString(SEP)
    }

    /** The page's host when it is a web page, else the file's; no leading "www.". */
    fun sourceHost(pageUrl: String?, downloadUrl: String): String? {
        val page = pageUrl?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        val host = (page?.let { UrlHost.of(it) }) ?: UrlHost.of(downloadUrl)
        return host?.removePrefix("www.")?.ifEmpty { null }
    }

    fun chip(summary: DownloadSummary): ChipText? {
        if (summary.active <= 0) return null
        val label = if (summary.active == 1 && summary.name != null) summary.name else "${summary.active} downloads"
        return ChipText(label, summary.percent?.let { "· $it%" })
    }

    fun chipDone(name: String): ChipText = ChipText("Downloaded · $name", null)
}
