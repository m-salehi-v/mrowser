package net.mrowser.data

/** Pure list operations on favorites, keyed by url. */
object FavoritesOps {

    /** Upsert by url; the new/updated entry goes to the front. */
    fun add(list: List<Favorite>, favorite: Favorite): List<Favorite> =
        listOf(favorite) + list.filterNot { it.url == favorite.url }

    fun remove(list: List<Favorite>, url: String): List<Favorite> =
        list.filterNot { it.url == url }

    /** Replace the entry at oldUrl, keeping its position; no-op if absent. */
    fun update(list: List<Favorite>, oldUrl: String, favorite: Favorite): List<Favorite> =
        list.map { if (it.url == oldUrl) favorite else it }
}
