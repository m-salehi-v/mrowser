package net.mrowser.handoff

import android.content.Context
import android.content.Intent
import android.widget.Toast
import net.mrowser.player.PlayerActivity
import net.mrowser.stream.StreamSniffer

/** Turns a chip activation into a launched PlayerActivity. */
class HandoffController(
    private val context: Context,
    private val sniffer: StreamSniffer
) {
    fun play() {
        val request = sniffer.bestRequest()
        if (request == null) {
            Toast.makeText(context, "No stream detected yet", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_REQUEST, request.toJson())
        )
    }
}
