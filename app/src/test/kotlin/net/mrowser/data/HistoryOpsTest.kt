package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryOpsTest {

    private fun e(url: String, t: Long, title: String = url) = HistoryEntry(title, url, t)

    @Test fun `record puts a new entry at the front`() {
        val a = e("https://a.net", 1)
        val b = e("https://b.net", 2)
        assertEquals(listOf(b, a), HistoryOps.record(listOf(a), b))
    }

    @Test fun `record dedups by url and bumps to the front with the new entry`() {
        val a = e("https://a.net", 1)
        val b = e("https://b.net", 2)
        val a2 = e("https://a.net", 3, title = "A again")
        assertEquals(listOf(a2, b), HistoryOps.record(listOf(b, a), a2))
    }

    @Test fun `record caps at the newest 50, dropping the oldest at the tail`() {
        // List is maintained newest-first; s1 at front, s50 at the back (oldest).
        val seed = (1..50).map { e("https://s$it.net", (100 - it).toLong()) }
        val fresh = e("https://new.net", 200)
        val result = HistoryOps.record(seed, fresh, cap = 50)
        assertEquals(50, result.size)
        assertEquals(fresh, result.first())              // newest at the front
        assertEquals("https://s49.net", result.last().url) // s50 (oldest, tail) dropped
    }

    @Test fun `clear empties the list`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryOps.clear())
    }
}
