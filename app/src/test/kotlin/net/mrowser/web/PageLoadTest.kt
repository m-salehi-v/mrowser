package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PageLoadTest {
    private fun run(vararg e: PageLoadEvent) = e.fold(PageLoadState()) { s, ev -> PageLoad.reduce(s, ev) }

    @Test fun `a click starts both signals at once`() =
        assertEquals(PageLoadState(true, 0, true), run(PageLoadEvent.Started))

    @Test fun `progress only moves forward and is clamped`() {
        assertEquals(60, run(PageLoadEvent.Started, PageLoadEvent.Progress(60), PageLoadEvent.Progress(40)).progress)
        assertEquals(100, run(PageLoadEvent.Started, PageLoadEvent.Progress(250)).progress)
    }

    @Test fun `the cursor stops spinning when the page first shows`() =
        assertEquals(
            PageLoadState(true, 70, false),
            run(PageLoadEvent.Started, PageLoadEvent.Progress(70), PageLoadEvent.Committed),
        )

    @Test fun `finish ends everything`() =
        assertEquals(PageLoadState(false, 100, false), run(PageLoadEvent.Started, PageLoadEvent.Finished))

    @Test fun `failure and timeout end everything`() {
        assertEquals(PageLoadState(), run(PageLoadEvent.Started, PageLoadEvent.Failed))
        assertEquals(PageLoadState(), run(PageLoadEvent.Started, PageLoadEvent.TimedOut))
    }

    @Test fun `a second click mid-load restarts cleanly`() =
        assertEquals(
            PageLoadState(true, 0, true),
            run(PageLoadEvent.Started, PageLoadEvent.Progress(80), PageLoadEvent.Committed, PageLoadEvent.Started),
        )

    @Test fun `a late progress tick after finish does not restart the cursor`() =
        assertFalse(run(PageLoadEvent.Started, PageLoadEvent.Finished, PageLoadEvent.Progress(30)).cursorBusy)

    @Test fun `progress we did not see start (back, forward) shows the line only`() =
        assertEquals(PageLoadState(true, 30, false), run(PageLoadEvent.Progress(30)))

    @Test fun `progress 100 alone does not open the line`() =
        assertFalse(run(PageLoadEvent.Progress(100)).loading)

    // Extra pins for the "never stay on screen" rule.
    @Test fun `a late progress tick after finish does not reopen the line`() =
        assertFalse(run(PageLoadEvent.Started, PageLoadEvent.Finished, PageLoadEvent.Progress(30)).loading)

    @Test fun `committed with nothing loading changes nothing`() =
        assertEquals(PageLoadState(), run(PageLoadEvent.Committed))

    @Test fun `timeout after commit ends the line too`() =
        assertEquals(
            PageLoadState(),
            run(PageLoadEvent.Started, PageLoadEvent.Progress(50), PageLoadEvent.Committed, PageLoadEvent.TimedOut),
        )

    @Test fun `a click after finish starts again`() =
        assertEquals(PageLoadState(true, 0, true), run(PageLoadEvent.Started, PageLoadEvent.Finished, PageLoadEvent.Started))
}
