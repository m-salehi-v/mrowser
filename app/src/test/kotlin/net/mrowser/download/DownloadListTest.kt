package net.mrowser.download

import net.mrowser.download.DownloadStatus.FAILED
import net.mrowser.download.DownloadStatus.PAUSED
import net.mrowser.download.DownloadStatus.PENDING
import net.mrowser.download.DownloadStatus.RUNNING
import net.mrowser.download.DownloadStatus.SUCCESSFUL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadListTest {

    @Test fun `active first, newest first in each group, hidden ones gone`() {
        val all = listOf(info(1, SUCCESSFUL), info(2, RUNNING), info(3, FAILED), info(4, PAUSED), info(5, SUCCESSFUL), info(6, PENDING))
        assertEquals(listOf(6L, 4L, 2L, 5L, 1L), DownloadList.visible(all, hidden = setOf(3L)).map { it.id })
    }

    @Test fun `one running download is named with its percent`() = assertEquals(
        DownloadSummary(active = 1, percent = 42, name = "a.mp4"),
        DownloadList.summary(listOf(info(1, RUNNING, name = "a.mp4", soFar = 420, total = 1000), info(2, SUCCESSFUL))),
    )

    @Test fun `several add up the sizes they know`() = assertEquals(
        DownloadSummary(active = 3, percent = 20, name = null),
        DownloadList.summary(
            listOf(
                info(1, RUNNING, soFar = 100, total = 1000), info(2, RUNNING, soFar = 270),
                info(3, PAUSED, soFar = 300, total = 1000),
            ),
        ),
    )

    @Test fun `no known size, no percent`() =
        assertNull(DownloadList.summary(listOf(info(1, RUNNING, soFar = 5))).percent)

    @Test fun `nothing running`() =
        assertEquals(DownloadSummary(0, null, null), DownloadList.summary(listOf(info(1, SUCCESSFUL))))

    @Test fun `percent is floored and clamped`() {
        assertEquals(99, DownloadList.percent(999, 1000))
        assertEquals(100, DownloadList.percent(1500, 1000))
        assertEquals(0, DownloadList.percent(-5, 1000))
        assertNull(DownloadList.percent(5, -1))
        assertNull(DownloadList.percent(5, 0))
    }

    @Test fun `more bytes than announced never passes 100`() = assertEquals(
        100, DownloadList.summary(listOf(info(1, RUNNING, soFar = 5000, total = 1000))).percent,
    )

    @Test fun `a huge size does not overflow the percent`() =
        assertEquals(49, DownloadList.percent(Long.MAX_VALUE / 2, Long.MAX_VALUE))

    @Test fun `just finished are the ones active at the last poll that succeeded`() = assertEquals(
        listOf(1L),
        DownloadList.justFinished(setOf(1L, 3L), listOf(info(1, SUCCESSFUL), info(2, SUCCESSFUL), info(3, FAILED))).map { it.id },
    )
}
