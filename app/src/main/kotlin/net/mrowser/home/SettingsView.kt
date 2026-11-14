package net.mrowser.home

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.text.NumberFormat
import net.mrowser.R
import net.mrowser.adblock.BlockList
import net.mrowser.data.CursorSpeed
import net.mrowser.data.Settings
import net.mrowser.data.SettingsRepository
import net.mrowser.web.SearchEngine
import net.mrowser.web.SiteSettings

/** Settings overlay: auto-open, display-mode matching, search engine, desktop-by-default, pop-up blocker, ad blocker, cursor speed,
 *  block-list info, favorites export/import. */
class SettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val autoOpenRow: View
    private val frameRateRow: View
    private val resolutionRow: View
    private val desktopRow: View
    private val popupRow: View
    private val adsRow: View
    private val searchRow: View
    private val cursorRow: View
    private val listsRow: View
    private val searchValue: TextView
    private val cursorValue: TextView
    private val listsValue: TextView

    private var repository: SettingsRepository? = null
    private var blockList: () -> BlockList = { BlockList.EMPTY }

    // The file work needs the Activity (permissions, the picker's result), so it lives there.
    private var onExportFavorites: () -> Unit = {}
    private var onImportFavorites: () -> Unit = {}

    init {
        LayoutInflater.from(context).inflate(R.layout.settings_view, this, true)
        autoOpenRow = findViewById(R.id.settingsAutoOpenRow)
        frameRateRow = findViewById(R.id.settingsFrameRateRow)
        resolutionRow = findViewById(R.id.settingsResolutionRow)
        desktopRow = findViewById(R.id.settingsDesktopRow)
        popupRow = findViewById(R.id.settingsPopupRow)
        adsRow = findViewById(R.id.settingsAdsRow)
        searchRow = findViewById(R.id.settingsSearchRow)
        cursorRow = findViewById(R.id.settingsCursorRow)
        listsRow = findViewById(R.id.settingsListsRow)
        searchValue = findViewById(R.id.settingsSearchValue)
        cursorValue = findViewById(R.id.settingsCursorValue)
        listsValue = findViewById(R.id.settingsListsValue)

        autoOpenRow.setOnClickListener { toggleAutoOpen() }
        frameRateRow.setOnClickListener { toggleMatchFrameRate() }
        resolutionRow.setOnClickListener { toggleMatchResolution() }
        desktopRow.setOnClickListener { toggleDesktopByDefault() }
        popupRow.setOnClickListener { toggleBlockPopups() }
        adsRow.setOnClickListener { toggleBlockAds() }
        searchRow.setOnClickListener { pickSearchEngine() }
        cursorRow.setOnClickListener { pickCursor() }
        listsRow.setOnClickListener { showLists() }
        findViewById<View>(R.id.settingsExportRow).setOnClickListener { onExportFavorites() }
        findViewById<View>(R.id.settingsImportRow).setOnClickListener { onImportFavorites() }
    }

    /** [blockList] is a provider because the list loads on a background thread after launch. */
    fun bind(repository: SettingsRepository, blockList: () -> BlockList) {
        this.repository = repository
        this.blockList = blockList
    }

    fun bindFavorites(onExport: () -> Unit, onImport: () -> Unit) {
        onExportFavorites = onExport
        onImportFavorites = onImport
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
        showToggle(autoOpenRow, R.string.auto_open_title, s.autoOpenPlayer)
        showToggle(frameRateRow, R.string.match_frame_rate_title, s.matchFrameRate)
        showToggle(resolutionRow, R.string.match_resolution_title, s.matchResolution)
        showToggle(desktopRow, R.string.desktop_by_default_title, s.desktopByDefault)
        showToggle(popupRow, R.string.block_popups_title, s.blockPopups)
        showToggle(adsRow, R.string.block_ads_title, s.blockAds)
        searchValue.text = s.searchEngine.label
        cursorValue.setText(cursorLabelRes(s.cursorSpeed))
        listsValue.text = grouped(blockList().size)
    }

    /** The row is activated for "on"; its switch copies the row's state (activated, and focused
     *  for the lighter off track), and the row, the only focus target, says "On"/"Off". */
    private fun showToggle(row: View, title: Int, on: Boolean) {
        row.isActivated = on
        row.contentDescription = context.getString(
            R.string.settings_toggle_state,
            context.getString(title),
            context.getString(if (on) R.string.on else R.string.off)
        )
    }

    private fun toggleAutoOpen() {
        val s = current()
        repository?.update(s.copy(autoOpenPlayer = !s.autoOpenPlayer))
        render()
    }

    private fun toggleMatchFrameRate() {
        val s = current()
        repository?.update(s.copy(matchFrameRate = !s.matchFrameRate))
        render()
    }

    private fun toggleMatchResolution() {
        val s = current()
        repository?.update(s.copy(matchResolution = !s.matchResolution))
        render()
    }

    /** Changing the default prunes the per-site choices that now equal it, so they cannot shadow it. */
    private fun toggleDesktopByDefault() {
        val s = current()
        val on = !s.desktopByDefault
        repository?.update(s.copy(desktopByDefault = on, desktopOverride = SiteSettings.pruneDesktop(s.desktopOverride, on)))
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

    private fun pickSearchEngine() {
        val options = SearchEngine.values()
        val labels = options.map { it.label }.toTypedArray()
        // Single-choice, as pickCursor: the current engine carries the checked dot.
        AlertDialog.Builder(context)
            .setTitle(R.string.search_engine_title)
            .setSingleChoiceItems(labels, options.indexOf(current().searchEngine)) { dialog, which ->
                repository?.update(current().copy(searchEngine = options[which]))
                render()
                dialog.dismiss()
            }
            .show()
    }

    private fun pickCursor() {
        val options = listOf(CursorSpeed.SLOW, CursorSpeed.NORMAL, CursorSpeed.FAST)
        val labels = options.map { context.getString(cursorLabelRes(it)) }.toTypedArray()
        // Single-choice so the current speed carries the dialog's checked dot and the D-pad
        // starts on it; a pick still closes the dialog at once.
        AlertDialog.Builder(context)
            .setTitle(R.string.cursor_speed_title)
            .setSingleChoiceItems(labels, options.indexOf(current().cursorSpeed)) { dialog, which ->
                repository?.update(current().copy(cursorSpeed = options[which]))
                render()
                dialog.dismiss()
            }
            .show()
    }

    /** A domain count with the locale's grouping: "92,745", not "92745". */
    private fun grouped(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    /** Attribution for the bundled lists: name, entries, licence and URL per source. */
    private fun showLists() {
        val list = blockList()
        val body = buildString {
            for (s in list.info.sources) {
                append(s.name).append('\n')
                s.entries?.let { append("  ").append(grouped(it)).append(" domains · ") } ?: append("  ")
                append(s.licence).append('\n')
                append("  ").append(s.url).append("\n\n")
            }
            append(context.getString(R.string.ad_block_lists_generated, list.info.generated ?: "—", grouped(list.size)))
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
