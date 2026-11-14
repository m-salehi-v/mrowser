package net.mrowser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadSpeedTest {

    @Test fun `needs two samples`() {
        val s = DownloadSpeed()
        assertNull(s.sample(1, 0, 0))
        assertEquals(1_000_000L, s.sample(1, 1_000_000, 1_000))
    }

    @Test fun `smooths later samples`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0); s.sample(1, 1_000_000, 1_000)
        assertEquals(1_300_000L, s.sample(1, 3_000_000, 2_000))
    }

    @Test fun `no time passed changes nothing`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0); s.sample(1, 1_000_000, 1_000)
        assertEquals(1_000_000L, s.sample(1, 1_500_000, 1_000))
    }

    @Test fun `bytes going backwards start over`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0); s.sample(1, 1_000_000, 1_000)
        assertNull(s.sample(1, 10, 2_000))
        assertEquals(990L, s.sample(1, 1_000, 3_000))
    }

    @Test fun `forgotten ids start over, kept ones do not`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0); s.sample(2, 0, 0)
        s.forget(keep = setOf(2L))
        assertNull(s.sample(1, 2_000_000, 2_000))
        assertEquals(500L, s.sample(2, 1_000, 2_000))
    }

    @Test fun `a stall decays the speed toward zero without going negative`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0); s.sample(1, 1_000_000, 1_000)
        assertEquals(700_000L, s.sample(1, 1_000_000, 2_000))
    }

    @Test fun `a long stall ends with no speed at all, and recovery brings it back`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0); s.sample(1, 1_000_000, 1_000)
        var last: Long? = 1L
        for (i in 2..100) last = s.sample(1, 1_000_000, i * 1_000L)
        assertNull(last)
        // downloading again: 0.3 * 2 MB/s is above the floor
        assertEquals(true, (s.sample(1, 3_000_000, 101_000L) ?: 0L) > 0L)
    }

    @Test fun `no time passed during a stall still reports no speed`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0); s.sample(1, 1_000_000, 1_000)
        for (i in 2..100) s.sample(1, 1_000_000, i * 1_000L)
        assertNull(s.sample(1, 1_000_000, 100_000L))
    }

    @Test fun `an unknown byte count is not a sample and not a baseline`() {
        val s = DownloadSpeed(); s.sample(1, 0, 0)
        assertNull(s.sample(1, -1, 500))
        assertEquals(1_000_000L, s.sample(1, 1_000_000, 1_000))
        assertEquals(1_000_000L, s.sample(1, -1, 2_000))
        assertNull(DownloadSpeed().sample(7, -1, 0))
        val fresh = DownloadSpeed(); fresh.sample(7, -1, 0)
        assertNull(fresh.sample(7, 0, 100))
        assertEquals(1_000L, fresh.sample(7, 100, 200))
    }
}
