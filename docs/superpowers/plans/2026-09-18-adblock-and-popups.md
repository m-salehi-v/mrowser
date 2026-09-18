# Ad Blocking and Pop-up Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Block requests and navigations to known ad hosts (issue #38): a bundled domain list, a pure suffix-walk matcher, interception in `shouldInterceptRequest`, and a navigation gate that refuses `window.open` targets and in-page navigations to listed hosts without ever blanking the page.

**Architecture:** New package `net.mrowser.adblock` holds pure decision objects (`BlockList`, `AdBlockPolicy`, `NavigationPolicy`, `BlockedResponse`) and one Android class, `AdBlocker`, that owns the loaded list, the per-page counter and the live settings providers. `SniffingWebViewClient` and `BrowserWebChromeClient` call into `AdBlocker`; `MainActivity` wires it, the chrome-bar shield button and two new Settings rows. Two URL helpers, `UrlHost` and `RegistrableDomain`, live in `web/` next to `UrlNormalizer` so `stream/` and `adblock/` do not depend on each other in a cycle.

**Tech Stack:** Kotlin, framework `Activity` + XML layouts (no AndroidX beyond Media3), `org.json`, JUnit 4, bash + curl for the list refresh, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-18-adblock-and-popups-design.md` (read it first; the research notes in `2026-09-18-adblock-and-popups-research.md` explain every "why").

**Refinements vs spec (intentional):**
1. **`PopupPolicy` is deleted, not simplified.** Its only reachable branch would be a constant. Today "Block pop-ups: Off" calls `super.onCreateWindow`, which returns `false` and silently drops every window, which is the opposite of what the setting's doc comment promises. In this plan Off means "open the clicked window in the current WebView without the list check", On means "refuse it when its destination is on the list". The relay is used in both cases.
2. **`UrlHost` and `RegistrableDomain` live in `net.mrowser.web`**, not `adblock/`, so that `MediaUrlClassifier` (in `stream/`) can delegate to `UrlHost` without `stream/` importing `adblock/` while `adblock/` imports `stream/`.
3. **The counter resets on the main-frame request** as well as on `onPageStarted`, because the main-frame request is the earliest signal and sub-resource requests can start before `onPageStarted` fires.

**Compile-green strategy:** every task ends with `./gradlew test` (or `assembleDebug` for Android-only tasks) passing. New constructor parameters are added in the same task as their call sites, so nothing is left dangling between tasks.

## Global Constraints

- No AndroidX/Compose beyond Media3. Activities extend framework `Activity`; UI is XML in `res/layout`.
- Pure modules are plain Kotlin `object`s/classes with **no Android imports**, under `app/src/main/kotlin/net/mrowser/...`, with tests under `app/src/test/kotlin/net/mrowser/...`.
- `minSdk 23`, `compileSdk 36`. `ServiceWorkerController` is API 24+ and must be guarded.
- Deps are pinned in `gradle/libs.versions.toml`; this plan adds none.
- Commit messages: Conventional Commits, no `Co-Authored-By` trailer, no assistant voice.
- Brand colours: `@color/accent` (#E50914) for "active" tints, `@color/on_surface` otherwise.
- Both bundled lists are GPL-3.0; the app stays MIT. Attribution must appear in `THIRD-PARTY-NOTICES.md` and in the Settings dialog.
- Never block: main-frame requests in `shouldInterceptRequest`, anything `MediaUrlClassifier` classifies as `MANIFEST_HLS`/`MANIFEST_DASH`/`SEGMENT`/`SUBTITLE`, or a first-party sub-resource.

## Test commands

```bash
./gradlew test                                                          # all unit tests
./gradlew testDebugUnitTest --tests "net.mrowser.adblock.BlockListTest" # one class
./gradlew assembleDebug                                                 # compile Android glue
```

---

## File structure

**Create**
- `app/src/main/kotlin/net/mrowser/web/UrlHost.kt` — pure: host from a URL string.
- `app/src/main/kotlin/net/mrowser/web/RegistrableDomain.kt` — pure: eTLD+1 heuristic.
- `app/src/main/kotlin/net/mrowser/adblock/BlockList.kt` — pure: hashed domain set with suffix walk, header info.
- `app/src/main/kotlin/net/mrowser/adblock/AdBlockPolicy.kt` — pure: sub-resource decision.
- `app/src/main/kotlin/net/mrowser/adblock/NavigationPolicy.kt` — pure: top-level navigation / pop-up destination decision.
- `app/src/main/kotlin/net/mrowser/adblock/BlockedResponse.kt` — pure: typed stand-in bodies.
- `app/src/main/kotlin/net/mrowser/adblock/AdBlocker.kt` — Android: state, counter, `WebResourceResponse` building, list loading.
- `app/src/main/res/raw/blocklist.txt` — the bundled list (generated).
- `app/src/main/res/drawable/ic_shield.xml` — chrome-bar icon.
- `scripts/update-blocklist.sh` — list refresh script.
- `.github/workflows/blocklist.yml` — weekly refresh PR.
- `THIRD-PARTY-NOTICES.md` — attributions + GPLv3 text.
- Tests: `app/src/test/kotlin/net/mrowser/web/UrlHostTest.kt`, `.../web/RegistrableDomainTest.kt`, `.../adblock/BlockListTest.kt`, `.../adblock/BlockListCanaryTest.kt`, `.../adblock/AdBlockPolicyTest.kt`, `.../adblock/NavigationPolicyTest.kt`, `.../adblock/BlockedResponseTest.kt`.

**Modify**
- `app/src/main/kotlin/net/mrowser/stream/MediaUrlClassifier.kt` — `hostOf` delegates to `UrlHost`.
- `app/src/main/kotlin/net/mrowser/data/Settings.kt`, `SettingsJson.kt` — `blockAds`, `adsAllowedOn`.
- `app/src/main/kotlin/net/mrowser/stream/SniffingWebViewClient.kt` — interception + navigation gate.
- `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt` — relay consults the list.
- `app/src/main/kotlin/net/mrowser/MainActivity.kt` — wiring, shield button, service worker.
- `app/src/main/kotlin/net/mrowser/home/SettingsView.kt`, `res/layout/settings_view.xml` — two rows.
- `app/src/main/res/layout/activity_main.xml`, `res/values/strings.xml`.
- `CLAUDE.md`, `README.md`.

**Delete**
- `app/src/main/kotlin/net/mrowser/web/PopupPolicy.kt`, `app/src/test/kotlin/net/mrowser/web/PopupPolicyTest.kt`.

---

### Task 1: `UrlHost` and `RegistrableDomain` (pure URL helpers)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/UrlHost.kt`
- Create: `app/src/main/kotlin/net/mrowser/web/RegistrableDomain.kt`
- Modify: `app/src/main/kotlin/net/mrowser/stream/MediaUrlClassifier.kt` (private `hostOf`)
- Test: `app/src/test/kotlin/net/mrowser/web/UrlHostTest.kt`
- Test: `app/src/test/kotlin/net/mrowser/web/RegistrableDomainTest.kt`

**Interfaces:**
- Produces: `object UrlHost { fun of(url: String): String? }` — lowercase host without scheme, userinfo, port, path, query, fragment, trailing dot; `null` when the string has no `://` or an empty host.
- Produces: `object RegistrableDomain { fun of(host: String): String }` — last two labels, or three when the last two form a known two-label public suffix; IP literals and single labels returned unchanged (lowercased).

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/net/mrowser/web/UrlHostTest.kt`:

```kotlin
package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlHostTest {

    @Test fun `extracts a plain host`() {
        assertEquals("example.com", UrlHost.of("https://example.com/path?q=1#f"))
    }

    @Test fun `lowercases and strips port userinfo and trailing dot`() {
        assertEquals("cdn.example.com", UrlHost.of("HTTP://user:pw@CDN.Example.com.:8080/a"))
    }

    @Test fun `keeps an ipv6 literal without brackets`() {
        assertEquals("::1", UrlHost.of("http://[::1]:8080/x"))
    }

    @Test fun `host only url without a path`() {
        assertEquals("example.com", UrlHost.of("https://example.com"))
    }

    @Test fun `query directly after the host`() {
        assertEquals("example.com", UrlHost.of("https://example.com?x=1"))
    }

    @Test fun `no scheme separator is null`() {
        assertNull(UrlHost.of("example.com/path"))
        assertNull(UrlHost.of(""))
    }

    @Test fun `empty host is null`() {
        assertNull(UrlHost.of("about:blank"))
        assertNull(UrlHost.of("https:///path"))
    }
}
```

`app/src/test/kotlin/net/mrowser/web/RegistrableDomainTest.kt`:

```kotlin
package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrableDomainTest {

    @Test fun `drops subdomains down to two labels`() {
        assertEquals("example.com", RegistrableDomain.of("www.example.com"))
        assertEquals("example.com", RegistrableDomain.of("a.b.c.example.com"))
    }

    @Test fun `keeps three labels for a two-label public suffix`() {
        assertEquals("example.co.uk", RegistrableDomain.of("a.b.example.co.uk"))
        assertEquals("example.com.au", RegistrableDomain.of("cdn.example.com.au"))
    }

    @Test fun `two labels or fewer are returned as is`() {
        assertEquals("example.com", RegistrableDomain.of("example.com"))
        assertEquals("localhost", RegistrableDomain.of("localhost"))
    }

    @Test fun `a bare public suffix is returned as is`() {
        assertEquals("co.uk", RegistrableDomain.of("co.uk"))
    }

    @Test fun `ip literals are returned unchanged`() {
        assertEquals("10.0.0.1", RegistrableDomain.of("10.0.0.1"))
        assertEquals("::1", RegistrableDomain.of("::1"))
    }

    @Test fun `lowercases and strips a trailing dot`() {
        assertEquals("example.com", RegistrableDomain.of("WWW.Example.COM."))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.web.UrlHostTest" --tests "net.mrowser.web.RegistrableDomainTest"`
Expected: compilation error, `Unresolved reference: UrlHost` / `RegistrableDomain`.

- [ ] **Step 3: Implement `UrlHost`**

`app/src/main/kotlin/net/mrowser/web/UrlHost.kt`:

```kotlin
package net.mrowser.web

/**
 * Pure: the host of an absolute URL, with no `android.net.Uri`.
 *
 * Lowercased, without scheme, userinfo, port, path, query, fragment, or a trailing dot. An
 * IPv6 literal comes back without its brackets. `null` when there is no `://` or the host is
 * empty (`about:blank`, relative URLs).
 */
object UrlHost {

    fun of(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']').removePrefix("[")
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase().trimEnd('.').ifEmpty { null }
    }
}
```

- [ ] **Step 4: Implement `RegistrableDomain`**

`app/src/main/kotlin/net/mrowser/web/RegistrableDomain.kt`:

```kotlin
package net.mrowser.web

/**
 * Pure: the registrable domain (eTLD+1) of a host, by a small heuristic rather than the full
 * public suffix list.
 *
 * `www.example.com` → `example.com`; `a.example.co.uk` → `example.co.uk`. Used to decide
 * "first-party" for the ad blocker and as the key of the per-site allowlist. Two-label public
 * suffixes are the common country-code ones; a host under a suffix not listed here just
 * collapses to its last two labels, which only makes first-party detection slightly wider.
 */
object RegistrableDomain {

    private val TWO_LABEL_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "com.br", "net.br", "org.br", "gov.br",
        "co.in", "net.in", "org.in", "gov.in",
        "co.za", "org.za", "gov.za",
        "com.mx", "com.tr", "co.kr", "or.kr", "com.ar", "com.cn", "net.cn", "org.cn",
        "com.tw", "com.hk", "co.nz", "org.nz", "com.sg", "com.my", "co.id", "com.ua",
        "com.pl", "com.ru", "com.eg", "com.sa", "com.pk", "com.ph", "com.vn", "com.ng",
        "co.il", "com.co", "com.pe", "com.ve", "com.hr", "co.th", "com.bd", "com.np"
    )

    fun of(host: String): String {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty() || h.contains(':') || isIpv4(h)) return h
        val labels = h.split('.')
        if (labels.size <= 2) return h
        val lastTwo = labels[labels.size - 2] + "." + labels[labels.size - 1]
        val keep = if (lastTwo in TWO_LABEL_SUFFIXES) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    private fun isIpv4(h: String): Boolean =
        h.count { it == '.' } == 3 && h.all { it.isDigit() || it == '.' }
}
```

- [ ] **Step 5: Make `MediaUrlClassifier.hostOf` delegate**

In `app/src/main/kotlin/net/mrowser/stream/MediaUrlClassifier.kt`, add `import net.mrowser.web.UrlHost` and replace the private `hostOf` function body:

```kotlin
    private fun hostOf(url: String): String? = UrlHost.of(url)
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL; `UrlHostTest`, `RegistrableDomainTest` and the existing `MediaUrlClassifierTest` all pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/web/UrlHost.kt app/src/main/kotlin/net/mrowser/web/RegistrableDomain.kt app/src/main/kotlin/net/mrowser/stream/MediaUrlClassifier.kt app/src/test/kotlin/net/mrowser/web/UrlHostTest.kt app/src/test/kotlin/net/mrowser/web/RegistrableDomainTest.kt
git commit -m "feat(web): add UrlHost and RegistrableDomain helpers"
```

---

### Task 2: `BlockList` (pure hashed domain set)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/adblock/BlockList.kt`
- Test: `app/src/test/kotlin/net/mrowser/adblock/BlockListTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class BlockList {
      data class Source(val name: String, val url: String, val licence: String, val entries: Int?)
      data class Info(val generated: String?, val sources: List<Source>)
      val size: Int
      val info: Info
      fun contains(host: String): Boolean
      companion object {
          val EMPTY: BlockList
          fun fromLines(lines: Sequence<String>): BlockList
      }
  }
  ```
- List file format consumed: one domain per line meaning "domain and all subdomains"; `#` lines are metadata. Header lines `# generated: <date>` and `# source: <name> | <url> | <licence> | <N> entries[ | ...]` populate `Info`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/net/mrowser/adblock/BlockListTest.kt`:

```kotlin
package net.mrowser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListTest {

    private fun list(vararg lines: String) = BlockList.fromLines(lines.asSequence())

    @Test fun `matches the exact domain`() {
        assertTrue(list("ads.example").contains("ads.example"))
    }

    @Test fun `matches any subdomain of a listed domain`() {
        val l = list("ads.example")
        assertTrue(l.contains("sub.ads.example"))
        assertTrue(l.contains("a.b.c.ads.example"))
    }

    @Test fun `does not match a sibling or a superstring`() {
        val l = list("ads.example")
        assertFalse(l.contains("example"))
        assertFalse(l.contains("notads.example"))
        assertFalse(l.contains("ads.example.org"))
    }

    @Test fun `never matches on the bare tld`() {
        // "com" as an entry must not make every .com host blocked: the walk stops before it.
        val l = list("com")
        assertFalse(l.contains("example.com"))
        assertEquals(0, l.size)
    }

    @Test fun `host lookup is case and trailing dot insensitive`() {
        assertTrue(list("ads.example").contains("Sub.ADS.Example."))
    }

    @Test fun `skips comments blanks and whitespace and dedupes`() {
        val l = list("# a comment", "", "   ", "ads.example", "ads.example", "  Tracker.Example  ")
        assertEquals(2, l.size)
        assertTrue(l.contains("tracker.example"))
    }

    @Test fun `normalises a wildcard prefix`() {
        assertTrue(list("*.ads.example").contains("x.ads.example"))
        assertTrue(list(".ads.example").contains("ads.example"))
    }

    @Test fun `ignores lines that are not a domain`() {
        val l = list("0.0.0.0 ads.example", "ads.example/path", "localhost")
        assertEquals(0, l.size)
    }

    @Test fun `parses the header into info`() {
        val l = list(
            "# mrowser block list",
            "# generated: 2026-09-18",
            "# source: oisd small | https://small.oisd.nl/domainswild2 | GPL-3.0 | 56157 entries | version 2026",
            "# source: HaGeZi Pop-Up Ads | https://example.org/popupads.txt | GPL-3.0 | 50556 entries",
            "# entries: 92643",
            "ads.example"
        )
        assertEquals("2026-09-18", l.info.generated)
        assertEquals(2, l.info.sources.size)
        assertEquals(
            BlockList.Source("oisd small", "https://small.oisd.nl/domainswild2", "GPL-3.0", 56157),
            l.info.sources[0]
        )
        assertEquals("HaGeZi Pop-Up Ads", l.info.sources[1].name)
        assertEquals(50556, l.info.sources[1].entries)
    }

    @Test fun `a malformed source line is kept with what it has`() {
        val l = list("# source: only a name")
        assertEquals(BlockList.Source("only a name", "", "", null), l.info.sources[0])
    }

    @Test fun `empty list contains nothing and has no info`() {
        assertFalse(BlockList.EMPTY.contains("ads.example"))
        assertEquals(0, BlockList.EMPTY.size)
        assertNull(BlockList.EMPTY.info.generated)
        assertTrue(BlockList.EMPTY.info.sources.isEmpty())
    }

    @Test fun `fnv1a64 matches the reference vector`() {
        // FNV-1a 64-bit of "a" is 0xaf63dc4c8601ec8c (reference test vector).
        assertEquals(0xaf63dc4c8601ec8cuL.toLong(), BlockList.fnv1a64("a"))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.adblock.BlockListTest"`
Expected: compilation error, `Unresolved reference: BlockList`.

- [ ] **Step 3: Implement `BlockList`**

`app/src/main/kotlin/net/mrowser/adblock/BlockList.kt`:

```kotlin
package net.mrowser.adblock

/**
 * Pure: a set of blocked domains, each meaning "this domain and every subdomain".
 *
 * Built from `res/raw/blocklist.txt` (one domain per line, `#` lines are metadata). Domains are
 * stored as sorted 64-bit FNV-1a hashes — about 0.8 MB for ~93k entries, versus several MB as
 * strings — and [contains] walks the host's suffixes (`a.b.c.d`, `b.c.d`, `c.d`) with a binary
 * search per step, stopping before the bare TLD. A collision at this size is a ~1e-9 event and
 * would only over-block one host. This is the scheme personalDNSfilter uses.
 */
class BlockList private constructor(
    private val hashes: LongArray,
    val info: Info
) {

    data class Source(val name: String, val url: String, val licence: String, val entries: Int?)

    data class Info(val generated: String?, val sources: List<Source>)

    val size: Int get() = hashes.size

    /** True when [host] or any parent domain of it (down to, but excluding, the TLD) is listed. */
    fun contains(host: String): Boolean {
        if (hashes.isEmpty()) return false
        var h = host.lowercase().trimEnd('.')
        while (true) {
            val dot = h.indexOf('.')
            if (dot < 0) return false
            if (hashes.binarySearch(fnv1a64(h)) >= 0) return true
            h = h.substring(dot + 1)
        }
    }

    companion object {
        val EMPTY = BlockList(LongArray(0), Info(null, emptyList()))

        private val FNV_OFFSET: Long = 0xcbf29ce484222325uL.toLong()
        private const val FNV_PRIME: Long = 0x100000001b3L

        internal fun fnv1a64(s: String): Long {
            var h = FNV_OFFSET
            for (ch in s) {
                h = h xor ch.code.toLong()
                h *= FNV_PRIME
            }
            return h
        }

        /** Never throws: malformed lines are skipped, comments feed [Info]. */
        fun fromLines(lines: Sequence<String>): BlockList {
            var buf = LongArray(1024)
            var n = 0
            var generated: String? = null
            val sources = ArrayList<Source>()

            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    parseHeader(line)?.let { (g, s) ->
                        if (g != null) generated = g
                        if (s != null) sources.add(s)
                    }
                    continue
                }
                val domain = normalise(line) ?: continue
                if (n == buf.size) buf = buf.copyOf(buf.size * 2)
                buf[n++] = fnv1a64(domain)
            }

            val sorted = buf.copyOf(n)
            sorted.sort()
            var w = 0
            for (i in sorted.indices) {
                if (i == 0 || sorted[i] != sorted[i - 1]) sorted[w++] = sorted[i]
            }
            return BlockList(sorted.copyOf(w), Info(generated, sources))
        }

        /** Lowercase domain with any `*.`/leading/trailing dots removed, or null if not a domain. */
        private fun normalise(line: String): String? {
            val d = line.lowercase().removePrefix("*.").trim('.')
            if (d.isEmpty()) return null
            if (d.any { it.isWhitespace() || it == '/' || it == ':' }) return null
            if (!d.contains('.')) return null
            return d
        }

        /** Returns (generated, source) for a recognised header line, else null. */
        private fun parseHeader(line: String): Pair<String?, Source?>? {
            val body = line.removePrefix("#").trim()
            return when {
                body.startsWith("generated:") ->
                    body.removePrefix("generated:").trim().ifEmpty { null } to null
                body.startsWith("source:") -> {
                    val parts = body.removePrefix("source:").split('|').map { it.trim() }
                    val entries = parts.getOrNull(3)
                        ?.takeWhile { it.isDigit() }
                        ?.toIntOrNull()
                    null to Source(
                        name = parts.getOrElse(0) { "" },
                        url = parts.getOrElse(1) { "" },
                        licence = parts.getOrElse(2) { "" },
                        entries = entries
                    )
                }
                else -> null
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.adblock.BlockListTest"`
Expected: all 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/adblock/BlockList.kt app/src/test/kotlin/net/mrowser/adblock/BlockListTest.kt
git commit -m "feat(adblock): add BlockList, a hashed domain set with suffix walk"
```

---

### Task 3: The bundled list, its refresh script, notices, and the canary test

**Files:**
- Create: `scripts/update-blocklist.sh`
- Create: `app/src/main/res/raw/blocklist.txt` (generated by the script; needs network once)
- Create: `THIRD-PARTY-NOTICES.md`
- Test: `app/src/test/kotlin/net/mrowser/adblock/BlockListCanaryTest.kt`

**Interfaces:**
- Consumes: `BlockList.fromLines`, `BlockList.contains`, `BlockList.info` (Task 2).
- Produces: `R.raw.blocklist` for Task 8; the header format `BlockList` parses.

- [ ] **Step 1: Write the refresh script**

`scripts/update-blocklist.sh`:

```bash
#!/usr/bin/env bash
# Refreshes app/src/main/res/raw/blocklist.txt from its two upstream lists.
#
#   oisd small        https://oisd.nl               GPL-3.0  low-breakage base
#   HaGeZi Pop-Up Ads https://github.com/hagezi/dns-blocklists  GPL-3.0  pop-under networks
#
# Both are "domain + all subdomains" lists. The output is one lowercase domain per line under a
# `#` header that BlockList parses for the Settings attribution dialog. Run from anywhere; needs
# curl, sort, grep, sed. Fails rather than writing a suspiciously small list.
set -euo pipefail
export LC_ALL=C

cd "$(dirname "$0")/.."
OUT=app/src/main/res/raw/blocklist.txt
UA="mrowser-blocklist-updater (+https://github.com/m-salehi-v/mrowser)"
OISD_URL="https://small.oisd.nl/domainswild2"
HAGEZI_URL="https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/popupads-onlydomains.txt"
MIN_ENTRIES=50000

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fetch() {
  curl -fsSL --retry 3 --retry-delay 5 -A "$UA" "$1" -o "$2"
  [ -s "$2" ] || { echo "empty download: $1" >&2; exit 1; }
}

# Comments and blanks out, lowercase, strip "*." and trailing dots and any whitespace, keep
# only lines that look like a domain (has a dot, no path).
clean() {
  grep -v '^[#!]' "$1" \
    | tr 'A-Z' 'a-z' \
    | sed -e 's/[[:space:]]//g' -e 's/^\*\.//' -e 's/^\.//' -e 's/\.$//' \
    | grep '\.' \
    | grep -v '/' \
    | grep -v '^$'
}

fetch "$OISD_URL" "$TMP/oisd.txt"
fetch "$HAGEZI_URL" "$TMP/hagezi.txt"

clean "$TMP/oisd.txt" | sort -u > "$TMP/oisd.clean"
clean "$TMP/hagezi.txt" | sort -u > "$TMP/hagezi.clean"
sort -u "$TMP/oisd.clean" "$TMP/hagezi.clean" > "$TMP/union"

OISD_N=$(wc -l < "$TMP/oisd.clean" | tr -d ' ')
HAGEZI_N=$(wc -l < "$TMP/hagezi.clean" | tr -d ' ')
TOTAL=$(wc -l < "$TMP/union" | tr -d ' ')
OISD_VER=$( { grep -m1 -i '^# Version:' "$TMP/oisd.txt" || true; } | sed 's/^# [Vv]ersion:[[:space:]]*//' | tr -d '\r')

if [ "$TOTAL" -lt "$MIN_ENTRIES" ]; then
  echo "only $TOTAL entries after cleaning (expected >= $MIN_ENTRIES); refusing to write" >&2
  exit 1
fi

{
  echo "# mrowser block list - generated by scripts/update-blocklist.sh, do not edit by hand"
  echo "# generated: $(date -u +%Y-%m-%d)"
  echo "# source: oisd small | $OISD_URL | GPL-3.0 | $OISD_N entries | version ${OISD_VER:-unknown}"
  echo "# source: HaGeZi Pop-Up Ads | $HAGEZI_URL | GPL-3.0 | $HAGEZI_N entries"
  echo "# entries: $TOTAL"
  cat "$TMP/union"
} > "$OUT"

echo "wrote $OUT: $TOTAL domains (oisd $OISD_N, hagezi $HAGEZI_N)"
```

Then: `chmod +x scripts/update-blocklist.sh`.

- [ ] **Step 2: Generate the list**

Run: `scripts/update-blocklist.sh`
Expected: `wrote app/src/main/res/raw/blocklist.txt: 9xxxx domains (oisd 5xxxx, hagezi 5xxxx)`. Then check:

```bash
head -6 app/src/main/res/raw/blocklist.txt
grep -c . app/src/main/res/raw/blocklist.txt
grep -x -E 'popads\.net|exoclick\.com|cloudfront\.net' app/src/main/res/raw/blocklist.txt
```
Expected: the five header lines and the first domain; ~92,650 lines; `popads.net` and `exoclick.com` printed, `cloudfront.net` absent.

- [ ] **Step 3: Write the canary test**

`app/src/test/kotlin/net/mrowser/adblock/BlockListCanaryTest.kt`:

```kotlin
package net.mrowser.adblock

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the real bundled list, not the matcher. A refresh that drops a pop-under network, or
 * picks up a video CDN apex that would break the HLS sniff, fails here before it ships.
 */
class BlockListCanaryTest {

    private val list: BlockList by lazy {
        // Gradle runs unit tests with the module directory (app/) as the working directory;
        // fall back to the repo root for IDE runners.
        val candidates = listOf("src/main/res/raw/blocklist.txt", "app/src/main/res/raw/blocklist.txt")
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("blocklist.txt not found from ${File(".").absolutePath}; run scripts/update-blocklist.sh")
        file.bufferedReader().useLines { BlockList.fromLines(it) }
    }

    private val popUnderNetworks = listOf(
        "popads.net", "propellerads.com", "exoclick.com", "adsterra.com", "hilltopads.net",
        "clickadu.com", "richads.com", "zeropark.com", "trafficstars.com", "popcash.net",
        "juicyads.com", "trafficjunky.net"
    )

    private val mustNeverBeListed = listOf(
        "cloudfront.net", "akamaized.net", "akamaihd.net", "fastly.net", "cdn77.org",
        "b-cdn.net", "jwpcdn.com", "cloudflarestream.com", "vimeocdn.com",
        "youtube.com", "googlevideo.com", "google.com", "github.com"
    )

    @Test fun `list is big enough to be real`() {
        assertTrue("only ${list.size} entries", list.size >= 50_000)
    }

    @Test fun `header names both sources and a date`() {
        assertNotNull(list.info.generated)
        val names = list.info.sources.map { it.name }
        assertTrue(names.toString(), names.any { it.contains("oisd", ignoreCase = true) })
        assertTrue(names.toString(), names.any { it.contains("HaGeZi", ignoreCase = true) })
        assertTrue(list.info.sources.all { it.licence == "GPL-3.0" && it.url.startsWith("https://") })
    }

    @Test fun `known pop-under networks are listed`() {
        val missing = popUnderNetworks.filterNot { list.contains("cdn.$it") }
        assertTrue("missing from the list: $missing", missing.isEmpty())
    }

    @Test fun `video cdn apexes and first-party staples are not listed`() {
        val listed = mustNeverBeListed.filter { list.contains(it) }
        assertTrue("must not be listed: $listed", listed.isEmpty())
    }

    @Test fun `every body line is a plain lowercase domain`() {
        val candidates = listOf("src/main/res/raw/blocklist.txt", "app/src/main/res/raw/blocklist.txt")
        val file = candidates.map(::File).first { it.exists() }
        file.useLines { lines ->
            lines.filterNot { it.startsWith("#") || it.isBlank() }.forEach { line ->
                assertFalse("not a bare domain: '$line'", line.any { it.isWhitespace() || it == '/' || it.isUpperCase() })
                assertTrue("no dot: '$line'", line.contains('.'))
            }
        }
    }
}
```

- [ ] **Step 4: Run the canary**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.adblock.BlockListCanaryTest"`
Expected: 5 tests pass. If `known pop-under networks are listed` fails, print the missing names; every one was present in both upstreams on 2026-09-18, so a miss means a cleaning bug in the script, not a list change.

- [ ] **Step 5: Write `THIRD-PARTY-NOTICES.md`**

Fetch the licence text once: `curl -fsSL https://www.gnu.org/licenses/gpl-3.0.txt -o /tmp/gpl-3.0.txt`. Then create `THIRD-PARTY-NOTICES.md` with this header followed by the full contents of that file under the last heading:

```markdown
# Third-party notices

mrowser is MIT-licensed (see `LICENSE`). It bundles the following data files, which are
distributed under their own licences and are read as data by the app; they are not part of the
app's source code.

## Block list (`app/src/main/res/raw/blocklist.txt`)

Generated by `scripts/update-blocklist.sh` from:

- **oisd small** — https://oisd.nl — GNU General Public License v3.0
  (https://github.com/sjhgvr/oisd/blob/main/LICENSE)
- **HaGeZi's Pop-Up Ads** — https://github.com/hagezi/dns-blocklists — GNU General Public
  License v3.0 (https://github.com/hagezi/dns-blocklists/blob/main/LICENSE)

The generation date and entry counts are in the file's header and in the app under
Settings → Ad block lists.

## GNU General Public License v3.0

```
followed by the verbatim text of `gpl-3.0.txt`.

- [ ] **Step 6: Run the whole suite and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

```bash
git add scripts/update-blocklist.sh app/src/main/res/raw/blocklist.txt THIRD-PARTY-NOTICES.md app/src/test/kotlin/net/mrowser/adblock/BlockListCanaryTest.kt
git commit -m "feat(adblock): bundle the block list with its refresh script and notices"
```

---

### Task 4: `AdBlockPolicy` and `NavigationPolicy` (pure decisions)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/adblock/AdBlockPolicy.kt`
- Create: `app/src/main/kotlin/net/mrowser/adblock/NavigationPolicy.kt`
- Test: `app/src/test/kotlin/net/mrowser/adblock/AdBlockPolicyTest.kt`
- Test: `app/src/test/kotlin/net/mrowser/adblock/NavigationPolicyTest.kt`

**Interfaces:**
- Consumes: `BlockList` (Task 2), `UrlHost`, `RegistrableDomain` (Task 1), `MediaUrlClassifier.classify` (existing).
- Produces:
  ```kotlin
  object AdBlockPolicy {
      enum class Decision { ALLOW, BLOCK }
      fun decide(requestUrl: String, pageHost: String?, isMainFrame: Boolean,
                 enabled: Boolean, allowlisted: Boolean, list: BlockList): Decision
  }
  object NavigationPolicy {
      enum class Decision { LOAD, BLOCK }
      fun decide(targetUrl: String, enabled: Boolean, allowlisted: Boolean, list: BlockList): Decision
  }
  ```

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/net/mrowser/adblock/AdBlockPolicyTest.kt`:

```kotlin
package net.mrowser.adblock

import net.mrowser.adblock.AdBlockPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class AdBlockPolicyTest {

    private val list = BlockList.fromLines(sequenceOf("ads.example", "video.example"))

    private fun decide(
        url: String,
        pageHost: String? = "www.site.example",
        isMainFrame: Boolean = false,
        enabled: Boolean = true,
        allowlisted: Boolean = false
    ) = AdBlockPolicy.decide(url, pageHost, isMainFrame, enabled, allowlisted, list)

    @Test fun `a listed third-party request is blocked`() {
        assertEquals(Decision.BLOCK, decide("https://cdn.ads.example/tag.js"))
    }

    @Test fun `an unlisted request is allowed`() {
        assertEquals(Decision.ALLOW, decide("https://cdn.other.example/app.js"))
    }

    @Test fun `disabled allows everything`() {
        assertEquals(Decision.ALLOW, decide("https://ads.example/tag.js", enabled = false))
    }

    @Test fun `an allowlisted page allows everything`() {
        assertEquals(Decision.ALLOW, decide("https://ads.example/tag.js", allowlisted = true))
    }

    @Test fun `the main frame is never blocked here`() {
        assertEquals(Decision.ALLOW, decide("https://ads.example/landing", isMainFrame = true))
    }

    @Test fun `media the sniffer cares about is never blocked`() {
        assertEquals(Decision.ALLOW, decide("https://video.example/master.m3u8?token=1"))
        assertEquals(Decision.ALLOW, decide("https://video.example/stream.mpd"))
        assertEquals(Decision.ALLOW, decide("https://video.example/seg-01.ts"))
        assertEquals(Decision.ALLOW, decide("https://video.example/seg-01.m4s"))
        assertEquals(Decision.ALLOW, decide("https://video.example/en.vtt"))
        assertEquals(Decision.ALLOW, decide("https://video.example/en.srt"))
    }

    @Test fun `a first-party request is never blocked even when listed`() {
        val firstParty = BlockList.fromLines(sequenceOf("site.example"))
        assertEquals(
            Decision.ALLOW,
            AdBlockPolicy.decide("https://static.site.example/ad.js", "www.site.example", false, true, false, firstParty)
        )
    }

    @Test fun `first-party is judged by registrable domain`() {
        val l = BlockList.fromLines(sequenceOf("cdn.site.example"))
        assertEquals(
            Decision.ALLOW,
            AdBlockPolicy.decide("https://cdn.site.example/x.js", "www.site.example", false, true, false, l)
        )
    }

    @Test fun `with no page host the list still applies`() {
        assertEquals(Decision.BLOCK, decide("https://ads.example/tag.js", pageHost = null))
    }

    @Test fun `a url without a host is allowed`() {
        assertEquals(Decision.ALLOW, decide("data:text/plain,hello"))
    }

    @Test fun `an empty list blocks nothing`() {
        assertEquals(
            Decision.ALLOW,
            AdBlockPolicy.decide("https://ads.example/tag.js", "www.site.example", false, true, false, BlockList.EMPTY)
        )
    }
}
```

`app/src/test/kotlin/net/mrowser/adblock/NavigationPolicyTest.kt`:

```kotlin
package net.mrowser.adblock

import net.mrowser.adblock.NavigationPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {

    private val list = BlockList.fromLines(sequenceOf("ads.example"))

    @Test fun `a navigation to a listed host is blocked`() {
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://go.ads.example/click?id=1", true, false, list))
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("http://ads.example", true, false, list))
    }

    @Test fun `a navigation to an unlisted host loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://site.example/page", true, false, list))
    }

    @Test fun `disabled or allowlisted always loads`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://ads.example/", false, false, list))
        assertEquals(Decision.LOAD, NavigationPolicy.decide("https://ads.example/", true, true, list))
    }

    @Test fun `non-web schemes are not this policy's business`() {
        assertEquals(Decision.LOAD, NavigationPolicy.decide("intent://ads.example/#Intent;end", true, false, list))
        assertEquals(Decision.LOAD, NavigationPolicy.decide("about:blank", true, false, list))
    }

    @Test fun `there is no first-party exemption for navigations`() {
        // Being sent to a listed host is the thing being refused, whoever sent you.
        assertEquals(Decision.BLOCK, NavigationPolicy.decide("https://ads.example/", true, false, list))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.adblock.AdBlockPolicyTest" --tests "net.mrowser.adblock.NavigationPolicyTest"`
Expected: compilation error, unresolved `AdBlockPolicy` / `NavigationPolicy`.

- [ ] **Step 3: Implement `AdBlockPolicy`**

`app/src/main/kotlin/net/mrowser/adblock/AdBlockPolicy.kt`:

```kotlin
package net.mrowser.adblock

import net.mrowser.stream.MediaUrlClassifier
import net.mrowser.stream.MediaUrlClassifier.MediaKind
import net.mrowser.web.RegistrableDomain
import net.mrowser.web.UrlHost

/**
 * Pure: whether a sub-resource request should be answered with a stand-in instead of fetched.
 *
 * Checked in order, first hit wins:
 * 1. blocking off → allow
 * 2. page allowlisted → allow
 * 3. main frame → allow (an empty body here blanks the page; navigations are gated by
 *    [NavigationPolicy] instead)
 * 4. anything the sniffer cares about (manifest, segment, subtitle) → allow, so the list can
 *    never cost the user the stream
 * 5. first-party (same registrable domain as the page) → allow
 * 6. host on the list → block
 */
object AdBlockPolicy {

    enum class Decision { ALLOW, BLOCK }

    private val PROTECTED_MEDIA = setOf(
        MediaKind.MANIFEST_HLS, MediaKind.MANIFEST_DASH, MediaKind.SEGMENT, MediaKind.SUBTITLE
    )

    fun decide(
        requestUrl: String,
        pageHost: String?,
        isMainFrame: Boolean,
        enabled: Boolean,
        allowlisted: Boolean,
        list: BlockList
    ): Decision {
        if (!enabled || allowlisted || isMainFrame) return Decision.ALLOW
        if (MediaUrlClassifier.classify(requestUrl) in PROTECTED_MEDIA) return Decision.ALLOW
        val host = UrlHost.of(requestUrl) ?: return Decision.ALLOW
        if (pageHost != null && RegistrableDomain.of(host) == RegistrableDomain.of(pageHost)) {
            return Decision.ALLOW
        }
        return if (list.contains(host)) Decision.BLOCK else Decision.ALLOW
    }
}
```

- [ ] **Step 4: Implement `NavigationPolicy`**

`app/src/main/kotlin/net/mrowser/adblock/NavigationPolicy.kt`:

```kotlin
package net.mrowser.adblock

import net.mrowser.web.UrlHost

/**
 * Pure: whether a renderer-initiated top-level navigation — a `window.open` destination seen
 * through the relay, or an in-page `location` change — may load.
 *
 * The user gesture is no signal here: Chromium already refused gestureless opens before the
 * app sees them, and a click-hijack script fires inside the user's click. What is checkable is
 * the destination. There is deliberately no first-party exemption: being sent to a listed host
 * is what is refused, whoever sent you. User-typed URLs go through `loadUrl` and never reach
 * this policy.
 */
object NavigationPolicy {

    enum class Decision { LOAD, BLOCK }

    fun decide(targetUrl: String, enabled: Boolean, allowlisted: Boolean, list: BlockList): Decision {
        if (!enabled || allowlisted) return Decision.LOAD
        val scheme = targetUrl.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return Decision.LOAD
        val host = UrlHost.of(targetUrl) ?: return Decision.LOAD
        return if (list.contains(host)) Decision.BLOCK else Decision.LOAD
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.adblock.AdBlockPolicyTest" --tests "net.mrowser.adblock.NavigationPolicyTest"`
Expected: all 16 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/adblock/AdBlockPolicy.kt app/src/main/kotlin/net/mrowser/adblock/NavigationPolicy.kt app/src/test/kotlin/net/mrowser/adblock/AdBlockPolicyTest.kt app/src/test/kotlin/net/mrowser/adblock/NavigationPolicyTest.kt
git commit -m "feat(adblock): add AdBlockPolicy and NavigationPolicy"
```

---

### Task 5: `BlockedResponse` (pure stand-in bodies)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/adblock/BlockedResponse.kt`
- Test: `app/src/test/kotlin/net/mrowser/adblock/BlockedResponseTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object BlockedResponse {
      enum class Kind(val mimeType: String, val body: ByteArray) { IMAGE, HTML, SCRIPT, EMPTY }
      fun kindFor(url: String, accept: String?): Kind
  }
  ```

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/net/mrowser/adblock/BlockedResponseTest.kt`:

```kotlin
package net.mrowser.adblock

import net.mrowser.adblock.BlockedResponse.Kind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedResponseTest {

    @Test fun `image by accept header`() {
        assertEquals(Kind.IMAGE, BlockedResponse.kindFor("https://ads.example/pixel", "image/avif,image/webp,*/*"))
    }

    @Test fun `image by extension`() {
        assertEquals(Kind.IMAGE, BlockedResponse.kindFor("https://ads.example/pixel.GIF?x=1", null))
        assertEquals(Kind.IMAGE, BlockedResponse.kindFor("https://ads.example/a.png", "*/*"))
    }

    @Test fun `html by accept header`() {
        assertEquals(Kind.HTML, BlockedResponse.kindFor("https://ads.example/frame", "text/html,application/xhtml+xml"))
    }

    @Test fun `script by extension`() {
        assertEquals(Kind.SCRIPT, BlockedResponse.kindFor("https://ads.example/tag.js", "*/*"))
        assertEquals(Kind.SCRIPT, BlockedResponse.kindFor("https://ads.example/tag.mjs", null))
    }

    @Test fun `everything else is empty text`() {
        assertEquals(Kind.EMPTY, BlockedResponse.kindFor("https://ads.example/track?e=1", "*/*"))
        assertEquals(Kind.EMPTY, BlockedResponse.kindFor("https://ads.example/track", null))
    }

    @Test fun `bodies and mime types`() {
        assertEquals("image/gif", Kind.IMAGE.mimeType)
        assertArrayEquals("GIF89a".toByteArray(Charsets.US_ASCII), Kind.IMAGE.body.copyOf(6))
        assertEquals(0x3b.toByte(), Kind.IMAGE.body.last())
        assertEquals("text/html", Kind.HTML.mimeType)
        assertEquals("application/javascript", Kind.SCRIPT.mimeType)
        assertEquals("text/plain", Kind.EMPTY.mimeType)
        assertEquals(0, Kind.HTML.body.size)
        assertEquals(0, Kind.SCRIPT.body.size)
        assertEquals(0, Kind.EMPTY.body.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.adblock.BlockedResponseTest"`
Expected: compilation error, unresolved `BlockedResponse`.

- [ ] **Step 3: Implement `BlockedResponse`**

`app/src/main/kotlin/net/mrowser/adblock/BlockedResponse.kt`:

```kotlin
package net.mrowser.adblock

/** A 1×1 transparent GIF89a (42 bytes). Top-level so the enum below can reference it safely. */
private val GIF_1X1: ByteArray = byteArrayOf(
    0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
    0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x21, 0xf9.toByte(), 0x04,
    0x01, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
    0x02, 0x01, 0x44, 0x00, 0x3b
)

/**
 * Pure: what to answer a blocked request with. An empty `text/plain` for an `<img>` fires the
 * page's `onerror` handlers and a bare empty body for an iframe breaks its layout, so the
 * stand-in is typed from the `Accept` header first, then the URL's extension.
 */
object BlockedResponse {

    enum class Kind(val mimeType: String, val body: ByteArray) {
        IMAGE("image/gif", GIF_1X1),
        HTML("text/html", ByteArray(0)),
        SCRIPT("application/javascript", ByteArray(0)),
        EMPTY("text/plain", ByteArray(0)),
    }

    private val IMAGE_EXTENSIONS = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp", ".svg", ".avif", ".ico")

    fun kindFor(url: String, accept: String?): Kind {
        val a = accept?.lowercase().orEmpty()
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            a.startsWith("image/") || IMAGE_EXTENSIONS.any { path.endsWith(it) } -> Kind.IMAGE
            a.contains("text/html") -> Kind.HTML
            path.endsWith(".js") || path.endsWith(".mjs") -> Kind.SCRIPT
            else -> Kind.EMPTY
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.adblock.BlockedResponseTest"`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/adblock/BlockedResponse.kt app/src/test/kotlin/net/mrowser/adblock/BlockedResponseTest.kt
git commit -m "feat(adblock): add typed stand-in bodies for blocked requests"
```

---

### Task 6: Settings fields `blockAds` and `adsAllowedOn`

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/data/Settings.kt`
- Modify: `app/src/main/kotlin/net/mrowser/data/SettingsJson.kt`
- Test: `app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt`

**Interfaces:**
- Produces: `Settings.blockAds: Boolean = true`, `Settings.adsAllowedOn: Set<String> = emptySet()` (registrable domains). JSON keys `blockAds` (boolean) and `adsAllowedOn` (array of strings, written sorted).

- [ ] **Step 1: Add the failing tests**

Append to `app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt` inside the class:

```kotlin
    @Test fun `round trips the ad blocker fields`() {
        val s = Settings(blockAds = false, adsAllowedOn = setOf("site.example", "other.example"))
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `ad blocking is on and the allowlist empty when the fields are absent`() {
        val s = SettingsJson.fromJson("""{"autoOpenPlayer":true}""")
        assertTrue(s.blockAds)
        assertTrue(s.adsAllowedOn.isEmpty())
    }

    @Test fun `non-string allowlist entries are skipped`() {
        val s = SettingsJson.fromJson("""{"adsAllowedOn":["site.example", 5, null, "", "b.example"]}""")
        assertEquals(setOf("site.example", "b.example"), s.adsAllowedOn)
    }

    @Test fun `a malformed allowlist falls back to empty`() {
        assertTrue(SettingsJson.fromJson("""{"adsAllowedOn":"nope"}""").adsAllowedOn.isEmpty())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.data.SettingsJsonTest"`
Expected: compilation error, no parameter `blockAds`.

- [ ] **Step 3: Extend `Settings`**

Replace the `blockPopups` doc comment and add the two fields in `app/src/main/kotlin/net/mrowser/data/Settings.kt`:

```kotlin
package net.mrowser.data

/** App-wide settings. Defaults are the shipped values. Immutable — update via copy(). */
data class Settings(
    val autoOpenPlayer: Boolean = true,
    val cursorSpeed: CursorSpeed = CursorSpeed.NORMAL,
    /**
     * Refuse a window the page opens whose destination is on the block list. On a browser with
     * no tabs a new window replaces the page, which is how streaming sites turn a click on
     * their video into an ad. Off opens whatever was clicked with no list check.
     */
    val blockPopups: Boolean = true,
    /** Answer requests to listed ad hosts with an empty stand-in, and refuse in-page
     *  navigations to them. Sub-resources only; see AdBlockPolicy for what is never blocked. */
    val blockAds: Boolean = true,
    /** Registrable domains (`site.example`) on which the user has allowed ads. */
    val adsAllowedOn: Set<String> = emptySet(),
    /** Internal bookkeeping, not a user preference: default favorites written once. */
    val seeded: Boolean = false,
    /** Internal bookkeeping, not a user preference: hold-BACK hint shown once. */
    val navHintShown: Boolean = false
)
```

- [ ] **Step 4: Extend `SettingsJson`**

`app/src/main/kotlin/net/mrowser/data/SettingsJson.kt`:

```kotlin
package net.mrowser.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of Settings. Missing/unparseable fields → defaults. */
object SettingsJson {

    fun toJson(settings: Settings): String =
        JSONObject()
            .put("autoOpenPlayer", settings.autoOpenPlayer)
            .put("cursorSpeed", settings.cursorSpeed.name)
            .put("blockPopups", settings.blockPopups)
            .put("blockAds", settings.blockAds)
            .put("adsAllowedOn", JSONArray(settings.adsAllowedOn.sorted()))
            .put("seeded", settings.seeded)
            .put("navHintShown", settings.navHintShown)
            .toString()

    fun fromJson(json: String): Settings {
        if (json.isBlank()) return Settings()
        return try {
            val o = JSONObject(json)
            val defaults = Settings()
            Settings(
                autoOpenPlayer = o.optBoolean("autoOpenPlayer", defaults.autoOpenPlayer),
                cursorSpeed = enumOrDefault(o.optString("cursorSpeed"), defaults.cursorSpeed),
                blockPopups = o.optBoolean("blockPopups", defaults.blockPopups),
                blockAds = o.optBoolean("blockAds", defaults.blockAds),
                adsAllowedOn = stringSet(o.optJSONArray("adsAllowedOn")) ?: defaults.adsAllowedOn,
                seeded = o.optBoolean("seeded", defaults.seeded),
                navHintShown = o.optBoolean("navHintShown", defaults.navHintShown)
            )
        } catch (e: JSONException) {
            Settings()
        }
    }

    /** Non-string and blank elements are skipped; a missing/non-array value is null. */
    private fun stringSet(arr: JSONArray?): Set<String>? {
        if (arr == null) return null
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i) as? String ?: continue
            if (v.isNotBlank()) out.add(v)
        }
        return out
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        T::class.java.enumConstants?.firstOrNull { it.name == name } ?: default
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "net.mrowser.data.SettingsJsonTest"`
Expected: all tests pass (the existing `round trips all fields` still holds because defaults compare equal).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/data/Settings.kt app/src/main/kotlin/net/mrowser/data/SettingsJson.kt app/src/test/kotlin/net/mrowser/data/SettingsJsonTest.kt
git commit -m "feat(data): add blockAds and adsAllowedOn settings"
```

---

### Task 7: `AdBlocker` (Android state holder)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/adblock/AdBlocker.kt`

**Interfaces:**
- Consumes: `BlockList`, `AdBlockPolicy`, `NavigationPolicy`, `BlockedResponse`, `UrlHost`, `RegistrableDomain`.
- Produces:
  ```kotlin
  class AdBlocker(
      blockAds: () -> Boolean, blockPopups: () -> Boolean,
      allowedSites: () -> Set<String>, onCountChanged: (Int) -> Unit   // UI thread
  ) {
      val list: BlockList                        // @Volatile, EMPTY until loaded
      var pageHost: String?                      // @Volatile
      val blockedCount: Int
      fun load(source: () -> InputStream)        // background thread, never throws
      fun onPageStarted(url: String)             // resets counter, sets pageHost
      fun onMainFrameRequest(url: String)        // sets pageHost
      fun isAllowlisted(host: String? = pageHost): Boolean
      fun intercept(request: WebResourceRequest): WebResourceResponse?
      fun interceptServiceWorker(request: WebResourceRequest): WebResourceResponse?
      fun isBlockedPopup(url: String): Boolean       // gated by blockPopups()
      fun isBlockedNavigation(url: String): Boolean  // gated by blockAds()
  }
  ```
- No unit test (Android glue). Verified by `assembleDebug` here and on the TV in Task 12.

- [ ] **Step 1: Implement `AdBlocker`**

`app/src/main/kotlin/net/mrowser/adblock/AdBlocker.kt`:

```kotlin
package net.mrowser.adblock

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import net.mrowser.web.RegistrableDomain
import net.mrowser.web.UrlHost

/**
 * Android glue around the pure ad-block policies. Safe to call from WebView worker threads:
 * the list and page host are volatile, the counter atomic, settings are read through the
 * provider lambdas (immutable snapshots), and UI callbacks are posted to the main looper.
 *
 * Until [load] finishes the list is [BlockList.EMPTY] and everything passes — no request thread
 * ever waits on the load.
 */
class AdBlocker(
    private val blockAds: () -> Boolean,
    private val blockPopups: () -> Boolean,
    private val allowedSites: () -> Set<String>,
    private val onCountChanged: (Int) -> Unit
) {

    @Volatile var list: BlockList = BlockList.EMPTY
        private set

    /** Host of the page being shown; set from the main-frame request and onPageStarted. */
    @Volatile var pageHost: String? = null

    private val count = AtomicInteger(0)
    private val ui = Handler(Looper.getMainLooper())

    val blockedCount: Int get() = count.get()

    /** Reads and hashes the list on a background thread; a failure leaves [BlockList.EMPTY]. */
    fun load(source: () -> InputStream) {
        Thread({
            val loaded = runCatching {
                source().bufferedReader().useLines { BlockList.fromLines(it) }
            }.onFailure {
                Log.w(TAG, "block list unavailable; blocking disabled", it)
            }.getOrDefault(BlockList.EMPTY)
            list = loaded
            Log.i(TAG, "block list loaded: ${loaded.size} domains")
        }, "blocklist-load").start()
    }

    fun onPageStarted(url: String) {
        pageHost = UrlHost.of(url)
        count.set(0)
        ui.post { onCountChanged(0) }
    }

    fun onMainFrameRequest(url: String) {
        pageHost = UrlHost.of(url)
    }

    fun isAllowlisted(host: String? = pageHost): Boolean =
        host != null && RegistrableDomain.of(host) in allowedSites()

    /** For [android.webkit.WebViewClient.shouldInterceptRequest]. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? =
        intercept(request.url.toString(), request.isForMainFrame, request.requestHeaders?.get("Accept"), pageHost)

    /**
     * For [android.webkit.ServiceWorkerClient.shouldInterceptRequest] (API 24+). A service
     * worker fetch has no WebView, so the page is taken from its Origin/Referer header.
     */
    fun interceptServiceWorker(request: WebResourceRequest): WebResourceResponse? {
        val headers = request.requestHeaders.orEmpty()
        val origin = headers["Origin"] ?: headers["Referer"]
        val host = origin?.let { UrlHost.of(it) } ?: pageHost
        return intercept(request.url.toString(), false, headers["Accept"], host)
    }

    private fun intercept(url: String, isMainFrame: Boolean, accept: String?, host: String?): WebResourceResponse? {
        val decision = AdBlockPolicy.decide(url, host, isMainFrame, blockAds(), isAllowlisted(host), list)
        if (decision != AdBlockPolicy.Decision.BLOCK) return null
        bump()
        val kind = BlockedResponse.kindFor(url, accept)
        return WebResourceResponse(
            kind.mimeType, "utf-8", 200, "OK",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(kind.body)
        )
    }

    /** A `window.open` destination seen through the relay. Counts when refused. */
    fun isBlockedPopup(url: String): Boolean = gate(url, blockPopups())

    /** An in-page top-level navigation. Counts when refused. */
    fun isBlockedNavigation(url: String): Boolean = gate(url, blockAds())

    private fun gate(url: String, enabled: Boolean): Boolean {
        val blocked = NavigationPolicy.decide(url, enabled, isAllowlisted(), list) == NavigationPolicy.Decision.BLOCK
        if (blocked) bump()
        return blocked
    }

    private fun bump() {
        val n = count.incrementAndGet()
        ui.post { onCountChanged(n) }
    }

    private companion object {
        const val TAG = "AdBlocker"
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/adblock/AdBlocker.kt
git commit -m "feat(adblock): add AdBlocker, the Android state holder"
```

---

### Task 8: Wire interception, the navigation gate and the relay

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/stream/SniffingWebViewClient.kt`
- Modify: `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt`
- Delete: `app/src/main/kotlin/net/mrowser/web/PopupPolicy.kt`, `app/src/test/kotlin/net/mrowser/web/PopupPolicyTest.kt`
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt` (construction of the two clients, `AdBlocker` creation and load, service worker client)

**Interfaces:**
- Consumes: `AdBlocker` (Task 7).
- Produces: `SniffingWebViewClient(sniffer, adBlocker, onNavigate, onLoaded, onExternalScheme, onNavigationBlocked)`; `BrowserWebChromeClient(..., onPopupBlocked, isBlockedPopup, launchExternal)`.

- [ ] **Step 1: Rewrite `SniffingWebViewClient`**

`app/src/main/kotlin/net/mrowser/stream/SniffingWebViewClient.kt`:

```kotlin
package net.mrowser.stream

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import net.mrowser.adblock.AdBlocker
import net.mrowser.web.ExternalSchemePolicy

/**
 * Feeds every request URL to the StreamSniffer, answers listed ad requests with a stand-in
 * (see [AdBlocker]), and routes navigations the WebView cannot or should not load out: non-web
 * schemes to the system (see [ExternalSchemePolicy]), listed ad hosts to nowhere.
 */
class SniffingWebViewClient(
    private val sniffer: StreamSniffer,
    private val adBlocker: AdBlocker,
    private val onNavigate: (String) -> Unit = {},
    private val onLoaded: (String) -> Unit = {},
    /** Hand a non-web URL (`obtainium://`, `intent://`, `mailto:`) to the system. */
    private val onExternalScheme: (String) -> Unit = {},
    /** A top-level navigation to a listed host was refused; the page stays. */
    private val onNavigationBlocked: () -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            adBlocker.onPageStarted(url)
            sniffer.onPageStarted(url)
            onNavigate(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) onLoaded(url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        val target = url.toString()
        if (consume(url.scheme, target, request.hasGesture())) return true
        return request.isForMainFrame && refuseIfListed(target)
    }

    @Deprecated("Kept for API < 24, which does not get the WebResourceRequest overload.")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val target = url ?: return false
        // Pre-24 there is no gesture flag at all. Treating it as user-initiated keeps app links
        // working on those devices; refusing every one of them is the bug being fixed.
        if (consume(Uri.parse(target).scheme, target, isUserGesture = true)) return true
        // No frame flag either: this overload is only called for top-level navigations.
        return refuseIfListed(target)
    }

    /** @return true when mrowser took the navigation and the WebView must not load it. */
    private fun consume(scheme: String?, url: String, isUserGesture: Boolean): Boolean =
        when (ExternalSchemePolicy.decide(scheme, isUserGesture)) {
            ExternalSchemePolicy.Decision.LetWebViewLoad -> false
            ExternalSchemePolicy.Decision.LaunchExternalApp -> {
                onExternalScheme(url)
                true
            }
            // Swallowed: a non-web link the page fired on its own. Returning true keeps the
            // ERR_UNKNOWN_URL_SCHEME error page off a navigation the user never asked for.
            ExternalSchemePolicy.Decision.Ignore -> true
        }

    /** Cancelling here leaves the current page in place — never an empty body for a page. */
    private fun refuseIfListed(url: String): Boolean {
        if (!adBlocker.isBlockedNavigation(url)) return false
        onNavigationBlocked()
        return true
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url?.toString() ?: return null
        if (req.isForMainFrame) adBlocker.onMainFrameRequest(url)
        sniffer.onRequest(url)
        return adBlocker.intercept(req)
    }
}
```

- [ ] **Step 2: Rewrite the relay in `BrowserWebChromeClient`**

In `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt`:

Replace the constructor parameter `private val blockPopups: () -> Boolean = { true },` with:

```kotlin
    /** True when a pop-up's destination is on the block list; see [AdBlocker.isBlockedPopup]. */
    private val isBlockedPopup: (String) -> Boolean = { false },
```

Replace the whole `onCreateWindow` function and its doc comment with:

```kotlin
    /**
     * A page asked for a new window. mrowser has no tabs, so the only place it can go is the
     * window the user is already looking at — a poster, a trailer or an external link opens
     * there and BACK returns.
     *
     * The `isUserGesture` flag is ignored on purpose. With `javaScriptCanOpenWindowsAutomatically`
     * false, Chromium refuses every gestureless open before this is called
     * (`AwContentBrowserClient::CanCreateWindow`), so the flag is always true here; and a
     * click-hijack script fires inside the user's click anyway. What can be judged is the
     * destination, which is only known once the relay below starts loading it.
     */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val host = view ?: return false
        return openInCurrentWindow(host, resultMsg)
    }
```

Replace `adopt` inside `openInCurrentWindow` with:

```kotlin
            private fun adopt(relayView: WebView?, url: String?): Boolean {
                if (url != null) {
                    if (isBlockedPopup(url)) onPopupBlocked() else loadOrLaunch(host, url)
                }
                // Destroying from inside the relay's own callback is not safe; post it.
                relayView?.post { relayView.destroy() }
                return true
            }
```

Update the class doc comment's last clause to "and routes a page's request for a new window into the current one, refusing it when its destination is a listed ad host (see [onCreateWindow])". Remove the `PopupPolicy` mention from the `loadOrLaunch` doc comment if present ("Only user-opened windows reach here" stays true).

- [ ] **Step 3: Delete `PopupPolicy` and its test**

```bash
git rm app/src/main/kotlin/net/mrowser/web/PopupPolicy.kt app/src/test/kotlin/net/mrowser/web/PopupPolicyTest.kt
```

Then grep: `grep -rn "PopupPolicy" app/src CLAUDE.md README.md` — the code must have no hits; docs are fixed in Task 11.

- [ ] **Step 4: Wire `MainActivity`**

In `app/src/main/kotlin/net/mrowser/MainActivity.kt`:

Add imports:

```kotlin
import android.os.Build
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import net.mrowser.adblock.AdBlocker
```

Add a field next to `sniffer`:

```kotlin
    private lateinit var adBlocker: AdBlocker
```

In `onCreate`, right after `seedDefaultFavorites()` and before `sniffer = StreamSniffer(...)`, create and load the blocker. The count callback is a no-op for now; Task 9 binds it to the shield button:

```kotlin
        adBlocker = AdBlocker(
            blockAds = { settings.get().blockAds },
            blockPopups = { settings.get().blockPopups },
            allowedSites = { settings.get().adsAllowedOn },
            onCountChanged = { }
        )
        adBlocker.load { resources.openRawResource(R.raw.blocklist) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Service-worker fetches bypass WebViewClient.shouldInterceptRequest entirely.
            ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    adBlocker.interceptServiceWorker(request)
            })
        }
```

Change the `SniffingWebViewClient` construction to:

```kotlin
        webView.webViewClient = SniffingWebViewClient(
            sniffer,
            adBlocker,
            onNavigate = { url -> updateUrlText(url) },
            onLoaded = { url ->
                recordHistory(url, webView.title)
                // Opening from home starts a fresh tab: drop any prior back-stack so
                // BACK at this page reaches root (close-tab) instead of walking old
                // pages / leftover about:blank entries from a previous tab.
                if (clearHistoryOnLoad) {
                    clearHistoryOnLoad = false
                    webView.clearHistory()
                }
            },
            onExternalScheme = { url -> externalLinks.launch(url) },
            onNavigationBlocked = { Toast.makeText(this, R.string.popup_blocked, Toast.LENGTH_SHORT).show() }
        )
```

In the `BrowserWebChromeClient(...)` construction replace `blockPopups = { settings.get().blockPopups },` with:

```kotlin
            isBlockedPopup = { url -> adBlocker.isBlockedPopup(url) },
```

- [ ] **Step 5: Build and run the suite**

Run: `./gradlew test assembleDebug`
Expected: BUILD SUCCESSFUL; no test references `PopupPolicy` any more.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/kotlin/net/mrowser/stream/SniffingWebViewClient.kt app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt app/src/main/kotlin/net/mrowser/MainActivity.kt app/src/main/kotlin/net/mrowser/web/PopupPolicy.kt app/src/test/kotlin/net/mrowser/web/PopupPolicyTest.kt
git commit -m "feat(adblock): intercept listed requests and gate navigations

Sub-resource requests to listed hosts get a typed empty stand-in; a
top-level navigation or window.open destination on the list is
cancelled so the page stays put. PopupPolicy goes: Chromium refuses
gestureless opens before onCreateWindow, so its Block branch never
ran, and with the setting off it dropped every window instead of
opening it."
```

---

### Task 9: Chrome-bar shield button (per-site allow + counter)

**Files:**
- Create: `app/src/main/res/drawable/ic_shield.xml`
- Modify: `app/src/main/res/layout/activity_main.xml` (chrome bar, between `favoriteButton` and `historyButton`)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt`

**Interfaces:**
- Consumes: `AdBlocker.isAllowlisted`, `AdBlocker.onCountChanged` (Task 7), `Settings.adsAllowedOn` (Task 6), `UrlHost`, `RegistrableDomain` (Task 1).

- [ ] **Step 1: Add the icon**

`app/src/main/res/drawable/ic_shield.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,1L3,5v6c0,5.55 3.84,10.74 9,12c5.16,-1.26 9,-6.45 9,-12V5L12,1z" />
</vector>
```

- [ ] **Step 2: Add the strings**

Append inside `<resources>` in `app/src/main/res/values/strings.xml`:

```xml
    <string name="block_ads_title">Block ads</string>
    <string name="ad_block_lists_title">Ad block lists</string>
    <string name="ad_block_lists_generated">List generated %1$s · %2$d domains</string>
    <string name="ads_allowed_here">Ads allowed on this site</string>
    <string name="ads_blocked_here">Ads blocked on this site</string>
    <string name="ad_block_button">Ad blocker</string>
```

- [ ] **Step 3: Add the button to the chrome bar**

In `app/src/main/res/layout/activity_main.xml`, insert between the `favoriteButton` and `historyButton` `ImageButton`s:

```xml
            <TextView
                android:id="@+id/adBlockButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="10dp"
                android:minHeight="48dp"
                android:paddingStart="8dp"
                android:paddingEnd="8dp"
                android:gravity="center_vertical"
                android:background="@drawable/focus_ring"
                android:focusable="true"
                android:clickable="true"
                android:drawableStart="@drawable/ic_shield"
                android:drawablePadding="4dp"
                android:drawableTint="@color/on_surface"
                android:textColor="@color/on_surface"
                android:textSize="16sp"
                android:contentDescription="@string/ad_block_button" />
```

- [ ] **Step 4: Wire it in `MainActivity`**

Add imports `net.mrowser.web.RegistrableDomain` and `net.mrowser.web.UrlHost`. Add a field:

```kotlin
    private lateinit var adBlockButton: TextView
```

In `onCreate`, after `favoriteButton = findViewById(R.id.favoriteButton)`:

```kotlin
        adBlockButton = findViewById(R.id.adBlockButton)
```

Change the `AdBlocker` construction's `onCountChanged` to:

```kotlin
            onCountChanged = { n -> adBlockButton.text = if (n == 0) "" else n.toString() }
```

(`adBlockButton` is assigned before `adBlocker` is constructed because the `findViewById` block runs first; keep that order.)

Next to `favoriteButton.setOnClickListener { ... }` add:

```kotlin
        adBlockButton.setOnClickListener {
            toggleAdsForSite()
            chrome.onInteracted()
        }
```

In `updateUrlText`, add `updateShieldIcon()` after `updateFavoriteIcon()`.

Add the two functions next to `toggleCurrentFavorite` / `updateFavoriteIcon`:

```kotlin
    /** Shield button: allow ads on the current site if blocked, block them if allowed. Reloads. */
    private fun toggleAdsForSite() {
        val host = webView.url?.let { UrlHost.of(it) } ?: return
        val site = RegistrableDomain.of(host)
        val s = settings.get()
        val wasAllowed = site in s.adsAllowedOn
        settings.update(s.copy(adsAllowedOn = if (wasAllowed) s.adsAllowedOn - site else s.adsAllowedOn + site))
        Toast.makeText(
            this,
            if (wasAllowed) R.string.ads_blocked_here else R.string.ads_allowed_here,
            Toast.LENGTH_SHORT
        ).show()
        updateShieldIcon()
        webView.reload()
    }

    /** Tint the shield accent (red) when ads are allowed on the current site, white otherwise. */
    private fun updateShieldIcon() {
        val allowed = adBlocker.isAllowlisted(webView.url?.let { UrlHost.of(it) })
        val color = getColor(if (allowed) R.color.accent else R.color.on_surface)
        adBlockButton.compoundDrawableTintList = ColorStateList.valueOf(color)
        adBlockButton.setTextColor(color)
    }
```

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/drawable/ic_shield.xml app/src/main/res/layout/activity_main.xml app/src/main/res/values/strings.xml app/src/main/kotlin/net/mrowser/MainActivity.kt
git commit -m "feat(web): add the chrome-bar shield with per-site allow and a counter"
```

---

### Task 10: Settings rows — Block ads toggle and Ad block lists dialog

**Files:**
- Modify: `app/src/main/res/layout/settings_view.xml`
- Modify: `app/src/main/kotlin/net/mrowser/home/SettingsView.kt`
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt` (`settingsView.bind`)

**Interfaces:**
- Consumes: `Settings.blockAds` (Task 6), `BlockList.info` / `size` (Task 2), `AdBlocker.list` (Task 7), strings from Task 9.
- Produces: `SettingsView.bind(repository: SettingsRepository, blockList: () -> BlockList)`.

- [ ] **Step 1: Add the two rows to the layout**

In `app/src/main/res/layout/settings_view.xml`, insert a **Block ads** row after the `settingsPopupRow` `LinearLayout` (same structure as the pop-up row, new ids), and an **Ad block lists** row after the `settingsCursorRow` (give the cursor row `android:layout_marginBottom="8dp"` since it is no longer last):

```xml
    <LinearLayout
        android:id="@+id/settingsAdsRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@drawable/focus_ring"
        android:padding="18dp"
        android:focusable="true">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/block_ads_title"
            android:textColor="@color/on_surface"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/settingsAdsValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/accent"
            android:textSize="18sp" />
    </LinearLayout>
```

```xml
    <LinearLayout
        android:id="@+id/settingsListsRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@drawable/focus_ring"
        android:padding="18dp"
        android:focusable="true">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/ad_block_lists_title"
            android:textColor="@color/on_surface"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/settingsListsValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/accent"
            android:textSize="18sp" />
    </LinearLayout>
```

Resulting row order top to bottom: auto-open, block pop-ups, block ads, cursor speed, ad block lists.

- [ ] **Step 2: Extend `SettingsView`**

`app/src/main/kotlin/net/mrowser/home/SettingsView.kt`:

```kotlin
package net.mrowser.home

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.adblock.BlockList
import net.mrowser.data.CursorSpeed
import net.mrowser.data.Settings
import net.mrowser.data.SettingsRepository

/** Settings overlay: auto-open, pop-up blocker, ad blocker, cursor speed, block-list info. */
class SettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val autoOpenRow: View
    private val popupRow: View
    private val adsRow: View
    private val cursorRow: View
    private val listsRow: View
    private val autoOpenValue: TextView
    private val popupValue: TextView
    private val adsValue: TextView
    private val cursorValue: TextView
    private val listsValue: TextView

    private var repository: SettingsRepository? = null
    private var blockList: () -> BlockList = { BlockList.EMPTY }

    init {
        LayoutInflater.from(context).inflate(R.layout.settings_view, this, true)
        autoOpenRow = findViewById(R.id.settingsAutoOpenRow)
        popupRow = findViewById(R.id.settingsPopupRow)
        adsRow = findViewById(R.id.settingsAdsRow)
        cursorRow = findViewById(R.id.settingsCursorRow)
        listsRow = findViewById(R.id.settingsListsRow)
        autoOpenValue = findViewById(R.id.settingsAutoOpenValue)
        popupValue = findViewById(R.id.settingsPopupValue)
        adsValue = findViewById(R.id.settingsAdsValue)
        cursorValue = findViewById(R.id.settingsCursorValue)
        listsValue = findViewById(R.id.settingsListsValue)

        autoOpenRow.setOnClickListener { toggleAutoOpen() }
        popupRow.setOnClickListener { toggleBlockPopups() }
        adsRow.setOnClickListener { toggleBlockAds() }
        cursorRow.setOnClickListener { pickCursor() }
        listsRow.setOnClickListener { showLists() }
    }

    /** [blockList] is a provider because the list loads on a background thread after launch. */
    fun bind(repository: SettingsRepository, blockList: () -> BlockList) {
        this.repository = repository
        this.blockList = blockList
    }

    fun show() {
        visibility = View.VISIBLE
        render()
        // Post: a synchronous requestFocus right after VISIBLE can fail before the layout
        // pass, leaving nothing focused (matches HomeView/HistoryView).
        post { restoreFocus() }
    }

    fun hide() {
        visibility = View.GONE
    }

    /** Re-seat D-pad focus on the first row. Returns false if it couldn't take focus. */
    fun restoreFocus(): Boolean = autoOpenRow.requestFocus()

    private fun current(): Settings = repository?.get() ?: Settings()

    private fun render() {
        val s = current()
        autoOpenValue.setText(if (s.autoOpenPlayer) R.string.on else R.string.off)
        popupValue.setText(if (s.blockPopups) R.string.on else R.string.off)
        adsValue.setText(if (s.blockAds) R.string.on else R.string.off)
        cursorValue.setText(cursorLabelRes(s.cursorSpeed))
        listsValue.text = blockList().size.toString()
    }

    private fun toggleAutoOpen() {
        val s = current()
        repository?.update(s.copy(autoOpenPlayer = !s.autoOpenPlayer))
        render()
    }

    private fun toggleBlockPopups() {
        val s = current()
        repository?.update(s.copy(blockPopups = !s.blockPopups))
        render()
    }

    private fun toggleBlockAds() {
        val s = current()
        repository?.update(s.copy(blockAds = !s.blockAds))
        render()
    }

    private fun pickCursor() {
        val options = listOf(CursorSpeed.SLOW, CursorSpeed.NORMAL, CursorSpeed.FAST)
        val labels = options.map { context.getString(cursorLabelRes(it)) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.cursor_speed_title)
            .setItems(labels) { _, which ->
                repository?.update(current().copy(cursorSpeed = options[which]))
                render()
            }
            .show()
    }

    /** Attribution for the bundled lists: name, entries, licence and URL per source. */
    private fun showLists() {
        val list = blockList()
        val body = buildString {
            for (s in list.info.sources) {
                append(s.name).append('\n')
                s.entries?.let { append("  ").append(it).append(" domains · ") } ?: append("  ")
                append(s.licence).append('\n')
                append("  ").append(s.url).append("\n\n")
            }
            append(context.getString(R.string.ad_block_lists_generated, list.info.generated ?: "—", list.size))
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.ad_block_lists_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun cursorLabelRes(c: CursorSpeed): Int = when (c) {
        CursorSpeed.SLOW -> R.string.cursor_slow
        CursorSpeed.NORMAL -> R.string.cursor_normal
        CursorSpeed.FAST -> R.string.cursor_fast
    }
}
```

- [ ] **Step 3: Update the bind call in `MainActivity`**

Replace `settingsView.bind(settings)` with:

```kotlin
        settingsView.bind(settings) { adBlocker.list }
```

- [ ] **Step 4: Build**

Run: `./gradlew test assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/settings_view.xml app/src/main/kotlin/net/mrowser/home/SettingsView.kt app/src/main/kotlin/net/mrowser/MainActivity.kt
git commit -m "feat(home): add Block ads toggle and Ad block lists rows to Settings"
```

---

### Task 11: Weekly list-refresh workflow

**Files:**
- Create: `.github/workflows/blocklist.yml`

**Interfaces:**
- Consumes: `scripts/update-blocklist.sh` (Task 3), the canary test.

- [ ] **Step 1: Write the workflow**

`.github/workflows/blocklist.yml` (action SHAs copied from `build.yml`; Dependabot keeps them bumped):

```yaml
name: blocklist

on:
  schedule:
    - cron: '0 6 * * 1'   # Mondays 06:00 UTC
  workflow_dispatch:

permissions:
  contents: write
  pull-requests: write

jobs:
  refresh:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7

      - name: Refresh the block list
        run: scripts/update-blocklist.sh

      - name: Detect a real change
        id: diff
        # The header's date changes on every run; only the body counts.
        run: |
          if git diff --quiet -I '^# (generated|source):' -- app/src/main/res/raw/blocklist.txt; then
            echo "changed=false" >> "$GITHUB_OUTPUT"
          else
            echo "changed=true" >> "$GITHUB_OUTPUT"
          fi

      - if: steps.diff.outputs.changed == 'true'
        uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v5
        with:
          distribution: temurin
          java-version: '17'

      - if: steps.diff.outputs.changed == 'true'
        uses: android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699 # v4
        with:
          packages: platform-tools

      - if: steps.diff.outputs.changed == 'true'
        name: Install SDK packages
        run: sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

      - if: steps.diff.outputs.changed == 'true'
        name: Unit tests (includes the block list canary)
        run: ./gradlew test

      - if: steps.diff.outputs.changed == 'true'
        name: Open a pull request
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          DATE=$(date -u +%Y-%m-%d)
          BRANCH="chore/blocklist-$DATE"
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git checkout -b "$BRANCH"
          git add app/src/main/res/raw/blocklist.txt
          git commit -m "chore(adblock): refresh block list $DATE"
          git push origin "$BRANCH"
          gh pr create \
            --base main --head "$BRANCH" \
            --title "chore(adblock): refresh block list $DATE" \
            --body "$(sed -n '2,5p' app/src/main/res/raw/blocklist.txt | sed 's/^# //')

          Unit tests, including the block list canary, passed in the refresh workflow. A PR opened with the workflow token does not trigger build.yml; close and reopen it to run CI here if needed."
```

- [ ] **Step 2: Validate the YAML**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/blocklist.yml'))" && echo ok` (or `ruby -ryaml -e 'YAML.load_file(".github/workflows/blocklist.yml"); puts "ok"'` if PyYAML is absent).
Expected: `ok`.

Also confirm the diff filter works locally: edit only the `# generated:` line of the list, run the `git diff --quiet -I ...` command from the workflow, expect exit 0; then `git checkout -- app/src/main/res/raw/blocklist.txt`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/blocklist.yml
git commit -m "ci: refresh the block list weekly and open a PR"
```

---

### Task 12: Docs, device checks, and the final sweep

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

- [ ] **Step 1: `CLAUDE.md`**

Add a new architecture section after `### \`web/\` — the D-pad cursor and chrome` and before `### \`home/\` + \`data/\``:

```markdown
### `adblock/` — host-list ad blocking and the navigation gate
Issue #38. A bundled domain list (`res/raw/blocklist.txt`: OISD small ∪ HaGeZi Pop-Up Ads, both GPL-3.0, regenerated by `scripts/update-blocklist.sh` and the weekly `blocklist.yml` workflow; attribution in `THIRD-PARTY-NOTICES.md`) is loaded on a background thread into the **pure** `BlockList` — sorted 64-bit FNV-1a hashes, `contains(host)` walks the host's suffixes down to but excluding the TLD. Until it loads everything passes. Two **pure** decisions: `AdBlockPolicy` for sub-resources (in order: off → allowlisted → main frame → media the sniffer cares about → first-party by `RegistrableDomain` → list), answered with a typed stand-in from the **pure** `BlockedResponse` (1×1 GIF / empty HTML / empty JS / empty text, from the `Accept` header then extension); and `NavigationPolicy` for a top-level destination — a `window.open` target peeked through the chrome client's relay, or an in-page navigation in `shouldOverrideUrlLoading` — which is **cancelled so the page stays put**, never served an empty body. There is no gesture heuristic: with `javaScriptCanOpenWindowsAutomatically=false` Chromium refuses gestureless opens before `onCreateWindow` (`AwContentBrowserClient::CanCreateWindow`), and click-hijack scripts fire inside the user's click, so only the destination can be judged (uBlock Origin's `$popup` model). `AdBlocker` is the one Android class: volatile list + page host, atomic per-page counter, settings via provider lambdas (`blockAds` gates interception and navigations, `blockPopups` gates the relay, `adsAllowedOn` is the per-site allowlist keyed by registrable domain). API 24+ also registers a `ServiceWorkerClient`, since service-worker fetches bypass `WebViewClient`. UI: the chrome-bar shield (count label, click = allow/block ads on this site + reload, tinted `accent` when allowed), Settings rows **Block ads** and **Ad block lists** (attribution dialog from the list header). `UrlHost` and `RegistrableDomain` live in `web/` next to `UrlNormalizer` so `stream/` and `adblock/` share them without a cycle. The canary test `BlockListCanaryTest` reads the real list and fails CI if a pop-under network drops out or a video-CDN apex creeps in. Host-level only: no cosmetic hiding, and same-origin ads (YouTube-style) are not blockable this way.
```

In the `### \`web/\`` section, replace the whole `BrowserWebChromeClient` bullet (the one that currently reads "…answers `onCreateWindow` via the **pure** `PopupPolicy`…") with:

```markdown
- `BrowserWebChromeClient` handles HTML5 fullscreen video, forwards page titles (`onReceivedTitle`) to record browsing history, and answers `onCreateWindow` by relaying the window into the current WebView (there are no tabs): a throwaway relay `WebView` receives the pending navigation purely to read its URL, which never reaches the opener and is then loaded here — unless `AdBlocker.isBlockedPopup` says it is a listed ad host, in which case it is refused and counted. The `isUserGesture` flag is ignored on purpose — see `adblock/`. The **Block pop-ups** setting gates only that list check; Off opens whatever was clicked. A pop-up aimed at a non-web scheme goes through `ExternalSchemePolicy` rather than `loadUrl` (which would hit the error page).
```

In the `home/` + `data/` section, in the **Global settings** bullet, replace

> `SettingsView` is the overlay (reached from a **Settings** gear in the home header) with three rows: an auto-open toggle, a **block pop-ups** toggle, and an `AlertDialog` picker for **cursor speed** (`CursorSpeed`, a multiplier over `CursorGeometry`). All three are read **live** at use-time via provider lambdas — the auto-open gate in `onStreamAvailable`, `blockPopups` in `BrowserWebChromeClient.onCreateWindow`, and `cursorSpeed.multiplier` in `CursorController` — so changes apply with no restart and need no change-callback (all access is on the UI thread).

with

> `SettingsView` is the overlay (reached from a **Settings** gear in the home header) with five rows: an auto-open toggle, a **block pop-ups** toggle, a **block ads** toggle, an `AlertDialog` picker for **cursor speed** (`CursorSpeed`, a multiplier over `CursorGeometry`), and an **Ad block lists** row that opens the attribution dialog built from the bundled list's header. Every setting is read **live** at use-time via provider lambdas — the auto-open gate in `onStreamAvailable`, `blockPopups`/`blockAds`/`adsAllowedOn` through `AdBlocker`, and `cursorSpeed.multiplier` in `CursorController` — so changes apply with no restart and need no change-callback. `Settings.get()` returns an immutable snapshot, which is why `AdBlocker` may read it from WebView worker threads.

and replace "`Settings` also holds `seeded` and `navHintShown`" with "`Settings` also holds the per-site ad allowlist `adsAllowedOn` (registrable domains, toggled from the chrome-bar shield, no row in `SettingsView`), plus `seeded` and `navHintShown`".

- [ ] **Step 2: `README.md`**

In `## Features`, replace the **Pop-up handling** bullet with:

```markdown
- **Ad blocking** — requests to ~90k known ad and pop-under hosts (OISD small + HaGeZi Pop-Up Ads, bundled and refreshed weekly) are answered with an empty stand-in, and a `window.open` or in-page redirect to one of them is refused while the page stays put. Per-site allow from the chrome bar, blocked counter, never touches the video stream itself.
- **Pop-up handling** — a window the user opens loads in the current window (there are no tabs) unless its destination is a known ad host; `target="_blank"` links still work and BACK returns you.
```

Update the **Global settings** bullet to "auto-open-player, block-pop-ups, block-ads, and cursor-speed".

In `## FAQ`, add after the "What is the HLS sniffer?" entry:

```markdown
**Does the ad blocker block everything?**
No. It blocks by **host**: requests to ~90k known ad and pop-under domains are dropped, and being sent to one of them by a click-hijack is refused. It does not hide empty ad slots (no cosmetic filtering) and cannot block ads served from the same host as the content, as YouTube does. The page's own video stream is never touched. Turn it off per site with the shield in the chrome bar, or globally in Settings.
```

In `## License`, append: "Bundled block lists are GPL-3.0; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)."

- [ ] **Step 3: Final sweep**

Run:

```bash
grep -rn "PopupPolicy" app/src CLAUDE.md README.md docs/superpowers/plans/2026-09-18-adblock-and-popups.md | grep -v "plans/" ; echo "---"
./gradlew test assembleDebug
```
Expected: no `PopupPolicy` hits outside this plan and the spec/research docs; BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: describe the ad blocker and the navigation gate"
```

- [ ] **Step 5: Device checks (Android TV, debug APK)**

Install `app/build/outputs/apk/debug/app-debug.apk` on the TV (`adb install -r`; see the `tv-adb-test-harness` memory note for the relay/keyevent gotchas). Record each result in the PR description.

1. **Counter + rendering.** Open a mainstream site heavy with third-party ads (a news site). Summon the chrome bar: the shield shows a non-zero count; the page renders without broken layout; `adb logcat -s AdBlocker` shows `block list loaded: 9xxxx domains` once at launch.
2. **Pop-under refused.** Open a streaming site known for click-hijacked pop-unders. Press OK on the player once: the page does **not** navigate away; toast "Pop-up blocked"; counter increments. Press again: the video plays.
3. **Stream survives.** Open the HLS test page used for the demo GIF: the play chip appears / the native player opens with **Block ads** on.
4. **Per-site allow.** Shield → toast "Ads allowed on this site", page reloads with ads, shield tinted red, count stays 0. Kill and relaunch the app, reopen the site: still allowed (persisted). Shield again → blocked, white.
5. **Global off.** Settings → Block ads Off → reload the news site: count stays 0, ads show. Back On.
6. **Attribution.** Settings → Ad block lists: dialog lists both sources, a generated date, and the total.
7. **Service worker (API 24+ box).** Open a PWA-style site that registers a service worker (any site with `navigator.serviceWorker` — check in logcat via `chrome://inspect` or the site's own docs); navigate within it: blocks still counted.
8. **Legit new window.** On a site with a `target="_blank"` link to an unlisted host, click it: it opens in the current window; BACK returns.
9. **Block pop-ups Off.** Settings → Block pop-ups Off → the pop-under in check 2 now opens in the current window (this is the documented Off behaviour). Back On.

Any failure in 2, 3 or 4 blocks the merge; fix the cause and re-run. Then follow the `superpowers:finishing-a-development-branch` skill.
