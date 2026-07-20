package com.vivicast.tv.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
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
 * Presentation-state holder for the series GRID screen. Owns the fachlich grid state (selected
 * provider/category/series, page size) and combines the provider, media, favorites and playback repositories
 * into an immutable [SeriesUiState]. No Android Context/Resources, no Compose types, no navigation, no
 * localized strings.
 *
 * Series detail (seasons / episodes / progress / mark-seen) is a separate self-contained destination
 * ([SeriesDetailViewModel]); this VM holds no detail state. [scope] lets unit tests inject a controlled scope.
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
    private val pageCountFlow = MutableStateFlow(1)

    private var providersRaw: List<Provider> = emptyList()
    private var categories: List<Category> = emptyList()
    private var favorites: List<Favorite> = emptyList()
    private var favoriteSeries: List<Series> = emptyList()
    private var continueTargets: Map<String, SeriesContinueTarget> = emptyMap()
    private var continueSeries: List<Series> = emptyList()
    private var observedSeries: List<Series> = emptyList()
    private var loadedSelectedSeries: Series? = null

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

    fun onToggleFavorite(seriesId: String) {
        val providerId = selectedProviderIdFlow.value ?: return
        coroutineScope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Series, seriesId) }
    }

    fun onLoadMore() {
        pageCountFlow.value += 1
    }

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

        // Auto-select first series when the current selection is gone (mirrors LaunchedEffect(seriesItems)).
        val currentSeriesId = selectedSeriesIdFlow.value
        if (currentSeriesId == null || seriesItems.none { it.id == currentSeriesId }) {
            selectedSeriesIdFlow.value = seriesItems.firstOrNull()?.id
        }
        val selectedSeries = seriesItems.firstOrNull { it.id == selectedSeriesIdFlow.value } ?: loadedSelectedSeries

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
            canLoadMore = categoryId !in SPECIAL_CATEGORY_IDS &&
                observedSeries.size >= pageCountFlow.value * VOD_PAGE_SIZE,
        )
    }
}
