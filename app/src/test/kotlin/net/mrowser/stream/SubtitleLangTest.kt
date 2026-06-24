package net.mrowser.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleLangTest {

    @Test fun `detects english from a filename`() {
        assertEquals(SubtitleLang.Detected("en", "English"), SubtitleLang.detect("https://cdn.x/movie.en.vtt"))
    }

    @Test fun `detects persian from a path segment`() {
        assertEquals(SubtitleLang.Detected("fa", "Persian"), SubtitleLang.detect("https://cdn.x/subs/farsi/01.srt"))
    }

    @Test fun `detects from a query parameter`() {
        assertEquals("fa", SubtitleLang.detect("https://cdn.x/s.vtt?lang=fa")?.code)
    }

    @Test fun `ignores the host`() {
        assertNull(SubtitleLang.detect("https://en.cdn.com/abc123.vtt"))
    }

    @Test fun `unknown url is null`() {
        assertNull(SubtitleLang.detect("https://cdn.x/a1b2c3.vtt"))
    }

    @Test fun `does not match a code inside a larger token`() {
        // "es" must not match inside "files"
        assertNull(SubtitleLang.detect("https://cdn.x/files/9981.vtt"))
    }
}
