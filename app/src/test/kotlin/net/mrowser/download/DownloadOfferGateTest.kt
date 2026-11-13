package net.mrowser.download

import net.mrowser.download.DownloadOfferGate.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOfferGateTest {

    private fun ticketOf(v: Verdict): Long = (v as Verdict.Offer).ticket

    @Test fun `offers are shown until the user declines one`() {
        val gate = DownloadOfferGate()
        assertTrue(gate.offer() is Verdict.Offer)
        assertTrue(gate.offer() is Verdict.Offer)
    }

    @Test fun `after a decline the same page's offers are blocked`() {
        val gate = DownloadOfferGate()
        gate.declined(ticketOf(gate.offer()))
        assertTrue(gate.offer() is Verdict.Blocked)
        assertTrue(gate.offer() is Verdict.Blocked)
    }

    @Test fun `only the first blocked offer asks for the notice`() {
        val gate = DownloadOfferGate()
        gate.declined(ticketOf(gate.offer()))
        assertEquals(Verdict.Blocked(notify = true), gate.offer())
        assertEquals(Verdict.Blocked(notify = false), gate.offer())
        assertEquals(Verdict.Blocked(notify = false), gate.offer())
    }

    @Test fun `a new page start lets offers through again`() {
        val gate = DownloadOfferGate()
        gate.declined(ticketOf(gate.offer()))
        gate.offer()
        gate.reset()
        assertTrue(gate.offer() is Verdict.Offer)
    }

    @Test fun `a user click lets offers through again and re-arms the notice`() {
        val gate = DownloadOfferGate()
        gate.declined(ticketOf(gate.offer()))
        assertEquals(Verdict.Blocked(notify = true), gate.offer())
        gate.reset() // the user's click
        gate.declined(ticketOf(gate.offer()))
        assertEquals(Verdict.Blocked(notify = true), gate.offer())
    }

    @Test fun `a decline of an offer from before the last reset changes nothing`() {
        val gate = DownloadOfferGate()
        val stale = ticketOf(gate.offer())
        gate.reset() // the page moved on while the dialog was open
        gate.declined(stale)
        assertTrue(gate.offer() is Verdict.Offer)
    }

    @Test fun `confirming is not declining`() {
        val gate = DownloadOfferGate()
        gate.offer() // the user pressed Download: no declined()
        assertTrue(gate.offer() is Verdict.Offer)
    }
}
