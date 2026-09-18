package net.mrowser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListTest {

    private fun list(vararg lines: String) = BlockList.fromLines(lines.asSequence())

    @Test fun `matches the exact domain`() {
        assertTrue(list("ads.example").contains("ads.example"))
    }

    @Test fun `matches any subdomain of a listed domain`() {
        val l = list("ads.example")
        assertTrue(l.contains("sub.ads.example"))
        assertTrue(l.contains("a.b.c.ads.example"))
    }

    @Test fun `does not match a sibling or a superstring`() {
        val l = list("ads.example")
        assertFalse(l.contains("example"))
        assertFalse(l.contains("notads.example"))
        assertFalse(l.contains("ads.example.org"))
    }

    @Test fun `never matches on the bare tld`() {
        // "com" as an entry must not make every .com host blocked: the walk stops before it.
        val l = list("com")
        assertFalse(l.contains("example.com"))
        assertEquals(0, l.size)
    }

    @Test fun `host lookup is case and trailing dot insensitive`() {
        assertTrue(list("ads.example").contains("Sub.ADS.Example."))
    }

    @Test fun `skips comments blanks and whitespace and dedupes`() {
        val l = list("# a comment", "", "   ", "ads.example", "ads.example", "  Tracker.Example  ")
        assertEquals(2, l.size)
        assertTrue(l.contains("tracker.example"))
    }

    @Test fun `normalises a wildcard prefix`() {
        assertTrue(list("*.ads.example").contains("x.ads.example"))
        assertTrue(list(".ads.example").contains("ads.example"))
    }

    @Test fun `ignores lines that are not a domain`() {
        val l = list("0.0.0.0 ads.example", "ads.example/path", "localhost")
        assertEquals(0, l.size)
    }

    @Test fun `parses the header into info`() {
        val l = list(
            "# mrowser block list",
            "# generated: 2026-09-18",
            "# source: oisd small | https://small.oisd.nl/domainswild2 | GPL-3.0 | 56157 entries | version 2026",
            "# source: HaGeZi Pop-Up Ads | https://example.org/popupads.txt | GPL-3.0 | 50556 entries",
            "# entries: 92643",
            "ads.example"
        )
        assertEquals("2026-09-18", l.info.generated)
        assertEquals(2, l.info.sources.size)
        assertEquals(
            BlockList.Source("oisd small", "https://small.oisd.nl/domainswild2", "GPL-3.0", 56157),
            l.info.sources[0]
        )
        assertEquals("HaGeZi Pop-Up Ads", l.info.sources[1].name)
        assertEquals(50556, l.info.sources[1].entries)
    }

    @Test fun `a malformed source line is kept with what it has`() {
        val l = list("# source: only a name")
        assertEquals(BlockList.Source("only a name", "", "", null), l.info.sources[0])
    }

    @Test fun `empty list contains nothing and has no info`() {
        assertFalse(BlockList.EMPTY.contains("ads.example"))
        assertEquals(0, BlockList.EMPTY.size)
        assertNull(BlockList.EMPTY.info.generated)
        assertTrue(BlockList.EMPTY.info.sources.isEmpty())
    }

    @Test fun `fnv1a64 matches the reference vector`() {
        // FNV-1a 64-bit of "a" is 0xaf63dc4c8601ec8c (reference test vector).
        assertEquals(0xaf63dc4c8601ec8cuL.toLong(), BlockList.fnv1a64("a"))
    }
}
