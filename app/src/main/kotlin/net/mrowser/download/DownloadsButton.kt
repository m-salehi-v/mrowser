package net.mrowser.download

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.ImageButton
import net.mrowser.R

/**
 * The capsule bar's Downloads button: the plain icon, and while anything downloads a thin ring
 * around it — the track, and an accent arc that fills with the overall percent when it is known.
 * It redraws only when [setRing] changes something; nothing animates on its own.
 */
class DownloadsButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.imageButtonStyle
) : ImageButton(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_DP * density
        color = context.getColor(R.color.ring_track)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.accent)
    }
    private val bounds = RectF()

    private var active = false
    private var percent: Int? = null

    /** [percent] is the overall progress, or null when a running download's size is unknown. */
    fun setRing(active: Boolean, percent: Int?) {
        val p = if (active) percent?.coerceIn(0, 100) else null
        if (active == this.active && p == this.percent) return
        this.active = active
        this.percent = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - STROKE_DP * density
        canvas.drawCircle(cx, cy, r, trackPaint)
        val p = percent ?: return
        bounds.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(bounds, -90f, p * 3.6f, false, arcPaint)
    }

    private companion object {
        const val STROKE_DP = 2f
    }
}
