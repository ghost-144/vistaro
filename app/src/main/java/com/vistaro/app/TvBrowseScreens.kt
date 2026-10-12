package com.vistaro.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.vistaro.app.core.model.CatalogGenre
import com.vistaro.app.core.model.CatalogItem
import com.vistaro.app.core.model.CatalogPage
import com.vistaro.app.core.model.CatalogSort
import com.vistaro.app.core.model.ContinueWatchingItem
import com.vistaro.app.core.model.MediaType
import com.vistaro.app.data.tmdb.TmdbRepository
import com.vistaro.app.data.tmdb.tmdbImageUrl
import kotlinx.coroutines.flow.collect

internal object TvPalette {
    val background = Color(0xFF0A0C10)
    val surface = Color(0xFF121620)
    val surfaceLight = Color(0xFF1A2130)
    val surfaceFocused = Color(0xFF1E283C)
    val text = Color(0xFFF3F4F6)
    val muted = Color(0xFF9CA3AF)
    val focus = Color(0xFF60A5FA)
    val accent = Color(0xFF3B82F6)
    val accentHover = Color(0xFF2563EB)
    val accentGlow = Color(0x593B82F6)
    val ratingGold = Color(0xFFFBBF24)
    val cardBorder = Color(0x14FFFFFF)
    val badgeBg = Color(0xD90A0C10)
}

private data class TvFeedSpec(
    val title: String,
    val type: MediaType,
    val sort: CatalogSort,
    val genreId: Int? = null,
    val genreName: String = "",
)

@Composable
internal fun HeroSpotlightBanner(
    item: CatalogItem,
    tag: String = "SPOTLIGHT FEATURE",
    overview: String = "Watch the highest rated trending content streaming now in high definition.",
    compactLayout: Boolean = false,
    onWatch: () -> Unit,
) {
    AppClickableSurface(
        onClick = onWatch,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compactLayout) 220.dp else 340.dp),
        shape = RoundedCornerShape(14.dp),
        containerColor = TvPalette.surface,
        borderStroke = BorderStroke(1.dp, TvPalette.cardBorder),
    ) {
        Box(Modifier.fillMaxSize()) {
            val backdropUrl = tmdbImageUrl(item.posterPath, "w780")
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = "${item.title} backdrop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x330A0C10),
                                Color(0x880A0C10),
                                Color(0xF20A0C10),
                            ),
                        )
                    )
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (compactLayout) 16.dp else 32.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvPalette.accent)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tag,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
                Text(
                    text = item.title,
                    modifier = Modifier.padding(top = 8.dp),
                    color = TvPalette.text,
                    fontSize = if (compactLayout) 22.sp else 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.year ?: "2024",
                        color = TvPalette.muted,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "•",
                        color = TvPalette.muted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = if (item.type == MediaType.MOVIE) "Movie" else "Series",
                        color = TvPalette.muted,
                        fontSize = 13.sp,
                    )
                    if (item.rating != null && item.rating > 0.0) {
                        Text(
                            text = "•",
                            color = TvPalette.muted,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "★ ${"%.1f".format(item.rating)} TMDB",
                            color = TvPalette.ratingGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (!compactLayout) {
                    Text(
                        text = overview,
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFFD1D5DB),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TvPalette.accent)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "▶  Watch Now",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TvHomeBrowseScreen(
    tmdbRepository: TmdbRepository,
    continueItems: List<ContinueWatchingItem>,
    onContinue: (ContinueWatchingItem) -> Unit,
    onMovie: (Int) -> Unit,
    onShow: (Int) -> Unit,
    onViewAll: (MediaType, CatalogSort, Int?, String) -> Unit,
    compactLayout: Boolean = false,
) {
    var heroItem by remember { mutableStateOf<CatalogItem?>(null) }
    LaunchedEffect(tmdbRepository) {
        val result = tmdbRepository.fetchBrowsePage(MediaType.MOVIE, CatalogSort.POPULAR, 1)
        if (result.items.isNotEmpty()) {
            heroItem = result.items.first()
        }
    }

    val sidePadding = if (compactLayout) 16.dp else 48.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 20.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        heroItem?.let { hero ->
            item {
                HeroSpotlightBanner(
                    item = hero,
                    tag = "PREMIERE SPOTLIGHT",
                    compactLayout = compactLayout,
                    onWatch = { onMovie(hero.tmdbId) },
                )
            }
        }
        item {
            TvContinueBrowseRow(continueItems, compactLayout, onContinue)
        }
        item {
            TvTrendingRow(
                repository = tmdbRepository,
                onItem = { item ->
                    if (item.type == MediaType.MOVIE) onMovie(item.tmdbId) else onShow(item.tmdbId)
                },
            )
        }
        item {
            TvBrowseRow(
                spec = TvFeedSpec("Latest Movies", MediaType.MOVIE, CatalogSort.LATEST),
                repository = tmdbRepository,
                onItem = { onMovie(it.tmdbId) },
                onViewAll = { onViewAll(MediaType.MOVIE, CatalogSort.LATEST, null, "") },
            )
        }
        item {
            TvBrowseRow(
                spec = TvFeedSpec("Latest TV Shows", MediaType.TV_SHOW, CatalogSort.LATEST),
                repository = tmdbRepository,
                onItem = { onShow(it.tmdbId) },
                onViewAll = { onViewAll(MediaType.TV_SHOW, CatalogSort.LATEST, null, "") },
            )
        }
    }
}

@Composable
private fun TvTrendingRow(
    repository: TmdbRepository,
    onItem: (CatalogItem) -> Unit,
) {
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository, retry) {
        loading = true
        error = null
        val result = repository.fetchTrending("day")
        if (result.error != null) {
            error = result.error
        } else {
            items = result.items.take(20)
        }
        loading = false
    }

    if (items.isNotEmpty() || loading || error != null) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Text(
                    text = "Trending Today",
                    color = TvPalette.text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x33EF4444))
                        .border(1.dp, Color(0x66EF4444), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HOT",
                        color = Color(0xFFF87171),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            val rowState = rememberLazyListState()
            LaunchedEffect(items) {
                if (items.isNotEmpty()) {
                    rowState.scrollToItem(0)
                }
            }
            LazyRow(
                state = rowState,
                reverseLayout = false,
                modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 8.dp),
            ) {
                items(items, key = { "trending-${it.type}:${it.tmdbId}" }) { item ->
                    TvMediaTile(item, onClick = { onItem(item) })
                }
            }
            if (loading) {
                Text("Loading trending titles…", modifier = Modifier.padding(top = 3.dp), color = TvPalette.muted, fontSize = 14.sp)
            }
            if (error != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(error.orEmpty(), color = TvPalette.muted, fontSize = 14.sp)
                    Surface(onClick = { retry++ }) { Text("Retry", modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun TvMediaBrowseScreen(
    type: MediaType,
    repository: TmdbRepository,
    onItem: (CatalogItem) -> Unit,
    onGenre: (CatalogGenre) -> Unit,
    onViewAll: (MediaType, CatalogSort, Int?, String) -> Unit,
    onViewAllGenres: () -> Unit,
    compactLayout: Boolean = false,
) {
    val label = if (type == MediaType.MOVIE) "Movies" else "TV Shows"
    var heroItem by remember(type) { mutableStateOf<CatalogItem?>(null) }
    LaunchedEffect(type, repository) {
        val result = repository.fetchBrowsePage(type, CatalogSort.TOP, 1)
        if (result.items.isNotEmpty()) {
            heroItem = result.items.first()
        }
    }

    val sidePadding = if (compactLayout) 16.dp else 48.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 20.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        heroItem?.let { hero ->
            item {
                HeroSpotlightBanner(
                    item = hero,
                    tag = if (type == MediaType.MOVIE) "FEATURED MOVIE" else "SPOTLIGHT SERIES",
                    compactLayout = compactLayout,
                    onWatch = { onItem(hero) },
                )
            }
        }
        item {
            TvBrowseRow(
                spec = TvFeedSpec("Latest $label", type, CatalogSort.LATEST),
                repository = repository,
                onItem = onItem,
                onViewAll = { onViewAll(type, CatalogSort.LATEST, null, "") },
            )
        }
        item {
            TvBrowseRow(
                spec = TvFeedSpec("Top $label", type, CatalogSort.TOP),
                repository = repository,
                onItem = onItem,
                onViewAll = { onViewAll(type, CatalogSort.TOP, null, "") },
            )
        }
        item {
            TvGenreRow(
                title = "Categories",
                type = type,
                repository = repository,
                onGenre = onGenre,
                onViewAll = onViewAllGenres,
                compactLayout = compactLayout,
            )
        }
    }
}

@Composable
internal fun TvGenreBrowseScreen(
    type: MediaType,
    genreId: Int,
    genreName: String,
    repository: TmdbRepository,
    onItem: (CatalogItem) -> Unit,
    onViewAll: (MediaType, CatalogSort, Int?, String) -> Unit,
    compactLayout: Boolean = false,
) {
    val label = if (type == MediaType.MOVIE) "Movies" else "TV Shows"
    val sidePadding = if (compactLayout) 16.dp else 48.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 20.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(genreName, color = TvPalette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(label, color = TvPalette.muted, fontSize = 16.sp)
            }
        }
        item {
            TvBrowseRow(
                spec = TvFeedSpec("Latest $genreName", type, CatalogSort.LATEST, genreId, genreName),
                repository = repository,
                onItem = onItem,
                onViewAll = { onViewAll(type, CatalogSort.LATEST, genreId, genreName) },
            )
        }
        item {
            TvBrowseRow(
                spec = TvFeedSpec("Popular $genreName", type, CatalogSort.POPULAR, genreId, genreName),
                repository = repository,
                onItem = onItem,
                onViewAll = { onViewAll(type, CatalogSort.POPULAR, genreId, genreName) },
            )
        }
    }
}

@Composable
internal fun TvCategoriesBrowseScreen(
    type: MediaType,
    repository: TmdbRepository,
    onGenre: (CatalogGenre) -> Unit,
    compactLayout: Boolean = false,
) {
    var genres by remember(type) { mutableStateOf<List<CatalogGenre>>(emptyList()) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    var retry by remember(type) { mutableIntStateOf(0) }
    LaunchedEffect(type, retry) {
        val result = repository.fetchGenres(type)
        genres = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
    }
    val sidePadding = if (compactLayout) 16.dp else 48.dp
    Column(Modifier.fillMaxSize().padding(horizontal = sidePadding, vertical = 22.dp)) {
        Text("Categories", color = TvPalette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(if (type == MediaType.MOVIE) "Movies" else "TV Shows", modifier = Modifier.padding(top = 4.dp, bottom = 18.dp), color = TvPalette.muted, fontSize = 16.sp)
        if (error != null) {
            Text(error.orEmpty(), color = TvPalette.muted, fontSize = 16.sp)
            Surface(onClick = { retry++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
        } else if (genres.isEmpty()) {
            Text("Loading categories…", color = TvPalette.muted, fontSize = 16.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compactLayout) 150.dp else 220.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 30.dp),
            ) {
                items(genres, key = { it.id }) { genre -> TvGenreCard(genre, compactLayout, onClick = { onGenre(genre) }) }
            }
        }
    }
}

@Composable
internal fun TvAllBrowseScreen(
    type: MediaType,
    sort: CatalogSort,
    genreId: Int?,
    heading: String,
    repository: TmdbRepository,
    onItem: (CatalogItem) -> Unit,
    compactLayout: Boolean = false,
) {
    val gridState = rememberLazyGridState()
    var items by remember(type, sort, genreId) { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var nextPage by remember(type, sort, genreId) { mutableIntStateOf(1) }
    var totalPages by remember(type, sort, genreId) { mutableIntStateOf(1) }
    var loading by remember(type, sort, genreId) { mutableStateOf(false) }
    var error by remember(type, sort, genreId) { mutableStateOf<String?>(null) }
    var retry by remember(type, sort, genreId) { mutableIntStateOf(0) }

    LaunchedEffect(type, sort, genreId, retry) {
        if (loading || nextPage > totalPages) return@LaunchedEffect
        loading = true
        var page = nextPage
        var accumulated = items
        var lastResult: CatalogPage? = null
        var attempts = 0
        do {
            lastResult = repository.fetchBrowsePage(type, sort, page, genreId)
            if (lastResult.error != null) {
                error = lastResult.error
                break
            }
            accumulated = (accumulated + lastResult.items).distinctBy { it.tmdbId }
            totalPages = lastResult.totalPages.coerceAtLeast(page)
            page++
            attempts++
        } while (accumulated.isEmpty() && page <= totalPages && attempts < 20)
        items = accumulated
        nextPage = page
        loading = false
    }

    LaunchedEffect(gridState, items.size, nextPage, totalPages, type, sort, genreId) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (items.isNotEmpty() && lastVisible >= items.size - 6 && !loading && error == null && nextPage <= totalPages) {
                    loading = true
                    var page = nextPage
                    var appended = emptyList<CatalogItem>()
                    var attempts = 0
                    while (appended.isEmpty() && page <= totalPages && attempts < 20) {
                        val result = repository.fetchBrowsePage(type, sort, page, genreId)
                        if (result.error != null) {
                            error = result.error
                            break
                        }
                        appended = result.items
                        totalPages = result.totalPages.coerceAtLeast(page)
                        page++
                        attempts++
                    }
                    items = (items + appended).distinctBy { it.tmdbId }
                    nextPage = page
                    loading = false
                }
            }
    }

    val sidePadding = if (compactLayout) 16.dp else 48.dp
    Column(Modifier.fillMaxSize().padding(horizontal = sidePadding, vertical = 18.dp)) {
        Text(heading, color = TvPalette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        if (error != null) {
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error.orEmpty(), color = TvPalette.muted, fontSize = 15.sp)
                Surface(onClick = { error = null; retry++ }) { Text("Retry", modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)) }
            }
        }
        if (items.isEmpty() && !loading && error == null) {
            Text("No titles from the current VidAPI catalog were found in this list.", modifier = Modifier.padding(top = 22.dp), color = TvPalette.muted, fontSize = 16.sp)
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(if (compactLayout) 150.dp else 176.dp),
            modifier = Modifier.fillMaxSize().padding(top = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            items(items, key = { "${it.type}:${it.tmdbId}" }) { item -> TvMediaTile(item, onClick = { onItem(item) }) }
            if (loading) item { Text("Loading more…", color = TvPalette.muted, modifier = Modifier.padding(18.dp)) }
        }
    }
}

@Composable
private fun TvBrowseRow(
    spec: TvFeedSpec,
    repository: TmdbRepository,
    onItem: (CatalogItem) -> Unit,
    onViewAll: () -> Unit,
) {
    var items by remember(spec) { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember(spec) { mutableStateOf(true) }
    var error by remember(spec) { mutableStateOf<String?>(null) }
    var retry by remember(spec) { mutableIntStateOf(0) }
    LaunchedEffect(spec, retry) {
        loading = true
        error = null
        val collected = mutableListOf<CatalogItem>()
        var page = 1
        var totalPages = 1
        var attempts = 0
        do {
            val result = repository.fetchBrowsePage(spec.type, spec.sort, page, spec.genreId)
            if (result.error != null) {
                error = result.error
                break
            }
            collected += result.items
            totalPages = result.totalPages
            page++
            attempts++
        } while (collected.size < 20 && page <= totalPages && attempts < 10)
        items = collected.distinctBy { it.tmdbId }.take(20)
        loading = false
    }
    val rowState = rememberLazyListState()
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            rowState.scrollToItem(0)
        }
    }
    Column {
        Text(spec.title, modifier = Modifier.padding(bottom = 9.dp), color = TvPalette.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            state = rowState,
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 8.dp),
        ) {
            items(items, key = { "${it.type}:${it.tmdbId}:${spec.title}" }) { item -> TvMediaTile(item, onClick = { onItem(item) }) }
            if (items.isNotEmpty()) {
                item(key = "view-all-${spec.title}") { TvViewAllCard(onClick = onViewAll) }
            }
        }
        if (loading) Text("Loading titles…", modifier = Modifier.padding(top = 3.dp), color = TvPalette.muted, fontSize = 14.sp)
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error.orEmpty(), color = TvPalette.muted, fontSize = 14.sp)
                Surface(onClick = { retry++ }) { Text("Retry", modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)) }
            }
        } else if (!loading && items.isEmpty()) {
            Text("No matching titles in the synced VidAPI catalog.", color = TvPalette.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TvGenreRow(
    title: String,
    type: MediaType,
    repository: TmdbRepository,
    onGenre: (CatalogGenre) -> Unit,
    onViewAll: () -> Unit,
    compactLayout: Boolean,
) {
    var genres by remember(type) { mutableStateOf<List<CatalogGenre>>(emptyList()) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    LaunchedEffect(type) {
        val result = repository.fetchGenres(type)
        genres = result.getOrDefault(emptyList()).take(20)
        error = result.exceptionOrNull()?.message
    }
    val genreRowState = rememberLazyListState()
    LaunchedEffect(genres) {
        if (genres.isNotEmpty()) {
            genreRowState.scrollToItem(0)
        }
    }
    Column {
        Text(title, modifier = Modifier.padding(bottom = 9.dp), color = TvPalette.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            state = genreRowState,
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 8.dp),
        ) {
            items(genres, key = { it.id }) { genre -> TvGenreCard(genre, compactLayout, onClick = { onGenre(genre) }) }
            if (genres.isNotEmpty()) {
                item(key = "view-all-genres-$type") { TvViewAllCard(label = "View all categories", categoryCard = true, compactLayout = compactLayout, onClick = onViewAll) }
            }
        }
        if (error != null) Text(error.orEmpty(), color = TvPalette.muted, fontSize = 14.sp)
        else if (genres.isEmpty()) Text("Loading categories…", color = TvPalette.muted, fontSize = 14.sp)
    }
}

@Composable
private fun TvGenreCard(genre: CatalogGenre, compactLayout: Boolean = false, onClick: () -> Unit) {
    AppClickableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(if (compactLayout) 150.dp else 210.dp)
            .height(if (compactLayout) 85.dp else 110.dp),
        shape = RoundedCornerShape(10.dp),
        containerColor = TvPalette.surface,
        borderStroke = BorderStroke(1.dp, TvPalette.cardBorder),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(TvPalette.surfaceLight, TvPalette.surface)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "▦",
                    color = TvPalette.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = genre.name,
                    color = TvPalette.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TvMediaTile(item: CatalogItem, onClick: () -> Unit) {
    AppClickableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(190.dp)
            .height(340.dp),
        shape = RoundedCornerShape(10.dp),
        containerColor = TvPalette.surface,
        borderStroke = BorderStroke(1.dp, TvPalette.cardBorder),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(TvPalette.surfaceLight),
                contentAlignment = Alignment.Center,
            ) {
                val imageUrl = tmdbImageUrl(item.posterPath, "w342")
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "${item.title} poster",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("No poster", color = TvPalette.muted, fontSize = 13.sp)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvPalette.badgeBg)
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (item.type == MediaType.MOVIE) "MOVIE" else "SERIES",
                        color = Color(0xFFE5E7EB),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.title,
                    color = TvPalette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.year ?: "—",
                        color = TvPalette.muted,
                        fontSize = 12.sp,
                    )
                    if (item.rating != null && item.rating > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "★",
                                color = TvPalette.ratingGold,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = "%.1f".format(item.rating),
                                color = TvPalette.ratingGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvViewAllCard(
    label: String = "View all",
    categoryCard: Boolean = false,
    compactLayout: Boolean = false,
    onClick: () -> Unit,
) {
    AppClickableSurface(
        onClick = onClick,
        modifier = if (categoryCard) {
            Modifier
                .width(if (compactLayout) 150.dp else 210.dp)
                .height(if (compactLayout) 85.dp else 110.dp)
        } else {
            Modifier
                .width(190.dp)
                .height(340.dp)
        },
        shape = RoundedCornerShape(10.dp),
        containerColor = TvPalette.surfaceLight,
        borderStroke = BorderStroke(1.dp, TvPalette.cardBorder),
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x263B82F6))
                        .padding(8.dp)
                ) {
                    Text("›", color = TvPalette.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = label,
                    color = TvPalette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TvContinueBrowseRow(
    items: List<ContinueWatchingItem>,
    compactLayout: Boolean,
    onContinue: (ContinueWatchingItem) -> Unit,
) {
    Column {
        Text(
            text = "Continue Watching",
            modifier = Modifier.padding(bottom = 12.dp),
            color = TvPalette.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        val continueRowState = rememberLazyListState()
        LaunchedEffect(items) {
            if (items.isNotEmpty()) {
                continueRowState.scrollToItem(0)
            }
        }
        LazyRow(
            state = continueRowState,
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 6.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .width(if (compactLayout) 300.dp else 420.dp)
                            .height(86.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TvPalette.surface)
                            .border(1.dp, TvPalette.cardBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "Continue Watching will appear here after playback starts.",
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = TvPalette.muted,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                items(items, key = { "continue-${it.item.type}:${it.item.tmdbId}" }) { continueItem ->
                    AppClickableSurface(
                        onClick = { onContinue(continueItem) },
                        modifier = Modifier
                            .width(if (compactLayout) 210.dp else 260.dp)
                            .height(if (compactLayout) 130.dp else 150.dp),
                        shape = RoundedCornerShape(10.dp),
                        containerColor = TvPalette.surface,
                        borderStroke = BorderStroke(1.dp, TvPalette.cardBorder),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            val posterUrl = tmdbImageUrl(continueItem.item.posterPath, "w342")
                            if (posterUrl != null) {
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = "${continueItem.item.title} backdrop",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0x550A0C10),
                                                    Color(0xF00A0C10),
                                                )
                                            )
                                        )
                                )
                            }
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TvPalette.badgeBg)
                                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "RESUME",
                                        color = TvPalette.accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = continueItem.item.title,
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = TvPalette.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = continueItem.progressLabel,
                                    modifier = Modifier.padding(top = 2.dp),
                                    color = TvPalette.muted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                            val progressFraction = (continueItem.positionMs.toFloat() / continueItem.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .align(Alignment.BottomStart)
                                    .background(Color(0x26FFFFFF))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(progressFraction)
                                        .height(4.dp)
                                        .background(TvPalette.accent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    borderStroke: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) }
    if (isTv) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            scale = ClickableSurfaceDefaults.scale(1f, 1f, 1f, 1f, 1f),
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                focusedContainerColor = TvPalette.surfaceFocused,
            ),
            border = ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(borderStroke ?: BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, TvPalette.focus)),
            ),
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(containerColor)
                .then(if (borderStroke == null) Modifier else Modifier.border(borderStroke, shape))
                .clickable(onClick = onClick),
            content = content,
        )
    }
}
