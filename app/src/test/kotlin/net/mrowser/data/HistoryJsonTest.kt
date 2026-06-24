package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryJsonTest {

    @Test fun `round trips a list`() {
        val items = listOf(
            HistoryEntry("A", "https://a.net", 1000L),
            HistoryEntry("B", "https://b.net", 2000L)
        )
        assertEquals(items, HistoryJson.fromJson(HistoryJson.toJson(items)))
    }

    @Test fun `empty list round trips`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryJson.fromJson(HistoryJson.toJson(emptyList())))
    }

    @Test fun `blank input is an empty list`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryJson.fromJson("   "))
    }

    @Test fun `corrupt input is an empty list`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryJson.fromJson("not json"))
    }
}
