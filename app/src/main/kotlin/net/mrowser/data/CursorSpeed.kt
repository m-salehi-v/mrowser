package net.mrowser.data

/** D-pad cursor speed, as a multiplier over the CursorGeometry base/max ramp. */
enum class CursorSpeed(val multiplier: Float) {
    SLOW(0.6f),
    NORMAL(1.0f),
    FAST(1.5f)
}
