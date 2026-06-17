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
        val pinnedTop = dy < 0 && y <= 0f
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
