package net.mrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import net.mrowser.R

/**
 * Hands a downloaded APK to Android's own installer, which shows the app and asks the user to
 * confirm — mrowser never installs anything silently.
 *
 * The only callers are a user's press: Install on a Downloads row, and the update dialog's
 * Install update. No page can reach this; a page's download only ever lands in the list.
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"

    /** Below API 26 the system-wide "Unknown sources" switch decides, and the installer asks for it. */
    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /**
     * A `content://` URI from API 24, granted to the installer per intent; below it the pre-N
     * installer reads only `file:`, and API 23 has no `FileUriExposedException` to stop one.
     */
    fun apkUri(context: Context, downloadId: Long): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Downloader.contentUri(context, downloadId)
        } else {
            Downloader.fileUri(context, downloadId)
        }

    fun install(activity: Activity, downloadId: Long, onUnavailable: (messageRes: Int) -> Unit) {
        val uri = apkUri(activity, downloadId)
        // A SUCCESSFUL row keeps its URI after a file manager deletes the file, and the installer
        // would show only a generic parse error; say what happened instead.
        if (uri == null || !Downloader.fileExists(activity, downloadId)) {
            onUnavailable(R.string.download_file_removed)
            return
        }
        install(activity, uri, onUnavailable)
    }

    /**
     * Without the install permission (API 26+) this explains, opens mrowser's "Install unknown
     * apps" page and stops: the user presses Install again once it is on. Returning from the
     * settings page does not install by itself, so nothing happens behind the user's back.
     */
    fun install(activity: Activity, apk: Uri, onUnavailable: (messageRes: Int) -> Unit) {
        if (!canRequestInstalls(activity)) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.install_permission_title)
                .setMessage(R.string.install_permission_message)
                .setPositiveButton(R.string.install_permission_open) { _, _ ->
                    try {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.packageName)
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        // Some TV builds strip the page. The installer then shows its own
                        // "blocked" screen, which on most builds links to the same switch.
                        Log.w(TAG, "no unknown-sources settings page", e)
                        launchInstaller(activity, apk, onUnavailable)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        launchInstaller(activity, apk, onUnavailable)
    }

    private fun launchInstaller(activity: Activity, apk: Uri, onUnavailable: (messageRes: Int) -> Unit) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apk, DownloadActions.APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no package installer", e)
            onUnavailable(R.string.download_no_installer)
        }
    }
}
