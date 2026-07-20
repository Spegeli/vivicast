package com.vivicast.tv.feature.series

import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.Series

/**
 * Immutable presentation state for the series GRID screen. Holds the repository-derived data (providers,
 * categories, the resolved+sorted series list, favorites, continue targets). Series detail (seasons /
 * episodes / progress) is now a separate self-contained destination ([SeriesDetailViewModel]) — the grid no
 * longer carries any detail state. Localized strings and image loading stay in the composable layer.
 */
internal data class SeriesUiState(
    val providers: List<Provider> = emptyList(),
    val selectedProviderId: String? = null,
    val categories: List<Category> = emptyList(),
    val hasContinueSeries: Boolean = false,
    val selectedCategoryId: String? = null,
    val seriesItems: List<Series> = emptyList(),
    val favoriteSeriesIds: Set<String> = emptySet(),
    val continueTargetsBySeriesId: Map<String, SeriesContinueTarget> = emptyMap(),
    val selectedProvider: Provider? = null,
    val selectedSeries: Series? = null,
    val canLoadMore: Boolean = false,
)

internal data class SeriesContinueTarget(
    val progress: PlaybackProgress,
    val episode: Episode,
)

internal val SeriesContinueTarget.cardMeta: String
    get() = "${progress.progressPercent} % | S${episode.seasonNumber}E${episode.episodeNumber} ${episode.name}"

internal const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
internal const val CONTINUE_CATEGORY_ID = "__CONTINUE__"
internal val SPECIAL_CATEGORY_IDS = setOf(FAVORITES_CATEGORY_ID, CONTINUE_CATEGORY_ID)
internal const val VOD_PAGE_SIZE = 80
