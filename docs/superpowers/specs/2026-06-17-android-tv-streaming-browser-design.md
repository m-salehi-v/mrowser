# mrowser — Lightweight Android TV Streaming Browser

- **Date:** 2026-06-17
- **Status:** Approved design (pre-implementation)
- **Target device:** Cheap Android TV box (e.g. "Sunyia" type, Iran market) — weak SoC, unknown Android version, no Google Play Services.

## 1. Overview

A tiny Android TV browser optimized for watching streaming video on a remote. It browses arbitrary sites in a System WebView, and when a video plays it pulls the underlying HLS (`.m3u8`) stream out of the WebView and plays it in a native Media3/ExoPlayer player. The native player gives tight, controllable audio/video sync that the WebView's built-in HTML5 player cannot.

It replaces TV Bro for the user's primary use case: a movie site (`andrei-tarkovsky.net`-style) whose web player streams HLS and toggles separate Persian subtitles.

## 2. Background / problem

The user watches movies in TV Bro (System WebView browser) and suffers audio/video desync. Research findings (June 2026):

- The desync lives in the **MediaCodec decode + HDMI/soundbar audio path**, plus a few Blink-specific bugs — layers the WebView gives **no tuning knobs** for.
- **Switching browser engine does not fix it.** GeckoView decodes through the same `MediaCodec` path, adds ~50 MB, and has its own sync bugs. So we stay on the lightweight System WebView.
- The reliable fix is to **hand the stream to native ExoPlayer**, which controls the A/V master clock (audio `AudioTrack` timestamp, vsync-aligned frame release) and supports **TV tunneling** (`FEATURE_TunneledPlayback`) for hardware-locked lip-sync.
- Native handoff is only feasible for **non-DRM streams we can observe** (HLS/DASH/MP4 over HTTP). The user's site serves plain `.m3u8` → in scope. DRM/blob-only content stays in the WebView.

A constant offset may also originate entirely in the TV/soundbar audio path; the user will first test the TV's own Lip-Sync/Audio-delay offset and Digital-Audio-Out = PCM as a zero-code check.

## 3. Goals & non-goals

**Goals**
- As lightweight as possible (small APK, low runtime cost on weak hardware).
- Fix A/V sync for HLS streams via native playback handoff.
- Usable entirely from a D-pad remote, including a mouse-cursor mode for sites D-pad can't navigate.
- Preserve separate (Persian) subtitles through the handoff.
- No Google Play Services dependency; sideload distribution.
- Clean enough to open-source later (MIT, no hardcoded sites, conventional commits).

**Non-goals (YAGNI)**
- Tabs, history, multi-window, bookmark sync.
- DRM/Widevine handoff (stays in WebView).
- DASH/SmoothStreaming (HLS only for v1).
- Downloads / offline, ad-blocking, Compose/Leanback UI.

## 4. Stack decisions

| Area | Decision | Why |
|------|----------|-----|
| Language | Kotlin | Modern Android standard, concise. |
| UI toolkit | Classic Android Views (no Compose, no Leanback lib) | Lightest runtime on a weak box; UI surface is small. |
| Web engine | System WebView (AOSP) | ~0 MB, no GMS; user already runs it via TV Bro. |
| Native player | Media3/ExoPlayer `1.10.1` (`-hls`, `-ui`) | Standard Android TV player; A/V clock + tunneling control. |
| Streaming | HLS only | Site serves `.m3u8`; DASH deferred. |
| Persistence | JSON file via built-in `org.json` | Avoid Room and heavy deps. |
| SDK levels | `minSdk 21`, `targetSdk 34`, `compileSdk 36` | Max cheap-box reach; TV exempt from Play API-35 mandate. |
| Packaging | One universal release APK, no ffmpeg extension | Smallest artifact. |

## 5. Architecture

Single Gradle `:app` module, organized into small files by feature. Two activities.

```
MainActivity ─┬─ home/     favorites grid + URL entry (RecyclerView, D-pad)
              ├─ web/      CursorLayout(WebView) + cursor + JS hooks + sniff intercept
              ├─ sniff/    StreamSniffer: collect .m3u8/.vtt + headers, pick real manifest
              ├─ handoff/  HandoffController: auto/manual trigger → PlaybackRequest
              └─ data/     FavoritesRepository (JSON), SettingsStore

PlayerActivity ─ player/   ExoPlayer on SurfaceView: tunneling, subtitles, controls
```

## 6. Components

### 6.1 App shell / manifest
- Launcher activity declares `android.intent.category.LEANBACK_LAUNCHER`; 320×180 xhdpi banner.
- `uses-feature android.software.leanback required=false`, `android.hardware.touchscreen required=false`; `INTERNET` permission.
- Landscape, `hardwareAccelerated`. `MainActivity` (home + web) and `PlayerActivity` (native player).

### 6.2 Home (in MainActivity)
- Favorites grid: `RecyclerView` of focusable cards, visible `state_focused` drawable, initial focus set.
- "Enter URL" action → dialog with `EditText` + TV IME on-screen keyboard.
- Add / edit / delete favorite. Minimal settings: auto-handoff on/off, cursor speed, clear data.
- **No site is hardcoded** — the user adds Sunyia and any others as favorites.

### 6.3 WebView host — `CursorLayout`
- `CursorLayout extends FrameLayout` wrapping the `WebView`. Draws cursor in `dispatchDraw()`; intercepts D-pad in `dispatchKeyEvent()`; moves cursor with velocity; `DPAD_CENTER` synthesizes `MotionEvent` `ACTION_DOWN`/`ACTION_UP` (tap) and `ACTION_HOVER_MOVE` (CSS `:hover`); edge auto-scroll. Toggle between cursor mode and native-focus mode.
- WebView config: JavaScript, DOM storage, autoplay without gesture, `CookieManager`, a modern desktop-ish User-Agent so the site serves its normal player.
- Injects `web_hooks.js` on each page (`onPageStarted`/`onPageFinished`): wraps `fetch` and `XMLHttpRequest`, reads `<video>.currentSrc`, and reports candidate media/subtitle URLs to a `@JavascriptInterface` bridge. Re-injected per navigation/iframe.
- `WebViewClient.shouldInterceptRequest` (runs off the UI thread) feeds observed request URLs to the sniffer.

### 6.4 StreamSniffer
- Collects candidates from both the intercept path and the JS bridge: `.m3u8` (master/variant), `.vtt`/`.srt`, segment fetches (`.ts`/`.m4s`, to confirm an actively-playing stream), and in-manifest `EXT-X-MEDIA TYPE=SUBTITLES` renditions.
- `ManifestClassifier` picks the real content manifest: prefer a master playlist, drop known ad/analytics hosts, favor manifests whose segments are actively fetched and that follow a user gesture. Deduplicates.
- Captures the headers needed downstream: `Referer`, `User-Agent`, and cookies (via `CookieManager`).
- Produces immutable `StreamCandidate` snapshots; never mutates prior state.

### 6.5 HandoffController
- **Auto (A):** when a content `.m3u8` plus active segment fetches are detected after a user gesture, launch the player (debounced).
- **Manual (B):** a remote menu button plays the last/best candidate, for cases auto-detection misses or mis-picks.
- Builds an immutable `PlaybackRequest { url, headers, cookies, subtitles[], title }`, pauses WebView playback (to avoid double audio), and starts `PlayerActivity`.

### 6.6 PlayerActivity (Media3/ExoPlayer)
- `SurfaceView` (required for tunneling) + `HlsMediaSource` with a `DefaultHttpDataSource.Factory` carrying forwarded headers, cookies, and UA.
- Feature-detects `FEATURE_TunneledPlayback` (and presence of both audio+video tracks) → enables tunneling via track-selector params; otherwise uses the non-tunneled path.
- Subtitles: side-loads sniffed `.vtt`/`.srt` as `MediaItem.SubtitleConfiguration` and exposes in-manifest subtitle renditions; subtitle on/off + track picker. Persian/RTL/UTF-8 render via `SubtitleView`.
- `PlayerView` (media3-ui) controls (play/pause/seek/track select), D-pad friendly; aspect-ratio toggle.
- On fatal stream error (403 / expired token / DRM detected) → toast + **return to the WebView player** as fallback.
- Back returns to the WebView so the user can pick the next episode.

### 6.7 Data
- `FavoritesRepository` interface (`findAll`/`add`/`remove`/`update`); JSON-file implementation in app `filesDir`. Immutable `Favorite` data class. Starts empty.
- `SettingsStore` for the handful of toggles.

## 7. Data flow

home → open site in WebView → user plays video → JS hooks + `shouldInterceptRequest` capture `.m3u8` + subtitles + headers → HandoffController (auto or manual) → PlayerActivity plays in ExoPlayer (tunneled where supported) with subtitles → **synced playback** → Back → WebView (choose next episode) → repeat.

## 8. Error handling

- No stream sniffed → manual handoff button shows a hint; WebView playback continues to work.
- ExoPlayer fails (403 / expired / DRM) → toast and automatic return to WebView playback.
- Tunneling unsupported → automatic non-tunneled path.
- Subtitles not captured → user can toggle the site's own subtitles in the WebView fallback.
- WebView missing/old → app still runs; show a message if WebView is absent.
- All errors logged with context (Logcat); user-facing messages are short and friendly.

## 9. Testing strategy

- **Unit tests (JVM, TDD):** sniffer URL classification, manifest selection, ad-host filtering, subtitle detection, handoff decision logic, favorites repository, header/cookie assembly, URL validation. 80% coverage target applies to this logic layer.
- **Instrumentation tests (Android TV emulator):** basic navigation, cursor `MotionEvent` synthesis, WebView load smoke tests.
- **Manual on the real box (only valid test of sync):** Is sync tight? Do Persian subtitles show? Is tunneling active? Does the cursor work on the site? Does the back→next-episode flow work? The cheap-box sync bug cannot be reproduced on an emulator — this is stated openly rather than implied as covered.

## 10. Project structure

```
mrowser/
  settings.gradle.kts
  build.gradle.kts            (root)
  gradle/ + gradlew           (wrapper)
  .github/workflows/build.yml
  README.md  LICENSE (MIT)  .gitignore
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/kotlin/net/mrowser/
      MainActivity.kt
      home/      HomeView, FavoritesAdapter, UrlEntryDialog
      web/       CursorLayout, WebViewHost, CursorController, JsBridge, web_hooks.js
      sniff/     StreamSniffer, StreamCandidate, ManifestClassifier, HeaderCapture
      handoff/   HandoffController, PlaybackRequest
      player/    PlayerActivity, PlayerController, SubtitleLoader, TunnelingSupport
      data/      FavoritesRepository, Favorite, JsonFavoritesStore, SettingsStore
    src/main/res/   (layouts, drawable/banner, values)
    src/test/kotlin/        (unit tests)
    src/androidTest/kotlin/ (instrumentation)
```
Files kept small (≈200–400 lines, 800 max).

## 11. Build & distribution

- Gradle wrapper (Kotlin DSL), latest stable AGP + Kotlin, `compileSdk 36` / build-tools 36. Java 17 (present).
- Headless Android SDK installed locally (cmdline-tools + platform-36 + build-tools-36) to build APKs and run unit tests.
- Release APK signed with a generated keystore — gitignored locally, stored in GitHub Actions secrets. A stable key so updates install over the previous build.
- **GitHub Actions:** on PR/push → build + run unit tests; on tag → produce the signed APK and attach it to a GitHub Release. The user downloads and copies the APK to the box by USB/file manager (no ADB).

## 12. Open-source setup

- Name: **mrowser**. License: **MIT**.
- README covering what/why, build, install, and a legal disclaimer (general-purpose browser; the user is responsible for the content they access).
- Conventional one-line commit messages; **no AI attribution** in commits. No hardcoded sites; no bundled DRM.

## 13. Risks & mitigations

- **Expiring segment tokens** → forward cookies/headers from the WebView; fall back to the web player if the CDN still rejects.
- **Box decoder itself is the bottleneck** → ExoPlayer improves A/V control but may not be pixel-perfect; complement with the TV's Lip-Sync offset.
- **Old System WebView on the box** → the user already runs TV Bro on the same WebView successfully, so this is acceptable.
- **MSE blob-only or DRM site** → cannot sniff a usable manifest; the WebView fallback keeps playback working (out of scope for handoff).

## 14. Out of scope (deferred)

Tabs/history, DRM handoff, DASH, downloads, ad-blocking, Compose/Leanback. Ad-blocking via request interception and DASH support are natural future additions if needed.
