package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesJsonTest {

    @Test fun `round trips a list`() {
        val items = listOf(Favorite("A", "https://a.net"), Favorite("B", "https://b.net"))
        assertEquals(items, FavoritesJson.fromJson(FavoritesJson.toJson(items)))
    }

    @Test fun `empty list round trips`() {
        assertEquals(emptyList<Favorite>(), FavoritesJson.fromJson(FavoritesJson.toJson(emptyList())))
    }

    @Test fun `blank input is an empty list`() {
        assertEquals(emptyList<Favorite>(), FavoritesJson.fromJson("   "))
    }

    @Test fun `corrupt input is an empty list`() {
        assertEquals(emptyList<Favorite>(), FavoritesJson.fromJson("not json"))
    }
}
