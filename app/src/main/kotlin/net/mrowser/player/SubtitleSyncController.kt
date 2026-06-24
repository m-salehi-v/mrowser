package net.mrowser.player

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.SubtitleView
import net.mrowser.stream.SubtitleTrack

/**
 * Renders the selected side-loaded subtitle track with a live, user-adjustable time offset.
 *
 * The ExoPlayer instance has no text track (PlayerActivity stops passing subtitle configs),
 * so this is the only source of cues for [subtitleView]. A ticker re-pushes the cues active
 * at `currentPosition + offsetMs` every [TICK_MS]; offset changes therefore apply instantly.
 * All methods run on the main thread except the per-track fetch+parse, which is offloaded.
 */
@OptIn(UnstableApi::class)
class SubtitleSyncController(
    private val player: ExoPlayer,
    private val subtitleView: SubtitleView?,
    private val tracks: List<SubtitleTrack>,
    private val headers: Map<String, String>,
    private val onOffsetChanged: (Long) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val parsedCache = HashMap<Int, List<SubtitleCue>>()
    private var selectedIndex = -1
    private var offsetMs = 0L

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** Auto-select the first track (today's "auto-show first") and begin rendering. */
    fun start() {
        if (tracks.isNotEmpty()) select(0)
        handler.post(ticker)
        onOffsetChanged(offsetMs)
    }

    fun stop() {
        handler.removeCallbacks(ticker)
    }

    fun adjust(deltaMs: Long) {
        offsetMs = (offsetMs + deltaMs).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        onOffsetChanged(offsetMs)
    }

    fun trackLabels(): List<String> = tracks.map { it.label }

    /** index -1 = off; otherwise the track index. Fetches+parses on first use. */
    fun select(index: Int) {
        selectedIndex = index
        if (index in tracks.indices && !parsedCache.containsKey(index)) {
            val track = tracks[index]
            Thread {
                val text = SubtitleFetcher.fetch(track.url, headers)
                val cues = if (text != null) SubtitleCueParser.parse(text) else emptyList()
                handler.post { parsedCache[index] = cues }
            }.start()
        }
    }

    private fun render() {
        val cues = parsedCache[selectedIndex]
        if (selectedIndex < 0 || cues == null) {
            subtitleView?.setCues(emptyList())
            return
        }
        val active = ActiveCueFinder.activeAt(cues, player.currentPosition + offsetMs)
        subtitleView?.setCues(active.map { Cue.Builder().setText(it.text).build() })
    }

    companion object {
        private const val TICK_MS = 150L
        private const val MAX_OFFSET_MS = 30_000L
    }
}
