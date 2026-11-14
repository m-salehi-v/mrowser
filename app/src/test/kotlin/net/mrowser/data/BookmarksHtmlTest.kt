package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksHtmlTest {

    private val chrome = BookmarksFixtures.chrome

    @Test fun `recognises an export`() {
        assertTrue(BookmarksHtml.looksLike(chrome))
        assertTrue(BookmarksHtml.looksLike("<dl><dt><a href=\"https://a.example\">a</a></dl>"))
        assertFalse(BookmarksHtml.looksLike("<html><body><a href=\"https://a.example\">a</a></body></html>"))
    }

    @Test fun `flattens folders in file order and keeps only web links`() {
        val p = BookmarksHtml.parse(chrome)
        assertEquals(
            listOf(
                Favorite("Star & Live", "https://www.star.gr/tv/live-stream"),
                Favorite("example.com", "https://example.com/watch?v=1&t=2"),
                Favorite("Old 'one' & <b>", "http://old.example.org/")
            ),
            p.favorites
        )
        assertEquals(2, p.skipped)
        assertEquals(FavoritesBackup.Source.BOOKMARKS_HTML, p.source)
    }

    @Test fun `a large ICON attribute and many anchors stay fast`() {
        val icon = "data:image/png;base64," + "A".repeat(200_000)
        val sb = StringBuilder("<!DOCTYPE NETSCAPE-Bookmark-file-1><DL>")
        repeat(200) { i ->
            sb.append("<DT><A HREF=\"https://s$i.example/\" ICON=\"$icon\">Site $i</A>\n")
        }
        sb.append("</DL>")
        val start = System.nanoTime()
        val p = BookmarksHtml.parse(sb.toString())
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(200, p.favorites.size)
        assertEquals("Site 199", p.favorites.last().title)
        assertTrue("took ${ms}ms", ms < 5_000)
    }

    @Test fun `a tag inside the title is stripped and an anchor without href is ignored`() {
        val p = BookmarksHtml.parse(
            "<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><DT><A NAME=x>no</A><DT><A HREF=\"https://a.example\"><b>Bold</b>\n  title</A></DL>"
        )
        assertEquals(listOf(Favorite("Bold title", "https://a.example")), p.favorites)
        assertEquals(0, p.skipped)
    }
}
