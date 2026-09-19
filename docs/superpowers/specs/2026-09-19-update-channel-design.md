# In-app update channel — design

**Date:** 2026-09-19
**Status:** approved, pending implementation plan

## Origin

mrowser ships as a sideloaded APK from GitHub Releases. A user on v1.2.1 has no way to learn
that v1.3.0 exists short of visiting the repository from another device. The app never tells
them, so in practice most installs stay on whatever version they first sideloaded.

## Goal

Once a day, ask GitHub whether a newer stable release exists. If one does, show a quiet line at
the bottom of the home screen. Opening it shows the version, the release notes and the size, and
offers to download the APK to the device's public `Downloads/` folder, from where the user
installs it themselves.

## Decisions

- **No `REQUEST_INSTALL_PACKAGES`.** mrowser will not install the APK itself. Android offers no
  other route — without that permission the system blocks the calling app as an unknown source —
  so the flow deliberately ends at "downloaded", not "installed". This keeps the door open to
  both F-Droid (which builds from source and objects to self-updaters on principle) and
  IzzyOnDroid, and keeps the manifest close to the INTERNET-only shape it has today. The nearest
  comparable app, TV Bro, takes `REQUEST_INSTALL_PACKAGES` plus read/write external storage, a
  foreground service and `QUERY_ALL_PACKAGES`, and is in neither catalogue.
- **Android's system `DownloadManager`**, not a bespoke downloader. No foreground service, no
  notification permission, no new dependency, and it is the same service a general download
  manager would later be built on.
- **Public `Downloads/`**, not app-private storage. A file under `Android/data/` is hidden from
  file managers by scoped storage on API 30+, which on a TV means unreachable. The cost is
  `WRITE_EXTERNAL_STORAGE` on API 23–28 only, declared with `android:maxSdkVersion="28"` so it
  vanishes from the manifest on modern devices, and requested only when the user taps Download.
- **Stable releases only.** The check hits `/releases/latest`, which GitHub defines as the newest
  non-draft, non-prerelease. Prereleases stay invisible; they are the project's staging step.
- **The banner persists until the newer version is installed.** No dismiss, no skip-this-version.
  It clears itself, because it is drawn from a comparison against the running version rather than
  from a stored "seen" flag.
- **An Obtainium handoff** is offered alongside the download. It is the only path that ends the
  update problem permanently, and the README already recommends it.
- **Failure is silent.** No network, blocked DNS, rate limit, malformed JSON — all log and return.
  A user in a region where `api.github.com` is unreachable sees exactly the app they see today.

## Non-goals

- Installing the APK. See above.
- A general download manager (downloading arbitrary links while browsing). Designed separately;
  this spec only lands the reusable `DownloadManager` wrapper it would build on.
- A beta channel, skip-this-version, or a manual "Check now" control.
- Delta updates, background checks while the app is closed, or any scheduled job.

## Architecture

New domain `net.mrowser.update`, following the repository's pure/glue split.

### Pure (no Android imports, unit-tested)

**`update/Release.kt`** — one immutable record:

```kotlin
data class Release(
    val tag: String,          // "v1.4.0"
    val version: String,      // "1.4.0" — tag with any leading v stripped
    val notes: String,        // GitHub markdown, left unrendered
    val apkUrl: String,       // asset browser_download_url
    val apkSizeBytes: Long,
    val htmlUrl: String       // release page, shown when the download path is unavailable
)
```

**`update/VersionCompare.kt`** — `compare(a: String, b: String): Int?`

Strips a leading `v`/`V`, splits on `.`, compares segments pairwise as integers with missing
segments treated as `0`, so `1.4` equals `1.4.0` and `1.10.0` is greater than `1.9.0`. Any
non-numeric segment on either side returns `null` — unknown, never a false positive. Callers
treat `null` as "no update".

**`update/ReleaseJson.kt`** — `parse(body: String): Release?`

Reads `tag_name`, `name`, `body`, `draft`, `prerelease`, `html_url` and the `assets` array.
Returns `null` when the body is unparseable, when `draft` or `prerelease` is true (the endpoint
should never return either — if it does, the response is not trusted), when `tag_name` or
`html_url` is missing, or when the asset list does not contain **exactly one** entry whose name
ends in `.apk` (the release workflow attaches exactly one, `app-release.apk`; zero or several
means something changed and we decline rather than guess).

**`update/UpdateState.kt` + `update/UpdateJson.kt`** — persisted cache:

```kotlin
data class UpdateState(
    val lastCheckedAt: Long = 0L,
    val etag: String? = null,
    val release: Release? = null
)
```

`UpdateJson.toJson` / `fromJson` mirror `SettingsJson`: missing or unparseable fields fall back to
defaults, a malformed file yields `UpdateState()`.

**`update/UpdateCheckPolicy.kt`** — the two decisions:

```kotlin
fun shouldCheck(nowMs: Long, lastCheckedAtMs: Long): Boolean
fun bannerFor(installedVersion: String, cached: Release?): Release?
```

`shouldCheck` is true when at least `CHECK_INTERVAL_MS` (24h) has elapsed, and also when
`lastCheckedAtMs` lies in the future — a TV whose clock jumped backwards would otherwise never
check again.

`bannerFor` returns the cached release only when `VersionCompare.compare(cached.version,
installedVersion)` is greater than zero. This is what makes the banner self-clearing: once the
user installs 1.4.0 the cached release is no longer newer and the line disappears with no state to
reset.

**`update/DownloadUrl.kt`** — `isAllowed(url: String): Boolean`

True only for `https` URLs whose host is `github.com` or ends in `.githubusercontent.com`. The
APK URL comes from a network response, and `DownloadManager` will fetch whatever it is handed, so
the destination is vetted before enqueuing. Redirects after that point belong to the platform and
stay over HTTPS.

### Android glue

**`update/UpdateStateRepository.kt` / `update/JsonUpdateStore.kt`** — `filesDir/update.json`,
same shape as `JsonSettingsStore`: an in-memory snapshot, serialize on the caller, write on a
single-thread executor, log failures.

**`update/ReleaseFetcher.kt`** — modelled on `player/SubtitleFetcher`:

```
GET https://api.github.com/repos/m-salehi-v/mrowser/releases/latest
Accept: application/vnd.github+json
User-Agent: mrowser/<installed versionName>
If-None-Match: <cached etag, when present>
```

The `User-Agent` is mandatory — GitHub answers 403 without one. `If-None-Match` turns a repeat
check into a 304 that costs no rate-limit quota, which matters because the unauthenticated limit
is 60 requests per hour **per IP** and carrier-grade NAT pools many users behind one address.
Connect and read timeouts 15s, redirects followed, response body read capped at 256 KB. Returns a
sealed result: `NotModified`, `Body(text, etag)`, or `Failed`.

**`update/UpdateController.kt`** — orchestration, no UI:

```kotlin
class UpdateController(
    private val store: UpdateStateRepository,
    private val installedVersion: String,
    private val io: Executor,
    private val main: (Runnable) -> Unit,
    private val now: () -> Long
) {
    fun cachedBanner(): Release?
    fun checkIfDue(onResult: (Release?) -> Unit)
}
```

`cachedBanner()` is synchronous and touches no network — it is `UpdateCheckPolicy.bannerFor` over
the stored state, so the line can be drawn on the first frame. `checkIfDue` returns immediately
when `shouldCheck` is false; otherwise it fetches on `io`, writes the new state, and delivers the
result through `main`.

**`update/ApkDownloader.kt`** — the reusable core:

```kotlin
fun enqueue(activity: Activity, release: Release): Long?
fun progress(activity: Activity, id: Long): Progress
```

```kotlin
sealed interface Progress {
    data class Running(val bytesSoFar: Long, val totalBytes: Long) : Progress
    object Done : Progress
    object Failed : Progress
}
```

`totalBytes` is `-1` while `DownloadManager` does not yet know it; the dialog then shows bytes
so far alone. `enqueue` refuses when `DownloadUrl.isAllowed` is false or `getSystemService(DOWNLOAD_SERVICE)`
returns null (stripped TV firmware), and on API ≤ 28 when `WRITE_EXTERNAL_STORAGE` is not granted
— the caller requests it first. The request sets the public destination
`Downloads/mrowser-<version>.apk`, MIME `application/vnd.android.package-archive`, a visible
completion notification, title "mrowser <version>", and allows metered networks. `progress`
queries `DownloadManager` for status plus bytes-so-far / total.

**`update/UpdateDialog.kt`** — framework `AlertDialog`, in the shape of `home/FavoriteDialog`.
It never touches the permission system itself; `show` takes a provider lambda so the dialog stays
ignorant of request codes:

```kotlin
fun show(
    activity: Activity,
    release: Release,
    ensureStoragePermission: (onResult: (granted: Boolean) -> Unit) -> Unit,
    onObtainium: () -> Unit
)
```

`MainActivity` supplies `ensureStoragePermission`: on API 29+ it calls back `true` immediately; on
API 23–28 it checks `checkSelfPermission`, and if not granted stores the callback, calls the
framework `Activity.requestPermissions` (API 23+, no AndroidX) with a private request code, and
fires the stored callback from `onRequestPermissionsResult`. One pending callback at a time; a
second request while one is outstanding replaces it. The dialog's contents:

- Title "Update to 1.4.0"
- Release notes as plain text in a `ScrollView`, truncated at 2000 characters, then the size
- Positive **Download** · Neutral **Automatic updates** · Negative **Close**

On Download it calls `ensureStoragePermission`; on `true` it enqueues and switches its body to a
progress line polled once a second on the existing `uiHandler` — polling rather than an `ACTION_DOWNLOAD_COMPLETE` receiver, which on
targetSdk 34 would need `RECEIVER_EXPORTED` and lifecycle registration for one message. The poll
is cancelled in the dismiss listener; `DownloadManager` carries on regardless and posts its own
notification.

On completion the body becomes the handoff instruction:

> **mrowser 1.4.0 saved**
> `Downloads/mrowser-1.4.0.apk`
> Open it with your file manager to install. Your settings and favorites are kept.

The last line is load-bearing — the new APK is signed with the same key, so this is an in-place
update, and users who have been burned by uninstall-and-reinstall need telling.

When the permission is denied, `DownloadManager` is missing, or the download fails, the body
becomes the release page URL as selectable text plus the Obtainium route, stating plainly that
mrowser cannot save the file. No second ask.

**Obtainium handoff.** The neutral button fires `obtainium://add/https://github.com/m-salehi-v/mrowser`
through the existing `web/ExternalIntentLauncher`, which already adds `BROWSABLE`, nulls the
component and selector, and strips URI-permission flags. Nothing resolves → a short dialog naming
Obtainium and offering to open its GitHub page in the WebView.

### UI

`res/layout/home_view.xml` gains a **third child of the root vertical `LinearLayout`, after the
favorites `ScrollView`**, so it sits on the bottom edge inside the existing 32dp padding:

```xml
<Button
    android:id="@+id/homeUpdateButton"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="start"
    android:layout_marginTop="16dp"
    android:background="@drawable/focus_ring"
    android:paddingStart="12dp"
    android:paddingEnd="12dp"
    android:text="@string/update_available"
    android:textAllCaps="false"
    android:textColor="@color/accent"
    android:textSize="14sp"
    android:visibility="gone" />
```

Deliberately minimal: accent *text* on the transparent `focus_ring` background the header buttons
already use, not a filled accent block. It reads as a quiet line and still shows a focus ring on
D-pad. Label "Update available — 1.4.0", version filled at bind time. `gone` when there is no
update, so the favorites grid keeps the full height it has today. The label is a format resource,
`<string name="update_available">Update available \u2014 %1$s</string>`, filled with
`release.version`.

Focus falls out for free — it is last in the vertical order, so D-pad down from the bottom row of
favorites reaches it, and the header's existing order (URL pill → History → Settings) is untouched.

`HomeView.bind` takes one more callback, `onUpdate: (Release) -> Unit`; `HomeView.showUpdate(release: Release?)`
sets the label and visibility, hiding on `null`. All strings go in `res/values/strings.xml`.

### Wiring

`MainActivity.onCreate`, alongside the existing repositories:

1. Build `JsonUpdateStore(File(filesDir, "update.json"))` and `UpdateController`, with
   `installedVersion` from `packageManager.getPackageInfo(packageName, 0).versionName` — no
   `buildConfig` feature to enable.
2. Pass `onUpdate = { UpdateDialog.show(this, it) }` into `homeView.bind`.
3. `homeView.showUpdate(updateController.cachedBanner())` — instant, from cache.
4. `updateController.checkIfDue { homeView.showUpdate(it) }` — one call per process launch, not
   per home show, so bouncing between page and home does not re-fire it. It runs whether the app
   opened on home or via a `VIEW` intent; a link launch simply warms the cache.

### Manifest delta

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

Nothing else. No `REQUEST_INSTALL_PACKAGES`, no service, no receiver, no new Gradle dependency —
`HttpURLConnection`, `org.json` and `DownloadManager` are all platform.

## Error handling

| Condition | Behaviour |
|---|---|
| No network, DNS blocked, timeout, 403, 404 | Log at `w`, return. No UI. |
| 304 Not Modified | Bump `lastCheckedAt`, keep the cached release. |
| Malformed or unexpected JSON | `ReleaseJson.parse` returns null; treated as no update. |
| Release has zero or several `.apk` assets | Treated as no update. |
| Version strings unparseable | `VersionCompare` returns null; treated as no update. |
| `update.json` corrupt | `UpdateJson.fromJson` returns `UpdateState()`; next launch re-checks. |
| APK URL not on an allowed host | `enqueue` refuses; dialog falls back to the release URL. |
| `DownloadManager` unavailable | Same fallback. |
| `WRITE_EXTERNAL_STORAGE` denied | Same fallback, stated plainly. No second ask. |
| Download fails mid-flight | Poll reports it; dialog offers to try again. |

## Security

mrowser never installs, so no signature check is ours to make — the system verifies the downloaded
APK against mrowser's existing signature at install time and refuses a mismatch, which is a
stronger guarantee than anything the app could enforce itself. The APK URL is taken only from the
release asset list and vetted by `DownloadUrl.isAllowed` before it reaches `DownloadManager`.
Release notes are network-supplied text and are rendered into a `TextView` as plain text, never
into the WebView.

## Testing

JVM unit tests in `app/src/test/kotlin/net/mrowser/update/`, one class per pure module. The
Android glue (`ReleaseFetcher`, `ApkDownloader`, `UpdateController`, `UpdateDialog`,
`JsonUpdateStore`) stays thin and untested, as `SubtitleFetcher` and `JsonSettingsStore` already
are.

- **`VersionCompareTest`** — `v1.4.0` > `1.3.0`; equal versions; `1.10.0` > `1.9.0` (not string
  order); `v` prefix on one side, both sides, neither; `1.4` equals `1.4.0`; `1.4.0-rc1` and other
  garbage return `null`; empty string returns `null`.
- **`ReleaseJsonTest`** — a real `/releases/latest` payload parses to the expected `Release`; no
  `.apk` asset → null; two `.apk` assets → null; `prerelease: true` → null; `draft: true` → null;
  missing `tag_name` → null; missing `html_url` → null; truncated JSON → null; the `v` prefix is
  stripped into `version` but kept in `tag`.
- **`UpdateCheckPolicyTest`** — `shouldCheck` at 0 elapsed, 23h59m, 24h exactly, 24h01m, and with
  `lastCheckedAt` in the future; `bannerFor` with no cache, an older cached version, an equal one,
  a newer one, and an unparseable one.
- **`UpdateJsonTest`** — round-trip with and without a cached release; missing fields fall back to
  defaults; unparseable input yields `UpdateState()`; a null `etag` survives the round trip.
- **`DownloadUrlTest`** — `github.com` and `objects.githubusercontent.com` over https allowed;
  http refused; a lookalike host (`github.com.evil.test`) refused; a bare path refused.

## Follow-ups (not in this design)

- **General download manager** (issue backlog): downloading arbitrary links while browsing, a
  downloads overlay, and history of downloads. `ApkDownloader` is the wrapper it builds on;
  expect it to generalise into a `Downloader` taking a URL, filename and MIME type.
- **In-app install**, if the store position ever settles in a way that makes
  `REQUEST_INSTALL_PACKAGES` acceptable. It would slot in as one extra button on the completion
  state and change nothing else in this design.
- **Beta channel** and **skip this version**, both deliberately deferred.
- CLAUDE.md states the repository is private; it is public. Correct it when this lands, since the
  update check depends on unauthenticated access.
