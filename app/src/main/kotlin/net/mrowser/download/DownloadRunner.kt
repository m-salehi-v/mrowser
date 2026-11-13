package net.mrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import net.mrowser.R
import net.mrowser.home.DestructiveButton
import net.mrowser.stream.PlaybackRequest

/**
 * What a Downloads row does: OK runs its primary action (Play, Install, Open, Retry, or the
 * options for a row that has none), hold OK or MENU opens the options. The decisions are
 * [DownloadActions]'; this is the Android half.
 *
 * `DownloadManager.remove()` deletes the file, so only Cancel download and Delete file reach
 * [DownloadsController.remove] — Delete file behind its own confirm. Remove from list hides.
 */
class DownloadRunner(
    private val activity: Activity,
    private val downloads: DownloadsController,
    private val play: (PlaybackRequest) -> Unit,
    private val userAgent: () -> String,
    private val ensureStoragePermission: ((Boolean) -> Unit) -> Unit,
) {

    fun primary(row: DownloadRowModel) {
        val info = row.info
        when (row.primary) {
            Primary.PLAY -> contentUriOrMissing(info.id)?.let { uri ->
                // No headers or subtitles: a local file. Resume is keyed by this URL as for any other.
                play(PlaybackRequest(uri.toString(), emptyMap(), emptyList(), info.name))
            }
            Primary.INSTALL -> if (exists(info.id)) {
                ApkInstaller.install(activity, info.id) { toast(it) }
            }
            Primary.OPEN -> contentUriOrMissing(info.id)?.let { uri -> open(uri, info.mimeType) }
            Primary.RETRY -> retry(info)
            Primary.OPTIONS -> options(row)
        }
    }

    fun options(row: DownloadRowModel) {
        val items = DownloadActions.options(row.info, row.fileMissing)
            .mapNotNull { option -> label(option, row.primary)?.let { option to it } }
        if (items.isEmpty()) return
        AlertDialog.Builder(activity)
            .setTitle(row.info.name)
            .setItems(items.map { it.second }.toTypedArray()) { _, which -> pick(items[which].first, row) }
            .show()
    }

    private fun pick(option: DownloadOption, row: DownloadRowModel) {
        // The dialog may have sat open while the download moved on: decide on its state now, so a
        // Cancel download picked after it finished cannot delete the finished file. Cancel and
        // Delete file act only on a fresh answer — never on the row as it was when it opened.
        val info = DownloadActions.stateFor(option, Downloader.query(activity, row.info.id), row.info)
        if (info == null) {
            toast(R.string.download_check_failed)
            return
        }
        when (val effect = DownloadActions.effect(option, info, row.fileMissing)) {
            is DownloadEffect.Run ->
                primary(row.copy(info = info, primary = effect.primary))
            is DownloadEffect.Hide -> downloads.hide(effect.ids)
            is DownloadEffect.RemoveFromManager ->
                if (option == DownloadOption.DELETE_FILE) confirmDelete(info, effect.id)
                else downloads.remove(effect.id)
            is DownloadEffect.CopyLink -> copyLink(effect.url)
        }
    }

    private fun confirmDelete(info: DownloadInfo, id: Long) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.download_delete_title)
            .setMessage(activity.getString(R.string.download_delete_message, info.name))
            .setPositiveButton(R.string.delete) { _, _ -> downloads.remove(id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also { DestructiveButton.apply(it, AlertDialog.BUTTON_POSITIVE) }
    }

    private fun retry(info: DownloadInfo) {
        ensureStoragePermission { granted ->
            if (!granted) {
                Toast.makeText(activity, R.string.download_storage_denied, Toast.LENGTH_LONG).show()
                return@ensureStoragePermission
            }
            val cookie = CookieManager.getInstance().getCookie(info.url)
            if (downloads.retry(info, cookie, userAgent()) == null) {
                Toast.makeText(activity, R.string.download_failed_to_start, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun open(uri: Uri, mimeType: String?) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeType ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: ActivityNotFoundException) {
            toast(R.string.download_no_app)
        } catch (e: SecurityException) {
            // A viewer that refuses the grant (or is not exported) is, to the user, no app at all.
            Log.w(TAG, "viewer refused the file", e)
            toast(R.string.download_no_app)
        }
    }

    private fun copyLink(url: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
        toast(R.string.download_link_copied)
    }

    /** False after telling the user, and marking the row "File removed", when the file is gone. */
    private fun exists(id: Long): Boolean {
        if (Downloader.fileExists(activity, id)) return true
        toast(R.string.download_file_removed)
        downloads.markMissing(id)
        return false
    }

    /** [exists] already needs the URI, so null here means the file is gone and the user was told. */
    private fun contentUriOrMissing(id: Long): Uri? =
        if (exists(id)) Downloader.contentUri(activity, id) else null

    private fun label(option: DownloadOption, primary: Primary): CharSequence? = when (option) {
        DownloadOption.PRIMARY -> DownloadsView.actionLabel(primary)?.let(activity::getString)
        DownloadOption.CANCEL -> activity.getString(R.string.download_cancel)
        DownloadOption.DELETE_FILE -> SpannableString(activity.getString(R.string.download_delete)).apply {
            setSpan(
                ForegroundColorSpan(activity.getColor(R.color.danger_text)),
                0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        DownloadOption.REMOVE_FROM_LIST -> activity.getString(R.string.download_remove)
        DownloadOption.COPY_LINK -> activity.getString(R.string.download_copy_link)
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "DownloadRunner"
    }
}
