package net.mrowser.player

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Button
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
        val qualityButton = findViewById<Button>(R.id.qualityButton)

        val json = intent.getStringExtra(EXTRA_REQUEST) ?: run { finish(); return }
        val request = PlaybackRequest.fromJson(json)

        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setPreferredTextLanguage("fa").build()
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

        // Quality lives next to the built-in controls: it shows/hides with them (no focus trap).
        // Audio + subtitle selection are handled by the player's own settings/CC controls.
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility -> qualityButton.visibility = visibility }
        )
        qualityButton.setOnClickListener {
            player?.let {
                TrackSelectionDialogBuilder(this, getString(R.string.track_video), it, C.TRACK_TYPE_VIDEO)
                    .build()
                    .show()
            }
        }

        exo.setMediaItem(buildMediaItem(request))
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Stream error — back to web player", Toast.LENGTH_LONG).show()
                finish()
            }
        })
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun buildMediaItem(request: PlaybackRequest): MediaItem =
        MediaItem.Builder()
            .setUri(request.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setSubtitleConfigurations(request.subtitles.mapIndexed { i, s -> s.toConfig(default = i == 0) })
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
