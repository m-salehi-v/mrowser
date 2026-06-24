# Global Settings — Design

**Date:** 2026-06-24
**Status:** Approved, ready for implementation plan

## Goal

A global Settings page for app-wide preferences, reached from the home overlay. Ships
with three settings: auto-open the synced player, preferred subtitle language, and cursor
speed. All persist across restarts and apply live (no app restart needed).

## Requirements

- **Auto-open synced player** (toggle, default **On**): On → a detected HLS manifest
  auto-launches `PlayerActivity` (today's behavior). Off → only the play chip shows; the
  user activates the chip to play (the `onChipClick → handoff.play()` path already exists).
- **Preferred subtitle language** (Persian / English / **Off**, default **English**): sets
  the default subtitle track.
  - Persian/English → the first side-loaded subtitle is tagged with that language code +
    label and marked default; the player's `preferredTextLanguage` is set to the code.
  - Off → no subtitle marked default, `preferredTextLanguage` unset. The CC button still
    lets the user turn subtitles on manually.
  - This flips the app's shipped default from Persian to English.
- **Cursor speed** (Slow / **Normal** / Fast, default **Normal**): a multiplier on the
  `CursorGeometry` speed ramp — Slow 0.6×, Normal 1.0×, Fast 1.5×.
- Reachable from a **gear button in the home overlay header**, to the right of History.
  BACK from the Settings overlay returns home.
- Persist across app restarts; changes apply on next use without a restart.

Out of scope (YAGNI): per-site settings, import/export, default playback speed,
desktop-site / User-Agent toggle, additional subtitle languages, a Settings entry point on
the chrome bar.

## Architecture

Mirror the existing Favorites/History clean-architecture split: a thin Android store over
pure, unit-testable logic. New data code lives under `net.mrowser.data`; the overlay view
under `net.mrowser.home` (where Home/History live).

Settings is a single immutable record (not a list), so there is **no `SettingsOps`** —
updates are plain `copy()`. Pure logic is limited to (de)serialization and the enums.

### Data layer (`data/`)

- **`SubtitleLanguagePref.kt`** — **pure** enum, no Android imports:
  - `PERSIAN(code = "fa")`, `ENGLISH(code = "en")`, `OFF(code = null)`.
  - Carries only the language `code: String?`. The human label is a string resource
    resolved in the UI layer (keeps the enum Android-free).
- **`CursorSpeed.kt`** — **pure** enum: `SLOW(multiplier = 0.6f)`, `NORMAL(1.0f)`,
  `FAST(1.5f)`.
- **`Settings.kt`** — `data class Settings(val autoOpenPlayer: Boolean = true,
  val subtitleLanguage: SubtitleLanguagePref = SubtitleLanguagePref.ENGLISH,
  val cursorSpeed: CursorSpeed = CursorSpeed.NORMAL)`. Defaults encode the shipped values.
- **`SettingsRepository.kt`** — interface:
  - `get(): Settings`
  - `update(settings: Settings)`
- **`SettingsJson.kt`** — **pure** object: `toJson(settings): String` /
  `fromJson(text): Settings` using `JSONObject`. Every field falls back to its default when
  missing or unparseable (unknown enum name → default). Blank/corrupt input → all defaults.
  Mirrors `FavoritesJson`'s defensive parsing.
- **`JsonSettingsStore.kt`** — `SettingsRepository` implementation. Loads
  `filesDir/settings.json` into an in-memory `Settings` on init (defaults if absent);
  `update` replaces the cached value and `persist()` writes via `SettingsJson`. File I/O
  isolated in `persist()`, write failures swallowed (mirror `JsonFavoritesStore`).

### Subtitle assembly (pure, extracted from `StreamSniffer`)

Today `StreamSniffer.bestRequest` builds the `SubtitleTrack` list inline (first sub forced
to Persian + default). Extract this into a **pure** helper so the language/Off branching is
unit-testable without Android:

- New **pure** function (e.g. `SubtitlePlan.build` in `stream/`, no Android imports):
  given the selected subtitle candidates (URLs already chosen by
  `StreamCandidateSelector.selectSubtitles`) and a `SubtitleLanguagePref`, returns
  `(tracks: List<SubtitleTrack>, preferredLanguage: String?)`:
  - Per-candidate MIME stays as today (`.srt` → `application/x-subrip`, else `text/vtt`).
  - Persian/English: first track gets the pref's code + label + default selection; the rest
    get `"und"` + `"Subtitle N"` labels, no default.
  - Off: no track marked default; `preferredLanguage = null`.
  - Non-Off: `preferredLanguage = pref.code`.

`StreamSniffer.bestRequest` calls this helper and puts `preferredLanguage` into the
`PlaybackRequest`.

### Transport (`stream/`)

- **`PlaybackRequest`** — add `val preferredTextLanguage: String?` (nullable). Carried
  through `toJson`/`fromJson` so the pref reaches `PlayerActivity` via the Intent and the
  player stays stateless (no second settings-store read, no cross-process cache staleness).

### Cursor geometry (`web/`)

- **`CursorGeometry.speedForHoldMs(heldMs: Long, multiplier: Float = 1f)`** — scale the
  ramped result by `multiplier` (default `1f` keeps current behavior and existing call
  sites/tests valid). Stays pure.
- **`CursorController`** — gains a `speedMultiplier: () -> Float` provider, read live each
  frame and passed to `speedForHoldMs`. Default provider returns `1f` if unset.

### UI layer (`home/`)

- **`settings_view.xml`** — full-screen overlay (like `home_view.xml`): a "Settings"
  heading + a vertical list of three D-pad-focusable rows, each showing a title and the
  current value.
- **`settings_row.xml`** — one row: title (start) + current-value label (end), focusable
  with the existing focus-ring treatment.
- **`SettingsView.kt`** — mirrors `HistoryView`:
  - `bind(repository: SettingsRepository)`.
  - `show()` / `hide()` (visibility + render + posted `restoreFocus()`); focus-modal.
  - Renders the three rows from `repository.get()`.
  - **Auto-open** row → toggles the boolean inline on OK, updates its value label.
  - **Subtitle language** row → opens an `AlertDialog` `setItems` list
    (Persian / English / Off) — same pattern as `PlayerActivity.showSettings`.
  - **Cursor speed** row → `AlertDialog` `setItems` (Slow / Normal / Fast).
  - Each change calls `repository.update(...)` and re-renders that row's value label. No
    callback to `MainActivity` is needed — all three settings are read live via providers,
    so changes take effect on next use with no notification.
- **`ic_settings.xml`** — gear vector drawable matching the existing chrome icons.
- New strings in `strings.xml` (page title, row titles, each option label).

### Wiring (`MainActivity.onCreate`)

- Construct `settings = JsonSettingsStore(File(filesDir, "settings.json"))` next to the
  favorites/history stores.
- **Auto-open gate**: `onStreamAvailable` becomes
  `runOnUiThread { showChip(); if (settings.get().autoOpenPlayer) handoff.play() }`.
- **Cursor speed**: build `CursorController(webView, speedMultiplier = {
  settings.get().cursorSpeed.multiplier }) { layout.invalidate() }`.
- **Subtitle pref**: pass `subtitlePref = { settings.get().subtitleLanguage }` into
  `StreamSniffer` (a provider lambda, consistent with its existing `userAgent` lambda).
- **Settings overlay**: `settingsView.bind(settings)`. No change-callback wiring — all
  three settings are read live at use-time (auto-open in `onStreamAvailable`, subtitle in
  `bestRequest`, cursor speed each frame).
- **Entry point**: add a gear `ImageButton` to the home header (`home_view.xml`), right of
  the History button, wired through `HomeView` to open the Settings overlay. Showing
  Settings hides Home/History (focus-modality rule). BACK from Settings → `showHome()`
  (extend `onBackPressed`, matching the History-from-home branch).

### Player (`player/`)

- **`PlayerActivity`** — replace the hard-coded `setPreferredTextLanguage("fa")`: read
  `request.preferredTextLanguage`; if non-null set it, else leave it unset. Subtitle
  `SubtitleConfiguration` default flags come from the request's tracks (already built by the
  pure helper), so the player no longer assumes Persian.

## Data flow

```
Settings change (SettingsView)
  → repository.update(Settings)            [JsonSettingsStore: cache + persist → settings.json]

Stream detected
  → StreamSniffer.onStreamAvailable
    → MainActivity: showChip(); if (settings.get().autoOpenPlayer) handoff.play()

handoff.play → sniffer.bestRequest()
  → SubtitlePlan.build(subs, settings.subtitleLanguage) → (tracks, preferredLanguage)
  → PlaybackRequest(url, headers, tracks, pageUrl, preferredTextLanguage)
  → Intent → PlayerActivity: setPreferredTextLanguage(pref?) + default-flagged tracks

Cursor move (each frame)
  → CursorController: CursorGeometry.speedForHoldMs(held, settings.cursorSpeed.multiplier)
```

## Error handling

- Corrupt / missing `settings.json` → all-defaults `Settings` (never crash); mirror
  `FavoritesJson` fallback. Unknown enum names in JSON → that field's default.
- `persist()` write failures swallowed (settings are best-effort, like favorites/history).
- `preferredTextLanguage` null is valid (the Off case) and means "do not set a preference".

## Testing

Pure modules only (JVM, no device), mirroring `FavoritesJsonTest` / `CursorGeometryTest`:

- **`SettingsJsonTest`**
  - `round trips all fields`
  - `missing fields fall back to defaults`
  - `corrupt / blank input is all defaults`
  - `unknown enum name falls back to default`
- **`SubtitlePlanTest`** (the extracted pure helper)
  - `english: first track tagged en + default, preferred = en`
  - `persian: first track tagged fa + default, preferred = fa`
  - `off: no default track, preferred = null`
  - `extra tracks get und + generic labels, no default`
- **`CursorGeometryTest`** (extend)
  - `multiplier scales the ramped speed` (and `multiplier 1f` matches existing values).
- **`PlaybackRequestTest`** (extend)
  - `preferredTextLanguage round-trips (including null)`.

## Files touched

New:
- `app/src/main/kotlin/net/mrowser/data/SubtitleLanguagePref.kt`
- `app/src/main/kotlin/net/mrowser/data/CursorSpeed.kt`
- `app/src/main/kotlin/net/mrowser/data/Settings.kt`
- `app/src/main/kotlin/net/mrowser/data/SettingsRepository.kt`
- `app/src/main/kotlin/net/mrowser/data/SettingsJson.kt`
- `app/src/main/kotlin/net/mrowser/data/JsonSettingsStore.kt`
- `app/src/main/kotlin/net/mrowser/stream/SubtitlePlan.kt` (pure subtitle assembly)
- `app/src/main/kotlin/net/mrowser/home/SettingsView.kt`
- `app/src/main/res/layout/settings_view.xml`
- `app/src/main/res/layout/settings_row.xml`
- `app/src/main/res/drawable/ic_settings.xml`
- `app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt`
- `app/src/test/kotlin/net/mrowser/stream/SubtitlePlanTest.kt`

Modified:
- `app/src/main/kotlin/net/mrowser/MainActivity.kt` (construct store, wire view + gear, auto-open gate, providers, back nav)
- `app/src/main/kotlin/net/mrowser/stream/StreamSniffer.kt` (subtitlePref provider, use SubtitlePlan in bestRequest)
- `app/src/main/kotlin/net/mrowser/stream/PlaybackRequest.kt` (`preferredTextLanguage` field)
- `app/src/main/kotlin/net/mrowser/player/PlayerActivity.kt` (use request pref instead of hard-coded "fa")
- `app/src/main/kotlin/net/mrowser/web/CursorGeometry.kt` (multiplier param)
- `app/src/main/kotlin/net/mrowser/web/CursorController.kt` (speedMultiplier provider)
- `app/src/main/kotlin/net/mrowser/home/HomeView.kt` (gear button → open Settings)
- `app/src/main/res/layout/home_view.xml` (gear button in header)
- `app/src/main/res/values/strings.xml` (settings strings)
- `app/src/test/kotlin/net/mrowser/web/CursorGeometryTest.kt` (multiplier cases)
- `app/src/test/kotlin/net/mrowser/stream/PlaybackRequestTest.kt` (preferred-language round-trip)
