# Browsing History — Design

**Date:** 2026-06-24
**Status:** Approved, ready for implementation plan

## Goal

Track recently visited websites and show them on a dedicated History page. Keep the
newest 50 visits, deduplicated by URL. Reachable from both the home overlay and the
chrome (address) bar.

## Requirements

- Record every main-frame page navigation (URL + title + visit time).
- Retention: keep the newest **50** distinct entries. Revisiting a URL already in
  history moves it to the top and refreshes its timestamp (dedup by URL, no duplicate rows).
- History page actions: **open** an entry, **clear all** history, **add an entry to
  favorites**.
- Reachable from: (1) the home overlay, (2) the chrome bar (summoned with MENU).
- Persist across app restarts.

Out of scope (YAGNI): single-entry delete, search/filter, time-bucketed grouping
("Today"/"Yesterday"), per-site favicons, sync.

## Architecture

Mirror the existing Favorites clean-architecture split exactly: a thin Android store
over pure, unit-testable logic. New code lives under `net.mrowser.data` (data layer)
and `net.mrowser.home` (overlay view), matching where Favorites lives.

### Data layer (`data/`)

- **`HistoryEntry.kt`** — `data class HistoryEntry(val title: String, val url: String, val visitedAt: Long)`.
  `visitedAt` is epoch millis.
- **`HistoryRepository.kt`** — interface:
  - `findAll(): List<HistoryEntry>` (newest first)
  - `record(entry: HistoryEntry)`
  - `clear()`
- **`HistoryOps.kt`** — **pure** object, no Android imports:
  - `record(list, entry, cap = 50): List<HistoryEntry>` — remove any existing entry with
    the same `url`, prepend the new entry, then truncate to `cap`.
  - `clear(): List<HistoryEntry>` — returns empty list.
  - Immutable: always returns a new list, never mutates input.
- **`HistoryJson.kt`** — **pure** object: `toJson(list): String` / `fromJson(text): List<HistoryEntry>`
  using `JSONArray`. Blank/corrupt input → empty list (mirror `FavoritesJson`).
- **`JsonHistoryStore.kt`** — `HistoryRepository` implementation. Loads
  `filesDir/history.json` into an in-memory list on init; each mutation calls the matching
  `HistoryOps.*` to produce a new list, then `persist()` writes via `HistoryJson`. File I/O
  isolated in `persist()`. Mirrors `JsonFavoritesStore`.

### Recording hook

- The existing `onNavigate` lambda in `MainActivity` (fired by
  `SniffingWebViewClient.onPageStarted`, main-frame only) also calls
  `history.record(HistoryEntry(host(url), url, now))`.
- `onPageStarted` has the URL but not yet the page title, so the entry is first recorded
  with the **URL host** as a placeholder title.
- Add `onReceivedTitle(view, title)` to the WebView's `WebChromeClient`
  (`BrowserWebChromeClient`) → re-record the current URL with the real title. Because
  `HistoryOps.record` dedups by URL, the title update is just a re-record of the same URL
  that bumps it back to the top with the correct title (no duplicate).

### UI layer (`home/`)

- **`history_view.xml`** — full-screen overlay (like `home_view.xml`):
  - Top bar: "History" heading + "Clear all" button.
  - Vertical scrollable list of rows.
  - Empty-state hint shown when there are no entries.
- **`history_row.xml`** — one row: letter tile (first char of title, stable hue from URL
  hash, reusing the Favorites tile treatment), title (ellipsized), url subtitle, relative
  time ("2h ago").
- **`HistoryView.kt`** — mirrors `HomeView`:
  - `bind(repository, onOpen, onClear, onAddFavorite)`
  - `show()` / `hide()` (visibility + `refresh()` + focus)
  - `refresh()` — fetch `findAll()`, clear list, render rows
  - Row click → `onOpen(entry.url)`. Row long-press → `onAddFavorite(entry)`.
  - "Clear all" → `onClear()`.
  - Relative-time formatting is a small pure helper (unit-testable) given a now-timestamp.

### Wiring (`MainActivity.onCreate`)

- Construct `history = JsonHistoryStore(File(filesDir, "history.json"))` next to the
  favorites store.
- Bind `HistoryView`:
  - `onOpen = { openUrl(it) }`
  - `onClear = { history.clear(); historyView.refresh() }`
  - `onAddFavorite = { favorites.add(Favorite(it.title, it.url)); toast("Added to favorites") }`
- Entry points (both):
  1. **Home overlay** — add a "History" button near the URL pill in `home_view.xml`,
     wired to `historyView.show()`.
  2. **Chrome bar** — add a History action/icon to the chrome bar, wired to
     `historyView.show()`.
- Navigation: `historyView` shows/hides like `homeView`. BACK from the History overlay
  returns to the home overlay (matching the existing home/back pattern in `onBackPressed`).

## Data flow

```
WebView main-frame nav
  → SniffingWebViewClient.onPageStarted(url)
    → onNavigate(url) lambda in MainActivity
      → history.record(HistoryEntry(host, url, now))   [placeholder title]
  → BrowserWebChromeClient.onReceivedTitle(title)
    → history.record(HistoryEntry(realTitle, url, now)) [dedup bumps same URL, real title]

JsonHistoryStore.record → HistoryOps.record (pure, dedup + cap 50) → persist → HistoryJson.toJson → history.json
```

## Error handling

- Corrupt / missing `history.json` → empty list (never crash); mirror `FavoritesJson`
  fallback.
- Null/blank URL from `onPageStarted` → skip recording.
- File write failures in `persist()` are swallowed like the Favorites store (history is
  non-critical, best-effort persistence).

## Testing

Pure modules only (JVM, no device), mirroring `FavoritesOpsTest` / `FavoritesJsonTest`:

- **`HistoryOpsTest`**
  - `record prepends a new entry`
  - `record dedups by url and bumps to top with new timestamp`
  - `record caps the list at 50 newest`
  - `clear empties the list`
- **`HistoryJsonTest`**
  - `round trips a list`
  - `corrupt input is an empty list`
- Relative-time helper: a couple of cases (seconds/minutes/hours/days) against a fixed now.

## Files touched

New:
- `app/src/main/kotlin/net/mrowser/data/HistoryEntry.kt`
- `app/src/main/kotlin/net/mrowser/data/HistoryRepository.kt`
- `app/src/main/kotlin/net/mrowser/data/HistoryOps.kt`
- `app/src/main/kotlin/net/mrowser/data/HistoryJson.kt`
- `app/src/main/kotlin/net/mrowser/data/JsonHistoryStore.kt`
- `app/src/main/kotlin/net/mrowser/home/HistoryView.kt`
- `app/src/main/res/layout/history_view.xml`
- `app/src/main/res/layout/history_row.xml`
- `app/src/test/kotlin/net/mrowser/data/HistoryOpsTest.kt`
- `app/src/test/kotlin/net/mrowser/data/HistoryJsonTest.kt`

Modified:
- `app/src/main/kotlin/net/mrowser/MainActivity.kt` (construct store, wire view, record hook, entry points, back nav)
- `app/src/main/kotlin/net/mrowser/stream/BrowserWebChromeClient.kt` (`onReceivedTitle` hook)
- `app/src/main/res/layout/home_view.xml` (History button)
- chrome bar layout (History action/icon)
