package net.mrowser.download

interface DownloadsRepository {
    fun get(): DownloadsState
    fun update(state: DownloadsState)
}
