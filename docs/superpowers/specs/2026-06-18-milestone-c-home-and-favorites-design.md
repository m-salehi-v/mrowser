# Milestone C — Home & Favorites — Design

- **Date:** 2026-06-18
- **Status:** Approved design (pre-implementation)
- **Builds on:** Milestones A + B (merged to `main`).

## 1. Overview

Give mrowser a real front door: a dark, Netflix-style **home screen** with a **favorites** grid and a URL field, shown on launch instead of the welcome page. The user adds the current page to favorites with a ★ button, opens a favorite to browse it, and returns home with a Home button or BACK. This is the "functional browser" milestone.

No new dependencies: the grid uses the framework `GridLayout`, not RecyclerView.

## 2. Goals & non-goals

**Goals**
- A home screen on launch: wordmark + URL field + favorites grid.
- Add the current page to favorites (★); edit/delete favorites.
- Persist favorites locally (JSON in `filesDir`).
- A deliberate dark/red look consistent with A/B; strong TV focus cues.
- Clean home↔web navigation with sensible BACK behavior.

**Non-goals (YAGNI)**
- Tabs, history, multiple windows.
- Favicons / thumbnails (letter tiles for now; favicons are a later enhancement that needs network + caching).
- A settings screen.
- Pre-seeded sites (open-source: ship empty).

## 3. Visual design

Dark cinema-minimal, same palette as A (`#141414` surface, `#E50914` accent, white text).
- **Header:** "mrowser" wordmark (white, the "m" or the play motif in red) + a focusable URL/search pill (rounded, dark, red focus ring).
- **Favorites grid:** framework `GridLayout` (≈4 columns) inside a vertical `ScrollView`. Each **card**: rounded dark tile with a colored **letter block** (first letter of the title on an accent-derived color) above the title text. Focus: **red border + scale to ~1.1** via an `OnFocusChangeListener` animation.
- **Empty state:** centered hint — "Add a site — press ★ while browsing, or type a URL above."

## 4. Components

### 4.1 `data/Favorite.kt` (data)
`data class Favorite(val title: String, val url: String)`. Identity is `url` (favorites are unique by URL).

### 4.2 `data/FavoritesJson.kt` (pure, tested)
- `toJson(items: List<Favorite>): String` and `fromJson(json: String): List<Favorite>` via `org.json`. Tolerates empty/blank input (returns empty list).

### 4.3 `data/FavoritesRepository.kt` (interface)
- `findAll(): List<Favorite>`
- `add(favorite: Favorite)` — upsert by URL (replaces an existing entry with the same URL; new entries go to the front).
- `remove(url: String)`
- `update(oldUrl: String, favorite: Favorite)` — replace the entry at `oldUrl`.

### 4.4 `data/JsonFavoritesStore.kt` (file I/O)
`FavoritesRepository` backed by `filesDir/favorites.json`, delegating (de)serialization to `FavoritesJson`. Reads on construction, writes after each mutation. Immutable in-memory list (each op produces a new list).

### 4.5 `home/HomeView.kt` (custom `FrameLayout`)
- Inflates the header + URL pill + scrollable `GridLayout`.
- `bind(repository, onOpen: (Favorite) -> Unit, onSubmitUrl: (String) -> Unit)`; `refresh()` rebuilds the cards from `repository.findAll()`; `show()` / `hide()` toggle visibility and move focus.
- Builds each card by inflating `favorite_card.xml`, sets title + letter + a stable per-URL tile color, wires OK → `onOpen`, long-press OK → edit/delete dialog, and the focus scale animation.
- The URL pill opens the IME; `actionGo` → `onSubmitUrl` (normalized via `UrlNormalizer`).

### 4.6 `home/FavoriteDialog.kt`
Plain `AlertDialog` helpers: edit (title + URL fields, save → `repository.update`) and delete (confirm → `repository.remove`), invoking a refresh callback.

### 4.7 `MainActivity` wiring
- Root layout becomes `FrameLayout { CursorLayout(web…), HomeView }`, `HomeView` on top and visible on launch (no more welcome page).
- Chrome bar gains a **★ (favorite)** button — adds `Favorite(webView.title ?: url, webView.url)` — and a **Home** button — `showHome()`.
- `onOpen`/`onSubmitUrl` load the URL in the WebView and hide home.
- BACK: if home is visible → exit the app; else (web) `CursorLayout` handles fullscreen/chrome/`goBack`, and when it has nothing left it returns false, so `MainActivity` shows home instead of finishing.
- The cursor is only active in web mode; while home is visible it is focused and consumes D-pad, so `CursorLayout` never sees those keys.

## 5. Data flow

launch → `HomeView` visible, cards from `JsonFavoritesStore` → user opens a card / submits a URL → WebView loads, home hides, cursor active → browse (A) / play synced (B) → ★ adds current page, Home button or exhausted BACK → home.

## 6. Error handling

- Corrupt/missing `favorites.json` → `FavoritesJson.fromJson` returns an empty list; the store starts empty.
- Add with a blank URL (no page loaded) → ignored (★ only acts when `webView.url` is non-null).
- Duplicate add (same URL) → upsert, no duplicate card.
- Write failure (storage) → logged; in-memory list still updates for the session.

## 7. Testing

- **Unit (JVM, TDD):** `FavoritesJson` (round-trip, empty/blank input), and the upsert/remove/update list logic (extracted as pure functions in `FavoritesJson` or a small `FavoritesOps` so they are testable without a `File`).
- **Manual on-box:** grid focus + scale, add via ★, edit/delete dialog, home↔web navigation, BACK behavior, empty-state.

## 8. File structure

```
app/src/main/kotlin/net/mrowser/
  data/Favorite.kt
  data/FavoritesJson.kt          (pure, tested)
  data/FavoritesOps.kt           (pure upsert/remove/update, tested)
  data/FavoritesRepository.kt    (interface)
  data/JsonFavoritesStore.kt     (file I/O)
  home/HomeView.kt
  home/FavoriteDialog.kt
  MainActivity.kt                (host home + ★/Home buttons; modify)
app/src/main/res/layout/
  activity_main.xml              (root FrameLayout: CursorLayout + HomeView; modify)
  home_view.xml                  (header + url pill + scroll/grid)
  favorite_card.xml              (letter tile + title)
app/src/main/res/drawable/
  card_bg.xml  letter_tile_bg.xml  url_pill_bg.xml
app/src/main/res/values/strings.xml   (add home strings; modify)
app/src/test/kotlin/net/mrowser/data/
  FavoritesJsonTest.kt  FavoritesOpsTest.kt
```

## 9. Risks

- **Framework `GridLayout` focus/scroll on cheap boxes** — standard widgets, well supported; verify on-box.
- **Scale-on-focus clipping** — set `clipChildren=false` on the grid/scroll containers so the enlarged card isn't cut off.
- **`webView.title` null early** — fall back to the host of the URL for the card title.

## 10. Out of scope (→ later)

Tabs, history, favicons, settings, auto-handoff (B follow-up). These are independent future milestones.
