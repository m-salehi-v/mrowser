package net.mrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseJsonTest {

    private fun payload(
        tag: String = "v1.4.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"app-release.apk","size":12400000,
            "browser_download_url":"https://github.com/m-salehi-v/mrowser/releases/download/v1.4.0/app-release.apk"}]"""
    ) = """
        {"tag_name":"$tag","name":"mrowser $tag","body":"Adds an update channel.",
         "draft":$draft,"prerelease":$prerelease,
         "html_url":"https://github.com/m-salehi-v/mrowser/releases/tag/$tag",
         "assets":$assets}
    """.trimIndent()

    @Test fun `parses a release`() {
        val r = ReleaseJson.parse(payload())!!
        assertEquals("v1.4.0", r.tag)
        assertEquals("1.4.0", r.version)
        assertEquals("Adds an update channel.", r.notes)
        assertEquals(12_400_000L, r.apkSizeBytes)
        assertEquals(
            "https://github.com/m-salehi-v/mrowser/releases/download/v1.4.0/app-release.apk",
            r.apkUrl
        )
        assertEquals("https://github.com/m-salehi-v/mrowser/releases/tag/v1.4.0", r.htmlUrl)
    }

    @Test fun `keeps the tag but strips the v from the version`() {
        assertEquals("1.4.0", ReleaseJson.parse(payload(tag = "v1.4.0"))!!.version)
        assertEquals("1.4.0", ReleaseJson.parse(payload(tag = "1.4.0"))!!.tag)
    }

    @Test fun `a release with no apk asset is not offered`() {
        assertNull(ReleaseJson.parse(payload(assets = """[{"name":"notes.txt","size":10,
            "browser_download_url":"https://github.com/a/b/releases/download/v1/notes.txt"}]""")))
    }

    @Test fun `a release with two apk assets is not offered`() {
        assertNull(ReleaseJson.parse(payload(assets = """[
            {"name":"app-release.apk","size":1,"browser_download_url":"https://github.com/a/b/1.apk"},
            {"name":"app-release-arm64.apk","size":2,"browser_download_url":"https://github.com/a/b/2.apk"}]""")))
    }

    @Test fun `an empty asset list is not offered`() {
        assertNull(ReleaseJson.parse(payload(assets = "[]")))
    }

    @Test fun `a prerelease is not offered`() {
        assertNull(ReleaseJson.parse(payload(prerelease = true)))
    }

    @Test fun `a draft is not offered`() {
        assertNull(ReleaseJson.parse(payload(draft = true)))
    }

    @Test fun `a missing tag is not offered`() {
        assertNull(ReleaseJson.parse("""{"html_url":"https://x.test","assets":[]}"""))
    }

    @Test fun `a missing html url is not offered`() {
        assertNull(ReleaseJson.parse("""{"tag_name":"v1.4.0","assets":[]}"""))
    }

    @Test fun `truncated json is not offered`() {
        assertNull(ReleaseJson.parse("""{"tag_name":"v1.4.0","assets":["""))
    }

    @Test fun `blank input is not offered`() {
        assertNull(ReleaseJson.parse(""))
    }

    @Test fun `absent notes read as empty`() {
        val json = """{"tag_name":"v1.4.0","html_url":"https://x.test",
            "assets":[{"name":"a.apk","size":1,"browser_download_url":"https://github.com/a/a.apk"}]}"""
        assertEquals("", ReleaseJson.parse(json)!!.notes)
    }
}
