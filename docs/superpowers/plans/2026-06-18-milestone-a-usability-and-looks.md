# Milestone A — Usability & Looks — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a D-pad mouse cursor, fullscreen video with an auto-hiding address bar, and a Netflix-style dark red restyle to the existing baseline browser.

**Architecture:** One Activity hosts a custom `CursorLayout` (FrameLayout) over the WebView. Pure logic (`CursorGeometry`, `ChromeVisibility`) is split out and unit-tested; Android-coupled controllers (`CursorController`, `ChromeController`, `BrowserWebChromeClient`) are build-verified and tested on-device. `MainActivity` is refactored to wire them together and reuses `UrlNormalizer`.

**Tech Stack:** Kotlin, AGP 9.1.1 (built-in Kotlin), Gradle 9.3.1, JDK 17, classic Android Views, JUnit 4.13.2. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-06-18-milestone-a-usability-and-looks-design.md`

**Conventions:** Conventional one-line commits, no AI attribution. Absolute paths under `/Users/mohammad/Projects/mrowser`. Build with `/Users/mohammad/Projects/mrowser/gradlew`.

---

## File map

| File | Responsibility |
|------|----------------|
| `res/values/colors.xml` | Netflix dark palette |
| `res/values/themes.xml` | Restyled app theme (modify) |
| `res/values/strings.xml` | Add `back`, `reload` strings (modify) |
| `res/drawable/focus_ring.xml` | Red focus state-list for controls |
| `res/drawable/bar_background.xml` | Frosted address-bar background |
| `web/CursorGeometry.kt` | Pure cursor math (unit-tested) |
| `web/ChromeVisibility.kt` | Pure bar-visibility reducer (unit-tested) |
| `web/ChromeController.kt` | Bar animation, idle timer, focus |
| `web/CursorController.kt` | Cursor state + MotionEvent synthesis + edge scroll |
| `web/BrowserWebChromeClient.kt` | HTML5 fullscreen video |
| `web/CursorLayout.kt` | FrameLayout host, D-pad routing, cursor draw |
| `res/layout/activity_main.xml` | CursorLayout root + chrome bar overlay (replace) |
| `MainActivity.kt` | Wire controllers + WebView (refactor) |
| `test/.../CursorGeometryTest.kt`, `ChromeVisibilityTest.kt` | Unit tests |

The cursor is painted programmatically in `CursorLayout.dispatchDraw` (no cursor drawable needed).

---

## Task 1: Netflix dark restyle

**Files:**
- Create: `app/src/main/res/values/colors.xml`, `app/src/main/res/drawable/focus_ring.xml`, `app/src/main/res/drawable/bar_background.xml`
- Modify: `app/src/main/res/values/themes.xml`, `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add colors**

Create `app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="surface">#FF141414</color>
    <color name="accent">#FFE50914</color>
    <color name="on_surface">#FFFFFFFF</color>
    <color name="hint">#FF8C8C8C</color>
    <color name="bar_frost">#CC1A1A1A</color>
    <color name="cursor_outline">#CCFFFFFF</color>
</resources>
```

- [ ] **Step 2: Restyle the theme**

Replace `app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.Mrowser" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/surface</item>
        <item name="android:colorAccent">@color/accent</item>
        <item name="android:colorControlActivated">@color/accent</item>
        <item name="android:textColorPrimary">@color/on_surface</item>
        <item name="android:textColorHint">@color/hint</item>
    </style>
</resources>
```

- [ ] **Step 3: Add control strings**

Replace `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">mrowser</string>
    <string name="url_hint">Enter address</string>
    <string name="go">Go</string>
    <string name="back">Back</string>
    <string name="reload">Reload</string>
</resources>
```

- [ ] **Step 4: Add the focus ring drawable**

Create `app/src/main/res/drawable/focus_ring.xml`:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#22FFFFFF" />
            <stroke android:width="2dp" android:color="@color/accent" />
            <corners android:radius="6dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#11FFFFFF" />
            <corners android:radius="6dp" />
        </shape>
    </item>
</selector>
```

- [ ] **Step 5: Add the bar background drawable**

Create `app/src/main/res/drawable/bar_background.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/bar_frost" />
</shape>
```

- [ ] **Step 6: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (old activity still builds; new resources unused yet).

- [ ] **Step 7: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add -A
git -C /Users/mohammad/Projects/mrowser commit -m "style: add netflix dark theme, colors, and focus drawables"
```

---

## Task 2: CursorGeometry (TDD)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/CursorGeometry.kt`
- Test: `app/src/test/kotlin/net/mrowser/web/CursorGeometryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/web/CursorGeometryTest.kt`:

```kotlin
package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorGeometryTest {

    @Test fun `clamp keeps a point inside bounds`() {
        assertEquals(CursorGeometry.Point(5f, 5f), CursorGeometry.clamp(5f, 5f, 100, 100))
    }

    @Test fun `clamp pulls a negative point to zero`() {
        assertEquals(CursorGeometry.Point(0f, 0f), CursorGeometry.clamp(-9f, -9f, 100, 100))
    }

    @Test fun `clamp pulls an overflowing point to the edge`() {
        assertEquals(CursorGeometry.Point(100f, 100f), CursorGeometry.clamp(999f, 999f, 100, 100))
    }

    @Test fun `step moves by speed times direction and clamps`() {
        val p = CursorGeometry.step(CursorGeometry.Point(50f, 50f), 1, -1, 10f, 100, 100)
        assertEquals(CursorGeometry.Point(60f, 40f), p)
    }

    @Test fun `speed is base at zero hold`() {
        assertEquals(CursorGeometry.BASE_SPEED_PX, CursorGeometry.speedForHoldMs(0L), 0.001f)
    }

    @Test fun `speed is max once accel window elapses`() {
        assertEquals(CursorGeometry.MAX_SPEED_PX, CursorGeometry.speedForHoldMs(CursorGeometry.ACCEL_MS), 0.001f)
    }

    @Test fun `speed ramps linearly at the midpoint`() {
        val mid = CursorGeometry.speedForHoldMs(CursorGeometry.ACCEL_MS / 2)
        assertEquals(26f, mid, 0.5f)
    }

    @Test fun `edge detection flags top and bottom zones`() {
        assertTrue(CursorGeometry.isAtTopEdge(10f, 48f))
        assertFalse(CursorGeometry.isAtTopEdge(60f, 48f))
        assertTrue(CursorGeometry.isAtBottomEdge(960f, 1000, 48f))
        assertFalse(CursorGeometry.isAtBottomEdge(900f, 1000, 48f))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — compile error `unresolved reference: CursorGeometry`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/kotlin/net/mrowser/web/CursorGeometry.kt`:

```kotlin
package net.mrowser.web

/** Pure cursor math: no Android types, fully unit-testable. */
object CursorGeometry {

    const val BASE_SPEED_PX = 12f
    const val MAX_SPEED_PX = 40f
    const val ACCEL_MS = 600L

    data class Point(val x: Float, val y: Float)

    fun clamp(x: Float, y: Float, width: Int, height: Int): Point =
        Point(x.coerceIn(0f, width.toFloat()), y.coerceIn(0f, height.toFloat()))

    fun step(current: Point, dirX: Int, dirY: Int, speedPx: Float, width: Int, height: Int): Point =
        clamp(current.x + dirX * speedPx, current.y + dirY * speedPx, width, height)

    /** Ramps base -> max linearly over ACCEL_MS, then holds at max. */
    fun speedForHoldMs(heldMs: Long): Float {
        if (heldMs <= 0L) return BASE_SPEED_PX
        if (heldMs >= ACCEL_MS) return MAX_SPEED_PX
        val t = heldMs.toFloat() / ACCEL_MS
        return BASE_SPEED_PX + (MAX_SPEED_PX - BASE_SPEED_PX) * t
    }

    fun isAtTopEdge(y: Float, zonePx: Float): Boolean = y <= zonePx

    fun isAtBottomEdge(y: Float, height: Int, zonePx: Float): Boolean = y >= height - zonePx
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL`; CursorGeometry tests pass.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/web/CursorGeometry.kt app/src/test/kotlin/net/mrowser/web/CursorGeometryTest.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add cursor geometry with tests"
```

---

## Task 3: ChromeVisibility (TDD)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/ChromeVisibility.kt`
- Test: `app/src/test/kotlin/net/mrowser/web/ChromeVisibilityTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/web/ChromeVisibilityTest.kt`:

```kotlin
package net.mrowser.web

import net.mrowser.web.ChromeVisibility.Event
import net.mrowser.web.ChromeVisibility.State
import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeVisibilityTest {

    @Test fun `reveal at top shows the bar`() {
        assertEquals(State.VISIBLE, ChromeVisibility.reduce(State.HIDDEN, Event.RevealRequested(atTop = true)))
    }

    @Test fun `reveal away from top keeps it hidden`() {
        assertEquals(State.HIDDEN, ChromeVisibility.reduce(State.HIDDEN, Event.RevealRequested(atTop = false)))
    }

    @Test fun `idle hides a visible bar`() {
        assertEquals(State.HIDDEN, ChromeVisibility.reduce(State.VISIBLE, Event.IdleElapsed))
    }

    @Test fun `page interaction hides a visible bar`() {
        assertEquals(State.HIDDEN, ChromeVisibility.reduce(State.VISIBLE, Event.PageInteracted))
    }

    @Test fun `interaction keeps a visible bar visible`() {
        assertEquals(State.VISIBLE, ChromeVisibility.reduce(State.VISIBLE, Event.Interacted))
    }

    @Test fun `events other than reveal do nothing while hidden`() {
        assertEquals(State.HIDDEN, ChromeVisibility.reduce(State.HIDDEN, Event.IdleElapsed))
        assertEquals(State.HIDDEN, ChromeVisibility.reduce(State.HIDDEN, Event.Interacted))
        assertEquals(State.HIDDEN, ChromeVisibility.reduce(State.HIDDEN, Event.PageInteracted))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — compile error `unresolved reference: ChromeVisibility`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/kotlin/net/mrowser/web/ChromeVisibility.kt`:

```kotlin
package net.mrowser.web

/** Pure visibility reducer for the address-bar overlay. */
object ChromeVisibility {

    enum class State { HIDDEN, VISIBLE }

    sealed interface Event {
        data class RevealRequested(val atTop: Boolean) : Event
        data object Interacted : Event
        data object PageInteracted : Event
        data object IdleElapsed : Event
    }

    fun reduce(state: State, event: Event): State = when (state) {
        State.HIDDEN -> when (event) {
            is Event.RevealRequested -> if (event.atTop) State.VISIBLE else State.HIDDEN
            else -> State.HIDDEN
        }
        State.VISIBLE -> when (event) {
            is Event.PageInteracted, is Event.IdleElapsed -> State.HIDDEN
            is Event.Interacted -> State.VISIBLE
            is Event.RevealRequested -> State.VISIBLE
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL`; ChromeVisibility tests pass.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/web/ChromeVisibility.kt app/src/test/kotlin/net/mrowser/web/ChromeVisibilityTest.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add chrome visibility reducer with tests"
```

---

## Task 4: ChromeController

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/ChromeController.kt`

- [ ] **Step 1: Write the controller**

Create `app/src/main/kotlin/net/mrowser/web/ChromeController.kt`:

```kotlin
package net.mrowser.web

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText

/**
 * Drives the address-bar overlay: visibility state, slide/fade animation,
 * idle auto-hide, and focus handoff. Visibility decisions delegate to ChromeVisibility.
 */
class ChromeController(
    private val bar: View,
    private val urlInput: EditText,
    private val webView: View
) {
    private val handler = Handler(Looper.getMainLooper())
    private var state = ChromeVisibility.State.HIDDEN
    private val hideRunnable = Runnable { dispatch(ChromeVisibility.Event.IdleElapsed) }

    companion object { const val IDLE_MS = 4000L }

    val isVisible: Boolean get() = state == ChromeVisibility.State.VISIBLE

    fun requestReveal(atTop: Boolean) = dispatch(ChromeVisibility.Event.RevealRequested(atTop))
    fun onInteracted() = dispatch(ChromeVisibility.Event.Interacted)
    fun onPageInteracted() = dispatch(ChromeVisibility.Event.PageInteracted)

    private fun dispatch(event: ChromeVisibility.Event) {
        val next = ChromeVisibility.reduce(state, event)
        if (next != state) {
            state = next
            if (next == ChromeVisibility.State.VISIBLE) animateIn() else animateOut()
        }
        if (state == ChromeVisibility.State.VISIBLE) armIdle()
    }

    private fun armIdle() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, IDLE_MS)
    }

    private fun animateIn() {
        bar.visibility = View.VISIBLE
        bar.translationY = -bar.height.toFloat()
        bar.alpha = 0f
        bar.animate().translationY(0f).alpha(1f).setDuration(180).start()
        urlInput.requestFocus()
    }

    private fun animateOut() {
        handler.removeCallbacks(hideRunnable)
        bar.animate().translationY(-bar.height.toFloat()).alpha(0f).setDuration(160)
            .withEndAction { bar.visibility = View.GONE }.start()
        webView.requestFocus()
    }
}
```

- [ ] **Step 2: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (class compiles; not wired in yet).

- [ ] **Step 3: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/web/ChromeController.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add chrome controller for auto-hiding address bar"
```

---

## Task 5: CursorController

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/CursorController.kt`

Note: edge auto-scroll uses `webView.scrollBy` (page-level scroll) for the MVP — simple and reliable for the common case. If the target site puts video controls inside an inner scroll container that this misses, switch to a synthesized vertical drag (verified on-device); not built until proven necessary.

- [ ] **Step 1: Write the controller**

Create `app/src/main/kotlin/net/mrowser/web/CursorController.kt`:

```kotlin
package net.mrowser.web

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView

/**
 * Cursor state + input synthesis. Moves a virtual pointer with the D-pad and
 * translates OK / edge proximity into MotionEvents dispatched to the WebView.
 * Pure geometry lives in CursorGeometry.
 */
class CursorController(
    private val webView: WebView,
    private val invalidate: () -> Unit
) {
    enum class Mode { CURSOR, FOCUS }

    var mode = Mode.CURSOR
        private set

    var x = 0f
        private set
    var y = 0f
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var dirX = 0
    private var dirY = 0
    private var holdStart = 0L
    private val edgeZonePx get() = webView.resources.displayMetrics.density * 48f

    private val mover = object : Runnable {
        override fun run() {
            if (dirX == 0 && dirY == 0) return
            val held = SystemClock.uptimeMillis() - holdStart
            val speed = CursorGeometry.speedForHoldMs(held)
            val p = CursorGeometry.step(
                CursorGeometry.Point(x, y), dirX, dirY, speed, webView.width, webView.height
            )
            x = p.x; y = p.y
            if (dirY < 0 && CursorGeometry.isAtTopEdge(y, edgeZonePx)) webView.scrollBy(0, -SCROLL_STEP_PX)
            if (dirY > 0 && CursorGeometry.isAtBottomEdge(y, webView.height, edgeZonePx)) webView.scrollBy(0, SCROLL_STEP_PX)
            invalidate()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    fun center(width: Int, height: Int) {
        x = width / 2f; y = height / 2f; invalidate()
    }

    /** @return true if UP was pressed while the cursor is pinned at the top and the page is already scrolled to top. */
    fun startMove(dx: Int, dy: Int): Boolean {
        val pinnedTop = dy < 0 && y <= 0f && webView.scrollY == 0
        if (pinnedTop) return true
        if (dirX == dx && dirY == dy) return false
        dirX = dx; dirY = dy; holdStart = SystemClock.uptimeMillis()
        handler.removeCallbacks(mover); handler.post(mover)
        return false
    }

    fun stopMove() {
        dirX = 0; dirY = 0; handler.removeCallbacks(mover)
    }

    fun tap() {
        val t = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_HOVER_MOVE, t, t)
        dispatch(MotionEvent.ACTION_DOWN, t, t)
        dispatch(MotionEvent.ACTION_UP, t, t + 1)
    }

    fun toggleMode() {
        mode = if (mode == Mode.CURSOR) Mode.FOCUS else Mode.CURSOR
        stopMove()
        invalidate()
    }

    private fun dispatch(action: Int, down: Long, event: Long) {
        val e = MotionEvent.obtain(down, event, action, x, y, 0)
        if (action == MotionEvent.ACTION_HOVER_MOVE) webView.dispatchGenericMotionEvent(e)
        else webView.dispatchTouchEvent(e)
        e.recycle()
    }

    companion object {
        const val FRAME_MS = 16L
        const val SCROLL_STEP_PX = 24
    }
}
```

- [ ] **Step 2: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/web/CursorController.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add cursor controller with motionevent synthesis"
```

---

## Task 6: BrowserWebChromeClient (fullscreen video)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt`

- [ ] **Step 1: Write the client**

Create `app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt`:

```kotlin
package net.mrowser.web

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient

/**
 * Handles HTML5 fullscreen video: swaps the player to a fullscreen view over
 * everything, hides chrome + cursor, keeps the screen on, and restores on exit.
 */
class BrowserWebChromeClient(
    private val activity: Activity,
    private val container: ViewGroup,
    private val onEnter: () -> Unit,
    private val onExit: () -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var callback: CustomViewCallback? = null

    val isFullscreen: Boolean get() = customView != null

    override fun onShowCustomView(view: View, cb: CustomViewCallback) {
        if (customView != null) {
            cb.onCustomViewHidden()
            return
        }
        customView = view
        callback = cb
        container.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onEnter()
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        container.removeView(view)
        customView = null
        callback?.onCustomViewHidden()
        callback = null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onExit()
    }

    /** @return true if a fullscreen view was active and is now dismissed. */
    fun exitIfFullscreen(): Boolean {
        if (customView == null) return false
        onHideCustomView()
        return true
    }
}
```

- [ ] **Step 2: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/web/BrowserWebChromeClient.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add fullscreen video webchromeclient"
```

---

## Task 7: CursorLayout (host + D-pad routing)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/CursorLayout.kt`

- [ ] **Step 1: Write the layout**

Create `app/src/main/kotlin/net/mrowser/web/CursorLayout.kt`:

```kotlin
package net.mrowser.web

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Hosts the WebView + chrome bar, routes D-pad input to the cursor / chrome,
 * and paints the cursor. See the control table in the Milestone A spec.
 */
class CursorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    lateinit var webView: WebView
    lateinit var cursor: CursorController
    lateinit var chrome: ChromeController

    /** Set by the Activity; returns true if it consumed BACK (e.g. exited fullscreen). */
    var onBack: () -> Boolean = { false }

    private val density = resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E50914") }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#CCFFFFFF")
    }

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var okDown = false
    private var longPressed = false
    private val longPress = Runnable { longPressed = true; cursor.toggleMode() }

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) return handleBack()
            return true
        }
        // A visible chrome bar gets keys normally (its controls have focus).
        if (chrome.isVisible) return super.dispatchKeyEvent(event)

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> return handleOk(event)
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (cursor.mode == CursorController.Mode.CURSOR) return handleDpad(event)
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleDpad(event: KeyEvent): Boolean {
        val (dx, dy) = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            else -> 1 to 0
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (cursor.startMove(dx, dy)) chrome.requestReveal(atTop = true)
            KeyEvent.ACTION_UP -> cursor.stopMove()
        }
        return true
    }

    private fun handleOk(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!okDown) {
                okDown = true
                longPressed = false
                longPressHandler.postDelayed(longPress, 500)
            }
            KeyEvent.ACTION_UP -> {
                okDown = false
                longPressHandler.removeCallbacks(longPress)
                if (!longPressed) cursor.tap()
            }
        }
        return true
    }

    private fun handleBack(): Boolean {
        if (onBack()) return true
        if (chrome.isVisible) { chrome.onPageInteracted(); return true }
        if (webView.canGoBack()) { webView.goBack(); return true }
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (cursor.mode == CursorController.Mode.CURSOR) {
            val r = 10f * density
            canvas.drawCircle(cursor.x, cursor.y, r, dotPaint)
            canvas.drawCircle(cursor.x, cursor.y, r, outlinePaint)
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/kotlin/net/mrowser/web/CursorLayout.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add cursor layout host with d-pad routing"
```

---

## Task 8: Wire into MainActivity + layout

**Files:**
- Replace: `app/src/main/res/layout/activity_main.xml`, `app/src/main/kotlin/net/mrowser/MainActivity.kt`

- [ ] **Step 1: Replace the layout**

Replace `app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<net.mrowser.web.CursorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/cursorLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/surface">

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
            android:id="@+id/reloadButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/focus_ring"
            android:src="@android:drawable/ic_menu_rotate"
            android:contentDescription="@string/reload" />
    </LinearLayout>
</net.mrowser.web.CursorLayout>
```

- [ ] **Step 2: Replace MainActivity**

Replace `app/src/main/kotlin/net/mrowser/MainActivity.kt`:

```kotlin
package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.cursorLayout)
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        val bar = findViewById<View>(R.id.chromeBar)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reloadButton = findViewById<ImageButton>(R.id.reloadButton)

        webView.webViewClient = WebViewClient()
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; layout.invalidate() },
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
        layout.onBack = { chromeClient.exitIfFullscreen() }

        layout.post {
            cursor.center(webView.width, webView.height)
            chrome.requestReveal(atTop = true)
        }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
            chrome.onInteracted()
        }
        reloadButton.setOnClickListener {
            webView.reload()
            chrome.onInteracted()
        }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromInput()
                true
            } else {
                false
            }
        }

        webView.loadData(WELCOME_HTML, "text/html", "utf-8")
        layout.requestFocus()
    }

    private fun loadFromInput() {
        val url = UrlNormalizer.normalize(urlInput.text.toString()) ?: return
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }

    companion object {
        private const val WELCOME_HTML =
            "<html><body style='background:#141414;color:#eee;font-family:sans-serif;" +
            "display:flex;height:100vh;margin:0;align-items:center;justify-content:center'>" +
            "<h1 style='color:#E50914'>mrowser</h1></body></html>"
    }
}
```

- [ ] **Step 3: Run unit tests + build the signed APK**

Run: `/Users/mohammad/Projects/mrowser/gradlew test assembleRelease`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 4: Manual test on the box**

Install the signed APK on the Sunyia box. Verify:
- On launch the bar is shown; a red cursor dot is visible.
- D-pad moves the cursor; holding a direction accelerates it.
- Cursor near the bottom edge scrolls the page down; near the top scrolls up.
- OK clicks links/buttons on a real site.
- Long-press OK toggles the cursor off (focus mode) and on.
- With the page at top, pressing UP past the top reveals the bar; it auto-hides after a few seconds.
- The site's fullscreen button fills the screen with video; BACK exits fullscreen.
- Focus rings are clearly visible (red) on the bar controls.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/src/main/res/layout/activity_main.xml app/src/main/kotlin/net/mrowser/MainActivity.kt
git -C /Users/mohammad/Projects/mrowser commit -m "feat: wire cursor, chrome, and fullscreen into main activity"
```

---

## Self-review (completed by plan author)

- **Spec coverage:** §3 control scheme → Tasks 5 (cursor/tap/edge-scroll/mode), 7 (key routing, BACK precedence), 4 (bar reveal/idle). §4 visual → Task 1 + cursor paint in Task 7. §5.1 CursorGeometry → Task 2. §5.2 CursorController → Task 5. §5.3 ChromeVisibility → Task 3. §5.4 ChromeController → Task 4. §5.5 CursorLayout → Task 7. §5.6 BrowserWebChromeClient → Task 6. §5.7 MainActivity → Task 8. §8 testing → Tasks 2, 3 (unit) + Task 8 Step 4 (manual). No gaps.
- **Placeholder scan:** none. The edge-scroll MVP note in Task 5 is an explicit, justified design choice, not a deferred gap.
- **Type consistency:** `CursorController.Mode`, `mode`, `x`/`y`, `startMove`/`stopMove`/`tap`/`toggleMode`/`center` used identically in Task 7 routing and Task 8 wiring. `ChromeController.requestReveal/onInteracted/onPageInteracted/isVisible` consistent across Tasks 4, 7, 8. `BrowserWebChromeClient.exitIfFullscreen/isFullscreen` consistent across Tasks 6, 8. View IDs (`cursorLayout`, `webView`, `chromeBar`, `urlInput`, `backButton`, `reloadButton`) match between `activity_main.xml` and `MainActivity.kt`. `ChromeVisibility.reduce` signature matches Tasks 3 and 4.
```
