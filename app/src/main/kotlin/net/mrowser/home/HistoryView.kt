package net.mrowser.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.data.HistoryEntry
import net.mrowser.data.HistoryRepository

/** History overlay: heading + clear-all + a list of visited sites, newest first. */
class HistoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val list: LinearLayout
    private val emptyHint: TextView
    private val clearButton: Button

    private var repository: HistoryRepository? = null
    private var onOpen: (String) -> Unit = {}
    private var onClear: () -> Unit = {}
    private var onAddFavorite: (HistoryEntry) -> Unit = {}
    private var now: () -> Long = { System.currentTimeMillis() }

    init {
        LayoutInflater.from(context).inflate(R.layout.history_view, this, true)
        list = findViewById(R.id.historyList)
        emptyHint = findViewById(R.id.historyEmptyHint)
        clearButton = findViewById(R.id.clearHistoryButton)
        clearButton.setOnClickListener { onClear() }
    }

    fun bind(
        repository: HistoryRepository,
        onOpen: (String) -> Unit,
        onClear: () -> Unit,
        onAddFavorite: (HistoryEntry) -> Unit
    ) {
        this.repository = repository
        this.onOpen = onOpen
        this.onClear = onClear
        this.onAddFavorite = onAddFavorite
    }

    fun show() {
        visibility = View.VISIBLE
        refresh()
        (list.getChildAt(0) ?: clearButton).requestFocus()
    }

    fun hide() {
        visibility = View.GONE
    }

    fun refresh() {
        val items = repository?.findAll().orEmpty()
        list.removeAllViews()
        emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val nowMs = now()
        items.forEach { list.addView(row(it, nowMs)) }
    }

    private fun row(entry: HistoryEntry, nowMs: Long): View {
        val v = LayoutInflater.from(context).inflate(R.layout.history_row, list, false)
        val letter = v.findViewById<TextView>(R.id.rowLetter)
        val title = v.findViewById<TextView>(R.id.rowTitle)
        val url = v.findViewById<TextView>(R.id.rowUrl)
        val time = v.findViewById<TextView>(R.id.rowTime)
        letter.text = entry.title.trim().take(1).uppercase().ifEmpty { "•" }
        letter.backgroundTintList = ColorStateList.valueOf(colorFor(entry.url))
        title.text = entry.title.ifBlank { entry.url }
        url.text = entry.url
        time.text = RelativeTime.format(nowMs - entry.visitedAt)
        v.setOnClickListener { onOpen(entry.url) }
        v.setOnLongClickListener { onAddFavorite(entry); true }
        v.setOnFocusChangeListener { row, hasFocus ->
            val s = if (hasFocus) 1.04f else 1f
            row.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        return v
    }

    /** Stable pleasant color derived from the url (matches HomeView). */
    private fun colorFor(url: String): Int {
        val hue = ((url.hashCode() % 360) + 360) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.80f))
    }
}
