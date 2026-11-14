package net.mrowser.update

import android.app.Activity
import android.util.Log
import net.mrowser.R
import net.mrowser.download.DownloadActions
import net.mrowser.download.DownloadSpec
import net.mrowser.download.DownloadStatus
import net.mrowser.download.Downloader

/** How an enqueued download is getting on. */
sealed class Progress {
    /** [totalBytes] is -1 until DownloadManager learns the content length. */
    data class Running(val bytesSoFar: Long, val totalBytes: Long) : Progress()
    object Done : Progress()
    object Failed : Progress()
}

/**
 * The update channel's side of [Downloader]: it saves the release APK to public `Downloads/`
 * under a fixed name, after checking the URL is GitHub's and the version is digits and dots.
 *
 * Saving is all it does. The file then shows on mrowser's Downloads page like any other download,
 * and installing it is a user's press there (or on Install update), which hands it to Android's
 * installer through `ApkInstaller` — the installer asks the user to confirm.
 */
object ApkDownloader {

    const val APK_MIME = DownloadActions.APK_MIME

    private const val TAG = "ApkDownloader"

    fun fileName(release: Release): String = "mrowser-${release.version}.apk"

    /** The download id, or null when it could not be started — caller shows the manual route. */
    fun enqueue(activity: Activity, release: Release): Long? {
        if (!DownloadUrl.isAllowed(release.apkUrl)) {
            Log.w(TAG, "refusing an apk url that is not on a GitHub host")
            return null
        }
        // Defence in depth, not a live hole: release.version feeds the destination filename
        // below, and UpdateCheckPolicy.bannerFor already refuses any version VersionCompare
        // can't read as digits-and-dots. But that invariant lives three modules away with no
        // test pinning the coupling, so this guards its own input too.
        if (release.version.any { !it.isDigit() && it != '.' }) {
            Log.w(TAG, "refusing a release version with unexpected characters")
            return null
        }
        return Downloader.enqueue(
            activity,
            DownloadSpec(
                url = release.apkUrl,
                fileName = fileName(release),
                mimeType = APK_MIME,
                headers = emptyMap(),
                title = activity.getString(R.string.update_download_title, release.version),
            )
        )
    }

    /** Polled every second by UpdateDialog; a failed query reads as a failed download. */
    fun progress(activity: Activity, id: Long): Progress {
        val info = Downloader.query(activity, id)?.firstOrNull() ?: return Progress.Failed
        return when (info.status) {
            DownloadStatus.SUCCESSFUL -> Progress.Done
            DownloadStatus.FAILED -> Progress.Failed
            else -> Progress.Running(info.bytesSoFar, info.totalBytes)
        }
    }
}
