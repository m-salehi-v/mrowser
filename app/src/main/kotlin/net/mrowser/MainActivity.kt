package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import java.io.File
import net.mrowser.data.Favorite
import net.mrowser.data.HistoryEntry
import net.mrowser.data.JsonFavoritesStore
import net.mrowser.data.JsonHistoryStore
import net.mrowser.data.JsonSettingsStore
import net.mrowser.handoff.HandoffController
import net.mrowser.home.FavoriteDialog
import net.mrowser.home.HistoryView
import net.mrowser.home.HomeView
import net.mrowser.home.SettingsView
import net.mrowser.stream.SniffingWebViewClient
import net.mrowser.stream.StreamSniffer
import net.mrowser.web.BrowserWebChromeClient
import net.mrowser.web.ChromeController
import net.mrowser.web.CursorController
import net.mrowser.web.CursorLayout
import net.mrowser.web.UrlNormalizer

class MainActivity : Activity() {

    private lateinit var layout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var chrome: ChromeController
    private lateinit var chromeClient: BrowserWebChromeClient
    private lateinit var sniffer: StreamSniffer
    private lateinit var playChip: TextView
    private lateinit var favoriteButton: ImageButton
    private lateinit var homeView: HomeView
    private lateinit var favorites: JsonFavoritesStore
    private lateinit var history: JsonHistoryStore
    private lateinit var historyView: HistoryView
    private lateinit var settings: JsonSettingsStore
    private lateinit var settingsView: SettingsView

    /** True when history was opened from the home overlay (BACK returns to home);
     *  false when opened from the chrome bar mid-browse (BACK returns to the page). */
    private var historyFromHome = false

    /** Set when a page is opened from home; clears the back-stack on its first load. */
    private var clearHistoryOnLoad = false
    private lateinit var handoff: HandoffController
    private val uiHandler = Handler(Looper.getMainLooper())
    private val chipHideRunnable = Runnable { playChip.visibility = View.GONE }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.cursorLayout)
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        playChip = findViewById(R.id.playChip)
        homeView = findViewById(R.id.homeView)
        historyView = findViewById(R.id.historyView)
        settingsView = findViewById(R.id.settingsView)
        val bar = findViewById<View>(R.id.chromeBar)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reloadButton = findViewById<ImageButton>(R.id.reloadButton)
        favoriteButton = findViewById(R.id.favoriteButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val historyButton = findViewById<ImageButton>(R.id.historyButton)

        favorites = JsonFavoritesStore(File(filesDir, "favorites.json"))
        history = JsonHistoryStore(File(filesDir, "history.json"))
        settings = JsonSettingsStore(File(filesDir, "settings.json"))

        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = {
                runOnUiThread {
                    showChip()
                    if (settings.get().autoOpenPlayer) handoff.play()
                }
            },
            onCleared = { playChip.visibility = View.GONE },
            subtitlePref = { settings.get().subtitleLanguage }
        )
        handoff = HandoffController(this, sniffer)

        webView.webViewClient = SniffingWebViewClient(
            sniffer,
            onNavigate = { url -> updateUrlText(url) },
            onLoaded = { url ->
                recordHistory(url, webView.title)
                // Opening from home starts a fresh tab: drop any prior back-stack so
                // BACK at this page reaches root (close-tab) instead of walking old
                // pages / leftover about:blank entries from a previous tab.
                if (clearHistoryOnLoad) {
                    clearHistoryOnLoad = false
                    webView.clearHistory()
                }
            }
        )
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() },
            onTitle = { url, title -> recordHistory(url, title) }
        )
        webView.webChromeClient = chromeClient
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        chrome = ChromeController(bar, urlInput, webView)
        val cursor = CursorController(webView, { settings.get().cursorSpeed.multiplier }) { layout.invalidate() }
        layout.webView = webView
        layout.cursor = cursor
        layout.chrome = chrome
        layout.playChip = playChip
        layout.onChipClick = { handoff.play() }
        layout.onBack = { chromeClient.exitIfFullscreen() }
        layout.onExitPage = { confirmCloseTab() }

        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } },
            onHistory = { showHistory(fromHome = true) },
            onSettings = { showSettings() }
        )
        historyView.bind(
            repository = history,
            onOpen = { openUrl(it) },
            onClear = { history.clear(); historyView.refresh() },
            onAddFavorite = { entry ->
                favorites.add(Favorite(entry.title, entry.url))
                Toast.makeText(this, R.string.add_favorite, Toast.LENGTH_SHORT).show()
            }
        )
        settingsView.bind(settings)

        layout.post { cursor.center(webView.width, webView.height) }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
            chrome.onInteracted()
        }
        reloadButton.setOnClickListener {
            webView.reload()
            chrome.onInteracted()
        }
        homeButton.setOnClickListener { showHome() }
        historyButton.setOnClickListener {
            showHistory(fromHome = false)
            chrome.onInteracted()
        }
        favoriteButton.setOnClickListener {
            toggleCurrentFavorite()
            chrome.onInteracted()
        }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                UrlNormalizer.normalize(urlInput.text.toString())?.let { openUrl(it) }
                true
            } else {
                false
            }
        }

        showHome()
    }

    private fun openUrl(url: String) {
        homeView.hide()
        historyView.hide()
        settingsView.hide()
        layout.requestFocus()
        clearHistoryOnLoad = true
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }

    private fun showHome() {
        homeView.show()
    }

    private fun showSettings() {
        // Focus-modal: hide the other overlays so window-global D-pad focus search
        // can't escape into them (same rule as showHistory).
        homeView.hide()
        historyView.hide()
        settingsView.show()
    }

    private fun showHistory(fromHome: Boolean) {
        historyFromHome = fromHome
        // Hide the home overlay first: leaving it visible behind the history
        // overlay keeps its favorites grid focusable, and window-global D-pad
        // focus search escapes into it (focus can't move past the first row).
        homeView.hide()
        historyView.show()
    }

    private fun recordHistory(url: String, title: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val label = title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
        history.record(HistoryEntry(label, url, System.currentTimeMillis()))
    }

    private fun updateUrlText(url: String) {
        urlInput.setText(url)
        updateFavoriteIcon()
    }

    private fun showChip() {
        playChip.visibility = View.VISIBLE
        uiHandler.removeCallbacks(chipHideRunnable)
        uiHandler.postDelayed(chipHideRunnable, CHIP_TIMEOUT_MS)
    }

    private fun isFavorite(url: String): Boolean = favorites.findAll().any { it.url == url }

    /** Star button: add the current page if absent, remove it if already saved. */
    private fun toggleCurrentFavorite() {
        val url = webView.url ?: return
        if (isFavorite(url)) {
            favorites.remove(url)
            Toast.makeText(this, R.string.remove_favorite, Toast.LENGTH_SHORT).show()
        } else {
            val title = webView.title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
            favorites.add(Favorite(title, url))
            Toast.makeText(this, R.string.add_favorite, Toast.LENGTH_SHORT).show()
        }
        updateFavoriteIcon()
        homeView.refresh()
    }

    /** Tint the star accent (red) when the current page is a favorite, white otherwise. */
    private fun updateFavoriteIcon() {
        val saved = webView.url?.let { isFavorite(it) } ?: false
        val color = getColor(if (saved) R.color.accent else R.color.on_surface)
        favoriteButton.imageTintList = ColorStateList.valueOf(color)
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        if (::sniffer.isInitialized && sniffer.hasStream() &&
            homeView.visibility != View.VISIBLE && settingsView.visibility != View.VISIBLE) showChip()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Recovery: if focus was lost (intermittent on the overlays), D-pad keys have
        // no anchor for focus search and the user is stuck. Re-seat focus into
        // whatever's on screen and swallow this one press.
        if (currentFocus == null && event.action == KeyEvent.ACTION_DOWN) {
            val recovered = when {
                settingsView.visibility == View.VISIBLE -> settingsView.restoreFocus()
                historyView.visibility == View.VISIBLE -> historyView.restoreFocus()
                homeView.visibility == View.VISIBLE -> homeView.restoreFocus()
                else -> layout.requestFocus()
            }
            if (recovered) return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            settingsView.visibility == View.VISIBLE -> {
                settingsView.hide()
                showHome()
            }
            historyView.visibility == View.VISIBLE -> {
                historyView.hide()
                // Return to wherever history was opened from.
                if (historyFromHome) showHome() else layout.requestFocus()
            }
            homeView.visibility == View.VISIBLE -> confirmExit()
            else -> confirmCloseTab()
        }
    }

    /** BACK at the first page in history: confirm before closing the page to home. */
    private fun confirmCloseTab() {
        AlertDialog.Builder(this)
            .setTitle(R.string.close_tab_title)
            .setMessage(R.string.close_tab_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.close_tab) { _, _ ->
                webView.loadUrl("about:blank")
                showHome()
            }
            .show()
    }

    /** BACK on the home overlay is the app's root: confirm before exiting to the launcher. */
    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }
            .show()
    }

    companion object {
        private const val CHIP_TIMEOUT_MS = 30_000L
    }
}
