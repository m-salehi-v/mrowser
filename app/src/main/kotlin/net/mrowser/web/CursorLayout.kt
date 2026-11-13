package net.mrowser.web

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.webkit.WebView
import android.widget.FrameLayout
import net.mrowser.R

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

    /** Set by the Activity; fired when BACK is pressed at the first page (no history left). */
    var onExitPage: () -> Unit = {}

    /**
     * Set by the Activity; fired when BACK is held. Summons the chrome bar.
     * Returns true if it consumed the hold — false means "I did nothing with it"
     * (e.g. during HTML5 fullscreen), and the release falls through to handleBack().
     */
    var onLongBack: () -> Boolean = { false }

    var playChip: android.view.View? = null
    var onChipClick: () -> Unit = {}

    /** The download progress chip; OK over it opens the Downloads page. */
    var downloadChip: android.view.View? = null
    var onDownloadChipClick: () -> Unit = {}

    private val density = resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E50914") }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#CCFFFFFF")
    }

    /**
     * A page is loading: the cursor wears a spinning ring (CURSOR mode only). Setting it
     * redraws once; while true and drawn, the ring keeps itself animating, and it asks for
     * nothing when false or detached.
     */
    var busy = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val busyTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BUSY_STROKE_DP * density
        color = context.getColor(R.color.ring_track)
    }
    private val busyArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BUSY_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.accent)
    }
    private val busyBounds = RectF()

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var okDown = false
    private var longPressed = false
    private val longPress = Runnable { longPressed = true; cursor.toggleMode() }

    private var backDown = false
    private var backLongPressed = false
    // Only a hold that onLongBack actually consumed counts as a long-press; a refused
    // one leaves the flag false so the release still runs the normal BACK chain.
    private val backLongPress = Runnable { backLongPressed = onLongBack() }

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return handleBackKey(event)
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (chrome.isVisible) chrome.onPageInteracted() else chrome.requestReveal(atTop = true)
            }
            return true
        }
        // While the bar is up it takes the keys: let focus search move through its buttons
        // and the URL field instead of driving the cursor.
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
            KeyEvent.ACTION_DOWN -> cursor.startMove(dx, dy)
            KeyEvent.ACTION_UP -> cursor.stopMove()
        }
        return true
    }

    private fun handleOk(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!okDown) {
                okDown = true
                longPressed = false
                longPressHandler.postDelayed(longPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                okDown = false
                longPressHandler.removeCallbacks(longPress)
                if (!longPressed) {
                    when {
                        chipContains(playChip, cursor.x, cursor.y) -> onChipClick()
                        chipContains(downloadChip, cursor.x, cursor.y) -> onDownloadChipClick()
                        else -> cursor.tap()
                    }
                }
            }
        }
        return true
    }

    private fun chipContains(chip: android.view.View?, x: Float, y: Float): Boolean {
        if (chip == null) return false
        if (chip.visibility != android.view.View.VISIBLE) return false
        return x >= chip.left && x <= chip.right && y >= chip.top && y <= chip.bottom
    }

    /**
     * BACK tap keeps every meaning it has today (see handleBack); BACK held summons
     * the chrome bar. Most TV remotes have no MENU key, which was the only other way
     * to open it. The routing itself is the pure BackGesture — this is just the
     * applier: it owns the timer, the flags, and the BACK chain.
     */
    private fun handleBackKey(event: KeyEvent): Boolean {
        when (BackGesture.decide(event.action, backDown, backLongPressed, chrome.isVisible)) {
            BackGesture.Decision.ArmLongPress -> {
                backDown = true
                backLongPressed = false
                longPressHandler.postDelayed(backLongPress, LONG_PRESS_MS)
            }
            BackGesture.Decision.BeginWithoutArming -> {
                backDown = true
                backLongPressed = false
            }
            BackGesture.Decision.RunTapChain -> {
                backDown = false
                longPressHandler.removeCallbacks(backLongPress)
                return handleBack()
            }
            BackGesture.Decision.Swallow -> {
                backDown = false
                longPressHandler.removeCallbacks(backLongPress)
            }
            BackGesture.Decision.Ignore -> Unit
        }
        return true
    }

    private fun handleBack(): Boolean {
        if (onBack()) return true
        if (chrome.isVisible) { chrome.onPageInteracted(); return true }
        if (webView.canGoBack()) { webView.goBack(); return true }
        // Root of history. We consume BACK's ACTION_DOWN above without startTracking,
        // so the Activity's back-tracking never fires onBackPressed — handle it here.
        onExitPage()
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (cursor.mode == CursorController.Mode.CURSOR) {
            val r = 10f * density
            canvas.drawCircle(cursor.x, cursor.y, r, dotPaint)
            canvas.drawCircle(cursor.x, cursor.y, r, outlinePaint)
            if (busy && isAttachedToWindow) {
                val ring = BUSY_RADIUS_DP * density
                canvas.drawCircle(cursor.x, cursor.y, ring, busyTrackPaint)
                busyBounds.set(cursor.x - ring, cursor.y - ring, cursor.x + ring, cursor.y + ring)
                val angle = (SystemClock.uptimeMillis() % BUSY_PERIOD_MS) * 360f / BUSY_PERIOD_MS
                canvas.drawArc(busyBounds, angle, BUSY_SWEEP_DEG, false, busyArcPaint)
                postInvalidateOnAnimation()
            }
        }
    }

    companion object {
        const val LONG_PRESS_MS = 500L
        private const val BUSY_RADIUS_DP = 18f
        private const val BUSY_STROKE_DP = 3f
        private const val BUSY_PERIOD_MS = 900L
        private const val BUSY_SWEEP_DEG = 90f
    }
}
