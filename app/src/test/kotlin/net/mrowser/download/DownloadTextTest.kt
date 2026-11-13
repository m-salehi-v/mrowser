package net.mrowser.download

import net.mrowser.download.DownloadStatus.FAILED
import net.mrowser.download.DownloadStatus.PAUSED
import net.mrowser.download.DownloadStatus.PENDING
import net.mrowser.download.DownloadStatus.RUNNING
import net.mrowser.download.DownloadStatus.SUCCESSFUL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTextTest {

    private val now = 30L * 86_400_000L
    private fun detailOf(i: DownloadInfo) = DownloadText.detail(i, "site.example", null, false, now)

    @Test fun `progress with size, speed and time left`() = assertEquals(
        "42% · 1.0 GB of 2.4 GB · 3.1 MB/s · about 8 min left",
        DownloadText.progress(1_008_000_000, 2_400_000_000, 3_100_000))

    @Test fun `no speed yet`() =
        assertEquals("10% · 100.0 MB of 1.0 GB", DownloadText.progress(100_000_000, 1_000_000_000, null))

    @Test fun `a stalled download shows no time left`() =
        assertEquals("50% · 500.0 MB of 1.0 GB", DownloadText.progress(500_000_000, 1_000_000_000, 0))

    @Test fun `unknown size`() = assertEquals("37.0 MB · 2.0 MB/s", DownloadText.progress(37_000_000, -1, 2_000_000))

    @Test fun `nothing arrived yet`() {
        assertEquals("Starting…", DownloadText.progress(0, -1, null))
        assertEquals("Starting…", DownloadText.progress(0, 1_000_000_000, 5_000))
        assertEquals("Starting…", DownloadText.progress(-1, -1, null))
    }

    @Test fun `more bytes than announced never passes 100 percent or counts time left`() {
        assertEquals("1.2 GB · 1.0 MB/s", DownloadText.progress(1_200_000_000, 1_000_000_000, 1_000_000))
    }

    @Test fun `a negative speed is ignored`() =
        assertEquals("50% · 500.0 MB of 1.0 GB", DownloadText.progress(500_000_000, 1_000_000_000, -5))

    @Test fun `time left`() {
        assertEquals("less than a minute left", DownloadText.timeLeft(50_000_000, 1_000_000))
        assertEquals("about 1 min left", DownloadText.timeLeft(60_000_000, 1_000_000))
        assertEquals("about 1 h 5 min left", DownloadText.timeLeft(3_900_000_000, 1_000_000))
        assertEquals("about 2 h left", DownloadText.timeLeft(7_200_000_000, 1_000_000))
        assertNull(DownloadText.timeLeft(1_000, 0))
        assertNull(DownloadText.timeLeft(1_000, -3))
    }

    @Test fun `finished line`() = assertEquals("276.0 MB · site.example · 1d ago",
        detailOf(info(1, SUCCESSFUL, total = 276_000_000, modified = now - 86_400_000)))

    @Test fun `finished line leaves out what is unknown`() {
        assertEquals("5.0 MB · 1d ago", DownloadText.detail(
            info(1, SUCCESSFUL, soFar = 5_000_000, total = -1, modified = now - 86_400_000), null, null, false, now))
        assertEquals("site.example", DownloadText.detail(info(1, SUCCESSFUL), "site.example", null, false, now))
    }

    @Test fun `failed lines name the reason`() {
        assertEquals("Failed · not enough space", detailOf(info(1, FAILED, reason = 1006)))
        assertEquals("Failed · the server answered 404", detailOf(info(1, FAILED, reason = 404)))
        assertEquals("Failed · something went wrong", detailOf(info(1, FAILED, reason = 0)))
    }

    @Test fun `every known failure reason has its text`() {
        assertEquals("couldn't save the file", DownloadText.failure(1001))
        assertEquals("the server refused it", DownloadText.failure(1002))
        assertEquals("the connection broke", DownloadText.failure(1004))
        assertEquals("too many redirects", DownloadText.failure(1005))
        assertEquals("no storage found", DownloadText.failure(1007))
        assertEquals("it couldn't resume", DownloadText.failure(1008))
        assertEquals("the file already exists", DownloadText.failure(1009))
        assertEquals("the server answered 500", DownloadText.failure(500))
        assertEquals("something went wrong", DownloadText.failure(399))
        assertEquals("something went wrong", DownloadText.failure(600))
    }

    @Test fun `waiting lines`() {
        assertEquals("Waiting for network", detailOf(info(1, PAUSED, reason = 2)))
        assertEquals("Waiting for network", detailOf(info(1, PAUSED, reason = 3)))
        assertEquals("Waiting to retry", detailOf(info(1, PAUSED, reason = 1)))
        assertEquals("Paused", detailOf(info(1, PAUSED, reason = 4)))
        assertEquals("Waiting to start", detailOf(info(1, PENDING)))
    }

    @Test fun `a running line is the progress`() = assertEquals("42% · 420.0 MB of 1.0 GB",
        DownloadText.detail(info(1, RUNNING, soFar = 420_000_000, total = 1_000_000_000), null, null, false, now))

    @Test fun `a file deleted elsewhere`() = assertEquals("File removed",
        DownloadText.detail(info(1, SUCCESSFUL, total = 5_000_000), "site.example", null, true, now))

    @Test fun `the from host is the page's, else the file's, without www`() {
        assertEquals("site.example", DownloadText.sourceHost("https://www.site.example/p", "https://cdn.other/x"))
        assertEquals("cdn.other", DownloadText.sourceHost(null, "https://cdn.other/x"))
        assertEquals("cdn.other", DownloadText.sourceHost("about:blank", "https://cdn.other/x"))
        assertEquals("cdn.other", DownloadText.sourceHost(null, "https://www.cdn.other/x"))
        assertNull(DownloadText.sourceHost(null, "not a url"))
    }

    @Test fun `chip text`() {
        assertEquals(ChipText("a.mp4", "· 42%"), DownloadText.chip(DownloadSummary(1, 42, "a.mp4")))
        assertEquals(ChipText("2 downloads", "· 37%"), DownloadText.chip(DownloadSummary(2, 37, null)))
        assertEquals(ChipText("2 downloads", null), DownloadText.chip(DownloadSummary(2, null, null)))
        assertNull(DownloadText.chip(DownloadSummary(0, null, null)))
        assertEquals(ChipText("Downloaded · a.mp4", null), DownloadText.chipDone("a.mp4"))
    }
}
