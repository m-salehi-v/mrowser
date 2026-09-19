package net.mrowser.update

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateControllerTest {

    private class FakeStore(initial: UpdateState = UpdateState()) : UpdateStateRepository {
        var state = initial
        override fun get(): UpdateState = state
        override fun update(state: UpdateState) { this.state = state }
    }

    private val direct = Executor { it.run() }
    private val now = 1_700_000_000_000L
    private val day = UpdateCheckPolicy.CHECK_INTERVAL_MS

    private fun payload(version: String) = """
        {"tag_name":"v$version","body":"notes","draft":false,"prerelease":false,
         "html_url":"https://github.com/m-salehi-v/mrowser/releases/tag/v$version",
         "assets":[{"name":"app-release.apk","size":12400000,
          "browser_download_url":"https://github.com/m-salehi-v/mrowser/releases/download/v$version/app-release.apk"}]}
    """.trimIndent()

    private fun controller(
        store: UpdateStateRepository,
        installed: String = "1.3.0",
        fetch: (String, String?) -> FetchResult
    ) = UpdateController(
        store = store,
        installedVersion = installed,
        fetch = fetch,
        io = direct,
        main = { it.run() },
        now = { now }
    )

    @Test fun `a check inside the interval does not touch the network`() {
        val store = FakeStore(UpdateState(lastCheckedAt = now))
        var fetched = false
        var called = false
        controller(store, fetch = { _, _ -> fetched = true; FetchResult.Failed })
            .checkIfDue { called = true }
        assertFalse(fetched)
        assertFalse(called)
    }

    @Test fun `a due check reports a newer release and caches it`() {
        val store = FakeStore(UpdateState(lastCheckedAt = now - day))
        var reported: Release? = null
        controller(store, fetch = { _, _ -> FetchResult.Body(payload("1.4.0"), "W/\"e1\"") })
            .checkIfDue { reported = it }
        assertEquals("1.4.0", reported?.version)
        assertEquals("1.4.0", store.state.release?.version)
        assertEquals("W/\"e1\"", store.state.etag)
        assertEquals(now, store.state.lastCheckedAt)
    }

    @Test fun `a due check reports nothing when the release is the running version`() {
        val store = FakeStore(UpdateState(lastCheckedAt = now - day))
        var reported: Release? = Release("x", "x", "", "x", 0, "x")
        controller(store, fetch = { _, _ -> FetchResult.Body(payload("1.3.0"), null) })
            .checkIfDue { reported = it }
        assertNull(reported)
        assertEquals("1.3.0", store.state.release?.version)
    }

    @Test fun `the stored etag is sent on the next check`() {
        val store = FakeStore(UpdateState(lastCheckedAt = now - day, etag = "W/\"e1\""))
        var seen: String? = null
        controller(store, fetch = { _, etag -> seen = etag; FetchResult.NotModified })
            .checkIfDue { }
        assertEquals("W/\"e1\"", seen)
    }

    @Test fun `not modified keeps the cached release and bumps the timestamp`() {
        val cached = Release("v1.4.0", "1.4.0", "", "https://github.com/a/a.apk", 1, "https://x.test")
        val store = FakeStore(UpdateState(lastCheckedAt = now - day, etag = "W/\"e1\"", release = cached))
        var reported: Release? = null
        controller(store, fetch = { _, _ -> FetchResult.NotModified }).checkIfDue { reported = it }
        assertEquals("1.4.0", reported?.version)
        assertEquals(cached, store.state.release)
        assertEquals("W/\"e1\"", store.state.etag)
        assertEquals(now, store.state.lastCheckedAt)
    }

    @Test fun `a failed check leaves the timestamp alone so the next launch retries`() {
        val store = FakeStore(UpdateState(lastCheckedAt = now - day))
        var reported: Release? = Release("x", "x", "", "x", 0, "x")
        controller(store, fetch = { _, _ -> FetchResult.Failed }).checkIfDue { reported = it }
        assertNull(reported)
        assertEquals(now - day, store.state.lastCheckedAt)
    }

    @Test fun `an unparseable body keeps the cached release`() {
        val cached = Release("v1.4.0", "1.4.0", "", "https://github.com/a/a.apk", 1, "https://x.test")
        val store = FakeStore(UpdateState(lastCheckedAt = now - day, release = cached))
        var reported: Release? = null
        controller(store, fetch = { _, _ -> FetchResult.Body("not json", null) })
            .checkIfDue { reported = it }
        assertEquals(cached, reported)
        assertEquals(cached, store.state.release)
    }

    @Test fun `cachedBanner reads the store without checking`() {
        val cached = Release("v1.4.0", "1.4.0", "", "https://github.com/a/a.apk", 1, "https://x.test")
        val store = FakeStore(UpdateState(lastCheckedAt = now, release = cached))
        var fetched = false
        val c = controller(store, fetch = { _, _ -> fetched = true; FetchResult.Failed })
        assertEquals(cached, c.cachedBanner())
        assertFalse(fetched)
    }

    @Test fun `cachedBanner is null once the running version catches up`() {
        val cached = Release("v1.4.0", "1.4.0", "", "https://github.com/a/a.apk", 1, "https://x.test")
        val store = FakeStore(UpdateState(lastCheckedAt = now, release = cached))
        val c = controller(store, installed = "1.4.0", fetch = { _, _ -> FetchResult.Failed })
        assertNull(c.cachedBanner())
    }

    @Test fun `the result is delivered through the main hop`() {
        val store = FakeStore(UpdateState(lastCheckedAt = now - day))
        var hops = 0
        UpdateController(
            store = store,
            installedVersion = "1.3.0",
            fetch = { _, _ -> FetchResult.Body(payload("1.4.0"), null) },
            io = direct,
            main = { hops++; it.run() },
            now = { now }
        ).checkIfDue { }
        assertTrue(hops == 1)
    }
}
