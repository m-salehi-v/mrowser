package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritesBackupTest {

    private val a = Favorite("A", "https://a.net")
    private val b = Favorite("B", "http://b.net/path?q=1")

    @Test fun `export round trips through parse`() {
        assertEquals(listOf(a, b), FavoritesBackup.parse(FavoritesBackup.toJson(listOf(a, b)))?.favorites)
    }

    @Test fun `export is wrapped so a wrong file is detectable`() {
        val json = org.json.JSONObject(FavoritesBackup.toJson(listOf(a)))
        assertEquals("mrowser", json.getString("app"))
        assertEquals(1, json.getInt("version"))
        assertEquals(1, json.getJSONArray("favorites").length())
    }

    @Test fun `an empty export round trips to an empty list`() {
        assertEquals(emptyList<Favorite>(), FavoritesBackup.parse(FavoritesBackup.toJson(emptyList()))?.favorites)
    }

    @Test fun `a bare array, the app's own favorites json, is accepted`() {
        assertEquals(listOf(a, b), FavoritesBackup.parse(FavoritesJson.toJson(listOf(a, b)))?.favorites)
    }

    @Test fun `a later version is still read for its favorites`() {
        val json = """{"app":"mrowser","version":2,"favorites":[{"title":"A","url":"https://a.net","icon":"x"}]}"""
        assertEquals(listOf(a), FavoritesBackup.parse(json)?.favorites)
    }

    @Test fun `not json is not a favorites file`() {
        assertNull(FavoritesBackup.parse("not json"))
    }

    @Test fun `blank is not a favorites file`() {
        assertNull(FavoritesBackup.parse("  \n"))
    }

    @Test fun `truncated json is not a favorites file`() {
        assertNull(FavoritesBackup.parse("""{"app":"mrowser","version":1,"favorites":[{"title":"A","""))
    }

    @Test fun `another app's json object is not a favorites file`() {
        assertNull(FavoritesBackup.parse("""{"app":"other","favorites":[{"title":"A","url":"https://a.net"}]}"""))
        assertNull(FavoritesBackup.parse("""{"name":"package.json"}"""))
    }

    @Test fun `an envelope without a favorites array is not a favorites file`() {
        assertNull(FavoritesBackup.parse("""{"app":"mrowser","version":1}"""))
        assertNull(FavoritesBackup.parse("""{"app":"mrowser","favorites":"nope"}"""))
    }

    @Test fun `a json scalar is not a favorites file`() {
        assertNull(FavoritesBackup.parse("42"))
        assertNull(FavoritesBackup.parse("\"text\""))
    }

    @Test fun `entries that are not web favorites are skipped, the rest kept`() {
        val json = """[
            {"title":"A","url":"https://a.net"},
            {"title":"no url"},
            {"title":"script","url":"javascript:alert(1)"},
            {"title":"private file","url":"file:///data/data/net.mrowser/files/settings.json"},
            {"title":"content","url":"content://net.mrowser.provider/x"},
            {"title":"intent","url":"intent://scan#Intent;scheme=zxing;end"},
            {"title":"data","url":"data:text/html,<script>alert(1)</script>"},
            {"title":"upper-case script","url":"JAVASCRIPT:alert(1)"},
            {"title":"number url","url":5},
            "a string",
            null,
            {"title":"B","url":"http://b.net/path?q=1"}
        ]"""
        assertEquals(listOf(a, b), FavoritesBackup.parse(json)?.favorites)
    }

    @Test fun `dropped entries are counted`() {
        val json = """[{"title":"A","url":"https://a.net"},{"url":"intent://x#Intent;end"},{"url":"content://x/y"},7]"""
        assertEquals(FavoritesBackup.Parsed(listOf(a), skipped = 3), FavoritesBackup.parse(json))
    }

    @Test fun `a clean file skips nothing`() {
        assertEquals(0, FavoritesBackup.parse(FavoritesBackup.toJson(listOf(a, b)))?.skipped)
    }

    @Test fun `a missing or blank title falls back to the url`() {
        val json = """[{"url":"https://a.net"},{"title":"  ","url":"https://b.net"}]"""
        assertEquals(
            listOf(Favorite("https://a.net", "https://a.net"), Favorite("https://b.net", "https://b.net")),
            FavoritesBackup.parse(json)?.favorites
        )
    }

    @Test fun `urls are trimmed`() {
        assertEquals(listOf(a), FavoritesBackup.parse("""[{"title":"A","url":"  https://a.net "}]""")?.favorites)
    }

    @Test fun `summary counts new and already present favorites`() {
        val c = Favorite("C", "https://c.net")
        assertEquals(
            FavoritesBackup.Summary(added = 2, alreadyPresent = 1),
            FavoritesBackup.summarize(existing = listOf(a), imported = listOf(Favorite("A2", a.url), b, c))
        )
    }

    @Test fun `summary counts a url repeated in the file once`() {
        assertEquals(
            FavoritesBackup.Summary(added = 1, alreadyPresent = 1),
            FavoritesBackup.summarize(existing = listOf(a), imported = listOf(b, b, a, a))
        )
    }

    @Test fun `parse falls through to bookmarks html`() =
        assertEquals(FavoritesBackup.Source.BOOKMARKS_HTML, FavoritesBackup.parse(BookmarksFixtures.chrome)!!.source)

    @Test fun `a random html page is still not a favorites file`() =
        assertNull(FavoritesBackup.parse("<html><body>hello</body></html>"))

    @Test fun `a json file is from mrowser`() =
        assertEquals(FavoritesBackup.Source.MROWSER, FavoritesBackup.parse(FavoritesBackup.toJson(listOf(a)))!!.source)
}
