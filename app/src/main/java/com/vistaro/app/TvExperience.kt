package com.vistaro.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.vistaro.app.core.database.EpisodeEntity
import com.vistaro.app.core.database.WatchProgressEntity
import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.ContinueWatchingItem
import com.vistaro.app.core.model.MediaType
import com.vistaro.app.core.model.Movie
import com.vistaro.app.core.model.TvShow
import com.vistaro.app.core.model.CatalogSort
import com.vistaro.app.data.catalog.CatalogRepository
import com.vistaro.app.data.tmdb.tmdbImageUrl
import com.vistaro.app.data.sync.CatalogSyncStatus

private data class TvDestination(val route: String, val label: String)

private val tvDestinations = listOf(
    TvDestination("home", "Home"),
    TvDestination("movies", "Movies"),
    TvDestination("shows", "TV Shows"),
)

private val appDestinations = tvDestinations + listOf(
    TvDestination("search", "Search"),
    TvDestination("settings", "Settings"),
)

@Composable
fun TvAppNavigation(container: AppContainer, useBottomNavigation: Boolean = false) {
    TvMaterialTheme {
      MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvPalette.accent,
            onPrimary = TvPalette.background,
            background = TvPalette.background,
            surface = TvPalette.surface,
            onBackground = TvPalette.text,
            onSurface = TvPalette.text,
        ),
      ) {
        val navController = rememberNavController()
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val currentEntry = navController.currentBackStackEntryAsState().value
        val destinationRoute = currentEntry?.destination?.route
        val currentRoute = when (destinationRoute) {
            "genre/{type}/{genreId}/{genreName}", "categories/{type}", "viewAll/{type}/{sort}/{genreId}/{genreName}" ->
                if (currentEntry?.arguments?.getString("type") == MediaType.TV_SHOW.name) "shows" else "movies"
            "movie/{tmdbId}" -> "movies"
            "tv/{tmdbId}", "tv/{tmdbId}/season/{season}" -> "shows"
            else -> destinationRoute
        }
        val movies by container.catalogRepository.observeMovies().collectAsState(initial = emptyList())
        val shows by container.catalogRepository.observeTvShows().collectAsState(initial = emptyList())
        val favoriteRows by container.favoriteRepository.observeFavorites().collectAsState(initial = emptyList())
        val favorites = remember(favoriteRows) { favoriteRows.mapTo(mutableSetOf()) { "${it.contentType}:${it.contentId}" } }
        val watchRows by container.watchProgressRepository.observeInProgress().collectAsState(initial = emptyList())
        val continueItems = remember(watchRows, movies, shows) { watchRows.toContinueItems(movies, shows) }

        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
            LocalContentColor provides TvPalette.text,
        ) {
        Column(
            Modifier.fillMaxSize()
                .background(TvPalette.background)
                .then(if (useBottomNavigation) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier),
        ) {
            if (!useBottomNavigation) {
                TvTopNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { navController.navigateTopLevel(it) },
                    onSearch = { navController.navigateTopLevel("search") },
                    onSettings = { navController.navigateTopLevel("settings") },
                    onFavorites = { navController.navigateTopLevel("favorites") },
                )
            }
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.weight(1f),
            ) {
                composable("home") {
                    TvHomeBrowseScreen(
                        tmdbRepository = container.tmdbRepository,
                        continueItems = continueItems,
                        onContinue = { context.playContinueItem(it) },
                        onMovie = { navController.navigate("movie/$it") },
                        onShow = { navController.navigate("tv/$it") },
                        onViewAll = { type, sort, genreId, genreName -> navController.navigate(browseRoute(type, sort, genreId, genreName)) },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable("movies") {
                    TvMediaBrowseScreen(
                        type = MediaType.MOVIE,
                        repository = container.tmdbRepository,
                        onItem = { navController.navigate("movie/${it.tmdbId}") },
                        onGenre = { genre -> navController.navigate("genre/MOVIE/${genre.id}/${Uri.encode(genre.name)}") },
                        onViewAll = { type, sort, genreId, genreName -> navController.navigate(browseRoute(type, sort, genreId, genreName)) },
                        onViewAllGenres = { navController.navigate("categories/MOVIE") },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable("shows") {
                    TvMediaBrowseScreen(
                        type = MediaType.TV_SHOW,
                        repository = container.tmdbRepository,
                        onItem = { navController.navigate("tv/${it.tmdbId}") },
                        onGenre = { genre -> navController.navigate("genre/TV_SHOW/${genre.id}/${Uri.encode(genre.name)}") },
                        onViewAll = { type, sort, genreId, genreName -> navController.navigate(browseRoute(type, sort, genreId, genreName)) },
                        onViewAllGenres = { navController.navigate("categories/TV_SHOW") },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable("favorites") {
                    TvFavoritesScreen(
                        movies = movies,
                        shows = shows,
                        favoriteKeys = favorites,
                        onItem = { item -> navController.navigate(item.detailRoute()) },
                    )
                }
                composable("continue") {
                    TvContinueWatchingScreen(
                        items = continueItems,
                        onResume = { context.playContinueItem(it) },
                    )
                }
                composable("search") {
                    TvSearchScreen(container.tmdbRepository, compactLayout = useBottomNavigation) { item -> navController.navigate(item.detailRoute()) }
                }
                composable("settings") { TvSettingsScreen(container.tmdbRepository.isConfigured, container.catalogSyncManager) { navController.navigateTopLevel("favorites") } }
                composable(
                    "genre/{type}/{genreId}/{genreName}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("genreId") { type = NavType.IntType },
                        navArgument("genreName") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val type = runCatching { MediaType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(MediaType.MOVIE)
                    val genreId = entry.arguments?.getInt("genreId") ?: return@composable
                    val genreName = entry.arguments?.getString("genreName").orEmpty()
                    TvGenreBrowseScreen(
                        type = type,
                        genreId = genreId,
                        genreName = genreName,
                        repository = container.tmdbRepository,
                        onItem = { item -> navController.navigate(item.detailRoute()) },
                        onViewAll = { browseType, sort, id, name -> navController.navigate(browseRoute(browseType, sort, id, name)) },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable(
                    "categories/{type}",
                    arguments = listOf(navArgument("type") { type = NavType.StringType }),
                ) { entry ->
                    val type = runCatching { MediaType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(MediaType.MOVIE)
                    TvCategoriesBrowseScreen(
                        type = type,
                        repository = container.tmdbRepository,
                        onGenre = { genre -> navController.navigate("genre/${type.name}/${genre.id}/${Uri.encode(genre.name)}") },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable(
                    "viewAll/{type}/{sort}/{genreId}/{genreName}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("sort") { type = NavType.StringType },
                        navArgument("genreId") { type = NavType.IntType },
                        navArgument("genreName") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val type = runCatching { MediaType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(MediaType.MOVIE)
                    val sort = runCatching { CatalogSort.valueOf(entry.arguments?.getString("sort").orEmpty()) }.getOrDefault(CatalogSort.LATEST)
                    val genreId = entry.arguments?.getInt("genreId")?.takeIf { it > 0 }
                    val genreName = entry.arguments?.getString("genreName").orEmpty()
                    val heading = listOfNotNull(genreName.takeIf { it.isNotBlank() }, when (sort) {
                        CatalogSort.LATEST -> "Latest"
                        CatalogSort.TOP -> "Top Rated"
                        CatalogSort.POPULAR -> "Popular"
                    }, if (type == MediaType.MOVIE) "Movies" else "TV Shows").joinToString(" · ")
                    TvAllBrowseScreen(
                        type = type,
                        sort = sort,
                        genreId = genreId,
                        heading = heading,
                        repository = container.tmdbRepository,
                        onItem = { item -> navController.navigate(item.detailRoute()) },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable("movie/{tmdbId}", arguments = listOf(navArgument("tmdbId") { type = NavType.IntType })) { entry ->
                    val id = entry.arguments?.getInt("tmdbId") ?: return@composable
                    val movie by container.catalogRepository.observeMovie(id).collectAsState(initial = null)
                    var refreshAttempt by remember(id) { mutableIntStateOf(0) }
                    var metadataMessage by remember(id) { mutableStateOf("Loading movie details from TMDB…") }
                    LaunchedEffect(id, refreshAttempt) { metadataMessage = container.tmdbRepository.refreshMovie(id) ?: "" }
                    val key = "MOVIE:$id"
                    TvMovieDetailsScreen(
                        movie = movie,
                        metadataMessage = metadataMessage,
                        onRetryMetadata = { refreshAttempt++ },
                        isFavorite = key in favorites,
                        onToggleFavorite = { coroutineScope.launch { container.favoriteRepository.setFavorite(id, MediaType.MOVIE, key !in favorites) } },
                        onBack = { navController.popBackStack() },
                        onPlay = { context.playMedia(MediaType.MOVIE, id, movie?.imdbId, movie?.title.orEmpty()) },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable("tv/{tmdbId}", arguments = listOf(navArgument("tmdbId") { type = NavType.IntType })) { entry ->
                    val id = entry.arguments?.getInt("tmdbId") ?: return@composable
                    val show by container.catalogRepository.observeTvShow(id).collectAsState(initial = null)
                    var refreshAttempt by remember(id) { mutableIntStateOf(0) }
                    var metadataMessage by remember(id) { mutableStateOf("Loading TV details from TMDB…") }
                    LaunchedEffect(id, refreshAttempt) { metadataMessage = container.tmdbRepository.refreshTvShow(id) ?: "" }
                    val key = "TV_SHOW:$id"
                    TvShowDetailsScreen(
                        show = show,
                        metadataMessage = metadataMessage,
                        onRetryMetadata = { refreshAttempt++ },
                        isFavorite = key in favorites,
                        onToggleFavorite = { coroutineScope.launch { container.favoriteRepository.setFavorite(id, MediaType.TV_SHOW, key !in favorites) } },
                        onBack = { navController.popBackStack() },
                        onSeason = { season -> navController.navigate("tv/$id/season/$season") },
                        compactLayout = useBottomNavigation,
                    )
                }
                composable(
                    "tv/{tmdbId}/season/{season}",
                    arguments = listOf(navArgument("tmdbId") { type = NavType.IntType }, navArgument("season") { type = NavType.IntType }),
                ) { entry ->
                    val showId = entry.arguments?.getInt("tmdbId") ?: return@composable
                    val season = entry.arguments?.getInt("season") ?: return@composable
                    val show by container.catalogRepository.observeTvShow(showId).collectAsState(initial = null)
                    val episodes by container.tmdbRepository.observeEpisodes(showId).collectAsState(initial = emptyList())
                    var episodeMessage by remember(showId, season) { mutableStateOf("Loading episodes from TMDB…") }
                    var refreshAttempt by remember(showId, season) { mutableIntStateOf(0) }
                    LaunchedEffect(showId, season, refreshAttempt) {
                        episodeMessage = container.tmdbRepository.refreshSeason(showId, season) ?: ""
                    }
                    TvSeasonScreen(
                        show = show,
                        season = season,
                        episodes = episodes,
                        metadataMessage = episodeMessage,
                        onRetryEpisodes = { refreshAttempt++ },
                        onBack = { navController.popBackStack() },
                        compactLayout = useBottomNavigation,
                        onEpisode = { episode -> context.playMedia(MediaType.TV_SHOW, showId, show?.imdbId, show?.title.orEmpty(), season, episode) },
                    )
                }
            }
            if (useBottomNavigation) {
                NavigationBar(containerColor = TvPalette.surface) {
                    appDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = {
                                Text(
                                    when (destination.route) {
                                        "home" -> "⌂"
                                        "movies" -> "▣"
                                        "shows" -> "▤"
                                        "search" -> "⌕"
                                        else -> "⚙"
                                    },
                                    fontSize = 18.sp,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        }
        }
      }
    }
}

private fun browseRoute(type: MediaType, sort: CatalogSort, genreId: Int?, genreName: String): String =
    "viewAll/${type.name}/${sort.name}/${genreId ?: 0}/${Uri.encode(genreName.ifBlank { "All" })}"

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun Movie.toCatalogItem() = CatalogItem(tmdbId, MediaType.MOVIE, title, releaseDate?.take(4), posterPath, rating)
private fun TvShow.toCatalogItem() = CatalogItem(tmdbId, MediaType.TV_SHOW, title, firstAirDate?.take(4), posterPath, rating)
private fun CatalogItem.detailRoute() = if (type == MediaType.MOVIE) "movie/$tmdbId" else "tv/$tmdbId"

private fun List<WatchProgressEntity>.toContinueItems(movies: List<Movie>, shows: List<TvShow>): List<ContinueWatchingItem> = mapNotNull { progress ->
    val type = runCatching { MediaType.valueOf(progress.contentType) }.getOrNull() ?: return@mapNotNull null
    val catalogItem = when (type) {
        MediaType.MOVIE -> movies.firstOrNull { it.tmdbId == progress.contentId }?.toCatalogItem()
        MediaType.TV_SHOW -> shows.firstOrNull { it.tmdbId == progress.contentId }?.toCatalogItem()
    } ?: CatalogItem(progress.contentId, type, if (type == MediaType.MOVIE) "Movie ${progress.contentId}" else "TV Show ${progress.contentId}", null)
    val remaining = (progress.durationMs - progress.positionMs).coerceAtLeast(0)
    val label = if (type == MediaType.TV_SHOW) {
        "S${progress.seasonNumber} E${progress.episodeNumber} · ${remaining.readableDuration()} left"
    } else "Continue at ${progress.positionMs.readableDuration()} · ${remaining.readableDuration()} left"
    ContinueWatchingItem(catalogItem, progress.seasonNumber, progress.episodeNumber, progress.positionMs, progress.durationMs, label)
}

private fun Long.readableDuration(): String {
    val totalMinutes = this / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun Context.playMedia(type: MediaType, tmdbId: Int, imdbId: String?, title: String, season: Int = 0, episode: Int = 0) {
    startActivity(Intent(this, PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_TYPE, type.name)
        putExtra(PlayerActivity.EXTRA_TMDB_ID, tmdbId)
        putExtra(PlayerActivity.EXTRA_IMDB_ID, imdbId)
        putExtra(PlayerActivity.EXTRA_TITLE, title)
        putExtra(PlayerActivity.EXTRA_SEASON, season)
        putExtra(PlayerActivity.EXTRA_EPISODE, episode)
    })
}

private fun Context.playContinueItem(item: ContinueWatchingItem) =
    playMedia(item.item.type, item.item.tmdbId, null, item.item.title, item.season, item.episode)

@Composable
private fun TvTopNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onFavorites: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tvDestinations.forEach { destination ->
            val active = currentRoute?.let { it == destination.route || it.startsWith("${destination.route}/") } == true
            TvSurface(
                onClick = { onNavigate(destination.route) },
                modifier = Modifier,
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = if (active) Color(0xFF292A2D) else Color.Transparent,
                    focusedContainerColor = TvPalette.surfaceFocused,
                ),
                border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(border = androidx.compose.foundation.BorderStroke(2.dp, TvPalette.focus)),
                ),
            ) {
                Text(
                    destination.label,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
                    color = TvPalette.text,
                    fontSize = 17.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        TvNavigationAction("⌕  Search", currentRoute == "search", onSearch)
        TvNavigationAction("★  Favorites", currentRoute == "favorites", onFavorites)
        TvNavigationAction("⚙  Settings", currentRoute == "settings", onSettings)
        Spacer(Modifier.weight(1f))
        Text("VISTARO", color = TvPalette.muted, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun TvNavigationAction(label: String, active: Boolean, onClick: () -> Unit) {
    TvSurface(
        onClick = onClick,
        modifier = Modifier.height(48.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(7.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color(0xFF292A2D) else Color.Transparent,
            focusedContainerColor = TvPalette.surfaceFocused,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(border = androidx.compose.foundation.BorderStroke(2.dp, TvPalette.focus)),
        ),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = TvPalette.text, fontSize = 16.sp)
    }
}

@Composable
private fun TvCatalogRow(title: String, items: List<CatalogItem>, onItem: (CatalogItem) -> Unit) {
    Column {
        Text(title, modifier = Modifier.padding(bottom = 10.dp), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = 0),
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp),
        ) {
            items(items, key = { "${it.type}:${it.tmdbId}:$title" }) { item ->
                TvPosterCard(item, onClick = { onItem(item) })
            }
        }
    }
}

@Composable
private fun TvPosterCard(item: CatalogItem, onClick: () -> Unit) {
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(190.dp).height(220.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = TvPalette.surface,
            focusedContainerColor = TvPalette.surfaceFocused,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(border = androidx.compose.foundation.BorderStroke(3.dp, TvPalette.focus)),
        ),
    ) {
        tmdbImageUrl(item.posterPath)?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = "${item.title} poster",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color(0x99081018)))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
            Text(if (item.type == MediaType.MOVIE) "MOVIE" else "SERIES", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(item.title, modifier = Modifier.padding(top = 12.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(item.year.orEmpty(), modifier = Modifier.padding(top = 5.dp), color = TvPalette.muted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun TvSearchScreen(
    repository: com.vistaro.app.data.tmdb.TmdbRepository,
    compactLayout: Boolean = false,
    onItem: (CatalogItem) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<CatalogItem>()) }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, page, retry, repository) {
        if (query.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        if (page == 1) delay(350)
        loading = true
        error = null
        try {
            val response = repository.searchAvailable(query, page)
            if (page == 1) results = response.items
            else results = (results + response.items).distinctBy { "${it.type}:${it.tmdbId}" }.take(120)
            totalPages = response.totalPages.coerceAtMost(3)
            error = response.error
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (compactLayout) 16.dp else 44.dp, vertical = 24.dp)) {
        Text("Search", fontSize = if (compactLayout) 28.sp else 34.sp, fontWeight = FontWeight.Bold)
        TextField(
            value = query,
            onValueChange = {
                query = it
                page = 1
                results = emptyList()
                totalPages = 1
                error = null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            singleLine = true,
            label = { Text("Search movies and TV shows") },
        )
        Text(
            when {
                error != null -> error!!
                query.isBlank() -> "Search online for movies and TV shows"
                loading && results.isEmpty() -> "Searching TMDB…"
                results.isEmpty() -> "No matching movies or TV shows in the VidAPI catalog."
                else -> "${results.size} results"
            },
            modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
            color = TvPalette.muted,
            fontSize = 17.sp,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).focusRestorer()) {
            items(results, key = { "${it.type}:${it.tmdbId}" }) { item ->
                TvResultCard(item, onClick = { onItem(item) })
            }
            if (error != null) {
                item { TvButton(onClick = { retry++ }) { Text("Retry") } }
            }
            if (page < totalPages && !loading && error == null) {
                item { TvButton(onClick = { page++ }) { Text("Load more") } }
            }
            if (loading && results.isNotEmpty()) {
                item { Text("Loading more…", color = TvPalette.muted, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

@Composable
private fun TvResultCard(item: CatalogItem, onClick: () -> Unit) {
    AppClickableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        containerColor = TvPalette.surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${if (item.type == MediaType.MOVIE) "Movie" else "TV Show"}${item.year?.let { " · $it" }.orEmpty()}", color = TvPalette.muted, fontSize = 15.sp)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp)
        }
    }
}

@Composable
private fun TvMovieDetailsScreen(
    movie: Movie?,
    metadataMessage: String,
    onRetryMetadata: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    compactLayout: Boolean = false,
) {
    if (movie == null) return TvMissingDetails("Movie", onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = if (compactLayout) 18.dp else 48.dp, end = if (compactLayout) 18.dp else 48.dp, top = 18.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TvButton(onClick = onBack) { Text("Back") } }
        item {
            Column(Modifier.widthIn(max = 820.dp)) {
                Text("MOVIE", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(movie.title, modifier = Modifier.padding(top = 8.dp), fontSize = if (compactLayout) 30.sp else 42.sp, fontWeight = FontWeight.Bold)
                Text("${movie.releaseDate?.take(4).orEmpty()}  ·  ${movie.rating?.let { "★ ${"%.1f".format(it)}" } ?: "Not rated"}", modifier = Modifier.padding(top = 8.dp), color = TvPalette.muted, fontSize = 19.sp)
                Text(movie.overview, modifier = Modifier.padding(top = 16.dp), color = TvPalette.text, fontSize = if (compactLayout) 16.sp else 19.sp, lineHeight = if (compactLayout) 23.sp else 27.sp)
                tmdbImageUrl(movie.backdropPath, "w780")?.let { image ->
                    AsyncImage(image, contentDescription = "${movie.title} backdrop", modifier = Modifier.fillMaxWidth().height(if (compactLayout) 210.dp else 270.dp).padding(top = 16.dp), contentScale = ContentScale.Crop)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusGroup()) {
                TvButton(onClick = onPlay) { Text("Watch Now") }
                TvButton(onClick = onToggleFavorite) { Text(if (isFavorite) "Remove Favorite" else "Add to Favorites") }
            }
        }
        if (metadataMessage.isNotBlank()) {
            item {
                Column {
                    Text(metadataMessage, color = TvPalette.muted, fontSize = 15.sp)
                    TvButton(onClick = onRetryMetadata, modifier = Modifier.padding(top = 6.dp)) { Text("Retry metadata") }
                }
            }
        }
    }
}

@Composable
private fun TvShowDetailsScreen(
    show: TvShow?,
    metadataMessage: String,
    onRetryMetadata: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onSeason: (Int) -> Unit,
    compactLayout: Boolean = false,
) {
    if (show == null) return TvMissingDetails("TV show", onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = if (compactLayout) 18.dp else 48.dp, end = if (compactLayout) 18.dp else 48.dp, top = 18.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TvButton(onClick = onBack) { Text("Back") } }
        item {
            Column(Modifier.widthIn(max = 820.dp)) {
                Text("TV SERIES", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(show.title, modifier = Modifier.padding(top = 8.dp), fontSize = if (compactLayout) 30.sp else 42.sp, fontWeight = FontWeight.Bold)
                Text("${show.firstAirDate?.take(4).orEmpty()}  ·  ${show.numberOfSeasons} Seasons  ·  ${show.rating?.let { "★ ${"%.1f".format(it)}" } ?: "Not rated"}", modifier = Modifier.padding(top = 8.dp), color = TvPalette.muted, fontSize = 19.sp)
                Text(show.overview, modifier = Modifier.padding(top = 16.dp), color = TvPalette.text, fontSize = if (compactLayout) 16.sp else 19.sp, lineHeight = if (compactLayout) 23.sp else 27.sp)
                tmdbImageUrl(show.backdropPath, "w780")?.let { image ->
                    AsyncImage(image, contentDescription = "${show.title} backdrop", modifier = Modifier.fillMaxWidth().height(if (compactLayout) 210.dp else 270.dp).padding(top = 16.dp), contentScale = ContentScale.Crop)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusGroup()) {
                TvButton(onClick = { onSeason(1) }) { Text("Choose Season") }
                TvButton(onClick = onToggleFavorite) { Text(if (isFavorite) "Remove Favorite" else "Add to Favorites") }
            }
        }
        if (metadataMessage.isNotBlank()) {
            item {
                Column {
                    Text(metadataMessage, color = TvPalette.muted, fontSize = 15.sp)
                    TvButton(onClick = onRetryMetadata, modifier = Modifier.padding(top = 6.dp)) { Text("Retry metadata") }
                }
            }
        }
        item {
            TvCatalogRow(
                "Seasons",
                (1..show.numberOfSeasons).map { season -> CatalogItem(show.tmdbId, MediaType.TV_SHOW, "Season $season", "$season · ${show.numberOfEpisodes / show.numberOfSeasons} episodes") },
                onItem = { item -> onSeason(item.title.removePrefix("Season ").toIntOrNull() ?: 1) },
            )
        }
    }
}

@Composable
private fun TvSeasonScreen(
    show: TvShow?,
    season: Int,
    episodes: List<EpisodeEntity>,
    metadataMessage: String,
    onRetryEpisodes: () -> Unit,
    onBack: () -> Unit,
    onEpisode: (Int) -> Unit,
    compactLayout: Boolean = false,
) {
    if (show == null) return TvMissingDetails("TV show", onBack)
    val count = if (season == show.numberOfSeasons) show.numberOfEpisodes - (season - 1) * (show.numberOfEpisodes / show.numberOfSeasons) else show.numberOfEpisodes / show.numberOfSeasons
    val episodeItems = episodes.filter { it.seasonNumber == season }.takeIf { it.isNotEmpty() }?.map { episode ->
        CatalogItem(episode.episodeNumber, MediaType.TV_SHOW, "Episode ${episode.episodeNumber} · ${episode.title}", episode.airDate ?: "S${season}E${episode.episodeNumber.toString().padStart(2, '0')}")
    } ?: (1..count.coerceAtLeast(1)).map { episode ->
        CatalogItem(episode, MediaType.TV_SHOW, "Episode $episode · ${if (episode == 1) "Pilot" else "The Story Continues"}", "S${season}E${episode.toString().padStart(2, '0')}")
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = if (compactLayout) 18.dp else 48.dp, end = if (compactLayout) 18.dp else 48.dp, top = 18.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { TvButton(onClick = onBack) { Text("Back to ${show.title}") } }
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(show.title, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                Text("Season $season", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("Choose an episode to continue", color = TvPalette.muted, fontSize = 17.sp)
            }
        }
        if (metadataMessage.isNotBlank()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(metadataMessage, color = TvPalette.muted, fontSize = 15.sp)
                    TvButton(onClick = onRetryEpisodes) { Text("Retry") }
                }
            }
        }
        items(episodeItems, key = { "episode-${it.tmdbId}" }) { episode ->
            TvSurface(
                onClick = { onEpisode(episode.tmdbId) },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(9.dp)),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = TvPalette.surface, focusedContainerColor = TvPalette.surfaceFocused),
                border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(border = androidx.compose.foundation.BorderStroke(3.dp, TvPalette.focus)),
                ),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(episode.year.orEmpty(), modifier = Modifier.width(100.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(episode.title, modifier = Modifier.weight(1f), fontSize = 18.sp)
                    Text("Play  ›", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun TvFavoritesScreen(movies: List<Movie>, shows: List<TvShow>, favoriteKeys: Set<String>, onItem: (CatalogItem) -> Unit) {
    val items = buildList {
        movies.filter { "MOVIE:${it.tmdbId}" in favoriteKeys }.forEach { add(it.toCatalogItem()) }
        shows.filter { "TV_SHOW:${it.tmdbId}" in favoriteKeys }.forEach { add(it.toCatalogItem()) }
    }
    TvCollectionScreen("Favorites", "Your saved movies and shows", items, emptyMessage = "No favorites yet. Add one from a details screen.", onItem = onItem)
}

@Composable
private fun TvContinueWatchingScreen(items: List<ContinueWatchingItem>, onResume: (ContinueWatchingItem) -> Unit) {
    val byKey = remember(items) { items.associateBy { "${it.item.type}:${it.item.tmdbId}" } }
    TvCollectionScreen(
        title = "Continue Watching",
        subtitle = "Resume where you left off",
        items = items.map { it.item.copy(year = it.progressLabel) },
        emptyMessage = "Continue Watching will appear here after you start playback.",
        onItem = { item -> byKey["${item.type}:${item.tmdbId}"]?.let(onResume) },
    )
}

@Composable
private fun TvContinueRow(onResume: () -> Unit) {
    Column {
        Text("Continue Watching", modifier = Modifier.padding(bottom = 10.dp), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = 0),
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(listOf("Breaking Bad", "Dune", "The Matrix")) { title ->
                TvSurface(
                    onClick = onResume,
                    modifier = Modifier.width(235.dp).height(142.dp),
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = TvPalette.surface, focusedContainerColor = TvPalette.surfaceFocused),
                    border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(border = androidx.compose.foundation.BorderStroke(3.dp, TvPalette.focus)),
                    ),
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                        Text("RESUME", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(title, modifier = Modifier.padding(top = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("S1 E2 · 23 min left", modifier = Modifier.padding(top = 4.dp), color = TvPalette.muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCollectionScreen(title: String, subtitle: String, items: List<CatalogItem>, emptyMessage: String, onItem: (CatalogItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 22.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, modifier = Modifier.padding(top = 6.dp), color = TvPalette.muted, fontSize = 17.sp)
            }
        }
        if (items.isEmpty()) {
            item {
                TvSurface(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = TvPalette.surface),
                ) { Text(emptyMessage, modifier = Modifier.padding(24.dp), fontSize = 18.sp) }
            }
        } else {
            items(items, key = { "collection-${it.type}-${it.tmdbId}" }) { item -> TvResultCard(item, onClick = { onItem(item) }) }
        }
    }
}

@Composable
private fun TvSettingsScreen(tmdbConfigured: Boolean, catalogSyncManager: com.vistaro.app.data.sync.CatalogSyncManager, onFavorites: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 22.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Settings", modifier = Modifier.padding(bottom = 12.dp), fontSize = 34.sp, fontWeight = FontWeight.Bold) }
        item { TvSettingSection("GENERAL") }
        item { CatalogSyncStatus(catalogSyncManager, isTv = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        item { TvSettingRow("Clear Catalog Cache", "Remove locally cached catalog data") }
        item { TvActionRow("Favorites", "Open saved movies and TV shows", onFavorites) }
        item { TvSettingSection("PLAYBACK") }
        item { TvSettingRow("Preferred Quality", "Default") }
        item { TvSettingRow("Autoplay", "On") }
        item { TvSettingSection("ABOUT") }
        item { TvSettingRow("TMDB Metadata", if (tmdbConfigured) "Access token configured" else "Add TMDB_ACCESS_TOKEN to secrets.properties") }
        item { Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", modifier = Modifier.padding(top = 8.dp), color = TvPalette.muted, fontSize = 14.sp) }
    }
}

@Composable
private fun TvActionRow(title: String, summary: String, onClick: () -> Unit) {
    AppClickableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        containerColor = TvPalette.surface,
        borderStroke = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF343638)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, modifier = Modifier.padding(top = 4.dp), color = TvPalette.muted, fontSize = 14.sp)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun TvSettingSection(title: String) {
    Text(title, modifier = Modifier.padding(top = 16.dp, bottom = 3.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun TvSettingRow(title: String, summary: String) {
    TvSurface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(9.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = TvPalette.surface, focusedContainerColor = TvPalette.surfaceFocused),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(border = androidx.compose.foundation.BorderStroke(3.dp, TvPalette.focus)),
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, modifier = Modifier.padding(top = 4.dp), color = TvPalette.muted, fontSize = 14.sp)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun TvMissingDetails(kind: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center) {
        Text("$kind not found in the local sample catalog.", fontSize = 22.sp)
        TvButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
    }
}
