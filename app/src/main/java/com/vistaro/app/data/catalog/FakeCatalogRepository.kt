package com.vistaro.app.data.catalog

import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.MediaType
import com.vistaro.app.core.model.Movie
import com.vistaro.app.core.model.TvShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeCatalogRepository : CatalogRepository {
    private val movies = listOf(
        Movie(603, "tt0133093", "The Matrix", "The Matrix", "A computer hacker learns that reality is a simulation and joins a rebellion against its controllers.", "1999-03-31", 8.2, 24500, 88.0, null, null, "en", 0L),
        Movie(245891, "tt2911666", "John Wick", "John Wick", "An ex-hitman returns to the criminal underworld after a devastating loss.", "2014-10-22", 7.4, 19000, 76.0, null, null, "en", 0L),
        Movie(27205, "tt1375666", "Inception", "Inception", "A skilled thief enters dreams to steal secrets and is offered a chance at redemption.", "2010-07-15", 8.4, 36000, 92.0, null, null, "en", 0L),
        Movie(438631, "tt1160419", "Dune", "Dune", "A young heir travels to a dangerous desert world that holds the key to humanity's future.", "2021-09-15", 7.8, 12000, 82.0, null, null, "en", 0L),
    )

    private val shows = listOf(
        TvShow(1396, "tt0903747", "Breaking Bad", "Breaking Bad", "A chemistry teacher turns to the drug trade after a life-changing diagnosis.", "2008-01-20", 9.5, 14000, 95.0, null, null, "en", 5, 62, 0L),
        TvShow(1399, "tt0944947", "Game of Thrones", "Game of Thrones", "Noble families struggle for power over the Seven Kingdoms.", "2011-04-17", 8.5, 22000, 91.0, null, null, "en", 8, 73, 0L),
        TvShow(100088, "tt1190634", "The Boys", "The Boys", "A group of vigilantes confronts superheroes who abuse their power.", "2019-07-26", 8.5, 9000, 87.0, null, null, "en", 4, 32, 0L),
    )

    override fun observeMovies(limit: Int): Flow<List<Movie>> = flowOf(movies.take(limit))

    override fun observeTvShows(limit: Int): Flow<List<TvShow>> = flowOf(shows.take(limit))

    override fun observeMovie(tmdbId: Int): Flow<Movie?> = flowOf(movies.firstOrNull { it.tmdbId == tmdbId })

    override fun observeTvShow(tmdbId: Int): Flow<TvShow?> = flowOf(shows.firstOrNull { it.tmdbId == tmdbId })

    override fun search(query: String, limit: Int): Flow<List<CatalogItem>> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return flowOf(emptyList())

        val results = buildList {
            movies.filter { it.title.contains(normalized, ignoreCase = true) || it.originalTitle.contains(normalized, ignoreCase = true) }
                .forEach { add(CatalogItem(it.tmdbId, MediaType.MOVIE, it.title, it.releaseDate?.take(4), it.posterPath)) }
            shows.filter { it.title.contains(normalized, ignoreCase = true) || it.originalTitle.contains(normalized, ignoreCase = true) }
                .forEach { add(CatalogItem(it.tmdbId, MediaType.TV_SHOW, it.title, it.firstAirDate?.take(4), it.posterPath)) }
        }.take(limit)

        return flowOf(results)
    }
}
