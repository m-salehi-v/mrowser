package net.mrowser.player

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TrackSelectionDialogBuilder
import net.mrowser.R
import net.mrowser.stream.PlaybackRequest

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var subtitleController: SubtitleSyncController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)
        val playerView = findViewById<PlayerView>(R.id.playerView)

        val json = intent.getStringExtra(EXTRA_REQUEST) ?: run { finish(); return }
        val request = PlaybackRequest.fromJson(json)

        val trackSelector = DefaultTrackSelector(this)
        // DefaultMediaSourceFactory (not HlsMediaSource.Factory) is what merges side-loaded
        // subtitles; it still builds an HlsMediaSource for the .m3u8.
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(request.headers)
            .setAllowCrossProtocolRedirects(true)
        val exo = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player = exo
        playerView.player = exo

        // Replace the built-in control-bar gear's menu with ours, so video Quality sits
        // alongside Audio and Speed in the same gear. Re-applied whenever the controls
        // re-appear (the player can reset its own listeners).
        val syncBox = findViewById<View>(R.id.subSyncBox)
        val hasSubtitles = request.subtitles.isNotEmpty()
        val installControls = {
            playerView.findViewById<View?>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener {
                player?.let { showSettings(it) }
            }
            // The CC button now drives our own picker (the player has no text track of its own).
            playerView.findViewById<View?>(androidx.media3.ui.R.id.exo_subtitle)?.setOnClickListener {
                showSubtitlePicker()
            }
        }
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                installControls()
                syncBox.visibility = if (hasSubtitles) visibility else View.GONE
            }
        )
        playerView.post { installControls() }

        exo.setMediaItem(buildMediaItem(request))
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Stream error — back to web player", Toast.LENGTH_LONG).show()
                finish()
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
        val controller = SubtitleSyncController(exo, subtitleView, request.subtitles, request.headers) { off ->
            syncValue.text = getString(R.string.sub_sync_value, off / 1000.0)
        }
        subtitleController = controller
        findViewById<View>(R.id.subSyncMinus).setOnClickListener { controller.adjust(-STEP_MS) }
        findViewById<View>(R.id.subSyncPlus).setOnClickListener { controller.adjust(STEP_MS) }
        controller.start()
        exo.playWhenReady = true
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
        AlertDialog.Builder(this)
            .setTitle(R.string.track_subtitles)
            .setItems(labels) { _, which -> c.select(which - 1) } // entry 0 = Off -> index -1
            .show()
    }

    private fun showSpeed(p: ExoPlayer) {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map { "${it}x" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.speed)
            .setItems(labels) { _, w -> p.setPlaybackSpeed(speeds[w]) }
            .show()
    }

    private fun buildMediaItem(request: PlaybackRequest): MediaItem =
        MediaItem.Builder()
            .setUri(request.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

    override fun onStart() {
        super.onStart()
        subtitleController?.resume()
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
        subtitleController?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        subtitleController?.stop()
        subtitleController = null
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_REQUEST = "mrowser.request"
        private const val STEP_MS = 500L
    }
}
