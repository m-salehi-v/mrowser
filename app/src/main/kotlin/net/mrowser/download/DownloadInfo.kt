package net.mrowser.download

enum class DownloadStatus { PENDING, RUNNING, PAUSED, SUCCESSFUL, FAILED }

/**
 * One row of the system DownloadManager, as the list shows it. [reason] is the manager's raw
 * `COLUMN_REASON`; [totalBytes] is -1 while the server has not said how big the file is.
 */
data class DownloadInfo(
    val id: Long,
    val name: String,
    val url: String,
    val mimeType: String?,
    val status: DownloadStatus,
    val reason: Int,
    val bytesSoFar: Long,
    val totalBytes: Long,
    val modifiedAt: Long,
) {
    val isActive: Boolean
        get() = status == DownloadStatus.PENDING || status == DownloadStatus.RUNNING || status == DownloadStatus.PAUSED
}
