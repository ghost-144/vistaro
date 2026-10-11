package com.vistaro.app.data.mongo

import android.util.Log
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.core.database.FavoriteEntity
import com.vistaro.app.core.database.WatchProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.bson.Document

class MongoUserRepository(
    private val mongoManager: MongoManager,
    private val database: CatalogDatabase,
) {
    suspend fun syncFromRemote(userId: String = DEFAULT_USER_ID) = withContext(Dispatchers.IO) {
        val col = mongoManager.usersCollection ?: return@withContext
        try {
            val userDoc = col.find(Filters.eq("_id", userId)).firstOrNull() ?: return@withContext

            // 1. Sync Favorites from Mongo -> Room
            val favDocs = userDoc.getList("favorites", Document::class.java).orEmpty()
            for (f in favDocs) {
                val contentId = f.getInteger("contentId") ?: continue
                val contentType = f.getString("contentType") ?: continue
                val createdAt = f.getLong("createdAt") ?: System.currentTimeMillis()
                database.favoriteDao().upsert(FavoriteEntity(contentId, contentType, createdAt))
            }

            // 2. Sync Watch Progress from Mongo -> Room
            val progressDocs = userDoc.getList("watchProgress", Document::class.java).orEmpty()
            for (p in progressDocs) {
                val contentId = p.getInteger("contentId") ?: continue
                val contentType = p.getString("contentType") ?: continue
                val seasonNumber = p.getInteger("seasonNumber") ?: 0
                val episodeNumber = p.getInteger("episodeNumber") ?: 0
                val positionMs = p.getLong("positionMs") ?: 0L
                val durationMs = p.getLong("durationMs") ?: 0L
                val updatedAt = p.getLong("updatedAt") ?: System.currentTimeMillis()
                val completed = p.getBoolean("completed") ?: false

                val local = database.watchProgressDao().get(contentId, contentType, seasonNumber, episodeNumber)
                // If remote has newer progress than local, update local
                if (local == null || updatedAt > local.updatedAt) {
                    database.watchProgressDao().upsert(
                        WatchProgressEntity(
                            contentId = contentId,
                            contentType = contentType,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            updatedAt = updatedAt,
                            completed = completed,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync from Mongo failed: ${e.message}")
        }
    }

    suspend fun saveWatchProgress(userId: String = DEFAULT_USER_ID, progress: WatchProgressEntity) = withContext(Dispatchers.IO) {
        val col = mongoManager.usersCollection ?: return@withContext
        try {
            val progressKey = "${progress.contentType}_${progress.contentId}_${progress.seasonNumber}_${progress.episodeNumber}"
            val progressDoc = Document()
                .append("key", progressKey)
                .append("contentId", progress.contentId)
                .append("contentType", progress.contentType)
                .append("seasonNumber", progress.seasonNumber)
                .append("episodeNumber", progress.episodeNumber)
                .append("positionMs", progress.positionMs)
                .append("durationMs", progress.durationMs)
                .append("updatedAt", progress.updatedAt)
                .append("completed", progress.completed)

            // Pull previous entry with this key then push updated one to keep array unique
            col.updateOne(
                Filters.eq("_id", userId),
                Updates.pull("watchProgress", Filters.eq("key", progressKey)),
                UpdateOptions().upsert(true),
            )
            col.updateOne(
                Filters.eq("_id", userId),
                Updates.combine(
                    Updates.set("updatedAt", System.currentTimeMillis()),
                    Updates.push("watchProgress", progressDoc),
                ),
                UpdateOptions().upsert(true),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save watch progress to Mongo: ${e.message}")
        }
    }

    suspend fun setFavorite(userId: String = DEFAULT_USER_ID, contentId: Int, contentType: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        val col = mongoManager.usersCollection ?: return@withContext
        try {
            val key = "${contentType}_$contentId"
            if (favorite) {
                val favDoc = Document()
                    .append("key", key)
                    .append("contentId", contentId)
                    .append("contentType", contentType)
                    .append("createdAt", System.currentTimeMillis())

                col.updateOne(
                    Filters.eq("_id", userId),
                    Updates.pull("favorites", Filters.eq("key", key)),
                    UpdateOptions().upsert(true),
                )
                col.updateOne(
                    Filters.eq("_id", userId),
                    Updates.push("favorites", favDoc),
                    UpdateOptions().upsert(true),
                )
            } else {
                col.updateOne(
                    Filters.eq("_id", userId),
                    Updates.pull("favorites", Filters.eq("key", key)),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update favorite in Mongo: ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_USER_ID = "default_user"
        private const val TAG = "MongoUserRepository"
    }
}
