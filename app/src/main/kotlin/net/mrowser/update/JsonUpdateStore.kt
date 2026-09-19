package net.mrowser.update

import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** UpdateStateRepository backed by a JSON file; pure logic delegated to UpdateJson. */
class JsonUpdateStore(private val file: File) : UpdateStateRepository {

    // Volatile: UpdateController writes this from its background executor while the UI thread
    // reads it through cachedBanner(). The record itself is immutable, but the reference still
    // needs a JMM guarantee to be seen across threads.
    @Volatile
    private var current: UpdateState =
        if (file.exists()) UpdateJson.fromJson(runCatching { file.readText() }.getOrDefault(""))
        else UpdateState()

    private val io = Executors.newSingleThreadExecutor()

    override fun get(): UpdateState = current

    override fun update(state: UpdateState) {
        current = state
        persist()
    }

    /** Serialize the (immutable) snapshot on the caller, then write off the caller's thread.
     *  The single-thread executor preserves write order; failures are logged, not swallowed. */
    private fun persist() {
        val snapshot = UpdateJson.toJson(current)
        io.execute {
            runCatching { file.writeText(snapshot) }
                .onFailure { Log.w(TAG, "persist failed: ${file.name}", it) }
        }
    }

    private companion object {
        private const val TAG = "JsonUpdateStore"
    }
}
