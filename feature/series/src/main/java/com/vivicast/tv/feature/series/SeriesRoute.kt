package com.vivicast.tv.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.HeroPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.PosterCard
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SeriesRoute(
    providerRepository: ProviderRepository? = null,
    mediaRepository: MediaRepository? = null,
    favoritesRepository: FavoritesRepository? = null,
    playbackRepository: PlaybackRepository? = null,
    resolveSeriesPosterModel: suspend (Series) -> Any? = { null },
    resolveSeriesBackdropModel: suspend (Series) -> Any? = { null },
    onOpenPlayer: (Episode) -> Unit = {},
    targetProviderId: String? = null,
    targetCategoryId: String? = null,
    targetSeriesId: String? = null,
    targetSeasonId: String? = null,
    targetEpisodeId: String? = null,
    onTargetConsumed: () -> Unit = {},
) {
    if (providerRepository == null || mediaRepository == null || favoritesRepository == null || playbackRepository == null) {
        SeriesUnavailableState()
    } else {
        RoomSeriesRoute(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
            resolveSeriesPosterModel = resolveSeriesPosterModel,
            resolveSeriesBackdropModel = resolveSeriesBackdropModel,
            onOpenPlayer = onOpenPlayer,
            targetProviderId = targetProviderId,
            targetCategoryId = targetCategoryId,
            targetSeriesId = targetSeriesId,
            targetSeasonId = targetSeasonId,
            targetEpisodeId = targetEpisodeId,
            onTargetConsumed = onTargetConsumed,
        )
    }
}

@Composable
private fun SeriesUnavailableState() {
    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        InfoPanel(
            title = stringResource(R.string.series_unavailable),
            body = stringResource(R.string.series_select_provider),
            badge = stringResource(R.string.common_empty_badge),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoomSeriesRoute(
    providerRepository: ProviderRepository,
    mediaRepository: MediaRepository,
    favoritesRepository: FavoritesRepository,
    playbackRepository: PlaybackRepository,
    resolveSeriesPosterModel: suspend (Series) -> Any?,
    resolveSeriesBackdropModel: suspend (Series) -> Any?,
    onOpenPlayer: (Episode) -> Unit,
    targetProviderId: String?,
    targetCategoryId: String?,
    targetSeriesId: String?,
    targetSeasonId: String?,
    targetEpisodeId: String?,
    onTargetConsumed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val strFavorites = stringResource(R.string.common_favorites)
    val strContinue = stringResource(R.string.series_continue)
    val strSeriesTypeBadge = stringResource(R.string.series_type_badge)
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var selectedSeasonId by remember { mutableStateOf<String?>(null) }
    var selectedEpisodeId by remember { mutableStateOf<String?>(null) }
    var detailSeriesId by remember { mutableStateOf<String?>(null) }
    var selectedEpisodeProgress by remember { mutableStateOf<PlaybackProgress?>(null) }
    var seriesPageCount by remember { mutableStateOf(1) }

    BackHandler(enabled = detailSeriesId != null) {
        detailSeriesId = null
    }

    val seriesProviders = remember(providers) { providers.filter { it.includeSeries } }

    LaunchedEffect(providers, seriesProviders) {
        if (selectedProviderId == null || seriesProviders.none { it.id == selectedProviderId }) {
            selectedProviderId = seriesProviders.firstOrNull { it.isActive }?.id
                ?: seriesProviders.firstOrNull()?.id
        }
    }
    LaunchedEffect(targetProviderId, targetCategoryId, targetSeriesId) {
        if (targetSeriesId == null) return@LaunchedEffect
        selectedProviderId = targetProviderId
        selectedCategoryId = targetCategoryId
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
    val continueFlow = remember(selectedProviderId) {
        selectedProviderId?.let { playbackRepository.observeContinueWatching(it) } ?: flowOf(emptyList())
    }
    val continueProgress by continueFlow.collectAsState(initial = emptyList())
    val continueSeriesTargets by produceState<Map<String, SeriesContinueTarget>>(
        initialValue = emptyMap(),
        selectedProviderId,
        continueProgress,
    ) {
        value = continueProgress
            .filter { it.mediaType == MediaType.Episode }
            .sortedByDescending { it.lastWatchedAt }
            .mapNotNull { progress ->
                mediaRepository.getEpisode(progress.providerId, progress.mediaId)
                    ?.takeIf { it.providerId == selectedProviderId }
                    ?.let { episode -> episode.seriesId to SeriesContinueTarget(progress, episode) }
            }
            .distinctBy { it.first }
            .toMap()
    }
    val continueOrder = remember(continueSeriesTargets) {
        continueSeriesTargets.values
            .sortedByDescending { it.progress.lastWatchedAt }
            .mapIndexed { index, target -> target.episode.seriesId to index }
            .toMap()
    }
    val categoriesWithSpecials = remember(selectedProviderId, categories, continueSeriesTargets) {
        selectedProviderId?.let { providerId ->
            buildList {
                add(specialCategory(providerId, FAVORITES_CATEGORY_ID, strFavorites))
                if (continueSeriesTargets.isNotEmpty()) {
                    add(specialCategory(providerId, CONTINUE_CATEGORY_ID, strContinue))
                }
                addAll(categories)
            }
        } ?: categories
    }

    LaunchedEffect(selectedProviderId, categoriesWithSpecials, favoriteSeriesIds, continueSeriesTargets) {
        if (selectedCategoryId == null || categoriesWithSpecials.none { it.id == selectedCategoryId }) {
            selectedCategoryId = categories.firstOrNull()?.id ?: categoriesWithSpecials.firstOrNull()?.id
        }
    }
    LaunchedEffect(selectedProviderId, selectedCategoryId) {
        seriesPageCount = 1
    }

    val seriesFlow = remember(selectedProviderId, selectedCategoryId, seriesPageCount) {
        selectedProviderId?.takeIf { selectedCategoryId !in SPECIAL_CATEGORY_IDS }?.let { providerId ->
            mediaRepository.observeSeriesPage(
                providerId = providerId,
                categoryId = selectedCategoryId,
                limit = seriesPageCount * VOD_PAGE_SIZE,
            )
        } ?: flowOf(emptyList())
    }
    val observedSeries by seriesFlow.collectAsState(initial = emptyList())
    val favoriteSeries by produceState<List<Series>>(initialValue = emptyList(), mediaRepository, favorites) {
        value = favorites.mapNotNull { mediaRepository.getSeries(it.providerId, it.mediaId) }
    }
    val continueSeries by produceState<List<Series>>(initialValue = emptyList(), mediaRepository, continueSeriesTargets) {
        value = continueSeriesTargets.values
            .sortedByDescending { it.progress.lastWatchedAt }
            .mapNotNull { mediaRepository.getSeries(it.episode.providerId, it.episode.seriesId) }
    }
    val seriesItems = remember(observedSeries, selectedCategoryId, favoriteSeries, favoriteOrder, continueSeries, continueOrder) {
        when (selectedCategoryId) {
            CONTINUE_CATEGORY_ID -> continueSeries
                .sortedWith(compareBy<Series> { continueOrder[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.getDefault()) })
            FAVORITES_CATEGORY_ID -> favoriteSeries
                .sortedWith(compareBy<Series> { favoriteOrder[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.getDefault()) })
            else -> observedSeries
        }
    }

    LaunchedEffect(seriesItems) {
        if (selectedSeriesId == null || seriesItems.none { it.id == selectedSeriesId }) {
            selectedSeriesId = seriesItems.firstOrNull()?.id
        }
        if (detailSeriesId != null && seriesItems.isNotEmpty() && seriesItems.none { it.id == detailSeriesId }) {
            detailSeriesId = null
        }
    }
    LaunchedEffect(targetSeriesId, seriesItems) {
        val seriesId = targetSeriesId ?: return@LaunchedEffect
        if (seriesItems.any { it.id == seriesId } || targetProviderId?.let { mediaRepository.getSeries(it, seriesId) } != null) {
            selectedSeriesId = seriesId
            detailSeriesId = seriesId
            if (targetSeasonId == null && targetEpisodeId == null) {
                onTargetConsumed()
            }
        }
    }

    val loadedSelectedSeries by produceState<Series?>(initialValue = null, mediaRepository, selectedProviderId, selectedSeriesId) {
        val providerId = selectedProviderId
        val seriesId = selectedSeriesId
        value = if (providerId != null && seriesId != null) {
            mediaRepository.getSeries(providerId, seriesId)
        } else {
            null
        }
    }
    val loadedDetailSeries by produceState<Series?>(initialValue = null, mediaRepository, selectedProviderId, detailSeriesId) {
        val providerId = selectedProviderId
        val seriesId = detailSeriesId
        value = if (providerId != null && seriesId != null) {
            mediaRepository.getSeries(providerId, seriesId)
        } else {
            null
        }
    }
    val selectedSeries = seriesItems.firstOrNull { it.id == selectedSeriesId } ?: loadedSelectedSeries
    val detailSeries = seriesItems.firstOrNull { it.id == detailSeriesId } ?: loadedDetailSeries
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedCategory = categoriesWithSpecials.firstOrNull { it.id == selectedCategoryId }
    val detailContinueTarget = detailSeries?.let { continueSeriesTargets[it.id] }
    val seasonsFlow = remember(selectedProviderId, selectedSeries?.id) {
        val providerId = selectedProviderId
        val seriesId = selectedSeries?.id
        if (providerId == null || seriesId == null) flowOf(emptyList()) else mediaRepository.observeSeasons(providerId, seriesId)
    }
    val seasons by seasonsFlow.collectAsState(initial = emptyList())
    LaunchedEffect(selectedSeries?.id, seasons) {
        if (selectedSeasonId == null || seasons.none { it.id == selectedSeasonId }) {
            selectedSeasonId = seasons.firstOrNull()?.id
        }
    }
    LaunchedEffect(targetSeasonId, seasons) {
        val seasonId = targetSeasonId ?: return@LaunchedEffect
        if (seasons.any { it.id == seasonId }) {
            selectedSeasonId = seasonId
            if (targetEpisodeId == null) {
                onTargetConsumed()
            }
        }
    }
    val episodesFlow = remember(selectedProviderId, selectedSeasonId) {
        val providerId = selectedProviderId
        val seasonId = selectedSeasonId
        if (providerId == null || seasonId == null) flowOf(emptyList()) else mediaRepository.observeEpisodes(providerId, seasonId)
    }
    val episodes by episodesFlow.collectAsState(initial = emptyList())
    LaunchedEffect(selectedSeasonId, episodes) {
        if (selectedEpisodeId == null || episodes.none { it.id == selectedEpisodeId }) {
            selectedEpisodeId = episodes.firstOrNull()?.id
        }
    }
    LaunchedEffect(targetEpisodeId, episodes) {
        val episodeId = targetEpisodeId ?: return@LaunchedEffect
        if (episodes.any { it.id == episodeId }) {
            selectedEpisodeId = episodeId
            onTargetConsumed()
        }
    }
    val selectedEpisode = episodes.firstOrNull { it.id == selectedEpisodeId } ?: episodes.firstOrNull()
    val backdropModel by produceState<Any?>(initialValue = null, selectedSeries?.id, selectedSeries?.backdropUrl) {
        value = selectedSeries?.let { resolveSeriesBackdropModel(it) }
    }
    val canLoadMoreSeries = selectedCategoryId !in SPECIAL_CATEGORY_IDS &&
        observedSeries.size >= seriesPageCount * VOD_PAGE_SIZE

    LaunchedEffect(selectedProviderId, selectedEpisode?.id) {
        val providerId = selectedProviderId
        selectedEpisodeProgress = if (providerId != null && selectedEpisode != null) {
            playbackRepository.getProgress(providerId, MediaType.Episode, selectedEpisode.id)
        } else {
            null
        }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            if (detailSeries != null) {
                SeriesDetailPage(
                    series = detailSeries,
                    provider = selectedProvider,
                    backdropModel = backdropModel,
                    isFavorite = detailSeries.id in favoriteSeriesIds,
                    seasons = seasons,
                    selectedSeasonId = selectedSeasonId,
                    episodes = episodes,
                    selectedEpisodeId = selectedEpisodeId,
                    selectedEpisodeProgress = selectedEpisodeProgress,
                    continueTarget = detailContinueTarget,
                    onSeasonSelected = {
                        selectedSeasonId = it.id
                        selectedEpisodeId = null
                    },
                    onEpisodeSelected = { selectedEpisodeId = it.id },
                    onEpisodePlay = onOpenPlayer,
                    onContinueEpisode = { target ->
                        selectedSeasonId = target.episode.seasonId
                        selectedEpisodeId = target.episode.id
                        onOpenPlayer(target.episode)
                    },
                    onMarkEpisodeSeen = {
                        val episode = selectedEpisode ?: return@SeriesDetailPage
                        scope.launch {
                            val now = System.currentTimeMillis()
                            val completedProgress = episode.completedProgress(selectedEpisodeProgress, now)
                            playbackRepository.saveProgress(completedProgress)
                            selectedEpisodeProgress = completedProgress
                        }
                    },
                    onMarkEpisodeUnseen = {
                        val providerId = selectedProviderId ?: return@SeriesDetailPage
                        val episode = selectedEpisode ?: return@SeriesDetailPage
                        scope.launch {
                            playbackRepository.deleteProgress(providerId, MediaType.Episode, episode.id)
                            selectedEpisodeProgress = null
                        }
                    },
                    onToggleFavorite = {
                        val providerId = selectedProviderId
                        if (providerId != null) {
                            scope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Series, detailSeries.id) }
                        }
                    },
                    onClose = { detailSeriesId = null },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
                    SeriesCategoryColumn(
                        providers = seriesProviders,
                        selectedProviderId = selectedProviderId,
                        categories = categoriesWithSpecials,
                        selectedCategoryId = selectedCategoryId,
                        onProviderSelected = {
                            selectedProviderId = it.id
                            selectedCategoryId = null
                            selectedSeriesId = null
                        },
                        onCategorySelected = {
                            selectedCategoryId = it.id
                            selectedSeriesId = null
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.weight(1f)) {
                        SeriesHero(
                            series = selectedSeries,
                            provider = selectedProvider,
                            backdropModel = backdropModel,
                            isFavorite = selectedSeries?.id in favoriteSeriesIds,
                            episode = selectedEpisode,
                            showActions = false,
                            onOpenPlayer = { selectedEpisode?.let(onOpenPlayer) },
                            onToggleFavorite = {
                                val providerId = selectedProviderId
                                val seriesId = selectedSeries?.id
                                if (providerId != null && seriesId != null) {
                                    scope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Series, seriesId) }
                                }
                            },
                        )
                        SectionTitle(stringResource(R.string.nav_series))
                        if (seriesItems.isEmpty()) {
                            InfoPanel(
                                title = emptyTitle(selectedProvider, selectedCategory),
                                body = emptyBody(selectedProvider, selectedCategory),
                                badge = stringResource(R.string.common_empty_badge),
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
                                        meta = continueSeriesTargets[series.id]?.cardMeta ?: series.cardMeta(strSeriesTypeBadge),
                                        hasPoster = !series.posterUrl.isNullOrBlank() || posterModel != null,
                                        progressPercent = continueSeriesTargets[series.id]?.progress?.progressPercent ?: 0,
                                        favorite = series.id in favoriteSeriesIds,
                                        seen = false,
                                        imageModel = posterModel,
                                        surfaceModifier = Modifier
                                            .testTag(seriesPosterTag(series.id))
                                            .semantics {
                                                onClick {
                                                    selectedSeriesId = series.id
                                                    detailSeriesId = series.id
                                                    true
                                                }
                                            },
                                        onFocused = { selectedSeriesId = series.id },
                                        onClick = {
                                            selectedSeriesId = series.id
                                            detailSeriesId = series.id
                                        },
                                    )
                                }
                                if (canLoadMoreSeries) {
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-series") {
                                        ActionPill(
                                            stringResource(R.string.common_load_more),
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { seriesPageCount += 1 },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesCategoryColumn(
    providers: List<Provider>,
    selectedProviderId: String?,
    categories: List<Category>,
    selectedCategoryId: String?,
    onProviderSelected: (Provider) -> Unit,
    onCategorySelected: (Category) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.width(220.dp)) {
        SectionTitle(stringResource(R.string.common_provider_section))
        providers.forEach { provider ->
            ActionPill(
                label = provider.name,
                selected = selectedProviderId == provider.id,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onProviderSelected(provider) },
            )
        }
        SectionTitle(stringResource(R.string.common_categories_section))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxSize()) {
            items(categories, key = { it.id }) { category ->
                ActionPill(
                    label = category.localizedDisplayName(),
                    selected = selectedCategoryId == category.id,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCategorySelected(category) },
                )
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
    episode: Episode?,
    showActions: Boolean = true,
    onOpenPlayer: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val noSeriesStr = stringResource(R.string.movies_no_content)
    val selectProviderStr = stringResource(R.string.series_select_provider)
    HeroPanel(
        title = series?.name ?: noSeriesStr,
        body = series?.plot?.takeIf { it.isNotBlank() } ?: selectProviderStr,
        meta = series?.heroMeta ?: provider?.name,
        modifier = Modifier.fillMaxWidth(),
        backdropModel = backdropModel,
        action = {
            if (series != null && showActions) {
                if (episode != null) {
                    ActionPill(stringResource(R.string.movies_play), onClick = onOpenPlayer)
                }
                ActionPill(if (isFavorite) stringResource(R.string.common_favorites) else stringResource(R.string.series_add_favorite), selected = isFavorite, onClick = onToggleFavorite)
            }
        },
    )
}

@Composable
private fun SeriesDetailPage(
    series: Series,
    provider: Provider?,
    backdropModel: Any?,
    isFavorite: Boolean,
    seasons: List<Season>,
    selectedSeasonId: String?,
    episodes: List<Episode>,
    selectedEpisodeId: String?,
    selectedEpisodeProgress: PlaybackProgress?,
    continueTarget: SeriesContinueTarget?,
    onSeasonSelected: (Season) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onContinueEpisode: (SeriesContinueTarget) -> Unit,
    onMarkEpisodeSeen: () -> Unit,
    onMarkEpisodeUnseen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    SeriesHero(
        series = series,
        provider = provider,
        backdropModel = backdropModel,
        isFavorite = isFavorite,
        episode = episodes.firstOrNull { it.id == selectedEpisodeId } ?: episodes.firstOrNull(),
        showActions = false,
        onOpenPlayer = {},
        onToggleFavorite = onToggleFavorite,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        if (continueTarget != null) {
            ActionPill(
                stringResource(R.string.series_continue),
                modifier = Modifier.testTag(seriesContinueActionTag(series.id)),
                onClick = { onContinueEpisode(continueTarget) },
            )
            StatusBadge(continueTarget.cardMeta)
        }
        ActionPill(if (isFavorite) stringResource(R.string.common_favorites) else stringResource(R.string.series_add_favorite), selected = isFavorite, onClick = onToggleFavorite)
        ActionPill(stringResource(R.string.series_back), onClick = onClose)
    }
    SeriesEpisodeSelector(
        seasons = seasons,
        selectedSeasonId = selectedSeasonId,
        onSeasonSelected = onSeasonSelected,
        episodes = episodes,
        selectedEpisodeId = selectedEpisodeId,
        selectedEpisodeProgress = selectedEpisodeProgress,
        onEpisodeSelected = onEpisodeSelected,
        onEpisodePlay = onEpisodePlay,
        onMarkEpisodeSeen = onMarkEpisodeSeen,
        onMarkEpisodeUnseen = onMarkEpisodeUnseen,
    )
}

@Composable
private fun SeriesEpisodeSelector(
    seasons: List<Season>,
    selectedSeasonId: String?,
    onSeasonSelected: (Season) -> Unit,
    episodes: List<Episode>,
    selectedEpisodeId: String?,
    selectedEpisodeProgress: PlaybackProgress? = null,
    onEpisodeSelected: (Episode) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onMarkEpisodeSeen: () -> Unit = {},
    onMarkEpisodeUnseen: () -> Unit = {},
) {
    if (seasons.isEmpty()) return

    val staffelStr = stringResource(R.string.series_season)
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            items(seasons, key = { it.id }) { season ->
                ActionPill(
                    label = season.name.ifBlank { "$staffelStr ${season.seasonNumber}" },
                    selected = season.id == selectedSeasonId,
                    onClick = { onSeasonSelected(season) },
                )
            }
        }

        if (episodes.isEmpty()) {
            InfoPanel(
                title = stringResource(R.string.series_no_episodes),
                body = stringResource(R.string.series_no_episodes_body),
                badge = stringResource(R.string.common_empty_badge),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                items(episodes, key = { it.id }) { episode ->
                    ActionPill(
                        label = "S${episode.seasonNumber}E${episode.episodeNumber} ${episode.name}",
                        selected = episode.id == selectedEpisodeId,
                        modifier = Modifier.testTag(seriesEpisodeTag(episode.id)),
                        onClick = {
                            onEpisodeSelected(episode)
                            onEpisodePlay(episode)
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                if (selectedEpisodeProgress?.isCompleted == true) {
                    StatusBadge(stringResource(R.string.series_watched))
                    ActionPill(stringResource(R.string.movies_mark_unwatched), onClick = onMarkEpisodeUnseen)
                } else {
                    ActionPill(stringResource(R.string.movies_mark_watched), onClick = onMarkEpisodeSeen)
                }
            }
        }
    }
}

private fun specialCategory(providerId: String, id: String, name: String): Category =
    Category(
        id = id,
        providerId = providerId,
        type = CategoryType.Series,
        remoteId = id,
        name = name,
        sortOrder = Int.MIN_VALUE,
        isHidden = false,
    )

@Composable
private fun emptyTitle(provider: Provider?, category: Category?): String =
    when {
        provider == null -> stringResource(R.string.common_no_playlists)
        category?.id == FAVORITES_CATEGORY_ID -> stringResource(R.string.series_no_favorites)
        else -> stringResource(R.string.series_none)
    }

@Composable
private fun emptyBody(provider: Provider?, category: Category?): String =
    when {
        provider == null -> stringResource(R.string.common_add_provider)
        category?.id == FAVORITES_CATEGORY_ID -> stringResource(R.string.series_favorites_empty)
        category == null -> stringResource(R.string.series_no_categories_body)
        else -> stringResource(R.string.series_no_series_body)
    }

@Composable
private fun Category.localizedDisplayName(): String =
    if (remoteId == "__UNCATEGORIZED__") stringResource(R.string.category_uncategorized) else name

private fun Series.cardMeta(typeBadge: String): String =
    listOfNotNull(year, rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" }).joinToString(" | ").ifBlank { typeBadge }

private val Series.heroMeta: String?
    get() = listOfNotNull(
        rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" },
        genre?.takeIf { it.isNotBlank() },
        year?.takeIf { it.isNotBlank() },
    ).joinToString(" | ").ifBlank { null }

private data class SeriesContinueTarget(
    val progress: PlaybackProgress,
    val episode: Episode,
)

private val SeriesContinueTarget.cardMeta: String
    get() = "${progress.progressPercent} % | S${episode.seasonNumber}E${episode.episodeNumber} ${episode.name}"

internal fun seriesPosterTag(seriesId: String): String = "series-poster-$seriesId"

internal fun seriesEpisodeTag(episodeId: String): String = "series-episode-$episodeId"

internal fun seriesContinueActionTag(seriesId: String): String = "series-continue-$seriesId"

private fun Episode.completedProgress(existing: PlaybackProgress?, now: Long): PlaybackProgress =
    PlaybackProgress(
        id = existing?.id ?: playbackProgressId(providerId, MediaType.Episode, id),
        providerId = providerId,
        mediaType = MediaType.Episode,
        mediaId = id,
        positionMillis = existing?.durationMillis?.takeIf { it > 0L } ?: existing?.positionMillis?.takeIf { it > 0L } ?: 1L,
        durationMillis = existing?.durationMillis?.takeIf { it > 0L } ?: 1L,
        progressPercent = 100,
        isCompleted = true,
        lastWatchedAt = now,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )

private fun playbackProgressId(providerId: String, mediaType: MediaType, mediaId: String): String =
    "progress-$providerId-${mediaType.name.lowercase(Locale.getDefault())}-$mediaId"

private const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
private const val CONTINUE_CATEGORY_ID = "__CONTINUE__"
private val SPECIAL_CATEGORY_IDS = setOf(FAVORITES_CATEGORY_ID, CONTINUE_CATEGORY_ID)
private const val VOD_PAGE_SIZE = 80
