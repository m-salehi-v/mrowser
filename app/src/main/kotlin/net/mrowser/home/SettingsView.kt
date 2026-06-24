package net.mrowser.home

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import net.mrowser.R
import net.mrowser.data.CursorSpeed
import net.mrowser.data.Settings
import net.mrowser.data.SettingsRepository
import net.mrowser.data.SubtitleLanguagePref

/** Settings overlay: auto-open toggle + subtitle-language and cursor-speed pickers. */
class SettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val autoOpenRow: View
    private val subtitleRow: View
    private val cursorRow: View
    private val autoOpenValue: TextView
    private val subtitleValue: TextView
    private val cursorValue: TextView

    private var repository: SettingsRepository? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.settings_view, this, true)
        autoOpenRow = findViewById(R.id.settingsAutoOpenRow)
        subtitleRow = findViewById(R.id.settingsSubtitleRow)
        cursorRow = findViewById(R.id.settingsCursorRow)
        autoOpenValue = findViewById(R.id.settingsAutoOpenValue)
        subtitleValue = findViewById(R.id.settingsSubtitleValue)
        cursorValue = findViewById(R.id.settingsCursorValue)

        autoOpenRow.setOnClickListener { toggleAutoOpen() }
        subtitleRow.setOnClickListener { pickSubtitle() }
        cursorRow.setOnClickListener { pickCursor() }
    }

    fun bind(repository: SettingsRepository) {
        this.repository = repository
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
        subtitleValue.setText(subtitleLabelRes(s.subtitleLanguage))
        cursorValue.setText(cursorLabelRes(s.cursorSpeed))
    }

    private fun toggleAutoOpen() {
        val s = current()
        repository?.update(s.copy(autoOpenPlayer = !s.autoOpenPlayer))
        render()
    }

    private fun pickSubtitle() {
        val options = listOf(
            SubtitleLanguagePref.PERSIAN,
            SubtitleLanguagePref.ENGLISH,
            SubtitleLanguagePref.OFF
        )
        val labels = options.map { context.getString(subtitleLabelRes(it)) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.subtitle_language_title)
            .setItems(labels) { _, which ->
                repository?.update(current().copy(subtitleLanguage = options[which]))
                render()
            }
            .show()
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

    private fun subtitleLabelRes(p: SubtitleLanguagePref): Int = when (p) {
        SubtitleLanguagePref.PERSIAN -> R.string.subtitle_persian
        SubtitleLanguagePref.ENGLISH -> R.string.subtitle_english
        SubtitleLanguagePref.OFF -> R.string.subtitle_off
    }

    private fun cursorLabelRes(c: CursorSpeed): Int = when (c) {
        CursorSpeed.SLOW -> R.string.cursor_slow
        CursorSpeed.NORMAL -> R.string.cursor_normal
        CursorSpeed.FAST -> R.string.cursor_fast
    }
}
