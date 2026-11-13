package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DesktopUserAgentTest {
    private fun desktop(chrome: String) =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"

    private val bravia = "Mozilla/5.0 (Linux; Android 9; BRAVIA 4K GB ATV3 Build/PTT1.190515.001.S52; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/112.0.5615.136 Mobile Safari/537.36"
    private val fireTv = "Mozilla/5.0 (Linux; Android 9; AFTKA Build/PS7633.3445N; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.102 Mobile Safari/537.36"
    private val miBox = "Mozilla/5.0 (Linux; Android 7.1.2; MIBOX3 Build/NHG47L; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.158 Safari/537.36"
    private val reduced = "Mozilla/5.0 (Linux; Android 14; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"

    @Test fun `keeps the WebView's Chrome version`() {
        assertEquals(desktop("112.0.5615.136"), DesktopUserAgent.from(bravia))
        assertEquals(desktop("130.0.6723.102"), DesktopUserAgent.from(fireTv))
        assertEquals(desktop("66.0.3359.158"), DesktopUserAgent.from(miBox))
        assertEquals(desktop("131.0.6778.135"), DesktopUserAgent.from(reduced))
    }

    @Test fun `nothing marks it as android, mobile or a webview`() =
        listOf(bravia, fireTv, miBox, reduced).forEach { ua ->
            val d = DesktopUserAgent.from(ua)
            listOf("Android", "Mobile", "; wv", "Version/").forEach { assertFalse("$it in $d", d.contains(it)) }
        }

    @Test fun `no Chrome token falls back to a fixed desktop Chrome`() =
        assertEquals(desktop(DesktopUserAgent.FALLBACK_CHROME), DesktopUserAgent.from("Dalvik/2.1.0 (Linux; U; Android 9)"))
}
