package net.mrowser.download

internal fun info(
    id: Long, status: DownloadStatus, name: String = "f$id.bin", mime: String? = null,
    soFar: Long = 0, total: Long = -1, reason: Int = 0,
    url: String = "https://cdn.example/f$id", modified: Long = 0,
) = DownloadInfo(id, name, url, mime, status, reason, soFar, total, modified)
