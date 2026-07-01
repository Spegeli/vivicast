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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.R
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
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivicast.tv.data.epg.EpgRepository
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
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
    val viewModel: LiveTvViewModel = viewModel(
        factory = LiveTvViewModelFactory(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            epgRepository = epgRepository,
            favoritesRepository = favoritesRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Pure-visual state kept in the composable layer.
    var mode by remember { mutableStateOf(LiveColumnMode.Category) }
    var focusedArea by remember { mutableStateOf(LiveFocusArea.Provider) }
    var selectedCategoryFocusRequest by remember { mutableStateOf(0) }
    var selectedChannelFocusRequest by remember { mutableStateOf(0) }
    var epgFocusRequest by remember { mutableStateOf(0) }
    var previewStarted by remember { mutableStateOf(false) }
    var firstProviderExpansionApplied by remember { mutableStateOf(false) }

    // Feed the App-hoisted expansion into the ViewModel (drives the category default guard).
    LaunchedEffect(expandedProviderIds) { viewModel.onExpandedProvidersChanged(expandedProviderIds) }
    // Mirror the original "preview off when the channel auto-resets" side effect.
    LaunchedEffect(uiState.channelResetSignal) { previewStarted = false }

    val providers = uiState.providers
    val categories = uiState.categories
    val channels = uiState.channels
    val selectedProviderId = uiState.selectedProviderId
    val selectedCategoryId = uiState.selectedCategoryId
    val selectedChannelId = uiState.selectedChannelId
    val favoriteChannelIds = uiState.favoriteChannelIds
    val selectedProvider = uiState.selectedProvider
    val selectedCategory = uiState.selectedCategory
    val selectedChannel = uiState.selectedChannel
    val channelProvider = uiState.channelProvider
    val canLoadMoreChannels = uiState.canLoadMore
    val nowMillis = uiState.nowMillis
    val selectedPrograms = uiState.selectedPrograms
    val currentProgram = uiState.currentProgram
    val nextProgram = uiState.nextProgram

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

    LaunchedEffect(targetProviderId, targetCategoryId, targetChannelId, targetEpgProgramId) {
        val providerId = targetProviderId ?: return@LaunchedEffect
        viewModel.onTarget(targetProviderId, targetCategoryId, targetChannelId, targetEpgStartTime)
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
    LaunchedEffect(channels) {
        onPlayableChannelsChanged(channels)
    }

    fun moveBrowserChannel(key: Key): Boolean {
        if (focusedArea != LiveFocusArea.ChannelList || channels.isEmpty()) return false
        val selectedIndex = channels.indexOfFirst { it.id == selectedChannelId }.takeUnless { it < 0 } ?: 0
        val nextIndex = when (key) {
            Key.ChannelUp -> (selectedIndex - 1).coerceAtLeast(0)
            Key.ChannelDown -> (selectedIndex + 1).coerceAtMost(channels.lastIndex)
            else -> return false
        }
        viewModel.onChannelFocused(channels[nextIndex].id)
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
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
            if (mode == LiveColumnMode.Category) {
                RoomProviderCategoryColumn(
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    expandedProviderIds = expandedProviderIds,
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    favoriteSelected = selectedCategoryId == FAVORITES_CATEGORY_ID,
                    favoriteCount = uiState.favoriteChannelCount,
                    requestSelectedFocusSignal = selectedCategoryFocusRequest,
                    onGlobalFavoritesFocused = {
                        viewModel.onGlobalFavoritesSelected()
                        mode = LiveColumnMode.Category
                        focusedArea = LiveFocusArea.Provider
                        previewStarted = false
                    },
                    onProviderFocused = {
                        viewModel.onProviderFocused(it.id)
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
                        viewModel.onProviderToggled(provider.id, wasExpanded)
                        focusedArea = LiveFocusArea.Provider
                        previewStarted = false
                    },
                    onCategoryFocused = {
                        viewModel.onCategorySelected(it.id)
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
                    viewModel.onChannelFocused(it.id)
                    focusedArea = LiveFocusArea.ChannelList
                    previewStarted = false
                },
                onChannelClick = {
                    viewModel.onChannelFocused(it.id)
                    mode = LiveColumnMode.Channel
                    focusedArea = LiveFocusArea.Epg
                    epgFocusRequest += 1
                    previewStarted = true
                },
                onLoadMore = { viewModel.onLoadMore() },
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
                onToggleFavorite = { viewModel.onToggleFavorite() },
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

    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = VivicastSpacing.Space4) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            SectionTitle(stringResource(R.string.livetv_favorites))
            FocusPanel(
                selected = favoriteSelected,
                onClick = onGlobalFavoritesFocused,
                onFocused = onGlobalFavoritesFocused,
                contentPadding = VivicastSpacing.Space3,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (favoriteSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                    .testTag(providerTreeCategoryTag(FAVORITES_CATEGORY_ID)),
            ) {
                BasicText(
                    text = "${stringResource(R.string.home_livetv_favorites)} ($favoriteCount)",
                    style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                )
            }
            SectionTitle(stringResource(R.string.livetv_section_provider))
            if (providers.isEmpty()) {
                InfoPanel(stringResource(R.string.livetv_no_lists), stringResource(R.string.livetv_add_provider), badge = stringResource(R.string.common_empty_badge))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxSize()) {
                items(providers, key = { it.id }) { provider ->
                    val selected = provider.id == selectedProviderId
                    val expanded = provider.id in expandedProviderIds
                    val selectedProviderFocusTarget = selected && selectedCategoryId == null && !favoriteSelected
                    FocusPanel(
                        selected = selected,
                        onClick = { onProviderToggle(provider) },
                        onFocused = { onProviderFocused(provider) },
                        contentPadding = VivicastSpacing.Space2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                            .then(if (selectedProviderFocusTarget) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            .testTag(providerTreeProviderTag(provider.id)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                            BasicText(
                                text = "${if (expanded) "v" else ">"} ${provider.name}",
                                style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                            )
                            BodyText(provider.status.localizedLabel(), color = provider.status.color)
                        }
                    }

                    if (selected && expanded) {
                        if (categories.isEmpty()) {
                            InfoPanel(
                                stringResource(R.string.livetv_no_categories),
                                stringResource(R.string.livetv_no_categories_body),
                                badge = stringResource(R.string.common_empty_badge),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            categories.forEach { category ->
                                val selectedCategoryFocusTarget = category.id == selectedCategoryId
                                FocusPanel(
                                    selected = selectedCategoryFocusTarget,
                                    onClick = { onCategoryFocused(category) },
                                    onFocused = { onCategoryFocused(category) },
                                    contentPadding = VivicastSpacing.Space3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = VivicastSpacing.Space3)
                                        .then(if (selectedCategoryFocusTarget) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                                        .testTag(providerTreeCategoryTag(category.id)),
                                ) {
                                    BasicText(
                                        text = category.localizedDisplayName(),
                                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
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
        contentPadding = VivicastSpacing.Space4,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
            SectionTitle(stringResource(R.string.livetv_channels))
            if (channels.isEmpty()) {
                InfoPanel(stringResource(R.string.livetv_no_channels), emptyMessage, badge = stringResource(R.string.common_empty_badge))
            }
            val strNoProgramInline = stringResource(R.string.livetv_no_program_inline)
            val strEpgOnFocus = stringResource(R.string.livetv_epg_on_focus)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxSize()) {
                items(channels, key = { it.id }) { channel ->
                    val logoModel by produceState<Any?>(initialValue = null, channel.id, channel.logoUrl) {
                        value = resolveChannelLogoModel(channel)
                    }
                    val isSelected = channel.id == selectedChannelId
                    val program = if (isSelected) selectedCurrentProgram?.title ?: strNoProgramInline else strEpgOnFocus
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
                        ActionPill(stringResource(R.string.livetv_load_more), modifier = Modifier.fillMaxWidth(), onClick = onLoadMore)
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

    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = VivicastSpacing.Space4) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
            SectionTitle(stringResource(R.string.livetv_section_epg))
            when {
                channel == null -> {
                    InfoPanel(stringResource(R.string.livetv_no_epg_channel), stringResource(R.string.livetv_no_epg_channel_body), badge = stringResource(R.string.livetv_epg_badge))
                }
                showNoCurrentProgramPlaceholder -> {
                    FocusPanel(
                        selected = true,
                        onClick = { onOpenPlayer(channel) },
                        onFocused = onEpgFocused,
                        contentPadding = VivicastSpacing.Space3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(initialFocusRequester)
                            .testTag(noEpgPlaceholderTag(channel.id)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                            BasicText(
                                text = stringResource(R.string.livetv_no_program_info),
                                    style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                            )
                            BodyText(stringResource(R.string.livetv_no_epg_data, channel.name))
                            StatusBadge(stringResource(R.string.livetv_badge_no_epg))
                        }
                    }
                }
                else -> {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxSize()) {
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
                                contentPadding = VivicastSpacing.Space3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (program.id == focusProgramId) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                    .testTag(epgProgramRowTag(program.id)),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                                    BodyText("${program.startTime.hhMm()} - ${program.endTime.hhMm()}")
                                    BasicText(
                                        text = program.title,
                                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                                    )
                                    program.description?.takeIf { it.isNotBlank() }?.let {
                                        BodyText(it, color = VivicastColors.TextSecondary)
                                    }
                                    if (current || catchUpReady) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                                            if (current) StatusBadge(stringResource(R.string.livetv_badge_current))
                                            if (catchUpReady) StatusBadge(stringResource(R.string.livetv_badge_catchup))
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
    val strPreviewHint = stringResource(R.string.livetv_preview_hint)
    val strPreviewRunning = stringResource(R.string.livetv_preview_running)
    val strNoProvider = stringResource(R.string.livetv_no_provider)
    val strSelectProvider = stringResource(R.string.livetv_select_provider)
    val strLiveBadge = stringResource(R.string.livetv_live_badge)
    val strCatButton = stringResource(R.string.livetv_cat_button)
    val strDetailsButton = stringResource(R.string.livetv_details_button)
    val strProviderLabel = stringResource(R.string.livetv_provider_label)
    val strNowLabel = stringResource(R.string.livetv_now_label)
    val strNoProgramInline = stringResource(R.string.livetv_no_program_inline)
    val strProviderError = stringResource(R.string.livetv_provider_error)
    val strNextProgram = nextProgram?.let { next -> stringResource(R.string.livetv_next_program, next.startTime.hhMm(), next.title) }
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = VivicastSpacing.Space4) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            SectionTitle(stringResource(R.string.livetv_preview))
            FocusPanel(
                onClick = onStartPreview,
                onFocused = onPreviewFocused,
                contentPadding = 0.dp,
                modifier = Modifier.fillMaxWidth().height(124.dp),
            ) {
                PreviewBox(if (previewStarted) strPreviewRunning else strPreviewHint)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                ActionPill(strLiveBadge, modifier = Modifier.weight(1f), onClick = onOpenPlayer)
                ActionPill(strCatButton, modifier = Modifier.weight(1f), onClick = onShowCategoryMode)
                ActionPill(
                    if (isFavorite) "★" else "☆",
                    modifier = Modifier.weight(1f),
                    selected = isFavorite,
                    onClick = onToggleFavorite,
                )
            }
            InfoPanel(
                title = channel?.name ?: strNoProvider,
                body = channel?.let {
                    buildString {
                        append("$strProviderLabel${provider?.name.orEmpty()}")
                        append("\n$strNowLabel${currentProgram?.title ?: strNoProgramInline}")
                        strNextProgram?.let { append("\n$it") }
                    }
                } ?: strSelectProvider,
                badge = if (previewStarted) strLiveBadge else strDetailsButton,
            )
            if (
                provider?.status == ProviderStatus.ConnectionError ||
                provider?.status == ProviderStatus.InvalidCredentials ||
                provider?.status == ProviderStatus.CredentialsRequired
            ) {
                InfoPanel(strProviderError, provider.status.localizedLabel(), badge = "Error")
            }
        }
    }
}

@Composable
private fun emptyChannelMessage(provider: Provider?, category: Category?): String =
    when {
        provider == null -> stringResource(R.string.livetv_no_channels_no_provider)
        category?.id == FAVORITES_CATEGORY_ID -> stringResource(R.string.livetv_no_favorites_saved)
        category == null -> stringResource(R.string.livetv_no_channels_provider)
        else -> stringResource(R.string.livetv_no_channels_category)
    }

private fun EpgProgram?.progressAt(nowMillis: Long): Int {
    if (this == null || endTime <= startTime || !isCurrentAt(nowMillis)) return 0
    return (((nowMillis - startTime) * 100) / (endTime - startTime)).toInt().coerceIn(0, 100)
}

private fun EpgProgram.isCurrentAt(nowMillis: Long): Boolean =
    nowMillis >= startTime && nowMillis < endTime

private fun Long.hhMm(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))

@Composable
private fun Category.localizedDisplayName(): String =
    if (remoteId == "__UNCATEGORIZED__") stringResource(R.string.category_uncategorized) else name

internal fun providerTreeProviderTag(providerId: String): String = "live-tv-provider-$providerId"
internal fun providerTreeCategoryTag(categoryId: String): String = "live-tv-category-$categoryId"
internal fun channelRowTag(channelId: String): String = "live-tv-channel-$channelId"
internal fun epgProgramRowTag(programId: String): String = "live-tv-epg-program-$programId"
internal fun noEpgPlaceholderTag(channelId: String): String = "live-tv-no-epg-$channelId"

@Composable
private fun ProviderStatus.localizedLabel(): String = when (this) {
    ProviderStatus.Active -> stringResource(R.string.livetv_status_active)
    ProviderStatus.ActiveWithPartialErrors -> stringResource(R.string.livetv_status_active_partial)
    ProviderStatus.Refreshing -> stringResource(R.string.livetv_status_refreshing)
    ProviderStatus.ConnectionError -> stringResource(R.string.livetv_status_connection_error)
    ProviderStatus.InvalidCredentials -> stringResource(R.string.livetv_status_invalid_credentials)
    ProviderStatus.Expired -> stringResource(R.string.livetv_status_expired)
    ProviderStatus.Disabled -> stringResource(R.string.livetv_status_disabled)
    ProviderStatus.CredentialsRequired -> stringResource(R.string.livetv_status_credentials_required)
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
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF16263A), Color(0xFF0B1320))))
            .border(1.dp, Color(0x5538BDF8), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = VivicastTypography.TitleMedium.copy(color = VivicastColors.TextPrimary),
        )
    }
}
