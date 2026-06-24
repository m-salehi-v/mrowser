# Browsing History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track recently visited sites and show them on a dedicated History overlay (newest 50, deduped by URL), reachable from the home overlay and the chrome bar.

**Architecture:** Mirror the existing Favorites clean-architecture split exactly — pure `HistoryOps`/`HistoryJson` (JVM-tested), a thin `JsonHistoryStore` for file I/O, and Android glue (`HistoryView`, recording hooks, wiring) kept thin. Recording is driven by `SniffingWebViewClient.onPageStarted` (host placeholder title) then upgraded by `WebChromeClient.onReceivedTitle` (real title) — dedup makes the title upgrade a clean re-record.

**Tech Stack:** Kotlin, Android framework `Activity` (no AndroidX/Compose), `org.json`, JUnit 4.

Reference implementation to mirror: `net.mrowser.data.Favorite*` and `net.mrowser.home.HomeView`.

---

## File Structure

New:
- `app/src/main/kotlin/net/mrowser/data/HistoryEntry.kt` — data model
- `app/src/main/kotlin/net/mrowser/data/HistoryRepository.kt` — interface
- `app/src/main/kotlin/net/mrowser/data/HistoryOps.kt` — pure dedup + cap
- `app/src/main/kotlin/net/mrowser/data/HistoryJson.kt` — pure (de)serialization
- `app/src/main/kotlin/net/mrowser/data/JsonHistoryStore.kt` — file-backed repo
- `app/src/main/kotlin/net/mrowser/home/RelativeTime.kt` — pure "Nm ago" formatter
- `app/src/main/kotlin/net/mrowser/home/HistoryView.kt` — overlay view
- `app/src/main/res/layout/history_view.xml` — overlay layout
- `app/src/main/res/layout/history_row.xml` — one row
- `app/src/test/kotlin/net/mrowser/data/HistoryOpsTest.kt`
- `app/src/test/kotlin/net/mrowser/data/HistoryJsonTest.kt`
- `app/src/test/kotlin/net/mrowser/home/RelativeTimeTest.kt`

Modified:
- `app/src/main/res/values/strings.xml` — new strings
- `app/src/main/res/layout/home_view.xml` — History button
- `app/src/main/res/layout/activity_main.xml` — chrome History button + HistoryView overlay
- `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt` — `onReceivedTitle` hook
- `app/src/main/kotlin/net/mrowser/home/HomeView.kt` — `onHistory` callback + button
- `app/src/main/kotlin/net/mrowser/MainActivity.kt` — store, recording, wiring, entry points, back nav

---

## Task 1: HistoryEntry + HistoryOps (pure dedup + cap)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/HistoryEntry.kt`
- Create: `app/src/main/kotlin/net/mrowser/data/HistoryOps.kt`
- Test: `app/src/test/kotlin/net/mrowser/data/HistoryOpsTest.kt`

- [ ] **Step 1: Create the data model**

`app/src/main/kotlin/net/mrowser/data/HistoryEntry.kt`:
```kotlin
package net.mrowser.data

/** A visited site. Identity is the url; visitedAt is epoch millis. */
data class HistoryEntry(val title: String, val url: String, val visitedAt: Long)
```

- [ ] **Step 2: Write the failing test**

`app/src/test/kotlin/net/mrowser/data/HistoryOpsTest.kt`:
```kotlin
package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryOpsTest {

    private fun e(url: String, t: Long, title: String = url) = HistoryEntry(title, url, t)

    @Test fun `record puts a new entry at the front`() {
        val a = e("https://a.net", 1)
        val b = e("https://b.net", 2)
        assertEquals(listOf(b, a), HistoryOps.record(listOf(a), b))
    }

    @Test fun `record dedups by url and bumps to the front with the new entry`() {
        val a = e("https://a.net", 1)
        val b = e("https://b.net", 2)
        val a2 = e("https://a.net", 3, title = "A again")
        assertEquals(listOf(a2, b), HistoryOps.record(listOf(b, a), a2))
    }

    @Test fun `record caps the list at the newest entries`() {
        val seed = (1..50).map { e("https://s$it.net", it.toLong()) }
        val fresh = e("https://new.net", 99)
        val result = HistoryOps.record(seed, fresh, cap = 50)
        assertEquals(50, result.size)
        assertEquals(fresh, result.first())
        assertEquals("https://s2.net", result.last().url)
    }

    @Test fun `clear empties the list`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryOps.clear())
    }
}
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `./gradlew test --tests "net.mrowser.data.HistoryOpsTest"`
Expected: FAIL — `HistoryOps` unresolved reference.

- [ ] **Step 4: Implement HistoryOps**

`app/src/main/kotlin/net/mrowser/data/HistoryOps.kt`:
```kotlin
package net.mrowser.data

/** Pure list operations on history, keyed by url. */
object HistoryOps {

    const val CAP = 50

    /** Remove any entry with the same url, prepend the new one, keep only the newest [cap]. */
    fun record(list: List<HistoryEntry>, entry: HistoryEntry, cap: Int = CAP): List<HistoryEntry> =
        (listOf(entry) + list.filterNot { it.url == entry.url }).take(cap)

    fun clear(): List<HistoryEntry> = emptyList()
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `./gradlew test --tests "net.mrowser.data.HistoryOpsTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/data/HistoryEntry.kt app/src/main/kotlin/net/mrowser/data/HistoryOps.kt app/src/test/kotlin/net/mrowser/data/HistoryOpsTest.kt
git commit -m "feat: add HistoryEntry and pure HistoryOps with dedup and cap"
```

---

## Task 2: HistoryJson (pure serialization)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/HistoryJson.kt`
- Test: `app/src/test/kotlin/net/mrowser/data/HistoryJsonTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/net/mrowser/data/HistoryJsonTest.kt`:
```kotlin
package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryJsonTest {

    @Test fun `round trips a list`() {
        val items = listOf(
            HistoryEntry("A", "https://a.net", 1000L),
            HistoryEntry("B", "https://b.net", 2000L)
        )
        assertEquals(items, HistoryJson.fromJson(HistoryJson.toJson(items)))
    }

    @Test fun `empty list round trips`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryJson.fromJson(HistoryJson.toJson(emptyList())))
    }

    @Test fun `blank input is an empty list`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryJson.fromJson("   "))
    }

    @Test fun `corrupt input is an empty list`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryJson.fromJson("not json"))
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew test --tests "net.mrowser.data.HistoryJsonTest"`
Expected: FAIL — `HistoryJson` unresolved reference.

- [ ] **Step 3: Implement HistoryJson**

`app/src/main/kotlin/net/mrowser/data/HistoryJson.kt`:
```kotlin
package net.mrowser.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of the history list. */
object HistoryJson {

    fun toJson(items: List<HistoryEntry>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("visitedAt", it.visitedAt)
            )
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<HistoryEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HistoryEntry(o.getString("title"), o.getString("url"), o.getLong("visitedAt"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew test --tests "net.mrowser.data.HistoryJsonTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/data/HistoryJson.kt app/src/test/kotlin/net/mrowser/data/HistoryJsonTest.kt
git commit -m "feat: add pure HistoryJson serialization"
```

---

## Task 3: HistoryRepository + JsonHistoryStore

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/HistoryRepository.kt`
- Create: `app/src/main/kotlin/net/mrowser/data/JsonHistoryStore.kt`

No unit test — this is thin file I/O over already-tested pure logic, mirroring `JsonFavoritesStore` (which also has no test). Verified by the build in Task 9.

- [ ] **Step 1: Create the repository interface**

`app/src/main/kotlin/net/mrowser/data/HistoryRepository.kt`:
```kotlin
package net.mrowser.data

interface HistoryRepository {
    fun findAll(): List<HistoryEntry>
    fun record(entry: HistoryEntry)
    fun clear()
}
```

- [ ] **Step 2: Create the file-backed store**

`app/src/main/kotlin/net/mrowser/data/JsonHistoryStore.kt`:
```kotlin
package net.mrowser.data

import java.io.File

/** HistoryRepository backed by a JSON file; pure logic delegated to HistoryOps/HistoryJson. */
class JsonHistoryStore(private val file: File) : HistoryRepository {

    private var items: List<HistoryEntry> =
        if (file.exists()) HistoryJson.fromJson(file.readText()) else emptyList()

    override fun findAll(): List<HistoryEntry> = items

    override fun record(entry: HistoryEntry) {
        items = HistoryOps.record(items, entry); persist()
    }

    override fun clear() {
        items = HistoryOps.clear(); persist()
    }

    private fun persist() {
        runCatching { file.writeText(HistoryJson.toJson(items)) }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/data/HistoryRepository.kt app/src/main/kotlin/net/mrowser/data/JsonHistoryStore.kt
git commit -m "feat: add HistoryRepository and JsonHistoryStore"
```

---

## Task 4: RelativeTime (pure "Nm ago" formatter)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/home/RelativeTime.kt`
- Test: `app/src/test/kotlin/net/mrowser/home/RelativeTimeTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/net/mrowser/home/RelativeTimeTest.kt`:
```kotlin
package net.mrowser.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    @Test fun `under a minute is just now`() {
        assertEquals("just now", RelativeTime.format(0))
        assertEquals("just now", RelativeTime.format(59_000))
    }

    @Test fun `minutes`() {
        assertEquals("1m ago", RelativeTime.format(60_000))
        assertEquals("59m ago", RelativeTime.format(59 * 60_000L))
    }

    @Test fun `hours`() {
        assertEquals("1h ago", RelativeTime.format(60 * 60_000L))
        assertEquals("23h ago", RelativeTime.format(23 * 60 * 60_000L))
    }

    @Test fun `days`() {
        assertEquals("1d ago", RelativeTime.format(24 * 60 * 60_000L))
        assertEquals("7d ago", RelativeTime.format(7 * 24 * 60 * 60_000L))
    }

    @Test fun `negative clock skew is just now`() {
        assertEquals("just now", RelativeTime.format(-5_000))
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew test --tests "net.mrowser.home.RelativeTimeTest"`
Expected: FAIL — `RelativeTime` unresolved reference.

- [ ] **Step 3: Implement RelativeTime**

`app/src/main/kotlin/net/mrowser/home/RelativeTime.kt`:
```kotlin
package net.mrowser.home

/** Pure formatter for an elapsed-millis delta into a short "Nm ago" label. */
object RelativeTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun format(deltaMillis: Long): String = when {
        deltaMillis < MINUTE -> "just now"
        deltaMillis < HOUR -> "${deltaMillis / MINUTE}m ago"
        deltaMillis < DAY -> "${deltaMillis / HOUR}h ago"
        else -> "${deltaMillis / DAY}d ago"
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew test --tests "net.mrowser.home.RelativeTimeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/home/RelativeTime.kt app/src/test/kotlin/net/mrowser/home/RelativeTimeTest.kt
git commit -m "feat: add pure RelativeTime formatter"
```

---

## Task 5: Strings + layouts

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/history_row.xml`
- Create: `app/src/main/res/layout/history_view.xml`

No unit test (resources). Verified by the build in Task 9.

- [ ] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`, add these inside `<resources>` (after the `favorites_empty` line):
```xml
    <string name="history">History</string>
    <string name="clear_history">Clear all</string>
    <string name="history_empty">No history yet — sites you visit will show up here.</string>
```

- [ ] **Step 2: Create the row layout**

`app/src/main/res/layout/history_row.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:background="@drawable/card_bg"
    android:padding="12dp"
    android:focusable="true">

    <TextView
        android:id="@+id/rowLetter"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:background="@drawable/letter_tile_bg"
        android:gravity="center"
        android:text="M"
        android:textColor="#141414"
        android:textSize="22sp"
        android:textStyle="bold" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="14dp"
        android:orientation="vertical">

        <TextView
            android:id="@+id/rowTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:ellipsize="end"
            android:text="@string/app_name"
            android:textColor="@color/on_surface"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/rowUrl"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:ellipsize="end"
            android:text="@string/url_hint"
            android:textColor="@color/hint"
            android:textSize="13sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/rowTime"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="14dp"
        android:text="@string/history"
        android:textColor="@color/hint"
        android:textSize="13sp" />
</LinearLayout>
```

- [ ] **Step 3: Create the overlay layout**

`app/src/main/res/layout/history_view.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/surface"
    android:padding="32dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingBottom="24dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/history"
            android:textColor="@color/on_surface"
            android:textSize="28sp"
            android:textStyle="bold" />

        <Button
            android:id="@+id/clearHistoryButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/focus_ring"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:text="@string/clear_history"
            android:textColor="@color/on_surface" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:fillViewport="true">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:id="@+id/historyList"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <TextView
                android:id="@+id/historyEmptyHint"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:gravity="center"
                android:paddingTop="80dp"
                android:text="@string/history_empty"
                android:textColor="@color/hint"
                android:textSize="18sp"
                android:visibility="gone" />
        </FrameLayout>
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 4: Verify resources compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (HistoryView is added next task; these layouts compile standalone).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/history_row.xml app/src/main/res/layout/history_view.xml
git commit -m "feat: add history strings and overlay layouts"
```

---

## Task 6: HistoryView

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/home/HistoryView.kt`

No unit test (Android view glue). Verified by the build in Task 9.

- [ ] **Step 1: Implement HistoryView**

`app/src/main/kotlin/net/mrowser/home/HistoryView.kt`:
```kotlin
package net.mrowser.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.data.HistoryEntry
import net.mrowser.data.HistoryRepository

/** History overlay: heading + clear-all + a list of visited sites, newest first. */
class HistoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val list: LinearLayout
    private val emptyHint: TextView
    private val clearButton: Button

    private var repository: HistoryRepository? = null
    private var onOpen: (String) -> Unit = {}
    private var onClear: () -> Unit = {}
    private var onAddFavorite: (HistoryEntry) -> Unit = {}
    private var now: () -> Long = { System.currentTimeMillis() }

    init {
        LayoutInflater.from(context).inflate(R.layout.history_view, this, true)
        list = findViewById(R.id.historyList)
        emptyHint = findViewById(R.id.historyEmptyHint)
        clearButton = findViewById(R.id.clearHistoryButton)
        clearButton.setOnClickListener { onClear() }
    }

    fun bind(
        repository: HistoryRepository,
        onOpen: (String) -> Unit,
        onClear: () -> Unit,
        onAddFavorite: (HistoryEntry) -> Unit
    ) {
        this.repository = repository
        this.onOpen = onOpen
        this.onClear = onClear
        this.onAddFavorite = onAddFavorite
    }

    fun show() {
        visibility = View.VISIBLE
        refresh()
        (list.getChildAt(0) ?: clearButton).requestFocus()
    }

    fun hide() {
        visibility = View.GONE
    }

    fun refresh() {
        val items = repository?.findAll().orEmpty()
        list.removeAllViews()
        emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val nowMs = now()
        items.forEach { list.addView(row(it, nowMs)) }
    }

    private fun row(entry: HistoryEntry, nowMs: Long): View {
        val v = LayoutInflater.from(context).inflate(R.layout.history_row, list, false)
        val letter = v.findViewById<TextView>(R.id.rowLetter)
        val title = v.findViewById<TextView>(R.id.rowTitle)
        val url = v.findViewById<TextView>(R.id.rowUrl)
        val time = v.findViewById<TextView>(R.id.rowTime)
        letter.text = entry.title.trim().take(1).uppercase().ifEmpty { "•" }
        letter.backgroundTintList = ColorStateList.valueOf(colorFor(entry.url))
        title.text = entry.title.ifBlank { entry.url }
        url.text = entry.url
        time.text = RelativeTime.format(nowMs - entry.visitedAt)
        v.setOnClickListener { onOpen(entry.url) }
        v.setOnLongClickListener { onAddFavorite(entry); true }
        v.setOnFocusChangeListener { row, hasFocus ->
            val s = if (hasFocus) 1.04f else 1f
            row.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        return v
    }

    /** Stable pleasant color derived from the url (matches HomeView). */
    private fun colorFor(url: String): Int {
        val hue = ((url.hashCode() % 360) + 360) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.80f))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/home/HistoryView.kt
git commit -m "feat: add HistoryView overlay"
```

---

## Task 7: onReceivedTitle hook in BrowserWebChromeClient

**Files:**
- Modify: `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt`

- [ ] **Step 1: Add the callback param and override**

In `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt`, add a new constructor
parameter and import, and an `onReceivedTitle` override.

Add `import android.webkit.WebView` near the existing `import android.webkit.WebChromeClient`.

Change the constructor (add the last param):
```kotlin
class BrowserWebChromeClient(
    private val activity: Activity,
    private val container: ViewGroup,
    private val onEnter: () -> Unit,
    private val onExit: () -> Unit,
    private val onTitle: (url: String, title: String) -> Unit = { _, _ -> }
) : WebChromeClient() {
```

Add this override (place it right after the `isFullscreen` property, before `onShowCustomView`):
```kotlin
    override fun onReceivedTitle(view: WebView?, title: String?) {
        val url = view?.url ?: return
        if (!title.isNullOrBlank()) onTitle(url, title)
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (default param means the existing `MainActivity` call still compiles).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt
git commit -m "feat: surface page title from WebChromeClient.onReceivedTitle"
```

---

## Task 8: Home overlay History button (HomeView)

**Files:**
- Modify: `app/src/main/res/layout/home_view.xml`
- Modify: `app/src/main/kotlin/net/mrowser/home/HomeView.kt`

- [ ] **Step 1: Add the button to the layout**

In `app/src/main/res/layout/home_view.xml`, add this `Button` as the last child of the
header `LinearLayout` (immediately after the `homeUrlInput` `EditText`, before that inner
`LinearLayout` closes at line 40):
```xml
        <Button
            android:id="@+id/homeHistoryButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:background="@drawable/focus_ring"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:text="@string/history"
            android:textColor="@color/on_surface" />
```

- [ ] **Step 2: Wire the callback in HomeView**

In `app/src/main/kotlin/net/mrowser/home/HomeView.kt`:

Add the import `import android.widget.Button` (with the other `android.widget` imports).

Add a field next to the other callback fields (after `onEdit`):
```kotlin
    private var onHistory: () -> Unit = {}
```

In `init`, after the `urlInput.setOnEditorActionListener { ... }` block, wire the button:
```kotlin
        findViewById<Button>(R.id.homeHistoryButton).setOnClickListener { onHistory() }
```

Change the `bind` signature to accept `onHistory` and store it. Replace the whole `bind`
function with:
```kotlin
    fun bind(
        repository: FavoritesRepository,
        onOpen: (Favorite) -> Unit,
        onSubmitUrl: (String) -> Unit,
        onEdit: (Favorite) -> Unit,
        onHistory: () -> Unit
    ) {
        this.repository = repository
        this.onOpen = onOpen
        this.onSubmitUrl = onSubmitUrl
        this.onEdit = onEdit
        this.onHistory = onHistory
    }
```

(The `MainActivity` call is updated in Task 9; the build won't pass until then.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/home_view.xml app/src/main/kotlin/net/mrowser/home/HomeView.kt
git commit -m "feat: add History button to the home overlay"
```

---

## Task 9: Chrome bar overlay + MainActivity wiring

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt`

- [ ] **Step 1: Add the chrome History button and the HistoryView overlay to the layout**

In `app/src/main/res/layout/activity_main.xml`:

(a) Add a History `ImageButton` to the `chromeBar`, immediately after the `favoriteButton`
`ImageButton` (after its closing `/>` at line 65, before the `urlInput` `EditText`):
```xml
            <ImageButton
                android:id="@+id/historyButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="10dp"
                android:background="@drawable/focus_ring"
                android:src="@android:drawable/ic_menu_recent_history"
                android:tint="@color/on_surface"
                android:contentDescription="@string/history" />
```

(b) Add the `HistoryView` overlay as the last child of the root `FrameLayout`, immediately
after the `HomeView` element (so it sits on top of everything; starts hidden):
```xml
    <net.mrowser.home.HistoryView
        android:id="@+id/historyView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone" />
```

- [ ] **Step 2: Wire everything in MainActivity**

In `app/src/main/kotlin/net/mrowser/MainActivity.kt`, make the following edits.

(a) Add imports (with the existing `net.mrowser` imports):
```kotlin
import net.mrowser.data.HistoryEntry
import net.mrowser.data.JsonHistoryStore
import net.mrowser.home.HistoryView
```

(b) Add fields next to the other `lateinit` fields (after `private lateinit var favorites: JsonFavoritesStore`):
```kotlin
    private lateinit var history: JsonHistoryStore
    private lateinit var historyView: HistoryView
```

(c) Find the view + construct the store. After `homeView = findViewById(R.id.homeView)`
add:
```kotlin
        historyView = findViewById(R.id.historyView)
```
And after `favorites = JsonFavoritesStore(File(filesDir, "favorites.json"))` add:
```kotlin
        history = JsonHistoryStore(File(filesDir, "history.json"))
```

(d) Find the chrome history button. After
`val homeButton = findViewById<ImageButton>(R.id.homeButton)` add:
```kotlin
        val historyButton = findViewById<ImageButton>(R.id.historyButton)
```

(e) Record on navigation. Replace the `webViewClient` line:
```kotlin
        webView.webViewClient = SniffingWebViewClient(sniffer) { url -> updateUrlText(url) }
```
with:
```kotlin
        webView.webViewClient = SniffingWebViewClient(sniffer) { url ->
            updateUrlText(url)
            recordHistory(url, null)
        }
```

(f) Record the real title. Replace the `chromeClient = BrowserWebChromeClient(...)` block:
```kotlin
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() }
        )
```
with (adds the `onTitle` arg):
```kotlin
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() },
            onTitle = { url, title -> recordHistory(url, title) }
        )
```

(g) Update the `homeView.bind(...)` call to pass `onHistory`, and bind `historyView`.
Replace the existing `homeView.bind(...)` block:
```kotlin
        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } }
        )
```
with:
```kotlin
        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } },
            onHistory = { showHistory() }
        )
        historyView.bind(
            repository = history,
            onOpen = { openUrl(it) },
            onClear = { history.clear(); historyView.refresh() },
            onAddFavorite = { entry ->
                favorites.add(Favorite(entry.title, entry.url))
                Toast.makeText(this, R.string.add_favorite, Toast.LENGTH_SHORT).show()
            }
        )
```

(h) Wire the chrome button. After `homeButton.setOnClickListener { showHome() }` add:
```kotlin
        historyButton.setOnClickListener {
            showHistory()
            chrome.onInteracted()
        }
```

(i) Hide the history overlay when opening a URL. Replace `openUrl`:
```kotlin
    private fun openUrl(url: String) {
        homeView.hide()
        layout.requestFocus()
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }
```
with:
```kotlin
    private fun openUrl(url: String) {
        homeView.hide()
        historyView.hide()
        layout.requestFocus()
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }
```

(j) Add the `showHistory` and `recordHistory` helpers. After the `showHome` function add:
```kotlin
    private fun showHistory() {
        historyView.show()
    }

    private fun recordHistory(url: String, title: String?) {
        if (!url.startsWith("http")) return
        val label = title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
        history.record(HistoryEntry(label, url, System.currentTimeMillis()))
    }
```

(k) Handle BACK from the history overlay. Replace `onBackPressed`:
```kotlin
    override fun onBackPressed() {
        if (homeView.visibility == View.VISIBLE) {
            super.onBackPressed()
        } else {
            showHome()
        }
    }
```
with:
```kotlin
    override fun onBackPressed() {
        when {
            historyView.visibility == View.VISIBLE -> {
                historyView.hide()
                showHome()
            }
            homeView.visibility == View.VISIBLE -> super.onBackPressed()
            else -> showHome()
        }
    }
```

- [ ] **Step 3: Build and run the full test suite**

Run: `./gradlew test assembleDebug`
Expected: BUILD SUCCESSFUL; all tests pass (HistoryOpsTest, HistoryJsonTest, RelativeTimeTest, plus the existing suite).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/kotlin/net/mrowser/MainActivity.kt
git commit -m "feat: wire history recording, overlay, and entry points into MainActivity"
```

---

## Manual verification (on device/emulator)

Not automated — sanity-check after Task 9:
- Visit 2-3 sites → open History from the home overlay → entries appear newest-first with real page titles and "Nm ago" times.
- Revisit a site → it jumps to the top, no duplicate row.
- Open History from the chrome bar (MENU → History icon) → same list.
- Click a row → loads that URL.
- Long-press a row → "Add to favorites" toast → favorite appears on home grid.
- "Clear all" → list empties; relaunch app → still empty (persisted).
- Browse 51+ distinct sites → history holds at 50.

---

## Notes / decisions

- **Recording timing:** `onPageStarted` (main-frame only) records the host as a placeholder; `onReceivedTitle` re-records with the real title. `HistoryOps.record` dedups by URL, so the title upgrade replaces the placeholder rather than adding a row.
- **Non-http URLs** (`about:blank`, `data:`, etc.) are skipped in `recordHistory`.
- **Back from History always returns to the home overlay** (per the approved spec), even if opened mid-browse.
- **Add-to-favorites toast** reuses the existing `R.string.add_favorite` ("Add to favorites") string.
