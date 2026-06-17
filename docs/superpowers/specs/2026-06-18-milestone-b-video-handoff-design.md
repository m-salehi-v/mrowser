# Milestone B — Native Video Handoff — Design

- **Date:** 2026-06-18
- **Status:** Approved design (pre-implementation)
- **Builds on:** Milestone A (cursor, chrome, fullscreen — branch `milestone-b-video-handoff` is off A).
- **Architectural basis:** original design `2026-06-17-android-tv-streaming-browser-design.md` §6.4–6.6.

## 1. Overview

Fix the audio/video sync by playing the site's HLS stream in a native Media3/ExoPlayer instead of the WebView. While the user browses, mrowser sniffs the `.m3u8` (plus subtitle files and the cookies/headers needed to fetch it). When a stream is available, a floating **"▶ Play synced"** chip appears; clicking it opens a fullscreen ExoPlayer that plays the stream with tight sync (tunneling where supported) and Persian subtitles. BACK returns to the page.

This is the milestone that addresses the original problem the project was started for.

## 2. Goals & non-goals

**Goals**
- Play the current page's HLS stream in ExoPlayer with correct A/V sync.
- Manual, reliable handoff via a discoverable floating chip.
- Carry the site's cookies / Referer / User-Agent so the CDN serves the stream.
- Preserve separate Persian subtitles (sniffed `.vtt`/`.srt`, toggleable).
- Stay GMS-free; keep the APK as small as ExoPlayer allows.

**Non-goals (deferred / out of scope)**
- Auto-handoff (manual chip only this milestone; auto added later once detection is proven on the real site).
- DRM/Widevine streams and MSE-blob-only sites — these stay in the WebView (fallback).
- JS `fetch`/XHR sniffing hooks — `shouldInterceptRequest` already sees those requests; add only if Sunyia hides the manifest.
- DASH/SmoothStreaming — HLS only.
- Home screen / favorites (Milestone C).

## 3. Key decisions (confirmed)

- **Trigger:** manual floating chip, not auto-detect.
- **Engine:** Media3 ExoPlayer `1.10.1` (`media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui`). HLS only.
- **Sniffing:** `WebViewClient.shouldInterceptRequest` only (catches the player's HLS/subtitle GETs).
- **Player UI:** standard `PlayerView` (D-pad controls out of the box).
- **Subtitles:** sniffed and side-loaded, **off by default**, user-toggleable.

## 4. Components

Pure logic under `net.mrowser.stream`; Android-coupled pieces alongside.

### 4.1 `MediaUrlClassifier` (pure)
- `classify(url): MediaKind` — `MANIFEST_HLS` (`.m3u8`), `MANIFEST_DASH` (`.mpd`), `SUBTITLE` (`.vtt`/`.srt`), `SEGMENT` (`.ts`/`.m4s`), `OTHER`. Case-insensitive, query string ignored.
- `isAdHost(url, denylist): Boolean` — host matches a small built-in ad/analytics denylist.

### 4.2 `StreamCandidate` (data) + `StreamCandidateSelector` (pure)
- `StreamCandidate { url, kind, headers, seq }` — `seq` orders by sighting.
- `selectBest(candidates): StreamCandidate?` — newest (highest `seq`) `MANIFEST_HLS` not on an ad host, else null.
- `selectSubtitles(candidates): List<StreamCandidate>` — all `SUBTITLE` candidates, newest first, deduped by url.

### 4.3 `PlaybackRequest` (data, JSON-serializable)
- `{ url, headers: Map<String,String>, subtitles: List<SubtitleTrack>, title }`; `SubtitleTrack { url, mimeType, language, label }`.
- `toJson()` / `fromJson()` via built-in `org.json` — passed to `PlayerActivity` as a single string extra. Pure, unit-tested.

### 4.4 `StreamSniffer` (Android)
- Per-page list of `StreamCandidate`s (thread-safe; `shouldInterceptRequest` runs off the UI thread).
- `onPageStarted(pageUrl)` — clears candidates, stores `pageUrl` (Referer source), notifies UI to hide the chip.
- `onRequest(url, requestHeaders)` — classify; for manifest/subtitle, append a candidate (capturing `seq` + any useful request headers); if it is the first manifest, notify UI (`onStreamAvailable`) to show the chip.
- `hasStream()` — any HLS manifest candidate.
- `bestRequest(userAgent, cookieFor): PlaybackRequest?` — `selectBest` + `selectSubtitles`, assembling headers: `Referer` = page URL, `User-Agent` = WebView UA, `Cookie` = `CookieManager.getCookie(manifestUrl)`. Null if no manifest.

Header note: `WebResourceRequest.requestHeaders` does not expose cookies; cookies come from `CookieManager`, UA from `webView.settings.userAgentString`, Referer from the page URL.

### 4.5 `SniffingWebViewClient`
Subclass of `WebViewClient`; forwards `shouldInterceptRequest` → `sniffer.onRequest` (returns null to let the WebView load normally) and `onPageStarted` → `sniffer.onPageStarted`.

### 4.6 `PlayChip` (overlay) + `CursorLayout` integration
- A focusable, clickable overlay (styled "▶ Play synced", red accent) added to `CursorLayout`, shown when `sniffer.hasStream()`.
- Cursor click: `CursorLayout` checks whether an OK tap lands within the chip's bounds; if so it fires the chip action instead of dispatching to the WebView (no change to WebView tap routing). Focus-mode D-pad can also focus + activate it.

### 4.7 `HandoffController`
- On chip activation: `sniffer.bestRequest(...)` → if non-null, start `PlayerActivity` with the JSON extra; if null, toast "No stream detected yet".

### 4.8 `PlayerActivity` (Media3/ExoPlayer)
- Layout: `PlayerView` wrapping a `SurfaceView` (required for tunneling).
- Reads the `PlaybackRequest` JSON extra. Builds `ExoPlayer` with an `HlsMediaSource` over a `DefaultHttpDataSource.Factory` carrying the forwarded headers (Referer/UA/Cookie).
- Subtitles: each sniffed track added as a `MediaItem.SubtitleConfiguration` (mime `text/vtt` or `application/x-subrip`, language `fa`); text track **disabled by default** via `TrackSelectionParameters`; user toggles via the PlayerView track button.
- Tunneling: feature-detect (`DefaultTrackSelector` `setTunnelingEnabled(true)` guarded by codec `FEATURE_TunneledPlayback`); fall back to non-tunneled.
- Errors (`Player.Listener.onPlayerError` — 403/expired/DRM/unsupported): toast + `finish()` (returns to the WebView player as fallback).
- BACK / finish releases the player and returns to the WebView. Manifest declares `PlayerActivity` (landscape, `configChanges`).

### 4.9 `MainActivity` wiring
Creates `StreamSniffer`, sets `SniffingWebViewClient`, adds the `PlayChip` to the layout, wires `HandoffController` to chip activation, and shows/hides the chip on `onStreamAvailable` / `onPageStarted` (posted to the main thread).

## 5. Data flow

browse → `SniffingWebViewClient.shouldInterceptRequest` → `StreamSniffer` collects manifest/subtitle candidates (+ page URL) → first manifest ⇒ chip shown → user clicks chip → `HandoffController` → `sniffer.bestRequest` (selector + headers + cookies) → `PlayerActivity` plays in ExoPlayer (tunneled, subs available) → BACK → WebView.

## 6. Error handling

- No manifest yet when chip clicked → toast, stay on page.
- ExoPlayer fatal error (403 / expired token / DRM / unsupported) → toast + return to WebView playback.
- Tunneling unsupported → non-tunneled path automatically.
- Subtitles missing/unparseable → none shown; site subtitles remain available in the WebView fallback.
- Cookie/Referer rejected by CDN → fails into the WebView fallback (manual retry on the page).

## 7. Testing

- **Unit (JVM, TDD):** `MediaUrlClassifier` (each kind + ad host), `StreamCandidateSelector` (newest-non-ad pick, no-manifest null, subtitle collection), `PlaybackRequest` JSON round-trip.
- **Manual on-box (the real sync test):** load Sunyia, play a movie, click the chip → does ExoPlayer play with tight sync? Do Persian subtitles toggle on? Is tunneling active where supported? Does BACK return for the next episode? Does a bad stream fall back cleanly?

## 8. Dependencies & size

Adds `androidx.media3:media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui` (target `1.10.1`). APK grows from ~650 KB to roughly ~3 MB — unavoidable for native playback, still small, no GMS. If `1.10.1` requires `compileSdk 37`, bump `compileSdk` (and AGP to 9.2.x which supports API 37) or pin a media3 version compatible with 36 — decided at build time.

## 9. File structure

```
app/src/main/kotlin/net/mrowser/
  stream/MediaUrlClassifier.kt        (pure)
  stream/StreamCandidate.kt           (data)
  stream/StreamCandidateSelector.kt   (pure)
  stream/PlaybackRequest.kt           (data + JSON)
  stream/StreamSniffer.kt             (collect + assemble)
  stream/SniffingWebViewClient.kt     (WebViewClient hook)
  handoff/HandoffController.kt        (chip -> player)
  player/PlayerActivity.kt            (ExoPlayer)
  web/PlayChip.kt                     (overlay view)         (+ CursorLayout hit-test hook)
  MainActivity.kt                     (wire sniffer + chip)
app/src/main/res/layout/player_activity.xml
app/src/main/res/drawable/play_chip_bg.xml
app/src/test/kotlin/net/mrowser/stream/
  MediaUrlClassifierTest.kt  StreamCandidateSelectorTest.kt  PlaybackRequestTest.kt
```

## 10. Risks

- **Site needs auth/cookies the sniffer can't see** — we forward `CookieManager` cookies + Referer + UA; if the CDN still rejects, the WebView fallback keeps playback working.
- **Expiring segment tokens** — ExoPlayer refetches via the same headers; if tokens are one-time, fall back to WebView.
- **Multiple manifests (ads, qualities)** — `selectBest` takes the newest non-ad manifest after the user has chosen quality on the page; manual chip means the user triggers it at the right moment.
- **media3 compileSdk floor** — handled at build time (see §8).
- **Untestable site** — I cannot reach Sunyia; manual trigger + fallbacks make it robust, and the on-box test drives adjustments.

## 11. Out of scope (→ later)

Auto-handoff, DRM, MSE-blob streams, JS sniff hooks, DASH, home/favorites (Milestone C).
