package com.vistaro.app.data.catalog

import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.Movie
import com.vistaro.app.core.model.TvShow
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeMovies(limit: Int = 100): Flow<List<Movie>>
    fun observeTvShows(limit: Int = 100): Flow<List<TvShow>>
    fun observeMovie(tmdbId: Int): Flow<Movie?>
    fun observeTvShow(tmdbId: Int): Flow<TvShow?>
    fun search(query: String, limit: Int = 50): Flow<List<CatalogItem>>
}
