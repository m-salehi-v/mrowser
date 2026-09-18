package net.mrowser.home

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.adblock.BlockList
import net.mrowser.data.CursorSpeed
import net.mrowser.data.Settings
import net.mrowser.data.SettingsRepository

/** Settings overlay: auto-open, pop-up blocker, ad blocker, cursor speed, block-list info. */
class SettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val autoOpenRow: View
    private val popupRow: View
    private val adsRow: View
    private val cursorRow: View
    private val listsRow: View
    private val autoOpenValue: TextView
    private val popupValue: TextView
    private val adsValue: TextView
    private val cursorValue: TextView
    private val listsValue: TextView

    private var repository: SettingsRepository? = null
    private var blockList: () -> BlockList = { BlockList.EMPTY }

    init {
        LayoutInflater.from(context).inflate(R.layout.settings_view, this, true)
        autoOpenRow = findViewById(R.id.settingsAutoOpenRow)
        popupRow = findViewById(R.id.settingsPopupRow)
        adsRow = findViewById(R.id.settingsAdsRow)
        cursorRow = findViewById(R.id.settingsCursorRow)
        listsRow = findViewById(R.id.settingsListsRow)
        autoOpenValue = findViewById(R.id.settingsAutoOpenValue)
        popupValue = findViewById(R.id.settingsPopupValue)
        adsValue = findViewById(R.id.settingsAdsValue)
        cursorValue = findViewById(R.id.settingsCursorValue)
        listsValue = findViewById(R.id.settingsListsValue)

        autoOpenRow.setOnClickListener { toggleAutoOpen() }
        popupRow.setOnClickListener { toggleBlockPopups() }
        adsRow.setOnClickListener { toggleBlockAds() }
        cursorRow.setOnClickListener { pickCursor() }
        listsRow.setOnClickListener { showLists() }
    }

    /** [blockList] is a provider because the list loads on a background thread after launch. */
    fun bind(repository: SettingsRepository, blockList: () -> BlockList) {
        this.repository = repository
        this.blockList = blockList
    }

    fun show() {
        visibility = View.VISIBLE
        render()
        // Post: a synchronous requestFocus right after VISIBLE can fail before the layout
        // pass, leaving nothing focused (matches HomeView/HistoryView).
        post { restoreFocus() }
    }

    fun hide() {
        visibility = View.GONE
    }

    /** Re-seat D-pad focus on the first row. Returns false if it couldn't take focus. */
    fun restoreFocus(): Boolean = autoOpenRow.requestFocus()

    private fun current(): Settings = repository?.get() ?: Settings()

    private fun render() {
        val s = current()
        autoOpenValue.setText(if (s.autoOpenPlayer) R.string.on else R.string.off)
        popupValue.setText(if (s.blockPopups) R.string.on else R.string.off)
        adsValue.setText(if (s.blockAds) R.string.on else R.string.off)
        cursorValue.setText(cursorLabelRes(s.cursorSpeed))
        listsValue.text = blockList().size.toString()
    }

    private fun toggleAutoOpen() {
        val s = current()
        repository?.update(s.copy(autoOpenPlayer = !s.autoOpenPlayer))
        render()
    }

    private fun toggleBlockPopups() {
        val s = current()
        repository?.update(s.copy(blockPopups = !s.blockPopups))
        render()
    }

    private fun toggleBlockAds() {
        val s = current()
        repository?.update(s.copy(blockAds = !s.blockAds))
        render()
    }

    private fun pickCursor() {
        val options = listOf(CursorSpeed.SLOW, CursorSpeed.NORMAL, CursorSpeed.FAST)
        val labels = options.map { context.getString(cursorLabelRes(it)) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.cursor_speed_title)
            .setItems(labels) { _, which ->
                repository?.update(current().copy(cursorSpeed = options[which]))
                render()
            }
            .show()
    }

    /** Attribution for the bundled lists: name, entries, licence and URL per source. */
    private fun showLists() {
        val list = blockList()
        val body = buildString {
            for (s in list.info.sources) {
                append(s.name).append('\n')
                s.entries?.let { append("  ").append(it).append(" domains · ") } ?: append("  ")
                append(s.licence).append('\n')
                append("  ").append(s.url).append("\n\n")
            }
            append(context.getString(R.string.ad_block_lists_generated, list.info.generated ?: "—", list.size))
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.ad_block_lists_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun cursorLabelRes(c: CursorSpeed): Int = when (c) {
        CursorSpeed.SLOW -> R.string.cursor_slow
        CursorSpeed.NORMAL -> R.string.cursor_normal
        CursorSpeed.FAST -> R.string.cursor_fast
    }
}
