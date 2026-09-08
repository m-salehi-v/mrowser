package net.mrowser.web

import net.mrowser.web.PopupPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupPolicyTest {

    @Test fun `a window the user asked for opens in the window they are already in`() {
        assertEquals(
            Decision.OpenInCurrentWindow,
            PopupPolicy.decide(isUserGesture = true, blockingEnabled = true)
        )
    }

    @Test fun `a window the page opened by itself is blocked`() {
        assertEquals(
            Decision.Block,
            PopupPolicy.decide(isUserGesture = false, blockingEnabled = true)
        )
    }

    @Test fun `with blocking off the page gets the window it asked for`() {
        assertEquals(
            Decision.AllowNewWindow,
            PopupPolicy.decide(isUserGesture = false, blockingEnabled = false)
        )
    }

    @Test fun `with blocking off a user-opened window is still left to the page`() {
        assertEquals(
            Decision.AllowNewWindow,
            PopupPolicy.decide(isUserGesture = true, blockingEnabled = false)
        )
    }
}
