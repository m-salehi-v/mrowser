package net.mrowser.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    @Test fun `under a minute is just now`() {
        assertEquals("just now", RelativeTime.format(0))
        assertEquals("just now", RelativeTime.format(59_000))
    }

    @Test fun `minutes`() {
        assertEquals("1m ago", RelativeTime.format(60_000))
        assertEquals("59m ago", RelativeTime.format(59 * 60_000L))
    }

    @Test fun `hours`() {
        assertEquals("1h ago", RelativeTime.format(60 * 60_000L))
        assertEquals("23h ago", RelativeTime.format(23 * 60 * 60_000L))
    }

    @Test fun `days`() {
        assertEquals("1d ago", RelativeTime.format(24 * 60 * 60_000L))
        assertEquals("7d ago", RelativeTime.format(7 * 24 * 60 * 60_000L))
    }

    @Test fun `negative clock skew is just now`() {
        assertEquals("just now", RelativeTime.format(-5_000))
    }
}
