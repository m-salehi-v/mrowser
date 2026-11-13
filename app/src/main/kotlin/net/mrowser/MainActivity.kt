package net.mrowser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import net.mrowser.adblock.AdBlocker
import net.mrowser.data.DefaultFavorites
import net.mrowser.data.DocumentPicker
import net.mrowser.data.Favorite
import net.mrowser.data.FavoritesBackup
import net.mrowser.data.FavoritesBackupFile
import net.mrowser.data.HistoryEntry
import net.mrowser.data.JsonFavoritesStore
import net.mrowser.data.JsonHistoryStore
import net.mrowser.data.JsonSettingsStore
import net.mrowser.handoff.HandoffController
import net.mrowser.home.DestructiveButton
import net.mrowser.home.FavoriteDialog
import net.mrowser.home.HistoryView
import net.mrowser.home.HomeView
import net.mrowser.home.OverlayFocus
import net.mrowser.home.SettingsView
import net.mrowser.stream.SniffingWebViewClient
import net.mrowser.stream.StreamSniffer
import net.mrowser.update.ApkDownloader
import net.mrowser.update.JsonUpdateStore
import net.mrowser.update.ReleaseFetcher
import net.mrowser.update.UpdateController
import net.mrowser.update.UpdateDialog
import net.mrowser.web.BrowserWebChromeClient
import net.mrowser.web.ChromeController
import net.mrowser.web.CursorController
import net.mrowser.web.CursorLayout
import net.mrowser.web.ErrorNavigation
import net.mrowser.web.ExternalIntentLauncher
import net.mrowser.web.IncomingUrl
import net.mrowser.web.MainFrameUrl
import net.mrowser.web.PageError
import net.mrowser.web.PageErrorKind
import net.mrowser.web.PageErrorView
import net.mrowser.web.PageLoad
import net.mrowser.web.PageLoadEvent
import net.mrowser.web.PageLoadEvents
import net.mrowser.web.PageLoadState
import net.mrowser.web.RegistrableDomain
import net.mrowser.web.UrlDisplay
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
    private lateinit var adBlockButton: View
    private lateinit var adBlockIcon: ImageView
    private lateinit var adBlockCount: TextView
    private lateinit var homeView: HomeView
    private lateinit var favorites: JsonFavoritesStore
    private lateinit var history: JsonHistoryStore
    private lateinit var historyView: HistoryView
    private lateinit var settings: JsonSettingsStore
    private lateinit var settingsView: SettingsView
    private lateinit var pageErrorView: PageErrorView
    private lateinit var externalLinks: ExternalIntentLauncher
    private lateinit var updates: UpdateController
    private lateinit var updateIo: ExecutorService

    /** One outstanding storage-permission ask at a time; see ensureStoragePermission. */
    private var pendingStoragePermission: ((Boolean) -> Unit)? = null

    /** True when history was opened from the home overlay (BACK returns to home);
     *  false when opened from the chrome bar mid-browse (BACK returns to the page). */
    private var historyFromHome = false

    /** True while [showPageUrl] itself is writing the field, so the edit watcher can tell
     *  the page's URL apart from the user typing over it. */
    private var showingPageUrl = false

    /** Set when a page is opened from home; clears the back-stack on its first load. */
    private var clearHistoryOnLoad = false
    private lateinit var handoff: HandoffController
    private lateinit var webClient: SniffingWebViewClient
    private val uiHandler = Handler(Looper.getMainLooper())
    private val chipHideRunnable = Runnable { playChip.visibility = View.GONE }

    private lateinit var loadingLine: View
    private var loadState = PageLoadState()
    /** Ends a load that never reports an end (a download, a same-document navigation). */
    private val loadTimeout = Runnable { applyLoad(PageLoadEvent.TimedOut) }

    /** What the error screen is showing, for Try again and Go back. */
    private var errorKind: PageErrorKind? = null
    private var errorUrl: String? = null
    /** The failed load never committed (a refused certificate); see [ErrorNavigation]. */
    private var errorNeverCommitted = false
    /** Latched when the screen is shown; see [ErrorNavigation.Facts.freshTab]. */
    private var errorFromFreshTab = false
    /** Times focus was taken back from the page for the screen now showing; see onCreate. */
    private var errorFocusReseats = 0

    /** The URL the last [PageLoadEvent.Started] began, so its own later `onPageStarted` (at
     *  commit, for a clicked link) continues the line instead of restarting it. */
    private var startedUrl: String? = null

    /** A main-frame 4xx/5xx waiting for its page to finish; see [scheduleEmptyPageCheck]. */
    private var httpErrorUrl: String? = null
    private var httpErrorStatus = 0
    private var emptyPageCheck: Runnable? = null

    /** Bumped by every navigation start, so a late empty-page answer for an old page is dropped. */
    private var navigation = 0

    /** The renderer died and the WebView was destroyed; nothing may touch it until [recreate]. */
    private var webViewGone = false

    /** The WebView's page-load signals: the loading feedback and the error screen. */
    private val pageEvents = object : PageLoadEvents {
        override fun onStarting(url: String) {
            // Any new navigation ends the error screen and an empty-page check still pending.
            navigation++
            cancelEmptyPageCheck()
            // onPageStarted can come at commit rather than at the start (Chromium does that for
            // some navigations), which is after onReceivedHttpError for that same response, so
            // only a different URL forgets the status. The page's finish consumes it either way.
            if (url != httpErrorUrl) {
                httpErrorUrl = null
                httpErrorStatus = 0
            }
            hidePageError()
            // about:blank (close tab, home) and other non-web URLs show no loading feedback here
            // (the app's own loads — openUrl, the bar's back/reload, Try again, Go back — apply
            // Started themselves and end through about:blank's finish all the same)
            if (isWebUrl(url)) {
                val again = MainFrameUrl.same(url, startedUrl)
                startedUrl = url
                applyLoad(if (again) PageLoadEvent.StartedAgain else PageLoadEvent.Started)
            }
        }

        override fun onCommitVisible(url: String) = applyLoad(PageLoadEvent.Committed)

        override fun onMainFrameError(url: String, code: Int, description: String?) {
            // null: cancelled, not failed (our own ad cancel, a download, a scheme) — show nothing
            val kind = PageError.classify(code, description) ?: return
            applyLoad(PageLoadEvent.Failed)
            showPageError(kind, url, PageError.codeLabel(description))
        }

        override fun onMainFrameSslError(url: String) {
            applyLoad(PageLoadEvent.Failed)
            // cancel() stops the load before it commits: the previous page stays current
            showPageError(
                PageErrorKind.NOT_SECURE, url, getString(R.string.page_error_code_certificate),
                neverCommitted = true
            )
        }

        override fun onMainFrameHttpError(url: String, status: Int) {
            httpErrorUrl = url
            httpErrorStatus = status
        }

        /**
         * Keeps the app alive: the dead WebView is dropped (it must not be used again) and the
         * Activity is recreated with a fresh one, which shows Page crashed for [crashedUrl] and
         * loads nothing by itself — so a page that kills the renderer on load cannot loop.
         */
        override fun onRendererGone(crashed: Boolean): Boolean {
            crashedUrl = webView.url.orEmpty()
            rendererKilled = !crashed
            webViewGone = true
            uiHandler.removeCallbacksAndMessages(null)
            emptyPageCheck = null
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            recreate()
            return true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.cursorLayout)
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        playChip = findViewById(R.id.playChip)
        loadingLine = findViewById(R.id.loadingLine)
        homeView = findViewById(R.id.homeView)
        historyView = findViewById(R.id.historyView)
        settingsView = findViewById(R.id.settingsView)
        pageErrorView = findViewById(R.id.pageErrorView)
        val bar = findViewById<View>(R.id.chromeBar)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reloadButton = findViewById<ImageButton>(R.id.reloadButton)
        favoriteButton = findViewById(R.id.favoriteButton)
        adBlockButton = findViewById(R.id.adBlockButton)
        adBlockIcon = findViewById(R.id.adBlockIcon)
        adBlockCount = findViewById(R.id.adBlockCount)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val historyButton = findViewById<ImageButton>(R.id.historyButton)

        favorites = JsonFavoritesStore(File(filesDir, "favorites.json"))
        history = JsonHistoryStore(File(filesDir, "history.json"))
        settings = JsonSettingsStore(File(filesDir, "settings.json"))
        seedDefaultFavorites()

        updateIo = Executors.newSingleThreadExecutor()
        updates = UpdateController(
            store = JsonUpdateStore(File(filesDir, "update.json")),
            installedVersion = installedVersion(),
            fetch = ReleaseFetcher::fetch,
            io = updateIo,
            main = { task -> runOnUiThread(task) },
            now = { System.currentTimeMillis() }
        )

        adBlocker = AdBlocker(
            blockAds = { settings.get().blockAds },
            blockPopups = { settings.get().blockPopups },
            allowedSites = { settings.get().adsAllowedOn },
            onCountChanged = { n -> showBlockedCount(n) }
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
            onFallback = { url -> loadPage(url) },
            onNoApp = { Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show() }
        )

        webClient = SniffingWebViewClient(
            sniffer,
            adBlocker,
            onNavigate = { url -> updateUrlText(url) },
            onLoaded = { url ->
                applyLoad(PageLoadEvent.Finished)
                if (httpErrorStatus != 0) scheduleEmptyPageCheck(url, httpErrorStatus)
                httpErrorUrl = null
                httpErrorStatus = 0
                recordHistory(url, webView.title)
                // Opening from home starts a fresh tab: drop any prior back-stack so
                // BACK at this page reaches root (close-tab) instead of walking old
                // pages / leftover about:blank entries from a previous tab.
                if (clearHistoryOnLoad) {
                    clearHistoryOnLoad = false
                    webView.clearHistory()
                }
                // The error page has committed (and a fresh tab's history is cleared) only now,
                // after the error was reported: bring Go back in line with what BACK will do.
                if (pageErrorView.visibility == View.VISIBLE) pageErrorView.setCanGoBack(canGoBackFromError())
            },
            onExternalScheme = { url -> externalLinks.launch(url) },
            onNavigationBlocked = { Toast.makeText(this, R.string.navigation_blocked, Toast.LENGTH_SHORT).show() },
            pageEvents = pageEvents
        )
        webView.webViewClient = webClient
        chromeClient = BrowserWebChromeClient(
            activity = this,
            container = layout,
            onEnter = { chrome.dismiss(); playChip.visibility = View.GONE; layout.invalidate() },
            onExit = { layout.invalidate() },
            onTitle = { url, title -> recordHistory(url, title) },
            onPopupBlocked = { Toast.makeText(this, R.string.popup_blocked, Toast.LENGTH_SHORT).show() },
            isBlockedPopup = { url -> adBlocker.isBlockedPopup(url) },
            launchExternal = { url -> externalLinks.launch(url) },
            onProgress = { applyLoad(PageLoadEvent.Progress(it)) },
            beforeLoad = { url -> webClient.expectNavigation(url) }
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
            onEdit = { fav -> FavoriteDialog.show(this, favorites, fav) { url -> homeView.refresh(url) } },
            onHistory = { showHistory(fromHome = true) },
            onSettings = { showSettings() },
            onUpdate = { release ->
                UpdateDialog.show(this, release, ::ensureStoragePermission, ::openObtainium)
            }
        )
        // Straight from the cache, so the line is there on the first frame; then once more if
        // today's check turns something up. Once per process launch, not per home show.
        homeView.showUpdate(updates.cachedBanner())
        updates.checkIfDue { homeView.showUpdate(it) }
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
        settingsView.bindFavorites(onExport = ::exportFavorites, onImport = ::importFavorites)
        pageErrorView.bind(onRetry = ::retryFromError, onBack = ::backFromError, onHome = ::closeToHome)
        // Focus that drops onto the page while the error screen owns it (the WebView took it back
        // after a certificate error from home, on the TV) is put back at once, rather than
        // costing the user a swallowed keypress in dispatchKeyEvent's recovery.
        // Capped per screen, so a page that keeps grabbing focus cannot ping-pong with it.
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (webViewGone || zoneOf(newFocus) != OverlayFocus.Zone.PAGE) return@addOnGlobalFocusChangeListener
            if (!errorScreenOwnsFocus() || errorFocusReseats >= MAX_ERROR_FOCUS_RESEATS) return@addOnGlobalFocusChangeListener
            errorFocusReseats++
            pageErrorView.post { if (errorScreenOwnsFocus()) pageErrorView.restoreFocus() }
        }

        layout.post { cursor.center(webView.width, webView.height) }

        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                startLoad(null)
                webView.goBack()
            }
            chrome.onInteracted()
        }
        reloadButton.setOnClickListener {
            startLoad(webView.url)
            webView.reload()
            chrome.onInteracted()
        }
        homeButton.setOnClickListener { showHome() }
        historyButton.setOnClickListener { showHistory(fromHome = false) }
        favoriteButton.setOnClickListener {
            toggleCurrentFavorite()
            chrome.onInteracted()
        }
        adBlockButton.setOnClickListener {
            toggleAdsForSite()
            chrome.onInteracted()
        }
        // The field sits mid-bar now, so LEFT/RIGHT must leave it rather than walk the caret
        // through the whole URL first. Typing happens in the IME, which takes the D-pad itself.
        urlInput.setOnKeyListener { v, keyCode, event ->
            val direction = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                else -> return@setOnKeyListener false
            }
            if (event.action == KeyEvent.ACTION_DOWN) v.focusSearch(direction)?.requestFocus()
            true
        }
        // The first edit turns the page's URL into the user's own text: plain, and no lock.
        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (showingPageUrl) return
                s.getSpans(0, s.length, ForegroundColorSpan::class.java).forEach { s.removeSpan(it) }
                urlInput.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            }
        })
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
        val crashed = crashedUrl
        val killed = rendererKilled
        crashedUrl = null
        rendererKilled = false
        when {
            // Recreated after the renderer died: say so and load nothing — not even the launch
            // link, which may be the very page that crashed — until the user picks Try again.
            crashed != null && isWebUrl(crashed) -> {
                hideAllOverlays() // home starts VISIBLE, and the screen would come up under it
                showPageError(PageErrorKind.CRASHED, crashed, "", rendererKilled = killed)
            }
            crashed != null -> showHome()
            link != null -> openUrl(link)
            else -> showHome()
        }
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
     *  first, then the caller shows the one it wants (or none, for openUrl). The address bar
     *  goes too: left up under an overlay, its buttons sit in the same focus search — the
     *  capsule's right-hand cluster is level with home's History and settings buttons, so
     *  RIGHT from home's address field jumped behind the overlay and got pulled back. */
    private fun hideAllOverlays() {
        chrome.dismiss()
        homeView.hide()
        historyView.hide()
        settingsView.hide()
        pageErrorView.hide()
    }

    /** The error screen is up with no overlay over it: D-pad focus belongs on it ([OverlayFocus]). */
    private fun errorScreenOwnsFocus(): Boolean = OverlayFocus.recoveryTarget(
        settingsVisible = settingsView.visibility == View.VISIBLE,
        historyVisible = historyView.visibility == View.VISIBLE,
        homeVisible = homeView.visibility == View.VISIBLE,
        errorVisible = pageErrorView.visibility == View.VISIBLE,
        focus = focusZone()
    ) == OverlayFocus.Zone.ERROR

    private fun overlayVisible(): Boolean = homeView.visibility == View.VISIBLE ||
        historyView.visibility == View.VISIBLE || settingsView.visibility == View.VISIBLE

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /**
     * The error screen for [url]. Under an overlay (a load that failed while history was open
     * over the page) it comes up without taking focus; closing the overlay hands it over.
     * [neverCommitted]: the load was cancelled before it committed (a refused certificate), so
     * the page it was started from is still current — see [ErrorNavigation].
     */
    private fun showPageError(
        kind: PageErrorKind,
        url: String,
        code: String,
        neverCommitted: Boolean = false,
        rendererKilled: Boolean = false,
    ) {
        errorKind = kind
        errorUrl = url
        errorNeverCommitted = neverCommitted
        errorFromFreshTab = ErrorNavigation.isFreshTab(clearHistoryOnLoad, webView.url)
        errorFocusReseats = 0
        // The capsule lives in CursorLayout, under this screen, and would stay in focus search.
        chrome.dismiss()
        pageErrorView.show(
            kind, url, code, canGoBackFromError(),
            mayTakeFocus = { !overlayVisible() },
            rendererKilled = rendererKilled
        )
    }

    private fun hidePageError() {
        if (pageErrorView.visibility != View.VISIBLE) return
        val hadFocus = pageErrorView.hasFocus()
        pageErrorView.hide()
        if (hadFocus) layout.requestFocus()
    }

    /** The WebView as it is now, for the pure [ErrorNavigation]. */
    private fun errorFacts(failedUrl: String) = ErrorNavigation.Facts(
        neverCommitted = errorNeverCommitted,
        freshTab = errorFromFreshTab,
        currentUrl = webView.url,
        failedUrl = failedUrl,
        canGoBack = webView.canGoBack()
    )

    /** Go back is offered only when it leads somewhere other than home; see [ErrorNavigation.back]. */
    private fun canGoBackFromError(): Boolean =
        errorUrl?.let { ErrorNavigation.showGoBack(errorFacts(it)) } ?: false

    /** Try again: a crash reloads into the fresh WebView; otherwise the failed URL again, by
     *  `reload()` only when it is the committed entry on screen ([ErrorNavigation.retry]). */
    private fun retryFromError() {
        val url = errorUrl ?: return
        if (errorKind == PageErrorKind.CRASHED) {
            openUrl(url)
            return
        }
        val retry = ErrorNavigation.retry(errorFacts(url))
        hidePageError()
        when (retry) {
            ErrorNavigation.Retry.RELOAD -> {
                startLoad(url)
                webView.reload()
            }
            ErrorNavigation.Retry.LOAD_FAILED_URL -> loadPage(url)
        }
    }

    /** Go back (and BACK on the error screen): uncover the page a refused load left in place,
     *  else the previous page, or home with nowhere to go back to ([ErrorNavigation.back]). */
    private fun backFromError() {
        val url = errorUrl
        val back = if (url == null) ErrorNavigation.Back.HOME else ErrorNavigation.back(errorFacts(url))
        when (back) {
            ErrorNavigation.Back.UNCOVER -> {
                hidePageError()
                layout.requestFocus()
            }
            ErrorNavigation.Back.GO_BACK -> {
                hidePageError()
                startLoad(null)
                webView.goBack()
            }
            ErrorNavigation.Back.HOME -> closeToHome()
        }
    }

    /**
     * An HTTP error page is often the site's own, and that is left alone: only a page that shows
     * nothing gets the error screen. Checked a second after it finished, since an app shell
     * served with a 4xx status is still empty at the finish and fills in from script, and it
     * has to be empty at [PageError.EMPTY_CHECKS] checks a second apart (one showing only a CSS
     * spinner at the first check is not covered). Any new navigation, and onDestroy, cancels it.
     */
    private fun scheduleEmptyPageCheck(url: String, status: Int, pass: Int = 1) {
        cancelEmptyPageCheck()
        val nav = navigation
        val check = Runnable {
            emptyPageCheck = null
            if (webViewGone || pageErrorView.visibility == View.VISIBLE) return@Runnable
            webView.evaluateJavascript(PageError.EMPTY_PAGE_SCRIPT) { result ->
                if (isDestroyed || webViewGone || nav != navigation || pageErrorView.visibility == View.VISIBLE) {
                    return@evaluateJavascript
                }
                when (PageError.emptyVerdict(pass, result)) {
                    PageError.EmptyVerdict.NOT_EMPTY -> Unit
                    PageError.EmptyVerdict.CHECK_AGAIN -> scheduleEmptyPageCheck(url, status, pass + 1)
                    PageError.EmptyVerdict.EMPTY ->
                        showPageError(PageErrorKind.SERVER, url, getString(R.string.page_error_code_http, status))
                }
            }
        }
        emptyPageCheck = check
        uiHandler.postDelayed(check, EMPTY_PAGE_CHECK_MS)
    }

    private fun cancelEmptyPageCheck() {
        emptyPageCheck?.let { uiHandler.removeCallbacks(it) }
        emptyPageCheck = null
    }

    private fun openUrl(url: String) {
        hideAllOverlays()
        layout.requestFocus()
        clearHistoryOnLoad = true
        adBlocker.onUserNavigation()
        loadPage(url)
        chrome.onPageInteracted()
        showNavHintOnce()
    }

    /**
     * Every app-initiated `loadUrl` of a web page goes through here: the client is told which URL
     * is coming (so a refused certificate on it is the main frame's, whenever `onPageStarted`
     * arrives — see [SniffingWebViewClient.expectNavigation]) and the loading feedback starts.
     */
    private fun loadPage(url: String) {
        webClient.expectNavigation(url)
        startLoad(url)
        webView.loadUrl(url)
    }

    /** Starts the loading feedback for a load the app begins; [url] is where it goes, if known. */
    private fun startLoad(url: String?) {
        startedUrl = url
        applyLoad(PageLoadEvent.Started)
    }

    /**
     * Folds [event] into [loadState] (the pure [PageLoad.reduce]) and applies it: the top line
     * follows `progress` while loading and fills then fades when the load ends, the cursor
     * ring follows `cursorBusy`. The timeout is armed on every Started and whenever the state
     * turns from idle to loading (a Progress can open the line too), and dropped when loading
     * stops, so nothing stays on screen without one.
     */
    private fun applyLoad(event: PageLoadEvent) {
        val wasLoading = loadState.loading
        loadState = PageLoad.reduce(loadState, event)
        val s = loadState
        if (s.loading) {
            if (event is PageLoadEvent.Started || !wasLoading) {
                uiHandler.removeCallbacks(loadTimeout)
                uiHandler.postDelayed(loadTimeout, PageLoad.TIMEOUT_MS)
            }
            loadingLine.animate().cancel()
            loadingLine.pivotX = 0f
            loadingLine.scaleX = s.progress / 100f
            loadingLine.alpha = 1f
            loadingLine.visibility = View.VISIBLE
        } else {
            uiHandler.removeCallbacks(loadTimeout)
            // Only a line that is fully up starts its fade: one already fading keeps going (a
            // second end event, Failed then Finished, must not restart it).
            if (loadingLine.visibility == View.VISIBLE && loadingLine.alpha == 1f) {
                loadingLine.animate().scaleX(1f).alpha(0f).setDuration(LINE_FADE_MS).withEndAction {
                    // a new load may have begun while this one faded out
                    if (!loadState.loading) loadingLine.visibility = View.GONE
                }
            }
        }
        layout.busy = s.cursorBusy
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

    /** Empty when it cannot be read, which VersionCompare treats as unknown — so, no banner. */
    private fun installedVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

    /**
     * Storage is free from API 29; below it the user is asked once, and only because they tapped
     * Download. The dialog stays ignorant of request codes — it just gets a yes or a no.
     */
    private fun ensureStoragePermission(onResult: (Boolean) -> Unit) {
        if (ApkDownloader.hasStoragePermission(this)) {
            onResult(true)
            return
        }
        pendingStoragePermission = onResult
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
    }

    /** Issue #48: a fixed file in Downloads, no picker — see [FavoritesBackupFile]. */
    private fun exportFavorites() {
        ensureStoragePermission { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.export_favorites_denied, Toast.LENGTH_LONG).show()
                return@ensureStoragePermission
            }
            val items = favorites.findAll()
            val path = FavoritesBackupFile.export(this, FavoritesBackup.toJson(items))
            val message = if (path != null) {
                getString(R.string.export_favorites_done, items.size, path)
            } else {
                getString(R.string.export_favorites_failed)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /** The system picker, opened in Downloads; the file comes back in [onActivityResult]. */
    @Suppress("DEPRECATION")
    private fun importFavorites() {
        val intent = FavoritesBackupFile.pickIntent()
        // Some TV boxes ship no DocumentsUI at all, and Android TV ones resolve the intent to a
        // framework stub instead — see DocumentPicker.
        if (!DocumentPicker.isUsable(intent.resolveActivity(packageManager)?.packageName)) {
            showNoPicker()
            return
        }
        try {
            startActivityForResult(intent, REQ_IMPORT_FAVORITES)
        } catch (e: ActivityNotFoundException) {
            showNoPicker()
        }
    }

    private fun showNoPicker() {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_favorites_no_picker_title)
            .setMessage(getString(R.string.import_favorites_no_picker, FavoritesBackup.FILE_NAME))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT_FAVORITES) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        importFrom(uri)
    }

    /**
     * Read [uri] off the main thread — promptly, while the picker's grant is live — then merge.
     * Malformed, empty or unreadable all leave the favorites exactly as they were.
     */
    private fun importFrom(uri: Uri) {
        Thread {
            val text = FavoritesBackupFile.read(this, uri)
            runOnUiThread { if (!isDestroyed) onImportRead(text) }
        }.start()
    }

    private fun onImportRead(text: String?) {
        val parsed = text?.let { FavoritesBackup.parse(it) }
        if (parsed == null || parsed.favorites.isEmpty()) {
            val problem = when {
                text == null -> R.string.import_favorites_unreadable
                parsed == null -> R.string.import_favorites_invalid
                else -> R.string.import_favorites_empty
            }
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            return
        }
        val summary = FavoritesBackup.summarize(favorites.findAll(), parsed.favorites)
        favorites.addAll(parsed.favorites)
        homeView.refresh()
        val message = if (parsed.skipped == 0) {
            getString(R.string.import_favorites_done, summary.added, summary.alreadyPresent)
        } else {
            getString(
                R.string.import_favorites_done_skipped,
                summary.added, summary.alreadyPresent, parsed.skipped
            )
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Obtainium is the one route that ends the update problem for good; the README says so too. */
    private fun openObtainium() {
        ExternalIntentLauncher(
            context = this,
            onFallback = { url -> openUrl(url) },
            // Without Obtainium installed this says what it is and where to get it, and stops
            // there. It deliberately does NOT offer to open Obtainium's release page here: mrowser
            // cannot install an APK, so that page is a dead end on a TV — the same chicken-and-egg
            // that makes this app's own update end at "saved to Downloads".
            onNoApp = {
                AlertDialog.Builder(this)
                    .setTitle(R.string.obtainium_title)
                    .setMessage(getString(R.string.obtainium_message, OBTAINIUM_URL))
                    .setPositiveButton(R.string.close, null)
                    .show()
            }
        ).launch(OBTAINIUM_ADD_URL)
    }

    private fun recordHistory(url: String, title: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val label = title?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url)
        history.record(HistoryEntry(label, url, System.currentTimeMillis()))
    }

    private fun updateUrlText(url: String) {
        showPageUrl(url)
        updateFavoriteIcon()
        updateShieldIcon()
    }

    /**
     * The current page in the address pill: host white, the rest grey, and for https a lock
     * in place of the scheme. What the field holds still submits to the same page — see
     * [UrlDisplay]. Anything that is not an http(s) URL is shown as it is.
     */
    private fun showPageUrl(url: String) {
        val parts = UrlDisplay.of(url)
        val text = SpannableString(parts?.text ?: url)
        if (parts != null) {
            val dim = getColor(R.color.text_dim)
            val hostStart = parts.lead.length
            val hostEnd = hostStart + parts.host.length
            if (hostStart > 0) text.setSpan(ForegroundColorSpan(dim), 0, hostStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(getColor(R.color.on_surface)), hostStart, hostEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (hostEnd < text.length) text.setSpan(ForegroundColorSpan(dim), hostEnd, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        showingPageUrl = true
        urlInput.setText(text)
        showingPageUrl = false
        val lock = if (parts?.secure == true) R.drawable.ic_lock else 0
        urlInput.setCompoundDrawablesRelativeWithIntrinsicBounds(lock, 0, 0, 0)
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

    /** A saved page gets the filled star in accent (on the focus disc too); otherwise the
     *  outline star in the bar's usual light/dark-on-focus tint. */
    private fun updateFavoriteIcon() {
        val saved = webView.url?.let { isFavorite(it) } ?: false
        favoriteButton.setImageResource(if (saved) R.drawable.ic_star_filled else R.drawable.ic_star)
        favoriteButton.imageTintList =
            if (saved) ColorStateList.valueOf(getColor(R.color.accent)) else getColorStateList(R.color.bar_icon_tint)
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

    /** Tint the shield accent (red) when ads are allowed on the current site; otherwise the
     *  bar's usual light/dark-on-focus tint. */
    private fun updateShieldIcon() {
        val allowed = adBlocker.isAllowlisted(webView.url?.let { UrlHost.of(it) })
        adBlockIcon.imageTintList =
            if (allowed) ColorStateList.valueOf(getColor(R.color.accent)) else getColorStateList(R.color.bar_icon_tint)
    }

    /** The shield's badge: this page's blocked count, hidden at zero. */
    private fun showBlockedCount(n: Int) {
        adBlockCount.text = n.toString()
        adBlockCount.visibility = if (n == 0) View.GONE else View.VISIBLE
        adBlockButton.contentDescription = if (n == 0) {
            getString(R.string.ad_block_button)
        } else {
            getString(R.string.ad_block_button_count, n)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized && !webViewGone) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && !webViewGone) webView.onResume()
        if (::sniffer.isInitialized && sniffer.hasStream() && !overlayVisible()) showChip()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_STORAGE) return
        val callback = pendingStoragePermission
        pendingStoragePermission = null
        callback?.invoke(
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // ServiceWorkerController is process-scoped, not Activity-scoped: its client keeps this
        // Activity reachable (client -> adBlocker -> onCountChanged -> adBlockButton -> this)
        // after finish(), along with the WebView and the ~740 KB BlockList. Clearing it here
        // also stops a recreated Activity's service-worker fetches from being filtered by this
        // now-stale AdBlocker (stale pageHost, stale settings cache).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) unregisterServiceWorkerAdBlock()
        updateIo.shutdownNow()
        uiHandler.removeCallbacks(loadTimeout)
        cancelEmptyPageCheck()
        loadingLine.animate().cancel()
        // Leaving for good (not the recreate() after a dead renderer, which is a configuration
        // change): a crash screen still pending must not greet the next cold launch.
        if (isFinishing && !isChangingConfigurations) {
            crashedUrl = null
            rendererKilled = false
        }
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
        if (webViewGone) return true // the renderer died and recreate() is under way: drive nothing
        if (event.action == KeyEvent.ACTION_DOWN) {
            val target = OverlayFocus.recoveryTarget(
                settingsVisible = settingsView.visibility == View.VISIBLE,
                historyVisible = historyView.visibility == View.VISIBLE,
                homeVisible = homeView.visibility == View.VISIBLE,
                errorVisible = pageErrorView.visibility == View.VISIBLE,
                focus = focusZone()
            )
            val recovered = when (target) {
                OverlayFocus.Zone.SETTINGS -> settingsView.restoreFocus()
                OverlayFocus.Zone.HISTORY -> historyView.restoreFocus()
                OverlayFocus.Zone.HOME -> homeView.restoreFocus()
                OverlayFocus.Zone.ERROR -> pageErrorView.restoreFocus()
                OverlayFocus.Zone.PAGE -> layout.requestFocus()
                else -> false
            }
            if (recovered) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Which of the five subtrees currently holds D-pad focus, for [OverlayFocus]. */
    private fun focusZone(): OverlayFocus.Zone = zoneOf(currentFocus)

    private fun zoneOf(view: View?): OverlayFocus.Zone {
        var v: View? = view
        while (v != null) {
            when (v) {
                settingsView -> return OverlayFocus.Zone.SETTINGS
                historyView -> return OverlayFocus.Zone.HISTORY
                homeView -> return OverlayFocus.Zone.HOME
                pageErrorView -> return OverlayFocus.Zone.ERROR
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
                // Return to wherever history was opened from. Back on the page, a load that failed
                // meanwhile has put the error screen up, and that takes the D-pad.
                if (historyFromHome) showHome()
                else if (!pageErrorView.restoreFocus()) layout.requestFocus()
            }
            homeView.visibility == View.VISIBLE -> confirmExit()
            pageErrorView.visibility == View.VISIBLE -> backFromError()
            else -> confirmCloseTab()
        }
    }

    /** BACK at the first page in history: confirm before closing the page to home. */
    private fun confirmCloseTab() {
        AlertDialog.Builder(this)
            .setTitle(R.string.close_tab_title)
            .setMessage(R.string.close_tab_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.close_tab) { _, _ -> closeToHome() }
            .show()
    }

    /** Close the page (blank it, so nothing keeps playing behind) and go home. */
    private fun closeToHome() {
        webView.loadUrl("about:blank")
        showHome()
    }

    /** BACK on the home overlay is the app's root: confirm before exiting to the launcher. */
    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }
            .show()
            .also { DestructiveButton.apply(it, AlertDialog.BUTTON_POSITIVE) }
    }

    companion object {
        private const val CHIP_TIMEOUT_MS = 30_000L
        private const val LINE_FADE_MS = 200L
        private const val EMPTY_PAGE_CHECK_MS = 1_000L
        private const val MAX_ERROR_FOCUS_RESEATS = 3
        private const val REQ_STORAGE = 101
        private const val REQ_IMPORT_FAVORITES = 102
        private const val OBTAINIUM_ADD_URL = "obtainium://add/https://github.com/m-salehi-v/mrowser"
        private const val OBTAINIUM_URL = "https://github.com/ImranR98/Obtainium"

        /**
         * Set when the renderer died and [recreate] is under way: the page that was showing
         * (empty when none was), for the new instance to show Page crashed for. Static because
         * the recreated Activity is a new object; read once and cleared in onCreate.
         */
        private var crashedUrl: String? = null

        /** With [crashedUrl]: the system killed the renderer to free memory, it did not crash. */
        private var rendererKilled = false
    }
}
