# Milestone C — Home & Favorites — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Netflix-style home screen with a persisted favorites grid and URL field, shown on launch, with add/edit/delete and clean home↔web navigation.

**Architecture:** Single activity. The root layout holds the existing web `CursorLayout` plus a `HomeView` overlay shown on launch. Favorites persist as JSON in `filesDir`; pure list/JSON logic (`FavoritesOps`, `FavoritesJson`) is unit-tested, the file store and views are build-verified. No new dependency — the grid uses the framework `GridLayout`.

**Tech Stack:** Kotlin, AGP 9.1.1 (built-in Kotlin), Gradle 9.3.1, JDK 17, classic Views, `org.json`, JUnit 4.13.2. Builds on A + B.

**Spec:** `docs/superpowers/specs/2026-06-18-milestone-c-home-and-favorites-design.md`

**Conventions:** Conventional one-line commits, no AI attribution. Absolute paths under `/Users/mohammad/Projects/mrowser`. Build with `/Users/mohammad/Projects/mrowser/gradlew`.

---

## File map

| File | Responsibility |
|------|----------------|
| `data/Favorite.kt` | Favorite data (title, url) |
| `data/FavoritesOps.kt` | Pure upsert/remove/update (tested) |
| `data/FavoritesJson.kt` | Pure JSON round-trip (tested) |
| `data/FavoritesRepository.kt` | Repository interface |
| `data/JsonFavoritesStore.kt` | File-backed repository |
| `home/HomeView.kt` | Home overlay: wordmark + URL pill + grid |
| `home/FavoriteDialog.kt` | Edit/delete dialog |
| `res/layout/home_view.xml` | Home content |
| `res/layout/favorite_card.xml` | One favorite card |
| `res/layout/activity_main.xml` | Root = CursorLayout + HomeView; chrome ★/Home (modify) |
| `res/drawable/card_bg.xml`, `letter_tile_bg.xml`, `url_pill_bg.xml` | Home drawables |
| `res/values/strings.xml` | Home/dialog strings (modify) |
| `MainActivity.kt` | Wire home + ★/Home + BACK (modify) |
| `test/.../data/*Test.kt` | Unit tests |

---

## Task 1: Favorite + FavoritesOps (TDD)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/Favorite.kt`, `app/src/main/kotlin/net/mrowser/data/FavoritesOps.kt`
- Test: `app/src/test/kotlin/net/mrowser/data/FavoritesOpsTest.kt`

- [ ] **Step 1: Write the data class**

Create `app/src/main/kotlin/net/mrowser/data/Favorite.kt`:

```kotlin
package net.mrowser.data

/** A saved site. Identity is the url. */
data class Favorite(val title: String, val url: String)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/data/FavoritesOpsTest.kt`:

```kotlin
package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesOpsTest {

    private val a = Favorite("A", "https://a.net")
    private val b = Favorite("B", "https://b.net")

    @Test fun `add puts a new favorite at the front`() {
        assertEquals(listOf(b, a), FavoritesOps.add(listOf(a), b))
    }

    @Test fun `add upserts by url without duplicating`() {
        val a2 = Favorite("A renamed", "https://a.net")
        assertEquals(listOf(a2), FavoritesOps.add(listOf(a), a2))
    }

    @Test fun `remove drops the matching url`() {
        assertEquals(listOf(a), FavoritesOps.remove(listOf(a, b), "https://b.net"))
    }

    @Test fun `update replaces the entry at the old url in place`() {
        val b2 = Favorite("B2", "https://b2.net")
        assertEquals(listOf(a, b2), FavoritesOps.update(listOf(a, b), "https://b.net", b2))
    }

    @Test fun `update is a no-op when the old url is absent`() {
        assertEquals(listOf(a), FavoritesOps.update(listOf(a), "https://x.net", b))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — `unresolved reference: FavoritesOps`.

- [ ] **Step 4: Write the implementation**

Create `app/src/main/kotlin/net/mrowser/data/FavoritesOps.kt`:

```kotlin
package net.mrowser.data

/** Pure list operations on favorites, keyed by url. */
object FavoritesOps {

    /** Upsert by url; the new/updated entry goes to the front. */
    fun add(list: List<Favorite>, favorite: Favorite): List<Favorite> =
        listOf(favorite) + list.filterNot { it.url == favorite.url }

    fun remove(list: List<Favorite>, url: String): List<Favorite> =
        list.filterNot { it.url == url }

    /** Replace the entry at oldUrl, keeping its position; no-op if absent. */
    fun update(list: List<Favorite>, oldUrl: String, favorite: Favorite): List<Favorite> =
        list.map { if (it.url == oldUrl) favorite else it }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/data/Favorite.kt app/src/main/kotlin/net/mrowser/data/FavoritesOps.kt app/src/test/kotlin/net/mrowser/data/FavoritesOpsTest.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add favorites list operations with tests"
```

---

## Task 2: FavoritesJson (TDD)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/FavoritesJson.kt`
- Test: `app/src/test/kotlin/net/mrowser/data/FavoritesJsonTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/data/FavoritesJsonTest.kt`:

```kotlin
package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesJsonTest {

    @Test fun `round trips a list`() {
        val items = listOf(Favorite("A", "https://a.net"), Favorite("B", "https://b.net"))
        assertEquals(items, FavoritesJson.fromJson(FavoritesJson.toJson(items)))
    }

    @Test fun `empty list round trips`() {
        assertEquals(emptyList<Favorite>(), FavoritesJson.fromJson(FavoritesJson.toJson(emptyList())))
    }

    @Test fun `blank input is an empty list`() {
        assertEquals(emptyList<Favorite>(), FavoritesJson.fromJson("   "))
    }

    @Test fun `corrupt input is an empty list`() {
        assertEquals(emptyList<Favorite>(), FavoritesJson.fromJson("not json"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — `unresolved reference: FavoritesJson`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/kotlin/net/mrowser/data/FavoritesJson.kt`:

```kotlin
package net.mrowser.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure JSON (de)serialization of the favorites list. */
object FavoritesJson {

    fun toJson(items: List<Favorite>): String {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
        return arr.toString()
    }

    fun fromJson(json: String): List<Favorite> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Favorite(o.getString("title"), o.getString("url"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/data/FavoritesJson.kt app/src/test/kotlin/net/mrowser/data/FavoritesJsonTest.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add favorites json serialization with tests"
```

---

## Task 3: FavoritesRepository + JsonFavoritesStore

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/data/FavoritesRepository.kt`, `app/src/main/kotlin/net/mrowser/data/JsonFavoritesStore.kt`

- [ ] **Step 1: Write the interface**

Create `app/src/main/kotlin/net/mrowser/data/FavoritesRepository.kt`:

```kotlin
package net.mrowser.data

interface FavoritesRepository {
    fun findAll(): List<Favorite>
    fun add(favorite: Favorite)
    fun remove(url: String)
    fun update(oldUrl: String, favorite: Favorite)
}
```

- [ ] **Step 2: Write the file-backed store**

Create `app/src/main/kotlin/net/mrowser/data/JsonFavoritesStore.kt`:

```kotlin
package net.mrowser.data

import java.io.File

/** FavoritesRepository backed by a JSON file; pure logic delegated to FavoritesOps/FavoritesJson. */
class JsonFavoritesStore(private val file: File) : FavoritesRepository {

    private var items: List<Favorite> =
        if (file.exists()) FavoritesJson.fromJson(file.readText()) else emptyList()

    override fun findAll(): List<Favorite> = items

    override fun add(favorite: Favorite) {
        items = FavoritesOps.add(items, favorite); persist()
    }

    override fun remove(url: String) {
        items = FavoritesOps.remove(items, url); persist()
    }

    override fun update(oldUrl: String, favorite: Favorite) {
        items = FavoritesOps.update(items, oldUrl, favorite); persist()
    }

    private fun persist() {
        runCatching { file.writeText(FavoritesJson.toJson(items)) }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/data/FavoritesRepository.kt app/src/main/kotlin/net/mrowser/data/JsonFavoritesStore.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add json favorites store"
```

---

## Task 4: Home layouts, drawables, strings

**Files:**
- Create: `app/src/main/res/drawable/card_bg.xml`, `letter_tile_bg.xml`, `url_pill_bg.xml`, `app/src/main/res/layout/home_view.xml`, `app/src/main/res/layout/favorite_card.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

Replace `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">mrowser</string>
    <string name="url_hint">Enter address</string>
    <string name="go">Go</string>
    <string name="back">Back</string>
    <string name="reload">Reload</string>
    <string name="play_synced">▶ Play synced</string>
    <string name="home">Home</string>
    <string name="add_favorite">Add to favorites</string>
    <string name="favorites_empty">Add a site — press ★ while browsing, or type a URL above.</string>
    <string name="edit_favorite">Edit favorite</string>
    <string name="title_hint">Title</string>
    <string name="save">Save</string>
    <string name="delete">Delete</string>
    <string name="cancel">Cancel</string>
</resources>
```

- [ ] **Step 2: Add the card background (focusable)**

Create `app/src/main/res/drawable/card_bg.xml`:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#1F1F1F" />
            <stroke android:width="3dp" android:color="@color/accent" />
            <corners android:radius="10dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#1A1A1A" />
            <corners android:radius="10dp" />
        </shape>
    </item>
</selector>
```

- [ ] **Step 3: Add the letter tile + URL pill backgrounds**

Create `app/src/main/res/drawable/letter_tile_bg.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FFFFFF" />
    <corners android:radius="8dp" />
</shape>
```

Create `app/src/main/res/drawable/url_pill_bg.xml`:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#262626" />
            <stroke android:width="2dp" android:color="@color/accent" />
            <corners android:radius="24dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#1A1A1A" />
            <corners android:radius="24dp" />
        </shape>
    </item>
</selector>
```

- [ ] **Step 4: Add the home layout**

Create `app/src/main/res/layout/home_view.xml`:

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
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/app_name"
            android:textColor="@color/on_surface"
            android:textSize="28sp"
            android:textStyle="bold"
            android:paddingEnd="24dp" />

        <EditText
            android:id="@+id/homeUrlInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:background="@drawable/url_pill_bg"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:paddingTop="12dp"
            android:paddingBottom="12dp"
            android:hint="@string/url_hint"
            android:imeOptions="actionGo"
            android:inputType="textUri"
            android:textColor="@color/on_surface"
            android:textColorHint="@color/hint" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipChildren="false"
        android:clipToPadding="false"
        android:fillViewport="true">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:clipChildren="false">

            <GridLayout
                android:id="@+id/favoritesGrid"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:clipChildren="false"
                android:columnCount="4" />

            <TextView
                android:id="@+id/emptyHint"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:gravity="center"
                android:paddingTop="80dp"
                android:text="@string/favorites_empty"
                android:textColor="@color/hint"
                android:textSize="18sp"
                android:visibility="gone" />
        </FrameLayout>
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 5: Add the card layout**

Create `app/src/main/res/layout/favorite_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="150dp"
    android:layout_height="wrap_content"
    android:layout_margin="10dp"
    android:orientation="vertical"
    android:background="@drawable/card_bg"
    android:padding="12dp"
    android:focusable="true">

    <TextView
        android:id="@+id/cardLetter"
        android:layout_width="match_parent"
        android:layout_height="84dp"
        android:background="@drawable/letter_tile_bg"
        android:gravity="center"
        android:text="M"
        android:textColor="#141414"
        android:textSize="36sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/cardTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingTop="8dp"
        android:maxLines="1"
        android:ellipsize="end"
        android:text="@string/app_name"
        android:textColor="@color/on_surface"
        android:textSize="14sp" />
</LinearLayout>
```

- [ ] **Step 6: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/res/
git -C /Users/mohammad/Projects/mrowser commit -m "style: add home and favorite-card layouts and drawables"
```

---

## Task 5: HomeView

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/home/HomeView.kt`

- [ ] **Step 1: Write the home view**

Create `app/src/main/kotlin/net/mrowser/home/HomeView.kt`:

```kotlin
package net.mrowser.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.data.Favorite
import net.mrowser.data.FavoritesRepository
import net.mrowser.web.UrlNormalizer

/** Home overlay: wordmark + URL pill + favorites grid. */
class HomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val grid: GridLayout
    private val emptyHint: TextView
    private val urlInput: EditText

    private var repository: FavoritesRepository? = null
    private var onOpen: (Favorite) -> Unit = {}
    private var onSubmitUrl: (String) -> Unit = {}
    private var onEdit: (Favorite) -> Unit = {}

    init {
        LayoutInflater.from(context).inflate(R.layout.home_view, this, true)
        grid = findViewById(R.id.favoritesGrid)
        emptyHint = findViewById(R.id.emptyHint)
        urlInput = findViewById(R.id.homeUrlInput)
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                UrlNormalizer.normalize(urlInput.text.toString())?.let { onSubmitUrl(it) }
                true
            } else {
                false
            }
        }
    }

    fun bind(
        repository: FavoritesRepository,
        onOpen: (Favorite) -> Unit,
        onSubmitUrl: (String) -> Unit,
        onEdit: (Favorite) -> Unit
    ) {
        this.repository = repository
        this.onOpen = onOpen
        this.onSubmitUrl = onSubmitUrl
        this.onEdit = onEdit
    }

    fun show() {
        visibility = View.VISIBLE
        refresh()
        urlInput.requestFocus()
    }

    fun hide() {
        visibility = View.GONE
    }

    fun refresh() {
        val items = repository?.findAll().orEmpty()
        grid.removeAllViews()
        emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { grid.addView(card(it)) }
    }

    private fun card(fav: Favorite): View {
        val v = LayoutInflater.from(context).inflate(R.layout.favorite_card, grid, false)
        val letter = v.findViewById<TextView>(R.id.cardLetter)
        val title = v.findViewById<TextView>(R.id.cardTitle)
        letter.text = fav.title.trim().take(1).uppercase().ifEmpty { "•" }
        letter.backgroundTintList = ColorStateList.valueOf(colorFor(fav.url))
        title.text = fav.title.ifBlank { fav.url }
        v.setOnClickListener { onOpen(fav) }
        v.setOnLongClickListener { onEdit(fav); true }
        v.setOnFocusChangeListener { card, hasFocus ->
            val s = if (hasFocus) 1.1f else 1f
            card.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        return v
    }

    /** Stable pleasant color derived from the url. */
    private fun colorFor(url: String): Int {
        val hue = ((url.hashCode() % 360) + 360) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.80f))
    }
}
```

- [ ] **Step 2: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/home/HomeView.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add home view with favorites grid"
```

---

## Task 6: FavoriteDialog (edit / delete)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/home/FavoriteDialog.kt`

- [ ] **Step 1: Write the dialog**

Create `app/src/main/kotlin/net/mrowser/home/FavoriteDialog.kt`:

```kotlin
package net.mrowser.home

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import net.mrowser.R
import net.mrowser.data.Favorite
import net.mrowser.data.FavoritesRepository

/** Edit (title + url) or delete a favorite, then invoke onChanged. */
object FavoriteDialog {

    fun show(context: Context, repository: FavoritesRepository, fav: Favorite, onChanged: () -> Unit) {
        val titleField = EditText(context).apply {
            setText(fav.title); hint = context.getString(R.string.title_hint)
        }
        val urlField = EditText(context).apply {
            setText(fav.url); hint = context.getString(R.string.url_hint)
        }
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(titleField)
            addView(urlField)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.edit_favorite)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                repository.update(fav.url, Favorite(titleField.text.toString(), urlField.text.toString()))
                onChanged()
            }
            .setNegativeButton(R.string.delete) { _, _ ->
                repository.remove(fav.url)
                onChanged()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
}
```

- [ ] **Step 2: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/home/FavoriteDialog.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add favorite edit and delete dialog"
```

---

## Task 7: Wire home + favorites into the browser

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`, `app/src/main/kotlin/net/mrowser/MainActivity.kt`

- [ ] **Step 1: Restructure the layout (root FrameLayout + HomeView, chrome ★/Home)**

Replace `app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/surface">

    <net.mrowser.web.CursorLayout
        android:id="@+id/cursorLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <WebView
            android:id="@+id/webView"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <LinearLayout
            android:id="@+id/chromeBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:background="@drawable/bar_background"
            android:padding="10dp"
            android:visibility="gone">

            <ImageButton
                android:id="@+id/backButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="@drawable/focus_ring"
                android:src="@android:drawable/ic_media_previous"
                android:contentDescription="@string/back" />

            <ImageButton
                android:id="@+id/homeButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:background="@drawable/focus_ring"
                android:src="@android:drawable/ic_menu_revert"
                android:contentDescription="@string/home" />

            <EditText
                android:id="@+id/urlInput"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginStart="8dp"
                android:layout_marginEnd="8dp"
                android:background="@drawable/focus_ring"
                android:padding="8dp"
                android:hint="@string/url_hint"
                android:imeOptions="actionGo"
                android:inputType="textUri"
                android:textColor="@color/on_surface"
                android:textColorHint="@color/hint" />

            <ImageButton
                android:id="@+id/favoriteButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="8dp"
                android:background="@drawable/focus_ring"
                android:src="@android:drawable/btn_star_big_on"
                android:contentDescription="@string/add_favorite" />

            <ImageButton
                android:id="@+id/reloadButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="@drawable/focus_ring"
                android:src="@android:drawable/ic_menu_rotate"
                android:contentDescription="@string/reload" />
        </LinearLayout>

        <TextView
            android:id="@+id/playChip"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|center_horizontal"
            android:layout_marginBottom="36dp"
            android:background="@drawable/play_chip_bg"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:paddingTop="12dp"
            android:paddingBottom="12dp"
            android:text="@string/play_synced"
            android:textColor="@color/on_surface"
            android:textSize="18sp"
            android:visibility="gone" />
    </net.mrowser.web.CursorLayout>

    <net.mrowser.home.HomeView
        android:id="@+id/homeView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
```

- [ ] **Step 2: Replace MainActivity**

Replace `app/src/main/kotlin/net/mrowser/MainActivity.kt`:

```kotlin
package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import java.io.File
import net.mrowser.data.Favorite
import net.mrowser.data.JsonFavoritesStore
import net.mrowser.handoff.HandoffController
import net.mrowser.home.FavoriteDialog
import net.mrowser.home.HomeView
import net.mrowser.stream.SniffingWebViewClient
import net.mrowser.stream.StreamSniffer
import net.mrowser.web.BrowserWebChromeClient
import net.mrowser.web.ChromeController
import net.mrowser.web.CursorController
import net.mrowser.web.CursorLayout
import net.mrowser.web.UrlNormalizer

class MainActivity : Activity() {

    private lateinit var layout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var chrome: ChromeController
    private lateinit var chromeClient: BrowserWebChromeClient
    private lateinit var sniffer: StreamSniffer
    private lateinit var playChip: TextView
    private lateinit var homeView: HomeView
    private lateinit var favorites: JsonFavoritesStore

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.cursorLayout)
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        playChip = findViewById(R.id.playChip)
        homeView = findViewById(R.id.homeView)
        val bar = findViewById<View>(R.id.chromeBar)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reloadButton = findViewById<ImageButton>(R.id.reloadButton)
        val favoriteButton = findViewById<ImageButton>(R.id.favoriteButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)

        favorites = JsonFavoritesStore(File(filesDir, "favorites.json"))

        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = { runOnUiThread { playChip.visibility = View.VISIBLE } },
            onCleared = { playChip.visibility = View.GONE }
        )
        val handoff = HandoffController(this, sniffer)

        webView.webViewClient = SniffingWebViewClient(sniffer)
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() }
        )
        webView.webChromeClient = chromeClient
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        chrome = ChromeController(bar, urlInput, webView)
        val cursor = CursorController(webView) { layout.invalidate() }
        layout.webView = webView
        layout.cursor = cursor
        layout.chrome = chrome
        layout.playChip = playChip
        layout.onChipClick = { handoff.play() }
        layout.onBack = { chromeClient.exitIfFullscreen() }

        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } }
        )

        layout.post { cursor.center(webView.width, webView.height) }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
            chrome.onInteracted()
        }
        reloadButton.setOnClickListener {
            webView.reload()
            chrome.onInteracted()
        }
        homeButton.setOnClickListener { showHome() }
        favoriteButton.setOnClickListener {
            addCurrentToFavorites()
            chrome.onInteracted()
        }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                UrlNormalizer.normalize(urlInput.text.toString())?.let { openUrl(it) }
                true
            } else {
                false
            }
        }

        showHome()
    }

    private fun openUrl(url: String) {
        homeView.hide()
        layout.requestFocus()
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }

    private fun showHome() {
        homeView.show()
    }

    private fun addCurrentToFavorites() {
        val url = webView.url ?: return
        val title = webView.title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
        favorites.add(Favorite(title, url))
        Toast.makeText(this, R.string.add_favorite, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (homeView.visibility == View.VISIBLE) {
            super.onBackPressed()
        } else {
            showHome()
        }
    }
}
```

- [ ] **Step 3: Run unit tests + build the signed APK**

Run: `/Users/mohammad/Projects/mrowser/gradlew test assembleRelease`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 4: Manual test on the box**

Install the signed APK on the Sunyia box. Verify:
- On launch the **home screen** shows (wordmark + URL field + empty-state hint).
- Type a URL in the home field → Go → the site loads (home hides, cursor active).
- Browse to the Sunyia site, press the **★** button in the chrome bar → a favorite is added.
- Press the **Home** button → home shows with the new card; the card has a colored letter tile + title.
- D-pad moves focus between cards; the focused card **grows + red border**.
- OK on a card opens it. Long-press OK on a card → edit/delete dialog; delete removes it, edit renames it.
- BACK from a page with no history returns to home; BACK on home exits the app.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/res/layout/activity_main.xml app/src/main/kotlin/net/mrowser/MainActivity.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: wire home screen and favorites into browser"
```

---

## Self-review (completed by plan author)

- **Spec coverage:** §3 visual → Task 4 (drawables/layouts) + Task 5 (focus scale, letter color). §4.1 Favorite → Task 1. §4.2 FavoritesJson → Task 2. §4.3 FavoritesRepository + §4.4 JsonFavoritesStore → Task 3 (pure ops in Task 1). §4.5 HomeView → Task 5. §4.6 FavoriteDialog → Task 6. §4.7 MainActivity wiring + root layout + ★/Home + BACK → Task 7. §7 testing → Tasks 1, 2 (unit) + Task 7 Step 4 (manual). No gaps.
- **Placeholder scan:** none.
- **Type consistency:** `Favorite(title, url)` consistent (Tasks 1, 2, 3, 5, 7). `FavoritesOps.add/remove/update` consistent (Tasks 1, 3). `FavoritesJson.toJson/fromJson` (Tasks 2, 3). `FavoritesRepository` methods `findAll/add/remove/update` consistent (Tasks 3, 5, 6, 7). `HomeView.bind(repository, onOpen, onSubmitUrl, onEdit)/show/hide/refresh` consistent (Tasks 5, 7). `FavoriteDialog.show(context, repository, fav, onChanged)` consistent (Tasks 6, 7). View IDs (`cursorLayout`, `webView`, `chromeBar`, `urlInput`, `playChip`, `homeView`, `backButton`, `reloadButton`, `favoriteButton`, `homeButton`, `favoritesGrid`, `emptyHint`, `homeUrlInput`, `cardLetter`, `cardTitle`) match layouts and code. Existing `CursorLayout.playChip/onChipClick/onBack/webView/cursor/chrome` reused unchanged.
```
