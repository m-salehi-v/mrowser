# Milestone B — Native Video Handoff — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sniff the page's HLS stream while browsing and play it in a native Media3/ExoPlayer (correct A/V sync, Persian subtitles) when the user clicks a floating "Play synced" chip.

**Architecture:** `WebViewClient.shouldInterceptRequest` feeds URLs to a `StreamSniffer`; pure classification/selection (`MediaUrlClassifier`, `StreamCandidateSelector`) is unit-tested. A floating chip (activated via the cursor) hands the best candidate — with forwarded cookies/Referer/UA — to a fullscreen `PlayerActivity` running ExoPlayer with tunneling and side-loaded subtitles. Builds on Milestone A.

**Tech Stack:** Kotlin, AGP 9.1.1 (built-in Kotlin), Gradle 9.3.1, JDK 17, classic Views, Media3/ExoPlayer 1.10.1 (exoplayer + hls + ui), `org.json`, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-06-18-milestone-b-video-handoff-design.md`

**Conventions:** Conventional one-line commits, no AI attribution. Absolute paths under `/Users/mohammad/Projects/mrowser`. Build with `/Users/mohammad/Projects/mrowser/gradlew`.

---

## File map

| File | Responsibility |
|------|----------------|
| `gradle/libs.versions.toml` | Add media3 + org.json test dep (modify) |
| `app/build.gradle.kts` | Add media3 dependencies (modify) |
| `stream/MediaUrlClassifier.kt` | Pure URL classification + ad-host check (tested) |
| `stream/StreamCandidate.kt` | Candidate data |
| `stream/StreamCandidateSelector.kt` | Pure best-manifest / subtitle selection (tested) |
| `stream/PlaybackRequest.kt` | Request data + JSON (tested) |
| `stream/StreamSniffer.kt` | Collect candidates, assemble request |
| `stream/SniffingWebViewClient.kt` | WebViewClient → sniffer |
| `web/CursorLayout.kt` | Add chip hit-test on OK (modify) |
| `handoff/HandoffController.kt` | Chip → launch player |
| `player/PlayerActivity.kt` | ExoPlayer playback |
| `res/layout/player_activity.xml` | PlayerView |
| `res/layout/activity_main.xml` | Add the chip view (modify) |
| `res/drawable/play_chip_bg.xml` | Chip pill background |
| `res/values/strings.xml` | Add `play_synced` (modify) |
| `AndroidManifest.xml` | Register PlayerActivity (modify) |
| `MainActivity.kt` | Wire sniffer + chip + handoff (modify) |
| `test/.../stream/*Test.kt` | Unit tests |

---

## Task 1: Add Media3 dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [ ] **Step 1: Add versions + libraries to the catalog**

Replace `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.1.1"
junit = "4.13.2"
media3 = "1.10.1"
json = "20240303"

[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
json = { group = "org.json", name = "json", version.ref = "json" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```

- [ ] **Step 2: Add dependencies to the app module**

In `app/build.gradle.kts`, replace the `dependencies { ... }` block:

```kotlin
dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
```

- [ ] **Step 3: Verify build (and handle compileSdk floor if it appears)**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.
If it fails with "compile against version 37" (media3's androidx floor), bump `compileSdk = 37` and `targetSdk = 34` stays; set catalog `agp = "9.2.0"` (supports API 37); install the SDK: `sdkmanager --sdk_root="$HOME/Library/Android/sdk" "platforms;android-37" "build-tools;37.0.0"`; if AGP 9.2 needs a newer Gradle, the wrapper error names the version — update `gradle/wrapper/gradle-wrapper.properties` accordingly. Then re-run.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add gradle/libs.versions.toml app/build.gradle.kts
git -C /Users/mohammad/Projects/mrowser commit -m "build: add media3 exoplayer dependencies"
```

---

## Task 2: MediaUrlClassifier (TDD)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/stream/MediaUrlClassifier.kt`
- Test: `app/src/test/kotlin/net/mrowser/stream/MediaUrlClassifierTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/stream/MediaUrlClassifierTest.kt`:

```kotlin
package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlClassifierTest {

    @Test fun `classifies hls manifest ignoring query`() {
        assertEquals(MediaKind.MANIFEST_HLS, MediaUrlClassifier.classify("https://x.net/a/index.m3u8?h=1080"))
    }

    @Test fun `classifies dash subtitle and segment`() {
        assertEquals(MediaKind.MANIFEST_DASH, MediaUrlClassifier.classify("https://x.net/a.mpd"))
        assertEquals(MediaKind.SUBTITLE, MediaUrlClassifier.classify("https://x.net/fa.vtt"))
        assertEquals(MediaKind.SUBTITLE, MediaUrlClassifier.classify("https://x.net/fa.SRT"))
        assertEquals(MediaKind.SEGMENT, MediaUrlClassifier.classify("https://x.net/seg1.ts"))
        assertEquals(MediaKind.SEGMENT, MediaUrlClassifier.classify("https://x.net/seg1.m4s"))
    }

    @Test fun `classifies everything else as other`() {
        assertEquals(MediaKind.OTHER, MediaUrlClassifier.classify("https://x.net/page.html"))
    }

    @Test fun `flags ad hosts including subdomains`() {
        assertTrue(MediaUrlClassifier.isAdHost("https://pubads.g.doubleclick.net/x.m3u8"))
        assertTrue(MediaUrlClassifier.isAdHost("https://googlesyndication.com/a"))
        assertFalse(MediaUrlClassifier.isAdHost("https://cdn.andrei-tarkovsky.net/a.m3u8"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — `unresolved reference: MediaUrlClassifier`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/kotlin/net/mrowser/stream/MediaUrlClassifier.kt`:

```kotlin
package net.mrowser.stream

/** Pure classification of a network URL by media role. */
object MediaUrlClassifier {

    enum class MediaKind { MANIFEST_HLS, MANIFEST_DASH, SUBTITLE, SEGMENT, OTHER }

    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "googleadservices.com", "adservice.google.com",
        "imasdk.googleapis.com", "amazon-adsystem.com", "adnxs.com", "scorecardresearch.com"
    )

    fun classify(url: String): MediaKind {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> MediaKind.MANIFEST_HLS
            path.endsWith(".mpd") -> MediaKind.MANIFEST_DASH
            path.endsWith(".vtt") || path.endsWith(".srt") -> MediaKind.SUBTITLE
            path.endsWith(".ts") || path.endsWith(".m4s") -> MediaKind.SEGMENT
            else -> MediaKind.OTHER
        }
    }

    fun isAdHost(url: String, denylist: Set<String> = AD_HOSTS): Boolean {
        val host = hostOf(url) ?: return false
        return denylist.any { host == it || host.endsWith(".$it") }
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        return authority.substringAfter('@', authority).substringBefore(':').lowercase().ifEmpty { null }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/stream/MediaUrlClassifier.kt app/src/test/kotlin/net/mrowser/stream/MediaUrlClassifierTest.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add media url classifier with tests"
```

---

## Task 3: StreamCandidate + StreamCandidateSelector (TDD)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/stream/StreamCandidate.kt`, `app/src/main/kotlin/net/mrowser/stream/StreamCandidateSelector.kt`
- Test: `app/src/test/kotlin/net/mrowser/stream/StreamCandidateSelectorTest.kt`

- [ ] **Step 1: Write the candidate data class**

Create `app/src/main/kotlin/net/mrowser/stream/StreamCandidate.kt`:

```kotlin
package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/** A media URL seen on the current page. `seq` orders by sighting. */
data class StreamCandidate(val url: String, val kind: MediaKind, val seq: Int)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/stream/StreamCandidateSelectorTest.kt`:

```kotlin
package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamCandidateSelectorTest {

    private fun hls(url: String, seq: Int) = StreamCandidate(url, MediaKind.MANIFEST_HLS, seq)
    private fun sub(url: String, seq: Int) = StreamCandidate(url, MediaKind.SUBTITLE, seq)

    @Test fun `selects the newest non-ad hls manifest`() {
        val list = listOf(
            hls("https://cdn.site.net/old.m3u8", 1),
            hls("https://pubads.g.doubleclick.net/ad.m3u8", 2),
            hls("https://cdn.site.net/new.m3u8", 3)
        )
        assertEquals("https://cdn.site.net/new.m3u8", StreamCandidateSelector.selectBest(list)?.url)
    }

    @Test fun `returns null when there is no manifest`() {
        assertNull(StreamCandidateSelector.selectBest(listOf(sub("https://x/fa.vtt", 1))))
    }

    @Test fun `collects subtitles newest first and deduped`() {
        val list = listOf(
            sub("https://x/fa.vtt", 1),
            sub("https://x/fa.vtt", 2),
            sub("https://x/en.vtt", 3)
        )
        val subs = StreamCandidateSelector.selectSubtitles(list)
        assertEquals(listOf("https://x/en.vtt", "https://x/fa.vtt"), subs.map { it.url })
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — `unresolved reference: StreamCandidateSelector`.

- [ ] **Step 4: Write the implementation**

Create `app/src/main/kotlin/net/mrowser/stream/StreamCandidateSelector.kt`:

```kotlin
package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/** Pure selection over collected stream candidates. */
object StreamCandidateSelector {

    fun selectBest(candidates: List<StreamCandidate>): StreamCandidate? =
        candidates
            .filter { it.kind == MediaKind.MANIFEST_HLS && !MediaUrlClassifier.isAdHost(it.url) }
            .maxByOrNull { it.seq }

    fun selectSubtitles(candidates: List<StreamCandidate>): List<StreamCandidate> =
        candidates
            .filter { it.kind == MediaKind.SUBTITLE }
            .sortedByDescending { it.seq }
            .distinctBy { it.url }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/stream/StreamCandidate.kt app/src/main/kotlin/net/mrowser/stream/StreamCandidateSelector.kt app/src/test/kotlin/net/mrowser/stream/StreamCandidateSelectorTest.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add stream candidate selector with tests"
```

---

## Task 4: PlaybackRequest (TDD, JSON)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/stream/PlaybackRequest.kt`
- Test: `app/src/test/kotlin/net/mrowser/stream/PlaybackRequestTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/stream/PlaybackRequestTest.kt`:

```kotlin
package net.mrowser.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRequestTest {

    @Test fun `round trips through json`() {
        val req = PlaybackRequest(
            url = "https://cdn.site.net/v.m3u8",
            headers = mapOf("User-Agent" to "UA", "Referer" to "https://site.net/movie", "Cookie" to "a=1"),
            subtitles = listOf(SubtitleTrack("https://site.net/fa.vtt", "text/vtt", "fa", "Persian")),
            title = "https://site.net/movie"
        )
        assertEquals(req, PlaybackRequest.fromJson(req.toJson()))
    }

    @Test fun `round trips with no subtitles`() {
        val req = PlaybackRequest("https://x/v.m3u8", mapOf("User-Agent" to "UA"), emptyList(), "t")
        assertEquals(req, PlaybackRequest.fromJson(req.toJson()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — `unresolved reference: PlaybackRequest`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/kotlin/net/mrowser/stream/PlaybackRequest.kt`:

```kotlin
package net.mrowser.stream

import org.json.JSONArray
import org.json.JSONObject

data class SubtitleTrack(
    val url: String,
    val mimeType: String,
    val language: String,
    val label: String
)

data class PlaybackRequest(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleTrack>,
    val title: String
) {
    fun toJson(): String {
        val h = JSONObject()
        headers.forEach { (k, v) -> h.put(k, v) }
        val subs = JSONArray()
        subtitles.forEach {
            subs.put(
                JSONObject()
                    .put("url", it.url).put("mimeType", it.mimeType)
                    .put("language", it.language).put("label", it.label)
            )
        }
        return JSONObject()
            .put("url", url).put("headers", h).put("subtitles", subs).put("title", title)
            .toString()
    }

    companion object {
        fun fromJson(json: String): PlaybackRequest {
            val o = JSONObject(json)
            val h = o.getJSONObject("headers")
            val headers = h.keys().asSequence().associateWith { h.getString(it) }
            val arr = o.getJSONArray("subtitles")
            val subs = (0 until arr.length()).map {
                val s = arr.getJSONObject(it)
                SubtitleTrack(s.getString("url"), s.getString("mimeType"), s.getString("language"), s.getString("label"))
            }
            return PlaybackRequest(o.getString("url"), headers, subs, o.getString("title"))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL` (the real `org.json` from the test dependency runs the serialization).

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/stream/PlaybackRequest.kt app/src/test/kotlin/net/mrowser/stream/PlaybackRequestTest.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add playback request serialization with tests"
```

---

## Task 5: StreamSniffer + SniffingWebViewClient

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/stream/StreamSniffer.kt`, `app/src/main/kotlin/net/mrowser/stream/SniffingWebViewClient.kt`

- [ ] **Step 1: Write the sniffer**

Create `app/src/main/kotlin/net/mrowser/stream/StreamSniffer.kt`:

```kotlin
package net.mrowser.stream

import android.webkit.CookieManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Collects media candidates for the current page (safe to call off the UI thread)
 * and assembles a PlaybackRequest. Pure selection lives in MediaUrlClassifier /
 * StreamCandidateSelector.
 */
class StreamSniffer(
    private val userAgent: () -> String,
    private val onStreamAvailable: () -> Unit,
    private val onCleared: () -> Unit
) {
    private val candidates = CopyOnWriteArrayList<StreamCandidate>()
    private val seq = AtomicInteger(0)

    @Volatile private var pageUrl: String = ""
    @Volatile private var announced = false

    fun onPageStarted(url: String) {
        pageUrl = url
        candidates.clear()
        announced = false
        onCleared()
    }

    fun onRequest(url: String) {
        val kind = MediaUrlClassifier.classify(url)
        if (kind == MediaUrlClassifier.MediaKind.MANIFEST_HLS || kind == MediaUrlClassifier.MediaKind.SUBTITLE) {
            candidates.add(StreamCandidate(url, kind, seq.incrementAndGet()))
            if (!announced && hasStream()) {
                announced = true
                onStreamAvailable()
            }
        }
    }

    fun hasStream(): Boolean =
        candidates.any { it.kind == MediaUrlClassifier.MediaKind.MANIFEST_HLS }

    fun bestRequest(): PlaybackRequest? {
        val best = StreamCandidateSelector.selectBest(candidates) ?: return null
        val headers = buildMap {
            put("User-Agent", userAgent())
            if (pageUrl.isNotEmpty()) put("Referer", pageUrl)
            CookieManager.getInstance().getCookie(best.url)?.let { put("Cookie", it) }
        }
        val subs = StreamCandidateSelector.selectSubtitles(candidates).map {
            val isSrt = it.url.substringBefore('?').lowercase().endsWith(".srt")
            SubtitleTrack(it.url, if (isSrt) "application/x-subrip" else "text/vtt", "fa", "Persian")
        }
        return PlaybackRequest(best.url, headers, subs, pageUrl)
    }
}
```

- [ ] **Step 2: Write the WebViewClient**

Create `app/src/main/kotlin/net/mrowser/stream/SniffingWebViewClient.kt`:

```kotlin
package net.mrowser.stream

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** Feeds every request URL to the StreamSniffer; never alters loading. */
class SniffingWebViewClient(private val sniffer: StreamSniffer) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) sniffer.onPageStarted(url)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        request?.url?.toString()?.let { sniffer.onRequest(it) }
        return null
    }
}
```

- [ ] **Step 3: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/stream/StreamSniffer.kt app/src/main/kotlin/net/mrowser/stream/SniffingWebViewClient.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add stream sniffer over webview requests"
```

---

## Task 6: Play-synced chip + cursor activation

**Files:**
- Create: `app/src/main/res/drawable/play_chip_bg.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/layout/activity_main.xml`, `app/src/main/kotlin/net/mrowser/web/CursorLayout.kt`

- [ ] **Step 1: Add the chip string**

Replace `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">mrowser</string>
    <string name="url_hint">Enter address</string>
    <string name="go">Go</string>
    <string name="back">Back</string>
    <string name="reload">Reload</string>
    <string name="play_synced">▶ Play synced</string>
</resources>
```

- [ ] **Step 2: Add the chip background**

Create `app/src/main/res/drawable/play_chip_bg.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#E6000000" />
    <stroke android:width="2dp" android:color="@color/accent" />
    <corners android:radius="22dp" />
</shape>
```

- [ ] **Step 3: Add the chip to the layout**

In `app/src/main/res/layout/activity_main.xml`, add this `TextView` as the last child inside `<net.mrowser.web.CursorLayout>` (immediately before the closing `</net.mrowser.web.CursorLayout>` tag):

```xml
    <TextView
        android:id="@+id/playChip"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="36dp"
        android:background="@drawable/play_chip_bg"
        android:paddingHorizontal="20dp"
        android:paddingVertical="12dp"
        android:text="@string/play_synced"
        android:textColor="@color/on_surface"
        android:textSize="18sp"
        android:visibility="gone" />
```

- [ ] **Step 4: Add chip fields + hit-test to CursorLayout**

In `app/src/main/kotlin/net/mrowser/web/CursorLayout.kt`, add these two properties immediately after the existing `var onBack: () -> Boolean = { false }` line:

```kotlin
    var playChip: android.view.View? = null
    var onChipClick: () -> Unit = {}
```

Then replace the `KeyEvent.ACTION_UP` branch inside `handleOk` with:

```kotlin
            KeyEvent.ACTION_UP -> {
                okDown = false
                longPressHandler.removeCallbacks(longPress)
                if (!longPressed) {
                    if (chipContains(cursor.x, cursor.y)) onChipClick() else cursor.tap()
                }
            }
```

And add this private helper immediately after the `handleOk` function:

```kotlin
    private fun chipContains(x: Float, y: Float): Boolean {
        val chip = playChip ?: return false
        if (chip.visibility != android.view.View.VISIBLE) return false
        return x >= chip.left && x <= chip.right && y >= chip.top && y <= chip.bottom
    }
```

- [ ] **Step 5: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/res/drawable/play_chip_bg.xml app/src/main/res/values/strings.xml app/src/main/res/layout/activity_main.xml app/src/main/kotlin/net/mrowser/web/CursorLayout.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add play-synced chip with cursor activation"
```

---

## Task 7: HandoffController + PlayerActivity

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/handoff/HandoffController.kt`, `app/src/main/kotlin/net/mrowser/player/PlayerActivity.kt`, `app/src/main/res/layout/player_activity.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the player layout**

Create `app/src/main/res/layout/player_activity.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.media3.ui.PlayerView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/playerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black"
    app:surface_type="surface_view"
    app:show_subtitle_button="true"
    app:resize_mode="fit" />
```

- [ ] **Step 2: Add the handoff controller**

Create `app/src/main/kotlin/net/mrowser/handoff/HandoffController.kt`:

```kotlin
package net.mrowser.handoff

import android.content.Context
import android.content.Intent
import android.widget.Toast
import net.mrowser.player.PlayerActivity
import net.mrowser.stream.StreamSniffer

/** Turns a chip activation into a launched PlayerActivity. */
class HandoffController(
    private val context: Context,
    private val sniffer: StreamSniffer
) {
    fun play() {
        val request = sniffer.bestRequest()
        if (request == null) {
            Toast.makeText(context, "No stream detected yet", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_REQUEST, request.toJson())
        )
    }
}
```

- [ ] **Step 3: Add the player activity**

Create `app/src/main/kotlin/net/mrowser/player/PlayerActivity.kt`:

```kotlin
package net.mrowser.player

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import net.mrowser.R
import net.mrowser.stream.PlaybackRequest
import net.mrowser.stream.SubtitleTrack

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)
        val playerView = findViewById<PlayerView>(R.id.playerView)

        val json = intent.getStringExtra(EXTRA_REQUEST) ?: run { finish(); return }
        val request = PlaybackRequest.fromJson(json)

        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setTunnelingEnabled(true)        // ExoPlayer falls back if unsupported
                .setPreferredTextLanguage(null)   // subtitles off by default
                .build()
        }
        val exo = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        player = exo
        playerView.player = exo

        exo.setMediaSource(buildSource(request))
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Stream error — back to web player", Toast.LENGTH_LONG).show()
                finish()
            }
        })
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun buildSource(request: PlaybackRequest): MediaSource {
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(request.headers)
            .setAllowCrossProtocolRedirects(true)
        val item = MediaItem.Builder()
            .setUri(request.url)
            .setSubtitleConfigurations(request.subtitles.map { it.toConfig() })
            .build()
        return HlsMediaSource.Factory(dataSource).createMediaSource(item)
    }

    private fun SubtitleTrack.toConfig(): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
            .setMimeType(if (mimeType == "application/x-subrip") MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
            .setLanguage(language)
            .setLabel(label)
            .build()

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_REQUEST = "mrowser.request"
    }
}
```

- [ ] **Step 4: Register PlayerActivity in the manifest**

In `app/src/main/AndroidManifest.xml`, add this `<activity>` immediately before the closing `</application>` tag:

```xml
        <activity
            android:name=".player.PlayerActivity"
            android:exported="false"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:screenOrientation="landscape"
            android:theme="@style/Theme.Mrowser" />
```

- [ ] **Step 5: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/handoff/HandoffController.kt app/src/main/kotlin/net/mrowser/player/PlayerActivity.kt app/src/main/res/layout/player_activity.xml app/src/main/AndroidManifest.xml
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add exoplayer handoff player activity"
```

---

## Task 8: Wire sniffing + handoff into the browser

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt`

- [ ] **Step 1: Replace MainActivity**

Replace `app/src/main/kotlin/net/mrowser/MainActivity.kt`:

```kotlin
package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import net.mrowser.handoff.HandoffController
import net.mrowser.stream.SniffingWebViewClient
import net.mrowser.stream.StreamSniffer
import net.mrowser.web.BrowserWebChromeClient
import net.mrowser.web.ChromeController
import net.mrowser.web.CursorController
import net.mrowser.web.CursorLayout
import net.mrowser.web.UrlNormalizer

class MainActivity : Activity() {

    private lateinit var layout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var chrome: ChromeController
    private lateinit var chromeClient: BrowserWebChromeClient
    private lateinit var sniffer: StreamSniffer
    private lateinit var playChip: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.cursorLayout)
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        playChip = findViewById(R.id.playChip)
        val bar = findViewById<View>(R.id.chromeBar)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reloadButton = findViewById<ImageButton>(R.id.reloadButton)

        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = { runOnUiThread { playChip.visibility = View.VISIBLE } },
            onCleared = { playChip.visibility = View.GONE }
        )
        val handoff = HandoffController(this, sniffer)

        webView.webViewClient = SniffingWebViewClient(sniffer)
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() }
        )
        webView.webChromeClient = chromeClient
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        chrome = ChromeController(bar, urlInput, webView)
        val cursor = CursorController(webView) { layout.invalidate() }
        layout.webView = webView
        layout.cursor = cursor
        layout.chrome = chrome
        layout.playChip = playChip
        layout.onChipClick = { handoff.play() }
        layout.onBack = { chromeClient.exitIfFullscreen() }

        layout.post {
            cursor.center(webView.width, webView.height)
            chrome.requestReveal(atTop = true)
        }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
            chrome.onInteracted()
        }
        reloadButton.setOnClickListener {
            webView.reload()
            chrome.onInteracted()
        }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromInput()
                true
            } else {
                false
            }
        }

        webView.loadData(WELCOME_HTML, "text/html", "utf-8")
        layout.requestFocus()
    }

    private fun loadFromInput() {
        val url = UrlNormalizer.normalize(urlInput.text.toString()) ?: return
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }

    companion object {
        private const val WELCOME_HTML =
            "<html><body style='background:#141414;color:#eee;font-family:sans-serif;" +
            "display:flex;height:100vh;margin:0;align-items:center;justify-content:center'>" +
            "<h1 style='color:#E50914'>mrowser</h1></body></html>"
    }
}
```

- [ ] **Step 2: Run unit tests + build the signed APK**

Run: `/Users/mohammad/Projects/mrowser/gradlew test assembleRelease`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 3: Manual test on the box**

Install the signed APK on the Sunyia box. Verify:
- Load the Sunyia site and start a movie. The red **"▶ Play synced"** chip appears (bottom center) once the stream loads.
- Move the cursor onto the chip and press OK → fullscreen ExoPlayer opens and plays the movie.
- **Audio and video are in sync** (the core fix).
- The PlayerView subtitle button enables Persian subtitles.
- Seeking/pause work with the D-pad.
- BACK exits the player and returns to the page (pick the next episode).
- If a stream can't play, a toast shows and it returns to the page.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/MainActivity.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: wire stream sniffing and handoff into browser"
```

---

## Self-review (completed by plan author)

- **Spec coverage:** §4.1 MediaUrlClassifier → Task 2. §4.2 StreamCandidate/Selector → Task 3. §4.3 PlaybackRequest → Task 4. §4.4 StreamSniffer + §4.5 SniffingWebViewClient → Task 5. §4.6 chip + CursorLayout → Task 6. §4.7 HandoffController + §4.8 PlayerActivity → Task 7. §4.9 MainActivity wiring → Task 8. §3 deps → Task 1. §7 testing → Tasks 2–4 (unit) + Task 8 Step 3 (manual). No gaps.
- **Placeholder scan:** none. The compileSdk-floor contingency in Task 1 Step 3 is explicit and actionable, not a deferral.
- **Type consistency:** `MediaUrlClassifier.classify/isAdHost`, `MediaKind` consistent across Tasks 2, 3, 5. `StreamCandidate(url, kind, seq)` consistent (Tasks 3, 5). `StreamCandidateSelector.selectBest/selectSubtitles` (Tasks 3, 5). `PlaybackRequest(url, headers, subtitles, title)`, `SubtitleTrack(url, mimeType, language, label)`, `toJson/fromJson` consistent (Tasks 4, 5, 7). `StreamSniffer(userAgent, onStreamAvailable, onCleared)` + `onPageStarted/onRequest/hasStream/bestRequest` consistent (Tasks 5, 8). `HandoffController(context, sniffer).play()` + `PlayerActivity.EXTRA_REQUEST` consistent (Tasks 7, 8). `CursorLayout.playChip/onChipClick` consistent (Tasks 6, 8). View IDs `playChip`, `playerView` match layouts and code.
```
