package net.mrowser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileNameTest {

    @Test fun `keeps an ordinary name`() =
        assertEquals("Solaris.1972.1080p.mp4", DownloadFileName.sanitize("Solaris.1972.1080p.mp4"))

    @Test fun `drops any directory part`() {
        assertEquals("evil.apk", DownloadFileName.sanitize("../../data/evil.apk"))
        assertEquals("evil.apk", DownloadFileName.sanitize("C:\\temp\\evil.apk"))
    }

    @Test fun `replaces reserved characters and strips control and bidi characters`() {
        assertEquals("a_b_c_.txt", DownloadFileName.sanitize("a:b*c?.txt"))
        assertEquals("photo.jpg", DownloadFileName.sanitize("pho\u0000to\u0007.jpg"))
        assertEquals("gpj.apk", DownloadFileName.sanitize("\u202Egpj.apk"))
    }

    @Test fun `collapses whitespace and trims dots and spaces`() {
        assertEquals("my file.pdf", DownloadFileName.sanitize("  my \t\n file.pdf  "))
        assertEquals("name", DownloadFileName.sanitize("...name..."))
    }

    @Test fun `nothing usable becomes download`() {
        assertEquals("download", DownloadFileName.sanitize(""))
        assertEquals("download", DownloadFileName.sanitize(" .. / "))
    }

    @Test fun `a long name is cut to the limit and keeps its extension`() {
        val name = DownloadFileName.sanitize("a".repeat(300) + ".mp4")
        assertEquals(DownloadFileName.MAX_LENGTH, name.length)
        assertTrue(name.endsWith("a.mp4"))
    }

    @Test fun `a long name without an extension is cut to the limit`() {
        val name = DownloadFileName.sanitize("b".repeat(300))
        assertEquals(DownloadFileName.MAX_LENGTH, name.length)
    }

    @Test fun `an 11 character extension is not treated as one`() {
        val name = DownloadFileName.sanitize("c".repeat(200) + ".abcdefghijk")
        assertEquals(DownloadFileName.MAX_LENGTH, name.length)
        assertTrue(name.startsWith("c"))
    }

    @Test fun `a cut never splits a surrogate pair`() {
        val emoji = "\uD83C\uDFAC"
        val name = DownloadFileName.sanitize("a".repeat(119) + emoji + "zz")
        assertEquals("a".repeat(119), name)
        assertTrue(name.none { Character.isSurrogate(it) })
        val withExt = DownloadFileName.sanitize("a".repeat(115) + emoji.repeat(5) + ".mp4")
        assertTrue(withExt.endsWith(".mp4"))
        assertTrue(withExt.length <= DownloadFileName.MAX_LENGTH)
        assertTrue(withExt.none { it == '\uFFFD' })
        assertEquals(withExt, String(withExt.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }

    @Test fun `a cut leaves no trailing space or dot`() {
        val name = DownloadFileName.sanitize("a".repeat(118) + " ." + "x".repeat(12))
        assertEquals("a".repeat(118), name)
    }

    @Test fun `a long CJK name stays within the byte limit and keeps its extension`() {
        val name = DownloadFileName.sanitize("\u6F22".repeat(300) + ".mp4")
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= DownloadFileName.MAX_BYTES)
        assertTrue(name.endsWith(".mp4"))
        assertTrue(name.length <= DownloadFileName.MAX_LENGTH)
    }

    @Test fun `a bidi mark alone becomes download`() =
        assertEquals("download", DownloadFileName.sanitize("\u200E"))

    @Test fun `generic mime types count as none`() {
        listOf("application/octet-stream", "binary/octet-stream", "application/force-download", " ", null)
            .forEach { assertNull(it, DownloadFileName.cleanMime(it)) }
        assertEquals(
            "application/vnd.android.package-archive",
            DownloadFileName.cleanMime("Application/vnd.android.package-archive; charset=binary"),
        )
    }

    @Test fun `the shown name is the saved file's, else the title`() {
        assertEquals("Solaris-1.mp4", DownloadFileName.displayName("Solaris.mp4", "file:///storage/emulated/0/Download/Solaris-1.mp4"))
        assertEquals("My Film.mkv", DownloadFileName.displayName("x", "file:///storage/emulated/0/Download/My%20Film.mkv"))
        assertEquals("mrowser 1.5.0", DownloadFileName.displayName("mrowser 1.5.0", null))
        assertEquals("Download", DownloadFileName.displayName(" ", "content://downloads/"))
        // a content:// local URI ends in a row id, which is no name
        assertEquals("a.mp4", DownloadFileName.displayName("a.mp4", "content://media/external/downloads/1234"))
        assertEquals("clip", DownloadFileName.displayName("clip", "file:///storage/emulated/0/Download/1234"))
    }

    @Test fun `a name with an extension keeps it`() {
        assertEquals("Solaris.1972.mkv", DownloadFileName.withExtension("Solaris.1972.mkv", "video/mp4", "https://x.example/a.mp4"))
        assertEquals("archive.tar.gz", DownloadFileName.withExtension("archive.tar.gz", null, "https://x.example/a"))
    }

    @Test fun `a title without an extension takes one from the mime type`() {
        assertEquals(
            "mrowser 1.5.0.apk",
            DownloadFileName.withExtension("mrowser 1.5.0", DownloadActions.APK_MIME, "https://github.com/o/r/releases/download/v1.5.0/app-release.apk"),
        )
        assertEquals("clip.mp4", DownloadFileName.withExtension("clip", "video/mp4; codecs=avc1", "https://x.example/get?id=1"))
        assertEquals("Download.mkv", DownloadFileName.withExtension("Download", "VIDEO/X-MATROSKA", "https://x.example/f"))
    }

    @Test fun `with no useful mime type the url's file name gives the extension`() {
        assertEquals(
            "mrowser 1.5.0.apk",
            DownloadFileName.withExtension("mrowser 1.5.0", "application/octet-stream", "https://cdn.example/dl/app-release.APK?sig=1#x"),
        )
        assertEquals("film.webm", DownloadFileName.withExtension("film", null, "https://cdn.example/v/film%20cut.webm"))
    }

    @Test fun `nothing to go on leaves the name as it is`() {
        assertEquals("film", DownloadFileName.withExtension("film", null, "https://cdn.example/get?id=7"))
        assertEquals("film", DownloadFileName.withExtension("film", "application/x-unknown-thing", "https://cdn.example/"))
        assertEquals("film", DownloadFileName.withExtension("film", null, "not a url %%"))
    }

    @Test fun `only a short alphanumeric suffix with a letter is an extension`() {
        assertEquals("apk", DownloadFileName.extensionOf("a.APK"))
        assertEquals("7z", DownloadFileName.extensionOf("a.7z"))
        assertNull(DownloadFileName.extensionOf("mrowser 1.5.0"))
        assertNull(DownloadFileName.extensionOf("Mr. Smith goes"))
        assertNull(DownloadFileName.extensionOf(".bashrc"))
        assertNull(DownloadFileName.extensionOf("name."))
        assertNull(DownloadFileName.extensionOf("a.verylongextension"))
        assertNull(DownloadFileName.extensionOf(null))
    }
}
