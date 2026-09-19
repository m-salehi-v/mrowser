package net.mrowser.update

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.widget.LinearLayout
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
            movementMethod = ScrollingMovementMethod()
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
            addView(body)
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
        dialog.setOnDismissListener { poll?.let(handler::removeCallbacks) }

        dialog.setOnShowListener {
            val download = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            download.setOnClickListener {
                ensureStoragePermission { granted ->
                    val id = if (granted) ApkDownloader.enqueue(activity, release) else null
                    if (id == null) {
                        body.text = activity.getString(R.string.update_manual, release.htmlUrl)
                        return@ensureStoragePermission
                    }
                    download.isEnabled = false
                    val tick = object : Runnable {
                        override fun run() {
                            when (val p = ApkDownloader.progress(activity, id)) {
                                is Progress.Running -> {
                                    body.text = activity.getString(
                                        R.string.update_downloading,
                                        ByteSize.format(p.bytesSoFar),
                                        ByteSize.format(p.totalBytes)
                                    )
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
