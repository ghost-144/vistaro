package com.vistaro.app.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VidApiPlaybackProviderTest {
    private val provider = VidApiPlaybackProvider()

    @Test
    fun getMoviePlayback_withTmdbId_buildsCorrectUrl() {
        val info = provider.getMoviePlayback(tmdbId = 557, imdbId = null)
        assertEquals("https://vaplayer.ru/embed/movie/557", info.url)
    }

    @Test
    fun getMoviePlayback_withImdbId_prefersImdbId() {
        val info = provider.getMoviePlayback(tmdbId = 557, imdbId = "tt0145487")
        assertEquals("https://vaplayer.ru/embed/movie/tt0145487", info.url)
    }

    @Test
    fun getMoviePlayback_withResumeSeconds_addsQueryParam() {
        val info = provider.getMoviePlayback(tmdbId = 557, imdbId = null, resumeAtSeconds = 120)
        assertEquals("https://vaplayer.ru/embed/movie/557?resumeAt=120", info.url)
    }

    @Test
    fun getEpisodePlayback_buildsCorrectUrl() {
        val info = provider.getEpisodePlayback(
            tmdbId = 0,
            showTmdbId = 1399,
            season = 1,
            episode = 1,
            imdbId = null,
        )
        assertEquals("https://vaplayer.ru/embed/tv/1399/1/1", info.url)
    }

    @Test
    fun getEpisodePlayback_withResumeSeconds_addsQueryParam() {
        val info = provider.getEpisodePlayback(
            tmdbId = 0,
            showTmdbId = 1399,
            season = 2,
            episode = 3,
            imdbId = "tt0944947",
            resumeAtSeconds = 300,
        )
        assertEquals("https://vaplayer.ru/embed/tv/tt0944947/2/3?resumeAt=300", info.url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getMoviePlayback_invalidId_throwsException() {
        provider.getMoviePlayback(tmdbId = 0, imdbId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getEpisodePlayback_invalidSeason_throwsException() {
        provider.getEpisodePlayback(tmdbId = 0, showTmdbId = 1399, season = 0, episode = 1)
    }
}
