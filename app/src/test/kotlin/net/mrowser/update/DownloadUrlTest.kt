package net.mrowser.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUrlTest {

    @Test fun `a github release asset is allowed`() {
        assertTrue(DownloadUrl.isAllowed(
            "https://github.com/m-salehi-v/mrowser/releases/download/v1.4.0/app-release.apk"))
    }

    @Test fun `a github asset redirect host is allowed`() {
        assertTrue(DownloadUrl.isAllowed("https://objects.githubusercontent.com/x/y.apk"))
        assertTrue(DownloadUrl.isAllowed("https://release-assets.githubusercontent.com/x/y.apk"))
    }

    @Test fun `plain http is refused`() {
        assertFalse(DownloadUrl.isAllowed("http://github.com/a/b.apk"))
    }

    @Test fun `another host is refused`() {
        assertFalse(DownloadUrl.isAllowed("https://example.test/app-release.apk"))
    }

    @Test fun `a lookalike host is refused`() {
        assertFalse(DownloadUrl.isAllowed("https://github.com.evil.test/app-release.apk"))
        assertFalse(DownloadUrl.isAllowed("https://evilgithubusercontent.com/app-release.apk"))
        assertFalse(DownloadUrl.isAllowed("https://objects.githubusercontent.com.evil.test/x.apk"))
    }

    @Test fun `a host in the userinfo is refused`() {
        assertFalse(DownloadUrl.isAllowed("https://github.com@evil.test/app-release.apk"))
    }

    @Test fun `a relative or empty url is refused`() {
        assertFalse(DownloadUrl.isAllowed("/releases/app-release.apk"))
        assertFalse(DownloadUrl.isAllowed(""))
    }

    @Test fun `the scheme check ignores case`() {
        assertTrue(DownloadUrl.isAllowed("HTTPS://github.com/a/b.apk"))
    }

    @Test fun `host normalization is transparent`() {
        assertTrue(DownloadUrl.isAllowed("https://github.com./x.apk"))
        assertTrue(DownloadUrl.isAllowed("https://GITHUB.COM/x.apk"))
        assertTrue(DownloadUrl.isAllowed("https://github.com:443/x.apk"))
    }

    @Test fun `only the https scheme is allowed`() {
        assertFalse(DownloadUrl.isAllowed("//github.com/x.apk"))
        assertFalse(DownloadUrl.isAllowed("javascript://github.com/x.apk"))
    }
}
