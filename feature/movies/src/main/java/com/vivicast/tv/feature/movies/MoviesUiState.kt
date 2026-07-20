package com.vivicast.tv.feature.movies

import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider

/**
 * Immutable presentation state for the movies GRID screen. Holds the repository-derived data (providers,
 * categories, the resolved+sorted movie list, favorites, continue progress). Movie detail is now a separate
 * self-contained destination ([MovieDetailViewModel]) — the grid no longer carries any detail state.
 * Localized strings, image loading and the pure-visual hero highlight stay in the composable layer.
 */
internal data class MoviesUiState(
    val providers: List<Provider> = emptyList(),
    val selectedProviderId: String? = null,
    val categories: List<Category> = emptyList(),
    val hasContinueMovies: Boolean = false,
    val selectedCategoryId: String? = null,
    val movies: List<Movie> = emptyList(),
    val favoriteMovieIds: Set<String> = emptySet(),
    val continueProgressByMovieId: Map<String, PlaybackProgress> = emptyMap(),
    val selectedProvider: Provider? = null,
    val canLoadMore: Boolean = false,
)

internal const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
internal const val CONTINUE_CATEGORY_ID = "__CONTINUE__"
internal val SPECIAL_CATEGORY_IDS = setOf(FAVORITES_CATEGORY_ID, CONTINUE_CATEGORY_ID)
internal const val VOD_PAGE_SIZE = 80
