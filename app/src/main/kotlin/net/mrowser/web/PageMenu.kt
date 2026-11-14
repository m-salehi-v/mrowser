package net.mrowser.web

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import net.mrowser.R

/**
 * The page menu behind the bar's ⋯: Desktop site and Zoom for the current site.
 *
 * A focusable [PopupWindow] rather than a view in the bar's window: it is its own window, so the
 * D-pad cannot leave it while it is open, and BACK (handled by the popup itself) closes it and
 * hands the main window its focus back, which is still on ⋯.
 *
 * The zoom row is one focus target, a slider: LEFT/RIGHT step it ([PageZoom]) and OK resets it
 * to 100%, so every press on either row does something. The choices are read live through
 * [desktopFor] / [zoomFor] after each change, so the panel always shows what is stored.
 */
class PageMenu(
    private val anchor: View,
    private val desktopFor: (site: String) -> Boolean,
    private val zoomFor: (site: String) -> Int,
    private val onDesktop: (site: String, desktop: Boolean) -> Unit,
    private val onZoom: (site: String, percent: Int) -> Unit,
) {
    private var popup: PopupWindow? = null
    /** The site the open panel shows and stores its choices for. */
    private var shownSite: String? = null

    val isShowing: Boolean get() = popup?.isShowing == true

    // A popup's content has no parent to inflate against; PopupWindow sets its size itself.
    @SuppressLint("InflateParams")
    fun show(site: String) {
        dismiss()
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val view = LayoutInflater.from(context).inflate(R.layout.page_menu, null)
        val desktopRow = view.findViewById<View>(R.id.pageMenuDesktopRow)
        val zoomRow = view.findViewById<View>(R.id.pageMenuZoomRow)
        val zoomOut = view.findViewById<View>(R.id.pageMenuZoomOut)
        val zoomValue = view.findViewById<TextView>(R.id.pageMenuZoomValue)
        val zoomIn = view.findViewById<View>(R.id.pageMenuZoomIn)
        view.findViewById<TextView>(R.id.pageMenuSite).text =
            context.getString(R.string.page_menu_remembered, site)

        fun render() {
            val desktop = desktopFor(site)
            desktopRow.isActivated = desktop
            desktopRow.contentDescription = context.getString(
                R.string.settings_toggle_state,
                context.getString(R.string.page_menu_desktop),
                context.getString(if (desktop) R.string.on else R.string.off)
            )
            val zoom = zoomFor(site)
            val label = PageZoom.label(zoom)
            zoomValue.text = label
            zoomOut.alpha = if (zoom <= PageZoom.STEPS.first()) DIMMED else 1f
            zoomIn.alpha = if (zoom >= PageZoom.STEPS.last()) DIMMED else 1f
            zoomRow.contentDescription = context.getString(R.string.page_menu_zoom_description, label)
        }

        fun setZoom(percent: Int) {
            if (percent == zoomFor(site)) return
            onZoom(site, percent)
            render()
        }

        // The page reloads behind the panel; the panel stays open on the new state.
        desktopRow.setOnClickListener {
            onDesktop(site, !desktopFor(site))
            render()
        }
        zoomRow.setOnClickListener { setZoom(PageZoom.DEFAULT) }
        // Both actions are consumed, so LEFT/RIGHT never move focus; a held key repeats the step.
        zoomRow.setOnKeyListener { _, keyCode, event ->
            val step: ((Int) -> Int)? = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> PageZoom::stepDown
                KeyEvent.KEYCODE_DPAD_RIGHT -> PageZoom::stepUp
                else -> null
            }
            if (step == null) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN) setZoom(step(zoomFor(site)))
            true
        }
        render()

        val window = PopupWindow(view, (WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        // The panel's card is the window's background, not the content's: the elevation's
        // shadow follows the background's outline, and a transparent one casts none.
        window.setBackgroundDrawable(context.getDrawable(R.drawable.page_menu_bg))
        window.elevation = ELEVATION_DP * density
        window.isOutsideTouchable = true
        window.setOnDismissListener { if (popup === window) { popup = null; shownSite = null } }
        popup = window
        shownSite = site
        window.showAsDropDown(anchor, 0, (OFFSET_DP * density).toInt(), Gravity.END)
        view.post { desktopRow.requestFocus() }
    }

    fun dismiss() {
        val window = popup ?: return
        popup = null
        shownSite = null
        window.dismiss()
    }

    /**
     * The page is going to [url] (`onPageStarted`): a panel for another site would show and store
     * that site's choices over a page they do not apply to, so it closes. The same site — the
     * Desktop toggle's own reload, a link within the site — keeps it open.
     */
    fun onNavigate(url: String) {
        if (isShowing && SiteSettings.site(url) != shownSite) dismiss()
    }

    private companion object {
        const val WIDTH_DP = 380
        const val ELEVATION_DP = 12f
        const val OFFSET_DP = 14
        const val DIMMED = 0.35f
    }
}
