package net.mrowser.handoff

import android.content.Context
import android.content.Intent
import android.widget.Toast
import net.mrowser.player.PlayerActivity
import net.mrowser.stream.PlaybackRequest
import net.mrowser.stream.StreamSniffer

/** Turns a chip activation, or a Play on a downloaded video, into a launched PlayerActivity. */
class HandoffController(
    private val context: Context,
    private val sniffer: StreamSniffer
) {
    fun play() {
        sniffer.bestRequest()?.let(::play)
            ?: Toast.makeText(context, "No stream detected yet", Toast.LENGTH_SHORT).show()
    }

    fun play(request: PlaybackRequest) {
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_REQUEST, request.toJson())
        )
    }
}
