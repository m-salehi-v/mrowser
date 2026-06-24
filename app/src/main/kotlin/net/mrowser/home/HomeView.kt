package net.mrowser.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
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
        findViewById<Button>(R.id.homeSettingsButton).setOnClickListener { onSettings() }
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
    }

    fun show() {
        visibility = View.VISIBLE
        refresh()
        // Post: a synchronous requestFocus right after VISIBLE can fail before the
        // layout pass, leaving nothing focused (D-pad then dead). See restoreFocus.
        post { restoreFocus() }
    }

    /** Re-seat D-pad focus on the URL pill. Returns false if it couldn't take focus. */
    fun restoreFocus(): Boolean = urlInput.requestFocus()

    fun hide() {
        visibility = View.GONE
    }

    fun refresh() {
        val items = repository?.findAll().orEmpty()
        grid.removeAllViews()
        emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { grid.addView(card(it)) }
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
}
