package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesOpsTest {

    private val a = Favorite("A", "https://a.net")
    private val b = Favorite("B", "https://b.net")

    @Test fun `add puts a new favorite at the front`() {
        assertEquals(listOf(b, a), FavoritesOps.add(listOf(a), b))
    }

    @Test fun `add upserts by url without duplicating`() {
        val a2 = Favorite("A renamed", "https://a.net")
        assertEquals(listOf(a2), FavoritesOps.add(listOf(a), a2))
    }

    @Test fun `remove drops the matching url`() {
        assertEquals(listOf(a), FavoritesOps.remove(listOf(a, b), "https://b.net"))
    }

    @Test fun `update replaces the entry at the old url in place`() {
        val b2 = Favorite("B2", "https://b2.net")
        assertEquals(listOf(a, b2), FavoritesOps.update(listOf(a, b), "https://b.net", b2))
    }

    @Test fun `update is a no-op when the old url is absent`() {
        assertEquals(listOf(a), FavoritesOps.update(listOf(a), "https://x.net", b))
    }
}
