package com.vistaro.app.data.playback


/** Builds the official VidAPI embed URLs; stream/source resolution remains on VidAPI. */
class VidApiPlaybackProvider(
    private val baseUrl: String = "https://vaplayer.ru/embed/",
) : PlaybackProvider {
    override fun getMoviePlayback(tmdbId: Int, imdbId: String?, resumeAtSeconds: Long): PlaybackInfo {
        require(tmdbId > 0) { "A valid TMDB movie ID is required." }
        val id = imdbId?.takeIf { it.startsWith("tt") } ?: tmdbId.toString()
        return PlaybackInfo(buildUrl("movie", id, resumeAtSeconds))
    }

    override fun getEpisodePlayback(
        tmdbId: Int,
        showTmdbId: Int,
        season: Int,
        episode: Int,
        imdbId: String?,
        resumeAtSeconds: Long,
    ): PlaybackInfo {
        require(showTmdbId > 0 && season > 0 && episode > 0) { "Choose a valid TV episode before playback." }
        val id = imdbId?.takeIf { it.startsWith("tt") } ?: showTmdbId.toString()
        return PlaybackInfo(buildUrl("tv", id, resumeAtSeconds, season, episode))
    }

    private fun buildUrl(type: String, id: String, resumeAtSeconds: Long, season: Int? = null, episode: Int? = null): String {
        val base = baseUrl.trimEnd('/')
        val path = if (season != null && episode != null) "$base/$type/$id/$season/$episode" else "$base/$type/$id"
        return if (resumeAtSeconds > 0) "$path?resumeAt=$resumeAtSeconds" else path
    }
}
