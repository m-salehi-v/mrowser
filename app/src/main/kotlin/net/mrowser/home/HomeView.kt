package net.mrowser.home

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.data.Favorite
import net.mrowser.data.FavoritesRepository
import net.mrowser.web.UrlNormalizer

/** Home overlay: wordmark + URL pill + favorites grid. */
class HomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val grid: GridLayout
    private val emptyHint: TextView
    private val urlInput: EditText

    private var repository: FavoritesRepository? = null
    private var onOpen: (Favorite) -> Unit = {}
    private var onSubmitUrl: (String) -> Unit = {}
    private var onEdit: (Favorite) -> Unit = {}
    private var onHistory: () -> Unit = {}
    private var onSettings: () -> Unit = {}

    private val backgroundDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, null)
    private var cycle: ValueAnimator? = null
    private var startIndex = 0
    private var lastFrameMs = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.home_view, this, true)
        grid = findViewById(R.id.favoritesGrid)
        emptyHint = findViewById(R.id.emptyHint)
        urlInput = findViewById(R.id.homeUrlInput)
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                UrlNormalizer.normalize(urlInput.text.toString())?.let { onSubmitUrl(it) }
                true
            } else {
                false
            }
        }
        findViewById<Button>(R.id.homeHistoryButton).setOnClickListener { onHistory() }
        findViewById<ImageButton>(R.id.homeSettingsButton).setOnClickListener { onSettings() }
    }

    fun bind(
        repository: FavoritesRepository,
        onOpen: (Favorite) -> Unit,
        onSubmitUrl: (String) -> Unit,
        onEdit: (Favorite) -> Unit,
        onHistory: () -> Unit,
        onSettings: () -> Unit
    ) {
        this.repository = repository
        this.onOpen = onOpen
        this.onSubmitUrl = onSubmitUrl
        this.onEdit = onEdit
        this.onHistory = onHistory
        this.onSettings = onSettings
        startIndex = HomeBackgrounds.indexFor(System.currentTimeMillis())
        background = backgroundDrawable
        applyGradient(0f)
    }

    /**
     * Walks the whole palette list forever, lerping between neighbours. One
     * GradientDrawable whose two colours are rewritten on a tick — no extra
     * layer, no overdraw. The animator's value is "position in the list", so
     * its integer part selects the pair and its fraction is the blend.
     */
    private fun applyGradient(position: Float) {
        val n = HomeBackgrounds.ALL.size
        val step = position.toInt()
        val from = HomeBackgrounds.ALL[(startIndex + step) % n]
        val to = HomeBackgrounds.ALL[(startIndex + step + 1) % n]
        val g = HomeBackgrounds.blend(from, to, position - step)
        backgroundDrawable.colors = intArrayOf(g.top, g.bottom)
    }

    fun show() {
        visibility = View.VISIBLE
        refresh()
        startCycle()
        // Post: a synchronous requestFocus right after VISIBLE can fail before the
        // layout pass, leaving nothing focused (D-pad then dead). See restoreFocus.
        post { restoreFocus() }
    }

    /** Resumes rather than restarts, so returning home doesn't jump the colour. */
    private fun startCycle() {
        val a = cycle ?: ValueAnimator.ofFloat(0f, HomeBackgrounds.ALL.size.toFloat()).apply {
            duration = CYCLE_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                // The colours crawl, so a 60fps redraw of the whole overlay buys
                // nothing. At 10fps each step is well under one 8-bit level.
                val now = SystemClock.uptimeMillis()
                if (now - lastFrameMs < FRAME_MS) return@addUpdateListener
                lastFrameMs = now
                applyGradient(it.animatedValue as Float)
            }
            cycle = this
        }
        if (a.isStarted) a.resume() else a.start()
    }

    private fun stopCycle() = cycle?.pause()

    /** Re-seat D-pad focus on the URL pill. Returns false if it couldn't take focus. */
    fun restoreFocus(): Boolean = urlInput.requestFocus()

    fun hide() {
        visibility = View.GONE
        // Nothing is on screen to animate. Left running, this keeps ticking
        // behind the WebView and straight through playback.
        stopCycle()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cycle?.cancel()
        cycle = null
    }

    fun refresh() {
        // Rebuilding destroys the focused card — an edit or delete from its long-press
        // dialog lands here — and the window's fallback then hands focus to the page
        // behind this overlay, where the D-pad drives the hidden cursor (#32). Only
        // when focus was ours to begin with: a refresh from the chrome bar's star
        // button runs while this overlay is hidden and must not steal it.
        val hadFocus = findFocus() != null
        val items = repository?.findAll().orEmpty()
        grid.removeAllViews()
        emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { grid.addView(card(it)) }
        if (hadFocus && findFocus() == null) restoreFocus()
    }

    private fun card(fav: Favorite): View {
        val v = LayoutInflater.from(context).inflate(R.layout.favorite_card, grid, false)
        val letter = v.findViewById<TextView>(R.id.cardLetter)
        val title = v.findViewById<TextView>(R.id.cardTitle)
        letter.text = fav.title.trim().take(1).uppercase().ifEmpty { "•" }
        letter.backgroundTintList = ColorStateList.valueOf(colorFor(fav.url))
        title.text = fav.title.ifBlank { fav.url }
        v.setOnClickListener { onOpen(fav) }
        v.setOnLongClickListener { onEdit(fav); true }
        v.setOnFocusChangeListener { card, hasFocus ->
            val s = if (hasFocus) 1.1f else 1f
            card.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        return v
    }

    /** Stable pleasant color derived from the url. */
    private fun colorFor(url: String): Int {
        val hue = ((url.hashCode() % 360) + 360) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.80f))
    }

    private companion object {
        /** One pass through all six palettes: 20s per neighbouring pair. */
        const val CYCLE_MS = 120_000L
        const val FRAME_MS = 100L
    }
}
