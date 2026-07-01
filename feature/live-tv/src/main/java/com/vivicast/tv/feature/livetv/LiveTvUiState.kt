package com.vivicast.tv.feature.livetv

import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Provider

/**
 * Immutable presentation state for the live-tv screen. Holds the repository-derived data
 * (providers, categories, the resolved channel list, favorites and the EPG programs plus the
 * frozen "now" and current/next program for the selected channel). Localized strings, focus/
 * D-Pad handling, the column mode and the preview/fullscreen triggers stay in the composable.
 *
 * [channelResetSignal] is bumped whenever the ViewModel auto-selects a channel because the
 * previous selection left the list; the composable mirrors the original `previewStarted = false`
 * side effect on that signal.
 */
internal data class LiveTvUiState(
    val providers: List<Provider> = emptyList(),
    val selectedProviderId: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val channels: List<Channel> = emptyList(),
    val selectedChannelId: String? = null,
    val favoriteChannelIds: Set<String> = emptySet(),
    val favoriteChannelCount: Int = 0,
    val selectedProvider: Provider? = null,
    val selectedCategory: Category? = null,
    val selectedChannel: Channel? = null,
    val channelProvider: Provider? = null,
    val canLoadMore: Boolean = false,
    val nowMillis: Long = 0L,
    val selectedPrograms: List<EpgProgram> = emptyList(),
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val channelResetSignal: Int = 0,
)

internal const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
internal const val LIVE_TV_PAGE_SIZE = 80
internal const val EPG_PAST_WINDOW_MILLIS = 4L * 60L * 60L * 1000L
internal const val EPG_FUTURE_WINDOW_MILLIS = 8L * 60L * 60L * 1000L
