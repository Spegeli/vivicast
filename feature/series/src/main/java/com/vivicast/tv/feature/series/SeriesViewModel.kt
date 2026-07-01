package com.vivicast.tv.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Presentation-state holder for the series screen. Owns the fachlich screen state
 * (selected provider/category/series/season/episode, page size, opened detail) and
 * combines the provider, media, favorites and playback repositories into an immutable
 * [SeriesUiState]. No Android Context/Resources, no Compose types, no navigation, no
 * localized strings. [scope] lets unit tests inject a controlled scope; production uses
 * [viewModelScope].
 *
 * All original SeriesRoute guards are preserved: provider/category/series/season/episode
 * auto-selection, the detail auto-close guard, page reset on provider/category change and
 * the staged (series -> season -> episode) deep-link target consumption.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SeriesViewModel(
    private val providerRepository: ProviderRepository,
    private val mediaRepository: MediaRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private val selectedProviderIdFlow = MutableStateFlow<String?>(null)
    private val selectedCategoryIdFlow = MutableStateFlow<String?>(null)
    private val selectedSeriesIdFlow = MutableStateFlow<String?>(null)
    private val selectedSeasonIdFlow = MutableStateFlow<String?>(null)
    private val selectedEpisodeIdFlow = MutableStateFlow<String?>(null)
    private val detailSeriesIdFlow = MutableStateFlow<String?>(null)
    private val pageCountFlow = MutableStateFlow(1)

    private var providersRaw: List<Provider> = emptyList()
    private var categories: List<Category> = emptyList()
    private var favorites: List<Favorite> = emptyList()
    private var favoriteSeries: List<Series> = emptyList()
    private var continueTargets: Map<String, SeriesContinueTarget> = emptyMap()
    private var continueSeries: List<Series> = emptyList()
    private var observedSeries: List<Series> = emptyList()
    private var loadedSelectedSeries: Series? = null
    private var loadedDetailSeries: Series? = null
    private var seasons: List<Season> = emptyList()
    private var episodes: List<Episode> = emptyList()
    private var selectedEpisodeProgressLoaded: PlaybackProgress? = null
    private var consumedTargetSeriesId: String? = null

    private var targetProvider: String? = null
    private var targetCategory: String? = null
    private var targetSeries: String? = null
    private var targetSeason: String? = null
    private var targetEpisode: String? = null

    private val _uiState = MutableStateFlow(SeriesUiState())
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            providerRepository.observeProviders().collect { all ->
                providersRaw = all
                val seriesProviders = all.filter { it.includeSeries }
                val current = selectedProviderIdFlow.value
                if (current == null || seriesProviders.none { it.id == current }) {
                    selectedProviderIdFlow.value = seriesProviders.firstOrNull { it.isActive }?.id
                        ?: seriesProviders.firstOrNull()?.id
                }
                rebuild()
            }
        }

        coroutineScope.launch {
            selectedProviderIdFlow.flatMapLatest { providerId ->
                if (providerId == null) {
                    flowOf(Triple(emptyList<Category>(), emptyList<Favorite>(), emptyList<PlaybackProgress>()))
                } else {
                    combine(
                        mediaRepository.observeCategories(providerId, CategoryType.Series),
                        favoritesRepository.observeFavorites(providerId, MediaType.Series),
                        playbackRepository.observeContinueWatching(providerId),
                    ) { categories, favorites, progress -> Triple(categories, favorites, progress) }
                }
            }.collect { (cats, favs, progress) ->
                val providerId = selectedProviderIdFlow.value
                categories = cats
                favorites = favs
                continueTargets = progress
                    .filter { it.mediaType == MediaType.Episode }
                    .sortedByDescending { it.lastWatchedAt }
                    .mapNotNull { p ->
                        mediaRepository.getEpisode(p.providerId, p.mediaId)
                            ?.takeIf { it.providerId == providerId }
                            ?.let { episode -> episode.seriesId to SeriesContinueTarget(p, episode) }
                    }
                    .distinctBy { it.first }
                    .toMap()
                favoriteSeries = favs.mapNotNull { mediaRepository.getSeries(it.providerId, it.mediaId) }
                continueSeries = continueTargets.values
                    .sortedByDescending { it.progress.lastWatchedAt }
                    .mapNotNull { mediaRepository.getSeries(it.episode.providerId, it.episode.seriesId) }
                ensureCategorySelected()
                trySeriesTargetStage()
                rebuild()
            }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, selectedCategoryIdFlow) { p, c -> p to c }
                .collect { pageCountFlow.value = 1 }
        }

        coroutineScope.launch {
            combine(
                selectedProviderIdFlow,
                selectedCategoryIdFlow,
                pageCountFlow,
            ) { p, c, page -> Triple(p, c, page) }
                .flatMapLatest { (providerId, categoryId, page) ->
                    if (providerId == null || categoryId in SPECIAL_CATEGORY_IDS) {
                        flowOf(emptyList())
                    } else {
                        mediaRepository.observeSeriesPage(providerId, categoryId, page * VOD_PAGE_SIZE)
                    }
                }
                .collect { series ->
                    observedSeries = series
                    trySeriesTargetStage()
                    rebuild()
                }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, selectedSeriesIdFlow) { p, s -> p to s }
                .collect { (providerId, seriesId) ->
                    loadedSelectedSeries = if (providerId != null && seriesId != null) {
                        mediaRepository.getSeries(providerId, seriesId)
                    } else {
                        null
                    }
                    rebuild()
                }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, detailSeriesIdFlow) { p, s -> p to s }
                .collect { (providerId, seriesId) ->
                    loadedDetailSeries = if (providerId != null && seriesId != null) {
                        mediaRepository.getSeries(providerId, seriesId)
                    } else {
                        null
                    }
                    rebuild()
                }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, selectedSeriesIdFlow) { p, s -> p to s }
                .flatMapLatest { (providerId, seriesId) ->
                    if (providerId == null || seriesId == null) {
                        flowOf(emptyList())
                    } else {
                        mediaRepository.observeSeasons(providerId, seriesId)
                    }
                }
                .collect { loadedSeasons ->
                    seasons = loadedSeasons
                    ensureSeasonSelected()
                    trySeasonTargetStage()
                    rebuild()
                }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, selectedSeasonIdFlow) { p, s -> p to s }
                .flatMapLatest { (providerId, seasonId) ->
                    if (providerId == null || seasonId == null) {
                        flowOf(emptyList())
                    } else {
                        mediaRepository.observeEpisodes(providerId, seasonId)
                    }
                }
                .collect { loadedEpisodes ->
                    episodes = loadedEpisodes
                    ensureEpisodeSelected()
                    tryEpisodeTargetStage()
                    rebuild()
                }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, selectedEpisodeIdFlow) { p, e -> p to e }
                .collect { (providerId, episodeId) ->
                    selectedEpisodeProgressLoaded = if (providerId != null && episodeId != null) {
                        playbackRepository.getProgress(providerId, MediaType.Episode, episodeId)
                    } else {
                        null
                    }
                    rebuild()
                }
        }
    }

    fun onProviderSelected(providerId: String) {
        selectedProviderIdFlow.value = providerId
        selectedCategoryIdFlow.value = null
        selectedSeriesIdFlow.value = null
    }

    fun onCategorySelected(categoryId: String) {
        selectedCategoryIdFlow.value = categoryId
        selectedSeriesIdFlow.value = null
    }

    fun onSeriesFocused(seriesId: String) {
        selectedSeriesIdFlow.value = seriesId
    }

    fun onOpenSeriesDetail(seriesId: String) {
        selectedSeriesIdFlow.value = seriesId
        detailSeriesIdFlow.value = seriesId
    }

    fun onCloseDetail() {
        detailSeriesIdFlow.value = null
    }

    fun onSeasonSelected(seasonId: String) {
        // Reset the episode first so the reloaded season's episodes auto-select the first one.
        selectedEpisodeIdFlow.value = null
        selectedSeasonIdFlow.value = seasonId
    }

    fun onEpisodeSelected(episodeId: String) {
        selectedEpisodeIdFlow.value = episodeId
    }

    fun onContinueEpisode(target: SeriesContinueTarget) {
        selectedSeasonIdFlow.value = target.episode.seasonId
        selectedEpisodeIdFlow.value = target.episode.id
    }

    fun onToggleFavorite(seriesId: String) {
        val providerId = selectedProviderIdFlow.value ?: return
        coroutineScope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Series, seriesId) }
    }

    fun onMarkEpisodeSeen() {
        val episode = currentSelectedEpisode() ?: return
        coroutineScope.launch {
            val now = System.currentTimeMillis()
            val completed = episode.completedProgress(selectedEpisodeProgressLoaded, now)
            playbackRepository.saveProgress(completed)
            selectedEpisodeProgressLoaded = completed
            rebuild()
        }
    }

    fun onMarkEpisodeUnseen() {
        val providerId = selectedProviderIdFlow.value ?: return
        val episode = currentSelectedEpisode() ?: return
        coroutineScope.launch {
            playbackRepository.deleteProgress(providerId, MediaType.Episode, episode.id)
            selectedEpisodeProgressLoaded = null
            rebuild()
        }
    }

    fun onLoadMore() {
        pageCountFlow.value += 1
    }

    fun onTarget(
        targetProviderId: String?,
        targetCategoryId: String?,
        targetSeriesId: String?,
        targetSeasonId: String?,
        targetEpisodeId: String?,
    ) {
        if (targetSeriesId == null) return
        targetProvider = targetProviderId
        targetCategory = targetCategoryId
        targetSeries = targetSeriesId
        targetSeason = targetSeasonId
        targetEpisode = targetEpisodeId
        selectedProviderIdFlow.value = targetProviderId
        selectedCategoryIdFlow.value = targetCategoryId
        coroutineScope.launch { trySeriesTargetStage() }
    }

    private fun currentSelectedEpisode(): Episode? =
        episodes.firstOrNull { it.id == selectedEpisodeIdFlow.value } ?: episodes.firstOrNull()

    private fun ensureCategorySelected() {
        if (selectedProviderIdFlow.value == null) return
        val validIds = buildSet {
            add(FAVORITES_CATEGORY_ID)
            if (continueTargets.isNotEmpty()) add(CONTINUE_CATEGORY_ID)
            addAll(categories.map { it.id })
        }
        val current = selectedCategoryIdFlow.value
        if (current == null || current !in validIds) {
            selectedCategoryIdFlow.value = categories.firstOrNull()?.id ?: FAVORITES_CATEGORY_ID
        }
    }

    private fun ensureSeasonSelected() {
        val current = selectedSeasonIdFlow.value
        if (current == null || seasons.none { it.id == current }) {
            selectedSeasonIdFlow.value = seasons.firstOrNull()?.id
        }
    }

    private fun ensureEpisodeSelected() {
        val current = selectedEpisodeIdFlow.value
        if (current == null || episodes.none { it.id == current }) {
            selectedEpisodeIdFlow.value = episodes.firstOrNull()?.id
        }
    }

    private suspend fun trySeriesTargetStage() {
        val series = targetSeries ?: return
        val exists = observedSeries.any { it.id == series } ||
            (targetProvider?.let { mediaRepository.getSeries(it, series) } != null)
        if (exists) {
            selectedSeriesIdFlow.value = series
            detailSeriesIdFlow.value = series
            if (targetSeason == null && targetEpisode == null) {
                consumeTarget()
                rebuild()
            } else {
                // Seasons for this series may already be loaded (series unchanged), so chain directly.
                trySeasonTargetStage()
            }
        }
    }

    private fun trySeasonTargetStage() {
        val season = targetSeason ?: return
        if (seasons.any { it.id == season }) {
            selectedSeasonIdFlow.value = season
            if (targetEpisode == null) {
                consumeTarget()
                rebuild()
            } else {
                // Episodes for this season may already be loaded (season unchanged), so chain directly.
                tryEpisodeTargetStage()
            }
        }
    }

    private fun tryEpisodeTargetStage() {
        val episode = targetEpisode ?: return
        if (episodes.any { it.id == episode }) {
            selectedEpisodeIdFlow.value = episode
            consumeTarget()
            rebuild()
        }
    }

    private fun consumeTarget() {
        consumedTargetSeriesId = targetSeries
        targetProvider = null
        targetCategory = null
        targetSeries = null
        targetSeason = null
        targetEpisode = null
    }

    private fun rebuild() {
        val providerId = selectedProviderIdFlow.value
        val categoryId = selectedCategoryIdFlow.value
        val seriesProviders = providersRaw.filter { it.includeSeries }
        val favoriteIds = favorites.mapTo(mutableSetOf()) { it.mediaId }
        val favoriteOrder = favorites.mapIndexed { index, favorite -> favorite.mediaId to index }.toMap()
        val continueOrder = continueTargets.values
            .sortedByDescending { it.progress.lastWatchedAt }
            .mapIndexed { index, target -> target.episode.seriesId to index }
            .toMap()
        val seriesItems = when (categoryId) {
            CONTINUE_CATEGORY_ID -> continueSeries.sortedWith(
                compareBy<Series> { continueOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase(Locale.getDefault()) },
            )
            FAVORITES_CATEGORY_ID -> favoriteSeries.sortedWith(
                compareBy<Series> { favoriteOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase(Locale.getDefault()) },
            )
            else -> observedSeries
        }

        // Auto-select first series when the current selection is gone (LaunchedEffect(seriesItems)).
        val currentSeriesId = selectedSeriesIdFlow.value
        if (currentSeriesId == null || seriesItems.none { it.id == currentSeriesId }) {
            selectedSeriesIdFlow.value = seriesItems.firstOrNull()?.id
        }
        // Auto-close a still-open detail when its series left the current, non-empty list.
        val currentDetailId = detailSeriesIdFlow.value
        val detailClosed = currentDetailId != null &&
            seriesItems.isNotEmpty() &&
            seriesItems.none { it.id == currentDetailId }
        if (detailClosed) {
            detailSeriesIdFlow.value = null
        }

        val effectiveSeriesId = selectedSeriesIdFlow.value
        val effectiveDetailId = if (detailClosed) null else detailSeriesIdFlow.value
        val selectedSeries = seriesItems.firstOrNull { it.id == effectiveSeriesId } ?: loadedSelectedSeries
        val detailSeries = if (effectiveDetailId == null) {
            null
        } else {
            seriesItems.firstOrNull { it.id == effectiveDetailId } ?: loadedDetailSeries
        }
        val selectedEpisode = episodes.firstOrNull { it.id == selectedEpisodeIdFlow.value } ?: episodes.firstOrNull()

        _uiState.value = SeriesUiState(
            providers = seriesProviders,
            selectedProviderId = providerId,
            categories = categories,
            hasContinueSeries = continueTargets.isNotEmpty(),
            selectedCategoryId = categoryId,
            seriesItems = seriesItems,
            favoriteSeriesIds = favoriteIds,
            continueTargetsBySeriesId = continueTargets,
            selectedProvider = providersRaw.firstOrNull { it.id == providerId },
            selectedSeries = selectedSeries,
            detailSeriesId = effectiveDetailId,
            detailSeries = detailSeries,
            seasons = seasons,
            selectedSeasonId = selectedSeasonIdFlow.value,
            episodes = episodes,
            selectedEpisodeId = selectedEpisodeIdFlow.value,
            selectedEpisode = selectedEpisode,
            selectedEpisodeProgress = selectedEpisodeProgressLoaded,
            canLoadMore = categoryId !in SPECIAL_CATEGORY_IDS &&
                observedSeries.size >= pageCountFlow.value * VOD_PAGE_SIZE,
            consumedTargetSeriesId = consumedTargetSeriesId,
        )
    }
}

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
