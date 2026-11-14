package net.mrowser.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Android glue for favorites export/import (issue #48); the format is [FavoritesBackup].
 *
 * Export goes to a **fixed** file, `Downloads/mrowser-favorites.json`, with no picker: on Fire OS
 * a document picker can't be D-padded anywhere except Downloads, and some boxes ship none at all.
 * Import can't mirror that. From Android 11 an app can't read a non-media file in Downloads that
 * another install wrote — which is the whole new-device case — so it goes through
 * `ACTION_OPEN_DOCUMENT`, opened straight in Downloads where API 26+ lets it.
 */
object FavoritesBackupFile {

    private const val TAG = "FavoritesBackupFile"

    /** Larger than any real favorites list or bookmarks.html (Firefox embeds a favicon per
     *  bookmark); a wrong pick must not read a video into memory. */
    private const val MAX_BYTES = 8 shl 20

    /** The Downloads root of the system's Downloads documents provider. */
    private val DOWNLOADS_DOCUMENT: Uri = DocumentsContract.buildDocumentUri(
        "com.android.providers.downloads.documents", "downloads"
    )

    /** Below API 29 this needs WRITE_EXTERNAL_STORAGE — see ApkDownloader.hasStoragePermission. */
    fun export(context: Context, json: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportScoped(context, json)
        else exportLegacy(context, json)
    } catch (e: Exception) {
        // IOException, or a SecurityException / IllegalStateException from a stripped or
        // operator-locked media provider — the same firmware class ApkDownloader anticipates.
        Log.w(TAG, "export failed", e)
        null
    }

    /** The picker, opened in Downloads where the platform takes a starting location. */
    fun pickIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        // Not application/json: providers disagree on a .json file's type (a pushed or copied
        // file is often application/octet-stream), and a hidden file can't be picked at all.
        // A wrong pick gets a clear message from FavoritesBackup.parse instead.
        type = "*/*"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_DOCUMENT)
        }
    }

    /** The picked file's text, or null if it can't be read or is implausibly large. */
    fun read(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        Log.w(TAG, "import read failed", e)
        null
    }

    /**
     * Overwrites this app's earlier export rather than letting MediaStore add a "(1)" copy each
     * time. The query only ever sees files this install created, so a same-named file from
     * elsewhere still gets a suffixed sibling — the name reported is the one actually written.
     */
    private fun exportScoped(context: Context, json: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val previous = findOwnExport(resolver, collection)
        if (previous != null) {
            try {
                write(resolver, previous, json)
                return displayPath(resolver, previous)
            } catch (e: IOException) {
                // A row whose file was removed behind MediaStore's back; start over.
                Log.w(TAG, "previous export unwritable, recreating", e)
                resolver.delete(previous, null, null)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FavoritesBackup.FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, FavoritesBackup.MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("insert refused")
        try {
            write(resolver, uri, json)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return displayPath(resolver, uri)
    }

    private fun findOwnExport(resolver: ContentResolver, collection: Uri): Uri? =
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(FavoritesBackup.FILE_NAME, Environment.DIRECTORY_DOWNLOADS + "/"),
            null
        )?.use { if (it.moveToFirst()) ContentUris.withAppendedId(collection, it.getLong(0)) else null }

    /** "wt": plain "w" leaves a longer previous export's tail behind on some versions. */
    private fun write(resolver: ContentResolver, uri: Uri, json: String) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("no stream for $uri")
        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    private fun displayPath(resolver: ContentResolver, uri: Uri): String {
        val name = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        return "Downloads/" + (name ?: FavoritesBackup.FILE_NAME)
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(context: Context, json: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, FavoritesBackup.FILE_NAME)
        file.writeText(json)
        // So the picker (and a file manager reading MediaStore) sees it without a reboot.
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(FavoritesBackup.MIME), null)
        return "Downloads/" + file.name
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
