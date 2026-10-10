package com.vistaro.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
    val background = Color(0xFF191A1B)
    val surface = Color(0xFF222425)
    val surfaceFocused = Color(0xFF303234)
    val text = Color(0xFFE5E5E8)
    val muted = Color(0xFFA8A9AD)
    val focus = Color(0xFFF1EFF4)
    val accent = Color(0xFFC8C1D8)
}

private data class TvFeedSpec(
    val title: String,
    val type: MediaType,
    val sort: CatalogSort,
    val genreId: Int? = null,
    val genreName: String = "",
)

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
    val sidePadding = if (compactLayout) 16.dp else 48.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 20.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        item {
            TvContinueBrowseRow(continueItems, compactLayout, onContinue)
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
    val sidePadding = if (compactLayout) 16.dp else 48.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 20.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
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
    Column {
        Text(spec.title, modifier = Modifier.padding(bottom = 9.dp), color = TvPalette.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = 0),
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 8.dp),
        ) {
            items(items, key = { "${it.type}:${it.tmdbId}:${spec.title}" }) { item -> TvMediaTile(item, onClick = { onItem(item) }) }
            item(key = "view-all-${spec.title}") { TvViewAllCard(onClick = onViewAll) }
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
    Column {
        Text(title, modifier = Modifier.padding(bottom = 9.dp), color = TvPalette.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = 0),
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 8.dp),
        ) {
            items(genres, key = { it.id }) { genre -> TvGenreCard(genre, compactLayout, onClick = { onGenre(genre) }) }
            item(key = "view-all-genres-$type") { TvViewAllCard(label = "View all categories", categoryCard = true, compactLayout = compactLayout, onClick = onViewAll) }
        }
        if (error != null) Text(error.orEmpty(), color = TvPalette.muted, fontSize = 14.sp)
        else if (genres.isEmpty()) Text("Loading categories…", color = TvPalette.muted, fontSize = 14.sp)
    }
}

@Composable
private fun TvGenreCard(genre: CatalogGenre, compactLayout: Boolean = false, onClick: () -> Unit) {
    AppClickableSurface(
        onClick = onClick,
        modifier = Modifier.width(if (compactLayout) 150.dp else 205.dp).height(112.dp),
        shape = RoundedCornerShape(8.dp),
        containerColor = TvPalette.surface,
        borderStroke = BorderStroke(1.dp, Color(0xFF343638)),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            Text(genre.name, color = TvPalette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun TvMediaTile(item: CatalogItem, onClick: () -> Unit) {
    AppClickableSurface(
        onClick = onClick,
        modifier = Modifier.width(174.dp).height(326.dp),
        shape = RoundedCornerShape(8.dp),
        containerColor = TvPalette.surface,
        borderStroke = BorderStroke(1.dp, Color(0xFF343638)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)).background(Color(0xFF2A2C2E)),
                contentAlignment = Alignment.Center,
            ) {
                val imageUrl = tmdbImageUrl(item.posterPath, "w342")
                if (imageUrl != null) {
                    AsyncImage(imageUrl, contentDescription = "${item.title} poster", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("No poster", color = TvPalette.muted, fontSize = 14.sp)
                }
            }
            Column(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 11.dp, vertical = 8.dp)) {
                Text(item.title, color = TvPalette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(item.year ?: "—", color = TvPalette.muted, fontSize = 13.sp)
                    if (item.rating != null && item.rating > 0.0) {
                        Text("★ ${"%.1f".format(item.rating)} · TMDB", color = TvPalette.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
        modifier = if (categoryCard) Modifier.width(if (compactLayout) 150.dp else 205.dp).height(112.dp) else Modifier.width(174.dp).height(326.dp),
        shape = RoundedCornerShape(8.dp),
        containerColor = Color(0xFF202223),
        borderStroke = BorderStroke(1.dp, Color(0xFF343638)),
    ) {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("›", color = TvPalette.text, fontSize = 36.sp, fontWeight = FontWeight.Light)
                Text(label, color = TvPalette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TvContinueBrowseRow(items: List<ContinueWatchingItem>, compactLayout: Boolean, onContinue: (ContinueWatchingItem) -> Unit) {
    Column {
        Text("Continue Watching", modifier = Modifier.padding(bottom = 9.dp), color = TvPalette.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = 0),
            reverseLayout = false,
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 6.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Box(Modifier.width(if (compactLayout) 320.dp else 420.dp).height(82.dp).clip(RoundedCornerShape(8.dp)).background(TvPalette.surface), contentAlignment = Alignment.CenterStart) {
                        Text("Continue Watching will appear here after you start playback.", modifier = Modifier.padding(horizontal = 18.dp), color = TvPalette.muted, fontSize = 15.sp)
                    }
                }
            } else {
                items(items, key = { "continue-${it.item.type}:${it.item.tmdbId}" }) { continueItem ->
                    AppClickableSurface(
                        onClick = { onContinue(continueItem) },
                        modifier = Modifier.width(if (compactLayout) 235.dp else 270.dp).height(132.dp),
                        shape = RoundedCornerShape(10.dp),
                        containerColor = TvPalette.surface,
                        borderStroke = BorderStroke(1.dp, Color(0xFF343638)),
                    ) {
                        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Bottom) {
                            Text("RESUME", color = TvPalette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(continueItem.item.title, modifier = Modifier.padding(top = 6.dp), color = TvPalette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(continueItem.progressLabel, modifier = Modifier.padding(top = 3.dp), color = TvPalette.muted, fontSize = 13.sp, maxLines = 1)
                            Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF48494C))) {
                                Box(Modifier.fillMaxWidth((continueItem.positionMs.toFloat() / continueItem.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)).height(3.dp).background(TvPalette.accent))
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
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = containerColor, focusedContainerColor = TvPalette.surfaceFocused),
            border = ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(borderStroke ?: BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, TvPalette.focus)),
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
