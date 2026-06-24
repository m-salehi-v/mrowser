# Sub Sync — Design

**Date:** 2026-06-24
**Status:** Approved (pending spec review)

## Goal

Let the user nudge side-loaded subtitle timing during playback, ±0.5 s per press,
with **instant** feedback (no rebuffer). A small box appears in the player's
top-left whenever the playback controls are visible:

```
[ − ]  Sub Sync +1.5s  [ + ]
```

- `−` shifts subtitles earlier, `+` later, 500 ms per press.
- Center label shows `Sub Sync` plus the live signed offset (`0.0s` at start).
- Offset is per-stream (resets each new `PlayerActivity`); not persisted.
- Clamp to ±30 s.

## Why a custom pipeline

Media3 1.10 has **no public API to offset subtitle timing live**. The feature
request is open since 2015 ([ExoPlayer #854]) and the "general delay" ask was
closed as a question ([ExoPlayer #9902]) — no built-in. Media3 parses subtitle
cues at load-time (even a custom `SubtitleParser.Factory` runs once at prepare),
so changing the offset live requires re-emitting cues ourselves. Reloading a
re-timed file (the other manual path) rebuffers on every adjustment, which is
unacceptable on TV wifi. Therefore the player owns cue emission for side-loaded
subtitles. This is the same approach Jellyfin / VLC / mpv take.

[ExoPlayer #854]: https://github.com/google/ExoPlayer/issues/854
[ExoPlayer #9902]: https://github.com/google/ExoPlayer/issues/9902

## Architecture

Follows the codebase's pure-logic / Android-glue split. Pure pieces get unit
tests under `app/src/test/kotlin/net/mrowser/player/`.

### Pure modules (no Android imports, unit-tested)

- **`SubtitleCue`** — `data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)`.
- **`SubtitleCueParser`** — `parse(content: String): List<SubtitleCue>`.
  One unified parser handles both WebVTT and SubRip (no `mimeType` arg needed — it keys
  on the `-->` line and normalizes `,`→`.` in timestamps, so format auto-detects):
  - Timestamp `HH:MM:SS,mmm` (SRT) and `HH:MM:SS.mmm` / `MM:SS.mmm` (VTT) → ms;
    a bare `HH:MM:SS` with no fractional part is treated as `.000`.
  - Joins multi-line cue text with `\n`; skips `WEBVTT` header, blank lines, SRT
    numeric index lines, and `NOTE`/`STYLE`/`REGION` blocks. VTT cue settings after the
    end timestamp are stripped.
  - Returns cues sorted by `startMs`. Malformed cue blocks are skipped, not fatal.
- **`ActiveCueFinder`** — `activeAt(cues: List<SubtitleCue>, timeMs: Long): List<SubtitleCue>`.
  Returns cues where `startMs <= timeMs < endMs`. Caller passes
  `currentPositionMs + offsetMs` as `timeMs` (offset can be negative). Pure.

### Android glue

- **`SubtitleFetcher`** — downloads a subtitle URL's text on a background thread
  using `HttpURLConnection` with `PlaybackRequest.headers` (User-Agent / Referer /
  Cookie). Returns the body string, or null on failure (logged, non-fatal — that
  track is dropped). One fetch per track, cached in memory.
- **`SubtitleSyncController`** — owns the live subtitle state for one player:
  - Holds the track list (`label`, `mimeType`, lazily fetched+parsed cues),
    `selectedIndex` (`-1` = off, default `0`), and `offsetMs` (default `0`).
  - A `Handler` ticker (~150 ms) reads `player.currentPosition`, calls
    `ActiveCueFinder.activeAt(cues, position + offsetMs)`, maps results to
    media3 `Cue` objects, and pushes them to the `SubtitleView`
    (`playerView.findViewById(androidx.media3.ui.R.id.exo_subtitles)`).
  - `adjust(deltaMs)` → `offsetMs = (offsetMs + deltaMs).coerceIn(-30_000, 30_000)`;
    updates the box label.
  - `select(index)` sets `selectedIndex`, fetching+parsing that track on first use.
  - Ticker starts after the first track is fetched; stops in `onStop` / `onDestroy`.

### PlayerActivity changes

- `buildMediaItem` **drops** `setSubtitleConfigurations` — the ExoPlayer instance
  has no text track, so it never fights our manual `setCues`.
- After `prepare`, construct `SubtitleSyncController` with the playerView + request
  subtitles; auto-select track `0` (preserves today's "auto-show first track").
- **Subtitle selection uses our own button**, not media3's CC button. With no player
  text track, media3 keeps re-disabling/greying its CC button (its `updateAll()` wins the
  re-install ordering), so it's hidden (`show_subtitle_button=false`) and a `CC` button
  lives in the sync box instead. It shows an `AlertDialog` picker of `Off` + each track
  `label`, calling `controller.select`, and is tinted `accent` when a track is showing
  (`controller.isShowing()`), `hint` when off — updated after each pick.
- The sync box's visibility is driven by the same `ControllerVisibilityListener`
  (VISIBLE with controls, GONE otherwise).

### Layout (`player_activity.xml`)

The root becomes a `FrameLayout` wrapping the existing `PlayerView` plus the
sync-box overlay (currently the root *is* the `PlayerView`). The box:
`LinearLayout` (horizontal), top-left, padded, semi-transparent dark rounded
background, `android:visibility="gone"` initially. Children: `−` button,
center `TextView` (`Sub Sync 0.0s`), `+` button. Buttons are `focusable`. To keep
D-pad navigation sane on TV, set explicit `nextFocusDown` from the buttons toward
the control bar so focus enters/leaves the box cleanly.

## Data flow

```
StreamSniffer → PlaybackRequest.subtitles (unchanged)
        │
PlayerActivity.onCreate
        ├─ ExoPlayer (no text track)
        └─ SubtitleSyncController
               ├─ SubtitleFetcher  (bg) → text
               ├─ SubtitleCueParser     → List<SubtitleCue>   [pure, tested]
               └─ ticker (150ms): ActiveCueFinder.activeAt(cues, pos+offset)  [pure, tested]
                        → SubtitleView.setCues(...)
Box − / +  → controller.adjust(∓500)  → offsetMs, label
CC button  → picker → controller.select(index)
```

## Error handling

- Fetch failure or parse yielding zero cues → that track shows nothing; CC picker
  still lists it; logged, never crashes. Other tracks unaffected.
- Player error path unchanged (`onPlayerError` → finish).
- Ticker guarded against null player / detached SubtitleView.

## Testing

- `SubtitleCueParserTest` — VTT happy path, SRT happy path, `,` vs `.` ms,
  multi-line text, header/index/NOTE skipping, malformed block skipped, empty input.
- `ActiveCueFinderTest` — cue active at boundaries (`start` inclusive, `end`
  exclusive), no overlap, multiple simultaneous cues, negative effective time
  (offset pushes before 0), empty list.
- Offset clamping covered via a small `SubtitleSyncController` pure helper if the
  clamp is extracted, otherwise asserted through `adjust` logic.

## Known limitations

- Embedded / HLS-muxed text tracks are **not** rendered (we removed the player's
  text track). These streaming sites use side-loaded VTT, which are exactly the
  tracks that need sync; muxed subs are already in sync and rare here. Documented
  intentionally.
- Only the **selected** track is fetched/parsed (lazy), so switching tracks may
  briefly show nothing while the new one downloads.
- Inline VTT markup (`<i>`, `<b>`, `<v Speaker>`, karaoke timestamps) is rendered
  verbatim, not as styling — cue text is pushed as-is to `Cue.setText`. Rare for
  side-loaded streaming subs; acceptable.

## Out of scope (YAGNI)

- Persisting offset across streams or a global default offset.
- Per-track independent offsets (one offset applies to the active track).
- Subtitle styling/position controls.
