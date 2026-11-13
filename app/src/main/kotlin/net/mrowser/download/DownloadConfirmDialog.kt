package net.mrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.os.SystemClock
import net.mrowser.R

/**
 * "Download this file?" — the one gate between a page offering a file and `DownloadManager`
 * saving it. A page can never start a download by itself: only Download here does.
 */
object DownloadConfirmDialog {

    /**
     * [size] is the formatted size, or the "Unknown size" text; [host] is the download URL's.
     * [onDecline] runs when the dialog goes away without Download (Cancel, BACK, outside).
     */
    fun show(
        activity: Activity,
        name: String,
        size: String,
        host: String,
        onDecline: () -> Unit,
        onDownload: () -> Unit,
    ): AlertDialog {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.download_confirm_title)
            .setMessage(activity.getString(R.string.download_confirm_message, name, size, host))
            // Listener set in onShow instead, so a press inside the ConfirmDelay neither confirms
            // nor dismisses.
            .setPositiveButton(R.string.download_action, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        var confirmed = false
        dialog.setOnDismissListener { if (!confirmed) onDecline() }
        dialog.setOnShowListener {
            val download = dialog.getButton(AlertDialog.BUTTON_POSITIVE) ?: return@setOnShowListener
            // The delay runs from when the dialog's window has focus, not from onShow: a dialog
            // opened while the player is in front has been "shown" all along, and would otherwise
            // be armed the moment the user comes back. Every regain of focus restarts it.
            val decor = dialog.window?.decorView
            var armedAt: Long? = if (decor == null || decor.hasWindowFocus()) SystemClock.uptimeMillis() else null
            decor?.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
                armedAt = if (hasFocus) SystemClock.uptimeMillis() else null
            }
            // The D-pad starts on the action, as the spec asks; ConfirmDelay keeps a repeat of
            // the press that made the page fire this download from landing on it.
            download.requestFocus()
            download.setOnClickListener {
                if (!ConfirmDelay.accepts(armedAt, SystemClock.uptimeMillis())) return@setOnClickListener
                confirmed = true
                dialog.dismiss()
                onDownload()
            }
        }
        dialog.show()
        return dialog
    }
}
