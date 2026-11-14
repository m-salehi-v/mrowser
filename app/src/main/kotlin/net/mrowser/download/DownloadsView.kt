package net.mrowser.download

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import net.mrowser.R

/**
 * The Downloads page: a heading, Clear finished and one row per download, newest first with the
 * running ones on top (the order comes from [DownloadList.visible]). The fourth overlay, focus-modal
 * like History.
 *
 * [render] runs on every poll — once a second while something downloads — so it redraws the rows
 * in place while the list is the same downloads in the same order, and rebuilds only when that
 * changes. A rebuild destroys the focused row, and the window would then hand focus to the page
 * behind this overlay (#32), so it re-seats focus on the same download's row, or the first row.
 */
class DownloadsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val list: LinearLayout
    private val emptyHint: TextView
    private val clearButton: Button

    private var onPrimary: (DownloadRowModel) -> Unit = {}
    private var onOptions: (DownloadRowModel) -> Unit = {}
    private var onClearFinished: () -> Unit = {}

    /** The latest model per id, so a row's click acts on what it shows now, not at its creation. */
    private var models: Map<Long, DownloadRowModel> = emptyMap()

    init {
        LayoutInflater.from(context).inflate(R.layout.downloads_view, this, true)
        list = findViewById(R.id.downloadsList)
        emptyHint = findViewById(R.id.downloadsEmptyHint)
        clearButton = findViewById(R.id.clearFinishedButton)
        clearButton.setOnClickListener { onClearFinished() }
    }

    fun bind(
        onPrimary: (DownloadRowModel) -> Unit,
        onOptions: (DownloadRowModel) -> Unit,
        onClearFinished: () -> Unit
    ) {
        this.onPrimary = onPrimary
        this.onOptions = onOptions
        this.onClearFinished = onClearFinished
    }

    fun show() {
        visibility = View.VISIBLE
        // Post: see HomeView.show — a sync requestFocus before layout can no-op.
        post { restoreFocus() }
    }

    fun hide() {
        visibility = View.GONE
    }

    /** Re-seat D-pad focus on the first row (or Clear finished). Returns false on failure. */
    fun restoreFocus(): Boolean = (list.getChildAt(0) ?: clearButton).requestFocus()

    fun render(rows: List<DownloadRowModel>) {
        models = rows.associateBy { it.info.id }
        emptyHint.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val ids = rows.map { it.info.id }
        val shown = (0 until list.childCount).map { list.getChildAt(it).tag as Long }
        if (ids == shown) {
            rows.forEachIndexed { i, model -> fill(list.getChildAt(i), model) }
            return
        }
        // Captured before the rows go: removing the focused one moves focus out of this view.
        val focusedId = list.focusedChild?.tag as Long?
        list.removeAllViews()
        rows.forEach { list.addView(row(it)) }
        if (focusedId == null) return
        val same = (0 until list.childCount).map(list::getChildAt).firstOrNull { it.tag == focusedId }
        if (same?.requestFocus() != true) restoreFocus()
    }

    private fun row(model: DownloadRowModel): View {
        val v = LayoutInflater.from(context).inflate(R.layout.download_row, list, false)
        val id = model.info.id
        v.tag = id
        v.setOnClickListener { models[id]?.let(onPrimary) }
        v.setOnLongClickListener { models[id]?.let(onOptions); true }
        // MENU opens the options too. Both actions are consumed so nothing else sees the key; the
        // dialog opens on the release, as a click does.
        v.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_MENU) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP) models[id]?.let(onOptions)
            true
        }
        fill(v, model)
        return v
    }

    /** Writes [model] into an existing row; only what changed, since this runs every second. */
    private fun fill(v: View, model: DownloadRowModel) {
        v.findViewById<ImageView>(R.id.rowType).apply {
            val icon = typeIcon(model.kind)
            if (tag != icon) {
                setImageResource(icon)
                tag = icon
            }
        }
        setIfChanged(v.findViewById(R.id.rowName), model.info.name)
        setIfChanged(v.findViewById(R.id.rowDetail), model.detail)
        v.findViewById<ProgressBar>(R.id.rowProgress).apply {
            val percent = model.progress
            visibility = if (percent == null) View.GONE else View.VISIBLE
            if (percent != null) progress = percent
        }
        v.findViewById<TextView>(R.id.rowAction).apply {
            val label = actionLabel(model.primary)
            visibility = if (label == null) View.GONE else View.VISIBLE
            if (label != null) setIfChanged(this, context.getString(label))
        }
    }

    private fun setIfChanged(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }

    private fun typeIcon(kind: DownloadKind): Int = when (kind) {
        DownloadKind.VIDEO -> R.drawable.ic_type_video
        DownloadKind.APK -> R.drawable.ic_type_apk
        DownloadKind.OTHER -> R.drawable.ic_type_file
    }

    companion object {
        /**
         * The word at the row's end for what OK does, and the options dialog's first item; none
         * for a row whose OK opens the options.
         */
        fun actionLabel(primary: Primary): Int? = when (primary) {
            Primary.PLAY -> R.string.download_play
            Primary.INSTALL -> R.string.download_install
            Primary.OPEN -> R.string.download_open
            Primary.RETRY -> R.string.download_retry
            Primary.OPTIONS -> null
        }
    }
}
