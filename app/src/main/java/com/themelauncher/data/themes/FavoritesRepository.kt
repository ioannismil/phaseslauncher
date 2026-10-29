package com.themelauncher.data.themes

import com.themelauncher.data.db.FavoritesDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AddFavoriteResult { Added, AlreadyFavorite, Full }

/** The dock: up to [ThemeDefaults.MAX_FAVORITES] apps shared by every theme. */
class FavoritesRepository(private val dao: FavoritesDao) {

    private val mutex = Mutex()

    val favoriteKeys: Flow<List<String>> = dao.observeFavoriteKeys()

    suspend fun add(appKey: String): AddFavoriteResult = mutex.withLock {
        val current = dao.getFavoriteKeys()
        when {
            appKey in current -> AddFavoriteResult.AlreadyFavorite
            current.size >= ThemeDefaults.MAX_FAVORITES -> AddFavoriteResult.Full
            else -> {
                dao.setFavorites(current + appKey)
                AddFavoriteResult.Added
            }
        }
    }

    suspend fun remove(appKey: String) = mutex.withLock { dao.remove(listOf(appKey)) }

    /** Replaces (and reorders) the favorites; extra entries beyond the limit are dropped. */
    suspend fun setFavorites(orderedKeys: List<String>) = mutex.withLock {
        dao.setFavorites(orderedKeys.distinct().take(ThemeDefaults.MAX_FAVORITES))
    }
}
