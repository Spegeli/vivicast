package com.vivicast.tv.feature.livetv

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.vivicast.tv.data.media.AssetState
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoChannel
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.media.ProviderStatus as DemoProviderStatus
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

@Composable
fun LiveTvRoute(
    providerRepository: ProviderRepository? = null,
    mediaRepository: MediaRepository? = null,
    epgRepository: EpgRepository? = null,
    favoritesRepository: FavoritesRepository? = null,
    expandedProviderIds: Set<String> = emptySet(),
    onExpandedProviderIdsChanged: (Set<String>) -> Unit = {},
    resolveChannelLogoModel: suspend (Channel) -> Any? = { null },
    onOpenPlayer: (Channel) -> Unit = {},
) {
    if (providerRepository == null || mediaRepository == null || epgRepository == null || favoritesRepository == null) {
        DemoLiveTvRoute()
    } else {
        RoomLiveTvRoute(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            epgRepository = epgRepository,
            favoritesRepository = favoritesRepository,
            expandedProviderIds = expandedProviderIds,
            onExpandedProviderIdsChanged = onExpandedProviderIdsChanged,
            resolveChannelLogoModel = resolveChannelLogoModel,
            onOpenPlayer = onOpenPlayer,
        )
    }
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
) {
    val scope = rememberCoroutineScope()
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(LiveColumnMode.Category) }
    var previewStarted by remember { mutableStateOf(false) }

    LaunchedEffect(providers) {
        if (selectedProviderId == null || providers.none { it.id == selectedProviderId }) {
            selectedProviderId = providers.firstOrNull { it.isActive }?.id ?: providers.firstOrNull()?.id
        }
    }

    val categoriesFlow = remember(selectedProviderId) {
        selectedProviderId?.let { mediaRepository.observeCategories(it, CategoryType.LiveTv) } ?: flowOf(emptyList())
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val favoritesFlow = remember(selectedProviderId) {
        selectedProviderId?.let { favoritesRepository.observeFavorites(it, MediaType.Channel) } ?: flowOf(emptyList())
    }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val favoriteChannelIds = remember(favorites) { favorites.mapTo(mutableSetOf()) { it.mediaId } }
    val selectedProviderExpanded = selectedProviderId in expandedProviderIds
    val categoriesWithFavorites = remember(selectedProviderId, categories) {
        selectedProviderId?.let { listOf(favoriteCategory(it)) + categories } ?: categories
    }

    LaunchedEffect(selectedProviderId, selectedProviderExpanded, categoriesWithFavorites, favoriteChannelIds) {
        if (!selectedProviderExpanded) {
            selectedCategoryId = null
        } else if (selectedCategoryId == null || categoriesWithFavorites.none { it.id == selectedCategoryId }) {
            selectedCategoryId = when {
                favoriteChannelIds.isNotEmpty() -> FAVORITES_CATEGORY_ID
                else -> categories.firstOrNull()?.id ?: categoriesWithFavorites.firstOrNull()?.id
            }
        }
    }

    val channelsFlow = remember(selectedProviderId, selectedCategoryId) {
        selectedProviderId?.let { providerId ->
            val categoryId = selectedCategoryId?.takeUnless { it == FAVORITES_CATEGORY_ID }
            mediaRepository.observeChannels(providerId, categoryId)
        } ?: flowOf(emptyList())
    }
    val observedChannels by channelsFlow.collectAsState(initial = emptyList())
    val channels = remember(observedChannels, selectedCategoryId, favoriteChannelIds) {
        if (selectedCategoryId == FAVORITES_CATEGORY_ID) {
            observedChannels.filter { it.id in favoriteChannelIds }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        } else {
            observedChannels
        }
    }

    LaunchedEffect(channels) {
        if (selectedChannelId == null || channels.none { it.id == selectedChannelId }) {
            selectedChannelId = channels.firstOrNull()?.id
            previewStarted = false
        }
    }

    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedCategory = categoriesWithFavorites.firstOrNull { it.id == selectedCategoryId }
    val selectedChannel = channels.firstOrNull { it.id == selectedChannelId }
    val nowMillis = remember(selectedChannel?.id) { System.currentTimeMillis() }
    val programsFlow = remember(selectedProviderId, selectedChannel?.id, nowMillis) {
        val providerId = selectedProviderId
        if (providerId == null || selectedChannel == null) {
            flowOf(emptyList())
        } else {
            epgRepository.observeProgramsForChannel(
                providerId = providerId,
                channelId = selectedChannel.id,
                fromMillis = nowMillis - EPG_PAST_WINDOW_MILLIS,
                toMillis = nowMillis + EPG_FUTURE_WINDOW_MILLIS,
            )
        }
    }
    val selectedPrograms by programsFlow.collectAsState(initial = emptyList())
    val currentProgram = remember(selectedPrograms, nowMillis) { selectedPrograms.currentAt(nowMillis) }
    val nextProgram = remember(selectedPrograms, nowMillis) { selectedPrograms.nextAfter(nowMillis) }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            if (mode == LiveColumnMode.Category) {
                RoomProviderCategoryColumn(
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    expandedProviderIds = expandedProviderIds,
                    categories = categoriesWithFavorites,
                    selectedCategoryId = selectedCategoryId,
                    onProviderFocused = {
                        selectedProviderId = it.id
                        if (it.id !in expandedProviderIds) selectedCategoryId = null
                        selectedChannelId = null
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
                        previewStarted = false
                    },
                    onCategoryFocused = {
                        selectedCategoryId = it.id
                        selectedChannelId = null
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
                onChannelFocused = {
                    selectedChannelId = it.id
                    mode = LiveColumnMode.Channel
                    previewStarted = false
                },
                onChannelClick = {
                    selectedChannelId = it.id
                    mode = LiveColumnMode.Channel
                    previewStarted = true
                },
                modifier = Modifier.weight(if (mode == LiveColumnMode.Category) 0.33f else 0.32f),
            )

            if (mode == LiveColumnMode.Channel) {
                RoomEpgColumn(
                    channel = selectedChannel,
                    programs = selectedPrograms,
                    nowMillis = nowMillis,
                    modifier = Modifier.weight(0.31f),
                )
            }

            RoomPreviewColumn(
                channel = selectedChannel,
                previewStarted = previewStarted,
                provider = selectedProvider,
                currentProgram = currentProgram,
                nextProgram = nextProgram,
                isFavorite = selectedChannel?.id in favoriteChannelIds,
                onStartPreview = { if (selectedChannel != null) previewStarted = true },
                onOpenPlayer = { selectedChannel?.let(onOpenPlayer) },
                onShowCategoryMode = { mode = LiveColumnMode.Category },
                onToggleFavorite = {
                    val providerId = selectedProviderId
                    val channelId = selectedChannel?.id
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
    onProviderFocused: (Provider) -> Unit,
    onProviderToggle: (Provider) -> Unit,
    onCategoryFocused: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Provider")
            if (providers.isEmpty()) {
                InfoPanel("Keine Listen", "Lege in den Einstellungen zuerst einen Provider an.", badge = "Leer")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(providers, key = { it.id }) { provider ->
                    val selected = provider.id == selectedProviderId
                    val expanded = provider.id in expandedProviderIds
                    FocusPanel(
                        selected = selected,
                        onClick = { onProviderToggle(provider) },
                        onFocused = { onProviderFocused(provider) },
                        contentPadding = 10.dp,
                        modifier = Modifier.fillMaxWidth().height(74.dp).testTag(providerTreeProviderTag(provider.id)),
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
                                FocusPanel(
                                    selected = category.id == selectedCategoryId,
                                    onClick = { onCategoryFocused(category) },
                                    onFocused = { onCategoryFocused(category) },
                                    contentPadding = 14.dp,
                                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp).testTag(providerTreeCategoryTag(category.id)),
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
    onChannelFocused: (Channel) -> Unit,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
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
                        modifier = Modifier.testTag(channelRowTag(channel.id)),
                    )
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
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Sender-EPG")
            when {
                channel == null -> {
                    InfoPanel("Kein Sender ausgewaehlt", "Waehle einen Sender aus, um dessen EPG zu sehen.", badge = "EPG")
                }
                programs.isEmpty() -> {
                    InfoPanel("Keine Programminformationen", "${channel.name} hat aktuell keine lokalen EPG-Daten.", badge = "Ohne EPG")
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(programs, key = { it.id }) { program ->
                            val current = program.isCurrentAt(nowMillis)
                            FocusPanel(selected = current, contentPadding = 14.dp, modifier = Modifier.fillMaxWidth()) {
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
                                    if (current || program.isCatchupAvailable) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (current) StatusBadge("Aktuell")
                                            if (program.isCatchupAvailable) StatusBadge("Catch-Up")
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
                contentPadding = 0.dp,
                modifier = Modifier.fillMaxWidth().height(144.dp),
            ) {
                PreviewBox(if (previewStarted) "Vorschau läuft" else "OK startet Vorschau")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionPill("Live", modifier = Modifier.weight(1f), onClick = onOpenPlayer)
                ActionPill("Kat.", modifier = Modifier.weight(1f), onClick = onShowCategoryMode)
                ActionPill(
                    if (isFavorite) "★" else "☆",
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
                } ?: "Wähle links einen Provider und Sender aus.",
                badge = if (previewStarted) "Live" else "Details",
            )
            if (provider?.status == ProviderStatus.ConnectionError || provider?.status == ProviderStatus.InvalidCredentials) {
                InfoPanel("Provider-Fehler", provider.status.label, badge = "Error")
            }
        }
    }
}

private fun emptyChannelMessage(provider: Provider?, category: Category?): String =
    when {
        provider == null -> "Lege in den Einstellungen zuerst eine Wiedergabeliste an."
        category?.id == FAVORITES_CATEGORY_ID -> "Noch keine Live-TV-Favoriten gespeichert."
        category == null -> "Dieser Provider enthält keine importierten Live-TV-Sender."
        else -> "Diese Kategorie enthält keine importierten Live-TV-Sender."
    }

private fun favoriteCategory(providerId: String): Category =
    Category(
        id = FAVORITES_CATEGORY_ID,
        providerId = providerId,
        type = CategoryType.LiveTv,
        remoteId = FAVORITES_CATEGORY_ID,
        name = "Favoriten",
        sortOrder = Int.MIN_VALUE,
        isHidden = false,
    )

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
private const val EPG_PAST_WINDOW_MILLIS = 4L * 60L * 60L * 1000L
private const val EPG_FUTURE_WINDOW_MILLIS = 8L * 60L * 60L * 1000L
internal fun providerTreeProviderTag(providerId: String): String = "live-tv-provider-$providerId"
internal fun providerTreeCategoryTag(categoryId: String): String = "live-tv-category-$categoryId"
internal fun channelRowTag(channelId: String): String = "live-tv-channel-$channelId"

private val ProviderStatus.label: String
    get() = when (this) {
        ProviderStatus.Active -> "Aktiv"
        ProviderStatus.Refreshing -> "Aktualisierung läuft"
        ProviderStatus.ConnectionError -> "Verbindungsfehler"
        ProviderStatus.InvalidCredentials -> "Anmeldedaten ungültig"
        ProviderStatus.Expired -> "Abgelaufen"
        ProviderStatus.Disabled -> "Deaktiviert"
    }

private val ProviderStatus.color: Color
    get() = when (this) {
        ProviderStatus.Active -> VivicastColors.TextTertiary
        ProviderStatus.Refreshing -> Color(0xFF93C5FD)
        ProviderStatus.ConnectionError,
        ProviderStatus.InvalidCredentials,
        ProviderStatus.Expired,
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

@Composable
private fun DemoLiveTvRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf("Favoriten") }
    var selectedChannel by remember { mutableStateOf(DemoCatalog.channels.first()) }
    var mode by remember { mutableStateOf(LiveColumnMode.Category) }
    var previewStarted by remember { mutableStateOf(false) }
    val channels = remember(selectedCategory) {
        if (selectedCategory == "Leer") emptyList() else DemoCatalog.channels.filter { selectedCategory in it.categories }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            if (mode == LiveColumnMode.Category) {
                DemoProviderCategoryColumn(
                    selectedCategory = selectedCategory,
                    onCategoryFocused = {
                        mode = LiveColumnMode.Category
                        selectedCategory = it
                        DemoCatalog.channels.firstOrNull { channel -> it in channel.categories }?.let { channel ->
                            selectedChannel = channel
                        }
                    },
                    modifier = Modifier.weight(0.25f),
                )
            }

            DemoChannelColumn(
                channels = channels,
                selectedChannel = selectedChannel,
                emptyCategory = selectedCategory == "Leer",
                onChannelFocused = {
                    selectedChannel = it
                    mode = LiveColumnMode.Channel
                },
                onChannelClick = {
                    selectedChannel = it
                    mode = LiveColumnMode.Channel
                    previewStarted = true
                },
                modifier = Modifier.weight(if (mode == LiveColumnMode.Category) 0.33f else 0.32f),
            )

            if (mode == LiveColumnMode.Channel) {
                DemoEpgColumn(channel = selectedChannel, modifier = Modifier.weight(0.31f))
            }

            DemoPreviewColumn(
                channel = selectedChannel,
                previewStarted = previewStarted,
                providerErrorVisible = mode == LiveColumnMode.Category,
                onStartPreview = { previewStarted = true },
                onOpenPlayer = onOpenPlayer,
                onShowCategoryMode = { mode = LiveColumnMode.Category },
                modifier = Modifier.weight(0.42f),
            )
        }
    }
}

@Composable
private fun DemoProviderCategoryColumn(
    selectedCategory: String,
    onCategoryFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Provider")
            DemoCatalog.providers.forEach { provider ->
                FocusPanel(
                    selected = provider.status == DemoProviderStatus.Active,
                    onClick = {},
                    contentPadding = 10.dp,
                    modifier = Modifier.fillMaxWidth().height(74.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(
                            text = provider.name,
                            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        )
                        BodyText(
                            provider.statusText,
                            color = if (provider.status == DemoProviderStatus.Error) Color(0xFFFFB4A8) else VivicastColors.TextTertiary,
                        )
                    }
                }
            }

            SectionTitle("Kategorien")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(DemoCatalog.categories) { category ->
                    FocusPanel(
                        selected = category == selectedCategory,
                        onClick = { onCategoryFocused(category) },
                        onFocused = { onCategoryFocused(category) },
                        contentPadding = 14.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            text = category,
                            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoChannelColumn(
    channels: List<DemoChannel>,
    selectedChannel: DemoChannel,
    emptyCategory: Boolean,
    onChannelFocused: (DemoChannel) -> Unit,
    onChannelClick: (DemoChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Senderliste")
            if (channels.isEmpty() && emptyCategory) {
                InfoPanel("Keine Sender", "Diese Kategorie enthält keine Sender.", badge = "Leer")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(channels) { channel ->
                    VivicastChannelCard(
                        channelName = channel.name,
                        program = channel.program,
                        logoText = channel.name,
                        logoMissing = channel.logoState == AssetState.Missing,
                        selected = channel == selectedChannel,
                        onFocused = { onChannelFocused(channel) },
                        onClick = { onChannelClick(channel) },
                        progressPercent = if (channel.hasEpg) 55 else 0,
                        favorite = channel.favorite,
                        catchUp = channel.catchUp,
                        logoResId = channel.logoResId,
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoEpgColumn(channel: DemoChannel, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Sender-EPG")
            if (!channel.hasEpg) {
                InfoPanel("Keine Programminformationen", "${channel.name} zeigt derzeit kein EPG.", badge = "Ohne EPG")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(channel.epg) { item ->
                        FocusPanel(selected = item.current, contentPadding = 14.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BodyText("${item.start} - ${item.end}")
                                BasicText(
                                    text = item.title,
                                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                                )
                                if (item.current || item.catchUp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (item.current) StatusBadge("Aktuell")
                                        if (item.catchUp) StatusBadge("Catch-Up")
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
private fun DemoPreviewColumn(
    channel: DemoChannel,
    previewStarted: Boolean,
    providerErrorVisible: Boolean,
    onStartPreview: () -> Unit,
    onOpenPlayer: () -> Unit,
    onShowCategoryMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Vorschau")
            FocusPanel(
                onClick = onStartPreview,
                contentPadding = 0.dp,
                modifier = Modifier.fillMaxWidth().height(144.dp),
            ) {
                PreviewBox(if (previewStarted) "Vorschau läuft" else "OK startet Vorschau")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionPill("Ansehen", modifier = Modifier.width(160.dp), onClick = onOpenPlayer)
                ActionPill("Kategorien", modifier = Modifier.width(190.dp), onClick = onShowCategoryMode)
            }
            InfoPanel(
                title = channel.name,
                body = channel.description.ifBlank { "Keine Beschreibung vorhanden." },
                badge = if (previewStarted) "Live" else "Details",
            )
            if (!channel.hasEpg) {
                InfoPanel("Keine EPG-Daten", "Für diesen Sender sind keine Programminformationen vorhanden.", badge = "Ohne EPG")
            }
            if (providerErrorVisible) {
                InfoPanel("Provider-Fehler", "Provider B: Anmeldung fehlgeschlagen.", badge = "Error")
            }
        }
    }
}
