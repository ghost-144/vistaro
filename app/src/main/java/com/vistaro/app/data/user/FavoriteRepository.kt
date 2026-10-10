package com.vistaro.app.data.user

import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.core.database.FavoriteEntity
import com.vistaro.app.core.model.MediaType
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val database: CatalogDatabase) {
    fun observeFavorites(): Flow<List<FavoriteEntity>> = database.favoriteDao().observeFavorites()

    suspend fun setFavorite(tmdbId: Int, type: MediaType, favorite: Boolean) {
        val contentType = type.name
        if (favorite) database.favoriteDao().upsert(FavoriteEntity(tmdbId, contentType, System.currentTimeMillis()))
        else database.favoriteDao().delete(tmdbId, contentType)
    }

    suspend fun clear() = database.favoriteDao().clear()
}
