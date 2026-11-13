package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageZoomTest {
    @Test fun `chrome's steps`() = assertEquals(listOf(50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200), PageZoom.STEPS)

    @Test fun `steps up and down and stops at the ends`() {
        assertEquals(110, PageZoom.stepUp(100)); assertEquals(90, PageZoom.stepDown(100))
        assertEquals(67, PageZoom.stepUp(50)); assertEquals(175, PageZoom.stepDown(200))
        assertEquals(200, PageZoom.stepUp(200)); assertEquals(50, PageZoom.stepDown(50))
    }

    @Test fun `off-step values snap to the nearest step`() {
        assertEquals(125, PageZoom.snap(130)); assertEquals(67, PageZoom.snap(66))
        assertEquals(50, PageZoom.snap(10)); assertEquals(200, PageZoom.snap(1000))
        assertEquals(150, PageZoom.stepUp(130))
    }

    @Test fun `a tie snaps to the lower step`() = assertEquals(100, PageZoom.snap(105))

    @Test fun `the script zooms the root element, and 100 clears it`() {
        assertTrue(PageZoom.script(125).contains("'1.25'"))
        assertTrue(PageZoom.script(67).contains("'0.67'"))
        assertTrue(PageZoom.script(125).contains("document.documentElement"))
        assertTrue(PageZoom.script(100).contains("var z='1'"))
    }

    @Test fun `label`() = assertEquals("125%", PageZoom.label(125))
}
