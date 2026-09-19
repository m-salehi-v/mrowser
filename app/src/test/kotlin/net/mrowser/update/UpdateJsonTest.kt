package net.mrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateJsonTest {

    private val release = Release(
        tag = "v1.4.0",
        version = "1.4.0",
        notes = "Adds an update channel.",
        apkUrl = "https://github.com/m-salehi-v/mrowser/releases/download/v1.4.0/app-release.apk",
        apkSizeBytes = 12_400_000L,
        htmlUrl = "https://github.com/m-salehi-v/mrowser/releases/tag/v1.4.0"
    )

    @Test fun `round trips a full state`() {
        val s = UpdateState(lastCheckedAt = 1_700_000_000_000L, etag = "W/\"abc\"", release = release)
        assertEquals(s, UpdateJson.fromJson(UpdateJson.toJson(s)))
    }

    @Test fun `round trips a state with no release yet`() {
        val s = UpdateState(lastCheckedAt = 42L, etag = null, release = null)
        assertEquals(s, UpdateJson.fromJson(UpdateJson.toJson(s)))
    }

    @Test fun `missing fields fall back to defaults`() {
        assertEquals(UpdateState(), UpdateJson.fromJson("{}"))
    }

    @Test fun `blank input is all defaults`() {
        assertEquals(UpdateState(), UpdateJson.fromJson(""))
    }

    @Test fun `corrupt input is all defaults`() {
        assertEquals(UpdateState(), UpdateJson.fromJson("not json"))
    }

    @Test fun `a release missing its apk url is dropped`() {
        val json = """{"lastCheckedAt":5,"release":{"tag":"v1.4.0","version":"1.4.0",
            "htmlUrl":"https://x.test"}}"""
        val s = UpdateJson.fromJson(json)
        assertEquals(5L, s.lastCheckedAt)
        assertNull(s.release)
    }

    @Test fun `a blank etag reads as absent`() {
        assertNull(UpdateJson.fromJson("""{"etag":""}""").etag)
    }
}
