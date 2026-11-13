package net.mrowser.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmDelayTest {

    @Test fun `a press right after the dialog appears is ignored`() {
        assertFalse(ConfirmDelay.accepts(shownAtMs = 1_000, nowMs = 1_000))
        assertFalse(ConfirmDelay.accepts(shownAtMs = 1_000, nowMs = 1_000 + ConfirmDelay.MS - 1))
    }

    @Test fun `a press once the delay has passed is taken`() {
        assertTrue(ConfirmDelay.accepts(shownAtMs = 1_000, nowMs = 1_000 + ConfirmDelay.MS))
        assertTrue(ConfirmDelay.accepts(shownAtMs = 1_000, nowMs = 60_000))
    }

    @Test fun `a dialog not yet shown takes no press`() =
        assertFalse(ConfirmDelay.accepts(shownAtMs = null, nowMs = 60_000))
}
