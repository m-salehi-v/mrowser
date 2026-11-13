package net.mrowser.download

import net.mrowser.download.DownloadStatus.RUNNING
import net.mrowser.download.DownloadStatus.SUCCESSFUL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRowsTest {

    private val now = 30L * 86_400_000L

    @Test fun `a finished video plays`() {
        val i = info(1, SUCCESSFUL, name = "a.mp4", total = 276_000_000, modified = now - 86_400_000)
        assertEquals(DownloadRowModel(i, DownloadKind.VIDEO, "276.0 MB · site.example · 1d ago", null, Primary.PLAY),
            DownloadRows.build(i, "https://www.site.example/p", null, false, now))
    }

    @Test fun `a running download shows its bar and has no action`() {
        val r = DownloadRows.build(info(2, RUNNING, name = "a.apk", soFar = 420_000_000, total = 1_000_000_000), null, null, false, now)
        assertEquals(42, r.progress); assertEquals(Primary.OPTIONS, r.primary); assertEquals(DownloadKind.APK, r.kind)
        assertEquals("42% · 420.0 MB of 1.0 GB", r.detail)
    }

    @Test fun `a running download of unknown size has no bar`() {
        val r = DownloadRows.build(info(2, RUNNING, soFar = 1_000, total = -1), null, null, false, now)
        assertNull(r.progress)
    }

    @Test fun `more bytes than announced never shows more than 100`() {
        val r = DownloadRows.build(info(2, RUNNING, soFar = 2_000_000, total = 1_000_000), null, 1_000, false, now)
        assertEquals(100, r.progress)
    }

    @Test fun `a removed file says so and has no action`() {
        val r = DownloadRows.build(info(3, SUCCESSFUL, name = "a.mp4", total = 5_000_000), null, null, true, now)
        assertEquals("File removed", r.detail); assertEquals(Primary.OPTIONS, r.primary); assertNull(r.progress); assertTrue(r.fileMissing)
    }
}
