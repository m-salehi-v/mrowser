package net.mrowser.update

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import net.mrowser.R

/** How an enqueued download is getting on. */
sealed class Progress {
    /** [totalBytes] is -1 until DownloadManager learns the content length. */
    data class Running(val bytesSoFar: Long, val totalBytes: Long) : Progress()
    object Done : Progress()
    object Failed : Progress()
}

/**
 * Android glue over the system `DownloadManager`.
 *
 * mrowser never installs the APK — that would need `REQUEST_INSTALL_PACKAGES`, which this app
 * deliberately does not hold. The file goes to public `Downloads/` instead, the one place a file
 * manager can still reach under scoped storage, and the user installs it from there.
 *
 * This is also the wrapper a general download manager would build on; expect it to generalise
 * into a `Downloader` taking a URL, a filename and a MIME type.
 */
object ApkDownloader {

    const val APK_MIME = "application/vnd.android.package-archive"

    private const val TAG = "ApkDownloader"

    fun fileName(release: Release): String = "mrowser-${release.version}.apk"

    /** Free from API 29 (scoped storage); a real check below it. */
    fun hasStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** The download id, or null when it could not be started — caller shows the manual route. */
    fun enqueue(activity: Activity, release: Release): Long? {
        if (!DownloadUrl.isAllowed(release.apkUrl)) {
            Log.w(TAG, "refusing an apk url that is not on a GitHub host")
            return null
        }
        if (!hasStoragePermission(activity)) return null
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return null
        return try {
            dm.enqueue(
                DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle(activity.getString(R.string.update_download_title, release.version))
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    // A name that already exists gets a "-1" suffix from DownloadManager; the
                    // dialog reports the name it asked for, which is the one a fresh box sees.
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, fileName(release)
                    )
                    .setAllowedOverMetered(true)
            )
        } catch (e: Exception) {
            Log.w(TAG, "enqueue failed", e)
            null
        }
    }

    fun progress(activity: Activity, id: Long): Progress {
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return Progress.Failed
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return Progress.Failed
        cursor.use {
            if (!it.moveToFirst()) return Progress.Failed
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val soFar =
                it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total =
                it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> Progress.Done
                DownloadManager.STATUS_FAILED -> Progress.Failed
                else -> Progress.Running(soFar, total)
            }
        }
    }
}
