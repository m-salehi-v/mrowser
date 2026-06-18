package net.mrowser.web

/** Pure cursor math: no Android types, fully unit-testable. */
object CursorGeometry {

    const val BASE_SPEED_PX = 6f
    const val MAX_SPEED_PX = 20f
    const val ACCEL_MS = 900L

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
