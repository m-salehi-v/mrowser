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
        val expected = (CursorGeometry.BASE_SPEED_PX + CursorGeometry.MAX_SPEED_PX) / 2
        assertEquals(expected, mid, 0.5f)
    }

    @Test fun `edge detection flags top and bottom zones`() {
        assertTrue(CursorGeometry.isAtTopEdge(10f, 48f))
        assertFalse(CursorGeometry.isAtTopEdge(60f, 48f))
        assertTrue(CursorGeometry.isAtBottomEdge(960f, 1000, 48f))
        assertFalse(CursorGeometry.isAtBottomEdge(900f, 1000, 48f))
    }

    @Test fun `wheels up at the top edge`() {
        assertEquals(0.2f, CursorGeometry.wheelStep(-1, 10f, 1000, 48f, 0.2f), 0.0001f)
    }

    @Test fun `wheels down at the bottom edge`() {
        assertEquals(-0.2f, CursorGeometry.wheelStep(1, 990f, 1000, 48f, 0.2f), 0.0001f)
    }

    @Test fun `no wheel away from either edge`() {
        assertEquals(0f, CursorGeometry.wheelStep(-1, 500f, 1000, 48f, 0.2f), 0.0001f)
        assertEquals(0f, CursorGeometry.wheelStep(1, 500f, 1000, 48f, 0.2f), 0.0001f)
    }

    @Test fun `no wheel when moving away from the edge the cursor sits in`() {
        assertEquals(0f, CursorGeometry.wheelStep(1, 10f, 1000, 48f, 0.2f), 0.0001f)
        assertEquals(0f, CursorGeometry.wheelStep(-1, 990f, 1000, 48f, 0.2f), 0.0001f)
    }

    @Test fun `no wheel for horizontal or idle movement`() {
        assertEquals(0f, CursorGeometry.wheelStep(0, 0f, 1000, 48f, 0.2f), 0.0001f)
        assertEquals(0f, CursorGeometry.wheelStep(0, 1000f, 1000, 48f, 0.2f), 0.0001f)
    }

    /**
     * The page's own limits are Chromium's business now: a wheel is clamped by
     * whatever it lands on, so unlike scrollBy there is no canScroll gate here and
     * no overshoot to pin. Pinning to the cursor is the point — the scroller under
     * it may be a fixed overlay the document knows nothing about (#31).
     */
    @Test fun `the cursor pinned at an edge keeps wheeling`() {
        assertEquals(0.2f, CursorGeometry.wheelStep(-1, 0f, 1000, 48f, 0.2f), 0.0001f)
        assertEquals(-0.2f, CursorGeometry.wheelStep(1, 1000f, 1000, 48f, 0.2f), 0.0001f)
    }

    @Test fun `multiplier scales the base speed`() {
        assertEquals(CursorGeometry.BASE_SPEED_PX * 0.5f, CursorGeometry.speedForHoldMs(0L, 0.5f), 0.001f)
    }

    @Test fun `multiplier scales the max speed`() {
        assertEquals(CursorGeometry.MAX_SPEED_PX * 2f, CursorGeometry.speedForHoldMs(CursorGeometry.ACCEL_MS, 2f), 0.001f)
    }

    @Test fun `default multiplier leaves speed unchanged`() {
        assertEquals(CursorGeometry.speedForHoldMs(0L), CursorGeometry.speedForHoldMs(0L, 1f), 0.001f)
    }
}
