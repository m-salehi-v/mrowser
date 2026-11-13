package net.mrowser.player

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TrackSelectionDialogBuilder
import net.mrowser.R
import net.mrowser.data.JsonSettingsStore
import net.mrowser.data.SettingsRepository
import net.mrowser.stream.MediaMimeType
import net.mrowser.stream.PlaybackRequest
import java.io.File

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var subtitleController: SubtitleSyncController? = null
    private var playerView: PlayerView? = null
    private var streamUrl: String? = null

    // Read-only here; the browser writes it. Read when a stream's tracks arrive, so a toggle
    // flipped in Settings applies to the next stream with no restart.
    private val settings: SettingsRepository by lazy { JsonSettingsStore(File(filesDir, "settings.json")) }

    // Display-mode matching. The matcher runs once per stream (this activity is one stream);
    // the window's own preference before that is what onStop puts back, and onStart re-asks.
    private var displayModeMatched = false
    private var originalModeId = 0
    private var requestedModeId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)
        val playerView = findViewById<PlayerView>(R.id.playerView)
        this.playerView = playerView

        val json = intent.getStringExtra(EXTRA_REQUEST) ?: run { finish(); return }
        val request = PlaybackRequest.fromJson(json)
        streamUrl = request.url

        val trackSelector = DefaultTrackSelector(this)
        // DefaultMediaSourceFactory builds the source the request's MIME type asks for — HLS for
        // an .m3u8, DASH for an .mpd, progressive for a plain file. The player carries
        // no text track — side-loaded subtitles are rendered by SubtitleSyncController, not merged here.
        // Cookie is re-resolved per request host (via CookieManager) instead of being sent
        // blanket on every request: a session cookie scoped to the stream host must never
        // ride along to a different CDN/key/redirect host (cross-host session-cookie leak).
        val baseHeaders = request.headers.filterKeys { !it.equals("Cookie", ignoreCase = true) }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(baseHeaders)
            .setAllowCrossProtocolRedirects(true)
        val cookieManager = CookieManager.getInstance()
        val dataSourceFactory = ResolvingDataSource.Factory(httpFactory) { dataSpec ->
            val cookie = cookieManager.getCookie(dataSpec.uri.toString())
            if (cookie.isNullOrEmpty()) dataSpec
            else dataSpec.withAdditionalHeaders(mapOf("Cookie" to cookie))
        }
        // DefaultDataSource opens local URIs itself (a finished download's content:// or file://)
        // and hands http(s) to the resolving factory, so the per-host cookies above still apply.
        val exo = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(this, dataSourceFactory))
            )
            .build()
        player = exo
        playerView.player = exo

        // Replace the built-in control-bar gear's menu with ours, so video Quality sits
        // alongside Audio and Speed in the same gear. Re-applied whenever the controls
        // re-appear (the player can reset its own listeners).
        // Subtitle on/off + track selection is NOT done via media3's CC button: with no player
        // text track, media3 keeps re-disabling/greying it (its updateAll() wins the ordering),
        // so it's hidden (show_subtitle_button=false) and we use our own button inside the sync box.
        val syncBox = findViewById<View>(R.id.subSyncBox)
        val hasSubtitles = request.subtitles.isNotEmpty()
        val installControls = {
            playerView.findViewById<View?>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener {
                player?.let { showSettings(it) }
            }
        }
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                installControls()
                syncBox.visibility = if (hasSubtitles) visibility else View.GONE
            }
        )
        playerView.post { installControls() }

        // Pick up where this stream was left (BACK to the browser, then Play synced). With nothing
        // saved, no start position at all: an explicit 0 would pin a live stream to the start of
        // its window instead of the live edge.
        val startMs = resumePositions.startFor(request.url)
        if (startMs > 0) exo.setMediaItem(buildMediaItem(request), startMs)
        else exo.setMediaItem(buildMediaItem(request))
        originalModeId = window.attributes.preferredDisplayModeId
        exo.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                matchDisplayMode(settle = false)
            }

            // First decoded frame: the last chance for a frame rate, so settle with what we have.
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                matchDisplayMode(settle = true)
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Stream error — back to web player", Toast.LENGTH_LONG).show()
                finish()
            }

            // Played to the end: the next open starts over.
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) resumePositions.clear(request.url)
            }

            // TV ignores D-pad/touch during playback, so without this the system
            // sleep timeout fires mid-movie. Hold the wake lock only while actually
            // playing — a paused stream should still let the screen sleep.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        })
        exo.prepare()
        val subtitleView = playerView.findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)
        val syncValue = findViewById<TextView>(R.id.subSyncValue)
        showDelay(syncValue, 0L)
        val controller = SubtitleSyncController(exo, subtitleView, request.subtitles, request.headers) { off ->
            showDelay(syncValue, off)
        }
        subtitleController = controller
        findViewById<View>(R.id.subSyncSubs).setOnClickListener { showSubtitlePicker() }
        findViewById<View>(R.id.subSyncMinus).setOnClickListener { controller.adjust(-STEP_MS) }
        findViewById<View>(R.id.subSyncPlus).setOnClickListener { controller.adjust(STEP_MS) }
        controller.start()
        updateSubsButton()
        exo.playWhenReady = true
    }

    /**
     * Ask the display for the mode that fits the stream (DisplayModeMatcher decides), once its
     * video tracks are known. All the selected renditions are passed in, not just the one
     * playing: adaptive HLS starts on a small variant and climbs. A manifest without FRAME-RATE
     * leaves the tracks' rate unknown, so with rate matching on that waits for the decoder's
     * format at the first frame ([settle]) rather than giving up early. `preferredDisplayModeId` is
     * API 23 and switches resolution too, unlike media3's frame-rate strategy (seamless only,
     * API 30+). Most TVs blank for a moment on a switch, which is why both toggles are opt-in.
     */
    private fun matchDisplayMode(settle: Boolean) {
        if (displayModeMatched) return
        val p = player ?: return
        val formats = p.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSelected(it) }.map { g.getTrackFormat(it) } }
        val renditions = (formats + listOfNotNull(p.videoFormat))
            .map { DisplayModeMatcher.Video(it.width, it.height, it.frameRate) }
        val video = DisplayModeMatcher.target(renditions) ?: return
        val s = settings.get()
        if (!settle && s.matchFrameRate && video.fps == null) return
        displayModeMatched = true
        if (!s.matchFrameRate && !s.matchResolution) return

        @Suppress("DEPRECATION") // Activity.display is API 30; minSdk is 23
        val display = windowManager.defaultDisplay
        val modes = display.supportedModes.map { it.toMatcherMode() }
        val current = display.mode.toMatcherMode()
        val id = DisplayModeMatcher.pick(
            modes, current, video.fps, video.width, video.height, s.matchFrameRate, s.matchResolution
        )
        Log.d(TAG, "display modes=$modes current=$current video=$video -> ${id ?: "keep"}")
        if (id == null) return
        requestedModeId = id
        setPreferredDisplayMode(id)
    }

    private fun android.view.Display.Mode.toMatcherMode() =
        DisplayModeMatcher.Mode(modeId, physicalWidth, physicalHeight, refreshRate)

    private fun setPreferredDisplayMode(id: Int) {
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId == id) return
        attrs.preferredDisplayModeId = id
        window.attributes = attrs
    }

    private fun showSettings(p: ExoPlayer) {
        val items = arrayOf(
            getString(R.string.track_video),
            getString(R.string.track_audio),
            getString(R.string.speed)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> TrackSelectionDialogBuilder(this, items[0], p, C.TRACK_TYPE_VIDEO).build().show()
                    1 -> TrackSelectionDialogBuilder(this, items[1], p, C.TRACK_TYPE_AUDIO).build().show()
                    2 -> showSpeed(p)
                }
            }
            .show()
    }

    private fun showSubtitlePicker() {
        val c = subtitleController ?: return
        val labels = (listOf(getString(R.string.subtitle_off)) + c.trackLabels()).toTypedArray()
        // Single-choice: the showing track (or Off) carries the checked dot and takes first focus.
        AlertDialog.Builder(this)
            .setTitle(R.string.track_subtitles)
            .setSingleChoiceItems(labels, c.selected() + 1) { dialog, which ->
                c.select(which - 1) // entry 0 = Off -> index -1
                updateSubsButton()
                dialog.dismiss()
            }
            .show()
    }

    // Our own subtitle button (in the sync box) — the CC badge is accent when a track is showing
    // (on the white focus disc too), light when off and dark on the disc.
    private fun updateSubsButton() {
        val on = subtitleController?.isShowing() == true
        findViewById<ImageView>(R.id.subSyncSubs).imageTintList =
            if (on) ColorStateList.valueOf(getColor(R.color.accent)) else getColorStateList(R.color.bar_icon_tint)
    }

    /** "Subtitle delay +0.5s": the label in the box's text colour, the number white and medium. */
    private fun showDelay(view: TextView, offsetMs: Long) {
        val label = getString(R.string.sub_sync_label)
        // A true minus sign: as wide as "+" in tabular figures, so the box doesn't shift.
        val number = getString(R.string.sub_sync_value, offsetMs / 1000.0).replace('-', '\u2212')
        view.text = SpannableStringBuilder(label).append(' ').append(
            number,
            ForegroundColorSpan(getColor(R.color.on_surface)),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        ).apply {
            setSpan(TypefaceSpan("sans-serif-medium"), length - number.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showSpeed(p: ExoPlayer) {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map { "${it}x" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.speed)
            .setSingleChoiceItems(labels, speeds.indexOfFirst { it == p.playbackParameters.speed }) { dialog, w ->
                p.setPlaybackSpeed(speeds[w])
                dialog.dismiss()
            }
            .show()
    }

    private fun buildMediaItem(request: PlaybackRequest): MediaItem =
        MediaItem.Builder()
            .setUri(request.url)
            // Null for a progressive file: the factory reads the container itself, and a query
            // string can hide the extension it would otherwise guess a manifest from.
            .setMimeType(MediaMimeType.of(request.url))
            .build()

    override fun onStart() {
        super.onStart()
        subtitleController?.resume()
        if (requestedModeId != 0) setPreferredDisplayMode(requestedModeId)
    }

    // media3's PlayerView does not consume BACK, so the press that was meant to dismiss the
    // controls would close the player. Hide them first; exit only when they are already hidden.
    // The settings and subtitle dialogs take BACK themselves and never get here.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val view = playerView
        if (view != null && view.isControllerFullyVisible) {
            view.hideController()
            return
        }
        super.onBackPressed()
    }

    override fun onStop() {
        savePosition()
        super.onStop()
        setPreferredDisplayMode(originalModeId)
        player?.playWhenReady = false
        subtitleController?.stop()
    }

    private fun savePosition() {
        val p = player ?: return
        val url = streamUrl ?: return
        // No timeline yet (left while still loading, or failed before it loaded): nothing is
        // known about this stream, so keep whatever was saved rather than forget it.
        if (p.duration == C.TIME_UNSET) return
        resumePositions.record(url, p.currentPosition, p.duration, p.isCurrentMediaItemLive)
    }

    override fun onDestroy() {
        super.onDestroy()
        setPreferredDisplayMode(originalModeId)
        subtitleController?.stop()
        subtitleController = null
        player?.release()
        player = null
        playerView = null
    }

    companion object {
        const val EXTRA_REQUEST = "mrowser.request"
        private const val STEP_MS = 500L
        private const val TAG = "PlayerActivity"

        // Process-wide, so it outlives each PlayerActivity: BACK finishes the player, and the
        // next Play synced starts a new one.
        private val resumePositions = ResumePositions()
    }
}
