package com.vistaro.app.data.mongo

import android.util.Log
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.core.database.EpisodeEntity
import com.vistaro.app.core.database.MovieEntity
import com.vistaro.app.core.database.TvShowEntity
import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document

class MongoCatalogRepository(
    private val mongoManager: MongoManager,
    private val database: CatalogDatabase,
) {
    suspend fun searchOnline(query: String, limit: Int = 40): List<CatalogItem> = withContext(Dispatchers.IO) {
        val col = mongoManager.catalogCollection ?: return@withContext emptyList()
        val normalized = query.trim()
        if (normalized.isBlank()) return@withContext emptyList()

        try {
            // Match via regex on title or originalTitle (case-insensitive)
            val filter = Filters.or(
                Filters.regex("title", "(?i)$normalized"),
                Filters.regex("originalTitle", "(?i)$normalized"),
            )
            val documents = col.find(filter).limit(limit).toList()

            val movies = mutableListOf<MovieEntity>()
            val shows = mutableListOf<TvShowEntity>()
            val items = mutableListOf<CatalogItem>()

            for (doc in documents) {
                val contentType = doc.getString("contentType") ?: "MOVIE"
                val tmdbId = doc.getInteger("tmdbId") ?: 0
                val title = doc.getString("title") ?: ""
                val originalTitle = doc.getString("originalTitle") ?: title
                val overview = doc.getString("overview") ?: ""
                val rating = doc.getDouble("rating")
                val posterPath = doc.getString("posterPath")
                val backdropPath = doc.getString("backdropPath")
                val originalLanguage = doc.getString("originalLanguage")
                val popularity = doc.getDouble("popularity") ?: 0.0
                val voteCount = doc.getInteger("voteCount") ?: 0
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                if (contentType == "MOVIE") {
                    val releaseDate = doc.getString("releaseDate")
                    val imdbId = doc.getString("imdbId")
                    movies.add(
                        MovieEntity(
                            tmdbId = tmdbId,
                            imdbId = imdbId,
                            title = title,
                            originalTitle = originalTitle,
                            overview = overview,
                            releaseDate = releaseDate,
                            rating = rating,
                            voteCount = voteCount,
                            popularity = popularity,
                            posterPath = posterPath,
                            backdropPath = backdropPath,
                            originalLanguage = originalLanguage,
                            updatedAt = updatedAt,
                        ),
                    )
                    items.add(
                        CatalogItem(
                            tmdbId = tmdbId,
                            type = MediaType.MOVIE,
                            title = title,
                            year = releaseDate?.take(4),
                            posterPath = posterPath,
                            rating = rating,
                        ),
                    )
                } else {
                    val firstAirDate = doc.getString("firstAirDate")
                    val imdbId = doc.getString("imdbId")
                    val numberOfSeasons = doc.getInteger("numberOfSeasons") ?: 0
                    val numberOfEpisodes = doc.getInteger("numberOfEpisodes") ?: 0
                    shows.add(
                        TvShowEntity(
                            tmdbId = tmdbId,
                            imdbId = imdbId,
                            title = title,
                            originalTitle = originalTitle,
                            overview = overview,
                            firstAirDate = firstAirDate,
                            rating = rating,
                            voteCount = voteCount,
                            popularity = popularity,
                            posterPath = posterPath,
                            backdropPath = backdropPath,
                            originalLanguage = originalLanguage,
                            numberOfSeasons = numberOfSeasons,
                            numberOfEpisodes = numberOfEpisodes,
                            updatedAt = updatedAt,
                        ),
                    )
                    items.add(
                        CatalogItem(
                            tmdbId = tmdbId,
                            type = MediaType.TV_SHOW,
                            title = title,
                            year = firstAirDate?.take(4),
                            posterPath = posterPath,
                            rating = rating,
                        ),
                    )
                }
            }

            // Cache matches into local Room database for 0ms future offline lookups
            if (movies.isNotEmpty()) database.movieDao().upsertAll(movies)
            if (shows.isNotEmpty()) database.tvShowDao().upsertAll(shows)

            items
        } catch (e: Exception) {
            Log.e(TAG, "Mongo search failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun saveMovie(movie: MovieEntity) = withContext(Dispatchers.IO) {
        val col = mongoManager.catalogCollection ?: return@withContext
        try {
            val doc = Document()
                .append("_id", "MOVIE:${movie.tmdbId}")
                .append("contentType", "MOVIE")
                .append("tmdbId", movie.tmdbId)
                .append("imdbId", movie.imdbId)
                .append("title", movie.title)
                .append("originalTitle", movie.originalTitle)
                .append("overview", movie.overview)
                .append("releaseDate", movie.releaseDate)
                .append("rating", movie.rating)
                .append("voteCount", movie.voteCount)
                .append("popularity", movie.popularity)
                .append("posterPath", movie.posterPath)
                .append("backdropPath", movie.backdropPath)
                .append("originalLanguage", movie.originalLanguage)
                .append("updatedAt", movie.updatedAt)

            col.replaceOne(Filters.eq("_id", "MOVIE:${movie.tmdbId}"), doc, ReplaceOptions().upsert(true))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save movie to Mongo: ${e.message}")
        }
    }

    suspend fun saveTvShow(show: TvShowEntity) = withContext(Dispatchers.IO) {
        val col = mongoManager.catalogCollection ?: return@withContext
        try {
            val doc = Document()
                .append("_id", "TV_SHOW:${show.tmdbId}")
                .append("contentType", "TV_SHOW")
                .append("tmdbId", show.tmdbId)
                .append("imdbId", show.imdbId)
                .append("title", show.title)
                .append("originalTitle", show.originalTitle)
                .append("overview", show.overview)
                .append("firstAirDate", show.firstAirDate)
                .append("rating", show.rating)
                .append("voteCount", show.voteCount)
                .append("popularity", show.popularity)
                .append("posterPath", show.posterPath)
                .append("backdropPath", show.backdropPath)
                .append("originalLanguage", show.originalLanguage)
                .append("numberOfSeasons", show.numberOfSeasons)
                .append("numberOfEpisodes", show.numberOfEpisodes)
                .append("updatedAt", show.updatedAt)

            col.updateOne(
                Filters.eq("_id", "TV_SHOW:${show.tmdbId}"),
                Updates.combine(
                    Updates.set("contentType", "TV_SHOW"),
                    Updates.set("tmdbId", show.tmdbId),
                    Updates.set("imdbId", show.imdbId),
                    Updates.set("title", show.title),
                    Updates.set("originalTitle", show.originalTitle),
                    Updates.set("overview", show.overview),
                    Updates.set("firstAirDate", show.firstAirDate),
                    Updates.set("rating", show.rating),
                    Updates.set("voteCount", show.voteCount),
                    Updates.set("popularity", show.popularity),
                    Updates.set("posterPath", show.posterPath),
                    Updates.set("backdropPath", show.backdropPath),
                    Updates.set("originalLanguage", show.originalLanguage),
                    Updates.set("numberOfSeasons", show.numberOfSeasons),
                    Updates.set("numberOfEpisodes", show.numberOfEpisodes),
                    Updates.set("updatedAt", show.updatedAt),
                ),
                UpdateOptions().upsert(true),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save TV show to Mongo: ${e.message}")
        }
    }

    suspend fun saveSeasonEpisodes(showTmdbId: Int, seasonNumber: Int, episodes: List<EpisodeEntity>) = withContext(Dispatchers.IO) {
        val col = mongoManager.catalogCollection ?: return@withContext
        try {
            val episodeDocs = episodes.map { ep ->
                Document()
                    .append("tmdbId", ep.tmdbId)
                    .append("seasonNumber", ep.seasonNumber)
                    .append("episodeNumber", ep.episodeNumber)
                    .append("title", ep.title)
                    .append("overview", ep.overview)
                    .append("airDate", ep.airDate)
                    .append("rating", ep.rating)
                    .append("stillPath", ep.stillPath)
                    .append("updatedAt", ep.updatedAt)
            }
            col.updateOne(
                Filters.eq("_id", "TV_SHOW:$showTmdbId"),
                Updates.set("seasons.season_$seasonNumber", episodeDocs),
                UpdateOptions().upsert(true),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save season episodes to Mongo: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MongoCatalogRepository"
    }
}
