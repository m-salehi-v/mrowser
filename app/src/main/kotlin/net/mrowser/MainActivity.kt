package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import net.mrowser.handoff.HandoffController
import net.mrowser.home.FavoriteDialog
import net.mrowser.home.HistoryView
import net.mrowser.home.HomeView
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
    private lateinit var homeView: HomeView
    private lateinit var favorites: JsonFavoritesStore
    private lateinit var history: JsonHistoryStore
    private lateinit var historyView: HistoryView
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
        val bar = findViewById<View>(R.id.chromeBar)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reloadButton = findViewById<ImageButton>(R.id.reloadButton)
        val favoriteButton = findViewById<ImageButton>(R.id.favoriteButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val historyButton = findViewById<ImageButton>(R.id.historyButton)

        favorites = JsonFavoritesStore(File(filesDir, "favorites.json"))
        history = JsonHistoryStore(File(filesDir, "history.json"))

        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = { runOnUiThread { showChip(); handoff.play() } },
            onCleared = { playChip.visibility = View.GONE }
        )
        handoff = HandoffController(this, sniffer)

        webView.webViewClient = SniffingWebViewClient(
            sniffer,
            onNavigate = { url -> updateUrlText(url) },
            onLoaded = { url -> recordHistory(url, webView.title) }
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
        val cursor = CursorController(webView) { layout.invalidate() }
        layout.webView = webView
        layout.cursor = cursor
        layout.chrome = chrome
        layout.playChip = playChip
        layout.onChipClick = { handoff.play() }
        layout.onBack = { chromeClient.exitIfFullscreen() }

        homeView.bind(
            repository = favorites,
            onOpen = { openUrl(it.url) },
            onSubmitUrl = { openUrl(it) },
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } },
            onHistory = { showHistory() }
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
            showHistory()
            chrome.onInteracted()
        }
        favoriteButton.setOnClickListener {
            addCurrentToFavorites()
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
        layout.requestFocus()
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }

    private fun showHome() {
        homeView.show()
    }

    private fun showHistory() {
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
    }

    private fun showChip() {
        playChip.visibility = View.VISIBLE
        uiHandler.removeCallbacks(chipHideRunnable)
        uiHandler.postDelayed(chipHideRunnable, CHIP_TIMEOUT_MS)
    }

    private fun addCurrentToFavorites() {
        val url = webView.url ?: return
        val title = webView.title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
        favorites.add(Favorite(title, url))
        Toast.makeText(this, R.string.add_favorite, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        if (::sniffer.isInitialized && sniffer.hasStream() && homeView.visibility != View.VISIBLE) showChip()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            historyView.visibility == View.VISIBLE -> {
                historyView.hide()
                showHome()
            }
            homeView.visibility == View.VISIBLE -> super.onBackPressed()
            else -> showHome()
        }
    }

    companion object {
        private const val CHIP_TIMEOUT_MS = 30_000L
    }
}
