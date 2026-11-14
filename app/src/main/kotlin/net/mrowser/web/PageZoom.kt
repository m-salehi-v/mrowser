package net.mrowser.web

import java.math.BigDecimal

/**
 * Pure: the page-zoom steps (Chrome's) and the script that applies one.
 *
 * Zoom is the CSS `zoom` property on the root element, set per page load by the glue: it is the
 * only zoom a WebView offers that a script-free TV remote can drive without a pinch gesture, and
 * it reflows the page like Chrome's own zoom. 100 removes the property again, but only when this
 * script put it there, so a page's own `zoom` is never wiped.
 */
object PageZoom {
    val STEPS: List<Int> = listOf(50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200)
    const val DEFAULT = 100

    /** The nearest step; a tie goes to the lower one. */
    fun snap(percent: Int): Int = STEPS.minByOrNull { Math.abs(it - percent) }!!

    fun stepUp(percent: Int): Int {
        val i = STEPS.indexOf(snap(percent))
        return STEPS[minOf(i + 1, STEPS.lastIndex)]
    }

    fun stepDown(percent: Int): Int {
        val i = STEPS.indexOf(snap(percent))
        return STEPS[maxOf(i - 1, 0)]
    }

    fun label(percent: Int): String = "$percent%"

    fun script(percent: Int): String {
        val factor = BigDecimal(percent).movePointLeft(2).stripTrailingZeros().toPlainString()
        return "(function(){var d=document.documentElement;if(!d)return;var z='$factor';" +
            "if(z==='1'){if(d.hasAttribute('data-mrowser-zoom')){d.style.removeProperty('zoom');" +
            "d.removeAttribute('data-mrowser-zoom');}}else{d.style.setProperty('zoom',z);" +
            "d.setAttribute('data-mrowser-zoom',z);}})()"
    }
}
