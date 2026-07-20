package com.vivicast.tv.feature.movies

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
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
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
 * Presentation-state holder for the movies GRID screen. Owns the fachlich grid state (selected
 * provider/category, page size) and combines the provider, media, favorites and playback repositories into
 * an immutable [MoviesUiState]. No Android Context/Resources, no Compose types, no navigation, no localized
 * strings.
 *
 * Movie detail is a separate self-contained destination ([MovieDetailViewModel]); this VM holds no detail
 * state. The pure-visual hero highlight remains in the composable layer. [scope] lets unit tests inject a
 * controlled scope; production uses [viewModelScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MoviesViewModel(
    private val providerRepository: ProviderRepository,
    private val mediaRepository: MediaRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private val selectedProviderIdFlow = MutableStateFlow<String?>(null)
    private val selectedCategoryIdFlow = MutableStateFlow<String?>(null)
    private val pageCountFlow = MutableStateFlow(1)

    private var allProviders: List<Provider> = emptyList()
    private var categories: List<Category> = emptyList()
    private var favorites: List<Favorite> = emptyList()
    private var favoriteMovies: List<Movie> = emptyList()
    private var continueMovieProgress: Map<String, PlaybackProgress> = emptyMap()
    private var continueMovies: List<Movie> = emptyList()
    private var observedMovies: List<Movie> = emptyList()

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            providerRepository.observeProviders().collect { all ->
                allProviders = all
                val movieProviders = all.filter { it.includeMovies }
                val current = selectedProviderIdFlow.value
                if (current == null || movieProviders.none { it.id == current }) {
                    selectedProviderIdFlow.value = movieProviders.firstOrNull { it.isActive }?.id
                        ?: movieProviders.firstOrNull()?.id
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
                        mediaRepository.observeCategories(providerId, CategoryType.Movies),
                        favoritesRepository.observeFavorites(providerId, MediaType.Movie),
                        playbackRepository.observeContinueWatching(providerId),
                    ) { categories, favorites, progress -> Triple(categories, favorites, progress) }
                }
            }.collect { (cats, favs, progress) ->
                categories = cats
                favorites = favs
                continueMovieProgress = progress
                    .filter { it.mediaType == MediaType.Movie }
                    .associateBy { it.mediaId }
                favoriteMovies = favs.mapNotNull { mediaRepository.getMovie(it.providerId, it.mediaId) }
                continueMovies = continueMovieProgress.values
                    .sortedByDescending { it.lastWatchedAt }
                    .mapNotNull { mediaRepository.getMovie(it.providerId, it.mediaId) }
                ensureCategorySelected()
                rebuild()
            }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, selectedCategoryIdFlow) { providerId, categoryId ->
                providerId to categoryId
            }.collect { pageCountFlow.value = 1 }
        }

        coroutineScope.launch {
            combine(
                selectedProviderIdFlow,
                selectedCategoryIdFlow,
                pageCountFlow,
            ) { providerId, categoryId, pageCount -> Triple(providerId, categoryId, pageCount) }
                .flatMapLatest { (providerId, categoryId, pageCount) ->
                    if (providerId == null || categoryId in SPECIAL_CATEGORY_IDS) {
                        flowOf(emptyList())
                    } else {
                        mediaRepository.observeMoviesPage(
                            providerId = providerId,
                            categoryId = categoryId,
                            limit = pageCount * VOD_PAGE_SIZE,
                        )
                    }
                }
                .collect { movies ->
                    observedMovies = movies
                    rebuild()
                }
        }
    }

    fun onProviderSelected(providerId: String) {
        selectedProviderIdFlow.value = providerId
        selectedCategoryIdFlow.value = null
    }

    fun onCategorySelected(categoryId: String) {
        selectedCategoryIdFlow.value = categoryId
    }

    fun onLoadMore() {
        pageCountFlow.value += 1
    }

    fun onToggleFavorite(movieId: String) {
        val providerId = selectedProviderIdFlow.value ?: return
        coroutineScope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Movie, movieId) }
    }

    private fun ensureCategorySelected() {
        if (selectedProviderIdFlow.value == null) return
        val validIds = buildSet {
            add(FAVORITES_CATEGORY_ID)
            if (continueMovieProgress.isNotEmpty()) add(CONTINUE_CATEGORY_ID)
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
        val movieProviders = allProviders.filter { it.includeMovies }
        val favoriteIds = favorites.mapTo(mutableSetOf()) { it.mediaId }
        val favoriteOrder = favorites.mapIndexed { index, favorite -> favorite.mediaId to index }.toMap()
        val continueOrder = continueMovieProgress.values
            .sortedByDescending { it.lastWatchedAt }
            .mapIndexed { index, progress -> progress.mediaId to index }
            .toMap()
        val movies = when (categoryId) {
            CONTINUE_CATEGORY_ID -> continueMovies.sortedWith(
                compareBy<Movie> { continueOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase(Locale.getDefault()) },
            )
            FAVORITES_CATEGORY_ID -> favoriteMovies.sortedWith(
                compareBy<Movie> { favoriteOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase(Locale.getDefault()) },
            )
            else -> observedMovies
        }
        _uiState.value = MoviesUiState(
            providers = movieProviders,
            selectedProviderId = providerId,
            categories = categories,
            hasContinueMovies = continueMovieProgress.isNotEmpty(),
            selectedCategoryId = categoryId,
            movies = movies,
            favoriteMovieIds = favoriteIds,
            continueProgressByMovieId = continueMovieProgress,
            selectedProvider = allProviders.firstOrNull { it.id == providerId },
            canLoadMore = categoryId !in SPECIAL_CATEGORY_IDS &&
                observedMovies.size >= pageCountFlow.value * VOD_PAGE_SIZE,
        )
    }
}
