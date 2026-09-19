package net.mrowser.update

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import net.mrowser.R

/**
 * "Update available": version, release notes, size, and a Download that puts the APK in public
 * `Downloads/`. mrowser does not install it — see the design doc for why.
 *
 * Progress is polled rather than watched through `ACTION_DOWNLOAD_COMPLETE`, which on targetSdk
 * 34 would need `RECEIVER_EXPORTED` and lifecycle registration for one line of text. The poll is
 * scoped to the dialog; backing out leaves `DownloadManager` running with its own notification.
 */
object UpdateDialog {

    private const val NOTES_LIMIT = 2000
    private const val POLL_MS = 1000L

    fun show(
        activity: Activity,
        release: Release,
        ensureStoragePermission: ((granted: Boolean) -> Unit) -> Unit,
        onObtainium: () -> Unit
    ) {
        val body = TextView(activity).apply {
            // Release notes are network-supplied text: plain into a TextView, never the WebView.
            text = activity.getString(
                R.string.update_body,
                release.notes.take(NOTES_LIMIT).trim(),
                ByteSize.format(release.apkSizeBytes)
            )
        }
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            // A ScrollView, not TextView.setMovementMethod(ScrollingMovementMethod()) — that
            // call makes the TextView itself focusable/clickable, which on a D-pad lets long
            // notes join the focus order ahead of the Download button.
            addView(ScrollView(activity).apply { addView(body) })
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title, release.version))
            .setView(container)
            // Listener set in onShow instead of here, so tapping Download does not dismiss the
            // dialog — it turns into the progress line.
            .setPositiveButton(R.string.update_download, null)
            .setNeutralButton(R.string.update_obtainium) { _, _ -> onObtainium() }
            .setNegativeButton(R.string.close, null)
            .create()

        val handler = Handler(Looper.getMainLooper())
        var poll: Runnable? = null
        // ensureStoragePermission resolves asynchronously on API 23-28 (the system prompt), so
        // the dialog can be dismissed before its callback runs. Latch that so the callback does
        // not enqueue a download or start a poll chain nothing is left to cancel.
        var dismissed = false
        dialog.setOnDismissListener {
            dismissed = true
            poll?.let(handler::removeCallbacks)
        }

        dialog.setOnShowListener {
            val download = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            download.setOnClickListener {
                ensureStoragePermission { granted ->
                    if (dismissed) return@ensureStoragePermission
                    val id = if (granted) ApkDownloader.enqueue(activity, release) else null
                    if (id == null) {
                        body.text = activity.getString(R.string.update_manual, release.htmlUrl)
                        // No second ask: leaving Download enabled would let another tap re-enter
                        // ensureStoragePermission, and on API 23-28 a second system prompt carries
                        // a "never ask again" checkbox.
                        download.isEnabled = false
                        return@ensureStoragePermission
                    }
                    download.isEnabled = false
                    val tick = object : Runnable {
                        override fun run() {
                            when (val p = ApkDownloader.progress(activity, id)) {
                                is Progress.Running -> {
                                    val soFar = ByteSize.format(p.bytesSoFar)
                                    body.text = when {
                                        // bytesSoFar is still 0 on the first tick or two.
                                        soFar.isBlank() ->
                                            activity.getString(R.string.update_downloading_starting)
                                        // totalBytes is -1 until DownloadManager learns it.
                                        p.totalBytes <= 0 -> activity.getString(
                                            R.string.update_downloading_unknown, soFar
                                        )
                                        else -> activity.getString(
                                            R.string.update_downloading,
                                            soFar,
                                            ByteSize.format(p.totalBytes)
                                        )
                                    }
                                    handler.postDelayed(this, POLL_MS)
                                }
                                Progress.Done -> body.text = activity.getString(
                                    R.string.update_saved, ApkDownloader.fileName(release)
                                )
                                Progress.Failed -> {
                                    body.text = activity.getString(R.string.update_failed)
                                    download.isEnabled = true
                                }
                            }
                        }
                    }
                    poll = tick
                    handler.post(tick)
                }
            }
        }
        dialog.show()
    }
}
