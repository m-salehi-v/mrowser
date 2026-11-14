package net.mrowser.web

/**
 * What the page-load feedback shows: [loading] is the top progress line (with its
 * [progress], 0..100) and [cursorBusy] is the busy ring around the D-pad cursor.
 */
data class PageLoadState(
    val loading: Boolean = false,
    val progress: Int = 0,
    val cursorBusy: Boolean = false,
)

sealed interface PageLoadEvent {
    /** A navigation began (click, URL bar, back/forward reported by `onPageStarted`). */
    data object Started : PageLoadEvent

    /**
     * A start for the navigation already under way: a clicked link reports
     * `shouldOverrideUrlLoading` first and `onPageStarted` for the same URL only at commit, and
     * an app-initiated load reports `onPageStarted` for the URL it already announced.
     */
    data object StartedAgain : PageLoadEvent

    /** `onProgressChanged`; any int, clamped to 0..100. */
    data class Progress(val percent: Int) : PageLoadEvent

    /** The page first became visible (`onPageCommitVisible`). */
    data object Committed : PageLoadEvent

    data object Finished : PageLoadEvent
    data object Failed : PageLoadEvent

    /** [PageLoad.TIMEOUT_MS] passed without Finished/Failed (download, same-document nav). */
    data object TimedOut : PageLoadEvent
}

/**
 * Pure reducer behind the loading line and the cursor's busy ring. Rules:
 *
 * - **Started** raises both signals at once and resets progress to 0 (a second click
 *   mid-load restarts cleanly).
 * - **StartedAgain** changes nothing while a load is running: the line keeps its progress and
 *   a ring that the commit already dropped stays off. With nothing loading it is a Started (a
 *   page reloading itself after it finished).
 * - **Progress** is clamped to 0..100 and only moves forward while loading. A tick that
 *   brings a loading state to 100 ends the load, exactly as Finished does (both signals
 *   off, progress 100), so a page at 100% never leaves an indicator behind. If we never
 *   saw Started (back/forward) a value below 100 raises the line alone; the cursor is
 *   not busy. A tick at 100 never opens the line, and once a load has Finished (state
 *   at 100, not loading) a late tick is ignored, so the line cannot be reopened with
 *   nothing left to close it. Progress never touches [PageLoadState.cursorBusy].
 * - **Committed** (the page is visible) drops the cursor ring only; the line keeps
 *   running until Finished. With nothing loading it changes nothing.
 * - **Finished** ends both signals, leaving progress at 100 (the line can fill, then hide).
 * - **Failed** and **TimedOut** end both signals and reset progress to 0. The state then
 *   equals the initial one, so a later below-100 Progress reopens the line (ring off);
 *   the glue's [TIMEOUT_MS] timer covers that.
 *
 * Every path other than Started (and a fresh below-100 Progress with no load) ends in
 * "not busy"; the glue starts a [TIMEOUT_MS] timer on Started so a navigation that never
 * reports an end still clears.
 */
object PageLoad {
    const val TIMEOUT_MS = 30_000L

    private val FINISHED = PageLoadState(loading = false, progress = 100, cursorBusy = false)

    fun reduce(state: PageLoadState, event: PageLoadEvent): PageLoadState = when (event) {
        PageLoadEvent.Started -> PageLoadState(loading = true, progress = 0, cursorBusy = true)
        PageLoadEvent.StartedAgain -> if (state.loading) state else reduce(state, PageLoadEvent.Started)
        is PageLoadEvent.Progress -> {
            val p = event.percent.coerceIn(0, 100)
            when {
                // reaching 100 while loading is a finish: nothing else is guaranteed to close the line
                state.loading && p >= 100 -> FINISHED
                state.loading -> state.copy(progress = maxOf(state.progress, p))
                // finished (progress 100, not loading) or a lone 100: nothing to show
                p >= 100 || state.progress >= 100 -> state
                else -> PageLoadState(loading = true, progress = p, cursorBusy = false)
            }
        }
        PageLoadEvent.Committed -> state.copy(cursorBusy = false)
        PageLoadEvent.Finished -> FINISHED
        PageLoadEvent.Failed, PageLoadEvent.TimedOut -> PageLoadState()
    }
}
