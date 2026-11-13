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

    @Test fun `progress reaching 100 while loading finishes the load`() =
        assertEquals(PageLoadState(false, 100, false), run(PageLoadEvent.Started, PageLoadEvent.Progress(100)))

    @Test fun `a progress-only line closes at 100`() =
        assertFalse(run(PageLoadEvent.Progress(30), PageLoadEvent.Progress(100)).loading)

    @Test fun `a progress tick after failure reopens the line with the ring off`() =
        assertEquals(PageLoadState(true, 30, false), run(PageLoadEvent.Started, PageLoadEvent.Failed, PageLoadEvent.Progress(30)))

    // A clicked link: shouldOverrideUrlLoading starts it, onPageStarted for the same URL comes at commit.
    @Test fun `a start for the load already running keeps its progress`() =
        assertEquals(
            PageLoadState(true, 40, true),
            run(PageLoadEvent.Started, PageLoadEvent.Progress(40), PageLoadEvent.StartedAgain),
        )

    @Test fun `a start for the load already running does not re-arm the ring after commit`() =
        assertEquals(
            PageLoadState(true, 60, false),
            run(PageLoadEvent.Started, PageLoadEvent.Progress(60), PageLoadEvent.Committed, PageLoadEvent.StartedAgain),
        )

    @Test fun `progress keeps moving after a start for the same load`() =
        assertEquals(
            75,
            run(PageLoadEvent.Started, PageLoadEvent.Progress(50), PageLoadEvent.StartedAgain, PageLoadEvent.Progress(75)).progress,
        )

    @Test fun `a start for the same url with nothing loading starts afresh`() {
        assertEquals(PageLoadState(true, 0, true), run(PageLoadEvent.StartedAgain))
        assertEquals(PageLoadState(true, 0, true), run(PageLoadEvent.Started, PageLoadEvent.Finished, PageLoadEvent.StartedAgain))
        assertEquals(PageLoadState(true, 0, true), run(PageLoadEvent.Started, PageLoadEvent.Failed, PageLoadEvent.StartedAgain))
    }
}
