# Ad Blocking and Pop-ups — Research Notes

**Date:** 2026-09-18. Companion to `2026-09-18-adblock-and-popups-design.md`. Findings from
reading the actual source of Chromium/AOSP, gecko-dev, uBlock Origin, and each surveyed browser
on that date. "Could not verify" is stated where it applies.

## 1. Why the gesture-only pop-up blocker cannot work

### Chromium user activation
- Transient activation lifespan is **5 s** (`third_party/blink/public/common/frame/user_activation_state.h`,
  `kActivationLifespan = base::Seconds(5)`).
- Activation is a property of the **frame**, set during dispatch of any trusted activating
  input event (`mousedown`/`pointerdown`/`pointerup`/`keydown`…). It does not depend on the
  event target. A `document`-level click listener or a full-page overlay gets a valid
  activation. An activation in a child frame activates **all ancestors**, and Chromium extends
  it to same-origin frames. So a click inside an ad iframe activates the top document.
- `window.open` **consumes** the transient activation (`RenderFrameHostImpl::CreateNewWindow`
  → `kConsumeTransientActivation`; HTML spec PR whatwg/html#10547). One click = one open.
  That is why ad scripts open the ad and navigate the *current* page instead (tab-under), or
  delay.

### Android WebView order of checks (`RenderFrameHostImpl::CreateNewWindow`)
sandbox → `AwContentBrowserClient::CanCreateWindow` → consume activation → multiple-windows
reuse check → `AwWebContentsDelegate::AddNewContents` → Java `onCreateWindow`.

- `android_webview/browser/aw_content_browser_client.cc`, `CanCreateWindow`:
  `return (settings && settings->GetJavaScriptCanOpenWindowsAutomatically()) || user_gesture;`
  **With `javaScriptCanOpenWindowsAutomatically=false`, `onCreateWindow` only ever sees
  gesture-backed opens.** mrowser's `Block` branch was unreachable.
- Java `addNewContents(boolean isDialog, boolean isUserGesture)` — the **target URL is not
  passed**. The app learns it only after handing the transport a WebView, in that WebView's
  `shouldOverrideUrlLoading`/`onPageStarted` (the relay trick; Jelly and DuckDuckGo do the
  same). Only one pending popup at a time.
- `isDialog` = Blink classified the open as a popup from its window-feature string; ad
  scripts choose it, useless as a signal.
- `setSupportMultipleWindows(false)`: Chromium's `kReuse` path navigates the opener's top
  frame in place, no `onCreateWindow` at all (what Privacy Browser and "pop-ups off"
  Fulguris/Lightning actually do, and why their users see ads replace the page).
- AOSP `WebChromeClient.onCreateWindow` javadoc: "There is no trustworthy way to tell which
  page requested the new window: the request might originate from a third-party iframe."
- Docs: https://source.chromium.org/chromium/chromium/src/+/main:android_webview/docs/how-does-on-create-window-work.md

### Chrome's own pop-under work
- `chrome/browser/ui/blocked_content/tab_under_navigation_throttle.cc` heuristic: opener
  navigates cross-site, renderer-initiated, no gesture, while in background, after opening a
  popup since the last gesture. Current `main`: "We unconditionally proceed. There used to be
  a tab-under blocking experiment, but it never launched." Only UMA remains.
- Chrome 65's "Pop-ups and redirects" setting is **framebust** blocking (cross-origin iframe
  navigating top without gesture), not tab-unders.
- `components/blocked_content/popup_blocker.cc`: no gesture → block; else allow, unless Safe
  Browsing's ABUSIVE list flags the page, in which case even gestured popups are blocked.
  Chrome's answer to gesture laundering is a **curated blocklist**, plus an infobar with
  per-site allow.

### Firefox
Event/gesture based too (`dom.popup_allowed_events`, `dom.disable_open_during_load`,
`dom.user_activation.transient.timeout = 5000`); one popup per activation (consumes
activation after an allowed open). A document-level click handler passes it.

### uBlock Origin (the model this design follows)
- No gesture heuristic at all. `src/js/tab.js` hooks new-tab creation, runs the static filter
  engine with type `popup` on the **new tab's URL** with the opener's origin as context;
  match → close the tab, badge count. `$popunder` evaluates the **opener's** URL after it
  navigates post-open and closes the opener.
- Legit-click exemption (`contentscript.js` `maybeGoodPopup`): on `mousedown` the content
  script reports `ev.target.closest('a[href]').href`; if the new tab's URL equals it, the popup
  test is skipped. A URL match, not a gesture.
- Per-site `no-popups:` switch blocks all popups on a site regardless of filters.
- EasyList carries dedicated pop-up sections (`easylist_adservers_popup.txt`,
  `easylist_thirdparty_popup.txt`, `easylist_specific_block_popup.txt`) which HaGeZi's Pop-Up
  Ads list is built from.

### Signals available at `onCreateWindow`
| Signal | Verdict |
|---|---|
| `isUserGesture` | Always true in our config. |
| `isDialog` | Chosen by the script. |
| Target URL | Only after accepting via a relay WebView — accept, peek, decide, drop is valid. |
| Target origin vs page | Derivable after peek; FP on legit cross-site `_blank`, FN on first-party `/go/` redirectors. |
| `WebView.getHitTestResult()` | Refreshed only on DOM `touchstart` and focus change; field is literally `mPossiblyStaleHitTestData`. Could not verify whether mrowser's synthesized `MotionEvent`s count as touch. |
| Timing click→open | Ad opens are synchronous inside the click, same as real links. Useless. |
| Opener navigates cross-site without gesture right after an open | Implementable (Chromium's throttle heuristic) — follow-up. |

### How surveyed WebView browsers handle `onCreateWindow`
| Browser | `jsCanOpenWindowsAutomatically` | Decision | Surfacing |
|---|---|---|---|
| TV Bro | false | Per-host level 0–3; default 2 = block dialogs + non-gesture; 3 = block all | Counter badge + toast + per-host dialog |
| Fulguris / Lightning | true | Always new tab; "pop-ups off" = Chromium reuse path | none |
| DuckDuckGo | false | gesture → new tab, else `return false` | silent |
| Jelly | not set | non-gesture refused; else relay WebView grabs URL → new tab | silent |
| Yuzu | pref | always new tab | none |
| Privacy Browser | not set | no override → reuse path, ads replace the page | — |

None applies a cross-origin, hit-test, rate-limit, iframe or timing check.

## 2. How WebView browsers implement ad blocking

| | Hook | List / shipping | Engine | Subdomains | Main-frame hit | Cosmetic | Update |
|---|---|---|---|---|---|---|---|
| TV Bro | `shouldInterceptRequest` | EasyList, downloaded, native-serialised cache | Brave C++ `ad-block` via JNI (MPL-2.0 fork) | in engine | empty 403 → blank page | no | 30 days |
| Lightning | `shouldInterceptRequest` | hosts file 1.77 MB bundled | Bloom filter + Room | exact host (+`www.` strip) | blank | no | manual |
| Fulguris | `shouldInterceptRequest` | EasyList/EasyPrivacy downloaded, binary cache | vendored Yuzu tag-hash engine (MPL-2.0 files) | `\|\|` boundary; `$domain=` walks labels; 3p = eTLD+1 via OkHttp PSL | HTML "blocked" page with reason | present, disabled | `! Expires`, If-Modified-Since |
| Yuzu | `shouldInterceptRequest` | 14 KB bundled + optional EasyList | tag-hash engine (Apache-2.0, dead since 2021) | `$domain=` exact (bug) | HTML page | JS `remove()` on page finish | JobScheduler |
| Privacy Browser | `shouldInterceptRequest` | 5.8 MB ABP bundled, no download | linear scan | via truncated URL | skipped by URL equality | no | releases only |
| DuckDuckGo | `shouldInterceptRequest` **+ `ServiceWorkerClient`** | own tds.json 1.57 MB bundled + Room | domain map, label walk, surrogates | label walk; same-entity | `!isForMainFrame` | no | 12 h worker, ETag |
| Jelly | — | none | — | — | — | — | — |

Universal: everyone blocks in `shouldInterceptRequest`; nobody blocks via
`shouldOverrideUrlLoading`; everyone but DDG returns empty `text/plain`; only DDG covers
service workers. No dependency-free JVM ABP engine exists as a library; the closest is
Yuzu's `core/` + `filter/` packages (~1.5–2k lines, uses `android.net.Uri`).

Blocked-response detail: Fulguris ships uBO's redirect resources (image → `1x1.gif`,
subdocument → `noop.html`, script → `noop.js`, media → `noop-0.1s.mp3`) because an empty
`text/plain` for an `<img>` fires `onerror` handlers and breaks iframe layouts.

### Lessons applied in the design
1. Never blank the main frame — gate navigations instead of serving an empty body.
2. Typed stand-ins for blocked subresources.
3. `||host^` semantics = domain-label boundary + subdomains → suffix walk.
4. Third-party needs a registrable domain, not host equality (`static.example.com` vs
   `www.example.com`).
5. Page URL is not `webView.url` off the UI thread — keep a volatile host set from the
   main-frame request.
6. Do not spin on the IO thread waiting for the list; let requests pass until loaded.
7. Cover service workers (API 24+) or blocking silently stops on PWA-style sites.
8. Hosts-only misses same-origin ads (YouTube/Tubi) — set expectations in the README.
9. Per-site escape hatch + visible counter (TV Bro, Lightning, Fulguris, Privacy Browser all
   have one or both).
10. Cosmetic hiding is what users notice next (Fulguris #577/#731) — follow-up.

### Issues worth knowing
- TV Bro #174: counter increments but pop-ups/porn ads still appear on a TV.
- TV Bro #251/#260: YouTube/Tubi ads unblockable by URL rules.
- Lightning #354: Facebook images broken by hosts list; #838 YouTube.
- Fulguris #639: first enable without lists → "adblock isn't working"; #446 crash on
  malformed regex filter; #560 render-process crash attributed to content control.
- StevenBlack #2077: Amazon Prime Video CDN host blocked; #2515 cdn.jsdelivr.net.

## 3. Filter lists (measured 2026-09-18)

| List | Format | Licence | Entries | Bytes / gz | Cadence | Streaming pop-under coverage | OK for MIT app? |
|---|---|---|---|---|---|---|---|
| EasyList | ABP (48k pure `\|\|host^`) | GPLv3 **or** CC BY-SA 3.0 | 68,203 | 2.16 MB / 756 KB | several/day | all | yes, but must compile it ourselves |
| justdomains EasyList | domains | as EasyList | 22,066 | 364 KB / 155 KB | **dead since 2022-10** | partial | stale |
| AdGuard DNS filter | ABP-DNS | GPLv3 | 180,487 | 4.38 MB / 1.48 MB | daily | all but monetag | aggregation |
| AdGuard DNS Popup Hosts | `$dnsrewrite` | GPLv3 | 1,067 | 59 KB / 8.7 KB | daily | landing domains only | aggregation |
| StevenBlack | hosts (exact) | MIT label, **NC sources inside** | 80,169 | 2.41 MB / 601 KB | 2×/week | misses hilltopads.com, monetag, richads, zeropark | no |
| Peter Lowe | hosts/domains | none stated; site licence non-commercial | 3,546 | 95 KB / 24 KB | weekly | weak | unclear |
| **OISD small** | domainswild2 | GPLv3 | 56,157 | 1.09 MB / 474 KB | daily-ish | misses adsterra, monetag, clickadu, richads, zeropark, trafficstars | aggregation |
| OISD big | domainswild2 | GPLv3 | 247,140 | 4.96 MB / 1.87 MB | ≥ daily | all but monetag/richads/zeropark | aggregation |
| HaGeZi Light / Normal / Pro | onlydomains | GPLv3 | 39,661 / 199,045 / 229,508 | 284 KB / 1.43 MB / 1.66 MB gz | daily | Pro: all but monetag.com | aggregation |
| **HaGeZi Pop-Up Ads** | onlydomains | GPLv3 | 50,556 | 869 KB / 367 KB | daily | all but monetag.com | aggregation |
| 1Hosts Lite | domains.wildcards | MPL-2.0 | 102,259 | 2.04 MB / 779 KB | ~weekly | all but hilltopads.net | yes, opaque provenance |
| anudeepND whitelist | allow | MIT | 191 | 3.7 KB | dead since 2021 | — | — |

Chosen: **OISD small ∪ HaGeZi Pop-Up Ads**. Union 92,643 domains, 729 KB gzipped, no video
CDN apex (cloudfront.net, akamaized.net, fastly.net, cdn77.org, b-cdn.net, jwpcdn.com,
cloudflarestream.com, vimeocdn.com) present. Both lists do contain specific ad-tenant
subdomains of those CDNs (e.g. `*.cloudfront.net` entries), which the suffix-walk matcher
would match for deeper sub-subdomains only; the design's media-kind and first-party guards
cover the fatal case.

URLs:
- OISD small: `https://small.oisd.nl/domainswild2` — https://oisd.nl, licence
  https://github.com/sjhgvr/oisd/blob/main/LICENSE, excludes https://oisd.nl/excludes
- HaGeZi Pop-Up Ads: `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/popupads-onlydomains.txt`
  (mirror `https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/popupads-onlydomains.txt`)
  — https://github.com/hagezi/dns-blocklists, sources
  https://raw.githubusercontent.com/hagezi/dns-blocklists/main/sources.md

### Licensing
- GPLv3 list read as data by an MIT matcher = "mere aggregation" (GPL FAQ
  https://www.gnu.org/licenses/gpl-faq.html#MereAggregation). App stays MIT; ship the GPLv3
  text and preserve attribution. Every DNS blocker that bundles these lists is itself
  copyleft, so there is little precedent for a permissive app beyond the aggregation reading.
- EasyList under CC BY-SA 3.0 would be the cleanest story (unmodified file in an APK is a
  Collection, §1) but its useful pop-up sections are already inside HaGeZi's list.
- OISD includes at least one CC BY-NC-SA source (d3host); HaGeZi's README says to check
  third-party source licences. Moot for a free sideload-only app; recorded for provenance.

### Matching (how DNS blockers do it)
- Plain hosts entries (Pi-hole gravity, DNS66 `HashSet`, AdAway SQLite) are exact-host;
  AdGuard `||example.com^` and the wildcard exports mean domain + subdomains. OISD dropped
  exact-host formats in 2024 because subdomain semantics are needed.
- personalDNSfilter stores hosts as 64-bit hashes in a packed sorted structure and walks
  parent domains by stripping the leftmost label — the scheme the design adopts
  (https://github.com/IngoZenz/personaldnsfilter, `dnsfilter/BlockedHosts.java`).
- DuckDuckGo `findCompiledTracker` does the same label walk over a map.
- `HashSet<String>` costs ~40–60 B/entry on ART (≈ 5–6 MB for 93k); sorted `LongArray` of
  hashes ≈ 0.75 MB.

### Update strategy
- 2022 EasyList hosting crisis: forked Android browsers fetched on every launch, 10–20×
  traffic, host threatened ToS action. AdGuard's write-up: bundle or self-host, honour
  `Expires`, never fetch on startup (https://adguard.com/en/blog/easylist-filter-problem-help.html).
- HaGeZi points at jsDelivr/GitHub raw, `Expires: 8 hours`; OISD `Expires: 1 hours`, no
  hotlink rule found (could not verify one exists).
- Chosen: weekly CI job fetches with a descriptive User-Agent, runs the canary test, opens a
  PR. No runtime fetch.

## 4. Scratch material
The three research agents left downloaded lists and browser sources in the session scratchpad
(`.../scratchpad/lists/`, `.../scratchpad/{tvbro,fulguris,yuzu,lightning,ddg,pb}/`, `cov.sh`
coverage probe). Session-local; regenerate with `scripts/update-blocklist.sh` once it exists.
