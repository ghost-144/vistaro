package com.vistaro.app.data.tmdb

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TmdbApi {
    @GET("trending/{media_type}/{time_window}")
    suspend fun trending(
        @Path("media_type") mediaType: String = "all",
        @Path("time_window") timeWindow: String = "day",
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US",
    ): TmdbPage<TmdbTrendingDto>

    @GET("movie/popular")
    suspend fun popularMovies(@Query("page") page: Int = 1, @Query("language") language: String = "en-US"): TmdbPage<TmdbMovieDto>

    @GET("tv/popular")
    suspend fun popularTvShows(@Query("page") page: Int = 1, @Query("language") language: String = "en-US"): TmdbPage<TmdbTvDto>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
    ): TmdbPage<TmdbMovieDto>

    @GET("search/tv")
    suspend fun searchTvShows(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
    ): TmdbPage<TmdbTvDto>

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String,
        @Query("with_genres") genreId: Int? = null,
        @Query("primary_release_date.lte") releasedBefore: String? = null,
        @Query("vote_count.gte") minimumVotes: Int? = null,
        @Query("language") language: String = "en-US",
    ): TmdbPage<TmdbMovieDto>

    @GET("discover/tv")
    suspend fun discoverTvShows(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String,
        @Query("with_genres") genreId: Int? = null,
        @Query("first_air_date.lte") airedBefore: String? = null,
        @Query("vote_count.gte") minimumVotes: Int? = null,
        @Query("language") language: String = "en-US",
    ): TmdbPage<TmdbTvDto>

    @GET("genre/movie/list")
    suspend fun movieGenres(@Query("language") language: String = "en-US"): TmdbGenrePage

    @GET("genre/tv/list")
    suspend fun tvGenres(@Query("language") language: String = "en-US"): TmdbGenrePage

    @GET("movie/{movie_id}")
    suspend fun movieDetails(@Path("movie_id") movieId: Int, @Query("language") language: String = "en-US"): TmdbMovieDto

    @GET("tv/{series_id}")
    suspend fun tvDetails(@Path("series_id") seriesId: Int, @Query("language") language: String = "en-US"): TmdbTvDto

    @GET("tv/{series_id}/season/{season_number}")
    suspend fun seasonDetails(
        @Path("series_id") seriesId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String = "en-US",
    ): TmdbSeasonDto
}

@Serializable
data class TmdbGenrePage(val genres: List<TmdbGenreDto> = emptyList())

@Serializable
data class TmdbGenreDto(val id: Int = 0, val name: String = "")

@Serializable
data class TmdbPage<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
data class TmdbMovieDto(
    val id: Int = 0,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    val overview: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
data class TmdbTvDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
    val overview: String = "",
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int = 0,
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
data class TmdbTrendingDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val overview: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class TmdbSeasonDto(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
)

@Serializable
data class TmdbEpisodeDto(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("still_path") val stillPath: String? = null,
)

class TmdbApiFactory(private val accessToken: String) {
    val isConfigured: Boolean get() = accessToken.isNotBlank()

    fun create(): TmdbApi {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
                .apply { if (accessToken.isNotBlank()) header("Authorization", "Bearer $accessToken") }
                .build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }
}
