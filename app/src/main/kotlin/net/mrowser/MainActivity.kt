package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import java.io.File
import net.mrowser.adblock.AdBlocker
import net.mrowser.data.DefaultFavorites
import net.mrowser.data.Favorite
import net.mrowser.data.HistoryEntry
import net.mrowser.data.JsonFavoritesStore
import net.mrowser.data.JsonHistoryStore
import net.mrowser.data.JsonSettingsStore
import net.mrowser.handoff.HandoffController
import net.mrowser.home.FavoriteDialog
import net.mrowser.home.HistoryView
import net.mrowser.home.HomeView
import net.mrowser.home.OverlayFocus
import net.mrowser.home.SettingsView
import net.mrowser.stream.SniffingWebViewClient
import net.mrowser.stream.StreamSniffer
import net.mrowser.web.BrowserWebChromeClient
import net.mrowser.web.ChromeController
import net.mrowser.web.CursorController
import net.mrowser.web.CursorLayout
import net.mrowser.web.ExternalIntentLauncher
import net.mrowser.web.IncomingUrl
import net.mrowser.web.RegistrableDomain
import net.mrowser.web.UrlHost
import net.mrowser.web.UrlNormalizer

class MainActivity : Activity() {

    private lateinit var layout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var chrome: ChromeController
    private lateinit var chromeClient: BrowserWebChromeClient
    private lateinit var sniffer: StreamSniffer
    private lateinit var adBlocker: AdBlocker
    private lateinit var playChip: TextView
    private lateinit var favoriteButton: ImageButton
    private lateinit var adBlockButton: TextView
    private lateinit var homeView: HomeView
    private lateinit var favorites: JsonFavoritesStore
    private lateinit var history: JsonHistoryStore
    private lateinit var historyView: HistoryView
    private lateinit var settings: JsonSettingsStore
    private lateinit var settingsView: SettingsView
    private lateinit var externalLinks: ExternalIntentLauncher

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
        adBlockButton = findViewById(R.id.adBlockButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val historyButton = findViewById<ImageButton>(R.id.historyButton)

        favorites = JsonFavoritesStore(File(filesDir, "favorites.json"))
        history = JsonHistoryStore(File(filesDir, "history.json"))
        settings = JsonSettingsStore(File(filesDir, "settings.json"))
        seedDefaultFavorites()

        adBlocker = AdBlocker(
            blockAds = { settings.get().blockAds },
            blockPopups = { settings.get().blockPopups },
            allowedSites = { settings.get().adsAllowedOn },
            onCountChanged = { n -> adBlockButton.text = if (n == 0) "" else n.toString() }
        )
        adBlocker.load { resources.openRawResource(R.raw.blocklist) }
        // Extracted to its own method (rather than inlined here) so ART on API 23 (minSdk) does
        // not have to resolve ServiceWorkerController/ServiceWorkerClient while verifying
        // onCreate itself — those classes don't exist below API 24, and a method ART can't fully
        // resolve gets verified interpreted-with-checks instead of fast, which would otherwise
        // apply to the whole of onCreate.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) registerServiceWorkerAdBlock()

        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = {
                runOnUiThread {
                    showChip()
                    if (settings.get().autoOpenPlayer) handoff.play()
                }
            },
            onCleared = { playChip.visibility = View.GONE },
            schedule = { delayMs, task -> uiHandler.postDelayed(task, delayMs) }
        )
        handoff = HandoffController(this, sniffer)

        externalLinks = ExternalIntentLauncher(
            context = this,
            onFallback = { url -> webView.loadUrl(url) },
            onNoApp = { Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show() }
        )

        webView.webViewClient = SniffingWebViewClient(
            sniffer,
            adBlocker,
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
            },
            onExternalScheme = { url -> externalLinks.launch(url) },
            onNavigationBlocked = { Toast.makeText(this, R.string.navigation_blocked, Toast.LENGTH_SHORT).show() }
        )
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { bar.visibility = View.GONE; playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() },
            onTitle = { url, title -> recordHistory(url, title) },
            onPopupBlocked = { Toast.makeText(this, R.string.popup_blocked, Toast.LENGTH_SHORT).show() },
            isBlockedPopup = { url -> adBlocker.isBlockedPopup(url) },
            launchExternal = { url -> externalLinks.launch(url) }
        )
        webView.webChromeClient = chromeClient
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Route window.open / target="_blank" through onCreateWindow so it can be refused.
            // Left at the default false, the WebView silently loads the pop-up over the current
            // page instead, which is the behaviour being fixed.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            // Lock down file:// access (defaults to true on API 23-29): a malicious page
            // must not be able to read the app's private files via a file:// URL.
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            allowContentAccess = false
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
        // Refuse the reveal in fullscreen (the bar would sit under the video) and report
        // it, so the hold falls through to the normal BACK chain and exits fullscreen.
        layout.onLongBack = {
            if (chromeClient.isFullscreen) false
            else { chrome.requestReveal(atTop = true); true }
        }

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
        settingsView.bind(settings) { adBlocker.list }

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
        adBlockButton.setOnClickListener {
            toggleAdsForSite()
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

        // Launched by another app's link (mrowser is a registered browser), or from the
        // launcher / TV home row. Only the former has a page to go to.
        val link = IncomingUrl.fromViewIntent(intent?.action, intent?.dataString)
        if (link != null) openUrl(link) else showHome()
    }

    /** A link from another app while mrowser is already running. `singleTask` sends it here
     *  instead of stacking a second browser, so the running page just navigates. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        IncomingUrl.fromViewIntent(intent?.action, intent?.dataString)?.let { openUrl(it) }
    }

    /** Show exactly one overlay at a time: a second VISIBLE overlay steals window-global
     *  D-pad focus (focus search can't move past it), so every transition hides all three
     *  first, then the caller shows the one it wants (or none, for openUrl). */
    private fun hideAllOverlays() {
        homeView.hide()
        historyView.hide()
        settingsView.hide()
    }

    private fun openUrl(url: String) {
        hideAllOverlays()
        layout.requestFocus()
        clearHistoryOnLoad = true
        adBlocker.onUserNavigation()
        webView.loadUrl(url)
        chrome.onPageInteracted()
        showNavHintOnce()
    }

    /** One-time nudge: the chrome bar has no MENU key to summon it on most TV remotes. */
    private fun showNavHintOnce() {
        if (settings.get().navHintShown) return
        settings.update(settings.get().copy(navHintShown = true))
        Toast.makeText(this, R.string.nav_hint, Toast.LENGTH_LONG).show()
    }

    private fun showHome() {
        hideAllOverlays()
        homeView.show()
    }

    private fun showSettings() {
        hideAllOverlays()
        settingsView.show()
    }

    private fun showHistory(fromHome: Boolean) {
        historyFromHome = fromHome
        hideAllOverlays()
        historyView.show()
    }

    /** First launch only: write the shipped starter favorites. Guarded by a persisted
     *  flag rather than an is-empty check — a user who deletes them must not get them
     *  back. Existing installs have no flag in settings.json, so they seed once on upgrade.
     *  Added in reverse: FavoritesOps.add prepends, so seeding back-to-front leaves the
     *  grid in DefaultFavorites.ALL order. */
    private fun seedDefaultFavorites() {
        if (settings.get().seeded) return
        DefaultFavorites.ALL.asReversed().forEach { favorites.add(it) }
        settings.update(settings.get().copy(seeded = true))
    }

    private fun recordHistory(url: String, title: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val label = title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
        history.record(HistoryEntry(label, url, System.currentTimeMillis()))
    }

    private fun updateUrlText(url: String) {
        urlInput.setText(url)
        updateFavoriteIcon()
        updateShieldIcon()
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

    /** Shield button: allow ads on the current site if blocked, block them if allowed. Reloads. */
    private fun toggleAdsForSite() {
        val host = webView.url?.let { UrlHost.of(it) } ?: return
        val site = RegistrableDomain.of(host)
        val s = settings.get()
        val wasAllowed = site in s.adsAllowedOn
        settings.update(s.copy(adsAllowedOn = if (wasAllowed) s.adsAllowedOn - site else s.adsAllowedOn + site))
        Toast.makeText(
            this,
            if (wasAllowed) R.string.ads_blocked_here else R.string.ads_allowed_here,
            Toast.LENGTH_SHORT
        ).show()
        updateShieldIcon()
        webView.reload()
    }

    /** Tint the shield accent (red) when ads are allowed on the current site, white otherwise. */
    private fun updateShieldIcon() {
        val allowed = adBlocker.isAllowlisted(webView.url?.let { UrlHost.of(it) })
        val color = getColor(if (allowed) R.color.accent else R.color.on_surface)
        adBlockButton.compoundDrawableTintList = ColorStateList.valueOf(color)
        adBlockButton.setTextColor(color)
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        if (::sniffer.isInitialized && sniffer.hasStream() &&
            homeView.visibility != View.VISIBLE && historyView.visibility != View.VISIBLE &&
            settingsView.visibility != View.VISIBLE) showChip()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ServiceWorkerController is process-scoped, not Activity-scoped: its client keeps this
        // Activity reachable (client -> adBlocker -> onCountChanged -> adBlockButton -> this)
        // after finish(), along with the WebView and the ~740 KB BlockList. Clearing it here
        // also stops a recreated Activity's service-worker fetches from being filtered by this
        // now-stale AdBlocker (stale pageHost, stale settings cache).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) unregisterServiceWorkerAdBlock()
    }

    /** Service-worker fetches bypass WebViewClient.shouldInterceptRequest entirely, so they need
     *  their own intercept hook. Kept out of onCreate — see the call site there. */
    private fun registerServiceWorkerAdBlock() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                adBlocker.interceptServiceWorker(request)
        })
    }

    /** Counterpart to [registerServiceWorkerAdBlock]; see [onDestroy]. */
    private fun unregisterServiceWorkerAdBlock() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Recovery: focus can be lost outright (intermittent on the overlays), or land
        // in the wrong subtree — destroying the focused view sends the window's fallback
        // to the root's first focusable, CursorLayout, which sits *behind* a visible
        // overlay and turns the D-pad into the hidden cursor. Either way the user is
        // stuck, so re-seat focus where it belongs and swallow this one press.
        if (event.action == KeyEvent.ACTION_DOWN) {
            val target = OverlayFocus.recoveryTarget(
                settingsVisible = settingsView.visibility == View.VISIBLE,
                historyVisible = historyView.visibility == View.VISIBLE,
                homeVisible = homeView.visibility == View.VISIBLE,
                focus = focusZone()
            )
            val recovered = when (target) {
                OverlayFocus.Zone.SETTINGS -> settingsView.restoreFocus()
                OverlayFocus.Zone.HISTORY -> historyView.restoreFocus()
                OverlayFocus.Zone.HOME -> homeView.restoreFocus()
                OverlayFocus.Zone.PAGE -> layout.requestFocus()
                else -> false
            }
            if (recovered) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Which of the four subtrees currently holds D-pad focus, for [OverlayFocus]. */
    private fun focusZone(): OverlayFocus.Zone {
        var v: View? = currentFocus
        while (v != null) {
            when (v) {
                settingsView -> return OverlayFocus.Zone.SETTINGS
                historyView -> return OverlayFocus.Zone.HISTORY
                homeView -> return OverlayFocus.Zone.HOME
                layout -> return OverlayFocus.Zone.PAGE
            }
            v = v.parent as? View
        }
        return OverlayFocus.Zone.NONE
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
