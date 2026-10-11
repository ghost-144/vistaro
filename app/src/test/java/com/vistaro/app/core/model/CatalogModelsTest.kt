package com.vistaro.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogModelsTest {

    @Test
    fun movieModel_properties() {
        val movie = Movie(
            tmdbId = 557,
            imdbId = "tt0145487",
            title = "Spider-Man",
            originalTitle = "Spider-Man",
            overview = "Bitten by a genetically altered spider...",
            releaseDate = "2002-05-03",
            rating = 7.4,
            voteCount = 18000,
            popularity = 85.5,
            posterPath = "/path.jpg",
            backdropPath = "/backdrop.jpg",
            originalLanguage = "en",
            updatedAt = System.currentTimeMillis(),
        )
        assertEquals(557, movie.tmdbId)
        assertEquals("Spider-Man", movie.title)
        assertEquals("tt0145487", movie.imdbId)
    }

    @Test
    fun tvShowModel_properties() {
        val show = TvShow(
            tmdbId = 1399,
            imdbId = "tt0944947",
            title = "Game of Thrones",
            originalTitle = "Game of Thrones",
            overview = "Seven noble families fight for control...",
            firstAirDate = "2011-04-17",
            rating = 8.4,
            voteCount = 22000,
            popularity = 95.0,
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            originalLanguage = "en",
            numberOfSeasons = 8,
            numberOfEpisodes = 73,
            updatedAt = System.currentTimeMillis(),
        )
        assertEquals(8, show.numberOfSeasons)
        assertEquals(73, show.numberOfEpisodes)
    }

    @Test
    fun watchProgress_calculationLogic() {
        val positionMs = 45_000L
        val durationMs = 120_000L
        val progressPercent = (positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        assertEquals(0.375f, progressPercent, 0.001f)

        val completed = positionMs >= durationMs * 0.95
        assertFalse(completed)

        val nearEndPositionMs = 118_000L
        val nearEndCompleted = nearEndPositionMs >= durationMs * 0.95
        assertTrue(nearEndCompleted)
    }
}
