package net.mrowser.download

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log

/**
 * Android glue over the system `DownloadManager`: every file mrowser saves goes through here, the
 * update channel's APK included. Files land in public `Downloads/`, the one place a file manager
 * can still reach under scoped storage.
 *
 * Every call degrades instead of throwing. A missing or disabled download service, or a
 * `SecurityException`/`SQLiteException` from a stripped or operator-locked Downloads provider, is
 * realistic on the TV boxes this app targets, and the list polls these calls for as long as it is
 * open — a crash here would take the process down mid-download.
 *
 * `remove` deletes the file as well as the entry, so only Cancel download and Delete file call it.
 */
object Downloader {

    private const val TAG = "Downloader"

    /** Free from API 29 (scoped storage); a real check below it. */
    fun hasStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** The download id, or null when it could not be started. */
    fun enqueue(context: Context, spec: DownloadSpec): Long? {
        if (!hasStoragePermission(context)) return null
        return try {
            val dm = manager(context) ?: return null
            val request = DownloadManager.Request(Uri.parse(spec.url))
                .setTitle(spec.title)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // A name that already exists gets a "-1" suffix from DownloadManager; query()
                // reports the name the file really got.
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, spec.fileName)
                .setAllowedOverMetered(true)
            spec.mimeType?.let { request.setMimeType(it) }
            for ((name, value) in spec.headers) request.addRequestHeader(name, value)
            dm.enqueue(request)
        } catch (e: Exception) {
            Log.w(TAG, "enqueue failed", e)
            null
        }
    }

    /**
     * The downloads with these ids, or every download this app has made when [ids] is empty. Null
     * on any failure, so a caller can tell "the manager forgot them" (empty) from "the manager
     * could not be asked" (null) and never prunes on the latter.
     */
    fun query(context: Context, vararg ids: Long): List<DownloadInfo>? = try {
        val dm = manager(context)
        if (dm == null) {
            null
        } else {
            val query = DownloadManager.Query()
            if (ids.isNotEmpty()) query.setFilterById(*ids)
            dm.query(query)?.use { cursor ->
                val out = ArrayList<DownloadInfo>(cursor.count.coerceAtLeast(0))
                while (cursor.moveToNext()) out += read(cursor)
                out
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "query failed", e)
        null
    }

    /** Cancels a running download or deletes a finished one — the file goes too. */
    fun remove(context: Context, id: Long): Boolean = try {
        (manager(context)?.remove(id) ?: 0) > 0
    } catch (e: Exception) {
        Log.w(TAG, "remove failed", e)
        false
    }

    /** A `content://` URI another app can be granted, or null until the download has succeeded. */
    fun contentUri(context: Context, id: Long): Uri? = try {
        manager(context)?.getUriForDownloadedFile(id)
    } catch (e: Exception) {
        Log.w(TAG, "content uri failed", e)
        null
    }

    /** The saved file's `COLUMN_LOCAL_URI` (a `file://` URI below API 29). */
    fun fileUri(context: Context, id: Long): Uri? = try {
        manager(context)?.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    ?.let(Uri::parse)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "file uri failed", e)
        null
    }

    /** Whether the saved file can still be opened: a file manager may have deleted it. */
    fun fileExists(context: Context, id: Long): Boolean = try {
        val uri = contentUri(context, id)
        if (uri == null) {
            false
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }
    } catch (e: Exception) {
        false
    }

    private fun manager(context: Context): DownloadManager? =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    // Never COLUMN_LOCAL_FILENAME: it throws on targetSdk >= 24. The name comes from LOCAL_URI.
    private fun read(cursor: Cursor): DownloadInfo {
        val title = cursor.string(DownloadManager.COLUMN_TITLE)
        val localUri = cursor.string(DownloadManager.COLUMN_LOCAL_URI)
        return DownloadInfo(
            id = cursor.long(DownloadManager.COLUMN_ID),
            name = DownloadFileName.displayName(title, localUri),
            url = cursor.string(DownloadManager.COLUMN_URI).orEmpty(),
            mimeType = cursor.string(DownloadManager.COLUMN_MEDIA_TYPE),
            status = status(cursor.int(DownloadManager.COLUMN_STATUS)),
            reason = cursor.int(DownloadManager.COLUMN_REASON),
            bytesSoFar = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            totalBytes = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            modifiedAt = cursor.long(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP),
        )
    }

    private fun status(raw: Int): DownloadStatus = when (raw) {
        DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
        DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
        DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
        DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
        else -> DownloadStatus.FAILED
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
}
