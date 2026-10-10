package com.vistaro.app.data.playback

data class PlaybackInfo(val url: String)

interface PlaybackProvider {
    fun getMoviePlayback(tmdbId: Int, imdbId: String?, resumeAtSeconds: Long = 0): PlaybackInfo

    fun getEpisodePlayback(
        tmdbId: Int,
        showTmdbId: Int,
        season: Int,
        episode: Int,
        imdbId: String? = null,
        resumeAtSeconds: Long = 0,
    ): PlaybackInfo
}
