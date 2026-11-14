package net.mrowser.download

/** One list row, ready to draw. [progress] is the percent while active and sized, else null. */
data class DownloadRowModel(
    val info: DownloadInfo,
    val kind: DownloadKind,
    val detail: String,
    val progress: Int?,
    val primary: Primary,
    val fileMissing: Boolean = false,
)

/** Pure: combines kind, action, text and progress into a row. */
object DownloadRows {

    fun build(info: DownloadInfo, pageUrl: String?, speedBps: Long?, fileMissing: Boolean, nowMs: Long): DownloadRowModel =
        DownloadRowModel(
            info = info,
            kind = DownloadActions.kind(info.name, info.mimeType),
            detail = DownloadText.detail(info, DownloadText.sourceHost(pageUrl, info.url), speedBps, fileMissing, nowMs),
            progress = if (info.isActive) DownloadList.percent(info.bytesSoFar, info.totalBytes) else null,
            primary = DownloadActions.primary(info, fileMissing),
            fileMissing = fileMissing,
        )
}
