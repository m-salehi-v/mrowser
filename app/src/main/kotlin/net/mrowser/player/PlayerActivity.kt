package net.mrowser.player

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
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
import androidx.media3.ui.TrackSelectionDialogBuilder
import net.mrowser.R
import net.mrowser.stream.PlaybackRequest
import net.mrowser.stream.SubtitleTrack

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)
        val playerView = findViewById<PlayerView>(R.id.playerView)

        val json = intent.getStringExtra(EXTRA_REQUEST) ?: run { finish(); return }
        val request = PlaybackRequest.fromJson(json)

        val trackSelector = DefaultTrackSelector(this).apply {
            request.preferredTextLanguage?.let {
                parameters = buildUponParameters().setPreferredTextLanguage(it).build()
            }
        }
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
        val installSettings = {
            playerView.findViewById<View?>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener {
                player?.let { showSettings(it) }
            }
        }
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { installSettings() }
        )
        playerView.post { installSettings() }

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
            .setSubtitleConfigurations(
                // Mirrors SubtitlePlan: the first track is the preferred-language track, so it is
                // the one marked default — and only when a preference is set (Off marks none).
                request.subtitles.mapIndexed { i, s ->
                    s.toConfig(default = i == 0 && request.preferredTextLanguage != null)
                }
            )
            .build()

    private fun SubtitleTrack.toConfig(default: Boolean): MediaItem.SubtitleConfiguration {
        val builder = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
            .setMimeType(if (mimeType == "application/x-subrip") MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
            .setLanguage(language)
            .setLabel(label)
        if (default) builder.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        return builder.build()
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_REQUEST = "mrowser.request"
    }
}
