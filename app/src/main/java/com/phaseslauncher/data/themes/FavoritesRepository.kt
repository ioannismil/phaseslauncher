package com.phaseslauncher.data.themes

import com.phaseslauncher.data.db.FavoriteEntity
import com.phaseslauncher.data.db.FavoritesDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AddFavoriteResult { Added, AlreadyFavorite, Full }

/**
 * Docks of up to [ThemeDefaults.MAX_FAVORITES] apps: the shared dock ([FavoriteEntity.SHARED_DOCK])
 * shown on every theme, plus the own dock of any theme that has one (keyed by theme id).
 */
class FavoritesRepository(private val dao: FavoritesDao) {

    private val mutex = Mutex()

    /** Dock id → ordered app keys. */
    val docks: Flow<Map<Long, List<String>>> =
        dao.observeAll().map { rows -> rows.groupBy({ it.dockId }, { it.appKey }) }

    /** The shared dock only. */
    val favoriteKeys: Flow<List<String>> = docks.map { it[FavoriteEntity.SHARED_DOCK].orEmpty() }

    suspend fun add(appKey: String, dockId: Long = FavoriteEntity.SHARED_DOCK): AddFavoriteResult = mutex.withLock {
        val current = dao.getFavoriteKeys(dockId)
        when {
            appKey in current -> AddFavoriteResult.AlreadyFavorite

            current.size >= ThemeDefaults.MAX_FAVORITES -> AddFavoriteResult.Full

            else -> {
                dao.setFavorites(current + appKey, dockId)
                AddFavoriteResult.Added
            }
        }
    }

    suspend fun remove(appKey: String, dockId: Long = FavoriteEntity.SHARED_DOCK) =
        mutex.withLock { dao.remove(dockId, listOf(appKey)) }

    suspend fun getKeys(dockId: Long = FavoriteEntity.SHARED_DOCK): List<String> = dao.getFavoriteKeys(dockId)

    /** Replaces (and reorders) a dock; extra entries beyond the limit are dropped. */
    suspend fun setFavorites(orderedKeys: List<String>, dockId: Long = FavoriteEntity.SHARED_DOCK) = mutex.withLock {
        dao.setFavorites(orderedKeys.distinct().take(ThemeDefaults.MAX_FAVORITES), dockId)
    }
}
