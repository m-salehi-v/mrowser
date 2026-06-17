package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import java.io.File
import net.mrowser.data.Favorite
import net.mrowser.data.JsonFavoritesStore
import net.mrowser.handoff.HandoffController
import net.mrowser.home.FavoriteDialog
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.cursorLayout)
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        playChip = findViewById(R.id.playChip)
        homeView = findViewById(R.id.homeView)
        val bar = findViewById<View>(R.id.chromeBar)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reloadButton = findViewById<ImageButton>(R.id.reloadButton)
        val favoriteButton = findViewById<ImageButton>(R.id.favoriteButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)

        favorites = JsonFavoritesStore(File(filesDir, "favorites.json"))

        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = { runOnUiThread { playChip.visibility = View.VISIBLE } },
            onCleared = { playChip.visibility = View.GONE }
        )
        val handoff = HandoffController(this, sniffer)

        webView.webViewClient = SniffingWebViewClient(sniffer) { url -> showAddressBar(url) }
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() }
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
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { homeView.refresh() } }
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
        layout.requestFocus()
        webView.loadUrl(url)
        chrome.onPageInteracted()
    }

    private fun showHome() {
        homeView.show()
    }

    private fun showAddressBar(url: String) {
        urlInput.setText(url)
        chrome.requestReveal(atTop = true, focusInput = false)
    }

    private fun addCurrentToFavorites() {
        val url = webView.url ?: return
        val title = webView.title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
        favorites.add(Favorite(title, url))
        Toast.makeText(this, R.string.add_favorite, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (homeView.visibility == View.VISIBLE) {
            super.onBackPressed()
        } else {
            showHome()
        }
    }
}
