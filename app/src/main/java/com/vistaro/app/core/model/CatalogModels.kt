package com.vistaro.app.core.model

enum class MediaType {
    MOVIE,
    TV_SHOW,
}

data class Movie(
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val releaseDate: String?,
    val rating: Double?,
    val voteCount: Int,
    val popularity: Double,
    val posterPath: String?,
    val backdropPath: String?,
    val originalLanguage: String?,
    val updatedAt: Long,
)

data class TvShow(
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val firstAirDate: String?,
    val rating: Double?,
    val voteCount: Int,
    val popularity: Double,
    val posterPath: String?,
    val backdropPath: String?,
    val originalLanguage: String?,
    val numberOfSeasons: Int,
    val numberOfEpisodes: Int,
    val updatedAt: Long,
)

data class CatalogItem(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val year: String?,
    val posterPath: String? = null,
    val rating: Double? = null,
)

data class ContinueWatchingItem(
    val item: CatalogItem,
    val season: Int,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val progressLabel: String,
)

enum class CatalogSort { LATEST, TOP, POPULAR }

data class CatalogGenre(val id: Int, val name: String)

data class CatalogPage(
    val items: List<CatalogItem>,
    val page: Int,
    val totalPages: Int,
    val error: String? = null,
)
