package net.mrowser.download

import net.mrowser.download.DownloadOption.CANCEL
import net.mrowser.download.DownloadOption.COPY_LINK
import net.mrowser.download.DownloadOption.DELETE_FILE
import net.mrowser.download.DownloadOption.PRIMARY
import net.mrowser.download.DownloadOption.REMOVE_FROM_LIST
import net.mrowser.download.DownloadStatus.FAILED
import net.mrowser.download.DownloadStatus.PAUSED
import net.mrowser.download.DownloadStatus.PENDING
import net.mrowser.download.DownloadStatus.RUNNING
import net.mrowser.download.DownloadStatus.SUCCESSFUL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadActionsTest {

    @Test fun `kind from extension or mime, apk first`() {
        assertEquals(DownloadKind.APK, DownloadActions.kind("app.apk", "application/octet-stream"))
        assertEquals(DownloadKind.APK, DownloadActions.kind("download", DownloadActions.APK_MIME))
        assertEquals(DownloadKind.VIDEO, DownloadActions.kind("Solaris.1972.MKV", null))
        assertEquals(DownloadKind.VIDEO, DownloadActions.kind("clip", "video/webm"))
        assertEquals(DownloadKind.OTHER, DownloadActions.kind("notes.pdf", "application/pdf"))
    }

    @Test fun `the OK action follows state and kind`() {
        assertEquals(Primary.PLAY, DownloadActions.primary(info(1, SUCCESSFUL, name = "a.mp4"), false))
        assertEquals(Primary.INSTALL, DownloadActions.primary(info(1, SUCCESSFUL, name = "a.apk"), false))
        assertEquals(Primary.OPEN, DownloadActions.primary(info(1, SUCCESSFUL, name = "a.pdf"), false))
        assertEquals(Primary.RETRY, DownloadActions.primary(info(1, FAILED, name = "a.mp4"), false))
        listOf(RUNNING, PAUSED, PENDING).forEach { assertEquals(Primary.OPTIONS, DownloadActions.primary(info(1, it), false)) }
        assertEquals(Primary.OPTIONS, DownloadActions.primary(info(1, SUCCESSFUL, name = "a.mp4"), fileMissing = true))
    }

    @Test fun `options per state`() {
        assertEquals(listOf(CANCEL, COPY_LINK), DownloadActions.options(info(1, RUNNING), false))
        assertEquals(
            listOf(PRIMARY, DELETE_FILE, REMOVE_FROM_LIST, COPY_LINK),
            DownloadActions.options(info(1, SUCCESSFUL, name = "a.apk"), false),
        )
        assertEquals(listOf(PRIMARY, REMOVE_FROM_LIST, COPY_LINK), DownloadActions.options(info(1, FAILED), false))
        assertEquals(listOf(REMOVE_FROM_LIST, COPY_LINK), DownloadActions.options(info(1, SUCCESSFUL), fileMissing = true))
    }

    @Test fun `a failed row offers Retry even with no file`() {
        assertEquals(Primary.RETRY, DownloadActions.primary(info(1, FAILED), fileMissing = true))
        assertEquals(listOf(PRIMARY, REMOVE_FROM_LIST, COPY_LINK), DownloadActions.options(info(1, FAILED), fileMissing = true))
    }

    @Test fun `remove from list only hides`() =
        assertEquals(DownloadEffect.Hide(setOf(7L)), DownloadActions.effect(REMOVE_FROM_LIST, info(7, SUCCESSFUL)))

    @Test fun `clear finished hides finished and failed rows only`() = assertEquals(
        DownloadEffect.Hide(setOf(1L, 3L)),
        DownloadActions.clearFinished(listOf(info(1, SUCCESSFUL), info(2, RUNNING), info(3, FAILED), info(4, PAUSED))),
    )

    @Test fun `only cancel and delete file ever reach DownloadManager remove`() {
        for (s in DownloadStatus.values()) for (o in DownloadOption.values()) for (missing in listOf(false, true)) {
            val e = DownloadActions.effect(o, info(1, s), missing)
            if (e is DownloadEffect.RemoveFromManager) {
                assertTrue("$o on $s", o == CANCEL || o == DELETE_FILE)
                assertTrue("$o on $s", if (o == CANCEL) info(1, s).isActive else s == SUCCESSFUL)
            }
        }
        for (s in listOf(SUCCESSFUL, FAILED)) assertEquals(DownloadEffect.Hide(emptySet()), DownloadActions.effect(CANCEL, info(1, s)))
        for (s in listOf(RUNNING, PAUSED, PENDING, FAILED)) {
            assertEquals(DownloadEffect.Hide(emptySet()), DownloadActions.effect(DELETE_FILE, info(1, s)))
        }
        assertEquals(DownloadEffect.RemoveFromManager(2L), DownloadActions.effect(CANCEL, info(2, RUNNING)))
        assertEquals(DownloadEffect.RemoveFromManager(1L), DownloadActions.effect(DELETE_FILE, info(1, SUCCESSFUL)))
    }

    @Test fun `primary runs the OK action, copy link carries the url`() {
        assertEquals(DownloadEffect.Run(Primary.RETRY), DownloadActions.effect(PRIMARY, info(3, FAILED)))
        assertEquals(DownloadEffect.CopyLink("https://cdn.example/f1"), DownloadActions.effect(COPY_LINK, info(1, SUCCESSFUL)))
    }
}
