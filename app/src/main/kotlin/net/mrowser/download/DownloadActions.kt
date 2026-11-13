package net.mrowser.download

enum class DownloadKind { VIDEO, APK, OTHER }

/** What OK does on a row. */
enum class Primary { PLAY, INSTALL, OPEN, RETRY, OPTIONS }

enum class DownloadOption { PRIMARY, CANCEL, DELETE_FILE, REMOVE_FROM_LIST, COPY_LINK }

sealed interface DownloadEffect {
    /** Hide these ids from the list; the file and the manager entry stay. */
    data class Hide(val ids: Set<Long>) : DownloadEffect

    /** `DownloadManager.remove`, which also deletes the file. Only Cancel and Delete file produce this. */
    data class RemoveFromManager(val id: Long) : DownloadEffect

    data class Run(val primary: Primary) : DownloadEffect

    data class CopyLink(val url: String) : DownloadEffect
}

/** Row semantics of the download list. Pure. */
object DownloadActions {
    const val APK_MIME = "application/vnd.android.package-archive"

    private val NO_OP = DownloadEffect.Hide(emptySet())

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "ts", "m2ts", "3gp", "flv", "avi", "mpg", "mpeg",
    )

    fun kind(name: String, mimeType: String?): DownloadKind {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime == APK_MIME || ext == "apk" -> DownloadKind.APK
            mime.startsWith("video/") || ext in VIDEO_EXTENSIONS -> DownloadKind.VIDEO
            else -> DownloadKind.OTHER
        }
    }

    fun primary(info: DownloadInfo, fileMissing: Boolean): Primary = when {
        info.status == DownloadStatus.FAILED -> Primary.RETRY
        info.status != DownloadStatus.SUCCESSFUL || fileMissing -> Primary.OPTIONS
        else -> when (kind(info.name, info.mimeType)) {
            DownloadKind.VIDEO -> Primary.PLAY
            DownloadKind.APK -> Primary.INSTALL
            DownloadKind.OTHER -> Primary.OPEN
        }
    }

    fun options(info: DownloadInfo, fileMissing: Boolean): List<DownloadOption> {
        val finished = info.status == DownloadStatus.SUCCESSFUL
        val failed = info.status == DownloadStatus.FAILED
        return buildList {
            // A failed download usually leaves no file, so Retry must not depend on one.
            if (failed || (finished && !fileMissing)) add(DownloadOption.PRIMARY)
            if (info.isActive) add(DownloadOption.CANCEL)
            if (finished && !fileMissing) add(DownloadOption.DELETE_FILE)
            if (!info.isActive) add(DownloadOption.REMOVE_FROM_LIST)
            add(DownloadOption.COPY_LINK)
        }
    }

    fun effect(option: DownloadOption, info: DownloadInfo, fileMissing: Boolean = false): DownloadEffect =
        when (option) {
            DownloadOption.PRIMARY -> DownloadEffect.Run(primary(info, fileMissing))
            // remove() deletes the file, so a row can only be removed by the option that fits its state.
            DownloadOption.CANCEL ->
                if (info.isActive) DownloadEffect.RemoveFromManager(info.id) else NO_OP
            DownloadOption.DELETE_FILE ->
                if (info.status == DownloadStatus.SUCCESSFUL) DownloadEffect.RemoveFromManager(info.id) else NO_OP
            DownloadOption.REMOVE_FROM_LIST -> DownloadEffect.Hide(setOf(info.id))
            DownloadOption.COPY_LINK -> DownloadEffect.CopyLink(info.url)
        }

    /** Hides every finished or failed row; active ones stay. */
    fun clearFinished(visible: List<DownloadInfo>): DownloadEffect.Hide =
        DownloadEffect.Hide(visible.filter { !it.isActive }.map { it.id }.toSet())
}
