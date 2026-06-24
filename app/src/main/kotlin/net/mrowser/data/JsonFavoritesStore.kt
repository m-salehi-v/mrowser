package net.mrowser.data

import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** FavoritesRepository backed by a JSON file; pure logic delegated to FavoritesOps/FavoritesJson. */
class JsonFavoritesStore(private val file: File) : FavoritesRepository {

    private var items: List<Favorite> =
        if (file.exists()) FavoritesJson.fromJson(file.readText()) else emptyList()

    private val io = Executors.newSingleThreadExecutor()

    override fun findAll(): List<Favorite> = items

    override fun add(favorite: Favorite) {
        items = FavoritesOps.add(items, favorite); persist()
    }

    override fun remove(url: String) {
        items = FavoritesOps.remove(items, url); persist()
    }

    override fun update(oldUrl: String, favorite: Favorite) {
        items = FavoritesOps.update(items, oldUrl, favorite); persist()
    }

    /** Serialize the (immutable) snapshot on the caller, then write off the UI thread.
     *  The single-thread executor preserves write order; failures are logged, not swallowed. */
    private fun persist() {
        val snapshot = FavoritesJson.toJson(items)
        io.execute {
            runCatching { file.writeText(snapshot) }
                .onFailure { Log.w(TAG, "persist failed: ${file.name}", it) }
        }
    }

    private companion object {
        private const val TAG = "JsonFavoritesStore"
    }
}
