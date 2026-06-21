package com.vivicast.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastBorders
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastShapes
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourcePriorityDirection
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoSetting
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.domain.model.EpgSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class GeneralSettingsState(
    val launchOnBoot: Boolean = false,
    val backgroundRefreshEnabled: Boolean = true,
    val rememberSorting: Boolean = true,
)

data class CacheSettingsState(
    val maxCacheSizeMb: Int = 500,
    val totalSizeBytes: Long = 0L,
    val fileCount: Int = 0,
)

@Composable
fun SettingsRoute(
    providerRepository: ProviderRepository,
    epgSourceRepository: EpgSourceRepository,
    generalSettingsState: GeneralSettingsState,
    cacheSettingsState: CacheSettingsState,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onRememberSortingChanged: (Boolean) -> Unit,
    onRunGlobalRefresh: () -> Unit,
    onCacheSizeChanged: (Int) -> Unit,
    onRunLogoRefresh: () -> Unit,
    onRunCacheCleanup: () -> Unit,
    onClearCache: () -> Unit,
    onReloadCacheStats: () -> Unit,
) {
    var selectedSection by remember { mutableStateOf("Allgemein") }
    var showConfirm by remember { mutableStateOf(false) }
    val detailFocusRequester = remember { FocusRequester() }
    val settingsSections = remember { DemoCatalog.settingsSections }
    val maintenanceSection = remember { settingsSections.last() }
    val sectionsWithDetailFocus = remember(maintenanceSection) {
        setOf("Allgemein", "Wiedergabelisten", "EPG", "Optik", maintenanceSection)
    }

    LaunchedEffect(Unit) {
        onReloadCacheStats()
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
            modifier = Modifier.fillMaxSize(),
        ) {
            GlassPanel(
                modifier = Modifier.weight(0.30f).fillMaxSize(),
                contentPadding = VivicastSpacing.Space5,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                    SectionTitle("Einstellungen")
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
                    ) {
                        items(settingsSections) { section ->
                            FocusPanel(
                                selected = section == selectedSection,
                                onClick = { selectedSection = section },
                                onFocused = { selectedSection = section },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(VivicastCardSizes.SettingsNavItemHeight)
                                    .then(
                                        if (section in sectionsWithDetailFocus) {
                                            Modifier.focusProperties { right = detailFocusRequester }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentPadding = VivicastSpacing.Space4,
                            ) {
                                BasicText(
                                    text = section,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = VivicastTypography.LabelLarge.copy(color = VivicastColors.TextPrimary),
                                )
                            }
                        }
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.weight(0.70f).fillMaxSize(),
                contentPadding = VivicastSpacing.Space6,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                    SectionTitle(selectedSection)
                    when (selectedSection) {
                        "Allgemein" -> GeneralSettingsPanel(
                            state = generalSettingsState,
                            onBackgroundRefreshChanged = onBackgroundRefreshChanged,
                            onRememberSortingChanged = onRememberSortingChanged,
                            onRunGlobalRefresh = onRunGlobalRefresh,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Wiedergabelisten" -> ProviderSettingsPanel(
                            providerRepository = providerRepository,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "EPG" -> EpgSettingsPanel(
                            providerRepository = providerRepository,
                            epgSourceRepository = epgSourceRepository,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Optik" -> SettingsOptions(
                            showConfirm = { showConfirm = true },
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        maintenanceSection -> MaintenanceSettingsPanel(
                            cacheSettingsState = cacheSettingsState,
                            onCacheSizeChanged = onCacheSizeChanged,
                            onRunLogoRefresh = onRunLogoRefresh,
                            onRunCacheCleanup = onRunCacheCleanup,
                            onClearCache = onClearCache,
                            onReloadCacheStats = onReloadCacheStats,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        else -> InfoPanel(
                            title = selectedSection,
                            body = "Dieser Bereich ist vorbereitet. Optionen werden hier gebündelt, sobald die jeweilige Verwaltung umgesetzt ist.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            GlassPanel(modifier = Modifier.width(560.dp), contentPadding = VivicastSpacing.Space5) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                    InfoPanel(
                        title = "Änderung bestätigen",
                        body = "Diese lokale UI-Aktion speichert keine sensiblen Providerdaten.",
                        badge = "Lokal",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                        ActionPill("Schließen", modifier = Modifier.width(150.dp), onClick = { showConfirm = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsPanel(
    state: GeneralSettingsState,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onRememberSortingChanged: (Boolean) -> Unit,
    onRunGlobalRefresh: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    val toggleBackgroundRefresh = {
        val enabled = !state.backgroundRefreshEnabled
        onBackgroundRefreshChanged(enabled)
        message = if (enabled) {
            "Hintergrundaktualisierung aktiviert."
        } else {
            "Hintergrundaktualisierung deaktiviert."
        }
    }
    val showBootNotice = {
        message = "Autostart wird erst mit Boot-Receiver-Unterstuetzung aktiv."
    }
    val toggleRememberSorting = {
        onRememberSortingChanged(!state.rememberSorting)
        message = "Sortierverhalten aktualisiert."
    }
    val runGlobalRefresh = {
        onRunGlobalRefresh()
        message = "Globale Aktualisierung wurde eingeplant."
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = "Hintergrundaktualisierung",
                help = "Playlists und EPG im Hintergrund aktualisieren.",
                value = if (state.backgroundRefreshEnabled) "Ein" else "Aus",
                modifier = firstFocusModifier.onTvCenterClick(toggleBackgroundRefresh),
                onClick = toggleBackgroundRefresh,
            )
        }

        item {
            VivicastSettingsRow(
                title = "App beim TV-Start starten",
                help = "System-Startintegration ist vorbereitet, aber noch nicht aktiviert.",
                value = if (state.launchOnBoot) "Ein" else "Aus",
                modifier = Modifier.onTvCenterClick(showBootNotice),
                onClick = showBootNotice,
            )
        }

        item {
            VivicastSettingsRow(
                title = "Sortierung merken",
                help = "Lokale Listenreihenfolge pro Ansicht beibehalten.",
                value = if (state.rememberSorting) "Ein" else "Aus",
                modifier = Modifier.onTvCenterClick(toggleRememberSorting),
                onClick = toggleRememberSorting,
            )
        }

        item {
            VivicastSettingsRow(
                title = "Jetzt aktualisieren",
                help = "Playlists, EPG, Logos und Cache nach Refresh-Plan einplanen.",
                value = "Starten",
                modifier = Modifier.onTvCenterClick(runGlobalRefresh),
                onClick = runGlobalRefresh,
            )
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = "Hinweis",
                    body = requireNotNull(message),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun Modifier.onTvCenterClick(action: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                action()
                true
            }
            else -> false
        }
    }

@Composable
private fun EpgSettingsPanel(
    providerRepository: ProviderRepository,
    epgSourceRepository: EpgSourceRepository,
    firstFocusModifier: Modifier = Modifier,
) {
    val sources by epgSourceRepository.observeEpgSources().collectAsState(initial = emptyList())
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(EpgSourceEditorState.newSource()) }
    var showEditor by remember { mutableStateOf(false) }
    var showManualMapping by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<EpgSource?>(null) }
    val providerLinks by (selectedProviderId?.let { epgSourceRepository.observeProviderEpgSources(it) } ?: flowOf(emptyList()))
        .collectAsState(initial = emptyList())

    LaunchedEffect(sources) {
        val selectedSource = selectedSourceId?.let { id -> sources.firstOrNull { it.id == id } }
        if (selectedSource == null && selectedSourceId != null) {
            selectedSourceId = null
            editor = EpgSourceEditorState.newSource()
            showEditor = false
        }
    }

    LaunchedEffect(providers) {
        if (selectedProviderId != null && providers.none { it.id == selectedProviderId }) {
            selectedProviderId = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(
                label = "EPG Quelle hinzufügen",
                modifier = firstFocusModifier.width(250.dp),
                selected = showEditor && !editor.isEditing,
                onClick = {
                    selectedSourceId = null
                    editor = EpgSourceEditorState.newSource()
                    showEditor = true
                    showManualMapping = false
                    message = null
                },
            )
            ActionPill(
                label = "Manuelle Zuordnung",
                modifier = Modifier.width(230.dp),
                selected = showManualMapping,
                onClick = {
                    showManualMapping = true
                    showEditor = false
                    message = null
                },
            )
        }

        if (showManualMapping) {
            ManualEpgMappingPanel(
                providers = providers,
                sources = sources,
                selectedProviderId = selectedProviderId,
                providerLinks = providerLinks,
                repository = epgSourceRepository,
                message = message,
                onSelectProvider = { selectedProviderId = it },
                onMessage = { message = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
            EpgSourceList(
                sources = sources,
                selectedSourceId = selectedSourceId,
                onSelectSource = { source ->
                    selectedSourceId = source.id
                    editor = EpgSourceEditorState.from(source)
                    showEditor = true
                    showManualMapping = false
                    message = null
                },
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
            )

            if (showEditor) {
                EpgSourceEditor(
                    editor = editor,
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    providerLinks = providerLinks,
                    message = message,
                    onEditorChange = { editor = it },
                    onSelectProvider = { selectedProviderId = it },
                    onSave = {
                        val validationMessage = editor.validationMessage()
                        if (validationMessage != null) {
                            message = validationMessage
                            return@EpgSourceEditor
                        }
                        scope.launch {
                            runCatching { epgSourceRepository.saveSource(editor.toEditRequest()) }
                                .onSuccess { source ->
                                    selectedSourceId = source.id
                                    editor = EpgSourceEditorState.from(source)
                                    showEditor = true
                                    message = "EPG Quelle gespeichert. URL bleibt verborgen."
                                }
                                .onFailure { error ->
                                    message = "Speichern fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                                }
                        }
                    },
                    onDelete = {
                        pendingDelete = sources.firstOrNull { it.id == editor.sourceId }
                    },
                    onLinkProvider = { providerId, sourceId, priority ->
                        scope.launch {
                            runCatching { epgSourceRepository.linkSourceToProvider(providerId, sourceId, priority) }
                                .onSuccess { message = "EPG Quelle wurde dem Provider zugeordnet." }
                                .onFailure { error ->
                                    message = "Zuordnung fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                                }
                        }
                    },
                    onUnlinkProvider = { providerId, sourceId ->
                        scope.launch {
                            runCatching { epgSourceRepository.unlinkSourceFromProvider(providerId, sourceId) }
                                .onSuccess { message = "EPG Quelle wurde vom Provider entfernt." }
                                .onFailure { error ->
                                    message = "Entfernen fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                                }
                        }
                    },
                    onMoveProviderLink = { providerId, sourceId, direction ->
                        scope.launch {
                            runCatching { epgSourceRepository.moveSourcePriority(providerId, sourceId, direction) }
                                .onSuccess { message = "EPG Prioritaet wurde aktualisiert." }
                                .onFailure { error ->
                                    message = "Prioritaet fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                                }
                        }
                    },
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            } else {
                InfoPanel(
                    title = "EPG Verwaltung",
                    body = message ?: "EPG Quellen werden separat gespeichert und später Providern priorisiert zugeordnet.",
                    badge = "Phase 04",
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            }
            }
        }
    }

    pendingDelete?.let { source ->
        DeleteEpgSourceDialog(
            source = source,
            onCancel = { pendingDelete = null },
            onDelete = {
                scope.launch {
                    runCatching { epgSourceRepository.deleteSource(source.id) }
                        .onSuccess {
                            pendingDelete = null
                            selectedSourceId = null
                            editor = EpgSourceEditorState.newSource()
                            showEditor = false
                            message = "EPG Quelle geloescht. Programme und Zuordnungen dieser Quelle wurden entfernt."
                        }
                        .onFailure { error ->
                            pendingDelete = null
                            message = "Loeschen fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                        }
                }
            },
        )
    }
}

@Composable
private fun ManualEpgMappingPanel(
    providers: List<Provider>,
    sources: List<EpgSource>,
    selectedProviderId: String?,
    providerLinks: List<ProviderEpgSource>,
    repository: EpgSourceRepository,
    message: String?,
    onSelectProvider: (String) -> Unit,
    onMessage: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedChannelId by remember(selectedProviderId) { mutableStateOf<String?>(null) }
    var selectedSourceId by remember(selectedProviderId) { mutableStateOf<String?>(null) }
    var externalChannelId by remember(selectedProviderId) { mutableStateOf("") }
    val channels by (selectedProviderId?.let { repository.observeChannelsForProvider(it) } ?: flowOf(emptyList()))
        .collectAsState(initial = emptyList())
    val mappings by (
        if (selectedProviderId != null && selectedChannelId != null) {
            repository.observeMappingsForChannel(selectedProviderId, selectedChannelId!!)
        } else {
            flowOf(emptyList())
        }
    ).collectAsState(initial = emptyList())
    val linkedSources = remember(providerLinks, sources) {
        providerLinks
            .sortedBy { it.priority }
            .mapNotNull { link -> sources.firstOrNull { it.id == link.epgSourceId } }
    }
    val selectedMapping = mappings.firstOrNull { it.epgSourceId == selectedSourceId }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channels) {
        if (selectedChannelId != null && channels.none { it.id == selectedChannelId }) {
            selectedChannelId = null
        }
    }

    LaunchedEffect(linkedSources) {
        if (selectedSourceId == null || linkedSources.none { it.id == selectedSourceId }) {
            selectedSourceId = linkedSources.firstOrNull()?.id
        }
    }

    LaunchedEffect(selectedProviderId, selectedChannelId, selectedSourceId, selectedMapping?.epgChannelId) {
        externalChannelId = selectedMapping?.epgChannelId.orEmpty()
    }

    if (providers.isEmpty()) {
        InfoPanel(
            title = "Keine Provider",
            body = "Lege zuerst eine Wiedergabeliste an, um manuelle EPG-Zuordnungen zu bearbeiten.",
            badge = "EPG",
            modifier = modifier,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = modifier) {
        ManualMappingProviderList(
            providers = providers,
            selectedProviderId = selectedProviderId,
            onSelectProvider = {
                onSelectProvider(it)
                onMessage(null)
            },
            modifier = Modifier.weight(0.28f).fillMaxHeight(),
        )

        ManualMappingChannelList(
            channels = channels,
            mappings = mappings,
            selectedProviderId = selectedProviderId,
            selectedChannelId = selectedChannelId,
            onSelectChannel = {
                selectedChannelId = it
                onMessage(null)
            },
            modifier = Modifier.weight(0.34f).fillMaxHeight(),
        )

        ManualMappingDetail(
            sources = linkedSources,
            selectedProviderId = selectedProviderId,
            selectedChannel = channels.firstOrNull { it.id == selectedChannelId },
            selectedSourceId = selectedSourceId,
            selectedMapping = selectedMapping,
            externalChannelId = externalChannelId,
            message = message,
            onSelectSource = {
                selectedSourceId = it
                onMessage(null)
            },
            onExternalChannelIdChange = { externalChannelId = it },
            onSave = {
                val providerId = selectedProviderId
                val channelId = selectedChannelId
                val sourceId = selectedSourceId
                val normalizedExternalId = externalChannelId.trim()
                if (providerId == null || channelId == null || sourceId == null || normalizedExternalId.isBlank()) {
                    onMessage("Provider, Sender, EPG Quelle und externe Kanal-ID muessen ausgewaehlt sein.")
                    return@ManualMappingDetail
                }
                scope.launch {
                    runCatching {
                        repository.setManualChannelMapping(
                            ManualEpgChannelMappingRequest(
                                providerId = providerId,
                                channelId = channelId,
                                epgSourceId = sourceId,
                                epgChannelId = normalizedExternalId,
                            ),
                        )
                    }.onSuccess {
                        onMessage("Manuelle EPG-Zuordnung gespeichert.")
                    }.onFailure { error ->
                        onMessage("Zuordnung fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}")
                    }
                }
            },
            onClear = {
                val providerId = selectedProviderId
                val channelId = selectedChannelId
                val sourceId = selectedSourceId
                if (providerId == null || channelId == null || sourceId == null) {
                    onMessage("Provider, Sender und EPG Quelle muessen ausgewaehlt sein.")
                    return@ManualMappingDetail
                }
                scope.launch {
                    runCatching { repository.clearManualChannelMapping(providerId, channelId, sourceId) }
                        .onSuccess {
                            externalChannelId = ""
                            onMessage("Manuelle Zuordnung entfernt. Automatische Zuordnung kann beim naechsten Import greifen.")
                        }
                        .onFailure { error ->
                            onMessage("Entfernen fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}")
                        }
                }
            },
            modifier = Modifier.weight(0.38f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ManualMappingProviderList(
    providers: List<Provider>,
    selectedProviderId: String?,
    onSelectProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (providers.isEmpty()) {
        InfoPanel(
            title = "Keine Provider",
            body = "Lege zuerst eine Wiedergabeliste an, um EPG Zuordnungen zu bearbeiten.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        items(providers, key = { it.id }) { provider ->
            FocusPanel(
                selected = provider.id == selectedProviderId,
                onClick = { onSelectProvider(provider.id) },
                onFocused = { onSelectProvider(provider.id) },
                modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                    BasicText(provider.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BodyText(provider.status.label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ManualMappingChannelList(
    channels: List<Channel>,
    mappings: List<EpgChannelMapping>,
    selectedProviderId: String?,
    selectedChannelId: String?,
    onSelectChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedProviderId == null) {
        InfoPanel(
            title = "Provider waehlen",
            body = "Waehle links einen Provider, danach einen Sender fuer die manuelle EPG Zuordnung.",
            modifier = modifier,
        )
        return
    }
    if (channels.isEmpty()) {
        InfoPanel(
            title = "Keine Sender",
            body = "Dieser Provider hat noch keine importierten Live-TV-Sender.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        items(channels, key = { it.id }) { channel ->
            val hasManualMapping = mappings.any { it.channelId == channel.id && it.isManual }
            FocusPanel(
                selected = channel.id == selectedChannelId,
                onClick = { onSelectChannel(channel.id) },
                onFocused = { onSelectChannel(channel.id) },
                modifier = Modifier.fillMaxWidth().height(104.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                        BasicText(channel.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BodyText(channel.channelNumber?.let { "Kanal $it" } ?: channel.remoteId, maxLines = 1)
                    }
                    if (hasManualMapping) {
                        StatusBadge("Manuell", tone = VivicastColors.Success)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualMappingDetail(
    sources: List<EpgSource>,
    selectedProviderId: String?,
    selectedChannel: Channel?,
    selectedSourceId: String?,
    selectedMapping: EpgChannelMapping?,
    externalChannelId: String,
    message: String?,
    onSelectSource: (String) -> Unit,
    onExternalChannelIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(
                title = "Manuelle Zuordnung",
                body = "Ordnet einen lokalen Sender einer externen Kanal-ID aus einer verknuepften EPG Quelle zu.",
                badge = selectedMapping?.let { if (it.isManual) "Manuell" else "Automatisch" } ?: "EPG",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (selectedProviderId == null || selectedChannel == null) {
            item {
                InfoPanel(
                    title = "Sender waehlen",
                    body = "Waehle zuerst Provider und Sender aus.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }

        item {
            InfoPanel(
                title = selectedChannel.name,
                body = "Remote-ID: ${selectedChannel.remoteId}",
                badge = selectedChannel.channelNumber?.let { "Kanal $it" },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (sources.isEmpty()) {
            item {
                InfoPanel(
                    title = "Keine EPG Quelle verknuepft",
                    body = "Ordne im EPG Quellenbereich zuerst eine Quelle diesem Provider zu.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }

        item {
            SectionTitle("EPG Quelle")
        }

        items(sources, key = { it.id }) { source ->
            FocusPanel(
                selected = source.id == selectedSourceId,
                onClick = { onSelectSource(source.id) },
                onFocused = { onSelectSource(source.id) },
                modifier = Modifier.fillMaxWidth().height(92.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                        BasicText(source.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BodyText("Zeitversatz: ${source.timeShiftMinutes} Minuten", maxLines = 1)
                    }
                    StatusBadge(if (source.isActive) "Aktiv" else "Aus", tone = if (source.isActive) VivicastColors.Success else VivicastColors.SurfaceHigh)
                }
            }
        }

        item {
            ProviderTextField(
                label = "Externe EPG-Kanal-ID",
                value = externalChannelId,
                placeholder = "z.B. ard.de",
                onValueChange = onExternalChannelIdChange,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                ActionPill("Speichern", modifier = Modifier.width(150.dp), selected = true, onClick = onSave)
                ActionPill("Manuell loeschen", modifier = Modifier.width(190.dp), onClick = onClear)
            }
        }

        if (message != null) {
            item {
                InfoPanel(title = "Hinweis", body = message, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EpgSourceList(
    sources: List<EpgSource>,
    selectedSourceId: String?,
    onSelectSource: (EpgSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) {
        InfoPanel(
            title = "Keine EPGs",
            body = "Quellen werden lokal hinzugefügt und später zugeordnet.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        items(sources, key = { it.id }) { source ->
            FocusPanel(
                selected = source.id == selectedSourceId,
                onClick = { onSelectSource(source) },
                onFocused = { onSelectSource(source) },
                modifier = Modifier.fillMaxWidth().height(116.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            text = source.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = VivicastTypography.LabelLarge,
                        )
                        Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
                        StatusBadge(if (source.isActive) "Aktiv" else "Aus", tone = if (source.isActive) VivicastColors.Success else VivicastColors.SurfaceHigh)
                    }
                    BodyText("Zeitversatz: ${source.timeShiftMinutes} Minuten", maxLines = 1)
                    BodyText("Verwendet von: noch nicht zugeordnet", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EpgSourceEditor(
    editor: EpgSourceEditorState,
    providers: List<Provider>,
    selectedProviderId: String?,
    providerLinks: List<ProviderEpgSource>,
    message: String?,
    onEditorChange: (EpgSourceEditorState) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onLinkProvider: (providerId: String, sourceId: String, priority: Int) -> Unit,
    onUnlinkProvider: (providerId: String, sourceId: String) -> Unit,
    onMoveProviderLink: (providerId: String, sourceId: String, direction: EpgSourcePriorityDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item {
            InfoPanel(
                title = if (editor.isEditing) "EPG Quelle bearbeiten" else "EPG Quelle",
                body = if (editor.isEditing) "Name, Zeitversatz und Aktiv-Status ändern. URL nur bei Bedarf neu setzen." else "URL wird geschützt gespeichert und nicht in Room abgelegt.",
                badge = if (editor.isActive) "Aktiv" else "Aus",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            ProviderTextField(
                label = "Name",
                value = editor.name,
                placeholder = "EPG Quelle",
                onValueChange = { onEditorChange(editor.copy(name = it)) },
            )
        }

        item {
            ProviderTextField(
                label = "URL",
                value = editor.url,
                placeholder = if (editor.isEditing) "Neu setzen oder leer lassen" else "https://...",
                onValueChange = { onEditorChange(editor.copy(url = it)) },
                secret = editor.isEditing,
            )
        }

        item {
            FocusPanel(
                modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                        BasicText("Zeitversatz", style = VivicastTypography.LabelLarge)
                        BodyText("Korrigiert EPG-Zeiten in Minuten.", maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill("-30", modifier = Modifier.width(82.dp), onClick = {
                            onEditorChange(editor.copy(timeShiftMinutes = (editor.timeShiftMinutes - 30).coerceAtLeast(-720)))
                        })
                        BasicText("${editor.timeShiftMinutes} min", style = VivicastTypography.LabelLarge)
                        ActionPill("+30", modifier = Modifier.width(82.dp), onClick = {
                            onEditorChange(editor.copy(timeShiftMinutes = (editor.timeShiftMinutes + 30).coerceAtMost(720)))
                        })
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                ActionPill(
                    label = if (editor.isActive) "Aktiv" else "Aus",
                    modifier = Modifier.width(132.dp),
                    selected = editor.isActive,
                    onClick = { onEditorChange(editor.copy(isActive = !editor.isActive)) },
                )
                ActionPill(label = "Speichern", modifier = Modifier.width(150.dp), selected = true, onClick = onSave)
                if (editor.isEditing) {
                    ActionPill(label = "Loeschen", modifier = Modifier.width(140.dp), onClick = onDelete)
                }
            }
        }

        if (editor.isEditing) {
            item {
                InfoPanel(
                    title = "Provider-Zuordnung",
                    body = "Priorität ist pro Provider konfigurierbar. Manuelle Kanalzuordnung bleibt ein separater Flow.",
                    badge = "EPG",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (providers.isEmpty()) {
                item {
                    InfoPanel(
                        title = "Keine Provider",
                        body = "Lege zuerst eine Wiedergabeliste an, um EPG Quellen zuzuordnen.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(providers, key = { it.id }) { provider ->
                    FocusPanel(
                        selected = provider.id == selectedProviderId,
                        onClick = { onSelectProvider(provider.id) },
                        onFocused = { onSelectProvider(provider.id) },
                        modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
                        contentPadding = VivicastSpacing.Space4,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                                BasicText(provider.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BodyText(provider.status.label, maxLines = 1)
                            }
                            val link = if (provider.id == selectedProviderId) {
                                providerLinks.firstOrNull { it.epgSourceId == editor.sourceId }
                            } else {
                                null
                            }
                            StatusBadge(
                                label = link?.let { "Priorität ${it.priority}" } ?: "Nicht zugeordnet",
                                tone = if (link != null) VivicastColors.Success else VivicastColors.SurfaceHigh,
                            )
                        }
                    }
                }

                item {
                    val providerId = selectedProviderId
                    val sourceId = editor.sourceId
                    val existingLink = providerLinks.firstOrNull { it.epgSourceId == sourceId }
                    val nextPriority = existingLink?.priority ?: (providerLinks.maxOfOrNull { it.priority } ?: 0) + 1
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                            ActionPill(
                                label = existingLink?.let { "Prioritaet ${it.priority}" } ?: "Als Prioritaet $nextPriority verwenden",
                                modifier = Modifier.width(270.dp),
                                selected = existingLink != null,
                                onClick = {
                                    if (providerId != null && sourceId != null && existingLink == null) {
                                        onLinkProvider(providerId, sourceId, nextPriority)
                                    }
                                },
                            )
                            if (existingLink != null) {
                                ActionPill(
                                    label = "Entfernen",
                                    modifier = Modifier.width(140.dp),
                                    onClick = {
                                        if (providerId != null && sourceId != null) {
                                            onUnlinkProvider(providerId, sourceId)
                                        }
                                    },
                                )
                            }
                        }
                        if (existingLink != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                                val canMoveUp = existingLink.priority > 1
                                val canMoveDown = existingLink.priority < providerLinks.size
                                ActionPill(
                                    label = if (canMoveUp) "Hoeher" else "Oben",
                                    modifier = Modifier.width(128.dp),
                                    selected = !canMoveUp,
                                    onClick = {
                                        if (providerId != null && sourceId != null && canMoveUp) {
                                            onMoveProviderLink(providerId, sourceId, EpgSourcePriorityDirection.Up)
                                        }
                                    },
                                )
                                ActionPill(
                                    label = if (canMoveDown) "Tiefer" else "Unten",
                                    modifier = Modifier.width(128.dp),
                                    selected = !canMoveDown,
                                    onClick = {
                                        if (providerId != null && sourceId != null && canMoveDown) {
                                            onMoveProviderLink(providerId, sourceId, EpgSourcePriorityDirection.Down)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = "Hinweis",
                    body = message,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DeleteEpgSourceDialog(
    source: EpgSource,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        GlassPanel(modifier = Modifier.widthIn(min = 560.dp, max = 680.dp), contentPadding = VivicastSpacing.Space5) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = "EPG Quelle wirklich loeschen?",
                    body = "Diese EPG Quelle, ihre Programme und ihre Zuordnungen werden entfernt. Provider bleiben erhalten.",
                    badge = source.name,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Abbrechen", modifier = Modifier.width(150.dp), selected = true, onClick = onCancel)
                    ActionPill("Loeschen", modifier = Modifier.width(140.dp), onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ProviderSettingsPanel(
    providerRepository: ProviderRepository,
    firstFocusModifier: Modifier = Modifier,
) {
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(ProviderEditorState.newProvider(ProviderType.M3u)) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }

    LaunchedEffect(providers) {
        val selectedProvider = selectedProviderId?.let { id -> providers.firstOrNull { it.id == id } }
        if (selectedProvider == null && selectedProviderId != null) {
            selectedProviderId = null
            editor = ProviderEditorState.newProvider(ProviderType.M3u)
            showEditor = false
        }
    }

    val duplicateName = editor.name.isNotBlank() &&
        providers.any { provider ->
            provider.id != editor.providerId && provider.name.equals(editor.name.trim(), ignoreCase = true)
        }

    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(
                label = "M3U hinzufügen",
                modifier = firstFocusModifier.width(210.dp),
                selected = showEditor && !editor.isEditing && editor.type == ProviderType.M3u,
                onClick = {
                    selectedProviderId = null
                    editor = ProviderEditorState.newProvider(ProviderType.M3u)
                    showEditor = true
                    message = null
                },
            )
            ActionPill(
                label = "Xtream hinzufügen",
                modifier = Modifier.width(240.dp),
                selected = showEditor && !editor.isEditing && editor.type == ProviderType.Xtream,
                onClick = {
                    selectedProviderId = null
                    editor = ProviderEditorState.newProvider(ProviderType.Xtream)
                    showEditor = true
                    message = null
                },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
            ProviderList(
                providers = providers,
                selectedProviderId = selectedProviderId,
                onSelectProvider = { provider ->
                    selectedProviderId = provider.id
                    editor = ProviderEditorState.from(provider)
                    showEditor = true
                    message = null
                },
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
            )

            if (showEditor) {
                ProviderEditor(
                    editor = editor,
                duplicateName = duplicateName,
                message = message,
                onEditorChange = { editor = it },
                onSave = {
                    val validationMessage = editor.validationMessage()
                    if (validationMessage != null) {
                        message = validationMessage
                        return@ProviderEditor
                    }
                    scope.launch {
                        runCatching {
                            if (editor.isEditing) {
                                providerRepository.updateProvider(editor.toUpdateRequest())
                            } else {
                                providerRepository.createProvider(editor.toCreateRequest())
                            }
                        }.onSuccess { result ->
                            selectedProviderId = result.provider.id
                            editor = ProviderEditorState.from(result.provider)
                            message = if (result.hasDuplicateName) {
                                "Provider gespeichert. Provider existiert bereits."
                            } else {
                                "Provider gespeichert. Zugangsdaten bleiben verborgen."
                            }
                        }.onFailure { error ->
                            message = "Speichern fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                        }
                    }
                },
                onToggleEnabled = {
                    val provider = providers.firstOrNull { it.id == editor.providerId } ?: return@ProviderEditor
                    scope.launch {
                        val enabled = !provider.isActive
                        runCatching { providerRepository.setProviderEnabled(provider.id, enabled) }
                            .onSuccess {
                                message = if (enabled) "Provider aktiviert." else "Provider deaktiviert. Daten bleiben erhalten."
                            }
                            .onFailure { error -> message = "Statusänderung fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}" }
                    }
                },
                onDelete = {
                    pendingDelete = providers.firstOrNull { it.id == editor.providerId }
                },
                modifier = Modifier.weight(0.58f).fillMaxHeight(),
            )
            } else {
                InfoPanel(
                    title = "Provider Verwaltung",
                    body = message ?: "Provider werden lokal konfiguriert. Zugangsdaten bleiben außerhalb der Datenbank gespeichert.",
                    badge = "Sicher",
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            }
        }
    }

    pendingDelete?.let { provider ->
        DeleteProviderDialog(
            provider = provider,
            onCancel = { pendingDelete = null },
            onDelete = {
                scope.launch {
                    runCatching { providerRepository.deleteProvider(provider.id) }
                        .onSuccess {
                            pendingDelete = null
                            selectedProviderId = null
                            editor = ProviderEditorState.newProvider(ProviderType.M3u)
                            showEditor = false
                            message = "Provider gelöscht. Providerbezogene Daten wurden entfernt."
                        }
                        .onFailure { error ->
                            pendingDelete = null
                            message = "Löschen fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                        }
                }
            },
        )
    }
}

@Composable
private fun ProviderList(
    providers: List<Provider>,
    selectedProviderId: String?,
    onSelectProvider: (Provider) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (providers.isEmpty()) {
        InfoPanel(
            title = "Keine Provider",
            body = "Noch keine lokale Konfiguration. Es wird nichts importiert.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        items(providers, key = { it.id }) { provider ->
            FocusPanel(
                selected = provider.id == selectedProviderId,
                onClick = { onSelectProvider(provider) },
                onFocused = { onSelectProvider(provider) },
                modifier = Modifier.fillMaxWidth().height(116.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            text = provider.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = VivicastTypography.LabelLarge,
                        )
                        Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
                        StatusBadge(provider.type.label)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                        StatusBadge(provider.status.label, tone = provider.status.tone)
                        if (!provider.isActive) {
                            StatusBadge("Aus", tone = VivicastColors.Warning)
                        }
                    }
                    BodyText(provider.importSummary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ProviderEditor(
    editor: ProviderEditorState,
    duplicateName: Boolean,
    message: String?,
    onEditorChange: (ProviderEditorState) -> Unit,
    onSave: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item {
            InfoPanel(
                title = if (editor.isEditing) "Bearbeiten" else "Provider",
                body = if (editor.isEditing) {
                    "Typ und ID bleiben stabil."
                } else {
                    "Verschlüsselt speichern. Kein Import."
                },
                badge = editor.type.label,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (duplicateName) {
            item {
                InfoPanel(
                    title = "Provider existiert bereits.",
                    body = "Der Name ist bereits lokal vorhanden. Speichern bleibt erlaubt.",
                    badge = "Warnung",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            ProviderTextField(
                label = "Name",
                value = editor.name,
                placeholder = "Provider Name",
                onValueChange = { onEditorChange(editor.copy(name = it)) },
            )
        }

        if (!editor.isEditing) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                    ActionPill(
                        label = "M3U",
                        modifier = Modifier.width(132.dp),
                        selected = editor.type == ProviderType.M3u,
                        onClick = { onEditorChange(ProviderEditorState.newProvider(ProviderType.M3u)) },
                    )
                    ActionPill(
                        label = "Xtream",
                        modifier = Modifier.width(150.dp),
                        selected = editor.type == ProviderType.Xtream,
                        onClick = { onEditorChange(ProviderEditorState.newProvider(ProviderType.Xtream)) },
                    )
                }
            }
        }

        when (editor.type) {
            ProviderType.M3u -> {
                item {
                    ProviderTextField(
                        label = "M3U URL",
                        value = editor.m3uUrl,
                        placeholder = if (editor.isEditing) "Neu setzen oder leer lassen" else "https://...",
                        onValueChange = { onEditorChange(editor.copy(m3uUrl = it)) },
                        secret = editor.isEditing,
                    )
                }
            }

            ProviderType.Xtream -> {
                item {
                    ProviderTextField(
                        label = "Server",
                        value = editor.xtreamServerUrl,
                        placeholder = if (editor.isEditing) "Neu setzen oder leer lassen" else "https://server.example",
                        onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it)) },
                        secret = editor.isEditing,
                    )
                }
                item {
                    ProviderTextField(
                        label = "Benutzername",
                        value = editor.xtreamUsername,
                        placeholder = if (editor.isEditing) "Neu setzen oder leer lassen" else "Benutzername",
                        onValueChange = { onEditorChange(editor.copy(xtreamUsername = it)) },
                        secret = editor.isEditing,
                    )
                }
                item {
                    ProviderTextField(
                        label = "Passwort",
                        value = editor.xtreamPassword,
                        placeholder = if (editor.isEditing) "Neu setzen oder leer lassen" else "Passwort",
                        onValueChange = { onEditorChange(editor.copy(xtreamPassword = it)) },
                        secret = true,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                BodyText("Inhalte", maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                    ActionPill(
                        label = "Live-TV",
                        modifier = Modifier.width(132.dp),
                        selected = editor.includeLiveTv,
                        onClick = { onEditorChange(editor.copy(includeLiveTv = !editor.includeLiveTv)) },
                    )
                    ActionPill(
                        label = "Filme",
                        modifier = Modifier.width(118.dp),
                        selected = editor.includeMovies,
                        onClick = { onEditorChange(editor.copy(includeMovies = !editor.includeMovies)) },
                    )
                    ActionPill(
                        label = "Serien",
                        modifier = Modifier.width(118.dp),
                        selected = editor.includeSeries,
                        onClick = { onEditorChange(editor.copy(includeSeries = !editor.includeSeries)) },
                    )
                }
            }
        }

        item {
            FocusPanel(
                modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                        BasicText("Intervall", style = VivicastTypography.LabelLarge)
                        BodyText("Wird erst in der Import-Phase verwendet.", maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill("-6h", modifier = Modifier.width(88.dp), onClick = {
                            onEditorChange(editor.copy(refreshIntervalHours = (editor.refreshIntervalHours - 6).coerceAtLeast(1)))
                        })
                        BasicText("${editor.refreshIntervalHours} h", style = VivicastTypography.LabelLarge)
                        ActionPill("+6h", modifier = Modifier.width(88.dp), onClick = {
                            onEditorChange(editor.copy(refreshIntervalHours = (editor.refreshIntervalHours + 6).coerceAtMost(168)))
                        })
                    }
                }
            }
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = "Hinweis",
                    body = message,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                ActionPill(label = "Speichern", modifier = Modifier.width(150.dp), selected = true, onClick = onSave)
                if (editor.isEditing) {
                    ActionPill(label = "Aktiv/Aus", modifier = Modifier.width(150.dp), onClick = onToggleEnabled)
                    ActionPill(label = "Löschen", modifier = Modifier.width(140.dp), onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ProviderTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        BasicText(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextSecondary),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = VivicastTypography.LabelLarge.copy(color = VivicastColors.TextPrimary),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(RoundedCornerShape(VivicastShapes.RadiusMedium))
                .background(if (focused) VivicastColors.SurfaceSelected else VivicastColors.Surface)
                .border(
                    width = if (focused) VivicastBorders.FocusWidth else VivicastBorders.Hairline,
                    color = if (focused) VivicastColors.FocusRing else Color(0x66344A62),
                    shape = RoundedCornerShape(VivicastShapes.RadiusMedium),
                )
                .padding(horizontal = VivicastSpacing.Space4, vertical = VivicastSpacing.Space3),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = VivicastTypography.LabelLarge.copy(color = VivicastColors.TextTertiary),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun DeleteProviderDialog(
    provider: Provider,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        GlassPanel(modifier = Modifier.widthIn(min = 560.dp, max = 680.dp), contentPadding = VivicastSpacing.Space5) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = "Provider wirklich löschen?",
                    body = "Diese Aktion kann nicht rückgängig gemacht werden. Providerbezogene Sender, Kategorien, Favoriten, Verlauf, Playback Progress und EPG-Zuordnungen werden gelöscht. EPG-Quellen bleiben erhalten.",
                    badge = provider.name,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Abbrechen", modifier = Modifier.width(150.dp), selected = true, onClick = onCancel)
                    ActionPill("Löschen", modifier = Modifier.width(140.dp), onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun SettingsOptions(
    showConfirm: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        itemsIndexed(DemoCatalog.settings) { index, setting ->
            SettingRow(
                setting = setting,
                modifier = if (index == 0) firstFocusModifier else Modifier,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                ActionPill("Änderung prüfen", modifier = Modifier.width(210.dp), onClick = showConfirm)
            }
        }
    }
}

@Composable
private fun SettingRow(
    setting: DemoSetting,
    modifier: Modifier = Modifier,
) {
    VivicastSettingsRow(title = setting.title, help = setting.help, value = setting.value, modifier = modifier)
}

@Composable
private fun MaintenanceSettingsPanel(
    cacheSettingsState: CacheSettingsState,
    onCacheSizeChanged: (Int) -> Unit,
    onRunLogoRefresh: () -> Unit,
    onRunCacheCleanup: () -> Unit,
    onClearCache: () -> Unit,
    onReloadCacheStats: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    val cacheOptions = listOf(250, 500, 1024, 2048, 0)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(
                title = "Vivicast",
                body = "Lokale Android-TV-App. Wartungsaktionen bleiben lokal und speichern keine Provider-Geheimnisse.",
                badge = "Phase 04",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            VivicastSettingsRow(
                title = "Cache Informationen",
                help = "${cacheSettingsState.fileCount} Dateien im lokalen Mediencache.",
                value = "${formatCacheSize(cacheSettingsState.totalSizeBytes)} / ${formatCacheLimit(cacheSettingsState.maxCacheSizeMb)}",
                modifier = firstFocusModifier,
                onClick = {
                    onReloadCacheStats()
                    message = "Cache Informationen wurden aktualisiert."
                },
            )
        }

        item {
            FocusPanel(
                modifier = Modifier.fillMaxWidth().height(128.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                    BasicText("Cache Größe", style = VivicastTypography.LabelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                        cacheOptions.forEach { sizeMb ->
                            ActionPill(
                                label = formatCacheLimit(sizeMb),
                                modifier = Modifier.width(if (sizeMb == 0) 150.dp else 112.dp),
                                selected = cacheSettingsState.maxCacheSizeMb == sizeMb,
                                onClick = {
                                    onCacheSizeChanged(sizeMb)
                                    message = "Cache-Größe wurde aktualisiert."
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            VivicastSettingsRow(
                title = "Medienbilder neu aufbauen",
                help = "Senderlogos, Poster, Backdrops und Episodenbilder werden über den bestehenden Refresh-Worker aktualisiert.",
                value = "Einplanen",
                onClick = {
                    onRunLogoRefresh()
                    message = "Medienbild-Neuaufbau wurde eingeplant."
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = "Cache Cleanup",
                help = "Entfernt alte Dateien, bis das konfigurierte Größenlimit eingehalten wird.",
                value = "Einplanen",
                onClick = {
                    onRunCacheCleanup()
                    message = "Cache Cleanup wurde eingeplant."
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = "Cache leeren",
                help = "Entfernt lokal gespeicherte Medienbilder. Providerdaten bleiben erhalten.",
                value = "Ausführen",
                onClick = {
                    onClearCache()
                    message = "Cache wurde geleert."
                },
            )
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = "Hinweis",
                    body = message.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DemoStates(showConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        InfoPanel("Ladezustand", "Lokaler Ladezustand ohne Netzwerkzugriff.", badge = "Laden")
        InfoPanel("Leerer Zustand", "Leere Kategorie und keine Suchtreffer sind sichtbar testbar.", badge = "Leer")
        InfoPanel("Fehlerzustand", "Provider kann lokal als fehlerhaft markiert werden, sobald Import existiert.", badge = "Fehler")
        InfoPanel("Provider-Hinweis", "Providerstatus bleibt isoliert und ändert keine anderen Provider.", badge = "Provider")
        ActionPill("Bestätigen", modifier = Modifier.width(150.dp), onClick = showConfirm)
    }
}

private data class ProviderEditorState(
    val providerId: String?,
    val type: ProviderType,
    val name: String,
    val m3uUrl: String,
    val xtreamServerUrl: String,
    val xtreamUsername: String,
    val xtreamPassword: String,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
) {
    val isEditing: Boolean get() = providerId != null

    fun validationMessage(): String? {
        if (name.isBlank()) return "Name fehlt."
        if (!includeLiveTv && !includeMovies && !includeSeries) return "Mindestens ein Inhaltstyp muss aktiv sein."
        if (!isEditing) {
            when (type) {
                ProviderType.M3u -> if (m3uUrl.isBlank()) return "M3U URL fehlt."
                ProviderType.Xtream -> {
                    if (xtreamServerUrl.isBlank()) return "Xtream Server fehlt."
                    if (xtreamUsername.isBlank()) return "Xtream Benutzername fehlt."
                    if (xtreamPassword.isBlank()) return "Xtream Passwort fehlt."
                }
            }
        }
        return null
    }

    fun toCreateRequest(): ProviderCreateRequest =
        ProviderCreateRequest(
            name = name,
            type = type,
            m3uUrl = m3uUrl,
            xtreamServerUrl = xtreamServerUrl,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword,
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
        )

    fun toUpdateRequest(): ProviderUpdateRequest =
        ProviderUpdateRequest(
            providerId = requireNotNull(providerId),
            name = name,
            m3uUrl = m3uUrl.ifBlank { null },
            xtreamServerUrl = xtreamServerUrl.ifBlank { null },
            xtreamUsername = xtreamUsername.ifBlank { null },
            xtreamPassword = xtreamPassword.ifBlank { null },
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
        )

    companion object {
        fun newProvider(type: ProviderType): ProviderEditorState =
            ProviderEditorState(
                providerId = null,
                type = type,
                name = "",
                m3uUrl = "",
                xtreamServerUrl = "",
                xtreamUsername = "",
                xtreamPassword = "",
                includeLiveTv = true,
                includeMovies = true,
                includeSeries = true,
                refreshIntervalHours = DEFAULT_REFRESH_INTERVAL_HOURS,
            )

        fun from(provider: Provider): ProviderEditorState =
            ProviderEditorState(
                providerId = provider.id,
                type = provider.type,
                name = provider.name,
                m3uUrl = "",
                xtreamServerUrl = "",
                xtreamUsername = "",
                xtreamPassword = "",
                includeLiveTv = provider.includeLiveTv,
                includeMovies = provider.includeMovies,
                includeSeries = provider.includeSeries,
                refreshIntervalHours = provider.refreshIntervalHours,
            )
    }
}

private data class EpgSourceEditorState(
    val sourceId: String?,
    val name: String,
    val url: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
) {
    val isEditing: Boolean get() = sourceId != null

    fun validationMessage(): String? {
        if (name.isBlank()) return "Name fehlt."
        if (!isEditing && url.isBlank()) return "EPG URL fehlt."
        return null
    }

    fun toEditRequest(): EpgSourceEditRequest =
        EpgSourceEditRequest(
            sourceId = sourceId,
            name = name,
            url = url.ifBlank { null },
            timeShiftMinutes = timeShiftMinutes,
            isActive = isActive,
        )

    companion object {
        fun newSource(): EpgSourceEditorState =
            EpgSourceEditorState(
                sourceId = null,
                name = "",
                url = "",
                timeShiftMinutes = 0,
                isActive = true,
            )

        fun from(source: EpgSource): EpgSourceEditorState =
            EpgSourceEditorState(
                sourceId = source.id,
                name = source.name,
                url = "",
                timeShiftMinutes = source.timeShiftMinutes,
                isActive = source.isActive,
            )
    }
}

private fun formatCacheLimit(sizeMb: Int): String =
    when (sizeMb) {
        0 -> "Unbegrenzt"
        1024 -> "1 GB"
        2048 -> "2 GB"
        else -> "$sizeMb MB"
    }

private fun formatCacheSize(sizeBytes: Long): String {
    val megabytes = sizeBytes / (1024L * 1024L)
    if (megabytes < 1024L) return "$megabytes MB"
    val gigabytes = megabytes / 1024L
    val remainingMegabytes = megabytes % 1024L
    return if (remainingMegabytes == 0L) {
        "$gigabytes GB"
    } else {
        "$gigabytes.${remainingMegabytes * 10L / 1024L} GB"
    }
}

private val ProviderType.label: String
    get() = when (this) {
        ProviderType.M3u -> "M3U"
        ProviderType.Xtream -> "Xtream"
    }

private val ProviderStatus.label: String
    get() = when (this) {
        ProviderStatus.Active -> "Aktiv"
        ProviderStatus.Refreshing -> "Aktualisierung"
        ProviderStatus.ConnectionError -> "Verbindungsfehler"
        ProviderStatus.InvalidCredentials -> "Ungültig"
        ProviderStatus.Expired -> "Abgelaufen"
        ProviderStatus.Disabled -> "Deaktiviert"
    }

private val ProviderStatus.tone: Color
    get() = when (this) {
        ProviderStatus.Active -> VivicastColors.Success
        ProviderStatus.Refreshing -> VivicastColors.Info
        ProviderStatus.ConnectionError -> VivicastColors.Warning
        ProviderStatus.InvalidCredentials -> VivicastColors.Error
        ProviderStatus.Expired -> VivicastColors.Warning
        ProviderStatus.Disabled -> VivicastColors.SurfaceHigh
    }

private val Provider.importSummary: String
    get() = listOfNotNull(
        "Live-TV".takeIf { includeLiveTv },
        "Filme".takeIf { includeMovies },
        "Serien".takeIf { includeSeries },
    ).joinToString(" | ") + " | alle $refreshIntervalHours h"
