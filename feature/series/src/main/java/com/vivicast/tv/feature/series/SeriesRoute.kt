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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series

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
    val viewModel: SeriesViewModel = viewModel(
        factory = SeriesViewModelFactory(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val strFavorites = stringResource(R.string.common_favorites)
    val strContinue = stringResource(R.string.series_continue)
    val strSeriesTypeBadge = stringResource(R.string.series_type_badge)

    LaunchedEffect(targetProviderId, targetCategoryId, targetSeriesId, targetSeasonId, targetEpisodeId) {
        viewModel.onTarget(targetProviderId, targetCategoryId, targetSeriesId, targetSeasonId, targetEpisodeId)
    }
    LaunchedEffect(uiState.consumedTargetSeriesId) {
        val consumed = uiState.consumedTargetSeriesId
        if (consumed != null && consumed == targetSeriesId) {
            onTargetConsumed()
        }
    }

    val categoriesWithSpecials = remember(uiState.selectedProviderId, uiState.categories, uiState.hasContinueSeries, strFavorites, strContinue) {
        val providerId = uiState.selectedProviderId
        if (providerId != null) {
            buildList {
                add(specialCategory(providerId, FAVORITES_CATEGORY_ID, strFavorites))
                if (uiState.hasContinueSeries) {
                    add(specialCategory(providerId, CONTINUE_CATEGORY_ID, strContinue))
                }
                addAll(uiState.categories)
            }
        } else {
            uiState.categories
        }
    }

    val seriesItems = uiState.seriesItems
    val favoriteSeriesIds = uiState.favoriteSeriesIds
    val continueSeriesTargets = uiState.continueTargetsBySeriesId
    val selectedSeries = uiState.selectedSeries
    val detailSeries = uiState.detailSeries
    val selectedProvider = uiState.selectedProvider
    val selectedCategory = categoriesWithSpecials.firstOrNull { it.id == uiState.selectedCategoryId }
    val selectedEpisode = uiState.selectedEpisode
    val detailContinueTarget = detailSeries?.let { continueSeriesTargets[it.id] }
    val backdropModel by produceState<Any?>(initialValue = null, selectedSeries?.id, selectedSeries?.backdropUrl) {
        value = selectedSeries?.let { resolveSeriesBackdropModel(it) }
    }
    val canLoadMoreSeries = uiState.canLoadMore

    BackHandler(enabled = uiState.detailSeriesId != null) {
        viewModel.onCloseDetail()
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            if (detailSeries != null) {
                SeriesDetailPage(
                    series = detailSeries,
                    provider = selectedProvider,
                    backdropModel = backdropModel,
                    isFavorite = detailSeries.id in favoriteSeriesIds,
                    seasons = uiState.seasons,
                    selectedSeasonId = uiState.selectedSeasonId,
                    episodes = uiState.episodes,
                    selectedEpisodeId = uiState.selectedEpisodeId,
                    selectedEpisodeProgress = uiState.selectedEpisodeProgress,
                    continueTarget = detailContinueTarget,
                    onSeasonSelected = { viewModel.onSeasonSelected(it.id) },
                    onEpisodeSelected = { viewModel.onEpisodeSelected(it.id) },
                    onEpisodePlay = onOpenPlayer,
                    onContinueEpisode = { target ->
                        viewModel.onContinueEpisode(target)
                        onOpenPlayer(target.episode)
                    },
                    onMarkEpisodeSeen = { viewModel.onMarkEpisodeSeen() },
                    onMarkEpisodeUnseen = { viewModel.onMarkEpisodeUnseen() },
                    onToggleFavorite = { viewModel.onToggleFavorite(detailSeries.id) },
                    onClose = { viewModel.onCloseDetail() },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
                    SeriesCategoryColumn(
                        providers = uiState.providers,
                        selectedProviderId = uiState.selectedProviderId,
                        categories = categoriesWithSpecials,
                        selectedCategoryId = uiState.selectedCategoryId,
                        onProviderSelected = { viewModel.onProviderSelected(it.id) },
                        onCategorySelected = { viewModel.onCategorySelected(it.id) },
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
                            onToggleFavorite = { selectedSeries?.id?.let { viewModel.onToggleFavorite(it) } },
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
                                                    viewModel.onOpenSeriesDetail(series.id)
                                                    true
                                                }
                                            },
                                        onFocused = { viewModel.onSeriesFocused(series.id) },
                                        onClick = { viewModel.onOpenSeriesDetail(series.id) },
                                    )
                                }
                                if (canLoadMoreSeries) {
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-series") {
                                        ActionPill(
                                            stringResource(R.string.common_load_more),
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { viewModel.onLoadMore() },
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

internal fun seriesPosterTag(seriesId: String): String = "series-poster-$seriesId"

internal fun seriesEpisodeTag(episodeId: String): String = "series-episode-$episodeId"

internal fun seriesContinueActionTag(seriesId: String): String = "series-continue-$seriesId"
