package com.vistaro.app.data.tmdb

import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.core.database.CatalogMetadataEntity
import com.vistaro.app.core.database.EpisodeEntity
import com.vistaro.app.core.database.toEntity
import com.vistaro.app.core.model.CatalogGenre
import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.CatalogPage
import com.vistaro.app.core.model.CatalogSort
import com.vistaro.app.core.model.MediaType
import com.vistaro.app.core.model.Movie
import com.vistaro.app.core.model.TvShow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.vistaro.app.data.mongo.MongoCatalogRepository

class TmdbRepository(
    private val apiFactory: TmdbApiFactory,
    private val database: CatalogDatabase,
    private val mongoCatalogRepository: MongoCatalogRepository? = null,
) {
    private val api: TmdbApi by lazy(apiFactory::create)
    private var lastPopularRefreshAt = 0L

    val isConfigured: Boolean get() = apiFactory.isConfigured

    suspend fun refreshPopularCatalog(force: Boolean = false): String? {
        if (!isConfigured) return "Add TMDB_ACCESS_TOKEN to secrets.properties to load live catalog data."
        val now = System.currentTimeMillis()
        if (!force && now - lastPopularRefreshAt < POPULAR_REFRESH_COOLDOWN_MS) return null
        return try {
            val movies = api.popularMovies().results
                .filter { it.id > 0 && it.title.isNotBlank() }
                .map { it.toModel(now) }
            val shows = api.popularTvShows().results
                .filter { it.id > 0 && it.name.isNotBlank() }
                .map { it.toModel(now) }
            database.withTransaction {
                if (movies.isNotEmpty()) database.movieDao().upsertAll(movies.map { it.toEntity() })
                if (shows.isNotEmpty()) database.tvShowDao().upsertAll(shows.map { it.toEntity() })
                database.catalogMetadataDao().upsert(
                    CatalogMetadataEntity("tmdb_popular_last_success", now.toString(), now),
                )
            }
            lastPopularRefreshAt = now
            null
        } catch (error: Exception) {
            error.userMessage()
        }
    }

    suspend fun refreshMovie(tmdbId: Int): String? {
        if (!isConfigured) return "Add TMDB_ACCESS_TOKEN to secrets.properties to load live movie details."
        return try {
            val movie = api.movieDetails(tmdbId).toModel(System.currentTimeMillis())
            database.movieDao().upsertAll(listOf(movie.toEntity()))
            null
        } catch (error: Exception) {
            error.userMessage()
        }
    }

    suspend fun refreshTvShow(tmdbId: Int): String? {
        if (!isConfigured) return "Add TMDB_ACCESS_TOKEN to secrets.properties to load live TV details."
        return try {
            val show = api.tvDetails(tmdbId).toModel(System.currentTimeMillis())
            database.tvShowDao().upsertAll(listOf(show.toEntity()))
            null
        } catch (error: Exception) {
            error.userMessage()
        }
    }

    fun observeEpisodes(showTmdbId: Int): Flow<List<EpisodeEntity>> =
        database.episodeDao().observeEpisodes(showTmdbId)

    suspend fun refreshSeason(showTmdbId: Int, seasonNumber: Int): String? {
        if (!isConfigured) return "Add TMDB_ACCESS_TOKEN to secrets.properties to load episodes."
        return try {
            val show = api.tvDetails(showTmdbId).toModel(System.currentTimeMillis())
            val season = api.seasonDetails(showTmdbId, seasonNumber)
            val episodeEntities = season.episodes.map { episode ->
                EpisodeEntity(
                    tmdbId = episode.id,
                    showTmdbId = showTmdbId,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    title = episode.name,
                    overview = episode.overview,
                    airDate = episode.airDate,
                    rating = episode.voteAverage,
                    stillPath = episode.stillPath,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            database.withTransaction {
                database.tvShowDao().upsertAll(listOf(show.toEntity()))
                database.episodeDao().upsertAll(episodeEntities)
            }
            mongoCatalogRepository?.let { mongo ->
                mongo.saveTvShow(show.toEntity())
                mongo.saveSeasonEpisodes(showTmdbId, seasonNumber, episodeEntities)
            }
            null
        } catch (error: Exception) {
            error.userMessage()
        }
    }

    suspend fun fetchGenres(type: MediaType): Result<List<CatalogGenre>> = runCatching {
        check(isConfigured) { "Add TMDB_ACCESS_TOKEN to secrets.properties to load categories." }
        val genres = if (type == MediaType.MOVIE) api.movieGenres().genres else api.tvGenres().genres
        genres.map { CatalogGenre(it.id, it.name) }
    }

    suspend fun fetchBrowsePage(
        type: MediaType,
        sort: CatalogSort,
        page: Int,
        genreId: Int? = null,
    ): CatalogPage {
        if (!isConfigured) return CatalogPage(emptyList(), page, page, "Add TMDB_ACCESS_TOKEN to secrets.properties to browse live lists.")
        return try {
            val beforeToday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val minimumVotes = if (sort == CatalogSort.TOP) 300 else null
            when (type) {
                MediaType.MOVIE -> {
                    val sortBy = when (sort) {
                        CatalogSort.LATEST -> "primary_release_date.desc"
                        CatalogSort.TOP -> "vote_average.desc"
                        CatalogSort.POPULAR -> "popularity.desc"
                    }
                    val response = api.discoverMovies(
                        page = page,
                        sortBy = sortBy,
                        genreId = genreId,
                        releasedBefore = beforeToday,
                        minimumVotes = minimumVotes,
                    )
                    val now = System.currentTimeMillis()
                    val models = response.results.filter { it.id > 0 && it.title.isNotBlank() }.map { it.toModel(now) }
                    val allowed = allowedIds(MediaType.MOVIE, models.map { it.tmdbId })
                    val visible = models.filter { it.tmdbId in allowed }
                    if (visible.isNotEmpty()) database.movieDao().upsertAll(visible.map { it.toEntity() })
                    CatalogPage(visible.map { CatalogItem(it.tmdbId, type, it.title, it.releaseDate?.take(4), it.posterPath, it.rating) }, response.page, response.totalPages)
                }
                MediaType.TV_SHOW -> {
                    val sortBy = when (sort) {
                        CatalogSort.LATEST -> "first_air_date.desc"
                        CatalogSort.TOP -> "vote_average.desc"
                        CatalogSort.POPULAR -> "popularity.desc"
                    }
                    val response = api.discoverTvShows(
                        page = page,
                        sortBy = sortBy,
                        genreId = genreId,
                        airedBefore = beforeToday,
                        minimumVotes = minimumVotes,
                    )
                    val now = System.currentTimeMillis()
                    val models = response.results.filter { it.id > 0 && it.name.isNotBlank() }.map { it.toModel(now) }
                    val allowed = allowedIds(MediaType.TV_SHOW, models.map { it.tmdbId })
                    val visible = models.filter { it.tmdbId in allowed }
                    if (visible.isNotEmpty()) database.tvShowDao().upsertAll(visible.map { it.toEntity() })
                    CatalogPage(visible.map { CatalogItem(it.tmdbId, type, it.title, it.firstAirDate?.take(4), it.posterPath, it.rating) }, response.page, response.totalPages)
                }
            }
        } catch (error: Exception) {
            CatalogPage(emptyList(), page, page, error.userMessage())
        }
    }

    suspend fun fetchTrending(timeWindow: String = "day"): CatalogPage {
        if (!isConfigured) return CatalogPage(emptyList(), 1, 1, "Add TMDB_ACCESS_TOKEN to secrets.properties to view trending titles.")
        return try {
            val response = api.trending(mediaType = "all", timeWindow = timeWindow, page = 1)
            val now = System.currentTimeMillis()
            val rawResults = response.results.filter { it.id > 0 && (!it.title.isNullOrBlank() || !it.name.isNullOrBlank()) }
            val movieDtos = rawResults.filter { it.mediaType == "movie" || (it.mediaType == null && !it.title.isNullOrBlank()) }
            val tvDtos = rawResults.filter { it.mediaType == "tv" || (it.mediaType == null && !it.name.isNullOrBlank()) }

            val allowedMovieIds = allowedIds(MediaType.MOVIE, movieDtos.map { it.id })
            val allowedTvIds = allowedIds(MediaType.TV_SHOW, tvDtos.map { it.id })

            val movieEntities = movieDtos.filter { it.id in allowedMovieIds }.map { dto ->
                Movie(
                    tmdbId = dto.id,
                    imdbId = null,
                    title = dto.title.orEmpty(),
                    originalTitle = dto.title.orEmpty(),
                    overview = dto.overview,
                    releaseDate = dto.releaseDate,
                    rating = dto.voteAverage,
                    voteCount = 0,
                    popularity = 0.0,
                    posterPath = dto.posterPath,
                    backdropPath = dto.backdropPath,
                    originalLanguage = null,
                    updatedAt = now,
                )
            }
            val tvEntities = tvDtos.filter { it.id in allowedTvIds }.map { dto ->
                TvShow(
                    tmdbId = dto.id,
                    imdbId = null,
                    title = dto.name.orEmpty(),
                    originalTitle = dto.name.orEmpty(),
                    overview = dto.overview,
                    firstAirDate = dto.firstAirDate,
                    rating = dto.voteAverage,
                    voteCount = 0,
                    popularity = 0.0,
                    posterPath = dto.posterPath,
                    backdropPath = dto.backdropPath,
                    originalLanguage = null,
                    numberOfSeasons = 1,
                    numberOfEpisodes = 1,
                    updatedAt = now,
                )
            }

            database.withTransaction {
                if (movieEntities.isNotEmpty()) database.movieDao().upsertAll(movieEntities.map { it.toEntity() })
                if (tvEntities.isNotEmpty()) database.tvShowDao().upsertAll(tvEntities.map { it.toEntity() })
            }

            val items = rawResults.mapNotNull { dto ->
                val isMovie = dto.mediaType == "movie" || (dto.mediaType == null && !dto.title.isNullOrBlank())
                if (isMovie && dto.id in allowedMovieIds) {
                    CatalogItem(
                        tmdbId = dto.id,
                        type = MediaType.MOVIE,
                        title = dto.title.orEmpty(),
                        year = dto.releaseDate?.take(4),
                        posterPath = dto.posterPath,
                        rating = dto.voteAverage,
                    )
                } else if (!isMovie && dto.id in allowedTvIds) {
                    CatalogItem(
                        tmdbId = dto.id,
                        type = MediaType.TV_SHOW,
                        title = dto.name.orEmpty(),
                        year = dto.firstAirDate?.take(4),
                        posterPath = dto.posterPath,
                        rating = dto.voteAverage,
                    )
                } else null
            }.take(20)

            CatalogPage(items, 1, 1)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CatalogPage(emptyList(), 1, 1, error.userMessage())
        }
    }


    suspend fun searchAvailable(query: String, page: Int): CatalogPage {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return CatalogPage(emptyList(), 1, 1)
        if (!isConfigured) return CatalogPage(emptyList(), page, page, "Add TMDB_ACCESS_TOKEN to secrets.properties to search online.")
        if (page !in 1..MAX_SEARCH_PAGES) return CatalogPage(emptyList(), page, page)

        val snapshotId = database.catalogMetadataDao().get(ID_SNAPSHOT_KEY)?.value?.toLongOrNull()
            ?: return CatalogPage(emptyList(), page, page, "Sync the VidAPI catalog in Settings before searching. The latest completed ID list is required.")

        return try {
            val (movieResponse, showResponse) = coroutineScope {
                val movies = async { api.searchMovies(normalizedQuery, page) }
                val shows = async { api.searchTvShows(normalizedQuery, page) }
                movies.await() to shows.await()
            }
            val now = System.currentTimeMillis()
            val movieModels = movieResponse.results.filter { it.id > 0 && it.title.isNotBlank() }.map { it.toModel(now) }
            val showModels = showResponse.results.filter { it.id > 0 && it.name.isNotBlank() }.map { it.toModel(now) }
            val availability = database.catalogAvailabilityDao()
            val movieIds = if (movieModels.isEmpty()) emptySet() else availability.idsInSnapshot("MOVIE", snapshotId, movieModels.map { it.tmdbId }).toSet()
            val showIds = if (showModels.isEmpty()) emptySet() else availability.idsInSnapshot("TV_SHOW", snapshotId, showModels.map { it.tmdbId }).toSet()
            val movies = movieModels.filter { it.tmdbId in movieIds }
            val shows = showModels.filter { it.tmdbId in showIds }
            database.withTransaction {
                if (movies.isNotEmpty()) database.movieDao().upsertAll(movies.map { it.toEntity() })
                if (shows.isNotEmpty()) database.tvShowDao().upsertAll(shows.map { it.toEntity() })
            }
            mongoCatalogRepository?.let { mongo ->
                movies.forEach { mongo.saveMovie(it.toEntity()) }
                shows.forEach { mongo.saveTvShow(it.toEntity()) }
            }
            val items = (movies.map { CatalogItem(it.tmdbId, MediaType.MOVIE, it.title, it.releaseDate?.take(4), it.posterPath, it.rating) } +
                shows.map { CatalogItem(it.tmdbId, MediaType.TV_SHOW, it.title, it.firstAirDate?.take(4), it.posterPath, it.rating) })
                .take(MAX_SEARCH_RESULTS_PER_PAGE)
            CatalogPage(items, page, minOf(maxOf(movieResponse.totalPages, showResponse.totalPages), MAX_SEARCH_PAGES))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val mongoFallback = mongoCatalogRepository?.searchOnline(normalizedQuery, MAX_SEARCH_RESULTS_PER_PAGE).orEmpty()
            if (mongoFallback.isNotEmpty()) {
                CatalogPage(mongoFallback, page, 1)
            } else {
                CatalogPage(emptyList(), page, page, error.userMessage())
            }
        }
    }

    private suspend fun allowedIds(type: MediaType, ids: List<Int>): Set<Int> {
        if (ids.isEmpty()) return emptySet()
        if (database.catalogMetadataDao().get("vidapi_catalog_id_snapshot") == null) return ids.toSet()
        val contentType = if (type == MediaType.MOVIE) "MOVIE" else "TV_SHOW"
        return database.catalogAvailabilityDao().availableIds(contentType, ids).toSet()
    }

    private fun Exception.userMessage(): String = when (this) {
        is HttpException -> when (code()) {
            401 -> "TMDB rejected the access token. Check TMDB_ACCESS_TOKEN in secrets.properties."
            404 -> "TMDB has no metadata for this item."
            else -> "TMDB is temporarily unavailable (HTTP ${code()}). Try again later."
        }
        is IOException -> "Could not reach TMDB. Check the internet connection and retry."
        else -> "Could not load TMDB data. Try again later."
    }

    private fun TmdbMovieDto.toModel(now: Long) = Movie(
        tmdbId = id,
        imdbId = imdbId,
        title = title,
        originalTitle = originalTitle.ifBlank { title },
        overview = overview,
        releaseDate = releaseDate,
        rating = voteAverage,
        voteCount = voteCount,
        popularity = popularity,
        posterPath = posterPath,
        backdropPath = backdropPath,
        originalLanguage = originalLanguage,
        updatedAt = now,
    )

    private fun TmdbTvDto.toModel(now: Long) = TvShow(
        tmdbId = id,
        imdbId = imdbId,
        title = name,
        originalTitle = originalName.ifBlank { name },
        overview = overview,
        firstAirDate = firstAirDate,
        rating = voteAverage,
        voteCount = voteCount,
        popularity = popularity,
        posterPath = posterPath,
        backdropPath = backdropPath,
        originalLanguage = originalLanguage,
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes,
        updatedAt = now,
    )

    companion object {
        private const val POPULAR_REFRESH_COOLDOWN_MS = 6 * 60 * 60 * 1000L
        private const val ID_SNAPSHOT_KEY = "vidapi_catalog_id_snapshot"
        private const val MAX_SEARCH_PAGES = 3
        private const val MAX_SEARCH_RESULTS_PER_PAGE = 40
    }
}
