package com.vivicast.tv.feature.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.HeroPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.PosterCard
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.AssetState
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoSeriesItem
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SeriesRoute(
    providerRepository: ProviderRepository? = null,
    mediaRepository: MediaRepository? = null,
    favoritesRepository: FavoritesRepository? = null,
    resolveSeriesPosterModel: suspend (Series) -> Any? = { null },
    resolveSeriesBackdropModel: suspend (Series) -> Any? = { null },
    onOpenPlayer: () -> Unit = {},
) {
    if (providerRepository == null || mediaRepository == null || favoritesRepository == null) {
        DemoSeriesRoute(onOpenPlayer = onOpenPlayer)
    } else {
        RoomSeriesRoute(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            resolveSeriesPosterModel = resolveSeriesPosterModel,
            resolveSeriesBackdropModel = resolveSeriesBackdropModel,
            onOpenPlayer = onOpenPlayer,
        )
    }
}

@Composable
private fun RoomSeriesRoute(
    providerRepository: ProviderRepository,
    mediaRepository: MediaRepository,
    favoritesRepository: FavoritesRepository,
    resolveSeriesPosterModel: suspend (Series) -> Any?,
    resolveSeriesBackdropModel: suspend (Series) -> Any?,
    onOpenPlayer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(providers) {
        val seriesProviders = providers.filter { it.includeSeries }
        if (selectedProviderId == null || providers.none { it.id == selectedProviderId }) {
            selectedProviderId = seriesProviders.firstOrNull { it.isActive }?.id
                ?: seriesProviders.firstOrNull()?.id
                ?: providers.firstOrNull { it.isActive }?.id
                ?: providers.firstOrNull()?.id
        }
    }

    val categoriesFlow = remember(selectedProviderId) {
        selectedProviderId?.let { mediaRepository.observeCategories(it, CategoryType.Series) } ?: flowOf(emptyList())
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val favoritesFlow = remember(selectedProviderId) {
        selectedProviderId?.let { favoritesRepository.observeFavorites(it, MediaType.Series) } ?: flowOf(emptyList())
    }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val favoriteSeriesIds = remember(favorites) { favorites.mapTo(mutableSetOf()) { it.mediaId } }
    val favoriteOrder = remember(favorites) { favorites.mapIndexed { index, favorite -> favorite.mediaId to index }.toMap() }
    val categoriesWithFavorites = remember(selectedProviderId, categories) {
        selectedProviderId?.let { listOf(favoriteCategory(it, CategoryType.Series)) + categories } ?: categories
    }

    LaunchedEffect(selectedProviderId, categoriesWithFavorites, favoriteSeriesIds) {
        if (selectedCategoryId == null || categoriesWithFavorites.none { it.id == selectedCategoryId }) {
            selectedCategoryId = when {
                favoriteSeriesIds.isNotEmpty() -> FAVORITES_CATEGORY_ID
                else -> categories.firstOrNull()?.id ?: categoriesWithFavorites.firstOrNull()?.id
            }
        }
    }

    val seriesFlow = remember(selectedProviderId, selectedCategoryId) {
        selectedProviderId?.let { providerId ->
            mediaRepository.observeSeries(providerId, selectedCategoryId?.takeUnless { it == FAVORITES_CATEGORY_ID })
        } ?: flowOf(emptyList())
    }
    val observedSeries by seriesFlow.collectAsState(initial = emptyList())
    val seriesItems = remember(observedSeries, selectedCategoryId, favoriteSeriesIds, favoriteOrder) {
        if (selectedCategoryId == FAVORITES_CATEGORY_ID) {
            observedSeries
                .filter { it.id in favoriteSeriesIds }
                .sortedWith(compareBy<Series> { favoriteOrder[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.getDefault()) })
        } else {
            observedSeries
        }
    }

    LaunchedEffect(seriesItems) {
        if (selectedSeriesId == null || seriesItems.none { it.id == selectedSeriesId }) {
            selectedSeriesId = seriesItems.firstOrNull()?.id
        }
    }

    val selectedSeries = seriesItems.firstOrNull { it.id == selectedSeriesId }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedCategory = categoriesWithFavorites.firstOrNull { it.id == selectedCategoryId }
    val backdropModel by produceState<Any?>(initialValue = null, selectedSeries?.id, selectedSeries?.backdropUrl) {
        value = selectedSeries?.let { resolveSeriesBackdropModel(it) }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            SeriesHero(
                series = selectedSeries,
                provider = selectedProvider,
                backdropModel = backdropModel,
                isFavorite = selectedSeries?.id in favoriteSeriesIds,
                onOpenPlayer = onOpenPlayer,
                onToggleFavorite = {
                    val providerId = selectedProviderId
                    val seriesId = selectedSeries?.id
                    if (providerId != null && seriesId != null) {
                        scope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Series, seriesId) }
                    }
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                items(categoriesWithFavorites, key = { it.id }) { category ->
                    ActionPill(
                        label = category.displayName,
                        selected = selectedCategoryId == category.id,
                        onClick = {
                            selectedCategoryId = category.id
                            selectedSeriesId = null
                        },
                    )
                }
            }
            SectionTitle("Serien")
            if (seriesItems.isEmpty()) {
                InfoPanel(
                    title = emptyTitle(selectedProvider, selectedCategory),
                    body = emptyBody(selectedProvider, selectedCategory),
                    badge = "Leer",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
                    verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(seriesItems, key = { it.id }) { series ->
                        val posterModel by produceState<Any?>(initialValue = null, series.id, series.posterUrl) {
                            value = resolveSeriesPosterModel(series)
                        }
                        PosterCard(
                            title = series.name,
                            rating = series.rating?.takeIf { it.isNotBlank() } ?: "-",
                            meta = series.cardMeta,
                            hasPoster = !series.posterUrl.isNullOrBlank() || posterModel != null,
                            progressPercent = 0,
                            favorite = series.id in favoriteSeriesIds,
                            seen = false,
                            imageModel = posterModel,
                            onFocused = { selectedSeriesId = series.id },
                            onClick = { selectedSeriesId = series.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHero(
    series: Series?,
    provider: Provider?,
    backdropModel: Any?,
    isFavorite: Boolean,
    onOpenPlayer: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    HeroPanel(
        title = series?.name ?: "Keine Serien",
        body = series?.plot?.takeIf { it.isNotBlank() } ?: "Waehle einen Provider und eine Serienkategorie aus.",
        meta = series?.heroMeta ?: provider?.name,
        modifier = Modifier.fillMaxWidth(),
        backdropModel = backdropModel,
        action = {
            if (series != null) {
                ActionPill("Abspielen", onClick = onOpenPlayer)
                ActionPill(if (isFavorite) "Favorit" else "Merken", selected = isFavorite, onClick = onToggleFavorite)
            }
        },
    )
}

@Composable
private fun DemoSeriesRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf("Alle Serien") }
    var selectedSeries by remember { mutableStateOf(DemoCatalog.series.first()) }
    val categories = listOf("Favoriten", "Fortsetzen", "Alle Serien", "Sci-Fi", "Ohne Poster")

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            DemoSeriesHero(selectedSeries, onOpenPlayer)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                items(categories) { category ->
                    ActionPill(
                        label = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }
            SectionTitle("Serien")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(DemoCatalog.series) { series ->
                    PosterCard(
                        title = series.title,
                        rating = series.rating,
                        meta = "${series.seasons} Staffeln | ${series.episodes} Episoden",
                        hasPoster = series.posterState == AssetState.Available,
                        progressPercent = if (series.progressLabel.contains("fortsetzen", ignoreCase = true)) 36 else 0,
                        favorite = false,
                        seen = series.progressLabel.contains("gesehen", ignoreCase = true),
                        imageResId = series.posterResId,
                        onFocused = { selectedSeries = series },
                        onClick = { selectedSeries = series },
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoSeriesHero(series: DemoSeriesItem, onOpenPlayer: () -> Unit) {
    HeroPanel(
        title = series.title,
        body = series.description,
        meta = "Rating ${series.rating} | ${series.seasons} Staffeln | ${series.episodes} Episoden",
        modifier = Modifier.fillMaxWidth(),
        backdropResId = series.backdropResId,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                ActionPill("Abspielen", onClick = onOpenPlayer)
                StatusBadge(series.progressLabel)
            }
        },
    )
}

private fun favoriteCategory(providerId: String, type: CategoryType): Category =
    Category(
        id = FAVORITES_CATEGORY_ID,
        providerId = providerId,
        type = type,
        remoteId = FAVORITES_CATEGORY_ID,
        name = "Favoriten",
        sortOrder = Int.MIN_VALUE,
        isHidden = false,
    )

private fun emptyTitle(provider: Provider?, category: Category?): String =
    when {
        provider == null -> "Keine Wiedergabelisten"
        category?.id == FAVORITES_CATEGORY_ID -> "Keine Serien-Favoriten"
        else -> "Keine Serien"
    }

private fun emptyBody(provider: Provider?, category: Category?): String =
    when {
        provider == null -> "Lege in den Einstellungen zuerst einen Provider an."
        category?.id == FAVORITES_CATEGORY_ID -> "Fuege Serien ueber die Merken-Aktion zu deinen Favoriten hinzu."
        category == null -> "Dieser Provider enthaelt keine importierten Serienkategorien."
        else -> "Diese Kategorie enthaelt keine importierten Serien."
    }

private val Category.displayName: String
    get() = if (remoteId == "__UNCATEGORIZED__") "Nicht kategorisiert" else name

private val Series.cardMeta: String
    get() = listOfNotNull(year, rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" }).joinToString(" | ").ifBlank { "Serie" }

private val Series.heroMeta: String?
    get() = listOfNotNull(
        rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" },
        genre?.takeIf { it.isNotBlank() },
        year?.takeIf { it.isNotBlank() },
    ).joinToString(" | ").ifBlank { null }

private const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
