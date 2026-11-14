package net.mrowser.home

import net.mrowser.home.OverlayFocus.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayFocusTest {

    private fun target(
        settings: Boolean = false,
        history: Boolean = false,
        downloads: Boolean = false,
        home: Boolean = false,
        error: Boolean = false,
        focus: Zone
    ) = OverlayFocus.recoveryTarget(
        settingsVisible = settings,
        historyVisible = history,
        downloadsVisible = downloads,
        homeVisible = home,
        errorVisible = error,
        focus = focus
    )

    @Test fun `focus already inside the visible overlay needs no recovery`() {
        assertNull(target(home = true, focus = Zone.HOME))
        assertNull(target(history = true, focus = Zone.HISTORY))
        assertNull(target(settings = true, focus = Zone.SETTINGS))
    }

    @Test fun `focus on the page while browsing needs no recovery`() {
        assertNull(target(focus = Zone.PAGE))
    }

    @Test fun `lost focus is re-seated into the visible overlay`() {
        assertEquals(Zone.HOME, target(home = true, focus = Zone.NONE))
        assertEquals(Zone.HISTORY, target(history = true, focus = Zone.NONE))
        assertEquals(Zone.SETTINGS, target(settings = true, focus = Zone.NONE))
    }

    @Test fun `lost focus while browsing is re-seated on the page`() {
        assertEquals(Zone.PAGE, target(focus = Zone.NONE))
    }

    /**
     * Issue #32: rebuilding the favorites grid destroys the focused card, and the
     * window's fallback lands focus on the first focusable in the tree — CursorLayout,
     * behind the still-visible home overlay. Focus is not null, just in the wrong
     * subtree, so a null-only check never fires.
     */
    @Test fun `focus stranded on the page under a visible overlay is pulled back`() {
        assertEquals(Zone.HOME, target(home = true, focus = Zone.PAGE))
        assertEquals(Zone.HISTORY, target(history = true, focus = Zone.PAGE))
        assertEquals(Zone.SETTINGS, target(settings = true, focus = Zone.PAGE))
    }

    @Test fun `focus left in a hidden overlay is pulled into the visible one`() {
        assertEquals(Zone.HISTORY, target(history = true, focus = Zone.HOME))
        assertEquals(Zone.HOME, target(home = true, focus = Zone.SETTINGS))
    }

    /** Overlays are focus-modal, but keep a deterministic order if two are ever up. */
    @Test fun `settings outranks history and home`() {
        assertEquals(Zone.SETTINGS, target(settings = true, history = true, home = true, focus = Zone.PAGE))
        assertEquals(Zone.HISTORY, target(history = true, home = true, focus = Zone.PAGE))
    }

    @Test fun `focus left in an overlay after it closed returns to the page`() {
        assertEquals(Zone.PAGE, target(focus = Zone.HOME))
    }

    @Test fun `the error screen owns focus over the page`() =
        assertEquals(
            OverlayFocus.Zone.ERROR,
            OverlayFocus.recoveryTarget(
                settingsVisible = false, historyVisible = false, downloadsVisible = false,
                homeVisible = false, errorVisible = true, focus = OverlayFocus.Zone.PAGE
            )
        )

    @Test fun `home outranks the error screen`() =
        assertEquals(
            OverlayFocus.Zone.HOME,
            OverlayFocus.recoveryTarget(
                settingsVisible = false, historyVisible = false, downloadsVisible = false,
                homeVisible = true, errorVisible = true, focus = OverlayFocus.Zone.ERROR
            )
        )

    @Test fun `focus already on the error screen needs no recovery`() {
        assertNull(target(error = true, focus = Zone.ERROR))
    }

    @Test fun `focus left on the error screen after it closed returns to the page`() {
        assertEquals(Zone.PAGE, target(focus = Zone.ERROR))
    }

    @Test fun `settings and history outrank the error screen`() {
        assertEquals(Zone.SETTINGS, target(settings = true, error = true, focus = Zone.ERROR))
        assertEquals(Zone.HISTORY, target(history = true, error = true, focus = Zone.ERROR))
    }

    @Test fun `the downloads page owns focus while it is up`() =
        assertEquals(Zone.DOWNLOADS, target(downloads = true, focus = Zone.PAGE))

    @Test fun `focus already in downloads needs no recovery`() =
        assertNull(target(downloads = true, focus = Zone.DOWNLOADS))

    @Test fun `downloads ranks below settings and history, above home and the error screen`() {
        assertEquals(Zone.HISTORY, target(history = true, downloads = true, focus = Zone.DOWNLOADS))
        assertEquals(Zone.DOWNLOADS, target(downloads = true, home = true, error = true, focus = Zone.HOME))
    }
}
