package net.mrowser.adblock

import net.mrowser.adblock.BlockedResponse.Kind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedResponseTest {

    @Test fun `image by accept header`() {
        assertEquals(Kind.IMAGE, BlockedResponse.kindFor("https://ads.example/pixel", "image/avif,image/webp,*/*"))
    }

    @Test fun `image by extension`() {
        assertEquals(Kind.IMAGE, BlockedResponse.kindFor("https://ads.example/pixel.GIF?x=1", null))
        assertEquals(Kind.IMAGE, BlockedResponse.kindFor("https://ads.example/a.png", "*/*"))
    }

    @Test fun `html by accept header`() {
        assertEquals(Kind.HTML, BlockedResponse.kindFor("https://ads.example/frame", "text/html,application/xhtml+xml"))
    }

    @Test fun `script by extension`() {
        assertEquals(Kind.SCRIPT, BlockedResponse.kindFor("https://ads.example/tag.js", "*/*"))
        assertEquals(Kind.SCRIPT, BlockedResponse.kindFor("https://ads.example/tag.mjs", null))
    }

    @Test fun `everything else is empty text`() {
        assertEquals(Kind.EMPTY, BlockedResponse.kindFor("https://ads.example/track?e=1", "*/*"))
        assertEquals(Kind.EMPTY, BlockedResponse.kindFor("https://ads.example/track", null))
    }

    @Test fun `bodies and mime types`() {
        assertEquals("image/gif", Kind.IMAGE.mimeType)
        assertArrayEquals("GIF89a".toByteArray(Charsets.US_ASCII), Kind.IMAGE.body.copyOf(6))
        assertEquals(0x3b.toByte(), Kind.IMAGE.body.last())
        assertEquals("text/html", Kind.HTML.mimeType)
        assertEquals("application/javascript", Kind.SCRIPT.mimeType)
        assertEquals("text/plain", Kind.EMPTY.mimeType)
        assertEquals(0, Kind.HTML.body.size)
        assertEquals(0, Kind.SCRIPT.body.size)
        assertEquals(0, Kind.EMPTY.body.size)
    }
}
