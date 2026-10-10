package com.vistaro.app.data.user

import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.core.database.WatchProgressEntity
import com.vistaro.app.core.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WatchProgressRepository(private val database: CatalogDatabase) {
    fun observeInProgress(limit: Int = 10): Flow<List<WatchProgressEntity>> =
        database.watchProgressDao().observeInProgress(limit * 4).map { rows ->
            rows.distinctBy { "${it.contentType}:${it.contentId}" }.take(limit)
        }

    suspend fun get(contentId: Int, type: MediaType, season: Int, episode: Int): WatchProgressEntity? =
        database.watchProgressDao().get(contentId, type.name, season, episode)

    suspend fun save(
        contentId: Int,
        type: MediaType,
        season: Int,
        episode: Int,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
    ) {
        if (contentId <= 0 || positionMs < 0 || durationMs <= 0) return
        database.watchProgressDao().upsert(
            WatchProgressEntity(
                contentId = contentId,
                contentType = type.name,
                seasonNumber = season,
                episodeNumber = episode,
                positionMs = positionMs.coerceAtMost(durationMs),
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
                completed = completed,
            ),
        )
    }

    suspend fun clear() = database.watchProgressDao().clear()
}
