package com.vistaro.app.data.catalog

import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.core.database.toModel
import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.MediaType
import com.vistaro.app.core.model.Movie
import com.vistaro.app.core.model.TvShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomCatalogRepository(private val database: CatalogDatabase) : CatalogRepository {
    override fun observeMovies(limit: Int): Flow<List<Movie>> =
        database.movieDao().observeMovies(limit).map { entities -> entities.map { it.toModel() } }

    override fun observeTvShows(limit: Int): Flow<List<TvShow>> =
        database.tvShowDao().observeTvShows(limit).map { entities -> entities.map { it.toModel() } }

    override fun observeMovie(tmdbId: Int): Flow<Movie?> =
        database.movieDao().observeMovie(tmdbId).map { it?.toModel() }

    override fun observeTvShow(tmdbId: Int): Flow<TvShow?> =
        database.tvShowDao().observeTvShow(tmdbId).map { it?.toModel() }

    override fun search(query: String, limit: Int): Flow<List<CatalogItem>> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())

        val movieResults = database.movieDao().search(normalized, limit).map { movies ->
            movies.map { CatalogItem(it.tmdbId, MediaType.MOVIE, it.title, it.releaseDate?.take(4), it.posterPath) }
        }
        val showResults = database.tvShowDao().search(normalized, limit).map { shows ->
            shows.map { CatalogItem(it.tmdbId, MediaType.TV_SHOW, it.title, it.firstAirDate?.take(4), it.posterPath) }
        }
        return combine(movieResults, showResults) { movies, shows -> (movies + shows).take(limit) }
    }
}
