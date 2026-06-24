# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

mrowser is a sideload-only Android TV browser (`net.mrowser`) for streaming video. It browses any site with a D-pad-driven virtual mouse cursor, sniffs the page's network traffic for an HLS manifest, and hands that stream off to a native Media3/ExoPlayer activity — because the WebView's built-in player gives poor A/V sync. No Google Play Services; single Gradle module `:app`, all Kotlin.

## Commands

```bash
./gradlew test               # unit tests (JVM, no device)
./gradlew test --tests "net.mrowser.stream.MediaUrlClassifierTest"   # single test class
./gradlew assembleDebug      # debug APK   -> app/build/outputs/apk/debug/
./gradlew assembleRelease    # signed APK  -> app/build/outputs/apk/release/  (needs keystore.properties; unsigned if absent)
```

Toolchain: JDK 17, AGP 9.1.1 (built-in Kotlin), Gradle 9.3.1. `compileSdk 36` / `minSdk 23` / `targetSdk 34`. Needs SDK packages `platforms;android-36` and `build-tools;36.0.0`. Versions are pinned in `gradle/libs.versions.toml` (version catalog) — change deps there, not in `app/build.gradle.kts`.

## Architecture

Two activities (`AndroidManifest.xml`), wired in `MainActivity.onCreate`:

- **MainActivity** — the browser. Hosts a `WebView` inside a custom `CursorLayout`, plus the chrome bar and the home screen overlay.
- **PlayerActivity** — the native player. Handoff is **automatic** when a stream is detected — unless the **Auto-open synced player** setting is off, in which case only the play chip shows and the user activates it; launched with a serialized `PlaybackRequest` Intent extra. **BACK returns to the browser** (the WebView is paused during playback via `onPause`/`onResume`); the play-synced chip re-enters the player and auto-hides after 30s. Quality / Audio / Speed live in the player's **built-in settings gear** — its click is overridden in `showSettings` (media3 has no public hook, so it reuses `androidx.media3.ui.R.id.exo_settings`); subtitles use the CC button. Finishes (back to the browser) on any playback error. Holds `FLAG_KEEP_SCREEN_ON` **only while playing** (toggled in `onIsPlayingChanged`) — TV gets no input events mid-movie, so without it the system sleep timeout fires; a paused stream can still sleep.

Code is split by domain under `net.mrowser.*`, and within each domain **pure logic is separated from Android-dependent code so it can be unit-tested without a device**. The pure pieces are the ones with tests in `app/src/test/`. When adding logic, follow this split: put decision/parsing/geometry in a plain object/class and keep the Android glue thin.

### `stream/` — HLS detection and handoff
The core pipeline:
1. `SniffingWebViewClient` forwards every `WebView` resource request to `StreamSniffer`.
2. `StreamSniffer` (thread-safe; called off the UI thread) classifies each URL via the **pure** `MediaUrlClassifier` (extension-based: `.m3u8`→HLS, `.vtt`/`.srt`→subtitle, etc.) and accumulates `StreamCandidate`s.
3. On the first HLS manifest it fires `onStreamAvailable`, which shows the play chip and triggers handoff.
4. `bestRequest()` uses the **pure** `StreamCandidateSelector` to pick the manifest + subtitle tracks, attaches headers (User-Agent, Referer, Cookie from `CookieManager`), and returns a `PlaybackRequest`.
5. `HandoffController` serializes the `PlaybackRequest` to JSON and starts `PlayerActivity`.

The default subtitle track is driven by the **Preferred subtitle language** setting (default **English**; Persian or **Off** also selectable). The **pure** `SubtitlePlan` turns the chosen subtitle candidates + that pref into the `PlaybackRequest`'s tracks and its `preferredTextLanguage`: the first side-loaded track is tagged with the language + marked default, except **Off** marks no default and sets no preference. `PlayerActivity` reads `request.preferredTextLanguage` (no longer hard-coded `"fa"`) and uses `DefaultMediaSourceFactory` (not `HlsMediaSource.Factory`) specifically so side-loaded subtitles get merged.

### `web/` — the D-pad cursor and chrome
- `CursorLayout` (FrameLayout) is the input router: it intercepts key events in `dispatchKeyEvent` and dispatches D-pad/OK/BACK/MENU to the cursor or chrome, and paints the cursor dot. This is where the remote-control scheme lives — see the control tables in the Milestone A spec/plan under `docs/superpowers/`. It consumes BACK's `ACTION_DOWN` without `startTracking`, so the Activity's back-tracking never fires `onBackPressed`; the **root BACK** (no chrome, no WebView history) is routed out via the `onExitPage` callback instead → `confirmCloseTab` (a "Close this page?" dialog that blanks the WebView and returns home).
- `CursorController` synthesizes `MotionEvent`s (hover/down/up) onto the WebView to emulate a mouse; geometry (acceleration, edge detection) is the **pure** `CursorGeometry`. OK long-press toggles CURSOR/FOCUS mode.
- `ChromeController` / `ChromeVisibility` manage the auto-hiding address bar, summoned only with **MENU** (a passive on-load reveal shows the current URL without freezing the cursor — see `ChromeController.isActive`). Idle auto-hide arms **only for the passive reveal**; an `isActive` bar (MENU-opened) stays put while the user types/navigates and closes on BACK/MENU/submit. `UrlNormalizer` turns bar text into a loadable URL, or null. The ★ favorite button **toggles** the current page (add/remove) and tints `accent` when the page is saved — see `MainActivity.toggleCurrentFavorite` / `updateFavoriteIcon`.
- `BrowserWebChromeClient` handles HTML5 fullscreen video, and forwards page titles (`onReceivedTitle`) to record browsing history.

### `home/` + `data/` — home screen, favorites, history, settings
- `HomeView` is the launch overlay (favorites grid + URL entry); `FavoriteDialog` edits entries. `HistoryView` is the history overlay (a vertical list of visited sites; open / clear-all / long-press-to-favorite).
- Favorites use the repository pattern: `FavoritesRepository` interface, `JsonFavoritesStore` implementation persisting to `filesDir/favorites.json`. The **pure** `FavoritesOps` (list transforms) and `FavoritesJson` (serialization) hold all the logic; the store is just file I/O.
- History mirrors the same split: `HistoryRepository` / `JsonHistoryStore` (→ `filesDir/history.json`), with the **pure** `HistoryOps` (dedup-by-url, newest-first, cap 50) and `HistoryJson`. `RelativeTime` (pure) formats the "Nm ago" labels. Recording happens on **page finish** (resolved URL + real title) — not `onPageStarted`, whose pre-redirect URL would leave duplicate rows.
- **Global settings** use the same split: `SettingsRepository` / `JsonSettingsStore` (→ `filesDir/settings.json`) over the **pure** `SettingsJson`; `Settings` is one immutable record (no `Ops` — updates are `copy()`). `SettingsView` is the overlay (reached from a **Settings** button in the home header) with an auto-open toggle and `AlertDialog` pickers for **subtitle language** (`SubtitleLanguagePref`, default English) and **cursor speed** (`CursorSpeed`, a multiplier over `CursorGeometry`). All three are read **live** at use-time via provider lambdas — the auto-open gate in `onStreamAvailable`, `subtitlePref` in `StreamSniffer.bestRequest`, `cursorSpeed.multiplier` in `CursorController` — so changes apply with no restart and need no change-callback. `JsonSettingsStore.current` is `@Volatile` (the sniffer reads it off the UI thread).
- **Overlays are focus-modal:** only one of `homeView` / `historyView` / `settingsView` is visible at a time. Android's D-pad `focusSearch` is window-global, so a second visible overlay behind the active one steals focus — every transition routes through `MainActivity.hideAllOverlays()` (hides all three) before showing one. History is reachable from the home overlay **and** the chrome bar (BACK returns to its origin — home, or the page if opened mid-browse, tracked by `historyFromHome`); settings is reachable only from the home header (BACK → home).
- **Focus recovery:** a synchronous `requestFocus` right after an overlay becomes `VISIBLE` can no-op before the layout pass, leaving nothing focused (dead D-pad). All three overlays' `show()` post `restoreFocus()`; as a backstop, `MainActivity.dispatchKeyEvent` re-seats focus (settings → history → home → WebView) whenever `currentFocus == null` and swallows that one keypress.
- **BACK / exit chain** (`MainActivity.onBackPressed`): settings overlay → hide (→ home); history overlay → hide; home overlay → `confirmExit` ("Exit mrowser?" dialog → `finish`); browsing → `confirmCloseTab` (via `CursorLayout.onExitPage`). Opening a page from home is a **fresh tab**: `openUrl` sets `clearHistoryOnLoad`, and the first `onPageFinished` calls `webView.clearHistory()` so the opened page is the lone back-stack entry and BACK reaches root immediately.

## Conventions

- **No AndroidX/Compose** beyond Media3. Activities extend the framework `Activity`, UI is XML layouts in `res/layout`, and pure modules are plain Kotlin `object`s/classes with no Android imports. Keep it that way.
- Netflix-style dark theme; brand red is `#E50914`.
- Design docs, specs, and milestone plans live in `docs/superpowers/`. Read the relevant milestone plan before changing cursor input or the handoff pipeline — they document the intended control scheme and edge cases.

## Release & CI

`.github/workflows/build.yml` runs `./gradlew test` + `assembleDebug` on every push/PR (`test-and-debug` job), and builds a **signed** release APK on GitHub Release publish (`release-apk` job, gated by `if: github.event_name == 'release'`) using secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (decoded into a temporary `keystore.properties`). The `release-apk` job needs `permissions: contents: write` to attach the APK — keep it.

Actions are pinned to commit SHAs (supply-chain safety); `.github/dependabot.yml` opens weekly PRs bumping those pins.

**Releasing:** bump `versionCode` (must increase per release) + `versionName` in `app/build.gradle.kts`, push, then `gh release create vX.Y.Z --prerelease --title … --notes …` — the published event triggers the signed build, which attaches `app-release.apk`. Drop `--prerelease` for a stable release. Remote `origin` = `github.com/m-salehi-v/mrowser` (private). Locally, `keystore.properties` and `release.keystore` are gitignored.
