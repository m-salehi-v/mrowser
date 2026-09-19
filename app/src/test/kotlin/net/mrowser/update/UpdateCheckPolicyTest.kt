package net.mrowser.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {

    private val day = UpdateCheckPolicy.CHECK_INTERVAL_MS
    private val now = 1_700_000_000_000L

    private fun release(version: String) = Release(
        tag = "v$version",
        version = version,
        notes = "",
        apkUrl = "https://github.com/m-salehi-v/mrowser/releases/download/v$version/app-release.apk",
        apkSizeBytes = 1L,
        htmlUrl = "https://github.com/m-salehi-v/mrowser/releases/tag/v$version"
    )

    @Test fun `a check just made is not repeated`() {
        assertFalse(UpdateCheckPolicy.shouldCheck(now, now))
    }

    @Test fun `a check is not repeated one minute short of a day`() {
        assertFalse(UpdateCheckPolicy.shouldCheck(now, now - day + 60_000L))
    }

    @Test fun `a check is due at exactly a day`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(now, now - day))
    }

    @Test fun `a check is due after a day`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(now, now - day - 60_000L))
    }

    @Test fun `a first run has never checked`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(now, 0L))
    }

    @Test fun `a clock moved backwards does not freeze the check forever`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(now, now + day))
    }

    @Test fun `no cached release means no banner`() {
        assertNull(UpdateCheckPolicy.bannerFor("1.3.0", null))
    }

    @Test fun `a newer cached release is shown`() {
        val r = release("1.4.0")
        assertSame(r, UpdateCheckPolicy.bannerFor("1.3.0", r))
    }

    @Test fun `the running version clears the banner`() {
        assertNull(UpdateCheckPolicy.bannerFor("1.4.0", release("1.4.0")))
    }

    @Test fun `an older cached release is not shown`() {
        assertNull(UpdateCheckPolicy.bannerFor("1.4.0", release("1.3.0")))
    }

    @Test fun `an unreadable version is not shown`() {
        assertNull(UpdateCheckPolicy.bannerFor("", release("1.4.0")))
        assertNull(UpdateCheckPolicy.bannerFor("1.3.0", release("nightly")))
    }
}
