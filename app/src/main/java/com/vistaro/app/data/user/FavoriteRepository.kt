package com.vistaro.app.data.user

import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.core.database.FavoriteEntity
import com.vistaro.app.core.model.MediaType
import kotlinx.coroutines.flow.Flow

import com.vistaro.app.data.mongo.MongoUserRepository

class FavoriteRepository(
    private val database: CatalogDatabase,
    private val mongoUserRepository: MongoUserRepository? = null,
) {
    fun observeFavorites(): Flow<List<FavoriteEntity>> = database.favoriteDao().observeFavorites()

    suspend fun setFavorite(tmdbId: Int, type: MediaType, favorite: Boolean) {
        val contentType = type.name
        if (favorite) database.favoriteDao().upsert(FavoriteEntity(tmdbId, contentType, System.currentTimeMillis()))
        else database.favoriteDao().delete(tmdbId, contentType)
        mongoUserRepository?.setFavorite(contentId = tmdbId, contentType = contentType, favorite = favorite)
    }

    suspend fun clear() = database.favoriteDao().clear()
}
