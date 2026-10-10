package com.vistaro.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY popularity DESC LIMIT :limit")
    fun observeMovies(limit: Int = 100): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE tmdbId = :tmdbId LIMIT 1")
    fun observeMovie(tmdbId: Int): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE title LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%' ORDER BY popularity DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<MovieEntity>>

    @Upsert
    suspend fun upsertAll(movies: List<MovieEntity>)
}

@Dao
interface TvShowDao {
    @Query("SELECT * FROM tv_shows ORDER BY popularity DESC LIMIT :limit")
    fun observeTvShows(limit: Int = 100): Flow<List<TvShowEntity>>

    @Query("SELECT * FROM tv_shows WHERE tmdbId = :tmdbId LIMIT 1")
    fun observeTvShow(tmdbId: Int): Flow<TvShowEntity?>

    @Query("SELECT * FROM tv_shows WHERE title LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%' ORDER BY popularity DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<TvShowEntity>>

    @Upsert
    suspend fun upsertAll(shows: List<TvShowEntity>)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE showTmdbId = :showTmdbId ORDER BY seasonNumber, episodeNumber")
    fun observeEpisodes(showTmdbId: Int): Flow<List<EpisodeEntity>>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun delete(contentId: Int, contentType: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE completed = 0 AND positionMs > 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeInProgress(limit: Int = 10): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE contentId = :contentId AND contentType = :contentType AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber LIMIT 1")
    suspend fun get(contentId: Int, contentType: String, seasonNumber: Int, episodeNumber: Int): WatchProgressEntity?

    @Upsert
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress")
    suspend fun clear()
}

@Dao
interface CatalogMetadataDao {
    @Query("SELECT * FROM catalog_metadata WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): CatalogMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: CatalogMetadataEntity)

    @Query("SELECT * FROM catalog_metadata WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<CatalogMetadataEntity?>
}

@Dao
interface CatalogAvailabilityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(items: List<CatalogAvailabilityEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshotIds(items: List<CatalogIdSnapshotEntity>)

    @Query("DELETE FROM catalog_id_snapshot WHERE syncId != :syncId")
    suspend fun deleteSnapshotsExcept(syncId: Long)

    @Query("UPDATE catalog_availability SET available = 1, lastSeenSyncId = :syncId WHERE contentType = :contentType AND tmdbId IN (:ids)")
    suspend fun markSeen(contentType: String, syncId: Long, ids: List<Int>)

    @Query("UPDATE catalog_availability SET available = 0 WHERE contentType = :contentType AND lastSeenSyncId != :syncId")
    suspend fun markMissing(contentType: String, syncId: Long)

    @Query("SELECT a.tmdbId FROM catalog_availability a LEFT JOIN movies m ON m.tmdbId = a.tmdbId WHERE a.contentType = 'MOVIE' AND a.available = 1 AND a.metadataUnavailable = 0 AND (a.metadataAttemptedAt IS NULL OR a.metadataAttemptedAt < :staleBefore) AND (m.tmdbId IS NULL OR m.updatedAt < :staleBefore) ORDER BY a.tmdbId DESC LIMIT :limit")
    suspend fun moviesMissingMetadata(staleBefore: Long, limit: Int): List<Int>

    @Query("SELECT a.tmdbId FROM catalog_availability a LEFT JOIN tv_shows s ON s.tmdbId = a.tmdbId WHERE a.contentType = 'TV_SHOW' AND a.available = 1 AND a.metadataUnavailable = 0 AND (a.metadataAttemptedAt IS NULL OR a.metadataAttemptedAt < :staleBefore) AND (s.tmdbId IS NULL OR s.updatedAt < :staleBefore) ORDER BY a.tmdbId DESC LIMIT :limit")
    suspend fun showsMissingMetadata(staleBefore: Long, limit: Int): List<Int>

    @Query("SELECT COUNT(*) FROM catalog_availability a LEFT JOIN movies m ON m.tmdbId = a.tmdbId WHERE a.contentType = 'MOVIE' AND a.available = 1 AND a.metadataUnavailable = 0 AND (a.metadataAttemptedAt IS NULL OR a.metadataAttemptedAt < :staleBefore) AND (m.tmdbId IS NULL OR m.updatedAt < :staleBefore)")
    suspend fun countMoviesMissingMetadata(staleBefore: Long): Int

    @Query("SELECT COUNT(*) FROM catalog_availability a LEFT JOIN tv_shows s ON s.tmdbId = a.tmdbId WHERE a.contentType = 'TV_SHOW' AND a.available = 1 AND a.metadataUnavailable = 0 AND (a.metadataAttemptedAt IS NULL OR a.metadataAttemptedAt < :staleBefore) AND (s.tmdbId IS NULL OR s.updatedAt < :staleBefore)")
    suspend fun countShowsMissingMetadata(staleBefore: Long): Int

    @Query("SELECT COUNT(*) FROM catalog_availability WHERE contentType = :contentType AND available = 1")
    suspend fun countAvailable(contentType: String): Int

    @Query("SELECT tmdbId FROM catalog_availability WHERE contentType = :contentType AND available = 1 AND tmdbId IN (:ids)")
    suspend fun availableIds(contentType: String, ids: List<Int>): List<Int>

    @Query("SELECT tmdbId FROM catalog_id_snapshot WHERE contentType = :contentType AND syncId = :syncId AND tmdbId IN (:ids)")
    suspend fun idsInSnapshot(contentType: String, syncId: Long, ids: List<Int>): List<Int>

    @Query("UPDATE catalog_availability SET metadataAttemptedAt = :attemptedAt, metadataUnavailable = :unavailable WHERE contentType = :contentType AND tmdbId = :tmdbId")
    suspend fun recordMetadataAttempt(contentType: String, tmdbId: Int, attemptedAt: Long, unavailable: Boolean)
}
