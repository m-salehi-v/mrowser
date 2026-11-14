package net.mrowser.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsJsonTest {

    @Test fun `round trips pages and hidden ids, including ids beyond Int`() {
        val s = DownloadsState(
            pages = mapOf(42L to "https://site.example/p", 3_000_000_000L to "https://b.example/"),
            hidden = setOf(7L, 9L),
        )
        assertEquals(s, DownloadsJson.fromJson(DownloadsJson.toJson(s)))
    }

    @Test fun `blank or corrupt files read as empty`() {
        assertEquals(DownloadsState(), DownloadsJson.fromJson(""))
        assertEquals(DownloadsState(), DownloadsJson.fromJson("{\"pages\":{\"1\":"))
    }

    @Test fun `bad entries are skipped one by one`() {
        assertEquals(
            DownloadsState(pages = mapOf(5L to "https://a.example/"), hidden = setOf(1L)),
            DownloadsJson.fromJson("""{"pages":{"5":"https://a.example/","x":"https://b.example/","6":7},"hidden":[1,"x"]}"""),
        )
    }
}
