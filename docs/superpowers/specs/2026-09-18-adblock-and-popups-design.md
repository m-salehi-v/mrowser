# Ad Blocking and Pop-up Hardening — Design

**Date:** 2026-09-18
**Status:** Approved, ready for implementation plan
**Issue:** #38 — "Pop up blocker and adblock"
**Research:** `docs/superpowers/specs/2026-09-18-adblock-and-popups-research.md`

## Goal

Block ad-network traffic and the click-hijacked pop-ups streaming sites use, with a
dependency-free, unit-testable engine that fits the existing pure-logic / thin-Android split.
Two user-visible results:

1. Ads and pop-under scripts served from known ad hosts are refused, so pages load without
   them and their click-hijack scripts never run.
2. A gesture-backed `window.open` or in-page navigation whose destination is a known ad host
   is refused and counted, and the page the user was on stays put.

## Why the current pop-up blocker fails (#38)

`PopupPolicy` keys on `isUserGesture` from `WebChromeClient.onCreateWindow`. Two facts from the
Chromium/AOSP source make that insufficient:

- With `javaScriptCanOpenWindowsAutomatically = false`, Chromium's own blocker
  (`AwContentBrowserClient::CanCreateWindow` returns `js_can_open || user_gesture`) refuses
  every gestureless open **before** `onCreateWindow` is called. Every open we see is
  gesture-backed; our `Block` branch is unreachable.
- Transient user activation is a property of the frame, granted by any trusted click anywhere
  in the document (including inside an ad iframe, which activates its ancestors). A
  document-level click listener that calls `window.open(ad)` passes as a user gesture. Because
  mrowser has no tabs, the ad then replaces the page.

No surveyed WebView browser (TV Bro, Fulguris, Lightning, DuckDuckGo, Jelly, Yuzu, Privacy
Browser) applies a cross-origin, hit-test, timing, or iframe heuristic at `onCreateWindow`.
uBlock Origin's approach is the one that works: evaluate the **destination URL** of the new
window against a **filter list**. That is what this design does, reusing the ad-block host
list.

## Requirements

- **Block ads** setting (toggle, default **On**). Subresource requests to a listed host are
  answered with a typed empty stand-in. Renderer-initiated top-level navigations to a listed
  host are cancelled; the page stays. User-typed URLs are never blocked.
- **Block pop-ups** setting keeps its meaning (window opens go through the relay); the relay
  now refuses a destination on the list.
- **Per-site allow**: a shield button in the chrome bar toggles "allow ads on this site" for
  the current page's registrable domain and reloads. Persisted in settings.
- **Blocked counter**: the shield button's label shows the number of blocked requests and
  pop-ups on the current page; resets on every page start.
- **Attribution**: a Settings row opens a dialog naming the bundled lists, their URLs,
  licence, fetch date and entry count. `THIRD-PARTY-NOTICES.md` at the repo root carries the
  GPLv3 text and attributions.
- **Never break the stream**: nothing `MediaUrlClassifier` recognises as a manifest, segment
  or subtitle is ever blocked, and no first-party subresource is blocked. A canary test on the
  real list file guards CDN apexes and known-good hosts.
- **Bundled list, refreshed at release time** by a CI job that opens a PR. No runtime
  download, no network code.

Out of scope (YAGNI, listed as follow-ups at the end): cosmetic/element hiding, ABP
network-filter syntax, runtime list updates, cross-site pop-up chip, tab-under guard,
hit-test link allowlist, pointing `MediaUrlClassifier.isAdHost` at the new list.

## Architecture

New package `net.mrowser.adblock`. Pure objects carry every decision; `AdBlocker` is the one
Android class and holds state. Settings follow the existing provider-lambda pattern so both
toggles and the allowlist apply live.

```
res/raw/blocklist.txt ──(bg thread, onCreate)──▶ BlockList (@Volatile in AdBlocker)
                                                      │
 WebView request ─▶ SniffingWebViewClient.shouldInterceptRequest
                        ├─ sniffer.onRequest(url)              (unchanged, runs first)
                        └─ adBlocker.intercept(request) ─▶ AdBlockPolicy.decide ─▶ stand-in | null
 SW fetch (API 24+) ─▶ ServiceWorkerClient.shouldInterceptRequest ─▶ adBlocker.intercept
 top-level nav ─▶ SniffingWebViewClient.shouldOverrideUrlLoading
                        ├─ ExternalSchemePolicy                (unchanged)
                        └─ NavigationPolicy.decide ─▶ cancel + count | load
 window.open ─▶ BrowserWebChromeClient.onCreateWindow ─▶ relay peeks URL
                        └─ NavigationPolicy.decide ─▶ refuse + count | loadOrLaunch
```

### The list (`app/src/main/res/raw/blocklist.txt`)

- One domain per line, lowercase, no leading dot or `*.`; each entry means **the domain and
  all its subdomains** (the "domainswild2" / "onlydomains" semantics both sources use).
- Lines beginning with `#` are metadata, ignored by the matcher, parsed for the attribution
  dialog. Header format (one `# key: value` per line):

  ```
  # mrowser block list — generated by scripts/update-blocklist.sh, do not edit by hand
  # generated: 2026-09-18
  # source: oisd small | https://small.oisd.nl/domainswild2 | GPL-3.0 | 56157 entries | version 202609181307
  # source: HaGeZi Pop-Up Ads | https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/popupads-onlydomains.txt | GPL-3.0 | 50556 entries
  # entries: 92643
  ```
- Sources: **OISD small** (low-breakage base, "Block. Don't break.", public excludes list)
  ∪ **HaGeZi Pop-Up Ads** (built from EasyList's three pop-up sections, AdGuard's popup
  filter and the popads lists; covered every streaming pop-under network probed — popads,
  propellerads, exoclick, adsterra, hilltopads, clickadu, richads, zeropark, trafficstars —
  that OISD small missed). Measured 2026-09-18: union 92,643 domains, 729 KB gzipped, no
  video-CDN apex present.

### Pure engine (no Android imports, unit-tested)

`UrlHost` and `RegistrableDomain` live in `web/` next to `UrlNormalizer` (so `stream/` can use
`UrlHost` without depending on `adblock/`, which itself depends on `stream/`); the rest is in
`adblock/`.

- **`UrlHost`** (`web/`) — `of(url: String): String?`: lowercase host from an absolute URL
  string (strip scheme, userinfo, port, path/query). Lifted from the private `hostOf` in
  `MediaUrlClassifier`, which then delegates to it.
- **`RegistrableDomain`** (`web/`) — `of(host: String): String`: last two labels, or last three when
  the last two are in a small built-in set of two-label public suffixes (`co.uk`, `org.uk`,
  `com.au`, `co.jp`, `com.br`, `co.in`, `co.za`, `com.mx`, `com.tr`, `co.kr`, … ~30).
  IP literals and single-label hosts return themselves. No full public suffix list.
- **`BlockList`** — `class BlockList private constructor(hashes: LongArray, info: Info)`.
  - `fromLines(lines: Sequence<String>): BlockList`: skips blank and `#` lines (collecting
    `# source:` / `# generated:` / `# entries:` into `Info`), trims, lowercases, hashes each
    domain with **FNV-1a 64-bit**, sorts, dedupes → `LongArray`.
  - `contains(host: String): Boolean`: **suffix walk** — for `a.b.c.d` test `a.b.c.d`,
    `b.c.d`, `c.d`; stop before the bare TLD (never test `d`). Binary search per step.
  - `size: Int`, `info: Info(generated: String?, sources: List<Source>)`,
    `Source(name, url, licence, entries)`.
  - `EMPTY` companion for tests and the not-yet-loaded state.
  - Rationale: ~0.8 MB heap for ~93k entries versus 6–10 MB as `HashSet<String>`; load is a
    hash pass over the text (~100 ms). 64-bit collision odds at this size are negligible.
- **`AdBlockPolicy`** — subresource decision:

  ```kotlin
  fun decide(
      requestUrl: String, pageHost: String?, isMainFrame: Boolean,
      enabled: Boolean, allowlisted: Boolean, list: BlockList
  ): Decision  // ALLOW | BLOCK
  ```
  Evaluated in this order, first hit wins:
  1. `!enabled` → ALLOW
  2. `allowlisted` (page's registrable domain in `adsAllowedOn`) → ALLOW
  3. `isMainFrame` → ALLOW (main frames are gated by `NavigationPolicy`; an empty body for a
     main frame blanks the page, which TV Bro and Lightning users report as "site broken")
  4. `MediaUrlClassifier.classify(url)` is `MANIFEST_HLS`, `MANIFEST_DASH`, `SEGMENT` or
     `SUBTITLE` → ALLOW (the sniff pipeline must never lose a stream to the list)
  5. `RegistrableDomain.of(host) == RegistrableDomain.of(pageHost)` → ALLOW (first-party)
  6. `list.contains(host)` → BLOCK
  7. else ALLOW
- **`NavigationPolicy`** — top-level navigation / pop-up destination decision:

  ```kotlin
  fun decide(targetUrl: String, enabled: Boolean, allowlisted: Boolean, list: BlockList): Decision // LOAD | BLOCK
  ```
  BLOCK iff `enabled && !allowlisted && scheme is http/https && list.contains(host)`.
  First-party is **not** exempt: the user is being sent to a listed host, whoever sent them.
- **`BlockedResponse`** — `kindFor(url: String, accept: String?): Kind` where `Kind` is
  `IMAGE` (Accept starts with `image/` or path ends in `.gif/.png/.jpg/.jpeg/.webp/.svg`),
  `HTML` (Accept contains `text/html`), `SCRIPT` (path ends in `.js` or `.mjs`), else
  `EMPTY`. Each kind carries `mimeType` and `body: ByteArray` (1×1 transparent GIF, empty
  document, empty script, empty text). Typed stand-ins avoid `onerror` cascades from images
  and broken iframe layouts (Fulguris lesson).
- **`PopupPolicy`** — **deleted** (with its test). Its gestureless `Block` branch was
  unreachable (`AwContentBrowserClient::CanCreateWindow`), and its `AllowNewWindow` branch
  called `super.onCreateWindow`, which returns `false` and silently dropped every window — the
  opposite of what "Block pop-ups: Off" promised. `onCreateWindow` now always relays, ignores
  `isUserGesture`, and the **Block pop-ups** setting gates only the list check on the relayed
  destination: On refuses a listed host, Off opens whatever was clicked. The doc comment on
  `onCreateWindow` records the Chromium reasoning.

### Android glue

- **`AdBlocker`** (`adblock/AdBlocker.kt`) — the one stateful class, safe to call from
  WebView worker threads:
  - `@Volatile var list: BlockList = BlockList.EMPTY` — published by `load(source: () ->
    InputStream)` which runs on a plain background `Thread` started from `MainActivity.
    onCreate`. Until it lands, every request passes (TV Bro pattern; no IO-thread spin).
  - Providers `enabled: () -> Boolean`, `allowedSites: () -> Set<String>` (read live from
    `Settings`; settings access happens on the UI thread today — `JsonSettingsStore.get()`
    returns the cached immutable record, safe to read from a worker thread).
  - `@Volatile var pageHost: String?` — set from the **main-frame request** in
    `shouldInterceptRequest` (earliest redirect-accurate signal) and from `onPageStarted`.
  - `blockedCount: AtomicInteger`, `resetForPage()` on page start, `onCountChanged: (Int) ->
    Unit` invoked on the UI thread via a `Handler`.
  - `intercept(url: String, isMainFrame: Boolean, accept: String?): WebResourceResponse?` —
    runs `AdBlockPolicy.decide`; on BLOCK increments the counter and returns a
    `WebResourceResponse(kind.mimeType, "utf-8", 200, "OK", mapOf("Cache-Control" to
    "no-store"), ByteArrayInputStream(kind.body))`. Otherwise `null`.
  - `isBlockedNavigation(url: String): Boolean` (gated by `blockAds`) and
    `isBlockedPopup(url: String): Boolean` (gated by `blockPopups`) — both run
    `NavigationPolicy.decide` and increment the counter on BLOCK.
  - `isAllowlisted(host: String?)` — `RegistrableDomain.of(host) in allowedSites()`.
- **`SniffingWebViewClient`** gains `adBlocker: AdBlocker`.
  - `onPageStarted`: `adBlocker.resetForPage()`, `adBlocker.pageHost = UrlHost.of(url)`
    (existing sniffer/onNavigate calls unchanged).
  - `shouldInterceptRequest`: if `request.isForMainFrame` set `pageHost`; call
    `sniffer.onRequest(url)` first (unchanged); then `return adBlocker.intercept(url,
    request.isForMainFrame, request.requestHeaders["Accept"])`.
  - `shouldOverrideUrlLoading` (both overloads): after the `ExternalSchemePolicy` step
    returns `LetWebViewLoad`, if the scheme is http/https and (API 24+) `request.isForMainFrame`,
    `if (adBlocker.isBlockedNavigation(url)) { onNavigationBlocked(); return true }`. The
    pre-24 overload has no frame flag and treats the navigation as main-frame.
  - New callback `onNavigationBlocked: () -> Unit` (toast "Pop-up blocked").
- **`BrowserWebChromeClient`** gains `isBlockedPopup: (String) -> Boolean` (bound to
  `AdBlocker.isBlockedPopup`, gated by **Block pop-ups**). In the relay's `adopt`: `if (url !=
  null) { if (isBlockedPopup(url)) onPopupBlocked() else loadOrLaunch(host, url) }`; the relay
  is destroyed either way. `onCreateWindow` always relays and no longer reads `isUserGesture`;
  the `blockPopups` constructor parameter goes.
- **Service workers** — `MainActivity.onCreate`, guarded by `Build.VERSION.SDK_INT >= 24`:
  `ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient()
  { override fun shouldInterceptRequest(request) = adBlocker.interceptServiceWorker(request) })`
  where `interceptServiceWorker` takes the page host from the `Origin` header, else `Referer`,
  else the current `pageHost`. On API 23 service-worker fetches bypass the blocker (documented
  limitation; only DuckDuckGo among surveyed browsers covers this at all).
- **Chrome bar shield** — a focusable `TextView` `adBlockButton` between ★ and History,
  styled like the bar's `ImageButton`s (same size, `focus_ring` background), with
  `drawableStart="@drawable/ic_shield"` and the count as its text (blank when 0). One view
  carries icon, count and click. `MainActivity.toggleAdsForSite()` flips the page's
  registrable domain in `Settings.adsAllowedOn`, updates the tint (`accent` when allowed), and
  calls `webView.reload()`. `updateShieldIcon()` runs on page start and after toggle. The
  count label is bound to `adBlocker.onCountChanged`.
- **Settings** — `Settings` gains `blockAds: Boolean = true` and `adsAllowedOn: Set<String>
  = emptySet()`. `SettingsJson` writes `blockAds` as a boolean and `adsAllowedOn` as a JSON
  array of strings; missing or malformed → defaults (a non-string element is skipped).
  `SettingsView` gains a **Block ads** On/Off row (after Block pop-ups) and an **Ad block
  lists** row (last) that opens an `AlertDialog` built from `BlockList.info` (title, one line
  per source: name, entries, licence, URL; footer with generated date and total). Focus order:
  auto-open → block pop-ups → block ads → cursor speed → ad block lists.
- **Strings** — `block_ads_title` ("Block ads"), `ad_block_lists_title` ("Ad block lists"),
  `ads_allowed_here` ("Ads allowed on this site"), `ads_blocked_here` ("Ads blocked on this
  site") for the toggle toast, `ad_block_lists_generated` ("List generated %1$s · %2$d
  domains"). Existing `popup_blocked` reused for refused navigations.

### List refresh (`scripts/update-blocklist.sh` + `.github/workflows/blocklist.yml`)

- Script: bash, `curl` with a descriptive User-Agent (`mrowser-blocklist-updater
  (+https://github.com/m-salehi-v/mrowser)`), fetches both sources, strips `#`/`!` lines and
  blanks, lowercases, dedupes, sorts, writes header + body to `app/src/main/res/raw/
  blocklist.txt`. Extracts each source's `# Version:` / entry count for the header. Fails on
  an empty download or fewer than 50,000 entries (sanity floor).
- Workflow: `schedule: cron '0 6 * * 1'` (weekly) + `workflow_dispatch`. Steps: checkout,
  run the script, `git diff --quiet` → exit if unchanged, set up JDK/SDK as `build.yml` does,
  `./gradlew test` (runs the canary), create branch `chore/blocklist-YYYY-MM-DD`, commit
  `chore(adblock): refresh block list YYYY-MM-DD`, `gh pr create`. Actions pinned to SHAs;
  Dependabot already covers those pins. Note: a PR opened with `GITHUB_TOKEN` does not
  trigger `build.yml`, which is why the refresh job runs the tests itself. `permissions:
  contents: write, pull-requests: write`.
- The list is committed to git as text so each refresh is a reviewable diff.

### Licensing

Both lists are GPL-3.0. The app remains MIT: the list is a data file read by an MIT matcher,
the "mere aggregation" case in the GPL FAQ. Obligations met by:
- `THIRD-PARTY-NOTICES.md` at repo root: both attributions (name, URL, licence) and the full
  GPLv3 text; README links to it.
- The **Ad block lists** dialog in Settings shows the same attribution in-app.
Caveat recorded for provenance: OISD aggregates at least one CC BY-NC-SA source; moot for a
free sideload-only app.

## Error handling

- List file missing or unreadable → `BlockList.EMPTY`, blocker is a no-op, logged. No crash.
- Malformed lines (spaces, uppercase, `*.` prefix, trailing dot) are normalised or skipped;
  `fromLines` never throws.
- `shouldInterceptRequest` runs on worker threads: `AdBlocker` touches only `@Volatile`
  fields, an `AtomicInteger`, and immutable `Settings`/`BlockList` snapshots. Counter updates
  reach the UI via a `Handler.post`.
- A blocked main-frame navigation is cancelled, never served an empty body; the user sees
  the page they were on plus the "Pop-up blocked" toast.
- Relay WebView is always destroyed via `post` after the decision, as today.

## Testing

Pure, under `app/src/test/kotlin/net/mrowser/adblock/` (`web/` for the two helpers):
- `UrlHostTest` — scheme/userinfo/port/path stripping, IPv6 literal, relative/blank → null.
- `RegistrableDomainTest` — `www.example.com` → `example.com`; `a.b.example.co.uk` →
  `example.co.uk`; IP and single label unchanged.
- `BlockListTest` — suffix walk hits `sub.ads.example` for entry `ads.example`; does not hit
  the bare TLD; exact and deeper matches; header parse into `Info`; blank/comment/whitespace
  lines skipped; `EMPTY` contains nothing; dedupe.
- `AdBlockPolicyTest` — one test per branch in order: disabled, allowlisted, main frame,
  each media kind, first-party (incl. `cdn.example.com` vs page `www.example.com`), listed
  third-party → BLOCK, unlisted → ALLOW.
- `NavigationPolicyTest` — listed → BLOCK, first-party listed → BLOCK, disabled/allowlisted
  → LOAD, non-http scheme → LOAD.
- `BlockedResponseTest` — kind by Accept header, by extension, fallback; bodies non-null;
  GIF header bytes.
- `BlockListCanaryTest` — loads `app/src/main/res/raw/blocklist.txt` relative to the test
  working directory (Gradle runs tests with `app/` as cwd; fall back to walking up to find it).
  Asserts ≥ 50,000 entries; presence of `popads.net`, `propellerads.com`, `exoclick.com`,
  `adsterra.com`, `hilltopads.net`, `clickadu.com`, `richads.com`, `zeropark.com`,
  `trafficstars.com`, `popcash.net`, `juicyads.com`, `trafficjunky.net`; absence of
  `cloudfront.net`, `akamaized.net`, `akamaihd.net`, `fastly.net`, `cdn77.org`, `b-cdn.net`,
  `jwpcdn.com`, `cloudflarestream.com`, `vimeocdn.com`, `youtube.com`, `googlevideo.com`,
  `google.com`, `github.com`.
- Deleted: `PopupPolicyTest`. Updated: `SettingsJsonTest` (round-trip of the two new
  fields, malformed array element skipped, missing → defaults), `MediaUrlClassifierTest`
  unchanged behaviour after `hostOf` delegates to `UrlHost`.

Device checks on the TV (recorded in the plan's final task, not automatable here):
1. A page that loads a listed ad script: counter increments; page renders; no ad.
2. A known pop-under site: pressing OK on the player once no longer replaces the page; toast
   "Pop-up blocked"; counter increments; second press plays.
3. An HLS site: stream still detected and handed off with **Block ads** on.
4. Shield toggle: ads allowed → page reloads with ads, tint accent, survives app restart;
   toggle back → blocked again.
5. **Block ads** Off in Settings → no interception, counter stays 0.
6. Ad block lists dialog shows both sources, date, count.
7. A site that registers a service worker (e.g. a PWA news site) still shows blocks on
   API 24+.

## Follow-ups (not in this design)

- **Cosmetic hiding**: inject a `<style>` with a curated generic-hide subset via a
  `MutationObserver` on page load — empty ad slots and anti-adblock overlays are what users
  notice next (Fulguris #577/#731).
- **Cross-site pop-up chip**: refuse unlisted cross-site window targets too, with a 10 s
  "OK to open" chip. Catches unlisted ad hosts at the cost of one press on legit external
  links.
- **Tab-under guard**: Chromium's never-launched `TabUnderNavigationThrottle` heuristic —
  after any window open, refuse a gestureless cross-site opener navigation until the next key
  press.
- **Hit-test link allowlist** (uBO `maybeGoodPopup`): snapshot `WebView.getHitTestResult()`
  on each synthesized click; allow a window whose destination equals the clicked href. Needs
  a TV check that mouse-sourced synthetic input refreshes the hit-test data.
- Point `MediaUrlClassifier.isAdHost` at `BlockList` so ad manifests (IMA pre-rolls) are
  skipped using the same list.
- Runtime list refresh (ETag, at most daily, never on launch) if release cadence proves too
  slow.
