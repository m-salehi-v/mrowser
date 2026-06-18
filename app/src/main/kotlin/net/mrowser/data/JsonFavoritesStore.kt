package net.mrowser.data

import java.io.File

/** FavoritesRepository backed by a JSON file; pure logic delegated to FavoritesOps/FavoritesJson. */
class JsonFavoritesStore(private val file: File) : FavoritesRepository {

    private var items: List<Favorite> =
        if (file.exists()) FavoritesJson.fromJson(file.readText()) else emptyList()

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

    private fun persist() {
        runCatching { file.writeText(FavoritesJson.toJson(items)) }
    }
}
