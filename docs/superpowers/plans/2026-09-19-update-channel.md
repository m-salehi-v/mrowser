# In-app Update Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Once a day mrowser asks GitHub whether a newer stable release exists and, if so, shows a quiet line at the bottom of the home screen that leads to release notes and a download of the APK into public `Downloads/`.

**Architecture:** A new `net.mrowser.update` domain following the repository's pure/glue split. Five pure objects (version comparison, release parsing, cache serialization, throttle policy, URL vetting) carry every decision and are unit-tested on the JVM; thin Android glue does the HTTP call, the JSON file, the `DownloadManager` request and the dialog. `UpdateController` takes its network call, both thread hops and the clock as constructor parameters, so it is pure Kotlin and tested too. mrowser never installs the APK — the flow ends at "saved to Downloads".

**Tech Stack:** Kotlin, framework `Activity`/`AlertDialog` (no AndroidX beyond Media3), `HttpURLConnection`, `org.json`, `android.app.DownloadManager`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-19-update-channel-design.md`

## Global Constraints

- **No AndroidX/Compose** beyond Media3. Framework `Activity`, XML layouts in `res/layout`, pure modules are plain Kotlin `object`s/classes with **no Android imports**.
- **No new Gradle dependencies.** `gradle/libs.versions.toml` and `app/build.gradle.kts` are not modified by this plan.
- **No `REQUEST_INSTALL_PACKAGES`.** The only manifest change in this plan is `WRITE_EXTERNAL_STORAGE` with `android:maxSdkVersion="28"`.
- minSdk 23, targetSdk 34, compileSdk 36, JDK 17. Any API above 23 must be guarded with `Build.VERSION.SDK_INT`.
- Brand accent is `@color/accent` (`#E50914`). Dark theme. All user-visible copy goes in `res/values/strings.xml` — no literals in Kotlin.
- Repository constant: `m-salehi-v/mrowser`. Endpoint: `https://api.github.com/repos/m-salehi-v/mrowser/releases/latest`.
- Check interval: **24h**. Notes truncation: **2000 characters**. Response body cap: **256 KB**. HTTP timeouts: **15s**.
- Every network and parse failure logs at `Log.w` and returns — never a toast, never a dialog, never a crash.
- Test names use backticked sentences, matching `app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt`.
- Commit messages: conventional commits, **no `Co-Authored-By` and no "Generated with" trailer**.
- Run `./gradlew test` before every commit that touches Kotlin.

---

## File Structure

**Created:**

| Path | Responsibility |
|---|---|
| `app/src/main/kotlin/net/mrowser/update/Release.kt` | Immutable record of one GitHub release |
| `app/src/main/kotlin/net/mrowser/update/VersionCompare.kt` | **Pure.** Compare dotted numeric versions |
| `app/src/main/kotlin/net/mrowser/update/ReleaseJson.kt` | **Pure.** GitHub payload → `Release?` |
| `app/src/main/kotlin/net/mrowser/update/UpdateState.kt` | Persisted cache record |
| `app/src/main/kotlin/net/mrowser/update/UpdateJson.kt` | **Pure.** `UpdateState` (de)serialization |
| `app/src/main/kotlin/net/mrowser/update/UpdateStateRepository.kt` | Store interface |
| `app/src/main/kotlin/net/mrowser/update/JsonUpdateStore.kt` | File I/O for `filesDir/update.json` |
| `app/src/main/kotlin/net/mrowser/update/UpdateCheckPolicy.kt` | **Pure.** Throttle + banner decisions |
| `app/src/main/kotlin/net/mrowser/update/DownloadUrl.kt` | **Pure.** Vet the APK URL host |
| `app/src/main/kotlin/net/mrowser/update/ByteSize.kt` | **Pure.** "12.4 MB" |
| `app/src/main/kotlin/net/mrowser/update/FetchResult.kt` | **Pure.** Result of one conditional GET |
| `app/src/main/kotlin/net/mrowser/update/ReleaseFetcher.kt` | HTTP call to the releases API |
| `app/src/main/kotlin/net/mrowser/update/UpdateController.kt` | Throttle → fetch → cache → callback |
| `app/src/main/kotlin/net/mrowser/update/ApkDownloader.kt` | `DownloadManager` wrapper (reusable core) |
| `app/src/main/kotlin/net/mrowser/update/UpdateDialog.kt` | Notes / size / Download / progress |
| 7 matching test classes under `app/src/test/kotlin/net/mrowser/update/` | |

**Modified:** `app/src/main/res/layout/home_view.xml`, `app/src/main/kotlin/net/mrowser/home/HomeView.kt`, `app/src/main/kotlin/net/mrowser/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/AndroidManifest.xml`, `README.md`, `CLAUDE.md`.

---

### Task 1: Release record and version comparison

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/Release.kt`
- Create: `app/src/main/kotlin/net/mrowser/update/VersionCompare.kt`
- Test: `app/src/test/kotlin/net/mrowser/update/VersionCompareTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `Release(tag, version, notes, apkUrl, apkSizeBytes, htmlUrl)`; `VersionCompare.compare(a: String, b: String): Int?`; `VersionCompare.isNewer(candidate: String, installed: String): Boolean`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/update/VersionCompareTest.kt`:

```kotlin
package net.mrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test fun `a newer release compares greater`() {
        assertEquals(1, VersionCompare.compare("v1.4.0", "1.3.0"))
    }

    @Test fun `an older release compares smaller`() {
        assertEquals(-1, VersionCompare.compare("1.2.1", "1.3.0"))
    }

    @Test fun `the same version compares equal`() {
        assertEquals(0, VersionCompare.compare("1.3.0", "1.3.0"))
    }

    @Test fun `segments compare as numbers not strings`() {
        assertEquals(1, VersionCompare.compare("1.10.0", "1.9.0"))
    }

    @Test fun `the v prefix is optional on either side`() {
        assertEquals(0, VersionCompare.compare("v1.3.0", "1.3.0"))
        assertEquals(0, VersionCompare.compare("1.3.0", "V1.3.0"))
        assertEquals(0, VersionCompare.compare("v1.3.0", "v1.3.0"))
    }

    @Test fun `a missing segment counts as zero`() {
        assertEquals(0, VersionCompare.compare("1.4", "1.4.0"))
        assertEquals(1, VersionCompare.compare("1.4.1", "1.4"))
    }

    @Test fun `a non-numeric segment is unknown`() {
        assertNull(VersionCompare.compare("1.4.0-rc1", "1.3.0"))
        assertNull(VersionCompare.compare("1.3.0", "nightly"))
    }

    @Test fun `an empty version is unknown`() {
        assertNull(VersionCompare.compare("", "1.3.0"))
        assertNull(VersionCompare.compare("v", "1.3.0"))
    }

    @Test fun `isNewer is true only for a strictly greater version`() {
        assertTrue(VersionCompare.isNewer("1.4.0", "1.3.0"))
        assertFalse(VersionCompare.isNewer("1.3.0", "1.3.0"))
        assertFalse(VersionCompare.isNewer("1.2.1", "1.3.0"))
    }

    @Test fun `isNewer is false when either version is unknown`() {
        assertFalse(VersionCompare.isNewer("1.4.0-rc1", "1.3.0"))
        assertFalse(VersionCompare.isNewer("1.4.0", ""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.VersionCompareTest"`
Expected: FAIL — compilation error, `VersionCompare` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/net/mrowser/update/Release.kt`:

```kotlin
package net.mrowser.update

/**
 * One GitHub release, as much of it as the update channel needs.
 *
 * [tag] is the git tag as published ("v1.4.0"); [version] is the same with any leading `v`
 * stripped, which is the form that compares against `PackageManager`'s `versionName`.
 */
data class Release(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)
```

Create `app/src/main/kotlin/net/mrowser/update/VersionCompare.kt`:

```kotlin
package net.mrowser.update

/**
 * Pure comparison of dotted numeric version strings ("1.4.0", "v1.10.0").
 *
 * Segments compare as numbers, so 1.10.0 beats 1.9.0 where string order would not, and a
 * missing segment counts as zero so "1.4" and "1.4.0" are the same release. Anything that is
 * not purely numeric comes back `null` — unknown, never a guess: a background check that
 * cannot read a version must say "no update" rather than nag about one that may not exist.
 */
object VersionCompare {

    /** -1 / 0 / 1 as [a] is older / same / newer than [b]; null when either is unreadable. */
    fun compare(a: String, b: String): Int? {
        val left = segments(a) ?: return null
        val right = segments(b) ?: return null
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return if (l < r) -1 else 1
        }
        return 0
    }

    /** True only when [candidate] is strictly newer; an unreadable version is never newer. */
    fun isNewer(candidate: String, installed: String): Boolean =
        (compare(candidate, installed) ?: 0) > 0

    /** "v1.4.0" -> [1, 4, 0]; null when any segment is not a non-negative integer. */
    private fun segments(version: String): List<Int>? {
        val trimmed = version.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return null
        return trimmed.split('.').map { part ->
            val n = part.toIntOrNull() ?: return null
            if (n < 0) return null
            n
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.VersionCompareTest"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/Release.kt \
        app/src/main/kotlin/net/mrowser/update/VersionCompare.kt \
        app/src/test/kotlin/net/mrowser/update/VersionCompareTest.kt
git commit -m "feat(update): compare release versions"
```

---

### Task 2: Parse the GitHub release payload

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/ReleaseJson.kt`
- Test: `app/src/test/kotlin/net/mrowser/update/ReleaseJsonTest.kt`

**Interfaces:**
- Consumes: `Release` (Task 1).
- Produces: `ReleaseJson.parse(body: String): Release?`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/update/ReleaseJsonTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.ReleaseJsonTest"`
Expected: FAIL — `ReleaseJson` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/net/mrowser/update/ReleaseJson.kt`:

```kotlin
package net.mrowser.update

import org.json.JSONException
import org.json.JSONObject

/**
 * Pure parse of one GitHub `/releases/latest` payload.
 *
 * Anything unexpected is `null`, and the caller treats `null` as "no update": a silent nothing
 * is always the right answer for a check the user did not ask for. `draft`/`prerelease` should
 * never come back from this endpoint — if one does, the response is not what we think it is and
 * is refused rather than trusted. Exactly one `.apk` asset is required: the release workflow
 * attaches one (`app-release.apk`), so zero or several means something changed upstream and
 * guessing which file to hand `DownloadManager` is not the job of a background check.
 */
object ReleaseJson {

    fun parse(body: String): Release? = try {
        val o = JSONObject(body)
        when {
            o.optBoolean("draft", false) -> null
            o.optBoolean("prerelease", false) -> null
            else -> build(o)
        }
    } catch (e: JSONException) {
        null
    }

    private fun build(o: JSONObject): Release? {
        val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val htmlUrl = o.optString("html_url").takeIf { it.isNotBlank() } ?: return null

        var apkUrl: String? = null
        var apkSize = 0L
        var apkCount = 0
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (!a.optString("name").endsWith(".apk", ignoreCase = true)) continue
                apkCount++
                apkUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                apkSize = a.optLong("size", 0L)
            }
        }
        if (apkCount != 1) return null
        val url = apkUrl ?: return null

        return Release(
            tag = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            notes = o.optString("body"),
            apkUrl = url,
            apkSizeBytes = apkSize,
            htmlUrl = htmlUrl
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.ReleaseJsonTest"`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/ReleaseJson.kt \
        app/src/test/kotlin/net/mrowser/update/ReleaseJsonTest.kt
git commit -m "feat(update): parse the GitHub releases payload"
```

---

### Task 3: Persist the check cache

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/UpdateState.kt`
- Create: `app/src/main/kotlin/net/mrowser/update/UpdateJson.kt`
- Create: `app/src/main/kotlin/net/mrowser/update/UpdateStateRepository.kt`
- Create: `app/src/main/kotlin/net/mrowser/update/JsonUpdateStore.kt`
- Test: `app/src/test/kotlin/net/mrowser/update/UpdateJsonTest.kt`

**Interfaces:**
- Consumes: `Release` (Task 1).
- Produces: `UpdateState(lastCheckedAt: Long, etag: String?, release: Release?)`; `UpdateJson.toJson(UpdateState): String` / `UpdateJson.fromJson(String): UpdateState`; `interface UpdateStateRepository { fun get(): UpdateState; fun update(state: UpdateState) }`; `class JsonUpdateStore(file: File) : UpdateStateRepository`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/update/UpdateJsonTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.UpdateJsonTest"`
Expected: FAIL — `UpdateState` and `UpdateJson` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/net/mrowser/update/UpdateState.kt`:

```kotlin
package net.mrowser.update

/**
 * What the update channel remembers between launches.
 *
 * [release] is cached so the home screen can draw its line on the first frame without waiting
 * for — or, when the check is throttled, without making — a network call. [etag] turns the next
 * check into a conditional GET that costs no rate-limit quota.
 */
data class UpdateState(
    val lastCheckedAt: Long = 0L,
    val etag: String? = null,
    val release: Release? = null
)
```

Create `app/src/main/kotlin/net/mrowser/update/UpdateJson.kt`:

```kotlin
package net.mrowser.update

import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of UpdateState. Missing/unparseable fields → defaults. */
object UpdateJson {

    fun toJson(state: UpdateState): String {
        val o = JSONObject().put("lastCheckedAt", state.lastCheckedAt)
        state.etag?.let { o.put("etag", it) }
        state.release?.let { r ->
            o.put(
                "release",
                JSONObject()
                    .put("tag", r.tag)
                    .put("version", r.version)
                    .put("notes", r.notes)
                    .put("apkUrl", r.apkUrl)
                    .put("apkSizeBytes", r.apkSizeBytes)
                    .put("htmlUrl", r.htmlUrl)
            )
        }
        return o.toString()
    }

    fun fromJson(json: String): UpdateState {
        if (json.isBlank()) return UpdateState()
        return try {
            val o = JSONObject(json)
            UpdateState(
                lastCheckedAt = o.optLong("lastCheckedAt", 0L),
                etag = o.optString("etag").takeIf { it.isNotBlank() },
                release = o.optJSONObject("release")?.let(::release)
            )
        } catch (e: JSONException) {
            UpdateState()
        }
    }

    /** A half-written release is no release: the fields the download path needs are required. */
    private fun release(o: JSONObject): Release? {
        val tag = o.optString("tag").takeIf { it.isNotBlank() } ?: return null
        val apkUrl = o.optString("apkUrl").takeIf { it.isNotBlank() } ?: return null
        val htmlUrl = o.optString("htmlUrl").takeIf { it.isNotBlank() } ?: return null
        return Release(
            tag = tag,
            version = o.optString("version").takeIf { it.isNotBlank() }
                ?: tag.removePrefix("v").removePrefix("V"),
            notes = o.optString("notes"),
            apkUrl = apkUrl,
            apkSizeBytes = o.optLong("apkSizeBytes", 0L),
            htmlUrl = htmlUrl
        )
    }
}
```

Create `app/src/main/kotlin/net/mrowser/update/UpdateStateRepository.kt`:

```kotlin
package net.mrowser.update

/** Storage for the update channel's cache. */
interface UpdateStateRepository {
    /** An immutable snapshot; safe to read from any thread. */
    fun get(): UpdateState

    fun update(state: UpdateState)
}
```

Create `app/src/main/kotlin/net/mrowser/update/JsonUpdateStore.kt`:

```kotlin
package net.mrowser.update

import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** UpdateStateRepository backed by a JSON file; pure logic delegated to UpdateJson. */
class JsonUpdateStore(private val file: File) : UpdateStateRepository {

    // Volatile: UpdateController writes this from its background executor while the UI thread
    // reads it through cachedBanner(). The record itself is immutable, but the reference still
    // needs a JMM guarantee to be seen across threads.
    @Volatile
    private var current: UpdateState =
        if (file.exists()) UpdateJson.fromJson(runCatching { file.readText() }.getOrDefault(""))
        else UpdateState()

    private val io = Executors.newSingleThreadExecutor()

    override fun get(): UpdateState = current

    override fun update(state: UpdateState) {
        current = state
        persist()
    }

    /** Serialize the (immutable) snapshot on the caller, then write off the caller's thread.
     *  The single-thread executor preserves write order; failures are logged, not swallowed. */
    private fun persist() {
        val snapshot = UpdateJson.toJson(current)
        io.execute {
            runCatching { file.writeText(snapshot) }
                .onFailure { Log.w(TAG, "persist failed: ${file.name}", it) }
        }
    }

    private companion object {
        private const val TAG = "JsonUpdateStore"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.UpdateJsonTest"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/UpdateState.kt \
        app/src/main/kotlin/net/mrowser/update/UpdateJson.kt \
        app/src/main/kotlin/net/mrowser/update/UpdateStateRepository.kt \
        app/src/main/kotlin/net/mrowser/update/JsonUpdateStore.kt \
        app/src/test/kotlin/net/mrowser/update/UpdateJsonTest.kt
git commit -m "feat(update): persist the update check cache"
```

---

### Task 4: Throttle and banner policy

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/UpdateCheckPolicy.kt`
- Test: `app/src/test/kotlin/net/mrowser/update/UpdateCheckPolicyTest.kt`

**Interfaces:**
- Consumes: `Release` (Task 1), `VersionCompare` (Task 1).
- Produces: `UpdateCheckPolicy.CHECK_INTERVAL_MS: Long`; `UpdateCheckPolicy.shouldCheck(nowMs: Long, lastCheckedAtMs: Long): Boolean`; `UpdateCheckPolicy.bannerFor(installedVersion: String, cached: Release?): Release?`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/update/UpdateCheckPolicyTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.UpdateCheckPolicyTest"`
Expected: FAIL — `UpdateCheckPolicy` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/net/mrowser/update/UpdateCheckPolicy.kt`:

```kotlin
package net.mrowser.update

/** Pure: when to ask GitHub, and whether the cached answer is worth showing. */
object UpdateCheckPolicy {

    /** One check a day. GitHub allows 60 unauthenticated requests an hour per IP, and carrier
     *  -grade NAT puts many users behind one address, so this stays deliberately far under. */
    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    /**
     * A timestamp in the future means the clock moved backwards (a TV that lost its time and
     * re-synced). Without this case the check would be frozen until real time caught up.
     */
    fun shouldCheck(nowMs: Long, lastCheckedAtMs: Long): Boolean =
        lastCheckedAtMs > nowMs || nowMs - lastCheckedAtMs >= CHECK_INTERVAL_MS

    /**
     * The release to advertise, or null for none. Comparing against the running version — rather
     * than storing a "seen" flag — is what makes the banner self-clearing: once the user installs
     * the new APK the cached release stops being newer and the line disappears on its own.
     */
    fun bannerFor(installedVersion: String, cached: Release?): Release? {
        if (cached == null) return null
        return if (VersionCompare.isNewer(cached.version, installedVersion)) cached else null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.UpdateCheckPolicyTest"`
Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/UpdateCheckPolicy.kt \
        app/src/test/kotlin/net/mrowser/update/UpdateCheckPolicyTest.kt
git commit -m "feat(update): throttle the check and decide the banner"
```

---

### Task 5: Vet the download URL and format sizes

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/DownloadUrl.kt`
- Create: `app/src/main/kotlin/net/mrowser/update/ByteSize.kt`
- Test: `app/src/test/kotlin/net/mrowser/update/DownloadUrlTest.kt`
- Test: `app/src/test/kotlin/net/mrowser/update/ByteSizeTest.kt`

**Interfaces:**
- Consumes: `net.mrowser.web.UrlHost.of(url: String): String?` (existing).
- Produces: `DownloadUrl.isAllowed(url: String): Boolean`; `ByteSize.format(bytes: Long): String`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/net/mrowser/update/DownloadUrlTest.kt`:

```kotlin
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
}
```

Create `app/src/test/kotlin/net/mrowser/update/ByteSizeTest.kt`:

```kotlin
package net.mrowser.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeTest {

    @Test fun `formats megabytes to one decimal`() {
        assertEquals("12.4 MB", ByteSize.format(12_400_000L))
    }

    @Test fun `rounds to one decimal`() {
        assertEquals("1.5 MB", ByteSize.format(1_460_000L))
    }

    @Test fun `a sub-megabyte size still reads in MB`() {
        assertEquals("0.2 MB", ByteSize.format(200_000L))
    }

    @Test fun `an unknown size is blank`() {
        assertEquals("", ByteSize.format(0L))
        assertEquals("", ByteSize.format(-1L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.DownloadUrlTest" --tests "net.mrowser.update.ByteSizeTest"`
Expected: FAIL — `DownloadUrl` and `ByteSize` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/net/mrowser/update/DownloadUrl.kt`:

```kotlin
package net.mrowser.update

import net.mrowser.web.UrlHost

/**
 * Pure: is this a URL we are willing to hand `DownloadManager`?
 *
 * The APK URL arrives in a network response, and `DownloadManager` fetches whatever it is given,
 * so the destination is vetted before it is enqueued. Only https, and only GitHub's own hosts —
 * `github.com` for the release asset URL and `*.githubusercontent.com` for the object storage it
 * redirects to. Redirects past that point belong to the platform and stay over https.
 */
object DownloadUrl {

    private const val HTTPS = "https://"
    private const val GITHUB = "github.com"
    private const val ASSET_SUFFIX = ".githubusercontent.com"

    fun isAllowed(url: String): Boolean {
        if (!url.startsWith(HTTPS, ignoreCase = true)) return false
        val host = UrlHost.of(url) ?: return false
        return host == GITHUB || host.endsWith(ASSET_SUFFIX)
    }
}
```

Create `app/src/main/kotlin/net/mrowser/update/ByteSize.kt`:

```kotlin
package net.mrowser.update

import java.util.Locale

/** Pure: a download size for a TV screen. Megabytes to one decimal; unknown reads as blank. */
object ByteSize {

    private const val MB = 1_000_000.0

    fun format(bytes: Long): String =
        if (bytes <= 0L) "" else String.format(Locale.US, "%.1f MB", bytes / MB)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.DownloadUrlTest" --tests "net.mrowser.update.ByteSizeTest"`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/DownloadUrl.kt \
        app/src/main/kotlin/net/mrowser/update/ByteSize.kt \
        app/src/test/kotlin/net/mrowser/update/DownloadUrlTest.kt \
        app/src/test/kotlin/net/mrowser/update/ByteSizeTest.kt
git commit -m "feat(update): vet the apk url and format its size"
```

---

### Task 6: Fetch the latest release

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/FetchResult.kt`
- Create: `app/src/main/kotlin/net/mrowser/update/ReleaseFetcher.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `sealed class FetchResult` with `FetchResult.Body(text: String, etag: String?)`, `FetchResult.NotModified`, `FetchResult.Failed`; `ReleaseFetcher.fetch(installedVersion: String, etag: String?): FetchResult`; `ReleaseFetcher.LATEST_RELEASE_URL: String`.

No test: this is Android glue around `HttpURLConnection` with a `Log` call, treated the same way as `player/SubtitleFetcher`. The decisions it feeds are tested in Tasks 2, 4 and 7.

- [ ] **Step 1: Create the result type**

Create `app/src/main/kotlin/net/mrowser/update/FetchResult.kt`:

```kotlin
package net.mrowser.update

/**
 * The outcome of one conditional GET against the releases API. Kept free of Android imports so
 * [UpdateController] — which branches on it — stays unit-testable.
 */
sealed class FetchResult {
    /** 200: a body to parse, and the ETag to send next time (null when the server sent none). */
    data class Body(val text: String, val etag: String?) : FetchResult()

    /** 304: the cached release is still current, and this cost no rate-limit quota. */
    object NotModified : FetchResult()

    /** Anything else: no network, a timeout, a rate limit, an unexpected status. */
    object Failed : FetchResult()
}
```

- [ ] **Step 2: Write the fetcher**

Create `app/src/main/kotlin/net/mrowser/update/ReleaseFetcher.kt`:

```kotlin
package net.mrowser.update

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the newest stable release. Modelled on [net.mrowser.player.SubtitleFetcher]:
 * one blocking call on a caller-supplied thread, every failure logged and swallowed.
 *
 * `/releases/latest` is GitHub's definition of the newest non-draft, non-prerelease release, so
 * prereleases published from this repository never reach users.
 */
object ReleaseFetcher {

    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/m-salehi-v/mrowser/releases/latest"

    private const val TAG = "ReleaseFetcher"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_BODY_BYTES = 256 * 1024

    fun fetch(installedVersion: String, etag: String?): FetchResult = try {
        val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            // Not optional: GitHub answers 403 to an API request that sends no User-Agent.
            setRequestProperty("User-Agent", "mrowser/$installedVersion")
            setRequestProperty("Accept", "application/vnd.github+json")
            // A 304 costs no rate-limit quota, which is what keeps a whole NAT'd neighbourhood
            // of users under the 60-per-hour unauthenticated limit.
            if (etag != null) setRequestProperty("If-None-Match", etag)
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> FetchResult.NotModified
                HttpURLConnection.HTTP_OK ->
                    FetchResult.Body(readCapped(conn), conn.getHeaderField("ETag"))
                else -> {
                    Log.w(TAG, "releases/latest returned $code")
                    FetchResult.Failed
                }
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "fetch failed", e)
        FetchResult.Failed
    }

    /** Reads at most [MAX_BODY_BYTES], so an unexpected or hostile response cannot balloon memory. */
    private fun readCapped(conn: HttpURLConnection): String {
        val out = ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8 * 1024)
            while (out.size() < MAX_BODY_BYTES) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, minOf(n, MAX_BODY_BYTES - out.size()))
            }
        }
        return out.toString("UTF-8")
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/FetchResult.kt \
        app/src/main/kotlin/net/mrowser/update/ReleaseFetcher.kt
git commit -m "feat(update): fetch the latest release from GitHub"
```

---

### Task 7: The update controller

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/UpdateController.kt`
- Test: `app/src/test/kotlin/net/mrowser/update/UpdateControllerTest.kt`

**Interfaces:**
- Consumes: `UpdateStateRepository` (Task 3), `UpdateCheckPolicy` (Task 4), `ReleaseJson` (Task 2), `FetchResult` (Task 6).
- Produces: `class UpdateController(store, installedVersion, fetch, io, main, now)` with `cachedBanner(): Release?` and `checkIfDue(onResult: (Release?) -> Unit)`.

**Note:** the spec listed this as untested glue. It is written here with its network call and both thread hops injected — the same shape as the tested `stream/StreamSniffer` — so it has no Android imports and its throttle, cache-write and callback paths are covered. This is a refinement of the spec, not a change to its behaviour.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/update/UpdateControllerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.UpdateControllerTest"`
Expected: FAIL — `UpdateController` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/net/mrowser/update/UpdateController.kt`:

```kotlin
package net.mrowser.update

import java.util.concurrent.Executor

/**
 * The update channel's decision path: throttle, fetch, cache, report.
 *
 * No Android imports — the network call, both thread hops and the clock are constructor
 * parameters, the same shape as [net.mrowser.stream.StreamSniffer], so every branch here is
 * unit-tested.
 */
class UpdateController(
    private val store: UpdateStateRepository,
    private val installedVersion: String,
    private val fetch: (installedVersion: String, etag: String?) -> FetchResult,
    private val io: Executor,
    private val main: (Runnable) -> Unit,
    private val now: () -> Long
) {

    /** Cache only — no network, no blocking. Safe to call while laying out the first frame. */
    fun cachedBanner(): Release? =
        UpdateCheckPolicy.bannerFor(installedVersion, store.get().release)

    /**
     * Fires at most once per [UpdateCheckPolicy.CHECK_INTERVAL_MS]. When the check is throttled
     * [onResult] is not called at all — the caller has already drawn [cachedBanner], and calling
     * back with the same answer would only make the line flicker.
     *
     * A failed check deliberately does not bump the timestamp, so a transient outage costs one
     * retry on the next launch rather than a day of silence.
     */
    fun checkIfDue(onResult: (Release?) -> Unit) {
        val state = store.get()
        if (!UpdateCheckPolicy.shouldCheck(now(), state.lastCheckedAt)) return
        io.execute {
            val updated = when (val result = fetch(installedVersion, state.etag)) {
                FetchResult.Failed -> null
                FetchResult.NotModified -> state.copy(lastCheckedAt = now())
                is FetchResult.Body -> state.copy(
                    lastCheckedAt = now(),
                    etag = result.etag ?: state.etag,
                    // A body we cannot read leaves the cache as it was: the last release we did
                    // understand is better than none.
                    release = ReleaseJson.parse(result.text) ?: state.release
                )
            }
            if (updated != null) store.update(updated)
            val banner = UpdateCheckPolicy.bannerFor(installedVersion, (updated ?: state).release)
            main(Runnable { onResult(banner) })
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.update.UpdateControllerTest"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/UpdateController.kt \
        app/src/test/kotlin/net/mrowser/update/UpdateControllerTest.kt
git commit -m "feat(update): throttle, cache and report the daily check"
```

---

### Task 8: Download the APK

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/ApkDownloader.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add the storage permission)

**Interfaces:**
- Consumes: `Release` (Task 1), `DownloadUrl` (Task 5).
- Produces: `sealed class Progress` with `Progress.Running(bytesSoFar: Long, totalBytes: Long)`, `Progress.Done`, `Progress.Failed`; `ApkDownloader.fileName(release: Release): String`; `ApkDownloader.hasStoragePermission(context: Context): Boolean`; `ApkDownloader.enqueue(activity: Activity, release: Release): Long?`; `ApkDownloader.progress(activity: Activity, id: Long): Progress`.

No test: pure `DownloadManager` glue. Its one decision, the URL check, is tested in Task 5.

- [ ] **Step 1: Add the permission**

In `app/src/main/AndroidManifest.xml`, directly after the existing `INTERNET` line:

```xml
    <!-- The update channel saves the APK into public Downloads, where a file manager can reach
         it; mrowser never installs it, so there is no REQUEST_INSTALL_PACKAGES here. Scoped
         storage makes this free from API 29, hence the cap. -->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
```

- [ ] **Step 2: Write the downloader**

Create `app/src/main/kotlin/net/mrowser/update/ApkDownloader.kt`:

```kotlin
package net.mrowser.update

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log

/** How an enqueued download is getting on. */
sealed class Progress {
    /** [totalBytes] is -1 until DownloadManager learns the content length. */
    data class Running(val bytesSoFar: Long, val totalBytes: Long) : Progress()
    object Done : Progress()
    object Failed : Progress()
}

/**
 * Android glue over the system `DownloadManager`.
 *
 * mrowser never installs the APK — that would need `REQUEST_INSTALL_PACKAGES`, which this app
 * deliberately does not hold. The file goes to public `Downloads/` instead, the one place a file
 * manager can still reach under scoped storage, and the user installs it from there.
 *
 * This is also the wrapper a general download manager would build on; expect it to generalise
 * into a `Downloader` taking a URL, a filename and a MIME type.
 */
object ApkDownloader {

    const val APK_MIME = "application/vnd.android.package-archive"

    private const val TAG = "ApkDownloader"

    fun fileName(release: Release): String = "mrowser-${release.version}.apk"

    /** Free from API 29 (scoped storage); a real check below it. */
    fun hasStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** The download id, or null when it could not be started — caller shows the manual route. */
    fun enqueue(activity: Activity, release: Release): Long? {
        if (!DownloadUrl.isAllowed(release.apkUrl)) {
            Log.w(TAG, "refusing an apk url that is not on a GitHub host")
            return null
        }
        if (!hasStoragePermission(activity)) return null
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return null
        return try {
            dm.enqueue(
                DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle("mrowser ${release.version}")
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    // A name that already exists gets a "-1" suffix from DownloadManager; the
                    // dialog reports the name it asked for, which is the one a fresh box sees.
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, fileName(release)
                    )
                    .setAllowedOverMetered(true)
            )
        } catch (e: Exception) {
            Log.w(TAG, "enqueue failed", e)
            null
        }
    }

    fun progress(activity: Activity, id: Long): Progress {
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return Progress.Failed
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return Progress.Failed
        cursor.use {
            if (!it.moveToFirst()) return Progress.Failed
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val soFar =
                it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total =
                it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> Progress.Done
                DownloadManager.STATUS_FAILED -> Progress.Failed
                else -> Progress.Running(soFar, total)
            }
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Confirm the manifest holds no install permission**

Run: `grep -c "REQUEST_INSTALL_PACKAGES" app/src/main/AndroidManifest.xml`
Expected: `0`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/ApkDownloader.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(update): download the apk into public Downloads"
```

---

### Task 9: The update dialog

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/update/UpdateDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Release` (Task 1), `ByteSize` (Task 5), `ApkDownloader` / `Progress` (Task 8).
- Produces: `UpdateDialog.show(activity: Activity, release: Release, ensureStoragePermission: ((Boolean) -> Unit) -> Unit, onObtainium: () -> Unit)`.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, before the closing `</resources>`:

```xml
    <string name="update_available">Update available — %1$s</string>
    <string name="update_title">Update to %1$s</string>
    <string name="update_body">%1$s\n\n%2$s</string>
    <string name="update_download">Download</string>
    <string name="update_obtainium">Automatic updates</string>
    <string name="close">Close</string>
    <string name="update_downloading">Downloading… %1$s / %2$s</string>
    <string name="update_saved">Saved to Downloads/%1$s\n\nOpen it with your file manager to install. Your settings and favorites are kept.</string>
    <string name="update_failed">Download failed. Try again.</string>
    <string name="update_manual">mrowser can\'t save the file on this device.\n\nOpen this on another device:\n%1$s\n\nOr use Obtainium to keep mrowser updated for you.</string>
    <string name="obtainium_title">Obtainium not installed</string>
    <string name="obtainium_message">Obtainium installs apps straight from GitHub Releases and keeps them updated. Open its page to install it?</string>
    <string name="obtainium_open">Open</string>
```

- [ ] **Step 2: Write the dialog**

Create `app/src/main/kotlin/net/mrowser/update/UpdateDialog.kt`:

```kotlin
package net.mrowser.update

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.widget.LinearLayout
import android.widget.TextView
import net.mrowser.R

/**
 * "Update available": version, release notes, size, and a Download that puts the APK in public
 * `Downloads/`. mrowser does not install it — see the design doc for why.
 *
 * Progress is polled rather than watched through `ACTION_DOWNLOAD_COMPLETE`, which on targetSdk
 * 34 would need `RECEIVER_EXPORTED` and lifecycle registration for one line of text. The poll is
 * scoped to the dialog; backing out leaves `DownloadManager` running with its own notification.
 */
object UpdateDialog {

    private const val NOTES_LIMIT = 2000
    private const val POLL_MS = 1000L

    fun show(
        activity: Activity,
        release: Release,
        ensureStoragePermission: ((granted: Boolean) -> Unit) -> Unit,
        onObtainium: () -> Unit
    ) {
        val body = TextView(activity).apply {
            movementMethod = ScrollingMovementMethod()
            // Release notes are network-supplied text: plain into a TextView, never the WebView.
            text = activity.getString(
                R.string.update_body,
                release.notes.take(NOTES_LIMIT).trim(),
                ByteSize.format(release.apkSizeBytes)
            )
        }
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(body)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title, release.version))
            .setView(container)
            // Listener set in onShow instead of here, so tapping Download does not dismiss the
            // dialog — it turns into the progress line.
            .setPositiveButton(R.string.update_download, null)
            .setNeutralButton(R.string.update_obtainium) { _, _ -> onObtainium() }
            .setNegativeButton(R.string.close, null)
            .create()

        val handler = Handler(Looper.getMainLooper())
        var poll: Runnable? = null
        dialog.setOnDismissListener { poll?.let(handler::removeCallbacks) }

        dialog.setOnShowListener {
            val download = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            download.setOnClickListener {
                ensureStoragePermission { granted ->
                    val id = if (granted) ApkDownloader.enqueue(activity, release) else null
                    if (id == null) {
                        body.text = activity.getString(R.string.update_manual, release.htmlUrl)
                        return@ensureStoragePermission
                    }
                    download.isEnabled = false
                    val tick = object : Runnable {
                        override fun run() {
                            when (val p = ApkDownloader.progress(activity, id)) {
                                is Progress.Running -> {
                                    body.text = activity.getString(
                                        R.string.update_downloading,
                                        ByteSize.format(p.bytesSoFar),
                                        ByteSize.format(p.totalBytes)
                                    )
                                    handler.postDelayed(this, POLL_MS)
                                }
                                Progress.Done -> body.text = activity.getString(
                                    R.string.update_saved, ApkDownloader.fileName(release)
                                )
                                Progress.Failed -> {
                                    body.text = activity.getString(R.string.update_failed)
                                    download.isEnabled = true
                                }
                            }
                        }
                    }
                    poll = tick
                    handler.post(tick)
                }
            }
        }
        dialog.show()
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/update/UpdateDialog.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(update): show release notes and run the download"
```

---

### Task 10: The home screen line, wired up

Adding `onUpdate` to `HomeView.bind` breaks `MainActivity`'s call site, so the layout, the view
and the wiring are one task — splitting them would leave a tree that does not compile.

**Files:**
- Modify: `app/src/main/res/layout/home_view.xml` (add a third child after the `ScrollView`)
- Modify: `app/src/main/kotlin/net/mrowser/home/HomeView.kt`
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–9.
- Produces: `HomeView.bind(..., onUpdate: (Release) -> Unit)` — one extra named parameter at the end of the existing list; `HomeView.showUpdate(release: Release?)`; a working feature.

- [ ] **Step 1: Add the button to the layout**

In `app/src/main/res/layout/home_view.xml`, after the closing `</ScrollView>` and before the closing `</LinearLayout>`:

```xml
    <!-- Bottom-left, deliberately quiet: accent text on the same focus_ring the header buttons
         use, not a filled chip. `gone` by default so the favorites grid keeps its full height. -->
    <Button
        android:id="@+id/homeUpdateButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="start"
        android:layout_marginTop="16dp"
        android:background="@drawable/focus_ring"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:text="@string/update_available"
        android:textAllCaps="false"
        android:textColor="@color/accent"
        android:textSize="14sp"
        android:visibility="gone" />
```

- [ ] **Step 2: Hold the button in HomeView**

In `app/src/main/kotlin/net/mrowser/home/HomeView.kt`, add the import:

```kotlin
import net.mrowser.update.Release
```

Add the fields next to the existing `private val grid` / `private val emptyHint` / `private val urlInput`:

```kotlin
    private val updateButton: Button
```

and next to the existing callbacks (`onOpen`, `onSubmitUrl`, …):

```kotlin
    private var onUpdate: (Release) -> Unit = {}
    private var pendingUpdate: Release? = null
```

In `init`, after the existing `findViewById` lines:

```kotlin
        updateButton = findViewById(R.id.homeUpdateButton)
        updateButton.setOnClickListener { pendingUpdate?.let { release -> onUpdate(release) } }
```

- [ ] **Step 3: Extend bind and add showUpdate**

Add `onUpdate: (Release) -> Unit` as the last parameter of `bind(...)`, and assign it alongside the others:

```kotlin
    fun bind(
        repository: FavoritesRepository,
        onOpen: (Favorite) -> Unit,
        onSubmitUrl: (String) -> Unit,
        onEdit: (Favorite) -> Unit,
        onHistory: () -> Unit,
        onSettings: () -> Unit,
        onUpdate: (Release) -> Unit
    ) {
        // ... existing assignments ...
        this.onUpdate = onUpdate
        // ... rest unchanged ...
    }
```

Add, next to `refresh()`:

```kotlin
    /**
     * Show or hide the update line. Called twice per launch — once from the cache while laying
     * out, once more if the daily check turns up something — so it must be idempotent.
     */
    fun showUpdate(release: Release?) {
        pendingUpdate = release
        updateButton.visibility = if (release == null) View.GONE else View.VISIBLE
        if (release != null) {
            updateButton.text = context.getString(R.string.update_available, release.version)
        }
    }
```

At this point `MainActivity` no longer compiles — its `homeView.bind` call is missing the new
argument. The remaining steps fix that; do not commit until Step 13.

- [ ] **Step 4: Add the imports**

In `app/src/main/kotlin/net/mrowser/MainActivity.kt`:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import net.mrowser.update.ApkDownloader
import net.mrowser.update.JsonUpdateStore
import net.mrowser.update.ReleaseFetcher
import net.mrowser.update.UpdateController
import net.mrowser.update.UpdateDialog
```

- [ ] **Step 5: Add the fields**

Next to the existing `private lateinit var settings: JsonSettingsStore`:

```kotlin
    private lateinit var updates: UpdateController
    private lateinit var updateIo: ExecutorService

    /** One outstanding storage-permission ask at a time; see ensureStoragePermission. */
    private var pendingStoragePermission: ((Boolean) -> Unit)? = null
```

- [ ] **Step 6: Build the controller in onCreate**

In `onCreate`, immediately after `seedDefaultFavorites()`:

```kotlin
        updateIo = Executors.newSingleThreadExecutor()
        updates = UpdateController(
            store = JsonUpdateStore(File(filesDir, "update.json")),
            installedVersion = installedVersion(),
            fetch = ReleaseFetcher::fetch,
            io = updateIo,
            main = { task -> runOnUiThread(task) },
            now = { System.currentTimeMillis() }
        )
```

- [ ] **Step 7: Pass onUpdate and draw the line**

Change the existing `homeView.bind(...)` call to add the new argument, and follow it with the two calls that drive the line:

```kotlin
        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } },
            onHistory = { showHistory(fromHome = true) },
            onSettings = { showSettings() },
            onUpdate = { release ->
                UpdateDialog.show(this, release, ::ensureStoragePermission, ::openObtainium)
            }
        )
        // Straight from the cache, so the line is there on the first frame; then once more if
        // today's check turns something up. Once per process launch, not per home show.
        homeView.showUpdate(updates.cachedBanner())
        updates.checkIfDue { homeView.showUpdate(it) }
```

- [ ] **Step 8: Add the three helpers**

Next to the other private helpers in `MainActivity`:

```kotlin
    /** Empty when it cannot be read, which VersionCompare treats as unknown — so, no banner. */
    private fun installedVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

    /**
     * Storage is free from API 29; below it the user is asked once, and only because they tapped
     * Download. The dialog stays ignorant of request codes — it just gets a yes or a no.
     */
    private fun ensureStoragePermission(onResult: (Boolean) -> Unit) {
        if (ApkDownloader.hasStoragePermission(this)) {
            onResult(true)
            return
        }
        pendingStoragePermission = onResult
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
    }

    /** Obtainium is the one route that ends the update problem for good; the README says so too. */
    private fun openObtainium() {
        ExternalIntentLauncher(
            context = this,
            onFallback = { url -> openUrl(url) },
            onNoApp = {
                AlertDialog.Builder(this)
                    .setTitle(R.string.obtainium_title)
                    .setMessage(R.string.obtainium_message)
                    .setPositiveButton(R.string.obtainium_open) { _, _ -> openUrl(OBTAINIUM_URL) }
                    .setNegativeButton(R.string.close, null)
                    .show()
            }
        ).launch(OBTAINIUM_ADD_URL)
    }
```

- [ ] **Step 9: Deliver the permission result**

Add the override next to the other lifecycle overrides:

```kotlin
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_STORAGE) return
        val callback = pendingStoragePermission
        pendingStoragePermission = null
        callback?.invoke(
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        )
    }
```

- [ ] **Step 10: Shut the executor down**

In the existing `onDestroy`, after the service-worker line:

```kotlin
        updateIo.shutdownNow()
```

- [ ] **Step 11: Add the constants**

`MainActivity` already has a `companion object` at the end of the class holding
`CHIP_TIMEOUT_MS`. Add these three lines to it — do not declare a second one:

```kotlin
        private const val REQ_STORAGE = 101
        private const val OBTAINIUM_ADD_URL = "obtainium://add/https://github.com/m-salehi-v/mrowser"
        private const val OBTAINIUM_URL = "https://github.com/ImranR98/Obtainium"
```

- [ ] **Step 12: Build and run the whole suite**

Run: `./gradlew test assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/res/layout/home_view.xml \
        app/src/main/kotlin/net/mrowser/home/HomeView.kt \
        app/src/main/kotlin/net/mrowser/MainActivity.kt
git commit -m "feat(update): surface the update line on the home screen"
```

---

### Task 11: Documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: the finished feature.
- Produces: nothing code depends on.

- [ ] **Step 1: Document the domain in CLAUDE.md**

In `CLAUDE.md`, after the `### adblock/ …` section and before `### home/ + data/ …`, add:

```markdown
### `update/` — the update channel

mrowser is sideload-only, so nothing tells a user on an old APK that a new release exists. On
each process launch `MainActivity` calls `UpdateController.checkIfDue`, which asks GitHub for
`/releases/latest` (stable only — that endpoint excludes drafts and prereleases) **at most once
every 24h**, guarded by the **pure** `UpdateCheckPolicy`. The request carries a `User-Agent`
(GitHub 403s API calls without one) and an `If-None-Match` from the cached ETag, so a repeat
check is a 304 costing no rate-limit quota — the unauthenticated limit is 60/hr **per IP**, and
CGNAT puts many users behind one. The body is parsed by the **pure** `ReleaseJson`, which
requires **exactly one** `.apk` asset and refuses drafts/prereleases, and cached to
`filesDir/update.json` through `UpdateStateRepository` / `JsonUpdateStore` over the **pure**
`UpdateJson`. Every failure logs and returns — there is no user-visible error anywhere in the
check, so a box where `api.github.com` is unreachable behaves exactly as before.

`UpdateController` itself has **no Android imports** (network call, both thread hops and the
clock are injected, like `StreamSniffer`) and is unit-tested. A failed check deliberately does
**not** bump `lastCheckedAt`, so a transient outage costs one retry rather than a day of silence.

The banner is a quiet accent-text button at the **bottom-left of the home overlay**
(`HomeView.showUpdate`), drawn from the cache on the first frame and again if the check turns
something up. It has no dismiss and stores no "seen" flag: the **pure** `UpdateCheckPolicy.bannerFor`
compares the cached release against `PackageManager`'s `versionName` through the **pure**
`VersionCompare`, so installing the new APK clears the line by itself.

`UpdateDialog` shows the notes (plain text — never the WebView), the size (**pure** `ByteSize`)
and a **Download**. **mrowser never installs the APK:** that needs `REQUEST_INSTALL_PACKAGES`,
which would complicate both F-Droid and IzzyOnDroid submission, so `ApkDownloader` hands the URL
— vetted by the **pure** `DownloadUrl` to https on `github.com`/`*.githubusercontent.com` — to
the system `DownloadManager`, which saves it to public `Downloads/mrowser-<version>.apk` for the
user to install from a file manager. The whole permission cost is `WRITE_EXTERNAL_STORAGE` with
`android:maxSdkVersion="28"` (scoped storage makes it free from API 29), asked for only on the
Download tap. Progress is **polled** on the UI handler rather than watched via
`ACTION_DOWNLOAD_COMPLETE`, which on targetSdk 34 would need `RECEIVER_EXPORTED`. When the
permission is denied or `DownloadManager` is missing, the dialog falls back to the release URL
plus the Obtainium route. `ApkDownloader` is the wrapper a general download manager would build
on.
```

- [ ] **Step 2: Correct the repository visibility claim**

In `CLAUDE.md`, under **Release & CI**, change:

```
Remote `origin` = `github.com/m-salehi-v/mrowser` (private).
```

to:

```
Remote `origin` = `github.com/m-salehi-v/mrowser` (public — the update channel depends on
unauthenticated access to its releases API).
```

- [ ] **Step 3: Document it for users in README.md**

In `README.md`, immediately before the existing `### Automatic updates with Obtainium` heading, add:

```markdown
### Update notices

mrowser checks GitHub for a new stable release once a day and, when there is one, shows a quiet
**Update available** line at the bottom of the home screen. Opening it shows the release notes and
a **Download** button that saves the APK to your `Downloads/` folder; install it from there with
your file manager. Your settings, favorites and history are kept — the new APK is signed with the
same key, so it updates in place.

mrowser does not install the APK for you. Doing that requires the `REQUEST_INSTALL_PACKAGES`
permission, and mrowser would rather not hold it. If your TV has no file manager, use Obtainium
below — the dialog's **Automatic updates** button sets it up.
```

- [ ] **Step 4: Verify nothing else claims the repo is private**

Run: `grep -rn "private)" CLAUDE.md README.md`
Expected: no line describing the repository as private.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: document the update channel"
```

---

## Device verification (after Task 11, on the TV)

The unit suite covers every decision; these are the things only hardware shows. Run through them
before merging, and record the result the way the earlier branches' device checks were recorded.

1. **The line appears.** Install a build whose `versionName` is lower than the latest release
   (temporarily set `versionName = "1.0.0"`). Launch, wait a few seconds, return home: the
   bottom-left line reads "Update available — 1.3.0".
2. **It survives a relaunch with no network call.** Force-stop, disable the network, relaunch:
   the line is still there, drawn from `update.json`.
3. **It clears itself.** Install the real current build. The line is gone with no other action.
4. **D-pad reaches it.** From the bottom row of favorites, press DOWN: focus lands on the line
   with a visible ring, OK opens the dialog, BACK closes the dialog and then the home BACK chain
   behaves as before (exit confirm).
5. **Download works.** Open the dialog, press Download. On API 29+ the progress line moves and
   ends at "Saved to Downloads/mrowser-1.3.0.apk". On an API 23–28 box the permission prompt
   appears first; granting it proceeds, denying it shows the manual fallback.
6. **The file is reachable.** With a file manager on the TV, open `Downloads/` and confirm the
   APK is there and installs over the running app without losing favorites.
7. **Backing out mid-download.** Press BACK while downloading: the dialog closes, the system
   notification continues, and reopening home does not restart the download.
8. **No network.** Turn the network off and launch: no line, no toast, no crash, and the app
   behaves exactly as before.
9. **Obtainium button.** With Obtainium installed, the button opens it on mrowser's add-app
   screen. Without it, the "Obtainium not installed" dialog appears and Open loads its page.

## Follow-ups (not in this plan)

- A general download manager for arbitrary links while browsing, built on `ApkDownloader`.
- A beta channel toggle and a skip-this-version action, both deliberately deferred.
