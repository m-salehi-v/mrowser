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

    var playChip: android.view.View? = null
    var onChipClick: () -> Unit = {}

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
                if (!longPressed) {
                    if (chipContains(cursor.x, cursor.y)) onChipClick() else cursor.tap()
                }
            }
        }
        return true
    }

    private fun chipContains(x: Float, y: Float): Boolean {
        val chip = playChip ?: return false
        if (chip.visibility != android.view.View.VISIBLE) return false
        return x >= chip.left && x <= chip.right && y >= chip.top && y <= chip.bottom
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
