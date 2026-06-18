package net.mrowser.player

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageButton
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
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
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
            parameters = buildUponParameters()
                .setPreferredTextLanguage("fa")   // prefer Persian subtitles
                .build()
        }
        val exo = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        player = exo
        playerView.player = exo

        findViewById<ImageButton>(R.id.tracksButton).setOnClickListener { showTrackMenu(exo) }

        exo.setMediaSource(buildSource(request))
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Stream error — back to web player", Toast.LENGTH_LONG).show()
                finish()
            }
        })
        exo.prepare()
        exo.playWhenReady = true
    }

    /** Menu → pick a track type → media3's built-in selection dialog. */
    private fun showTrackMenu(target: ExoPlayer) {
        val labels = arrayOf(
            getString(R.string.track_video),
            getString(R.string.track_audio),
            getString(R.string.track_subtitles)
        )
        val types = intArrayOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)
        AlertDialog.Builder(this)
            .setTitle(R.string.tracks_title)
            .setItems(labels) { _, which ->
                TrackSelectionDialogBuilder(this, labels[which], target, types[which]).build().show()
            }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            player?.let { showTrackMenu(it) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun buildSource(request: PlaybackRequest): MediaSource {
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(request.headers)
            .setAllowCrossProtocolRedirects(true)
        val item = MediaItem.Builder()
            .setUri(request.url)
            .setSubtitleConfigurations(request.subtitles.mapIndexed { i, s -> s.toConfig(default = i == 0) })
            .build()
        return HlsMediaSource.Factory(dataSource).createMediaSource(item)
    }

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
