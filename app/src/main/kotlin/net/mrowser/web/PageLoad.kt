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
 * - **Progress** is clamped to 0..100 and only moves forward while loading. If we never
 *   saw Started (back/forward) a value below 100 raises the line alone; the cursor is
 *   not busy. A tick at 100 never opens the line, and once a load has Finished (state
 *   at 100, not loading) a late tick is ignored, so the line cannot be reopened with
 *   nothing left to close it. Progress never touches [PageLoadState.cursorBusy].
 * - **Committed** (the page is visible) drops the cursor ring only; the line keeps
 *   running until Finished. With nothing loading it changes nothing.
 * - **Finished** ends both signals, leaving progress at 100 (the line can fill, then hide).
 * - **Failed** and **TimedOut** end both signals and reset progress to 0.
 *
 * Every path other than Started (and a fresh below-100 Progress with no load) ends in
 * "not busy"; the glue starts a [TIMEOUT_MS] timer on Started so a navigation that never
 * reports an end still clears.
 */
object PageLoad {
    const val TIMEOUT_MS = 30_000L

    fun reduce(state: PageLoadState, event: PageLoadEvent): PageLoadState = when (event) {
        PageLoadEvent.Started -> PageLoadState(loading = true, progress = 0, cursorBusy = true)
        is PageLoadEvent.Progress -> {
            val p = event.percent.coerceIn(0, 100)
            when {
                state.loading -> state.copy(progress = maxOf(state.progress, p))
                // finished (progress 100, not loading) or a lone 100: nothing to show
                p >= 100 || state.progress >= 100 -> state
                else -> PageLoadState(loading = true, progress = p, cursorBusy = false)
            }
        }
        PageLoadEvent.Committed -> state.copy(cursorBusy = false)
        PageLoadEvent.Finished -> PageLoadState(loading = false, progress = 100, cursorBusy = false)
        PageLoadEvent.Failed, PageLoadEvent.TimedOut -> PageLoadState()
    }
}
