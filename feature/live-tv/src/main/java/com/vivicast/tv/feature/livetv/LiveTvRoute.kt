package com.vivicast.tv.feature.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastChannelCard
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.data.epg.EpgRepository
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LiveColumnMode { Category, Channel }
private enum class LiveFocusArea { Provider, ChannelList, Epg, Preview }

@Composable
fun LiveTvRoute(
    providerRepository: ProviderRepository,
    mediaRepository: MediaRepository,
    epgRepository: EpgRepository,
    favoritesRepository: FavoritesRepository,
    expandedProviderIds: Set<String> = emptySet(),
    onExpandedProviderIdsChanged: (Set<String>) -> Unit = {},
    resolveChannelLogoModel: suspend (Channel) -> Any? = { null },
    onOpenPlayer: (Channel) -> Unit = {},
    onPlayableChannelsChanged: (List<Channel>) -> Unit = {},
    onOpenCatchUp: (Channel, EpgProgram) -> Unit = { _, _ -> },
    targetProviderId: String? = null,
    targetCategoryId: String? = null,
    targetChannelId: String? = null,
    targetEpgProgramId: String? = null,
    targetEpgStartTime: Long? = null,
    onTargetConsumed: () -> Unit = {},
) {
    RoomLiveTvRoute(
        providerRepository = providerRepository,
        mediaRepository = mediaRepository,
        epgRepository = epgRepository,
        favoritesRepository = favoritesRepository,
        expandedProviderIds = expandedProviderIds,
        onExpandedProviderIdsChanged = onExpandedProviderIdsChanged,
        resolveChannelLogoModel = resolveChannelLogoModel,
        onOpenPlayer = onOpenPlayer,
        onPlayableChannelsChanged = onPlayableChannelsChanged,
        onOpenCatchUp = onOpenCatchUp,
        targetProviderId = targetProviderId,
        targetCategoryId = targetCategoryId,
        targetChannelId = targetChannelId,
        targetEpgProgramId = targetEpgProgramId,
        targetEpgStartTime = targetEpgStartTime,
        onTargetConsumed = onTargetConsumed,
    )
}

@Composable
private fun RoomLiveTvRoute(
    providerRepository: ProviderRepository,
    mediaRepository: MediaRepository,
    epgRepository: EpgRepository,
    favoritesRepository: FavoritesRepository,
    expandedProviderIds: Set<String>,
    onExpandedProviderIdsChanged: (Set<String>) -> Unit,
    resolveChannelLogoModel: suspend (Channel) -> Any?,
    onOpenPlayer: (Channel) -> Unit,
    onPlayableChannelsChanged: (List<Channel>) -> Unit,
    onOpenCatchUp: (Channel, EpgProgram) -> Unit,
    targetProviderId: String?,
    targetCategoryId: String?,
    targetChannelId: String?,
    targetEpgProgramId: String?,
    targetEpgStartTime: Long?,
    onTargetConsumed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(LiveColumnMode.Category) }
    var focusedArea by remember { mutableStateOf(LiveFocusArea.Provider) }
    var selectedCategoryFocusRequest by remember { mutableStateOf(0) }
    var selectedChannelFocusRequest by remember { mutableStateOf(0) }
    var epgFocusRequest by remember { mutableStateOf(0) }
    var previewStarted by remember { mutableStateOf(false) }
    var firstProviderExpansionApplied by remember { mutableStateOf(false) }
    var channelPageCount by remember { mutableStateOf(1) }

    BackHandler(enabled = focusedArea != LiveFocusArea.Provider) {
        when (focusedArea) {
            LiveFocusArea.Epg,
            LiveFocusArea.Preview,
            -> {
                focusedArea = LiveFocusArea.ChannelList
                selectedChannelFocusRequest += 1
            }
            LiveFocusArea.ChannelList -> {
                mode = LiveColumnMode.Category
                previewStarted = false
                focusedArea = LiveFocusArea.Provider
                selectedCategoryFocusRequest += 1
            }
            LiveFocusArea.Provider -> Unit
        }
    }

    LaunchedEffect(providers) {
        if (selectedProviderId == null || providers.none { it.id == selectedProviderId }) {
            selectedProviderId = providers.firstOrNull { it.isActive }?.id ?: providers.firstOrNull()?.id
        }
    }
    LaunchedEffect(targetProviderId, targetCategoryId, targetChannelId, targetEpgProgramId) {
        val providerId = targetProviderId ?: return@LaunchedEffect
        selectedProviderId = providerId
        selectedCategoryId = targetCategoryId
        selectedChannelId = targetChannelId
        if (providerId !in expandedProviderIds) {
            onExpandedProviderIdsChanged(expandedProviderIds + providerId)
        }
        if (targetChannelId != null) {
            mode = LiveColumnMode.Channel
            focusedArea = LiveFocusArea.Epg
            epgFocusRequest += 1
            previewStarted = true
        }
        if (targetEpgProgramId == null) onTargetConsumed()
    }
    LaunchedEffect(providers) {
        val firstProvider = providers.firstOrNull { it.isActive } ?: providers.firstOrNull()
        if (!firstProviderExpansionApplied && firstProvider != null && expandedProviderIds.isEmpty()) {
            onExpandedProviderIdsChanged(setOf(firstProvider.id))
            firstProviderExpansionApplied = true
        }
    }

    val categoriesFlow = remember(selectedProviderId) {
        selectedProviderId?.let { mediaRepository.observeCategories(it, CategoryType.LiveTv) } ?: flowOf(emptyList())
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val favoritesFlow = remember(favoritesRepository) { favoritesRepository.observeFavorites(MediaType.Channel) }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val favoriteChannelIds = remember(favorites) { favorites.mapTo(mutableSetOf()) { it.mediaId } }
    val favoriteChannels by produceState(initialValue = emptyList(), mediaRepository, favorites) {
        value = favorites.mapNotNull { favorite ->
            mediaRepository.getChannel(favorite.providerId, favorite.mediaId)
        }
    }
    val selectedProviderExpanded = selectedProviderId in expandedProviderIds

    LaunchedEffect(selectedProviderId, selectedCategoryId) {
        channelPageCount = 1
    }

    LaunchedEffect(selectedProviderId, selectedProviderExpanded, categories, selectedCategoryId) {
        if (selectedCategoryId == FAVORITES_CATEGORY_ID) return@LaunchedEffect
        if (!selectedProviderExpanded) {
            selectedCategoryId = null
        } else if (selectedCategoryId == null || categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = categories.firstOrNull()?.id
        }
    }

    val channelsFlow = remember(selectedProviderId, selectedCategoryId, channelPageCount) {
        if (selectedCategoryId == FAVORITES_CATEGORY_ID) {
            flowOf(emptyList())
        } else {
            selectedProviderId?.let { providerId ->
            val categoryId = selectedCategoryId?.takeUnless { it == FAVORITES_CATEGORY_ID }
            mediaRepository.observeChannelsPage(
                providerId = providerId,
                categoryId = categoryId,
                limit = channelPageCount * LIVE_TV_PAGE_SIZE,
            )
            } ?: flowOf(emptyList())
        }
    }
    val observedChannels by channelsFlow.collectAsState(initial = emptyList())
    val channels = remember(observedChannels, selectedCategoryId, favoriteChannels) {
        if (selectedCategoryId == FAVORITES_CATEGORY_ID) {
            favoriteChannels.sortedBy { it.name.lowercase(Locale.getDefault()) }
        } else {
            observedChannels
        }
    }

    LaunchedEffect(channels) {
        onPlayableChannelsChanged(channels)
    }

    LaunchedEffect(channels) {
        if (selectedChannelId == null || channels.none { it.id == selectedChannelId }) {
            selectedChannelId = channels.firstOrNull()?.id
            previewStarted = false
        }
    }

    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }
    val targetChannel by produceState<Channel?>(initialValue = null, mediaRepository, targetProviderId, targetChannelId) {
        value = if (targetProviderId != null && targetChannelId != null) {
            mediaRepository.getChannel(targetProviderId, targetChannelId)
        } else {
            null
        }
    }
    val selectedChannel = channels.firstOrNull { it.id == selectedChannelId } ?: targetChannel?.takeIf { it.id == selectedChannelId }
    val channelProvider = selectedChannel?.let { channel -> providers.firstOrNull { it.id == channel.providerId } } ?: selectedProvider
    val canLoadMoreChannels = selectedCategoryId != FAVORITES_CATEGORY_ID &&
        observedChannels.size >= channelPageCount * LIVE_TV_PAGE_SIZE
    val nowMillis = remember(selectedChannel?.id, targetEpgStartTime) { targetEpgStartTime ?: System.currentTimeMillis() }
    val programsFlow = remember(selectedProviderId, selectedChannel?.id, nowMillis) {
        if (selectedChannel == null) {
            flowOf(emptyList())
        } else {
            epgRepository.observeProgramsForChannel(
                providerId = selectedChannel.providerId,
                channelId = selectedChannel.id,
                fromMillis = nowMillis - EPG_PAST_WINDOW_MILLIS,
                toMillis = nowMillis + EPG_FUTURE_WINDOW_MILLIS,
            )
        }
    }
    val selectedPrograms by programsFlow.collectAsState(initial = emptyList())
    val currentProgram = remember(selectedPrograms, nowMillis) { selectedPrograms.currentAt(nowMillis) }
    val nextProgram = remember(selectedPrograms, nowMillis) { selectedPrograms.nextAfter(nowMillis) }

    fun moveBrowserChannel(key: Key): Boolean {
        if (focusedArea != LiveFocusArea.ChannelList || channels.isEmpty()) return false
        val selectedIndex = channels.indexOfFirst { it.id == selectedChannelId }.takeUnless { it < 0 } ?: 0
        val nextIndex = when (key) {
            Key.ChannelUp -> (selectedIndex - 1).coerceAtLeast(0)
            Key.ChannelDown -> (selectedIndex + 1).coerceAtMost(channels.lastIndex)
            else -> return false
        }
        selectedChannelId = channels[nextIndex].id
        previewStarted = false
        selectedChannelFocusRequest += 1
        return true
    }

    VivicastScreen(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && moveBrowserChannel(event.key)
            },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            if (mode == LiveColumnMode.Category) {
                RoomProviderCategoryColumn(
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    expandedProviderIds = expandedProviderIds,
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    favoriteSelected = selectedCategoryId == FAVORITES_CATEGORY_ID,
                    favoriteCount = favoriteChannels.size,
                    requestSelectedFocusSignal = selectedCategoryFocusRequest,
                    onGlobalFavoritesFocused = {
                        selectedCategoryId = FAVORITES_CATEGORY_ID
                        selectedChannelId = null
                        mode = LiveColumnMode.Category
                        focusedArea = LiveFocusArea.Provider
                        previewStarted = false
                    },
                    onProviderFocused = {
                        selectedProviderId = it.id
                        if (it.id !in expandedProviderIds) selectedCategoryId = null
                        selectedChannelId = null
                        focusedArea = LiveFocusArea.Provider
                        previewStarted = false
                    },
                    onProviderToggle = { provider ->
                        val wasExpanded = provider.id in expandedProviderIds
                        val nextExpandedProviderIds = if (wasExpanded) {
                            expandedProviderIds - provider.id
                        } else {
                            expandedProviderIds + provider.id
                        }
                        onExpandedProviderIdsChanged(nextExpandedProviderIds)
                        selectedProviderId = provider.id
                        if (wasExpanded) selectedCategoryId = null
                        selectedChannelId = null
                        focusedArea = LiveFocusArea.Provider
                        previewStarted = false
                    },
                    onCategoryFocused = {
                        selectedCategoryId = it.id
                        selectedChannelId = null
                        focusedArea = LiveFocusArea.Provider
                        previewStarted = false
                    },
                    modifier = Modifier.weight(0.25f),
                )
            }

            RoomChannelColumn(
                channels = channels,
                selectedChannelId = selectedChannelId,
                emptyMessage = emptyChannelMessage(selectedProvider, selectedCategory),
                resolveChannelLogoModel = resolveChannelLogoModel,
                nowMillis = nowMillis,
                selectedCurrentProgram = currentProgram,
                favoriteChannelIds = favoriteChannelIds,
                canLoadMore = canLoadMoreChannels,
                requestSelectedFocusSignal = selectedChannelFocusRequest,
                onChannelFocused = {
                    selectedChannelId = it.id
                    focusedArea = LiveFocusArea.ChannelList
                    previewStarted = false
                },
                onChannelClick = {
                    selectedChannelId = it.id
                    mode = LiveColumnMode.Channel
                    focusedArea = LiveFocusArea.Epg
                    epgFocusRequest += 1
                    previewStarted = true
                },
                onLoadMore = { channelPageCount += 1 },
                modifier = Modifier.weight(if (mode == LiveColumnMode.Category) 0.33f else 0.32f),
            )

            if (mode == LiveColumnMode.Channel) {
                RoomEpgColumn(
                    channel = selectedChannel,
                    programs = selectedPrograms,
                    nowMillis = nowMillis,
                    requestInitialFocusSignal = epgFocusRequest,
                    targetProgramId = targetEpgProgramId,
                    onTargetConsumed = onTargetConsumed,
                    onEpgFocused = { focusedArea = LiveFocusArea.Epg },
                    onOpenPlayer = onOpenPlayer,
                    onOpenCatchUp = onOpenCatchUp,
                    modifier = Modifier.weight(0.31f),
                )
            }

            RoomPreviewColumn(
                channel = selectedChannel,
                previewStarted = previewStarted,
                provider = channelProvider,
                currentProgram = currentProgram,
                nextProgram = nextProgram,
                isFavorite = selectedChannel?.id in favoriteChannelIds,
                onPreviewFocused = { focusedArea = LiveFocusArea.Preview },
                onStartPreview = {
                    if (selectedChannel != null) {
                        focusedArea = LiveFocusArea.Preview
                        previewStarted = true
                    }
                },
                onOpenPlayer = { selectedChannel?.let(onOpenPlayer) },
                onShowCategoryMode = { mode = LiveColumnMode.Category },
                onToggleFavorite = {
                    val channelId = selectedChannel?.id
                    val providerId = selectedChannel?.providerId
                    if (providerId != null && channelId != null) {
                        scope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Channel, channelId) }
                    }
                },
                modifier = Modifier.weight(0.42f),
            )
        }
    }
}
@Composable
private fun RoomProviderCategoryColumn(
    providers: List<Provider>,
    selectedProviderId: String?,
    expandedProviderIds: Set<String>,
    categories: List<Category>,
    selectedCategoryId: String?,
    favoriteSelected: Boolean,
    favoriteCount: Int,
    requestSelectedFocusSignal: Int,
    onGlobalFavoritesFocused: () -> Unit,
    onProviderFocused: (Provider) -> Unit,
    onProviderToggle: (Provider) -> Unit,
    onCategoryFocused: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedFocusRequester = remember { FocusRequester() }

    LaunchedEffect(requestSelectedFocusSignal, favoriteSelected, selectedProviderId, selectedCategoryId, categories, expandedProviderIds) {
        if (requestSelectedFocusSignal > 0) selectedFocusRequester.requestFocus()
    }

    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Favoriten")
            FocusPanel(
                selected = favoriteSelected,
                onClick = onGlobalFavoritesFocused,
                onFocused = onGlobalFavoritesFocused,
                contentPadding = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (favoriteSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                    .testTag(providerTreeCategoryTag(FAVORITES_CATEGORY_ID)),
            ) {
                BasicText(
                    text = "Live-TV Favoriten ($favoriteCount)",
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            SectionTitle("Provider")
            if (providers.isEmpty()) {
                InfoPanel("Keine Listen", "Lege in den Einstellungen zuerst einen Provider an.", badge = "Leer")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(providers, key = { it.id }) { provider ->
                    val selected = provider.id == selectedProviderId
                    val expanded = provider.id in expandedProviderIds
                    val selectedProviderFocusTarget = selected && selectedCategoryId == null && !favoriteSelected
                    FocusPanel(
                        selected = selected,
                        onClick = { onProviderToggle(provider) },
                        onFocused = { onProviderFocused(provider) },
                        contentPadding = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .then(if (selectedProviderFocusTarget) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            .testTag(providerTreeProviderTag(provider.id)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText(
                                text = "${if (expanded) "v" else ">"} ${provider.name}",
                                style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                            )
                            BodyText(provider.status.label, color = provider.status.color)
                        }
                    }

                    if (selected && expanded) {
                        if (categories.isEmpty()) {
                            InfoPanel(
                                "Keine Kategorien",
                                "Dieser Provider enthaelt keine importierten Live-TV-Kategorien.",
                                badge = "Leer",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            categories.forEach { category ->
                                val selectedCategoryFocusTarget = category.id == selectedCategoryId
                                FocusPanel(
                                    selected = selectedCategoryFocusTarget,
                                    onClick = { onCategoryFocused(category) },
                                    onFocused = { onCategoryFocused(category) },
                                    contentPadding = 14.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp)
                                        .then(if (selectedCategoryFocusTarget) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                                        .testTag(providerTreeCategoryTag(category.id)),
                                ) {
                                    BasicText(
                                        text = category.displayName,
                                        style = TextStyle(
                                            color = VivicastColors.TextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
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

@Composable
private fun RoomChannelColumn(
    channels: List<Channel>,
    selectedChannelId: String?,
    emptyMessage: String,
    resolveChannelLogoModel: suspend (Channel) -> Any?,
    nowMillis: Long,
    selectedCurrentProgram: EpgProgram?,
    favoriteChannelIds: Set<String>,
    canLoadMore: Boolean,
    requestSelectedFocusSignal: Int,
    onChannelFocused: (Channel) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    var channelKeyFocusRequest by remember { mutableStateOf(0) }

    LaunchedEffect(requestSelectedFocusSignal, channelKeyFocusRequest, selectedChannelId, channels) {
        if ((requestSelectedFocusSignal > 0 || channelKeyFocusRequest > 0) && selectedChannelId != null && channels.any { it.id == selectedChannelId }) {
            selectedFocusRequester.requestFocus()
        }
    }

    fun moveChannelFocus(key: Key): Boolean {
        if (channels.isEmpty()) return false
        val selectedIndex = channels.indexOfFirst { it.id == selectedChannelId }.takeUnless { it < 0 } ?: 0
        val nextIndex = when (key) {
            Key.ChannelUp -> (selectedIndex - 1).coerceAtLeast(0)
            Key.ChannelDown -> (selectedIndex + 1).coerceAtMost(channels.lastIndex)
            else -> return false
        }
        if (nextIndex != selectedIndex) {
            onChannelFocused(channels[nextIndex])
            channelKeyFocusRequest += 1
        }
        return true
    }

    GlassPanel(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && moveChannelFocus(event.key)
            },
        contentPadding = 18.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Senderliste")
            if (channels.isEmpty()) {
                InfoPanel("Keine Sender", emptyMessage, badge = "Leer")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(channels, key = { it.id }) { channel ->
                    val logoModel by produceState<Any?>(initialValue = null, channel.id, channel.logoUrl) {
                        value = resolveChannelLogoModel(channel)
                    }
                    val isSelected = channel.id == selectedChannelId
                    val program = if (isSelected) selectedCurrentProgram?.title ?: "Keine Programminformationen" else "EPG bei Fokus"
                    VivicastChannelCard(
                        channelName = channel.name,
                        program = program,
                        logoText = channel.name,
                        logoMissing = channel.logoUrl.isNullOrBlank() && logoModel == null,
                        selected = isSelected,
                        onFocused = { onChannelFocused(channel) },
                        onClick = { onChannelClick(channel) },
                        progressPercent = if (isSelected) selectedCurrentProgram.progressAt(nowMillis) else 0,
                        favorite = channel.id in favoriteChannelIds,
                        catchUp = channel.isCatchupAvailable && isSelected && selectedCurrentProgram != null,
                        logoModel = logoModel,
                        modifier = Modifier
                            .then(if (isSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            .onPreviewKeyEvent { event ->
                                event.type == KeyEventType.KeyDown && moveChannelFocus(event.key)
                            }
                            .onKeyEvent { event ->
                                event.type == KeyEventType.KeyDown && moveChannelFocus(event.key)
                            }
                            .testTag(channelRowTag(channel.id)),
                    )
                }
                if (canLoadMore) {
                    item(key = "load-more-channels") {
                        ActionPill("Mehr laden", modifier = Modifier.fillMaxWidth(), onClick = onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomEpgColumn(
    channel: Channel?,
    programs: List<EpgProgram>,
    nowMillis: Long,
    requestInitialFocusSignal: Int,
    targetProgramId: String?,
    onTargetConsumed: () -> Unit,
    onEpgFocused: () -> Unit,
    onOpenPlayer: (Channel) -> Unit,
    onOpenCatchUp: (Channel, EpgProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val currentProgramId = remember(programs, nowMillis) { programs.firstOrNull { it.isCurrentAt(nowMillis) }?.id }
    val focusProgramId = targetProgramId ?: currentProgramId
    val showNoCurrentProgramPlaceholder = channel != null && currentProgramId == null

    LaunchedEffect(requestInitialFocusSignal, channel?.id, focusProgramId, programs) {
        val targetIndex = programs.indexOfFirst { it.id == focusProgramId }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
        if (requestInitialFocusSignal > 0 && channel != null) initialFocusRequester.requestFocus()
        if (targetProgramId != null && targetIndex >= 0) onTargetConsumed()
    }

    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Sender-EPG")
            when {
                channel == null -> {
                    InfoPanel("Kein Sender ausgewaehlt", "Waehle einen Sender aus, um dessen EPG zu sehen.", badge = "EPG")
                }
                showNoCurrentProgramPlaceholder -> {
                    FocusPanel(
                        selected = true,
                        onClick = { onOpenPlayer(channel) },
                        onFocused = onEpgFocused,
                        contentPadding = 14.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(initialFocusRequester)
                            .testTag(noEpgPlaceholderTag(channel.id)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicText(
                                text = "Keine Programminformationen verfÃ¼gbar",
                                style = TextStyle(
                                    color = VivicastColors.TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            BodyText("${channel.name} hat aktuell keine lokalen EPG-Daten.")
                            StatusBadge("Ohne EPG")
                        }
                    }
                }
                else -> {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(programs, key = { it.id }) { program ->
                            val current = program.isCurrentAt(nowMillis)
                            val target = program.id == targetProgramId
                            val catchUpReady = program.isCatchupAvailable && program.endTime <= nowMillis
                            FocusPanel(
                                selected = current || target,
                                onClick = {
                                    if (current) {
                                        onOpenPlayer(channel)
                                    } else if (catchUpReady) {
                                        onOpenCatchUp(channel, program)
                                    }
                                },
                                onFocused = onEpgFocused,
                                contentPadding = 14.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (program.id == focusProgramId) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                    .testTag(epgProgramRowTag(program.id)),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BodyText("${program.startTime.hhMm()} - ${program.endTime.hhMm()}")
                                    BasicText(
                                        text = program.title,
                                        style = TextStyle(
                                            color = VivicastColors.TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    )
                                    program.description?.takeIf { it.isNotBlank() }?.let {
                                        BodyText(it, color = VivicastColors.TextSecondary)
                                    }
                                    if (current || catchUpReady) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (current) StatusBadge("Aktuell")
                                            if (catchUpReady) StatusBadge("Catch-Up")
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
}

@Composable
private fun RoomPreviewColumn(
    channel: Channel?,
    previewStarted: Boolean,
    provider: Provider?,
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    isFavorite: Boolean,
    onPreviewFocused: () -> Unit,
    onStartPreview: () -> Unit,
    onOpenPlayer: () -> Unit,
    onShowCategoryMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Vorschau")
            FocusPanel(
                onClick = onStartPreview,
                onFocused = onPreviewFocused,
                contentPadding = 0.dp,
                modifier = Modifier.fillMaxWidth().height(144.dp),
            ) {
                PreviewBox(if (previewStarted) "Vorschau lÃ¤uft" else "OK startet Vorschau")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionPill("Live", modifier = Modifier.weight(1f), onClick = onOpenPlayer)
                ActionPill("Kat.", modifier = Modifier.weight(1f), onClick = onShowCategoryMode)
                ActionPill(
                    if (isFavorite) "â˜…" else "â˜†",
                    modifier = Modifier.weight(1f),
                    selected = isFavorite,
                    onClick = onToggleFavorite,
                )
            }
            InfoPanel(
                title = channel?.name ?: "Kein Sender",
                body = channel?.let {
                    buildString {
                        append("Provider: ${provider?.name.orEmpty()}")
                        append("\nJetzt: ${currentProgram?.title ?: "Keine Programminformationen"}")
                        nextProgram?.let { next -> append("\nDanach: ${next.startTime.hhMm()} ${next.title}") }
                    }
                } ?: "WÃ¤hle links einen Provider und Sender aus.",
                badge = if (previewStarted) "Live" else "Details",
            )
            if (
                provider?.status == ProviderStatus.ConnectionError ||
                provider?.status == ProviderStatus.InvalidCredentials ||
                provider?.status == ProviderStatus.CredentialsRequired
            ) {
                InfoPanel("Provider-Fehler", provider.status.label, badge = "Error")
            }
        }
    }
}

private fun emptyChannelMessage(provider: Provider?, category: Category?): String =
    when {
        provider == null -> "Lege in den Einstellungen zuerst eine Wiedergabeliste an."
        category?.id == FAVORITES_CATEGORY_ID -> "Noch keine Live-TV-Favoriten gespeichert."
        category == null -> "Dieser Provider enthÃ¤lt keine importierten Live-TV-Sender."
        else -> "Diese Kategorie enthÃ¤lt keine importierten Live-TV-Sender."
    }

private fun List<EpgProgram>.currentAt(nowMillis: Long): EpgProgram? =
    firstOrNull { it.isCurrentAt(nowMillis) }

private fun List<EpgProgram>.nextAfter(nowMillis: Long): EpgProgram? =
    firstOrNull { it.startTime > nowMillis }

private fun EpgProgram?.progressAt(nowMillis: Long): Int {
    if (this == null || endTime <= startTime || !isCurrentAt(nowMillis)) return 0
    return (((nowMillis - startTime) * 100) / (endTime - startTime)).toInt().coerceIn(0, 100)
}

private fun EpgProgram.isCurrentAt(nowMillis: Long): Boolean =
    nowMillis >= startTime && nowMillis < endTime

private fun Long.hhMm(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))

private val Category.displayName: String
    get() = if (remoteId == "__UNCATEGORIZED__") "Nicht kategorisiert" else name

private const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
private const val LIVE_TV_PAGE_SIZE = 80
private const val EPG_PAST_WINDOW_MILLIS = 4L * 60L * 60L * 1000L
private const val EPG_FUTURE_WINDOW_MILLIS = 8L * 60L * 60L * 1000L
internal fun providerTreeProviderTag(providerId: String): String = "live-tv-provider-$providerId"
internal fun providerTreeCategoryTag(categoryId: String): String = "live-tv-category-$categoryId"
internal fun channelRowTag(channelId: String): String = "live-tv-channel-$channelId"
internal fun epgProgramRowTag(programId: String): String = "live-tv-epg-program-$programId"
internal fun noEpgPlaceholderTag(channelId: String): String = "live-tv-no-epg-$channelId"

private val ProviderStatus.label: String
    get() = when (this) {
        ProviderStatus.Active -> "Aktiv"
        ProviderStatus.ActiveWithPartialErrors -> "Aktiv mit Teilfehlern"
        ProviderStatus.Refreshing -> "Aktualisierung lÃ¤uft"
        ProviderStatus.ConnectionError -> "Verbindungsfehler"
        ProviderStatus.InvalidCredentials -> "Anmeldedaten ungÃ¼ltig"
        ProviderStatus.Expired -> "Abgelaufen"
        ProviderStatus.Disabled -> "Deaktiviert"
        ProviderStatus.CredentialsRequired -> "Zugangsdaten erforderlich"
    }

private val ProviderStatus.color: Color
    get() = when (this) {
        ProviderStatus.Active -> VivicastColors.TextTertiary
        ProviderStatus.ActiveWithPartialErrors -> Color(0xFFFFD166)
        ProviderStatus.Refreshing -> Color(0xFF93C5FD)
        ProviderStatus.ConnectionError,
        ProviderStatus.InvalidCredentials,
        ProviderStatus.Expired,
        ProviderStatus.CredentialsRequired,
        -> Color(0xFFFFB4A8)
        ProviderStatus.Disabled -> Color(0xFF9CA3AF)
    }

@Composable
private fun PreviewBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF16263A), Color(0xFF0B1320))))
            .border(1.dp, Color(0x5538BDF8), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
