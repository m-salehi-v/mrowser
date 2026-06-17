package net.mrowser.data

interface FavoritesRepository {
    fun findAll(): List<Favorite>
    fun add(favorite: Favorite)
    fun remove(url: String)
    fun update(oldUrl: String, favorite: Favorite)
}
