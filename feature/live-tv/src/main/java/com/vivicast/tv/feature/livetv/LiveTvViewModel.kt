package com.vivicast.tv.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.data.epg.EpgRepository
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
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
 * Presentation-state holder for the live-tv screen. Owns the fachlich selection state
 * (provider/category/channel, page size) and combines the provider, media, favorites and EPG
 * repositories into an immutable [LiveTvUiState]. Pure-visual state (column mode, focus area,
 * focus-request signals, preview started) stays in the composable.
 *
 * [nowProvider] freezes the EPG "now" per channel selection (default wall clock; injectable for
 * tests). [scope] lets unit tests inject a controlled scope; production uses [viewModelScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LiveTvViewModel(
    private val providerRepository: ProviderRepository,
    private val mediaRepository: MediaRepository,
    private val epgRepository: EpgRepository,
    private val favoritesRepository: FavoritesRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private val selectedProviderIdFlow = MutableStateFlow<String?>(null)
    private val selectedCategoryIdFlow = MutableStateFlow<String?>(null)
    private val selectedChannelIdFlow = MutableStateFlow<String?>(null)
    private val pageCountFlow = MutableStateFlow(1)
    private val expandedProviderIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val selectedChannelFlow = MutableStateFlow<Channel?>(null)

    private var providersRaw: List<Provider> = emptyList()
    private var categories: List<Category> = emptyList()
    private var favorites: List<Favorite> = emptyList()
    private var favoriteChannels: List<Channel> = emptyList()
    private var observedChannels: List<Channel> = emptyList()
    private var targetChannelLoaded: Channel? = null
    private var selectedPrograms: List<EpgProgram> = emptyList()
    private var nowMillisState: Long = 0L
    private var targetEpgStartTime: Long? = null
    private var lastGuardChannels: List<Channel>? = null
    private var channelResetSignal: Int = 0

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            providerRepository.observeProviders().collect { all ->
                providersRaw = all
                val current = selectedProviderIdFlow.value
                // Only active providers are browsable; re-pick if the selected one is gone or deactivated.
                if (current == null || all.none { it.id == current && it.isActive }) {
                    selectedProviderIdFlow.value = all.firstOrNull { it.isActive }?.id
                }
                rebuild()
            }
        }

        coroutineScope.launch {
            favoritesRepository.observeFavorites(MediaType.Channel).collect { favs ->
                favorites = favs
                favoriteChannels = favs.mapNotNull { mediaRepository.getChannel(it.providerId, it.mediaId) }
                rebuild()
            }
        }

        coroutineScope.launch {
            selectedProviderIdFlow.flatMapLatest { providerId ->
                if (providerId == null) flowOf(emptyList()) else mediaRepository.observeCategories(providerId, CategoryType.LiveTv)
            }.collect { cats ->
                categories = cats
                ensureCategorySelected()
                rebuild()
            }
        }

        coroutineScope.launch {
            combine(selectedProviderIdFlow, expandedProviderIdsFlow) { p, e -> p to e }
                .collect {
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
                    if (providerId == null || categoryId == FAVORITES_CATEGORY_ID) {
                        flowOf(emptyList())
                    } else {
                        mediaRepository.observeChannelsPage(
                            providerId = providerId,
                            categoryId = categoryId,
                            limit = page * LIVE_TV_PAGE_SIZE,
                        )
                    }
                }
                .collect { channels ->
                    observedChannels = channels
                    rebuild()
                }
        }

        coroutineScope.launch {
            selectedChannelFlow.flatMapLatest { channel ->
                nowMillisState = targetEpgStartTime ?: nowProvider()
                if (channel == null) {
                    flowOf(emptyList())
                } else {
                    epgRepository.observeProgramsForChannel(
                        providerId = channel.providerId,
                        channelId = channel.id,
                        fromMillis = nowMillisState - EPG_PAST_WINDOW_MILLIS,
                        toMillis = nowMillisState + EPG_FUTURE_WINDOW_MILLIS,
                    )
                }
            }.collect { programs ->
                selectedPrograms = programs
                rebuild()
            }
        }
    }

    fun onExpandedProvidersChanged(expanded: Set<String>) {
        expandedProviderIdsFlow.value = expanded
    }

    fun onProviderFocused(providerId: String) {
        selectedProviderIdFlow.value = providerId
        if (providerId !in expandedProviderIdsFlow.value) {
            selectedCategoryIdFlow.value = null
        }
        selectedChannelIdFlow.value = null
    }

    fun onProviderToggled(providerId: String, wasExpanded: Boolean) {
        selectedProviderIdFlow.value = providerId
        if (wasExpanded) {
            selectedCategoryIdFlow.value = null
        }
        selectedChannelIdFlow.value = null
    }

    fun onGlobalFavoritesSelected() {
        selectedCategoryIdFlow.value = FAVORITES_CATEGORY_ID
        selectedChannelIdFlow.value = null
    }

    fun onCategorySelected(categoryId: String) {
        selectedCategoryIdFlow.value = categoryId
        selectedChannelIdFlow.value = null
    }

    fun onChannelFocused(channelId: String) {
        selectedChannelIdFlow.value = channelId
    }

    fun onLoadMore() {
        pageCountFlow.value += 1
    }

    fun onToggleFavorite() {
        val channel = selectedChannelFlow.value ?: return
        coroutineScope.launch { favoritesRepository.toggleFavorite(channel.providerId, MediaType.Channel, channel.id) }
    }

    fun onTarget(
        targetProviderId: String?,
        targetCategoryId: String?,
        targetChannelId: String?,
        targetEpgStartTimeMillis: Long?,
    ) {
        if (targetProviderId == null) return
        targetEpgStartTime = targetEpgStartTimeMillis
        selectedProviderIdFlow.value = targetProviderId
        selectedCategoryIdFlow.value = targetCategoryId
        selectedChannelIdFlow.value = targetChannelId
        coroutineScope.launch {
            targetChannelLoaded = if (targetChannelId != null) {
                mediaRepository.getChannel(targetProviderId, targetChannelId)
            } else {
                null
            }
            rebuild()
        }
    }

    private fun ensureCategorySelected() {
        val providerId = selectedProviderIdFlow.value
        if (selectedCategoryIdFlow.value == FAVORITES_CATEGORY_ID) return
        val expanded = providerId != null && providerId in expandedProviderIdsFlow.value
        if (!expanded) {
            selectedCategoryIdFlow.value = null
        } else {
            val current = selectedCategoryIdFlow.value
            if (current == null || categories.none { it.id == current }) {
                selectedCategoryIdFlow.value = categories.firstOrNull()?.id
            }
        }
    }

    private fun rebuild() {
        val providerId = selectedProviderIdFlow.value
        val categoryId = selectedCategoryIdFlow.value
        val favoriteIds = favorites.mapTo(mutableSetOf()) { it.mediaId }
        val channels = if (categoryId == FAVORITES_CATEGORY_ID) {
            favoriteChannels.sortedBy { it.name.lowercase(Locale.getDefault()) }
        } else {
            observedChannels
        }

        // Auto-select the first channel when the previous selection left the (changed) list,
        // mirroring the original LaunchedEffect(channels) guard incl. the preview reset signal.
        if (channels != lastGuardChannels) {
            lastGuardChannels = channels
            val currentChannelId = selectedChannelIdFlow.value
            if (currentChannelId == null || channels.none { it.id == currentChannelId }) {
                selectedChannelIdFlow.value = channels.firstOrNull()?.id
                channelResetSignal += 1
            }
        }

        val effectiveChannelId = selectedChannelIdFlow.value
        val selectedChannel = channels.firstOrNull { it.id == effectiveChannelId }
            ?: targetChannelLoaded?.takeIf { it.id == effectiveChannelId }
        selectedChannelFlow.value = selectedChannel

        val selectedProvider = providersRaw.firstOrNull { it.id == providerId }
        val channelProvider = selectedChannel?.let { channel -> providersRaw.firstOrNull { it.id == channel.providerId } }
            ?: selectedProvider
        val now = nowMillisState
        val currentProgram = selectedPrograms.firstOrNull { now >= it.startTime && now < it.endTime }
        val nextProgram = selectedPrograms.firstOrNull { it.startTime > now }

        _uiState.value = LiveTvUiState(
            // Deactivated playlists are not browsable (refresh/WatchNext/resume already honor isActive).
            providers = providersRaw.filter { it.isActive },
            selectedProviderId = providerId,
            categories = categories,
            selectedCategoryId = categoryId,
            channels = channels,
            selectedChannelId = effectiveChannelId,
            favoriteChannelIds = favoriteIds,
            favoriteChannelCount = favoriteChannels.size,
            selectedProvider = selectedProvider,
            selectedCategory = categories.firstOrNull { it.id == categoryId },
            selectedChannel = selectedChannel,
            channelProvider = channelProvider,
            canLoadMore = categoryId != FAVORITES_CATEGORY_ID &&
                observedChannels.size >= pageCountFlow.value * LIVE_TV_PAGE_SIZE,
            nowMillis = now,
            selectedPrograms = selectedPrograms,
            currentProgram = currentProgram,
            nextProgram = nextProgram,
            channelResetSignal = channelResetSignal,
        )
    }
}
