package net.mrowser.adblock

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the real bundled list, not the matcher. A refresh that drops a pop-under network, or
 * picks up a video CDN apex that would break the HLS sniff, fails here before it ships.
 */
class BlockListCanaryTest {

    private val list: BlockList by lazy {
        // Gradle runs unit tests with the module directory (app/) as the working directory;
        // fall back to the repo root for IDE runners.
        val candidates = listOf("src/main/res/raw/blocklist.txt", "app/src/main/res/raw/blocklist.txt")
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("blocklist.txt not found from ${File(".").absolutePath}; run scripts/update-blocklist.sh")
        file.bufferedReader().useLines { BlockList.fromLines(it) }
    }

    private val popUnderNetworks = listOf(
        "popads.net", "propellerads.com", "exoclick.com", "adsterra.com", "hilltopads.net",
        "clickadu.com", "richads.com", "zeropark.com", "trafficstars.com", "popcash.net",
        "juicyads.com", "trafficjunky.net"
    )

    private val mustNeverBeListed = listOf(
        "cloudfront.net", "akamaized.net", "akamaihd.net", "fastly.net", "cdn77.org",
        "b-cdn.net", "jwpcdn.com", "cloudflarestream.com", "vimeocdn.com",
        "youtube.com", "googlevideo.com", "google.com", "github.com"
    )

    /**
     * A representative slice of `RegistrableDomain.TWO_LABEL_SUFFIXES` (kept private there, so
     * mirrored rather than referenced). `BlockList.contains` has no public-suffix awareness — it
     * just walks a host's suffixes down to but excluding the bare TLD — so a single upstream
     * entry like "co.uk" or "com.br" would silently mass-block an entire ccTLD's worth of sites.
     * This is the only quality gate the weekly `blocklist.yml` refresh runs before opening its PR.
     */
    private val publicSuffixesMustNeverBeListed = listOf(
        "co.uk", "org.uk", "com.au", "co.jp", "com.br", "co.in", "co.za", "com.mx", "com.tr", "co.kr"
    )

    @Test fun `list is big enough to be real`() {
        assertTrue("only ${list.size} entries", list.size >= 50_000)
    }

    @Test fun `header names both sources and a date`() {
        assertNotNull(list.info.generated)
        val names = list.info.sources.map { it.name }
        assertTrue(names.toString(), names.any { it.contains("oisd", ignoreCase = true) })
        assertTrue(names.toString(), names.any { it.contains("HaGeZi", ignoreCase = true) })
        assertTrue(list.info.sources.all { it.licence == "GPL-3.0" && it.url.startsWith("https://") })
    }

    @Test fun `known pop-under networks are listed`() {
        val missing = popUnderNetworks.filterNot { list.contains("cdn.$it") }
        assertTrue("missing from the list: $missing", missing.isEmpty())
    }

    @Test fun `video cdn apexes and first-party staples are not listed`() {
        val listed = mustNeverBeListed.filter { list.contains(it) }
        assertTrue("must not be listed: $listed", listed.isEmpty())
    }

    @Test fun `public suffixes are never listed as a bare entry`() {
        val listed = publicSuffixesMustNeverBeListed.filter { list.contains(it) }
        assertTrue(
            "a refresh listed a public suffix directly, which mass-blocks its entire ccTLD " +
                "(e.g. a bare 'co.uk' entry blocks every *.co.uk site): $listed",
            listed.isEmpty()
        )
    }

    @Test fun `every body line is a plain lowercase domain`() {
        val candidates = listOf("src/main/res/raw/blocklist.txt", "app/src/main/res/raw/blocklist.txt")
        val file = candidates.map(::File).first { it.exists() }
        file.useLines { lines ->
            lines.filterNot { it.startsWith("#") || it.isBlank() }.forEach { line ->
                assertFalse("not a bare domain: '$line'", line.any { it.isWhitespace() || it == '/' || it.isUpperCase() })
                assertTrue("no dot: '$line'", line.contains('.'))
            }
        }
    }
}
