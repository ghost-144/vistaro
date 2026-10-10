package com.vistaro.app.data.catalog

import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.Movie
import com.vistaro.app.core.model.TvShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Keeps the sample catalog available until a cached TMDB catalog has been downloaded. */
class FallbackCatalogRepository(
    private val cached: CatalogRepository,
    private val fallback: CatalogRepository,
) : CatalogRepository {
    override fun observeMovies(limit: Int): Flow<List<Movie>> =
        combine(cached.observeMovies(limit), fallback.observeMovies(limit)) { stored, sample -> stored.ifEmpty { sample } }

    override fun observeTvShows(limit: Int): Flow<List<TvShow>> =
        combine(cached.observeTvShows(limit), fallback.observeTvShows(limit)) { stored, sample -> stored.ifEmpty { sample } }

    override fun observeMovie(tmdbId: Int): Flow<Movie?> =
        combine(cached.observeMovie(tmdbId), fallback.observeMovie(tmdbId)) { stored, sample -> stored ?: sample }

    override fun observeTvShow(tmdbId: Int): Flow<TvShow?> =
        combine(cached.observeTvShow(tmdbId), fallback.observeTvShow(tmdbId)) { stored, sample -> stored ?: sample }

    override fun search(query: String, limit: Int): Flow<List<CatalogItem>> =
        combine(cached.search(query, limit), fallback.search(query, limit)) { stored, sample ->
            (stored + sample).distinctBy { it.type to it.tmdbId }.take(limit)
        }

}
