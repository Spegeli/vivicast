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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
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
    val startDestination: SettingsStartDestination = SettingsStartDestination.Home,
)

enum class SettingsStartDestination {
    Home,
    LiveTv,
    Movies,
    Series,
}

data class CacheSettingsState(
    val totalSizeBytes: Long = 0L,
    val fileCount: Int = 0,
)

data class EpgSettingsState(
    val pastRetentionDays: Int = 1,
    val futureRetentionDays: Int = 7,
    val refreshIntervalHours: Int = 24,
    val refreshOnAppStartEnabled: Boolean = true,
    val refreshOnPlaylistChangeEnabled: Boolean = true,
)

data class PlaybackSettingsState(
    val externalPlayer: PlaybackExternalPlayerMode = PlaybackExternalPlayerMode.Internal,
    val timeshiftEnabled: Boolean = true,
    val timeshiftMinutes: Int = 30,
    val timeshiftStorage: PlaybackTimeshiftStorageMode = PlaybackTimeshiftStorageMode.Automatic,
    val autoNextEnabled: Boolean = false,
    val autoNextCountdownSeconds: Int = 10,
)

data class ParentalControlSettingsState(
    val hasPin: Boolean = false,
    val lockedUntilMillis: Long = 0L,
    val protectSettings: Boolean = false,
    val protectMovies: Boolean = false,
    val protectSeries: Boolean = false,
    val protectAdultContent: Boolean = false,
)

enum class ParentalProtectionArea {
    Settings,
    Movies,
    Series,
    AdultContent,
}

enum class PlaybackExternalPlayerMode {
    Internal,
    External,
    AskEveryTime,
}

enum class PlaybackTimeshiftStorageMode {
    Automatic,
    Ram,
    InternalStorage,
}

data class AboutAppState(
    val appVersion: String = "Unbekannt",
    val packageName: String = "com.vivicast.tv",
    val databaseVersion: Int = 0,
    val androidVersion: String = "Unbekannt",
    val deviceModel: String = "Unbekannt",
    val languageTag: String = "Unbekannt",
    val timeZoneId: String = "Unbekannt",
    val supportInformationText: String = "",
)

data class DiagnosticsSettingsState(
    val diagnosticsLoggingEnabled: Boolean = false,
    val retentionDays: Int = 1,
)

enum class HistoryClearTarget {
    LiveTv,
    Movies,
    Series,
    Search,
    All,
}

private val SettingsSections = listOf(
    "Allgemein",
    "Wiedergabelisten",
    "EPG",
    "Optik",
    "Wiedergabe",
    "Kindersicherung",
    "Speicher & Verlauf",
    "Backup",
    "\u00dcber die App",
)

@Composable
fun SettingsRoute(
    providerRepository: ProviderRepository,
    epgSourceRepository: EpgSourceRepository,
    generalSettingsState: GeneralSettingsState,
    epgSettingsState: EpgSettingsState,
    playbackSettingsState: PlaybackSettingsState,
    parentalControlSettingsState: ParentalControlSettingsState = ParentalControlSettingsState(),
    cacheSettingsState: CacheSettingsState,
    aboutAppState: AboutAppState,
    diagnosticsSettingsState: DiagnosticsSettingsState = DiagnosticsSettingsState(),
    onTestProviderConnection: suspend (ProviderCreateRequest) -> String?,
    onProviderSaved: (String) -> Unit,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onRememberSortingChanged: (Boolean) -> Unit,
    onStartDestinationChanged: (SettingsStartDestination) -> Unit,
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    onPlaybackPreferencesChanged: (PlaybackSettingsState) -> Unit,
    onSetPin: (String) -> String? = { null },
    onChangePin: (String, String) -> String? = { _, _ -> null },
    onDisablePin: (String) -> String? = { null },
    onProtectionChanged: (ParentalProtectionArea, Boolean) -> String? = { _, _ -> null },
    onExportStandardBackup: () -> Unit = {},
    onImportStandardBackup: () -> Unit = {},
    onExportEncryptedFullBackup: (String) -> Unit = {},
    onImportEncryptedFullBackup: (String) -> Unit = {},
    onDiagnosticsSettingsChanged: (DiagnosticsSettingsState) -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    onCopySupportInformation: () -> Unit = {},
    onRunGlobalRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: (HistoryClearTarget) -> Unit,
    onReloadCacheStats: () -> Unit,
) {
    var selectedSection by remember { mutableStateOf("Allgemein") }
    val detailFocusRequester = remember { FocusRequester() }
    val settingsSections = remember { SettingsSections }
    val sectionsWithDetailFocus = remember { SettingsSections.toSet() }

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
                            onStartDestinationChanged = onStartDestinationChanged,
                            onRunGlobalRefresh = onRunGlobalRefresh,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Wiedergabelisten" -> ProviderSettingsPanel(
                            providerRepository = providerRepository,
                            onTestProviderConnection = onTestProviderConnection,
                            onProviderSaved = onProviderSaved,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "EPG" -> EpgSettingsPanel(
                            providerRepository = providerRepository,
                            epgSourceRepository = epgSourceRepository,
                            state = epgSettingsState,
                            onEpgPreferencesChanged = onEpgPreferencesChanged,
                            onRunGlobalRefresh = onRunGlobalRefresh,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Optik" -> AppearanceSettingsPanel(
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Wiedergabe" -> PlaybackSettingsPanel(
                            state = playbackSettingsState,
                            onPlaybackPreferencesChanged = onPlaybackPreferencesChanged,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Kindersicherung" -> ParentalControlSettingsPanel(
                            state = parentalControlSettingsState,
                            onSetPin = onSetPin,
                            onChangePin = onChangePin,
                            onDisablePin = onDisablePin,
                            onProtectionChanged = onProtectionChanged,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Speicher & Verlauf" -> MaintenanceSettingsPanel(
                            cacheSettingsState = cacheSettingsState,
                            onClearCache = onClearCache,
                            onClearHistory = onClearHistory,
                            onReloadCacheStats = onReloadCacheStats,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "Backup" -> BackupSettingsPanel(
                            onExportStandardBackup = onExportStandardBackup,
                            onImportStandardBackup = onImportStandardBackup,
                            onExportEncryptedFullBackup = onExportEncryptedFullBackup,
                            onImportEncryptedFullBackup = onImportEncryptedFullBackup,
                            firstFocusModifier = Modifier.focusRequester(detailFocusRequester),
                        )
                        "\u00dcber die App" -> AboutSettingsPanel(
                            state = aboutAppState,
                            diagnosticsSettingsState = diagnosticsSettingsState,
                            onDiagnosticsSettingsChanged = onDiagnosticsSettingsChanged,
                            onExportDiagnostics = onExportDiagnostics,
                            onCopySupportInformation = onCopySupportInformation,
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

}

@Composable
fun PlaybackSettingsPanel(
    state: PlaybackSettingsState = PlaybackSettingsState(),
    onPlaybackPreferencesChanged: (PlaybackSettingsState) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    val cycleExternalPlayer = {
        onPlaybackPreferencesChanged(state.copy(externalPlayer = state.externalPlayer.nextExternalPlayerPreference()))
    }
    val toggleTimeshift = {
        onPlaybackPreferencesChanged(state.copy(timeshiftEnabled = !state.timeshiftEnabled))
    }
    val cycleTimeshiftMinutes = {
        if (state.timeshiftEnabled) {
            onPlaybackPreferencesChanged(state.copy(timeshiftMinutes = state.timeshiftMinutes.nextTimeshiftMinutes()))
        }
    }
    val cycleTimeshiftStorage = {
        if (state.timeshiftEnabled) {
            onPlaybackPreferencesChanged(state.copy(timeshiftStorage = state.timeshiftStorage.nextTimeshiftStorage()))
        }
    }
    val toggleAutoNext = {
        onPlaybackPreferencesChanged(state.copy(autoNextEnabled = !state.autoNextEnabled))
    }
    val cycleCountdown = {
        if (state.autoNextEnabled) {
            onPlaybackPreferencesChanged(
                state.copy(autoNextCountdownSeconds = state.autoNextCountdownSeconds.nextAutoNextCountdown()),
            )
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(
                title = "Wiedergabe",
                body = "Lokale Wiedergabeoptionen für Timeshift und Player-Verhalten.",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            VivicastSettingsRow(
                title = "Externer Player",
                help = "Gilt nur fuer Filme und einzelne Episoden. Live-TV und Catch-Up bleiben intern.",
                value = state.externalPlayer.label,
                modifier = firstFocusModifier.onTvCenterClick(cycleExternalPlayer),
                onClick = cycleExternalPlayer,
            )
        }

        item {
            VivicastSettingsRow(
                title = "Timeshift",
                help = "Erlaubt zeitversetztes Live-TV, sofern Sender und Stream es unterstuetzen.",
                value = if (state.timeshiftEnabled) "Ein" else "Aus",
                modifier = Modifier.onTvCenterClick(toggleTimeshift),
                onClick = toggleTimeshift,
            )
        }

        item {
            VivicastSettingsRow(
                title = "Maximale Timeshift-Dauer",
                help = if (state.timeshiftEnabled) {
                    "Begrenzt den aktiven Timeshift-Puffer."
                } else {
                    "Bleibt gespeichert, ist bei deaktiviertem Timeshift nicht bedienbar."
                },
                value = "${state.timeshiftMinutes.validTimeshiftMinutes()} Minuten",
                modifier = if (state.timeshiftEnabled) Modifier.onTvCenterClick(cycleTimeshiftMinutes) else Modifier,
                onClick = cycleTimeshiftMinutes,
            )
        }

        item {
            VivicastSettingsRow(
                title = "Timeshift-Speicher",
                help = if (state.timeshiftEnabled) {
                    "Waehlt den Speicher fuer den aktiven Timeshift-Puffer."
                } else {
                    "Bleibt sichtbar, ist bei deaktiviertem Timeshift nicht bedienbar."
                },
                value = state.timeshiftStorage.label,
                modifier = if (state.timeshiftEnabled) Modifier.onTvCenterClick(cycleTimeshiftStorage) else Modifier,
                onClick = cycleTimeshiftStorage,
            )
        }

        item {
            VivicastSettingsRow(
                title = "Automatisch n\u00e4chste Folge",
                help = "Gilt f\u00fcr Serienepisoden im internen Player.",
                value = if (state.autoNextEnabled) "Ein" else "Aus",
                modifier = Modifier.onTvCenterClick(toggleAutoNext),
                onClick = toggleAutoNext,
            )
        }

        item {
            VivicastSettingsRow(
                title = "Countdown n\u00e4chste Folge",
                help = if (state.autoNextEnabled) {
                    "Legt fest, wann das Auto-Next-Panel erscheint."
                } else {
                    "Bleibt gespeichert, ist bei deaktiviertem Auto-Next nicht bedienbar."
                },
                value = "${state.autoNextCountdownSeconds.validAutoNextCountdown()} Sekunden",
                modifier = Modifier.onTvCenterClick(cycleCountdown),
                onClick = cycleCountdown,
            )
        }
    }
}

@Composable
private fun GeneralSettingsPanel(
    state: GeneralSettingsState,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onRememberSortingChanged: (Boolean) -> Unit,
    onStartDestinationChanged: (SettingsStartDestination) -> Unit,
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
    val cycleStartDestination = {
        onStartDestinationChanged(state.startDestination.next())
        message = "Startbereich gespeichert. Wirkt beim nächsten App-Start."
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
                title = "Startbereich",
                help = "Öffnet beim nächsten regulären App-Start diesen Bereich.",
                value = state.startDestination.label,
                modifier = Modifier.onTvCenterClick(cycleStartDestination),
                onClick = cycleStartDestination,
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

private val SettingsStartDestination.label: String
    get() = when (this) {
        SettingsStartDestination.Home -> "Home"
        SettingsStartDestination.LiveTv -> "Live-TV"
        SettingsStartDestination.Movies -> "Filme"
        SettingsStartDestination.Series -> "Serien"
    }

private fun SettingsStartDestination.next(): SettingsStartDestination =
    when (this) {
        SettingsStartDestination.Home -> SettingsStartDestination.LiveTv
        SettingsStartDestination.LiveTv -> SettingsStartDestination.Movies
        SettingsStartDestination.Movies -> SettingsStartDestination.Series
        SettingsStartDestination.Series -> SettingsStartDestination.Home
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

private fun Int.validAutoNextCountdown(): Int =
    when (this) {
        5, 10, 15, 30 -> this
        else -> 10
    }

private fun Int.nextAutoNextCountdown(): Int =
    when (validAutoNextCountdown()) {
        5 -> 10
        10 -> 15
        15 -> 30
        else -> 5
    }

private fun Int.validTimeshiftMinutes(): Int =
    when (this) {
        15, 30, 60, 120 -> this
        else -> 30
    }

private fun Int.nextTimeshiftMinutes(): Int =
    when (validTimeshiftMinutes()) {
        15 -> 30
        30 -> 60
        60 -> 120
        else -> 15
    }

private val PlaybackExternalPlayerMode.label: String
    get() = when (this) {
        PlaybackExternalPlayerMode.Internal -> "Intern"
        PlaybackExternalPlayerMode.External -> "Extern"
        PlaybackExternalPlayerMode.AskEveryTime -> "Immer fragen"
    }

private fun PlaybackExternalPlayerMode.nextExternalPlayerPreference(): PlaybackExternalPlayerMode =
    when (this) {
        PlaybackExternalPlayerMode.Internal -> PlaybackExternalPlayerMode.External
        PlaybackExternalPlayerMode.External -> PlaybackExternalPlayerMode.AskEveryTime
        PlaybackExternalPlayerMode.AskEveryTime -> PlaybackExternalPlayerMode.Internal
    }

private val PlaybackTimeshiftStorageMode.label: String
    get() = when (this) {
        PlaybackTimeshiftStorageMode.Automatic -> "Automatisch"
        PlaybackTimeshiftStorageMode.Ram -> "RAM"
        PlaybackTimeshiftStorageMode.InternalStorage -> "Festplatte"
    }

private fun PlaybackTimeshiftStorageMode.nextTimeshiftStorage(): PlaybackTimeshiftStorageMode =
    when (this) {
        PlaybackTimeshiftStorageMode.Automatic -> PlaybackTimeshiftStorageMode.Ram
        PlaybackTimeshiftStorageMode.Ram -> PlaybackTimeshiftStorageMode.InternalStorage
        PlaybackTimeshiftStorageMode.InternalStorage -> PlaybackTimeshiftStorageMode.Automatic
    }

@Composable
private fun EpgSettingsPanel(
    providerRepository: ProviderRepository,
    epgSourceRepository: EpgSourceRepository,
    state: EpgSettingsState,
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    onRunGlobalRefresh: () -> Unit,
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
        EpgGlobalSettings(
            preferences = state,
            onEpgPreferencesChanged = onEpgPreferencesChanged,
            onRunGlobalRefresh = {
                onRunGlobalRefresh()
                message = "EPG Aktualisierung wurde eingeplant."
            },
            firstFocusModifier = firstFocusModifier,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(
                label = "EPG Quelle hinzufügen",
                modifier = Modifier.width(250.dp),
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
private fun EpgGlobalSettings(
    preferences: EpgSettingsState,
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    onRunGlobalRefresh: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        AdjustableSettingsRow(
            title = "Globales EPG-Aktualisierungsintervall",
            help = "Gilt nur fuer den automatischen Intervall-Refresh.",
            value = "${preferences.refreshIntervalHours} Stunden",
            onDecrease = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshIntervalHours = (preferences.refreshIntervalHours - 1).coerceAtLeast(1)),
                )
            },
            onIncrease = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshIntervalHours = (preferences.refreshIntervalHours + 1).coerceAtMost(168)),
                )
            },
            modifier = firstFocusModifier,
        )
        AdjustableSettingsRow(
            title = "EPG-Vergangenheit behalten",
            help = "Cleanup entfernt nur EPG-Programmdaten ausserhalb dieses Fensters.",
            value = "${preferences.pastRetentionDays} Tag${if (preferences.pastRetentionDays == 1) "" else "e"}",
            onDecrease = {
                onEpgPreferencesChanged(
                    preferences.copy(pastRetentionDays = (preferences.pastRetentionDays - 1).coerceAtLeast(1)),
                )
            },
            onIncrease = {
                onEpgPreferencesChanged(
                    preferences.copy(pastRetentionDays = (preferences.pastRetentionDays + 1).coerceAtMost(14)),
                )
            },
        )
        AdjustableSettingsRow(
            title = "EPG-Zukunft laden/behalten",
            help = "Quellen, Provider-Zuordnungen und manuelle Mappings bleiben erhalten.",
            value = "${preferences.futureRetentionDays} Tage",
            onDecrease = {
                onEpgPreferencesChanged(
                    preferences.copy(futureRetentionDays = (preferences.futureRetentionDays - 1).coerceAtLeast(1)),
                )
            },
            onIncrease = {
                onEpgPreferencesChanged(
                    preferences.copy(futureRetentionDays = (preferences.futureRetentionDays + 1).coerceAtMost(14)),
                )
            },
        )
        VivicastSettingsRow(
            title = "EPG beim App-Start aktualisieren",
            help = "Startet nur, wenn Hintergrundaktualisierung erlaubt ist.",
            value = if (preferences.refreshOnAppStartEnabled) "Ein" else "Aus",
            onClick = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshOnAppStartEnabled = !preferences.refreshOnAppStartEnabled),
                )
            },
        )
        VivicastSettingsRow(
            title = "EPG bei Playlist-Aenderung aktualisieren",
            help = "Separater Ausloeser neben dem Intervall.",
            value = if (preferences.refreshOnPlaylistChangeEnabled) "Ein" else "Aus",
            onClick = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshOnPlaylistChangeEnabled = !preferences.refreshOnPlaylistChangeEnabled),
                )
            },
        )
        VivicastSettingsRow(
            title = "EPG jetzt aktualisieren",
            help = "Plant eine Aktualisierung nach dem bestehenden Refresh-Plan ein.",
            value = "Starten",
            onClick = onRunGlobalRefresh,
        )
    }
}

@Composable
private fun AdjustableSettingsRow(
    title: String,
    help: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(VivicastCardSizes.SettingsRowHeight),
        contentPadding = VivicastSpacing.Space4,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1),
                modifier = Modifier.weight(1f),
            ) {
                BasicText(title, style = VivicastTypography.LabelLarge)
                BodyText(help, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                ActionPill("-", modifier = Modifier.width(64.dp), onClick = onDecrease)
                BasicText(value, style = VivicastTypography.LabelLarge)
                ActionPill("+", modifier = Modifier.width(64.dp), onClick = onIncrease)
            }
        }
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
    var epgChannelId by remember(selectedProviderId) { mutableStateOf("") }
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
        epgChannelId = selectedMapping?.epgChannelId.orEmpty()
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
            epgChannelId = epgChannelId,
            message = message,
            onSelectSource = {
                selectedSourceId = it
                onMessage(null)
            },
            onEpgChannelIdChange = { epgChannelId = it },
            onSave = {
                val providerId = selectedProviderId
                val channelId = selectedChannelId
                val sourceId = selectedSourceId
                val normalizedExternalId = epgChannelId.trim()
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
                            epgChannelId = ""
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
    epgChannelId: String,
    message: String?,
    onSelectSource: (String) -> Unit,
    onEpgChannelIdChange: (String) -> Unit,
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
                value = epgChannelId,
                placeholder = "z.B. ard.de",
                onValueChange = onEpgChannelIdChange,
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
fun DeleteEpgSourceDialog(
    source: EpgSource,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onCancel) {
        GlassPanel(
            modifier = Modifier
                .widthIn(min = 560.dp, max = 680.dp)
                .onPreviewKeyEvent {
                    if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                        onCancel()
                        true
                    } else {
                        false
                    }
                }
                .testTag(deleteEpgSourceDialogTag(source.id)),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = "EPG Quelle wirklich loeschen?",
                    body = "Diese EPG Quelle, ihre Programme und ihre Zuordnungen werden entfernt. Provider bleiben erhalten.",
                    badge = source.name,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill(
                        "Abbrechen",
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .width(150.dp)
                            .testTag(deleteEpgSourceCancelTag(source.id)),
                        selected = true,
                        onClick = onCancel,
                    )
                    ActionPill(
                        "Loeschen",
                        modifier = Modifier
                            .width(140.dp)
                            .testTag(deleteEpgSourceConfirmTag(source.id)),
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSettingsPanel(
    providerRepository: ProviderRepository,
    onTestProviderConnection: suspend (ProviderCreateRequest) -> String?,
    onProviderSaved: (String) -> Unit,
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
                    onEditorChange = {
                        editor = it
                        message = null
                    },
                    onTestConnection = {
                        val validationMessage = editor.connectionTestRequestMessage()
                        when {
                            duplicateName -> message = "Name existiert bereits."
                            validationMessage != null -> message = validationMessage
                            else -> {
                                message = "Verbindung wird gepr\u00fcft."
                                scope.launch {
                                    val errorMessage = onTestProviderConnection(editor.toConnectionTestRequest())
                                    if (errorMessage == null) {
                                        editor = editor.copy(connectionTestPassed = true)
                                        message = "Verbindung erfolgreich."
                                    } else {
                                        editor = editor.copy(connectionTestPassed = false)
                                        message = errorMessage
                                    }
                                }
                            }
                        }
                    },
                    onSave = {
                    if (duplicateName) {
                        message = "Name existiert bereits."
                        return@ProviderEditor
                    }
                    val validationMessage = editor.validationMessage(requireConnectionTest = true)
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
                            onProviderSaved(result.provider.id)
                            message = "Wiedergabeliste gespeichert. Import wurde gestartet."
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
                                message = if (enabled) "Wiedergabeliste aktiviert." else "Wiedergabeliste deaktiviert. Daten bleiben erhalten."
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
                    title = "Wiedergabelisten",
                    body = message ?: "Wiedergabelisten werden lokal konfiguriert. Zugangsdaten bleiben außerhalb der Datenbank gespeichert.",
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
                            message = "Wiedergabeliste gelöscht. Zugehörige Daten wurden entfernt."
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
            title = "Keine Wiedergabelisten",
            body = "Noch keine Wiedergabeliste vorhanden. Es wird nichts importiert.",
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
    onTestConnection: () -> Unit,
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
                title = if (editor.isEditing) "Bearbeiten" else "Wiedergabeliste",
                body = if (editor.isEditing) {
                    "Typ und ID bleiben stabil."
                } else {
                    "Zugangsdaten werden geschützt gespeichert. Kein eigener User-Agent."
                },
                badge = editor.type.label,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (duplicateName) {
            item {
                InfoPanel(
                    title = "Name existiert bereits.",
                    body = "Der Name ist bereits lokal vorhanden. Speichern ist blockiert.",
                    badge = "Warnung",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            ProviderTextField(
                label = "Name",
                value = editor.name,
                placeholder = "Name der Wiedergabeliste",
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
                        onValueChange = { onEditorChange(editor.copy(m3uUrl = it, connectionTestPassed = false)) },
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
                        onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it, connectionTestPassed = false)) },
                        secret = editor.isEditing,
                    )
                }
                item {
                    ProviderTextField(
                        label = "Benutzername",
                        value = editor.xtreamUsername,
                        placeholder = if (editor.isEditing) "Neu setzen oder leer lassen" else "Benutzername",
                        onValueChange = { onEditorChange(editor.copy(xtreamUsername = it, connectionTestPassed = false)) },
                        secret = editor.isEditing,
                    )
                }
                item {
                    ProviderTextField(
                        label = "Passwort",
                        value = editor.xtreamPassword,
                        placeholder = if (editor.isEditing) "Neu setzen oder leer lassen" else "Passwort",
                        onValueChange = { onEditorChange(editor.copy(xtreamPassword = it, connectionTestPassed = false)) },
                        secret = true,
                    )
                }
            }
        }

        if (editor.type == ProviderType.Xtream) {
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
                ActionPill(
                    label = "Verbindung testen",
                    modifier = Modifier.width(210.dp),
                    selected = editor.connectionTestPassed,
                    onClick = onTestConnection,
                )
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
fun DeleteProviderDialog(
    provider: Provider,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onCancel) {
        GlassPanel(
            modifier = Modifier
                .widthIn(min = 560.dp, max = 680.dp)
                .onPreviewKeyEvent {
                    if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                        onCancel()
                        true
                    } else {
                        false
                    }
                }
                .testTag(deleteProviderDialogTag(provider.id)),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = "Wiedergabeliste wirklich löschen?",
                    body = "Diese Aktion kann nicht rückgängig gemacht werden. Zugehörige Sender, Kategorien, Favoriten, Verlauf, Playback Progress und EPG-Zuordnungen werden gelöscht. EPG-Quellen bleiben erhalten.",
                    badge = provider.name,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill(
                        "Abbrechen",
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .width(150.dp)
                            .testTag(deleteProviderCancelTag(provider.id)),
                        selected = true,
                        onClick = onCancel,
                    )
                    ActionPill(
                        "Löschen",
                        modifier = Modifier
                            .width(140.dp)
                            .testTag(deleteProviderConfirmTag(provider.id)),
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsPanel(
    firstFocusModifier: Modifier = Modifier,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = "Hintergrundthema",
                help = "TV-taugliche dunkle Darstellung.",
                value = "Standard dunkel",
                modifier = firstFocusModifier,
            )
        }
        item {
            VivicastSettingsRow(title = "Akzentfarbe", help = "Fokus- und Warnkontrast bleiben verbindlich.", value = "Vivicast Blau")
        }
        item {
            VivicastSettingsRow(title = "Transparenz", help = "Panel- und Overlay-Lesbarkeit bleibt begrenzt.", value = "25 %")
        }
        item {
            VivicastSettingsRow(title = "Schriftgröße", help = "Größere Stufen werden später über das Designsystem verdrahtet.", value = "Mittel")
        }
        item {
            VivicastSettingsRow(title = "Animationen", help = "Fokuswechsel bleiben vorhersehbar.", value = "Normal")
        }
        item {
            VivicastSettingsRow(title = "Globale Logo-Standardreihenfolge", help = "Provider können später abweichen.", value = "Playlist")
        }
        item {
            VivicastSettingsRow(title = "Logos-Ordner", help = "Lokale Logo-Ordnerauswahl ist noch nicht aktiviert.", value = "Nicht gesetzt")
        }
        item {
            VivicastSettingsRow(title = "EPG-Darstellung", help = "Detail-Toggles werden mit dem Live-TV-EPG umgesetzt.", value = "Öffnen")
        }
    }
}

@Composable
private fun PlaceholderSettingsPanel(
    title: String,
    body: String,
    firstFocusModifier: Modifier = Modifier,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(title = title, body = body, modifier = firstFocusModifier.fillMaxWidth())
        }
    }
}

@Composable
fun ParentalControlSettingsPanel(
    state: ParentalControlSettingsState,
    onSetPin: (String) -> String?,
    onChangePin: (String, String) -> String?,
    onDisablePin: (String) -> String?,
    onProtectionChanged: (ParentalProtectionArea, Boolean) -> String? = { _, _ -> null },
    firstFocusModifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<PinDialogMode?>(null) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    fun changeProtection(area: ParentalProtectionArea, enabled: Boolean) {
        inlineError = if (state.hasPin) {
            onProtectionChanged(area, enabled)
        } else {
            "PIN zuerst setzen."
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(
                title = "Kindersicherung",
                body = if (state.hasPin) {
                    "PIN ist eingerichtet. Schutzbereiche können aktiviert werden."
                } else {
                    "PIN ist nicht gesetzt. Schutzbereiche können erst danach aktiviert werden."
                },
                modifier = firstFocusModifier.fillMaxWidth(),
            )
        }
        inlineError?.let { message ->
            item {
                InfoPanel(
                    title = "Hinweis",
                    body = message,
                )
            }
        }
        item {
            VivicastSettingsRow(
                title = if (state.hasPin) "PIN ändern" else "PIN setzen",
                help = "Vier Ziffern, verdeckte Eingabe über die Systemtastatur.",
                value = if (state.hasPin) "Ändern" else "Setzen",
                onClick = { dialog = if (state.hasPin) PinDialogMode.Change else PinDialogMode.Set },
            )
        }
        if (state.hasPin) {
            item {
                VivicastSettingsRow(
                    title = "Kindersicherung deaktivieren",
                    help = "Erfordert aktuelle PIN und Bestätigung.",
                    value = "Deaktivieren",
                    onClick = { dialog = PinDialogMode.Disable },
                )
            }
        }
        protectionAreaItem(
            title = "Einstellungen schützen",
            help = "Schützt sensible Einstellungen mit PIN.",
            enabled = state.protectSettings,
            onClick = { changeProtection(ParentalProtectionArea.Settings, !state.protectSettings) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = "Filme schützen",
            help = "Öffnet Filme erst nach PIN-Freigabe.",
            enabled = state.protectMovies,
            onClick = { changeProtection(ParentalProtectionArea.Movies, !state.protectMovies) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = "Serien schützen",
            help = "Öffnet Serien erst nach PIN-Freigabe.",
            enabled = state.protectSeries,
            onClick = { changeProtection(ParentalProtectionArea.Series, !state.protectSeries) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = "Inhalte ab 18 schützen",
            help = "Blendet geschützte Erwachseneninhalte ohne Freigabe aus.",
            enabled = state.protectAdultContent,
            onClick = { changeProtection(ParentalProtectionArea.AdultContent, !state.protectAdultContent) },
            hasPin = state.hasPin,
        )
        item {
            VivicastSettingsRow(
                title = "Sitzungsfreigabe sperren",
                help = "Aktive Freigaben bleiben im Speicher und werden nicht gesichert.",
                value = "Keine Freigabe",
            )
        }
    }

    dialog?.let { mode ->
        PinDialog(
            mode = mode,
            onCancel = { dialog = null },
            onConfirm = { currentPin, newPin ->
                val error = when (mode) {
                    PinDialogMode.Set -> onSetPin(newPin)
                    PinDialogMode.Change -> onChangePin(currentPin, newPin)
                    PinDialogMode.Disable -> onDisablePin(currentPin)
                }
                if (error == null) {
                    dialog = null
                }
                error
            },
        )
    }
}

private fun LazyListScope.protectionAreaItem(
    title: String,
    help: String,
    enabled: Boolean,
    onClick: () -> Unit,
    hasPin: Boolean,
) {
    item {
        VivicastSettingsRow(
            title = title,
            help = help,
            value = if (!hasPin) {
                "PIN erforderlich"
            } else if (enabled) {
                "Ein"
            } else {
                "Aus"
            },
            onClick = onClick,
        )
    }
}

private enum class PinDialogMode { Set, Change, Disable }

@Composable
private fun PinDialog(
    mode: PinDialogMode,
    onCancel: () -> Unit,
    onConfirm: (currentPin: String, newPin: String) -> String?,
) {
    val firstFocusRequester = remember { FocusRequester() }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeatPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val title = when (mode) {
        PinDialogMode.Set -> "PIN setzen"
        PinDialogMode.Change -> "PIN ändern"
        PinDialogMode.Disable -> "Kindersicherung deaktivieren"
    }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onCancel) {
        GlassPanel(
            modifier = Modifier
                .widthIn(min = 560.dp, max = 680.dp)
                .testTag(pinDialogTag()),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                SectionTitle(title)
                if (mode != PinDialogMode.Set) {
                    PinTextField(
                        label = "Aktuelle PIN",
                        value = currentPin,
                        onValueChange = { currentPin = it.pinInput() },
                        modifier = Modifier
                            .focusRequester(firstFocusRequester)
                            .testTag(pinCurrentFieldTag()),
                    )
                }
                if (mode != PinDialogMode.Disable) {
                    PinTextField(
                        label = "Neue PIN",
                        value = newPin,
                        onValueChange = { newPin = it.pinInput() },
                        modifier = Modifier
                            .then(if (mode == PinDialogMode.Set) Modifier.focusRequester(firstFocusRequester) else Modifier)
                            .testTag(pinNewFieldTag()),
                    )
                    PinTextField(
                        label = "Neue PIN wiederholen",
                        value = repeatPin,
                        onValueChange = { repeatPin = it.pinInput() },
                        modifier = Modifier.testTag(pinRepeatFieldTag()),
                    )
                }
                error?.let { BodyText(it, color = VivicastColors.Error, maxLines = 2) }
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    ActionPill(
                        label = "Abbrechen",
                        modifier = Modifier.testTag(pinCancelTag()),
                        onClick = onCancel,
                    )
                    ActionPill(
                        label = when (mode) {
                            PinDialogMode.Set -> "Speichern"
                            PinDialogMode.Change -> "Speichern"
                            PinDialogMode.Disable -> "Deaktivieren"
                        },
                        selected = mode == PinDialogMode.Disable,
                        modifier = Modifier.testTag(pinConfirmTag()),
                        onClick = {
                            error = validatePinDialog(mode, currentPin, newPin, repeatPin)
                                ?: onConfirm(currentPin, newPin)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = VivicastTypography.LabelLarge.copy(color = VivicastColors.TextPrimary),
            visualTransformation = PasswordVisualTransformation(),
            modifier = modifier
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
                            text = "4 Ziffern",
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

private fun validatePinDialog(
    mode: PinDialogMode,
    currentPin: String,
    newPin: String,
    repeatPin: String,
): String? {
    if (mode != PinDialogMode.Set && currentPin.length != PIN_LENGTH) {
        return "Aktuelle PIN vollständig eingeben."
    }
    if (mode != PinDialogMode.Disable && newPin.length != PIN_LENGTH) {
        return "Neue PIN muss vier Ziffern haben."
    }
    if (mode != PinDialogMode.Disable && newPin != repeatPin) {
        return "Neue PIN stimmt nicht überein."
    }
    return null
}

private fun String.pinInput(): String =
    filter(Char::isDigit).take(PIN_LENGTH)

fun pinDialogTag(): String = "pin-dialog"
fun pinCurrentFieldTag(): String = "pin-current"
fun pinNewFieldTag(): String = "pin-new"
fun pinRepeatFieldTag(): String = "pin-repeat"
fun pinCancelTag(): String = "pin-cancel"
fun pinConfirmTag(): String = "pin-confirm"

private const val PIN_LENGTH = 4

@Composable
internal fun BackupSettingsPanel(
    onExportStandardBackup: () -> Unit = {},
    onImportStandardBackup: () -> Unit = {},
    onExportEncryptedFullBackup: (String) -> Unit = {},
    onImportEncryptedFullBackup: (String) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    var pendingFullAction by remember { mutableStateOf<BackupFullAction?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(
                title = "Backup",
                body = "Standard-Backup speichert keine Zugangsdaten. Verschlüsselte Vollbackups enthalten Zugangsdaten nur mit Backup-Passphrase.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            VivicastSettingsRow(
                title = "Backup exportieren",
                help = "Standard-Backup ohne geheime Zugangswerte.",
                value = "Auswählen",
                onClick = onExportStandardBackup,
                modifier = firstFocusModifier,
            )
        }
        item {
            VivicastSettingsRow(
                title = "Backup importieren",
                help = "Restore ersetzt den Backup-Umfang nach Validierung und Bestätigung.",
                value = "Auswählen",
                onClick = onImportStandardBackup,
            )
        }
        item {
            VivicastSettingsRow(
                title = "Vollbackup exportieren",
                help = "Verschlüsselt mit Backup-Passphrase und enthält Quellen-Zugangsdaten.",
                value = "Passphrase",
                onClick = { pendingFullAction = BackupFullAction.Export },
            )
        }
        item {
            VivicastSettingsRow(
                title = "Vollbackup importieren",
                help = "Restore erfordert die Backup-Passphrase und ersetzt den Backup-Umfang.",
                value = "Passphrase",
                onClick = { pendingFullAction = BackupFullAction.Import },
            )
        }
        item {
            VivicastSettingsRow(title = "Backup-Ziel", help = "SMB und Google Drive speichern keine Zugangswerte im Standard-Backup.", value = "Lokaler Speicher")
        }
        item {
            VivicastSettingsRow(title = "Vorhandene Backups", help = "Verwaltung und Loeschen brauchen spaeter eine Bestaetigung.", value = "Vorbereitet")
        }
    }

    pendingFullAction?.let { action ->
        FullBackupPassphraseDialog(
            action = action,
            onCancel = { pendingFullAction = null },
            onConfirm = { passphrase ->
                pendingFullAction = null
                when (action) {
                    BackupFullAction.Export -> onExportEncryptedFullBackup(passphrase)
                    BackupFullAction.Import -> onImportEncryptedFullBackup(passphrase)
                }
            },
        )
    }
}

@Composable
private fun FullBackupPassphraseDialog(
    action: BackupFullAction,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onCancel) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth().testTag(fullBackupPassphraseDialogTag()),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = action.title,
                    body = error ?: "Backup-Passphrase eingeben. Sie wird nicht gespeichert und schützt enthaltene Zugangsdaten.",
                    modifier = Modifier.fillMaxWidth(),
                )
                BasicTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag(fullBackupPassphraseFieldTag()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Abbrechen", modifier = Modifier.width(150.dp).testTag(fullBackupPassphraseCancelTag()), onClick = onCancel)
                    ActionPill(action.confirmLabel, modifier = Modifier.width(180.dp).testTag(fullBackupPassphraseConfirmTag()), selected = true) {
                        val value = passphrase.trim()
                        if (value.isBlank()) {
                            error = "Passphrase fehlt."
                        } else {
                            onConfirm(value)
                        }
                    }
                }
            }
        }
    }
}

private enum class BackupFullAction(
    val title: String,
    val confirmLabel: String,
) {
    Export("Vollbackup exportieren", "Exportieren"),
    Import("Vollbackup importieren", "Importieren"),
}

fun fullBackupPassphraseDialogTag(): String = "full-backup-passphrase-dialog"
fun fullBackupPassphraseFieldTag(): String = "full-backup-passphrase-field"
fun fullBackupPassphraseCancelTag(): String = "full-backup-passphrase-cancel"
fun fullBackupPassphraseConfirmTag(): String = "full-backup-passphrase-confirm"

@Composable
private fun AboutSettingsPanel(
    state: AboutAppState,
    diagnosticsSettingsState: DiagnosticsSettingsState,
    onDiagnosticsSettingsChanged: (DiagnosticsSettingsState) -> Unit,
    onExportDiagnostics: () -> Unit,
    onCopySupportInformation: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    val toggleDiagnostics = {
        onDiagnosticsSettingsChanged(
            diagnosticsSettingsState.copy(
                diagnosticsLoggingEnabled = !diagnosticsSettingsState.diagnosticsLoggingEnabled,
            ),
        )
    }
    val decreaseRetention = {
        if (diagnosticsSettingsState.diagnosticsLoggingEnabled) {
            onDiagnosticsSettingsChanged(
                diagnosticsSettingsState.copy(retentionDays = (diagnosticsSettingsState.retentionDays - 1).coerceAtLeast(1)),
            )
        }
    }
    val increaseRetention = {
        if (diagnosticsSettingsState.diagnosticsLoggingEnabled) {
            onDiagnosticsSettingsChanged(
                diagnosticsSettingsState.copy(retentionDays = (diagnosticsSettingsState.retentionDays + 1).coerceAtMost(7)),
            )
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = "App-Informationen",
                help = "App-Version, Paketname, Datenbank-Version und Gerätedaten.",
                value = "Öffnen",
                modifier = firstFocusModifier,
            )
        }
        item {
            VivicastSettingsRow(title = "Versionsinformationen kopieren", help = "Kopiert nur nicht-private technische Informationen.", value = "Kopieren")
        }
        item {
            VivicastSettingsRow(title = "App-Version", help = "Installierte App-Version.", value = state.appVersion)
        }
        item {
            VivicastSettingsRow(title = "Paketname", help = "Android Application ID.", value = state.packageName)
        }
        item {
            VivicastSettingsRow(title = "Datenbank-Version", help = "Lokales Room-Schema.", value = state.databaseVersion.toString())
        }
        item {
            VivicastSettingsRow(title = "Android-Version", help = "Systemversion des Geraets.", value = state.androidVersion)
        }
        item {
            VivicastSettingsRow(title = "Geraetemodell", help = "Nicht-private technische Supportinformation.", value = state.deviceModel)
        }
        item {
            InfoPanel(
                title = "Diagnose und Support",
                body = "Allgemeine Supportdaten duerfen kopiert werden. Loginhalt wird nicht angezeigt und nicht kopiert.",
                badge = "Support",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            VivicastSettingsRow(
                title = "Diagnoseprotokollierung",
                help = "Schreibt nur bereinigte technische Ereignisse in privaten App-Speicher.",
                value = if (diagnosticsSettingsState.diagnosticsLoggingEnabled) "Ein" else "Aus",
                modifier = Modifier.onTvCenterClick(toggleDiagnostics),
                onClick = toggleDiagnostics,
            )
        }
        item {
            AdjustableSettingsRow(
                title = "Aufbewahrungsdauer",
                help = if (diagnosticsSettingsState.diagnosticsLoggingEnabled) {
                    "Begrenzt interne Diagnosesitzungen auf 1 bis 7 Tage."
                } else {
                    "Bleibt sichtbar, ist bei ausgeschalteter Diagnoseprotokollierung nicht aenderbar."
                },
                value = "${diagnosticsSettingsState.retentionDays.coerceIn(1, 7)} Tag${if (diagnosticsSettingsState.retentionDays == 1) "" else "e"}",
                onDecrease = decreaseRetention,
                onIncrease = increaseRetention,
            )
        }
        item {
            VivicastSettingsRow(
                title = "Diagnoseprotokoll exportieren",
                help = "Erstellt ein ZIP mit vivicast-diagnostics.log und diagnostics-metadata.json.",
                value = "Exportieren",
                modifier = Modifier.onTvCenterClick(onExportDiagnostics),
                onClick = onExportDiagnostics,
            )
        }
        item {
            VivicastSettingsRow(
                title = "Support-Informationen kopieren",
                help = "Kopiert nur die sichtbaren nicht-privaten technischen Informationen.",
                value = "Kopieren",
                modifier = Modifier.onTvCenterClick(onCopySupportInformation),
                onClick = onCopySupportInformation,
            )
        }
        item {
            VivicastSettingsRow(title = "Lizenzhinweise", help = "Lokale rechtliche Hinweise.", value = "Öffnen")
        }
        item {
            VivicastSettingsRow(title = "Datenschutzinformationen", help = "Lokale Datenschutzinformationen.", value = "Öffnen")
        }
        item {
            VivicastSettingsRow(title = "Drittanbieter-Lizenzen", help = "Per D-Pad lesbare Lizenzliste.", value = "Öffnen")
        }
    }
}

@Composable
private fun MaintenanceSettingsPanel(
    cacheSettingsState: CacheSettingsState,
    onClearCache: () -> Unit,
    onClearHistory: (HistoryClearTarget) -> Unit,
    onReloadCacheStats: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<MaintenanceAction?>(null) }

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
                value = formatCacheSize(cacheSettingsState.totalSizeBytes),
                modifier = firstFocusModifier,
                onClick = {
                    onReloadCacheStats()
                    message = "Cache Informationen wurden aktualisiert."
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = "Cache leeren",
                help = "Entfernt lokal gespeicherte Medienbilder. Providerdaten bleiben erhalten.",
                value = "Ausführen",
                onClick = {
                    pendingAction = MaintenanceAction.ClearCache
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = "Verlauf l\u00f6schen",
                help = "L\u00f6scht Live-TV-Verlauf, Filme, Serien und Suchverlauf.",
                value = "Alles",
                onClick = {
                    pendingAction = MaintenanceAction.ClearAllHistory
                },
            )
        }

        items(MaintenanceAction.SelectiveHistoryActions) { action ->
            VivicastSettingsRow(
                title = action.rowTitle,
                help = action.rowHelp,
                value = "L\u00f6schen",
                onClick = {
                    pendingAction = action
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

    pendingAction?.let { action ->
        MaintenanceConfirmDialog(
            action = action,
            onCancel = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                when (action) {
                    MaintenanceAction.ClearCache -> {
                        onClearCache()
                        message = "Cache wurde geleert."
                    }
                    else -> {
                        onClearHistory(requireNotNull(action.historyTarget))
                        message = "Verlauf wurde gel\u00f6scht."
                    }
                }
            },
        )
    }
}

@Composable
private fun MaintenanceConfirmDialog(
    action: MaintenanceAction,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth().testTag(action.dialogTag),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = action.confirmTitle,
                    body = action.body,
                    badge = "Best\u00e4tigung",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Abbrechen", modifier = Modifier.width(150.dp), onClick = onCancel)
                    ActionPill(action.confirmLabel, modifier = Modifier.width(170.dp), onClick = onConfirm)
                }
            }
        }
    }
}

private enum class MaintenanceAction(
    val rowTitle: String,
    val rowHelp: String,
    val confirmTitle: String,
    val body: String,
    val confirmLabel: String,
    val dialogTag: String,
    val historyTarget: HistoryClearTarget? = null,
) {
    ClearCache(
        rowTitle = "Cache leeren",
        rowHelp = "Entfernt lokal gespeicherte Medienbilder.",
        confirmTitle = "Cache leeren?",
        body = "Es werden nur lokal gespeicherte Medienbilder gel\u00f6scht. Providerdaten, Verlauf, Favoriten, EPG und Zugangsdaten bleiben erhalten.",
        confirmLabel = "Cache leeren",
        dialogTag = "settings-clear-cache-dialog",
    ),
    ClearLiveTvHistory(
        rowTitle = "Live-TV-Verlauf l\u00f6schen",
        rowHelp = "L\u00f6scht zuletzt gesehene Live-TV-Sender.",
        confirmTitle = "Live-TV-Verlauf l\u00f6schen?",
        body = "Der Live-TV-Verlauf wird gel\u00f6scht. Filme, Serien, Suche, Providerdaten und Favoriten bleiben erhalten.",
        confirmLabel = "L\u00f6schen",
        dialogTag = "settings-clear-live-tv-history-dialog",
        historyTarget = HistoryClearTarget.LiveTv,
    ),
    ClearMovieHistory(
        rowTitle = "Filmverlauf l\u00f6schen",
        rowHelp = "L\u00f6scht Film-Wiedergabefortschritt.",
        confirmTitle = "Filmverlauf l\u00f6schen?",
        body = "Filmverlauf und Film-Wiedergabefortschritt werden gel\u00f6scht. Andere Verlaeufe bleiben erhalten.",
        confirmLabel = "L\u00f6schen",
        dialogTag = "settings-clear-movie-history-dialog",
        historyTarget = HistoryClearTarget.Movies,
    ),
    ClearSeriesHistory(
        rowTitle = "Serienverlauf l\u00f6schen",
        rowHelp = "L\u00f6scht Serien- und Episoden-Wiedergabefortschritt.",
        confirmTitle = "Serienverlauf l\u00f6schen?",
        body = "Serienverlauf und Episoden-Wiedergabefortschritt werden gel\u00f6scht. Andere Verlaeufe bleiben erhalten.",
        confirmLabel = "L\u00f6schen",
        dialogTag = "settings-clear-series-history-dialog",
        historyTarget = HistoryClearTarget.Series,
    ),
    ClearSearchHistory(
        rowTitle = "Suchverlauf l\u00f6schen",
        rowHelp = "L\u00f6scht lokale Suchbegriffe.",
        confirmTitle = "Suchverlauf l\u00f6schen?",
        body = "Der Suchverlauf wird gel\u00f6scht. Wiedergabeverlauf, Providerdaten und Favoriten bleiben erhalten.",
        confirmLabel = "L\u00f6schen",
        dialogTag = "settings-clear-search-history-dialog",
        historyTarget = HistoryClearTarget.Search,
    ),
    ClearAllHistory(
        rowTitle = "Gesamten Verlauf l\u00f6schen",
        rowHelp = "L\u00f6scht Live-TV, Filme, Serien und Suche.",
        confirmTitle = "Gesamten Verlauf l\u00f6schen?",
        body = "Live-TV-Verlauf, Film- und Serienfortschritt sowie Suchverlauf werden gel\u00f6scht. Providerdaten und Favoriten bleiben erhalten.",
        confirmLabel = "Alles l\u00f6schen",
        dialogTag = "settings-clear-history-dialog",
        historyTarget = HistoryClearTarget.All,
    );

    companion object {
        val SelectiveHistoryActions = listOf(
            ClearLiveTvHistory,
            ClearMovieHistory,
            ClearSeriesHistory,
            ClearSearchHistory,
        )
    }
}

fun deleteProviderDialogTag(providerId: String): String = "settings-delete-provider-dialog-$providerId"
fun deleteProviderCancelTag(providerId: String): String = "settings-delete-provider-cancel-$providerId"
fun deleteProviderConfirmTag(providerId: String): String = "settings-delete-provider-confirm-$providerId"
fun deleteEpgSourceDialogTag(sourceId: String): String = "settings-delete-epg-source-dialog-$sourceId"
fun deleteEpgSourceCancelTag(sourceId: String): String = "settings-delete-epg-source-cancel-$sourceId"
fun deleteEpgSourceConfirmTag(sourceId: String): String = "settings-delete-epg-source-confirm-$sourceId"

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
    val connectionTestPassed: Boolean,
) {
    val isEditing: Boolean get() = providerId != null

    fun validationMessage(requireConnectionTest: Boolean): String? {
        if (name.isBlank()) return "Name fehlt."
        if (type == ProviderType.Xtream && !includeLiveTv && !includeMovies && !includeSeries) {
            return "Mindestens ein Inhaltstyp muss aktiv sein."
        }
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
        if (requireConnectionTest && !connectionTestPassed) return "Verbindungstest fehlt."
        return null
    }

    fun connectionTestRequestMessage(): String? {
        validationMessage(requireConnectionTest = false)?.let { return it }
        return when (type) {
            ProviderType.M3u -> if (m3uUrl.isBlank()) "M3U URL fehlt." else null
            ProviderType.Xtream -> when {
                xtreamServerUrl.isBlank() -> "Xtream Server fehlt."
                xtreamUsername.isBlank() -> "Xtream Benutzername fehlt."
                xtreamPassword.isBlank() -> "Xtream Passwort fehlt."
                else -> null
            }
        }
    }

    fun toConnectionTestRequest(): ProviderCreateRequest =
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
                connectionTestPassed = false,
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
                connectionTestPassed = true,
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
        ProviderStatus.ActiveWithPartialErrors -> "Aktiv mit Teilfehlern"
        ProviderStatus.Refreshing -> "Aktualisierung"
        ProviderStatus.ConnectionError -> "Verbindungsfehler"
        ProviderStatus.InvalidCredentials -> "Ungültig"
        ProviderStatus.Expired -> "Abgelaufen"
        ProviderStatus.Disabled -> "Deaktiviert"
        ProviderStatus.CredentialsRequired -> "Zugangsdaten erforderlich"
    }

private val ProviderStatus.tone: Color
    get() = when (this) {
        ProviderStatus.Active -> VivicastColors.Success
        ProviderStatus.ActiveWithPartialErrors -> VivicastColors.Warning
        ProviderStatus.Refreshing -> VivicastColors.Info
        ProviderStatus.ConnectionError -> VivicastColors.Warning
        ProviderStatus.InvalidCredentials -> VivicastColors.Error
        ProviderStatus.Expired -> VivicastColors.Warning
        ProviderStatus.Disabled -> VivicastColors.SurfaceHigh
        ProviderStatus.CredentialsRequired -> VivicastColors.Error
    }

private val Provider.importSummary: String
    get() = listOfNotNull(
        "Live-TV".takeIf { includeLiveTv },
        "Filme".takeIf { includeMovies },
        "Serien".takeIf { includeSeries },
    ).joinToString(" | ") + " | alle $refreshIntervalHours h"
