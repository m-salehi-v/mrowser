# Milestone A — Usability & Looks — Design

- **Date:** 2026-06-18
- **Status:** Approved design (pre-implementation)
- **Builds on:** Plan 1 (baseline WebView browser, merged to `main`)
- **Driven by:** on-box user feedback — address bar always visible / no fullscreen, no cursor / can't scroll, UI too bare.

## 1. Overview

Make the browser pleasant to drive from a TV remote and stop it looking bare. Three capabilities plus a visual restyle: a D-pad **mouse cursor**, **fullscreen video** with an **auto-hiding address bar**, and a **Netflix-style dark red** chrome. The audio/video sync fix is explicitly *not* here — that is Milestone B (native player handoff).

## 2. Goals & non-goals

**Goals**
- Navigate any site comfortably with the remote: a cursor dot you move with the D-pad, OK to click, edge auto-scroll.
- Watch video fullscreen with no chrome in the way.
- Address bar hidden by default, summoned deliberately.
- A deliberate dark, cinematic look (red accent, strong TV focus cues).

**Non-goals (deferred)**
- Native ExoPlayer handoff / sync fix → Milestone B.
- Home screen, favorites/bookmarks, settings screen → Milestone C.
- Tabs, history, downloads.

## 3. Control scheme (confirmed)

| Input | Cursor mode (default) | Focus mode | Chrome bar visible |
|-------|----------------------|------------|--------------------|
| D-pad | Move cursor (hold = accelerate); at top edge with page at `scrollY==0`, a further UP reveals the bar; near top/bottom edge auto-scrolls | Native WebView focus nav + scroll | Navigate bar controls; DOWN returns to page |
| OK (short) | Tap at cursor (synthesized `MotionEvent`) | Click focused element | Activate control (URL field opens IME) |
| OK (long-press ≥500 ms) | Toggle → focus mode | Toggle → cursor mode | — |
| BACK | Hide bar if shown → else exit fullscreen → else WebView back → else finish | same | Hide bar |

The dual use of UP (move cursor vs reveal bar) is unambiguous: UP keeps moving the cursor up; only once the cursor is pinned at the top edge **and** the page cannot scroll up does a further UP reveal the bar.

## 4. Visual direction (confirmed)

Dark cinema-minimal, Netflix energy.

- Surface `#141414`, accent **`#E50914`** (red), text `#FFFFFF`, hint `#8C8C8C`.
- Frosted address bar: semi-transparent dark (`#CC1A1A1A`) floating over the page.
- **Bold red focus rings** via state-list drawables — TVs are viewed across a room; focus must be obvious.
- Cursor: red filled dot (~10 dp) with a thin light outline + soft shadow for contrast on any background.
- Clean system sans, generous spacing.

## 5. Components

All under `net.mrowser.web`. Pure logic is split out so it can be unit-tested off-device.

### 5.1 `CursorGeometry` (pure)
Stateless math, no Android types:
- `clamp(x, y, width, height) -> Point`
- `step(current, dirX, dirY, speedPx) -> Point` (then clamped)
- `speedForHoldMs(ms) -> Float` — ramps from base (12 dp/frame) to max (40 dp/frame) over 600 ms.
- `isAtTopEdge(y, zonePx)`, `isAtBottomEdge(y, height, zonePx)` — `zonePx` ≈ 48 dp.

### 5.2 `CursorController`
Owns cursor state (position, mode, held direction). Drives a ~60 fps repeating `Runnable` while a direction key is held, using `CursorGeometry`. Produces:
- **Tap:** `MotionEvent.obtain` ACTION_DOWN then ACTION_UP at the cursor, dispatched to the WebView; also an ACTION_HOVER_MOVE before tap so CSS `:hover` menus open.
- **Edge auto-scroll:** while the cursor sits in the top/bottom edge zone, scroll the page via `webView.scrollBy` (MVP — simple and reliable for the common case). If the target site puts controls inside an inner scroll container this misses, switch to a synthesized vertical drag under the cursor (verified on-device); not built until proven necessary.
- **Mode toggle** on long-press OK.

### 5.3 `ChromeVisibility` (pure)
A tiny reducer: `reduce(state, event) -> state` over states `HIDDEN | VISIBLE` and events `RevealRequested(atTop)`, `Interacted`, `PageInteracted`, `IdleElapsed`.
- `HIDDEN + RevealRequested(atTop=true) -> VISIBLE`; `atTop=false -> HIDDEN`.
- `VISIBLE + PageInteracted -> HIDDEN`; `VISIBLE + IdleElapsed -> HIDDEN`; `VISIBLE + Interacted -> VISIBLE`.

### 5.4 `ChromeController`
Wraps `ChromeVisibility`, animates the bar in/out (slide + fade), owns the 4 s idle timer (reset on `Interacted`), and moves focus (URL field on reveal, page on hide).

### 5.5 `CursorLayout`
`FrameLayout` containing the WebView and the chrome bar overlay. Intercepts D-pad in `dispatchKeyEvent` and routes to `CursorController` / `ChromeController` per the table in §3. Draws the cursor in `dispatchDraw` (skipped in focus mode and fullscreen).

### 5.6 `BrowserWebChromeClient`
`WebChromeClient` handling `onShowCustomView`/`onHideCustomView`: adds the player view fullscreen above everything, hides bar + cursor, sets `KEEP_SCREEN_ON`; restores on hide / BACK.

### 5.7 `MainActivity` (refactor)
Replaces the Plan 1 LinearLayout+visible-bar with `CursorLayout`. Keeps `UrlNormalizer`. Wires WebView settings, `BrowserWebChromeClient`, the controllers, and the key routing.

## 6. State & data flow

`onCreate` builds `CursorLayout(WebView + chrome bar)`, attaches `CursorController` + `ChromeController` + `BrowserWebChromeClient`. Key events → `CursorLayout.dispatchKeyEvent` → controller → WebView (synthesized motion) or chrome (reveal/hide) or fullscreen. Page scroll position is read from `webView.scrollY` to gate bar reveal.

## 7. Error handling

- Synthesized tap lands on a non-interactive element → no-op (harmless); user can long-press OK → focus mode as a fallback.
- Edge-scroll drag misread as a tap → guarded by a movement threshold.
- `onShowCustomView` called twice / old WebView quirks → ignore a second view while one is active; always restore on `onHideCustomView` and BACK.
- All controllers are null-safe against a destroyed WebView.

## 8. Testing

- **Unit (JVM):** `CursorGeometry` (clamp bounds, step direction, hold-acceleration curve, edge detection), `ChromeVisibility` (every state/event transition). TDD these.
- **Manual on-box:** MotionEvent tap/click on the real site, edge auto-scroll inside the player page, long-press mode toggle, bar reveal/auto-hide, fullscreen enter/exit, look-and-feel/focus visibility.

## 9. Files

```
app/src/main/kotlin/net/mrowser/
  MainActivity.kt                 (refactor: host CursorLayout)
  web/CursorGeometry.kt           (pure math)
  web/CursorController.kt         (cursor state + MotionEvent synthesis + edge scroll)
  web/ChromeVisibility.kt         (pure state reducer)
  web/ChromeController.kt         (bar animation + idle timer + focus)
  web/CursorLayout.kt             (FrameLayout host + key routing + draw cursor)
  web/BrowserWebChromeClient.kt   (fullscreen video)
app/src/main/res/
  layout/activity_main.xml        (CursorLayout root + chrome bar overlay)
  drawable/cursor.xml  focus_ring.xml  bar_background.xml
  values/colors.xml  themes.xml   (Netflix dark restyle)
app/src/test/kotlin/net/mrowser/web/
  CursorGeometryTest.kt  ChromeVisibilityTest.kt
```

## 10. Risks

- **MotionEvent synthesis coverage** — some players use pointer/hover events; mitigated by hover-before-tap and the focus-mode fallback. Verify on the real site.
- **Edge-scroll vs inner scroll** — MVP uses `webView.scrollBy` (page scroll); inner-container scroll via synthesized drag is added on-device only if the target site needs it.
- **Old system WebView fullscreen** — verify `onShowCustomView` on the box; the user already runs TV Bro (same WebView) so fullscreen is expected to work.

## 11. Out of scope (→ later milestones)

Home screen, favorites, settings (Milestone C). Native player handoff / sync fix (Milestone B). Tabs, history, downloads.
