package net.mrowser.download

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Polls `DownloadManager` and turns each answer into a [DownloadsSnapshot] for the chip, the bar
 * ring and the Downloads page. One tick a second on the UI-thread handler, and only while
 * something is downloading or the Downloads page is open; [kick] runs a tick now (a download
 * started, a row hidden, the Activity resumed).
 *
 * The query runs on the UI thread, as the update dialog's poll already does: it is a single
 * cursor over this app's own rows. A query that fails keeps the last snapshot and never prunes —
 * an empty set given to [DownloadsOps.prune] would forget every download's page.
 *
 * `DownloadManager.remove()` deletes the file, so only [remove] reaches it, and only Cancel
 * download and Delete file call that; every "take it off the list" path goes through [hide].
 */
class DownloadsController(
    context: Context,
    private val store: DownloadsRepository,
    private val isPageOpen: () -> Boolean,
    private val onSnapshot: (DownloadsSnapshot) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val context = context.applicationContext
    private val speed = DownloadSpeed()

    /** Active at the previous successful poll, for [DownloadList.justFinished] and `justFailed`. */
    private var previouslyActive: Set<Long> = emptySet()

    /** The rows last shown, for Clear finished. */
    private var lastVisible: List<DownloadInfo> = emptyList()
    private var last: DownloadsSnapshot? = null

    private val tick = Runnable { poll() }

    /** Runs a poll now, replacing one already waiting. */
    fun kick() {
        handler.removeCallbacks(tick)
        poll()
    }

    fun stop() {
        handler.removeCallbacks(tick)
    }

    /** Enqueues [spec] and remembers the page it came from. Null when it could not be started. */
    fun start(spec: DownloadSpec, pageUrl: String?): Long? {
        val id = Downloader.enqueue(context, spec) ?: return null
        save(DownloadsOps.recordPage(store.get(), id, pageUrl))
        kick()
        return id
    }

    /** A new download of the same link with fresh headers; the failed row leaves the list. */
    fun retry(info: DownloadInfo, cookie: String?, userAgent: String?): Long? {
        val spec = DownloadSpec(
            url = info.url,
            // A failed row's name may be the title, without the extension the file needs.
            fileName = DownloadFileName.sanitize(DownloadFileName.withExtension(info.name, info.mimeType, info.url)),
            mimeType = info.mimeType,
            headers = DownloadRequest.headers(cookie, userAgent, pageUrl(info.id), info.url),
        )
        val id = Downloader.enqueue(context, spec) ?: return null
        save(DownloadsOps.retried(store.get(), info.id, id))
        kick()
        return id
    }

    fun pageUrl(id: Long): String? = store.get().pages[id]

    /** Off the list only: the file and the manager's entry stay. */
    fun hide(ids: Set<Long>) {
        if (ids.isNotEmpty()) save(DownloadsOps.hide(store.get(), ids))
        kick()
    }

    /** Cancel download / Delete file: `DownloadManager.remove`, which deletes the file too. */
    fun remove(id: Long) {
        Downloader.remove(context, id)
        kick()
    }

    /** The saved file is gone (a file manager deleted it); the row says so from now on. */
    fun markMissing(id: Long) {
        missing += id
        kick()
    }

    /** Hides every finished or failed row that is showing; active ones stay. */
    fun clearFinished() {
        hide(DownloadActions.clearFinished(lastVisible).ids)
    }

    private fun save(state: DownloadsState) {
        if (state !== store.get()) store.update(state)
    }

    private fun poll() {
        val all = Downloader.query(context)
        if (all == null) {
            // The manager could not be asked: keep what is on screen and never prune on it.
            reschedule(last?.summary?.active ?: 0)
            return
        }
        val state = DownloadsOps.prune(store.get(), all.mapTo(HashSet()) { it.id })
        save(state)

        val visible = DownloadList.visible(all, state.hidden)
        val at = now()
        val speeds = HashMap<Long, Long?>()
        for (info in visible) {
            if (info.status == DownloadStatus.RUNNING) speeds[info.id] = speed.sample(info.id, info.bytesSoFar, at)
        }
        speed.forget(speeds.keys)

        val finished = DownloadList.justFinished(previouslyActive, visible)
        val failed = DownloadList.justFailed(previouslyActive, visible)
        previouslyActive = visible.filter { it.isActive }.mapTo(HashSet()) { it.id }
        lastVisible = visible

        val rows = visible.map { info ->
            DownloadRows.build(info, state.pages[info.id], speeds[info.id], info.id in missing, at)
        }
        val snapshot = DownloadsSnapshot(rows, DownloadList.summary(visible), finished, failed)
        last = snapshot
        onSnapshot(snapshot)
        reschedule(snapshot.summary.active)
    }

    private fun reschedule(active: Int) {
        handler.removeCallbacks(tick)
        if (active > 0 || isPageOpen()) handler.postDelayed(tick, POLL_MS)
    }

    companion object {
        const val POLL_MS = 1_000L

        /** Files found deleted; for the process's lifetime, like the player's resume positions. */
        private val missing = HashSet<Long>()
    }
}
