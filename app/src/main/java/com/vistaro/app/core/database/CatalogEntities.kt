package com.vistaro.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.vistaro.app.core.model.Movie
import com.vistaro.app.core.model.TvShow

@Entity(tableName = "movies")
data class MovieEntity(
    @androidx.room.PrimaryKey val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val releaseDate: String?,
    val rating: Double?,
    val voteCount: Int,
    val popularity: Double,
    val posterPath: String?,
    val backdropPath: String?,
    val originalLanguage: String?,
    val updatedAt: Long,
)

@Entity(tableName = "tv_shows")
data class TvShowEntity(
    @androidx.room.PrimaryKey val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val firstAirDate: String?,
    val rating: Double?,
    val voteCount: Int,
    val popularity: Double,
    val posterPath: String?,
    val backdropPath: String?,
    val originalLanguage: String?,
    val numberOfSeasons: Int,
    val numberOfEpisodes: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "episodes",
    primaryKeys = ["tmdbId"],
    indices = [Index("showTmdbId")],
    foreignKeys = [
        ForeignKey(
            entity = TvShowEntity::class,
            parentColumns = ["tmdbId"],
            childColumns = ["showTmdbId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EpisodeEntity(
    val tmdbId: Int,
    val showTmdbId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String,
    val airDate: String?,
    val rating: Double?,
    val stillPath: String?,
    val updatedAt: Long,
)

@Entity(tableName = "genres")
data class GenreEntity(
    @androidx.room.PrimaryKey val tmdbId: Int,
    val name: String,
)

@Entity(tableName = "favorites", primaryKeys = ["contentId", "contentType"])
data class FavoriteEntity(
    val contentId: Int,
    val contentType: String,
    val createdAt: Long,
)

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["contentId", "contentType", "seasonNumber", "episodeNumber"],
)
data class WatchProgressEntity(
    val contentId: Int,
    val contentType: String,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean,
)

@Entity(tableName = "catalog_metadata")
data class CatalogMetadataEntity(
    @androidx.room.PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "catalog_availability",
    primaryKeys = ["contentType", "tmdbId"],
    indices = [Index(value = ["contentType", "available", "lastSeenSyncId"])],
)
data class CatalogAvailabilityEntity(
    val contentType: String,
    val tmdbId: Int,
    val available: Boolean = true,
    val lastSeenSyncId: Long,
    val metadataAttemptedAt: Long? = null,
    val metadataUnavailable: Boolean = false,
)

@Entity(
    tableName = "catalog_id_snapshot",
    primaryKeys = ["syncId", "contentType", "tmdbId"],
    indices = [Index(value = ["syncId", "contentType", "tmdbId"])],
)
data class CatalogIdSnapshotEntity(
    val syncId: Long,
    val contentType: String,
    val tmdbId: Int,
)

fun MovieEntity.toModel() = Movie(
    tmdbId, imdbId, title, originalTitle, overview, releaseDate, rating, voteCount,
    popularity, posterPath, backdropPath, originalLanguage, updatedAt,
)

fun Movie.toEntity() = MovieEntity(
    tmdbId, imdbId, title, originalTitle, overview, releaseDate, rating, voteCount,
    popularity, posterPath, backdropPath, originalLanguage, updatedAt,
)

fun TvShowEntity.toModel() = TvShow(
    tmdbId, imdbId, title, originalTitle, overview, firstAirDate, rating, voteCount,
    popularity, posterPath, backdropPath, originalLanguage, numberOfSeasons,
    numberOfEpisodes, updatedAt,
)

fun TvShow.toEntity() = TvShowEntity(
    tmdbId, imdbId, title, originalTitle, overview, firstAirDate, rating, voteCount,
    popularity, posterPath, backdropPath, originalLanguage, numberOfSeasons,
    numberOfEpisodes, updatedAt,
)
