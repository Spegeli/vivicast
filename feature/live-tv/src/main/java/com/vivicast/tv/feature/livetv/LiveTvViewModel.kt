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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

    // The channels currently visible in the list (browse page or the active-gated favorites) — drives the
    // per-channel current-programme batch. Set from rebuild(); StateFlow dedups equal lists so it only
    // re-queries when the visible set actually changes.
    private val visibleChannelsFlow = MutableStateFlow<List<Channel>>(emptyList())
    // Wall-clock tick used for the current programme / progress / EPG "Live" badge, updated ~every minute.
    private val nowTickFlow = MutableStateFlow(nowProvider())

    private var providersRaw: List<Provider> = emptyList()
    private var categories: List<Category> = emptyList()
    private var favorites: List<Favorite> = emptyList()
    private var favoriteChannels: List<Channel> = emptyList()
    private var observedChannels: List<Channel> = emptyList()
    private var targetChannelLoaded: Channel? = null
    private var selectedPrograms: List<EpgProgram> = emptyList()
    private var currentProgramsByChannel: Map<String, EpgProgram> = emptyMap()
    private var nowMillisState: Long = 0L
    private var targetEpgStartTime: Long? = null
    private var lastGuardChannels: List<Channel>? = null
    private var channelResetSignal: Int = 0
    // initialProvidersLoaded flips on the first providers emission; initialLoadComplete is the one-way "screen
    // presentable" latch — providers loaded AND the channel query has answered for a selected provider (a real
    // list or a confirmed-empty category), OR there are no active providers at all. Drives
    // LiveTvUiState.isInitialLoading: true only during the cold DB load, never on warm category switches (the
    // latch never flips back, so a genuinely-empty category still shows "Keine Sender" instantly).
    private var initialProvidersLoaded: Boolean = false
    private var initialLoadComplete: Boolean = false

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            providerRepository.observeProviders().collect { all ->
                providersRaw = all
                initialProvidersLoaded = true
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
                    // The channel query answered for a selected provider (a real list or a confirmed-empty
                    // category) → the screen is presentable; drop the cold-load state. Guarded on a non-null
                    // provider so the initial provider==null flowOf(empty) emission doesn't latch prematurely.
                    if (selectedProviderIdFlow.value != null) initialLoadComplete = true
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

        // Channel focus/selection changes must re-emit UI state immediately (drives the preview + EPG to
        // follow the focused channel). selectedChannelIdFlow is otherwise only read inside rebuild(), so
        // without this collector the preview/EPG would only catch up on the next tick.
        coroutineScope.launch { selectedChannelIdFlow.collect { rebuild() } }

        // ~1-minute wall-clock tick: advances the selected channel's current/next + progress + the EPG "Live"
        // badge without re-querying the (wide) EPG window (S4).
        coroutineScope.launch {
            while (true) {
                delay(NOW_TICK_MILLIS)
                nowTickFlow.value = nowProvider()
            }
        }
        coroutineScope.launch { nowTickFlow.collect { rebuild() } }

        // Winner-aware CURRENT programme per visible channel (P2), grouped by provider (the favorites list
        // spans providers). Re-runs on a visible-set change or a tick; keeps the winner + isActive filter.
        coroutineScope.launch {
            combine(visibleChannelsFlow, nowTickFlow) { channels, now -> channels to now }
                .flatMapLatest { (channels, now) -> currentProgramsFlow(channels, now) }
                .collect { programs ->
                    currentProgramsByChannel = programs
                    rebuild()
                }
        }
    }

    private fun currentProgramsFlow(channels: List<Channel>, now: Long): Flow<Map<String, EpgProgram>> {
        if (channels.isEmpty()) return flowOf(emptyMap())
        val flows = channels.groupBy { it.providerId }.map { (providerId, providerChannels) ->
            epgRepository.observeCurrentProgramsForChannels(providerId, providerChannels.map { it.id }, now)
        }
        return combine(flows) { results -> results.asList().flatten().associateBy { it.channelId } }
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
        // Expand the target provider FIRST (synchronously). Otherwise the (provider, expanded) combine collector
        // runs ensureCategorySelected while the target provider is not yet in the (async, preference-backed)
        // expanded set, which nulls the target category — so the channel list never loads the target's channels
        // and the selection falls back to the default provider. The App's expansion-pref write then converges.
        expandedProviderIdsFlow.value = expandedProviderIdsFlow.value + targetProviderId
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
            // Only auto-pick the first category once categories are actually LOADED and the current one is
            // truly gone. Re-picking while `categories` is still empty (async load) would clobber a target
            // category (set by onTarget before its categories arrive) with firstOrNull() — landing on the
            // wrong channel/category on a Search / player-close return.
            if (categories.isNotEmpty() && (current == null || categories.none { it.id == current })) {
                selectedCategoryIdFlow.value = categories.firstOrNull()?.id
            }
        }
    }

    private fun rebuild() {
        val providerId = selectedProviderIdFlow.value
        val categoryId = selectedCategoryIdFlow.value
        // A — hide favorites whose provider is deactivated (consistent with the tree). `getChannel` has no
        // isActive gate, so a favorite from a disabled provider would otherwise still show/play. Gating here
        // (in rebuild, which runs on both provider and favorite changes) also drops favorites whose channel
        // row vanished, keeping the star set + count consistent.
        val activeFavoriteChannels = favoriteChannels.filter { channel ->
            providersRaw.any { it.id == channel.providerId && it.isActive }
        }
        val favoriteIds = activeFavoriteChannels.mapTo(mutableSetOf()) { it.id }
        val channels = if (categoryId == FAVORITES_CATEGORY_ID) {
            activeFavoriteChannels.sortedBy { it.name.lowercase(Locale.getDefault()) }
        } else {
            observedChannels
        }

        // Auto-select the first channel when the selection is missing/invalid — whether the list changed OR
        // the selection was just cleared (e.g. onCategorySelected/onProviderFocused set it null). Only once the
        // channel list is actually LOADED (non-empty): auto-selecting the "first" while `channels` is still
        // empty (async load) would clobber a target channel (set by onTarget before its channels arrive) to null
        // and then to the real first on arrival — landing on the wrong channel on a Search / player-close return.
        val currentChannelId = selectedChannelIdFlow.value
        val selectionValid = currentChannelId != null && channels.any { it.id == currentChannelId }
        if (channels != lastGuardChannels || !selectionValid) {
            lastGuardChannels = channels
            if (!selectionValid && channels.isNotEmpty()) {
                val next = channels.firstOrNull()?.id
                if (next != currentChannelId) {
                    selectedChannelIdFlow.value = next
                    channelResetSignal += 1
                }
            }
        }

        val effectiveChannelId = selectedChannelIdFlow.value
        val selectedChannel = channels.firstOrNull { it.id == effectiveChannelId }
            ?: targetChannelLoaded?.takeIf { it.id == effectiveChannelId }
        selectedChannelFlow.value = selectedChannel

        val selectedProvider = providersRaw.firstOrNull { it.id == providerId }
        val channelProvider = selectedChannel?.let { channel -> providersRaw.firstOrNull { it.id == channel.providerId } }
            ?: selectedProvider
        // Ticking wall-clock (S4) for current/next/progress; the EPG window itself stays anchored at
        // nowMillisState so a minute tick never re-queries the wide window.
        val now = nowTickFlow.value
        val currentProgram = selectedPrograms.firstOrNull { now >= it.startTime && now < it.endTime }
        val nextProgram = selectedPrograms.firstOrNull { it.startTime > now }

        // Feed the per-channel current-programme batch (dedups equal lists → only re-queries on a real change).
        visibleChannelsFlow.value = channels
        // B — logo config signal: changes when any provider's logoPriority (or the set) changes.
        val logoConfigSignal = providersRaw.joinToString(separator = ",") { it.id + ":" + it.logoPriority }.hashCode()

        // A user with no active providers never runs a channel query → latch on the confirmed-empty active set
        // (else the loading state would never clear for them).
        if (initialProvidersLoaded && providersRaw.none { it.isActive }) initialLoadComplete = true

        _uiState.value = LiveTvUiState(
            isInitialLoading = !initialLoadComplete,
            // Deactivated playlists are not browsable (refresh/WatchNext/resume already honor isActive).
            providers = providersRaw.filter { it.isActive },
            selectedProviderId = providerId,
            categories = categories,
            selectedCategoryId = categoryId,
            channels = channels,
            selectedChannelId = effectiveChannelId,
            favoriteChannelIds = favoriteIds,
            favoriteChannelCount = activeFavoriteChannels.size,
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
            currentProgramsByChannel = currentProgramsByChannel,
            logoConfigSignal = logoConfigSignal,
            channelResetSignal = channelResetSignal,
        )
    }
}
