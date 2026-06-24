# Global Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an app-wide Settings overlay (auto-open synced player, preferred subtitle language, cursor speed), persisted to `settings.json` and applied live.

**Architecture:** Mirror the existing Favorites/History clean split — a thin `JsonSettingsStore` over pure `SettingsJson` + plain-data enums. Settings are read live through provider lambdas at each use site (auto-open gate, `bestRequest`, cursor frame), so changes take effect with no restart. The subtitle preference rides on `PlaybackRequest` into `PlayerActivity`, keeping the player free of a settings dependency.

**Tech Stack:** Kotlin, framework `Activity` + XML layouts (no AndroidX beyond Media3), `org.json`, JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-24-global-settings-design.md`

**Refinements vs spec (intentional, simpler):**
1. Entry point is a text **"Settings"** `Button` next to History (reuses `@string/settings`) — not a gear icon; the home header already uses text buttons. No `ic_settings.xml`.
2. The three setting rows are inlined in `settings_view.xml` (distinct ids) — no separate `settings_row.xml`.
3. The subtitle "default" selection flag is derived in `PlayerActivity` (`i == 0 && preferredTextLanguage != null`) rather than stored on `SubtitleTrack` — keeps that data class unchanged.

**Compile-green strategy:** new constructor params (`CursorController.speedMultiplier`, `StreamSniffer.subtitlePref`) and the new `PlaybackRequest.preferredTextLanguage` field all get defaults, so every task compiles before the final wiring task swaps in real values.

---

## Task 1: Settings data model (enums + record)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/SubtitleLanguagePref.kt`
- Create: `app/src/main/kotlin/net/mrowser/data/CursorSpeed.kt`
- Create: `app/src/main/kotlin/net/mrowser/data/Settings.kt`

No dedicated test — these are plain data with no behavior; their values are exercised by `SettingsJsonTest` (Task 2) and `SubtitlePlanTest` (Task 7).

- [ ] **Step 1: Create `SubtitleLanguagePref.kt`**

```kotlin
package net.mrowser.data

/**
 * Default-subtitle preference. [code] is the BCP-47 language tag (null = no preference);
 * [trackLabel] is the player-facing label baked onto the first side-loaded subtitle.
 */
enum class SubtitleLanguagePref(val code: String?, val trackLabel: String?) {
    PERSIAN("fa", "Persian"),
    ENGLISH("en", "English"),
    OFF(null, null)
}
```

- [ ] **Step 2: Create `CursorSpeed.kt`**

```kotlin
package net.mrowser.data

/** D-pad cursor speed, as a multiplier over the CursorGeometry base/max ramp. */
enum class CursorSpeed(val multiplier: Float) {
    SLOW(0.6f),
    NORMAL(1.0f),
    FAST(1.5f)
}
```

- [ ] **Step 3: Create `Settings.kt`**

```kotlin
package net.mrowser.data

/** App-wide settings. Defaults are the shipped values. Immutable — update via copy(). */
data class Settings(
    val autoOpenPlayer: Boolean = true,
    val subtitleLanguage: SubtitleLanguagePref = SubtitleLanguagePref.ENGLISH,
    val cursorSpeed: CursorSpeed = CursorSpeed.NORMAL
)
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/data/SubtitleLanguagePref.kt \
        app/src/main/kotlin/net/mrowser/data/CursorSpeed.kt \
        app/src/main/kotlin/net/mrowser/data/Settings.kt
git commit -m "feat: add Settings data model and enums"
```

---

## Task 2: SettingsJson (pure ser/de)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/SettingsJson.kt`
- Test: `app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt`:

```kotlin
package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsJsonTest {

    @Test fun `round trips all fields`() {
        val s = Settings(
            autoOpenPlayer = false,
            subtitleLanguage = SubtitleLanguagePref.PERSIAN,
            cursorSpeed = CursorSpeed.FAST
        )
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `missing fields fall back to defaults`() {
        assertEquals(Settings(), SettingsJson.fromJson("{}"))
    }

    @Test fun `blank input is all defaults`() {
        assertEquals(Settings(), SettingsJson.fromJson(""))
    }

    @Test fun `corrupt input is all defaults`() {
        assertEquals(Settings(), SettingsJson.fromJson("not json"))
    }

    @Test fun `unknown enum name falls back to default`() {
        val json = """{"autoOpenPlayer":true,"subtitleLanguage":"KLINGON","cursorSpeed":"WARP"}"""
        val s = SettingsJson.fromJson(json)
        assertEquals(SubtitleLanguagePref.ENGLISH, s.subtitleLanguage)
        assertEquals(CursorSpeed.NORMAL, s.cursorSpeed)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "net.mrowser.data.SettingsJsonTest"`
Expected: FAIL / compile error — `SettingsJson` is unresolved.

- [ ] **Step 3: Write the implementation**

`app/src/main/kotlin/net/mrowser/data/SettingsJson.kt`:

```kotlin
package net.mrowser.data

import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of Settings. Missing/unparseable fields → defaults. */
object SettingsJson {

    fun toJson(settings: Settings): String =
        JSONObject()
            .put("autoOpenPlayer", settings.autoOpenPlayer)
            .put("subtitleLanguage", settings.subtitleLanguage.name)
            .put("cursorSpeed", settings.cursorSpeed.name)
            .toString()

    fun fromJson(json: String): Settings {
        if (json.isBlank()) return Settings()
        return try {
            val o = JSONObject(json)
            val defaults = Settings()
            Settings(
                autoOpenPlayer = o.optBoolean("autoOpenPlayer", defaults.autoOpenPlayer),
                subtitleLanguage = enumOrDefault(o.optString("subtitleLanguage"), defaults.subtitleLanguage),
                cursorSpeed = enumOrDefault(o.optString("cursorSpeed"), defaults.cursorSpeed)
            )
        } catch (e: JSONException) {
            Settings()
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        T::class.java.enumConstants?.firstOrNull { it.name == name } ?: default
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "net.mrowser.data.SettingsJsonTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/data/SettingsJson.kt \
        app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt
git commit -m "feat: add pure SettingsJson serialization"
```

---

## Task 3: SettingsRepository + JsonSettingsStore

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/SettingsRepository.kt`
- Create: `app/src/main/kotlin/net/mrowser/data/JsonSettingsStore.kt`

No unit test — file I/O glue, mirroring `JsonFavoritesStore` (also untested); the logic it delegates to (`SettingsJson`) is covered by Task 2.

- [ ] **Step 1: Create the interface**

`app/src/main/kotlin/net/mrowser/data/SettingsRepository.kt`:

```kotlin
package net.mrowser.data

interface SettingsRepository {
    fun get(): Settings
    fun update(settings: Settings)
}
```

- [ ] **Step 2: Create the store**

`app/src/main/kotlin/net/mrowser/data/JsonSettingsStore.kt`:

```kotlin
package net.mrowser.data

import java.io.File

/** SettingsRepository backed by a JSON file; pure logic delegated to SettingsJson. */
class JsonSettingsStore(private val file: File) : SettingsRepository {

    private var current: Settings =
        if (file.exists()) SettingsJson.fromJson(file.readText()) else Settings()

    override fun get(): Settings = current

    override fun update(settings: Settings) {
        current = settings
        persist()
    }

    private fun persist() {
        runCatching { file.writeText(SettingsJson.toJson(current)) }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/data/SettingsRepository.kt \
        app/src/main/kotlin/net/mrowser/data/JsonSettingsStore.kt
git commit -m "feat: add JsonSettingsStore repository"
```

---

## Task 4: CursorGeometry speed multiplier (pure)

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/web/CursorGeometry.kt`
- Test: `app/src/test/kotlin/net/mrowser/web/CursorGeometryTest.kt`

- [ ] **Step 1: Add the failing tests**

Append these three tests inside `CursorGeometryTest` (before the closing brace):

```kotlin
    @Test fun `multiplier scales the base speed`() {
        assertEquals(CursorGeometry.BASE_SPEED_PX * 0.5f, CursorGeometry.speedForHoldMs(0L, 0.5f), 0.001f)
    }

    @Test fun `multiplier scales the max speed`() {
        assertEquals(CursorGeometry.MAX_SPEED_PX * 2f, CursorGeometry.speedForHoldMs(CursorGeometry.ACCEL_MS, 2f), 0.001f)
    }

    @Test fun `default multiplier leaves speed unchanged`() {
        assertEquals(CursorGeometry.speedForHoldMs(0L), CursorGeometry.speedForHoldMs(0L, 1f), 0.001f)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "net.mrowser.web.CursorGeometryTest"`
Expected: FAIL / compile error — `speedForHoldMs` takes one argument.

- [ ] **Step 3: Implement the multiplier**

In `CursorGeometry.kt`, replace the existing `speedForHoldMs` function with:

```kotlin
    /** Ramps base -> max linearly over ACCEL_MS, then holds at max; scaled by [multiplier]. */
    fun speedForHoldMs(heldMs: Long, multiplier: Float = 1f): Float {
        val base = when {
            heldMs <= 0L -> BASE_SPEED_PX
            heldMs >= ACCEL_MS -> MAX_SPEED_PX
            else -> {
                val t = heldMs.toFloat() / ACCEL_MS
                BASE_SPEED_PX + (MAX_SPEED_PX - BASE_SPEED_PX) * t
            }
        }
        return base * multiplier
    }
```

- [ ] **Step 4: Run to verify all pass**

Run: `./gradlew test --tests "net.mrowser.web.CursorGeometryTest"`
Expected: PASS (existing + 3 new tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/web/CursorGeometry.kt \
        app/src/test/kotlin/net/mrowser/web/CursorGeometryTest.kt
git commit -m "feat: add cursor speed multiplier to CursorGeometry"
```

---

## Task 5: CursorController speed provider

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/web/CursorController.kt`

No unit test (drives a real `WebView`). The default `{ 1f }` provider keeps the current `MainActivity` call site valid until Task 12.

- [ ] **Step 1: Add the constructor param**

Replace the constructor header:

```kotlin
class CursorController(
    private val webView: WebView,
    private val invalidate: () -> Unit
) {
```

with:

```kotlin
class CursorController(
    private val webView: WebView,
    private val speedMultiplier: () -> Float = { 1f },
    private val invalidate: () -> Unit
) {
```

(The existing `CursorController(webView) { layout.invalidate() }` call still compiles: the trailing lambda binds to the last param `invalidate`, and `speedMultiplier` uses its default.)

- [ ] **Step 2: Use the multiplier in the mover**

In the `mover` runnable, replace:

```kotlin
            val speed = CursorGeometry.speedForHoldMs(held)
```

with:

```kotlin
            val speed = CursorGeometry.speedForHoldMs(held, speedMultiplier())
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/web/CursorController.kt
git commit -m "feat: read cursor speed multiplier in CursorController"
```

---

## Task 6: PlaybackRequest preferred text language

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/stream/PlaybackRequest.kt`
- Test: `app/src/test/kotlin/net/mrowser/stream/PlaybackRequestTest.kt`

- [ ] **Step 1: Add the failing tests**

Append inside `PlaybackRequestTest` (before the closing brace):

```kotlin
    @Test fun `round trips preferred text language`() {
        val req = PlaybackRequest("https://x/v.m3u8", mapOf("User-Agent" to "UA"), emptyList(), "t", "en")
        assertEquals(req, PlaybackRequest.fromJson(req.toJson()))
    }

    @Test fun `null preferred text language round trips as null`() {
        val req = PlaybackRequest("https://x/v.m3u8", mapOf("User-Agent" to "UA"), emptyList(), "t", null)
        val restored = PlaybackRequest.fromJson(req.toJson())
        assertEquals(req, restored)
        assertEquals(null, restored.preferredTextLanguage)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "net.mrowser.stream.PlaybackRequestTest"`
Expected: FAIL / compile error — `PlaybackRequest` has no 5th parameter.

- [ ] **Step 3: Add the field + (de)serialization**

In `PlaybackRequest.kt`, change the data class header from:

```kotlin
data class PlaybackRequest(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleTrack>,
    val title: String
) {
```

to:

```kotlin
data class PlaybackRequest(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleTrack>,
    val title: String,
    val preferredTextLanguage: String? = null
) {
```

Replace the `toJson` body's final `return` statement:

```kotlin
        return JSONObject()
            .put("url", url).put("headers", h).put("subtitles", subs).put("title", title)
            .toString()
```

with:

```kotlin
        val o = JSONObject()
            .put("url", url).put("headers", h).put("subtitles", subs).put("title", title)
        preferredTextLanguage?.let { o.put("preferredTextLanguage", it) }
        return o.toString()
```

In `fromJson`, replace the final `return`:

```kotlin
            return PlaybackRequest(o.getString("url"), headers, subs, o.getString("title"))
```

with:

```kotlin
            val pref = if (o.has("preferredTextLanguage")) o.getString("preferredTextLanguage") else null
            return PlaybackRequest(o.getString("url"), headers, subs, o.getString("title"), pref)
```

- [ ] **Step 4: Run to verify all pass**

Run: `./gradlew test --tests "net.mrowser.stream.PlaybackRequestTest"`
Expected: PASS (existing 2 + 2 new — existing ones use the 4-arg constructor, `preferredTextLanguage` defaults to null, omitted from JSON, restored as null).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/stream/PlaybackRequest.kt \
        app/src/test/kotlin/net/mrowser/stream/PlaybackRequestTest.kt
git commit -m "feat: carry preferredTextLanguage on PlaybackRequest"
```

---

## Task 7: SubtitlePlan (pure subtitle assembly)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/stream/SubtitlePlan.kt`
- Test: `app/src/test/kotlin/net/mrowser/stream/SubtitlePlanTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/net/mrowser/stream/SubtitlePlanTest.kt`:

```kotlin
package net.mrowser.stream

import net.mrowser.data.SubtitleLanguagePref
import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitlePlanTest {

    private fun sub(url: String, seq: Int) = StreamCandidate(url, MediaKind.SUBTITLE, seq)

    @Test fun `english tags first track en and prefers en`() {
        val plan = SubtitlePlan.build(listOf(sub("https://x/a.vtt", 1)), SubtitleLanguagePref.ENGLISH)
        assertEquals("en", plan.preferredLanguage)
        assertEquals("en", plan.tracks[0].language)
        assertEquals("English", plan.tracks[0].label)
    }

    @Test fun `persian tags first track fa and prefers fa`() {
        val plan = SubtitlePlan.build(listOf(sub("https://x/a.vtt", 1)), SubtitleLanguagePref.PERSIAN)
        assertEquals("fa", plan.preferredLanguage)
        assertEquals("fa", plan.tracks[0].language)
        assertEquals("Persian", plan.tracks[0].label)
    }

    @Test fun `off marks no preference and uses a generic label`() {
        val plan = SubtitlePlan.build(listOf(sub("https://x/a.vtt", 1)), SubtitleLanguagePref.OFF)
        assertNull(plan.preferredLanguage)
        assertEquals("und", plan.tracks[0].language)
        assertEquals("Subtitle 1", plan.tracks[0].label)
    }

    @Test fun `extra tracks get generic labels, und language, and srt mime`() {
        val plan = SubtitlePlan.build(
            listOf(sub("https://x/a.vtt", 1), sub("https://x/b.srt", 2)),
            SubtitleLanguagePref.ENGLISH
        )
        assertEquals("application/x-subrip", plan.tracks[1].mimeType)
        assertEquals("und", plan.tracks[1].language)
        assertEquals("Subtitle 2", plan.tracks[1].label)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "net.mrowser.stream.SubtitlePlanTest"`
Expected: FAIL / compile error — `SubtitlePlan` is unresolved.

- [ ] **Step 3: Write the implementation**

`app/src/main/kotlin/net/mrowser/stream/SubtitlePlan.kt`:

```kotlin
package net.mrowser.stream

import net.mrowser.data.SubtitleLanguagePref

/**
 * Pure: turns the selected subtitle candidates + a language preference into the player's
 * subtitle tracks and the preferred-language hint. The first track carries the preferred
 * language + label when a preference is set; every other track — and all tracks when the
 * preference is OFF — gets a generic "Subtitle N" label with an undetermined language.
 */
object SubtitlePlan {

    data class Plan(val tracks: List<SubtitleTrack>, val preferredLanguage: String?)

    fun build(subs: List<StreamCandidate>, pref: SubtitleLanguagePref): Plan {
        val code = pref.code
        val tracks = subs.mapIndexed { idx, c ->
            val isSrt = c.url.substringBefore('?').lowercase().endsWith(".srt")
            val mime = if (isSrt) "application/x-subrip" else "text/vtt"
            if (idx == 0 && code != null) {
                SubtitleTrack(c.url, mime, code, pref.trackLabel ?: "Subtitle 1")
            } else {
                SubtitleTrack(c.url, mime, "und", "Subtitle ${idx + 1}")
            }
        }
        return Plan(tracks, code)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "net.mrowser.stream.SubtitlePlanTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/stream/SubtitlePlan.kt \
        app/src/test/kotlin/net/mrowser/stream/SubtitlePlanTest.kt
git commit -m "feat: add pure SubtitlePlan subtitle assembly"
```

---

## Task 8: Wire SubtitlePlan + preference into StreamSniffer

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/stream/StreamSniffer.kt`

No unit test — `bestRequest` touches `CookieManager` (Android). The branching logic it now calls (`SubtitlePlan`) is covered by Task 7. The `subtitlePref` default keeps the `MainActivity` construction valid until Task 12.

- [ ] **Step 1: Add the import**

At the top of `StreamSniffer.kt`, add under the existing imports:

```kotlin
import net.mrowser.data.SubtitleLanguagePref
```

- [ ] **Step 2: Add the constructor param**

Change the constructor from:

```kotlin
class StreamSniffer(
    private val userAgent: () -> String,
    private val onStreamAvailable: () -> Unit,
    private val onCleared: () -> Unit
) {
```

to:

```kotlin
class StreamSniffer(
    private val userAgent: () -> String,
    private val onStreamAvailable: () -> Unit,
    private val onCleared: () -> Unit,
    private val subtitlePref: () -> SubtitleLanguagePref = { SubtitleLanguagePref.ENGLISH }
) {
```

- [ ] **Step 3: Replace the subtitle assembly in `bestRequest`**

Replace this block:

```kotlin
        val subs = StreamCandidateSelector.selectSubtitles(candidates).mapIndexed { idx, c ->
            val isSrt = c.url.substringBefore('?').lowercase().endsWith(".srt")
            val mime = if (isSrt) "application/x-subrip" else "text/vtt"
            // The first subtitle on a Persian-default page is Persian; the rest get distinct generic labels.
            if (idx == 0) SubtitleTrack(c.url, mime, "fa", "Persian")
            else SubtitleTrack(c.url, mime, "und", "Subtitle ${idx + 1}")
        }
        return PlaybackRequest(best.url, headers, subs, pageUrl)
```

with:

```kotlin
        val plan = SubtitlePlan.build(StreamCandidateSelector.selectSubtitles(candidates), subtitlePref())
        return PlaybackRequest(best.url, headers, plan.tracks, pageUrl, plan.preferredLanguage)
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/stream/StreamSniffer.kt
git commit -m "feat: use SubtitlePlan + preference in StreamSniffer.bestRequest"
```

---

## Task 9: PlayerActivity honors the preferred language

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/player/PlayerActivity.kt`

No unit test (Android `Activity`). Behavioral change: stop hard-coding `"fa"`; mark a default subtitle only when a preference is set.

- [ ] **Step 1: Read the preferred language from the request**

Replace:

```kotlin
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setPreferredTextLanguage("fa").build()
        }
```

with:

```kotlin
        val trackSelector = DefaultTrackSelector(this).apply {
            request.preferredTextLanguage?.let {
                parameters = buildUponParameters().setPreferredTextLanguage(it).build()
            }
        }
```

- [ ] **Step 2: Derive the default subtitle flag from the preference**

In `buildMediaItem`, replace:

```kotlin
            .setSubtitleConfigurations(request.subtitles.mapIndexed { i, s -> s.toConfig(default = i == 0) })
```

with:

```kotlin
            .setSubtitleConfigurations(
                request.subtitles.mapIndexed { i, s ->
                    s.toConfig(default = i == 0 && request.preferredTextLanguage != null)
                }
            )
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/player/PlayerActivity.kt
git commit -m "feat: PlayerActivity honors request preferred subtitle language"
```

---

## Task 10: Settings strings + overlay layout

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/settings_view.xml`

- [ ] **Step 1: Add the strings**

In `strings.xml`, add before the closing `</resources>` (note: `@string/settings` already exists and is reused for the page title):

```xml
    <string name="auto_open_title">Auto-open synced player</string>
    <string name="subtitle_language_title">Preferred subtitle language</string>
    <string name="cursor_speed_title">Cursor speed</string>
    <string name="on">On</string>
    <string name="off">Off</string>
    <string name="subtitle_persian">Persian</string>
    <string name="subtitle_english">English</string>
    <string name="subtitle_off">Off</string>
    <string name="cursor_slow">Slow</string>
    <string name="cursor_normal">Normal</string>
    <string name="cursor_fast">Fast</string>
```

- [ ] **Step 2: Create the overlay layout**

`app/src/main/res/layout/settings_view.xml` (three inlined rows, each focusable with the existing `focus_ring` background):

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/surface"
    android:padding="32dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingBottom="24dp"
        android:text="@string/settings"
        android:textColor="@color/on_surface"
        android:textSize="28sp"
        android:textStyle="bold" />

    <LinearLayout
        android:id="@+id/settingsAutoOpenRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@drawable/focus_ring"
        android:padding="18dp"
        android:focusable="true">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/auto_open_title"
            android:textColor="@color/on_surface"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/settingsAutoOpenValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/accent"
            android:textSize="18sp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/settingsSubtitleRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@drawable/focus_ring"
        android:padding="18dp"
        android:focusable="true">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/subtitle_language_title"
            android:textColor="@color/on_surface"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/settingsSubtitleValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/accent"
            android:textSize="18sp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/settingsCursorRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@drawable/focus_ring"
        android:padding="18dp"
        android:focusable="true">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/cursor_speed_title"
            android:textColor="@color/on_surface"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/settingsCursorValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/accent"
            android:textSize="18sp" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew processDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/settings_view.xml
git commit -m "feat: add settings overlay layout and strings"
```

---

## Task 11: SettingsView + register the overlay

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/home/SettingsView.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`

No unit test (Android `View`); verified by build. Not yet reachable — Task 12 wires the entry point and repository.

- [ ] **Step 1: Create the view**

`app/src/main/kotlin/net/mrowser/home/SettingsView.kt`:

```kotlin
package net.mrowser.home

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.data.CursorSpeed
import net.mrowser.data.Settings
import net.mrowser.data.SettingsRepository
import net.mrowser.data.SubtitleLanguagePref

/** Settings overlay: auto-open toggle + subtitle-language and cursor-speed pickers. */
class SettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val autoOpenRow: View
    private val subtitleRow: View
    private val cursorRow: View
    private val autoOpenValue: TextView
    private val subtitleValue: TextView
    private val cursorValue: TextView

    private var repository: SettingsRepository? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.settings_view, this, true)
        autoOpenRow = findViewById(R.id.settingsAutoOpenRow)
        subtitleRow = findViewById(R.id.settingsSubtitleRow)
        cursorRow = findViewById(R.id.settingsCursorRow)
        autoOpenValue = findViewById(R.id.settingsAutoOpenValue)
        subtitleValue = findViewById(R.id.settingsSubtitleValue)
        cursorValue = findViewById(R.id.settingsCursorValue)

        autoOpenRow.setOnClickListener { toggleAutoOpen() }
        subtitleRow.setOnClickListener { pickSubtitle() }
        cursorRow.setOnClickListener { pickCursor() }
    }

    fun bind(repository: SettingsRepository) {
        this.repository = repository
    }

    fun show() {
        visibility = View.VISIBLE
        render()
        // Post: a synchronous requestFocus right after VISIBLE can fail before the layout
        // pass, leaving nothing focused (matches HomeView/HistoryView).
        post { restoreFocus() }
    }

    fun hide() {
        visibility = View.GONE
    }

    /** Re-seat D-pad focus on the first row. Returns false if it couldn't take focus. */
    fun restoreFocus(): Boolean = autoOpenRow.requestFocus()

    private fun current(): Settings = repository?.get() ?: Settings()

    private fun render() {
        val s = current()
        autoOpenValue.setText(if (s.autoOpenPlayer) R.string.on else R.string.off)
        subtitleValue.setText(subtitleLabelRes(s.subtitleLanguage))
        cursorValue.setText(cursorLabelRes(s.cursorSpeed))
    }

    private fun toggleAutoOpen() {
        val s = current()
        repository?.update(s.copy(autoOpenPlayer = !s.autoOpenPlayer))
        render()
    }

    private fun pickSubtitle() {
        val options = listOf(
            SubtitleLanguagePref.PERSIAN,
            SubtitleLanguagePref.ENGLISH,
            SubtitleLanguagePref.OFF
        )
        val labels = options.map { context.getString(subtitleLabelRes(it)) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.subtitle_language_title)
            .setItems(labels) { _, which ->
                repository?.update(current().copy(subtitleLanguage = options[which]))
                render()
            }
            .show()
    }

    private fun pickCursor() {
        val options = listOf(CursorSpeed.SLOW, CursorSpeed.NORMAL, CursorSpeed.FAST)
        val labels = options.map { context.getString(cursorLabelRes(it)) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.cursor_speed_title)
            .setItems(labels) { _, which ->
                repository?.update(current().copy(cursorSpeed = options[which]))
                render()
            }
            .show()
    }

    private fun subtitleLabelRes(p: SubtitleLanguagePref): Int = when (p) {
        SubtitleLanguagePref.PERSIAN -> R.string.subtitle_persian
        SubtitleLanguagePref.ENGLISH -> R.string.subtitle_english
        SubtitleLanguagePref.OFF -> R.string.subtitle_off
    }

    private fun cursorLabelRes(c: CursorSpeed): Int = when (c) {
        CursorSpeed.SLOW -> R.string.cursor_slow
        CursorSpeed.NORMAL -> R.string.cursor_normal
        CursorSpeed.FAST -> R.string.cursor_fast
    }
}
```

- [ ] **Step 2: Register the overlay in `activity_main.xml`**

Immediately after the `<net.mrowser.home.HistoryView .../>` element (and before the closing `</FrameLayout>`), add:

```xml
    <net.mrowser.home.SettingsView
        android:id="@+id/settingsView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone" />
```

- [ ] **Step 3: Verify it builds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/home/SettingsView.kt \
        app/src/main/res/layout/activity_main.xml
git commit -m "feat: add SettingsView overlay"
```

---

## Task 12: Entry point + MainActivity wiring

**Files:**
- Modify: `app/src/main/res/layout/home_view.xml`
- Modify: `app/src/main/kotlin/net/mrowser/home/HomeView.kt`
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt`

This task makes everything live: the home "Settings" button, the store, the three provider wires, the auto-open gate, and BACK/focus handling. Verified by the full build + test suite.

- [ ] **Step 1: Add the "Settings" button to the home header**

In `home_view.xml`, immediately after the `homeHistoryButton` `<Button>` element (still inside the header `LinearLayout`), add:

```xml
        <Button
            android:id="@+id/homeSettingsButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:background="@drawable/focus_ring"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:text="@string/settings"
            android:textColor="@color/on_surface" />
```

- [ ] **Step 2: Wire the button in `HomeView`**

In `HomeView.kt`, add the callback field next to the other `onX` fields:

```kotlin
    private var onSettings: () -> Unit = {}
```

In the `init` block, after the `homeHistoryButton` listener, add:

```kotlin
        findViewById<Button>(R.id.homeSettingsButton).setOnClickListener { onSettings() }
```

Change the `bind` signature from:

```kotlin
    fun bind(
        repository: FavoritesRepository,
        onOpen: (Favorite) -> Unit,
        onSubmitUrl: (String) -> Unit,
        onEdit: (Favorite) -> Unit,
        onHistory: () -> Unit
    ) {
        this.repository = repository
        this.onOpen = onOpen
        this.onSubmitUrl = onSubmitUrl
        this.onEdit = onEdit
        this.onHistory = onHistory
    }
```

to:

```kotlin
    fun bind(
        repository: FavoritesRepository,
        onOpen: (Favorite) -> Unit,
        onSubmitUrl: (String) -> Unit,
        onEdit: (Favorite) -> Unit,
        onHistory: () -> Unit,
        onSettings: () -> Unit
    ) {
        this.repository = repository
        this.onOpen = onOpen
        this.onSubmitUrl = onSubmitUrl
        this.onEdit = onEdit
        this.onHistory = onHistory
        this.onSettings = onSettings
    }
```

- [ ] **Step 3: Add the import + fields in `MainActivity`**

Add the import next to the other `net.mrowser.data` imports:

```kotlin
import net.mrowser.data.JsonSettingsStore
```

Add the import next to the other `net.mrowser.home` imports:

```kotlin
import net.mrowser.home.SettingsView
```

Add the fields next to `favorites` / `history`:

```kotlin
    private lateinit var settings: JsonSettingsStore
    private lateinit var settingsView: SettingsView
```

- [ ] **Step 4: Find the view + construct the store**

In `onCreate`, next to `historyView = findViewById(R.id.historyView)`, add:

```kotlin
        settingsView = findViewById(R.id.settingsView)
```

Next to the favorites/history store construction, add (the store must exist before `sniffer`, whose callback reads it):

```kotlin
        settings = JsonSettingsStore(File(filesDir, "settings.json"))
```

- [ ] **Step 5: Gate auto-open + pass the subtitle preference**

Replace the `sniffer` construction:

```kotlin
        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = { runOnUiThread { showChip(); handoff.play() } },
            onCleared = { playChip.visibility = View.GONE }
        )
```

with:

```kotlin
        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = {
                runOnUiThread {
                    showChip()
                    if (settings.get().autoOpenPlayer) handoff.play()
                }
            },
            onCleared = { playChip.visibility = View.GONE },
            subtitlePref = { settings.get().subtitleLanguage }
        )
```

- [ ] **Step 6: Pass the cursor-speed multiplier**

Replace:

```kotlin
        val cursor = CursorController(webView) { layout.invalidate() }
```

with:

```kotlin
        val cursor = CursorController(webView, { settings.get().cursorSpeed.multiplier }) { layout.invalidate() }
```

- [ ] **Step 7: Bind the settings overlay + home callback**

After the existing `homeView.bind(...)` call, add its new argument. Change:

```kotlin
        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } },
            onHistory = { showHistory(fromHome = true) }
        )
```

to:

```kotlin
        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } },
            onHistory = { showHistory(fromHome = true) },
            onSettings = { showSettings() }
        )
```

After the `historyView.bind(...)` call, add:

```kotlin
        settingsView.bind(settings)
```

- [ ] **Step 8: Add `showSettings()` + hide it from `openUrl`**

Add this method next to `showHistory`:

```kotlin
    private fun showSettings() {
        // Focus-modal: hide the other overlays so window-global D-pad focus search
        // can't escape into them (same rule as showHistory).
        homeView.hide()
        historyView.hide()
        settingsView.show()
    }
```

In `openUrl`, after `historyView.hide()`, add:

```kotlin
        settingsView.hide()
```

- [ ] **Step 9: Handle BACK + focus recovery for the settings overlay**

In `onBackPressed`, add a `settingsView` branch as the first `when` case:

```kotlin
    override fun onBackPressed() {
        when {
            settingsView.visibility == View.VISIBLE -> {
                settingsView.hide()
                showHome()
            }
            historyView.visibility == View.VISIBLE -> {
                historyView.hide()
                // Return to wherever history was opened from.
                if (historyFromHome) showHome() else layout.requestFocus()
            }
            homeView.visibility == View.VISIBLE -> confirmExit()
            else -> confirmCloseTab()
        }
    }
```

In `dispatchKeyEvent`, add `settingsView` as the first recovery case:

```kotlin
            val recovered = when {
                settingsView.visibility == View.VISIBLE -> settingsView.restoreFocus()
                historyView.visibility == View.VISIBLE -> historyView.restoreFocus()
                homeView.visibility == View.VISIBLE -> homeView.restoreFocus()
                else -> layout.requestFocus()
            }
```

In `onResume`, guard the chip against the settings overlay. Change:

```kotlin
        if (::sniffer.isInitialized && sniffer.hasStream() && homeView.visibility != View.VISIBLE) showChip()
```

to:

```kotlin
        if (::sniffer.isInitialized && sniffer.hasStream() &&
            homeView.visibility != View.VISIBLE && settingsView.visibility != View.VISIBLE) showChip()
```

- [ ] **Step 10: Build + run the full test suite**

Run: `./gradlew test assembleDebug`
Expected: BUILD SUCCESSFUL; all unit tests pass (existing + `SettingsJsonTest`, `SubtitlePlanTest`, extended `CursorGeometryTest`/`PlaybackRequestTest`).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/res/layout/home_view.xml \
        app/src/main/kotlin/net/mrowser/home/HomeView.kt \
        app/src/main/kotlin/net/mrowser/MainActivity.kt
git commit -m "feat: wire global settings overlay, providers, and entry point"
```

---

## Manual verification (on a TV / emulator, after Task 12)

The unit tests cover the pure logic; these check the Android wiring that can't be unit-tested:

- [ ] Home → **Settings** button opens the overlay; D-pad lands on the first row.
- [ ] Toggle **Auto-open synced player** to Off → browse to a stream → only the play chip shows (no auto-launch); activating the chip launches the player.
- [ ] Toggle back to On → next detected stream auto-launches.
- [ ] **Preferred subtitle language** = Off → player starts with no subtitle selected; CC button still lists tracks. Set to Persian/English → first track is auto-selected with that label.
- [ ] **Cursor speed** = Slow/Fast → cursor visibly slower/faster while held.
- [ ] Settings persist across an app restart (force-stop + relaunch).
- [ ] BACK from Settings returns to Home; BACK again shows the Exit dialog.

---

## Self-review notes

- **Spec coverage:** auto-open gate (Task 12), subtitle preference end-to-end (Tasks 1/6/7/8/9), cursor speed (Tasks 1/4/5/12), storage (Tasks 1/2/3), overlay + entry point (Tasks 10/11/12), tests (Tasks 2/4/6/7). The spec's `ic_settings.xml` / `settings_row.xml` are intentionally dropped (see Refinements).
- **Type consistency:** `SubtitlePlan.build(List<StreamCandidate>, SubtitleLanguagePref): Plan(tracks, preferredLanguage)`; `Settings(autoOpenPlayer, subtitleLanguage, cursorSpeed)`; `SettingsRepository.get()/update(Settings)`; `CursorController(webView, speedMultiplier, invalidate)`; `StreamSniffer(..., subtitlePref)`; `PlaybackRequest(..., preferredTextLanguage)`. Names match across all tasks.
