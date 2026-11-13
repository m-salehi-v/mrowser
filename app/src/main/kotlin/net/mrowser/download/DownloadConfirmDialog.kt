package net.mrowser.download

import android.app.Activity
import android.app.AlertDialog
import net.mrowser.R

/**
 * "Download this file?" — the one gate between a page offering a file and `DownloadManager`
 * saving it. A page can never start a download by itself: only Download here does.
 */
object DownloadConfirmDialog {

    /** [size] is the formatted size, or the "Unknown size" text; [host] is the download URL's. */
    fun show(activity: Activity, name: String, size: String, host: String, onDownload: () -> Unit): AlertDialog {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.download_confirm_title)
            .setMessage(activity.getString(R.string.download_confirm_message, name, size, host))
            .setPositiveButton(R.string.download_action) { _, _ -> onDownload() }
            .setNegativeButton(R.string.cancel, null)
            .create()
        // The leanback layout puts Download first, but say so: the D-pad starts on the action.
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus() }
        dialog.show()
        return dialog
    }
}
