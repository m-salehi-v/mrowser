package net.mrowser.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmDelayTest {

    @Test fun `a press right after the dialog appears is ignored`() {
        assertFalse(ConfirmDelay.accepts(armedAtMs = 1_000, nowMs = 1_000))
        assertFalse(ConfirmDelay.accepts(armedAtMs = 1_000, nowMs = 1_000 + ConfirmDelay.MS - 1))
    }

    @Test fun `a press once the delay has passed is taken`() {
        assertTrue(ConfirmDelay.accepts(armedAtMs = 1_000, nowMs = 1_000 + ConfirmDelay.MS))
        assertTrue(ConfirmDelay.accepts(armedAtMs = 1_000, nowMs = 60_000))
    }

    @Test fun `a dialog without window focus takes no press`() =
        assertFalse(ConfirmDelay.accepts(armedAtMs = null, nowMs = 60_000))

    @Test fun `regaining focus restarts the delay`() {
        // Armed long ago, then the player took focus and gave it back at 60_000.
        assertTrue(ConfirmDelay.accepts(armedAtMs = 1_000, nowMs = 60_000))
        assertFalse(ConfirmDelay.accepts(armedAtMs = 60_000, nowMs = 60_000 + ConfirmDelay.MS - 1))
    }
}
