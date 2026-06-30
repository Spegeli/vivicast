package com.vivicast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastBorders
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogError
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastTextField
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
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.data.provider.isAutomaticallyRefreshable
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.domain.model.EpgSource
import androidx.compose.ui.res.stringResource
import com.vivicast.tv.core.designsystem.R
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class GeneralSettingsState(
    val launchOnBoot: Boolean = false,
    val doubleBackToExit: Boolean = true,
    val backgroundRefreshEnabled: Boolean = true,
    val rememberSorting: Boolean = true,
    val appLanguage: SettingsLanguage = SettingsLanguage.System,
    val globalUserAgent: String = "Vivicast/1.0",
)

data class AppearanceSettingsState(
    val themeMode: SettingsThemeMode = SettingsThemeMode.StandardDark,
    val accentColor: SettingsAccentColor = SettingsAccentColor.Blue,
    val transparency: SettingsTransparency = SettingsTransparency.Percent25,
    val fontScale: SettingsFontScale = SettingsFontScale.Medium,
    val animationSpeed: SettingsAnimationSpeed = SettingsAnimationSpeed.Normal,
)

enum class SettingsLanguage {
    System,
    German,
    English,
}

enum class SettingsThemeMode {
    StandardDark,
    HighContrastDark,
    AmoledDark,
}

enum class SettingsAccentColor {
    Blue,
}

enum class SettingsTransparency {
    Percent0,
    Percent25,
    Percent50,
}

enum class SettingsFontScale {
    Small,
    Medium,
    Large,
    ExtraLarge,
}

enum class SettingsAnimationSpeed {
    Off,
    Fast,
    Normal,
    Slow,
}

data class CacheSettingsState(
    val totalSizeBytes: Long = 0L,
    val fileCount: Int = 0,
)

data class BackupSettingsState(
    val target: BackupTargetMode = BackupTargetMode.LocalStorage,
    val lastBackupAtMillis: Long? = null,
)

enum class BackupTargetMode {
    LocalStorage,
    Smb,
    GoogleDrive,
}

data class EpgSettingsState(
    val pastRetentionDays: Int = 1,
    val futureRetentionDays: Int = 7,
    val refreshIntervalHours: Int = 24,
    val refreshOnAppStartEnabled: Boolean = true,
    val refreshOnPlaylistChangeEnabled: Boolean = true,
)

data class PlaybackSettingsState(
    val bufferSize: PlaybackBufferSizeMode = PlaybackBufferSizeMode.Medium,
    val audioDecoder: PlaybackDecoderMode = PlaybackDecoderMode.Hardware,
    val videoDecoder: PlaybackDecoderMode = PlaybackDecoderMode.Hardware,
    val afrEnabled: Boolean = false,
    val preferredAudioLanguage: PlaybackAudioLanguage = PlaybackAudioLanguage.SystemDefault,
    val preferredSubtitleLanguage: PlaybackSubtitleLanguage = PlaybackSubtitleLanguage.Off,
    val audioPassthroughEnabled: Boolean = false,
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

enum class PlaybackBufferSizeMode {
    Off,
    Small,
    Medium,
    Large,
    ExtraLarge,
}

enum class PlaybackDecoderMode {
    Hardware,
    Software,
}

enum class PlaybackAudioLanguage {
    SystemDefault,
    German,
    English,
    Original,
}

enum class PlaybackSubtitleLanguage {
    Off,
    SystemDefault,
    German,
    English,
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

@Composable
private fun settingsSectionsList() = listOf(
    stringResource(R.string.settings_section_general),
    stringResource(R.string.settings_section_playlists),
    stringResource(R.string.settings_section_epg),
    stringResource(R.string.settings_section_appearance),
    stringResource(R.string.settings_section_playback),
    stringResource(R.string.settings_section_parental),
    stringResource(R.string.settings_section_cache),
    stringResource(R.string.settings_section_backup),
    stringResource(R.string.settings_section_about),
)

@Composable
fun SettingsRoute(
    providerRepository: ProviderRepository,
    epgSourceRepository: EpgSourceRepository,
    generalSettingsState: GeneralSettingsState,
    appearanceSettingsState: AppearanceSettingsState = AppearanceSettingsState(),
    epgSettingsState: EpgSettingsState,
    playbackSettingsState: PlaybackSettingsState,
    parentalControlSettingsState: ParentalControlSettingsState = ParentalControlSettingsState(),
    cacheSettingsState: CacheSettingsState,
    backupSettingsState: BackupSettingsState = BackupSettingsState(),
    aboutAppState: AboutAppState,
    diagnosticsSettingsState: DiagnosticsSettingsState = DiagnosticsSettingsState(),
    initialSelectedSection: String? = null,
    onTestProviderConnection: suspend (ProviderCreateRequest) -> String?,
    onPickM3uFile: ((String, String) -> Unit) -> Unit = {},
    onProviderSaved: (String) -> Unit,
    onSelectedSectionChanged: (String) -> Unit = {},
    onLaunchOnBootChanged: (Boolean) -> Unit = {},
    onDoubleBackToExitChanged: (Boolean) -> Unit = {},
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onRememberSortingChanged: (Boolean) -> Unit,
    onLanguageChanged: (SettingsLanguage) -> Unit = {},
    onGlobalUserAgentChanged: (String) -> Unit = {},
    onAppearanceSettingsChanged: (AppearanceSettingsState) -> Unit = {},
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    onPlaybackPreferencesChanged: (PlaybackSettingsState) -> Unit,
    onSetPin: (String) -> String? = { null },
    onChangePin: (String, String) -> String? = { _, _ -> null },
    onDisablePin: (String) -> String? = { null },
    onProtectionChanged: (ParentalProtectionArea, Boolean) -> String? = { _, _ -> null },
    onExportStandardBackup: () -> Unit = {},
    onImportStandardBackup: () -> Unit = {},
    onBackupSettingsChanged: (BackupSettingsState) -> Unit = {},
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
    val settingsSections = settingsSectionsList()
    val mainSections = remember(settingsSections) { settingsSections.dropLast(1) }
    val sectionGeneral = stringResource(R.string.settings_section_general)
    val sectionPlaylists = stringResource(R.string.settings_section_playlists)
    val sectionEpg = stringResource(R.string.settings_section_epg)
    val sectionAppearance = stringResource(R.string.settings_section_appearance)
    val sectionPlayback = stringResource(R.string.settings_section_playback)
    val sectionParental = stringResource(R.string.settings_section_parental)
    val sectionCache = stringResource(R.string.settings_section_cache)
    val sectionBackup = stringResource(R.string.settings_section_backup)
    val sectionAbout = stringResource(R.string.settings_section_about)
    var selectedSection by remember(initialSelectedSection) {
        mutableStateOf(initialSelectedSection?.takeIf { it in settingsSections } ?: sectionGeneral)
    }
    val detailFocusRequester = remember { FocusRequester() }
    var pendingDetailFocus by remember { mutableStateOf(false) }
    val sectionFocusRequesters = remember(settingsSections) {
        settingsSections.associateWith { FocusRequester() }
    }
    val sectionsWithDetailFocus = remember(settingsSections) { settingsSections.toSet() }
    val selectedSectionFocusRequester = sectionFocusRequesters[selectedSection] ?: FocusRequester.Default
    val detailFirstFocusModifier = Modifier
        .focusRequester(detailFocusRequester)
        .focusProperties { left = selectedSectionFocusRequester }
    val selectSection: (String) -> Unit = { section ->
        selectedSection = section
        onSelectedSectionChanged(section)
    }

    LaunchedEffect(Unit) {
        onReloadCacheStats()
        awaitFrame()
        selectedSectionFocusRequester.requestFocus()
    }
    LaunchedEffect(selectedSection, pendingDetailFocus) {
        if (pendingDetailFocus) {
            detailFocusRequester.requestFocus()
            pendingDetailFocus = false
        }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
            modifier = Modifier.fillMaxSize(),
        ) {
            GlassPanel(
                modifier = Modifier.weight(0.26f).fillMaxSize(),
                contentPadding = VivicastSpacing.Space3,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                ) {
                    SettingsPanelTitle(stringResource(R.string.settings_title))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1),
                        contentPadding = PaddingValues(top = VivicastSpacing.Space2),
                    ) {
                        items(mainSections) { section ->
                            FocusPanel(
                                selected = section == selectedSection,
                                onClick = {
                                    selectSection(section)
                                    pendingDetailFocus = true
                                },
                                onFocused = { selectSection(section) },
                                modifier = Modifier
                                    .focusRequester(sectionFocusRequesters.getValue(section))
                                    .fillMaxWidth()
                                    .then(
                                        if (section in sectionsWithDetailFocus) {
                                            Modifier.focusProperties { right = detailFocusRequester }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentPadding = VivicastSpacing.Space2,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsSectionIcon(section = section, selected = section == selectedSection)
                                    BasicText(
                                        text = section,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                                    )
                                }
                            }
                        }
                    }
                    FocusPanel(
                        selected = sectionAbout == selectedSection,
                        onClick = {
                            selectSection(sectionAbout)
                            pendingDetailFocus = true
                        },
                        onFocused = { selectSection(sectionAbout) },
                        modifier = Modifier
                            .focusRequester(sectionFocusRequesters.getValue(sectionAbout))
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true)
                            .focusProperties { right = detailFocusRequester },
                        contentPadding = VivicastSpacing.Space2,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsSectionIcon(section = sectionAbout, selected = sectionAbout == selectedSection)
                            BasicText(
                                text = sectionAbout,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                            )
                        }
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.weight(0.78f).fillMaxSize(),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    SettingsPanelTitle(selectedSection)
                    when (selectedSection) {
                        sectionGeneral -> GeneralSettingsPanel(
                            state = generalSettingsState,
                            onLaunchOnBootChanged = onLaunchOnBootChanged,
                            onDoubleBackToExitChanged = onDoubleBackToExitChanged,
                            onBackgroundRefreshChanged = onBackgroundRefreshChanged,
                            onRememberSortingChanged = onRememberSortingChanged,
                            onLanguageChanged = onLanguageChanged,
                            onGlobalUserAgentChanged = onGlobalUserAgentChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionPlaylists -> ProviderSettingsPanel(
                            providerRepository = providerRepository,
                            onTestProviderConnection = onTestProviderConnection,
                            onPickM3uFile = onPickM3uFile,
                            onProviderSaved = onProviderSaved,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionEpg -> EpgSettingsPanel(
                            providerRepository = providerRepository,
                            epgSourceRepository = epgSourceRepository,
                            state = epgSettingsState,
                            onEpgPreferencesChanged = onEpgPreferencesChanged,
                            onRunGlobalRefresh = onRunGlobalRefresh,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionAppearance -> AppearanceSettingsPanel(
                            state = appearanceSettingsState,
                            onAppearanceSettingsChanged = onAppearanceSettingsChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionPlayback -> PlaybackSettingsPanel(
                            state = playbackSettingsState,
                            onPlaybackPreferencesChanged = onPlaybackPreferencesChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionParental -> ParentalControlSettingsPanel(
                            state = parentalControlSettingsState,
                            onSetPin = onSetPin,
                            onChangePin = onChangePin,
                            onDisablePin = onDisablePin,
                            onProtectionChanged = onProtectionChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionCache -> MaintenanceSettingsPanel(
                            cacheSettingsState = cacheSettingsState,
                            onClearCache = onClearCache,
                            onClearHistory = onClearHistory,
                            onReloadCacheStats = onReloadCacheStats,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionBackup -> BackupSettingsPanel(
                            state = backupSettingsState,
                            onBackupSettingsChanged = onBackupSettingsChanged,
                            onExportStandardBackup = onExportStandardBackup,
                            onImportStandardBackup = onImportStandardBackup,
                            onExportEncryptedFullBackup = onExportEncryptedFullBackup,
                            onImportEncryptedFullBackup = onImportEncryptedFullBackup,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionAbout -> AboutSettingsPanel(
                            state = aboutAppState,
                            diagnosticsSettingsState = diagnosticsSettingsState,
                            onDiagnosticsSettingsChanged = onDiagnosticsSettingsChanged,
                            onExportDiagnostics = onExportDiagnostics,
                            onCopySupportInformation = onCopySupportInformation,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        else -> InfoPanel(
                            title = selectedSection,
                            body = stringResource(R.string.settings_no_options),
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
    val cycleBufferSize = {
        onPlaybackPreferencesChanged(state.copy(bufferSize = state.bufferSize.nextBufferSize()))
    }
    val cycleAudioDecoder = {
        onPlaybackPreferencesChanged(state.copy(audioDecoder = state.audioDecoder.nextDecoderMode()))
    }
    val cycleVideoDecoder = {
        onPlaybackPreferencesChanged(state.copy(videoDecoder = state.videoDecoder.nextDecoderMode()))
    }
    val toggleAfr = {
        onPlaybackPreferencesChanged(state.copy(afrEnabled = !state.afrEnabled))
    }
    val cycleAudioLanguage = {
        onPlaybackPreferencesChanged(state.copy(preferredAudioLanguage = state.preferredAudioLanguage.nextAudioLanguage()))
    }
    val cycleSubtitleLanguage = {
        onPlaybackPreferencesChanged(
            state.copy(preferredSubtitleLanguage = state.preferredSubtitleLanguage.nextSubtitleLanguage()),
        )
    }
    val toggleAudioPassthrough = {
        onPlaybackPreferencesChanged(state.copy(audioPassthroughEnabled = !state.audioPassthroughEnabled))
    }
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
            VivicastSettingsRow(
                title = stringResource(R.string.settings_buffer_size),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.bufferSize.label(),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("buffer") },
                onClick = cycleBufferSize,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_decoder),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.audioDecoder.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("speaker") },
                onClick = cycleAudioDecoder,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_video_decoder),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.videoDecoder.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("film") },
                onClick = cycleVideoDecoder,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_afr),
                help = stringResource(R.string.settings_help_afr),
                value = if (state.afrEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("display") },
                onClick = toggleAfr,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_external_player),
                help = stringResource(R.string.settings_help_external_player),
                value = state.externalPlayer.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("external") },
                onClick = cycleExternalPlayer,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift),
                help = stringResource(R.string.settings_help_timeshift),
                value = if (state.timeshiftEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("timeshift") },
                onClick = toggleTimeshift,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift_max_duration),
                help = if (state.timeshiftEnabled) stringResource(R.string.settings_help_timeshift_buffer) else stringResource(R.string.settings_help_disabled_note),
                value = stringResource(R.string.common_minutes, state.timeshiftMinutes.validTimeshiftMinutes()),
                modifier = if (state.timeshiftEnabled) Modifier else Modifier,
                enabled = state.timeshiftEnabled,
                icon = { SettingsRowIcon("clock") },
                onClick = cycleTimeshiftMinutes,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift_storage),
                help = if (state.timeshiftEnabled) stringResource(R.string.settings_help_timeshift_storage) else stringResource(R.string.settings_help_disabled_note),
                value = state.timeshiftStorage.label(),
                modifier = if (state.timeshiftEnabled) Modifier else Modifier,
                enabled = state.timeshiftEnabled,
                icon = { SettingsRowIcon("storage") },
                onClick = cycleTimeshiftStorage,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_language),
                help = stringResource(R.string.settings_help_audio_lang),
                value = state.preferredAudioLanguage.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("microphone") },
                onClick = cycleAudioLanguage,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_subtitle_language),
                help = stringResource(R.string.settings_help_subtitle_lang),
                value = state.preferredSubtitleLanguage.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("subtitles") },
                onClick = cycleSubtitleLanguage,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_auto_next),
                help = stringResource(R.string.settings_help_auto_next),
                value = if (state.autoNextEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("skip_forward") },
                onClick = toggleAutoNext,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_auto_next_countdown),
                help = if (state.autoNextEnabled) stringResource(R.string.settings_help_auto_next_countdown) else stringResource(R.string.settings_help_autonext_note),
                value = stringResource(R.string.common_seconds, state.autoNextCountdownSeconds.validAutoNextCountdown()),
                modifier = if (state.autoNextEnabled) Modifier else Modifier,
                enabled = state.autoNextEnabled,
                icon = { SettingsRowIcon("timer") },
                onClick = cycleCountdown,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_passthrough),
                help = stringResource(R.string.settings_help_passthrough),
                value = if (state.audioPassthroughEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("passthrough") },
                onClick = toggleAudioPassthrough,
            )
        }
    }
}

@Composable
private fun SettingsPanelTitle(text: String) {
    BasicText(
        text = text.uppercase(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = VivicastTypography.LabelSmall.copy(color = VivicastColors.TextSecondary),
    )
}

@Composable
private fun SettingsSectionIcon(section: String, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) VivicastColors.AccentSoft else VivicastColors.TextSecondary
    val sGeneral = stringResource(R.string.settings_section_general)
    val sPlaylists = stringResource(R.string.settings_section_playlists)
    val sEpg = stringResource(R.string.settings_section_epg)
    val sAppearance = stringResource(R.string.settings_section_appearance)
    val sPlayback = stringResource(R.string.settings_section_playback)
    val sCache = stringResource(R.string.settings_section_cache)
    val sBackup = stringResource(R.string.settings_section_backup)
    val sParental = stringResource(R.string.settings_section_parental)
    val sAbout = stringResource(R.string.settings_section_about)
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (section) {
            sGeneral -> {
                // Horizontal sliders icon (3 adjustable controls)
                val xPositions = listOf(0.38f, 0.62f, 0.46f)
                listOf(0.26f, 0.50f, 0.74f).forEachIndexed { i, y ->
                    drawLine(color, Offset(w * 0.12f, h * y), Offset(w * 0.88f, h * y), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawCircle(color, radius = w * 0.08f, center = Offset(w * xPositions[i], h * y), style = stroke)
                }
            }
            sPlaylists -> {
                repeat(3) { i ->
                    val y = h * (0.26f + i * 0.24f)
                    drawLine(color, Offset(w * 0.20f, y), Offset(w * 0.80f, y), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
            sEpg -> {
                // Calendar: outer rect, header bar, tick marks, two content lines
                drawRect(color, topLeft = Offset(w * 0.16f, h * 0.22f), size = Size(w * 0.68f, h * 0.58f), style = stroke)
                drawLine(color, Offset(w * 0.16f, h * 0.38f), Offset(w * 0.84f, h * 0.38f), strokeWidth = 2f)
                drawLine(color, Offset(w * 0.32f, h * 0.14f), Offset(w * 0.32f, h * 0.30f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.68f, h * 0.14f), Offset(w * 0.68f, h * 0.30f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.52f), Offset(w * 0.54f, h * 0.52f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.64f), Offset(w * 0.48f, h * 0.64f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            sAppearance -> {
                // Palette: circle with four color dots
                drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.34f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.34f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.34f, h * 0.62f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.62f))
            }
            sPlayback -> {
                // Play triangle
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.22f); lineTo(w * 0.76f, h * 0.50f); lineTo(w * 0.28f, h * 0.78f); close()
                }
                drawPath(p, color, style = stroke)
            }
            sCache -> {
                // Clock
                drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.26f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.68f, h * 0.58f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            sBackup -> {
                // Cloud with upload arrow
                drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.36f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.20f, center = Offset(w * 0.56f, h * 0.44f), style = stroke)
                drawLine(color, Offset(w * 0.22f, h * 0.60f), Offset(w * 0.74f, h * 0.60f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.50f, h * 0.86f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.40f, h * 0.82f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.60f, h * 0.82f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            sParental -> {
                // Lock
                drawRect(color, topLeft = Offset(w * 0.24f, h * 0.44f), size = Size(w * 0.52f, h * 0.40f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.30f, h * 0.18f), size = Size(w * 0.40f, h * 0.32f), style = stroke)
                drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.62f))
            }
            sAbout -> {
                // Info circle with "i"
                drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.46f), Offset(w * 0.50f, h * 0.70f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawCircle(color, radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.34f))
            }
            else -> {
                // Generic dot grid fallback
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.36f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.36f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.64f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.64f))
            }
        }
    }
}

@Composable
private fun SettingsRowIcon(key: String, modifier: Modifier = Modifier) {
    val color = VivicastColors.TextSecondary
    Canvas(modifier = modifier.size(18.dp)) {
        val sw = 1.8f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (key) {
            "power" -> {
                drawArc(color, startAngle = -230f, sweepAngle = 280f, useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.20f), size = Size(w * 0.64f, h * 0.64f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.08f), Offset(w * 0.50f, h * 0.44f), strokeWidth = sw + 0.5f, cap = StrokeCap.Round)
            }
            "home" -> {
                drawLine(color, Offset(w * 0.14f, h * 0.54f), Offset(w * 0.50f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.54f), Offset(w * 0.50f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                val p = Path().apply {
                    moveTo(w * 0.24f, h * 0.52f); lineTo(w * 0.24f, h * 0.86f)
                    lineTo(w * 0.76f, h * 0.86f); lineTo(w * 0.76f, h * 0.52f)
                }
                drawPath(p, color, style = stroke)
                drawRect(color, topLeft = Offset(w * 0.40f, h * 0.62f), size = Size(w * 0.20f, h * 0.24f), style = stroke)
            }
            "back" -> {
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.38f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.38f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "language" -> {
                drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawOval(color, topLeft = Offset(w * 0.26f, h * 0.12f), size = Size(w * 0.48f, h * 0.76f), style = stroke)
                drawLine(color, Offset(w * 0.12f, h * 0.50f), Offset(w * 0.88f, h * 0.50f), strokeWidth = sw)
            }
            "refresh" -> {
                drawArc(color, startAngle = 40f, sweepAngle = 270f, useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f), size = Size(w * 0.68f, h * 0.68f), style = stroke)
                val p = Path().apply {
                    moveTo(w * 0.78f, h * 0.20f); lineTo(w * 0.84f, h * 0.08f); lineTo(w * 0.70f, h * 0.14f)
                }
                drawPath(p, color, style = stroke)
            }
            "sort" -> {
                drawLine(color, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.78f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.68f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.72f), Offset(w * 0.52f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "server" -> {
                // Code / terminal icon: < / >
                drawLine(color, Offset(w * 0.18f, h * 0.38f), Offset(w * 0.36f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.50f), Offset(w * 0.18f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.82f, h * 0.38f), Offset(w * 0.64f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.50f), Offset(w * 0.82f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, h * 0.24f), Offset(w * 0.44f, h * 0.76f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "buffer" -> {
                drawRect(color, topLeft = Offset(w * 0.12f, h * 0.52f), size = Size(w * 0.16f, h * 0.32f), style = stroke)
                drawRect(color, topLeft = Offset(w * 0.34f, h * 0.36f), size = Size(w * 0.16f, h * 0.48f), style = stroke)
                drawRect(color, topLeft = Offset(w * 0.56f, h * 0.20f), size = Size(w * 0.16f, h * 0.64f), style = stroke)
                drawLine(color, Offset(w * 0.08f, h * 0.88f), Offset(w * 0.80f, h * 0.88f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "speaker" -> {
                val p = Path().apply {
                    moveTo(w * 0.18f, h * 0.36f); lineTo(w * 0.36f, h * 0.36f)
                    lineTo(w * 0.56f, h * 0.18f); lineTo(w * 0.56f, h * 0.82f)
                    lineTo(w * 0.36f, h * 0.64f); lineTo(w * 0.18f, h * 0.64f); close()
                }
                drawPath(p, color, style = stroke)
                drawArc(color, startAngle = -40f, sweepAngle = 80f, useCenter = false,
                    topLeft = Offset(w * 0.60f, h * 0.28f), size = Size(w * 0.26f, h * 0.44f), style = stroke)
            }
            "film" -> {
                drawRect(color, topLeft = Offset(w * 0.14f, h * 0.20f), size = Size(w * 0.72f, h * 0.60f), style = stroke)
                drawLine(color, Offset(w * 0.34f, h * 0.20f), Offset(w * 0.34f, h * 0.80f), strokeWidth = sw)
                drawLine(color, Offset(w * 0.66f, h * 0.20f), Offset(w * 0.66f, h * 0.80f), strokeWidth = sw)
                val p = Path().apply {
                    moveTo(w * 0.42f, h * 0.36f); lineTo(w * 0.42f, h * 0.64f); lineTo(w * 0.60f, h * 0.50f); close()
                }
                drawPath(p, color)
            }
            "display" -> {
                drawRect(color, topLeft = Offset(w * 0.10f, h * 0.18f), size = Size(w * 0.80f, h * 0.52f), style = stroke)
                drawLine(color, Offset(w * 0.38f, h * 0.70f), Offset(w * 0.62f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.70f), Offset(w * 0.50f, h * 0.84f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.84f), Offset(w * 0.68f, h * 0.84f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "external" -> {
                drawRect(color, topLeft = Offset(w * 0.14f, h * 0.32f), size = Size(w * 0.44f, h * 0.44f), style = stroke)
                drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.82f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.82f, h * 0.18f), Offset(w * 0.82f, h * 0.38f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, h * 0.56f), Offset(w * 0.82f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "timeshift" -> {
                // Clock with counterclockwise arrow overlay
                drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.56f, h * 0.54f), style = stroke)
                drawLine(color, Offset(w * 0.56f, h * 0.54f), Offset(w * 0.56f, h * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, h * 0.54f), Offset(w * 0.70f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
                drawArc(color, startAngle = 110f, sweepAngle = -230f, useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.10f), size = Size(w * 0.42f, h * 0.42f), style = stroke)
                val arr = Path().apply { moveTo(w*0.12f, h*0.10f); lineTo(w*0.24f, h*0.08f); lineTo(w*0.18f, h*0.22f) }
                drawPath(arr, color, style = stroke)
            }
            "clock" -> {
                drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.24f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.68f, h * 0.60f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "storage" -> {
                drawOval(color, topLeft = Offset(w * 0.14f, h * 0.14f), size = Size(w * 0.72f, h * 0.22f), style = stroke)
                drawLine(color, Offset(w * 0.14f, h * 0.25f), Offset(w * 0.14f, h * 0.64f), strokeWidth = sw)
                drawLine(color, Offset(w * 0.86f, h * 0.25f), Offset(w * 0.86f, h * 0.64f), strokeWidth = sw)
                drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.53f), size = Size(w * 0.72f, h * 0.22f), style = stroke)
            }
            "microphone" -> {
                drawRoundRect(color, topLeft = Offset(w * 0.36f, h * 0.10f), size = Size(w * 0.28f, h * 0.50f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = stroke)
                drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.30f), size = Size(w * 0.56f, h * 0.40f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.70f), Offset(w * 0.50f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.86f), Offset(w * 0.64f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "subtitles" -> {
                drawRect(color, topLeft = Offset(w * 0.12f, h * 0.22f), size = Size(w * 0.76f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.22f, h * 0.44f), Offset(w * 0.52f, h * 0.44f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.56f), Offset(w * 0.66f, h * 0.56f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, h * 0.80f), Offset(w * 0.56f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, h * 0.80f), Offset(w * 0.30f, h * 0.80f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "skip_forward" -> {
                val p1 = Path().apply { moveTo(w*0.14f,h*0.22f); lineTo(w*0.52f,h*0.50f); lineTo(w*0.14f,h*0.78f); close() }
                val p2 = Path().apply { moveTo(w*0.50f,h*0.22f); lineTo(w*0.78f,h*0.50f); lineTo(w*0.50f,h*0.78f); close() }
                drawPath(p1, color, style = stroke); drawPath(p2, color, style = stroke)
                drawLine(color, Offset(w * 0.82f, h * 0.22f), Offset(w * 0.82f, h * 0.78f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "timer" -> {
                drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.50f, h * 0.58f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.58f), Offset(w * 0.50f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.40f, h * 0.14f), Offset(w * 0.60f, h * 0.14f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "passthrough" -> {
                drawArc(color, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(w * 0.46f, h * 0.22f), size = Size(w * 0.22f, h * 0.56f), style = stroke)
                drawArc(color, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(w * 0.62f, h * 0.14f), size = Size(w * 0.26f, h * 0.72f), style = stroke)
                val p = Path().apply { moveTo(w*0.16f,h*0.36f); lineTo(w*0.38f,h*0.50f); lineTo(w*0.16f,h*0.64f) }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.44f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "moon" -> {
                val p = Path().apply {
                    moveTo(w * 0.60f, h * 0.14f)
                    cubicTo(w * 0.14f, h * 0.18f, w * 0.14f, h * 0.82f, w * 0.60f, h * 0.86f)
                    cubicTo(w * 0.30f, h * 0.78f, w * 0.28f, h * 0.22f, w * 0.60f, h * 0.14f)
                }
                drawPath(p, color, style = stroke)
            }
            "palette" -> {
                drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.32f))
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.64f, h * 0.32f))
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.34f, h * 0.62f))
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.64f, h * 0.62f))
            }
            "transparency" -> {
                drawRect(color, topLeft = Offset(w * 0.10f, h * 0.10f), size = Size(w * 0.50f, h * 0.50f), style = stroke)
                drawRect(color, topLeft = Offset(w * 0.40f, h * 0.40f), size = Size(w * 0.50f, h * 0.50f), style = stroke)
            }
            "font" -> {
                val p = Path().apply { moveTo(w*0.16f,h*0.82f); lineTo(w*0.50f,h*0.18f); lineTo(w*0.84f,h*0.82f) }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(w * 0.28f, h * 0.58f), Offset(w * 0.72f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "animation" -> {
                for (i in 0..3) {
                    val x = w * (0.14f + i * 0.20f)
                    drawArc(color, startAngle = if (i % 2 == 0) 180f else 0f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(x, h * 0.30f), size = Size(w * 0.20f, h * 0.40f), style = stroke)
                }
            }
            "key" -> {
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.30f, h * 0.42f), style = stroke)
                drawLine(color, Offset(w * 0.48f, h * 0.58f), Offset(w * 0.86f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.74f, h * 0.58f), Offset(w * 0.74f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.58f), Offset(w * 0.86f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "lock" -> {
                drawRect(color, topLeft = Offset(w * 0.22f, h * 0.46f), size = Size(w * 0.56f, h * 0.42f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.30f, h * 0.16f), size = Size(w * 0.40f, h * 0.36f), style = stroke)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.64f))
            }
            "lock_off" -> {
                drawRect(color, topLeft = Offset(w * 0.22f, h * 0.46f), size = Size(w * 0.56f, h * 0.42f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.40f, h * 0.36f), style = stroke)
                drawLine(color, Offset(w * 0.52f, h * 0.30f), Offset(w * 0.78f, h * 0.30f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "shield" -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f); lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.54f); lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.16f, h * 0.54f); lineTo(w * 0.16f, h * 0.24f); close()
                }
                drawPath(p, color, style = stroke)
            }
            "export" -> {
                drawRect(color, topLeft = Offset(w * 0.18f, h * 0.46f), size = Size(w * 0.64f, h * 0.38f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.56f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.34f, h * 0.32f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.66f, h * 0.32f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "import" -> {
                drawRect(color, topLeft = Offset(w * 0.18f, h * 0.46f), size = Size(w * 0.64f, h * 0.38f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.54f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.34f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.66f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "folder" -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.36f); lineTo(w * 0.12f, h * 0.78f)
                    lineTo(w * 0.88f, h * 0.78f); lineTo(w * 0.88f, h * 0.36f)
                    lineTo(w * 0.48f, h * 0.36f); lineTo(w * 0.40f, h * 0.24f)
                    lineTo(w * 0.12f, h * 0.24f); close()
                }
                drawPath(p, color, style = stroke)
            }
            "calendar_refresh" -> {
                drawRect(color, topLeft = Offset(w * 0.12f, h * 0.26f), size = Size(w * 0.54f, h * 0.48f), style = stroke)
                drawLine(color, Offset(w * 0.12f, h * 0.42f), Offset(w * 0.66f, h * 0.42f), strokeWidth = sw)
                drawLine(color, Offset(w * 0.30f, h * 0.16f), Offset(w * 0.30f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawArc(color, startAngle = 40f, sweepAngle = 260f, useCenter = false,
                    topLeft = Offset(w * 0.60f, h * 0.48f), size = Size(w * 0.30f, h * 0.30f), style = stroke)
            }
            "list_refresh" -> {
                drawLine(color, Offset(w * 0.12f, h * 0.26f), Offset(w * 0.58f, h * 0.26f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.48f), Offset(w * 0.52f, h * 0.48f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.70f), Offset(w * 0.44f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
                drawArc(color, startAngle = 40f, sweepAngle = 260f, useCenter = false,
                    topLeft = Offset(w * 0.52f, h * 0.38f), size = Size(w * 0.36f, h * 0.36f), style = stroke)
            }
            else -> drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
        }
    }
}

@Composable
internal fun GeneralSettingsPanel(
    state: GeneralSettingsState,
    onLaunchOnBootChanged: (Boolean) -> Unit,
    onDoubleBackToExitChanged: (Boolean) -> Unit,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onRememberSortingChanged: (Boolean) -> Unit,
    onLanguageChanged: (SettingsLanguage) -> Unit,
    onGlobalUserAgentChanged: (String) -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    var showUserAgentDialog by remember { mutableStateOf(false) }
    val toggleLaunchOnBoot = { onLaunchOnBootChanged(!state.launchOnBoot) }
    val toggleBackgroundRefresh = { onBackgroundRefreshChanged(!state.backgroundRefreshEnabled) }
    val toggleDoubleBack = { onDoubleBackToExitChanged(!state.doubleBackToExit) }
    val toggleRememberSorting = { onRememberSortingChanged(!state.rememberSorting) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_launch_on_boot),
                help = stringResource(R.string.settings_help_launch_on_boot),
                value = if (state.launchOnBoot) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("power") },
                onClick = toggleLaunchOnBoot,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_double_back),
                help = stringResource(R.string.settings_help_double_back),
                value = if (state.doubleBackToExit) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("back") },
                onClick = toggleDoubleBack,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_language),
                help = stringResource(R.string.settings_help_language),
                value = state.appLanguage.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("language") },
                onClick = { showLanguagePicker = true },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_background_refresh),
                help = stringResource(R.string.settings_help_background_refresh),
                value = if (state.backgroundRefreshEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("refresh") },
                onClick = toggleBackgroundRefresh,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_remember_sorting),
                help = stringResource(R.string.settings_help_remember_sorting),
                value = if (state.rememberSorting) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("sort") },
                onClick = toggleRememberSorting,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_user_agent),
                help = stringResource(R.string.settings_help_user_agent),
                value = state.globalUserAgent.ifBlank { stringResource(R.string.value_app_default) },
                modifier = Modifier,
                icon = { SettingsRowIcon("server") },
                onClick = { showUserAgentDialog = true },
            )
        }

    }

    if (showUserAgentDialog) {
        UserAgentDialog(
            initialValue = state.globalUserAgent,
            onCancel = { showUserAgentDialog = false },
            onSave = { value ->
                onGlobalUserAgentChanged(value)
                showUserAgentDialog = false
            },
        )
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            current = state.appLanguage,
            onSelect = { lang ->
                if (lang != state.appLanguage) onLanguageChanged(lang)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }
}

@Composable
private fun UserAgentDialog(
    initialValue: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    val strInvalidChars = stringResource(R.string.settings_ua_invalid_chars)
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_user_agent),
        modifier = Modifier.testTag(userAgentDialogTag()),
    ) {
        VivicastTextField(
            value = value,
            onValueChange = {
                value = it.take(200)
                error = null
            },
            fieldModifier = Modifier.testTag(userAgentFieldTag()),
            focusRequester = fieldFocus,
            isError = error != null,
        )
        BodyText(stringResource(R.string.settings_ua_default_hint), maxLines = 2)
        if (error != null) {
            BodyText(error!!, color = VivicastColors.Error)
        }
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.common_save),
            onPrimary = {
                val trimmed = value.trim()
                if (trimmed.any { it.isISOControl() }) {
                    error = strInvalidChars
                } else {
                    onSave(trimmed)
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = userAgentSaveTag(),
            secondaryTestTag = userAgentCancelTag(),
        )
    }
}

fun userAgentDialogTag(): String = "settings-user-agent-dialog"
fun userAgentFieldTag(): String = "settings-user-agent-field"
fun userAgentSaveTag(): String = "settings-user-agent-save"
fun userAgentCancelTag(): String = "settings-user-agent-cancel"

@Composable
private fun SettingsLanguage.label(): String = when (this) {
    SettingsLanguage.System -> stringResource(R.string.language_system)
    SettingsLanguage.German -> stringResource(R.string.language_german)
    SettingsLanguage.English -> stringResource(R.string.language_english)
}

@Composable
private fun LanguagePickerDialog(
    current: SettingsLanguage,
    onSelect: (SettingsLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_language),
        initialFocus = selectedFocusRequester,
    ) {
        SettingsLanguage.entries.forEach { lang ->
            FocusPanel(
                selected = lang == current,
                onClick = { onSelect(lang) },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true)
                    .then(if (lang == current) Modifier.focusRequester(selectedFocusRequester) else Modifier),
            ) {
                BasicText(
                    text = lang.label(),
                    style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                )
            }
        }
    }
}

@Composable
private fun SettingsThemeMode.label(): String = when (this) {
    SettingsThemeMode.StandardDark -> stringResource(R.string.theme_dark)
    SettingsThemeMode.HighContrastDark -> stringResource(R.string.theme_dark_contrast)
    SettingsThemeMode.AmoledDark -> stringResource(R.string.theme_amoled)
}

private fun SettingsThemeMode.next(): SettingsThemeMode =
    when (this) {
        SettingsThemeMode.StandardDark -> SettingsThemeMode.HighContrastDark
        SettingsThemeMode.HighContrastDark -> SettingsThemeMode.AmoledDark
        SettingsThemeMode.AmoledDark -> SettingsThemeMode.StandardDark
    }

@Composable
private fun SettingsAccentColor.label(): String = when (this) {
    SettingsAccentColor.Blue -> stringResource(R.string.accent_blue)
}

private fun SettingsAccentColor.next(): SettingsAccentColor =
    when (this) {
        SettingsAccentColor.Blue -> SettingsAccentColor.Blue
    }

@Composable
private fun SettingsTransparency.label(): String = when (this) {
    SettingsTransparency.Percent0 -> "0 %"
    SettingsTransparency.Percent25 -> "25 %"
    SettingsTransparency.Percent50 -> "50 %"
}

private fun SettingsTransparency.next(): SettingsTransparency =
    when (this) {
        SettingsTransparency.Percent0 -> SettingsTransparency.Percent25
        SettingsTransparency.Percent25 -> SettingsTransparency.Percent50
        SettingsTransparency.Percent50 -> SettingsTransparency.Percent0
    }

@Composable
private fun SettingsFontScale.label(): String = when (this) {
    SettingsFontScale.Small -> stringResource(R.string.font_small)
    SettingsFontScale.Medium -> stringResource(R.string.font_medium)
    SettingsFontScale.Large -> stringResource(R.string.font_large)
    SettingsFontScale.ExtraLarge -> stringResource(R.string.font_very_large)
}

private fun SettingsFontScale.next(): SettingsFontScale =
    when (this) {
        SettingsFontScale.Small -> SettingsFontScale.Medium
        SettingsFontScale.Medium -> SettingsFontScale.Large
        SettingsFontScale.Large -> SettingsFontScale.ExtraLarge
        SettingsFontScale.ExtraLarge -> SettingsFontScale.Small
    }

@Composable
private fun SettingsAnimationSpeed.label(): String = when (this) {
    SettingsAnimationSpeed.Off -> stringResource(R.string.value_off)
    SettingsAnimationSpeed.Fast -> stringResource(R.string.anim_fast)
    SettingsAnimationSpeed.Normal -> stringResource(R.string.anim_normal)
    SettingsAnimationSpeed.Slow -> stringResource(R.string.anim_slow)
}

private fun SettingsAnimationSpeed.next(): SettingsAnimationSpeed =
    when (this) {
        SettingsAnimationSpeed.Off -> SettingsAnimationSpeed.Fast
        SettingsAnimationSpeed.Fast -> SettingsAnimationSpeed.Normal
        SettingsAnimationSpeed.Normal -> SettingsAnimationSpeed.Slow
        SettingsAnimationSpeed.Slow -> SettingsAnimationSpeed.Off
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

@Composable
private fun PlaybackBufferSizeMode.label(): String = when (this) {
    PlaybackBufferSizeMode.Off -> stringResource(R.string.value_off)
    PlaybackBufferSizeMode.Small -> stringResource(R.string.size_small)
    PlaybackBufferSizeMode.Medium -> stringResource(R.string.size_medium)
    PlaybackBufferSizeMode.Large -> stringResource(R.string.size_large)
    PlaybackBufferSizeMode.ExtraLarge -> stringResource(R.string.size_very_large)
}

private fun PlaybackBufferSizeMode.nextBufferSize(): PlaybackBufferSizeMode =
    when (this) {
        PlaybackBufferSizeMode.Off -> PlaybackBufferSizeMode.Small
        PlaybackBufferSizeMode.Small -> PlaybackBufferSizeMode.Medium
        PlaybackBufferSizeMode.Medium -> PlaybackBufferSizeMode.Large
        PlaybackBufferSizeMode.Large -> PlaybackBufferSizeMode.ExtraLarge
        PlaybackBufferSizeMode.ExtraLarge -> PlaybackBufferSizeMode.Off
    }

@Composable
private fun PlaybackDecoderMode.label(): String = when (this) {
    PlaybackDecoderMode.Hardware -> stringResource(R.string.decoder_hardware)
    PlaybackDecoderMode.Software -> stringResource(R.string.decoder_software)
}

private fun PlaybackDecoderMode.nextDecoderMode(): PlaybackDecoderMode =
    when (this) {
        PlaybackDecoderMode.Hardware -> PlaybackDecoderMode.Software
        PlaybackDecoderMode.Software -> PlaybackDecoderMode.Hardware
    }

@Composable
private fun PlaybackAudioLanguage.label(): String = when (this) {
    PlaybackAudioLanguage.SystemDefault -> stringResource(R.string.language_system)
    PlaybackAudioLanguage.German -> stringResource(R.string.language_german)
    PlaybackAudioLanguage.English -> stringResource(R.string.language_english)
    PlaybackAudioLanguage.Original -> stringResource(R.string.audio_original)
}

private fun PlaybackAudioLanguage.nextAudioLanguage(): PlaybackAudioLanguage =
    when (this) {
        PlaybackAudioLanguage.SystemDefault -> PlaybackAudioLanguage.German
        PlaybackAudioLanguage.German -> PlaybackAudioLanguage.English
        PlaybackAudioLanguage.English -> PlaybackAudioLanguage.Original
        PlaybackAudioLanguage.Original -> PlaybackAudioLanguage.SystemDefault
    }

@Composable
private fun PlaybackSubtitleLanguage.label(): String = when (this) {
    PlaybackSubtitleLanguage.Off -> stringResource(R.string.value_off)
    PlaybackSubtitleLanguage.SystemDefault -> stringResource(R.string.language_system)
    PlaybackSubtitleLanguage.German -> stringResource(R.string.language_german)
    PlaybackSubtitleLanguage.English -> stringResource(R.string.language_english)
}

private fun PlaybackSubtitleLanguage.nextSubtitleLanguage(): PlaybackSubtitleLanguage =
    when (this) {
        PlaybackSubtitleLanguage.Off -> PlaybackSubtitleLanguage.SystemDefault
        PlaybackSubtitleLanguage.SystemDefault -> PlaybackSubtitleLanguage.German
        PlaybackSubtitleLanguage.German -> PlaybackSubtitleLanguage.English
        PlaybackSubtitleLanguage.English -> PlaybackSubtitleLanguage.Off
    }

@Composable
private fun PlaybackExternalPlayerMode.label(): String = when (this) {
    PlaybackExternalPlayerMode.Internal -> stringResource(R.string.player_internal)
    PlaybackExternalPlayerMode.External -> stringResource(R.string.player_external)
    PlaybackExternalPlayerMode.AskEveryTime -> stringResource(R.string.player_ask)
}

private fun PlaybackExternalPlayerMode.nextExternalPlayerPreference(): PlaybackExternalPlayerMode =
    when (this) {
        PlaybackExternalPlayerMode.Internal -> PlaybackExternalPlayerMode.External
        PlaybackExternalPlayerMode.External -> PlaybackExternalPlayerMode.AskEveryTime
        PlaybackExternalPlayerMode.AskEveryTime -> PlaybackExternalPlayerMode.Internal
    }

@Composable
private fun PlaybackTimeshiftStorageMode.label(): String = when (this) {
    PlaybackTimeshiftStorageMode.Automatic -> stringResource(R.string.storage_auto)
    PlaybackTimeshiftStorageMode.Ram -> "RAM"
    PlaybackTimeshiftStorageMode.InternalStorage -> stringResource(R.string.storage_internal)
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
    val providers by remember { providerRepository.observeProviders() }.collectAsState(initial = emptyList())
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
    val strEpgScheduled = stringResource(R.string.settings_epg_msg_scheduled)
    val strEpgSourceSaved = stringResource(R.string.settings_epg_msg_source_saved)
    val strEpgSourceAssigned = stringResource(R.string.settings_epg_msg_source_assigned)
    val strEpgSourceUnlinked = stringResource(R.string.settings_epg_msg_source_unlinked)
    val strEpgSourceDeleted = stringResource(R.string.settings_epg_msg_source_deleted)
    val strEpgSourceSelect = stringResource(R.string.settings_epg_msg_source_select)
    val strEpgPriorityUpdated = stringResource(R.string.settings_epg_msg_priority_updated)
    val strValidationEpgNameMissing = stringResource(R.string.validation_name_missing)
    val strValidationEpgUrlMissing = stringResource(R.string.validation_epg_url_missing)
    val strUnknownError = stringResource(R.string.common_unknown_error)
    val strEpgSrcSaveFailed = stringResource(R.string.settings_epg_source_save_failed)
    val strEpgLinkFailed = stringResource(R.string.settings_epg_msg_mapping_failed)
    val strEpgUnlinkFailed = stringResource(R.string.settings_epg_msg_remove_failed)
    val strEpgPriorityFailed = stringResource(R.string.settings_epg_msg_priority_failed)
    val strEpgDeleteFailed = stringResource(R.string.settings_epg_msg_source_delete_failed)

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
                message = strEpgScheduled
            },
            firstFocusModifier = firstFocusModifier,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(
                label = stringResource(R.string.settings_epg_add_source),
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
                label = stringResource(R.string.settings_epg_manual_mapping),
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
                        val validationMessage = editor.validationMessage(strValidationEpgNameMissing, strValidationEpgUrlMissing)
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
                                    message = strEpgSourceSaved
                                }
                                .onFailure { error ->
                                    message = strEpgSrcSaveFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    onDelete = {
                        pendingDelete = sources.firstOrNull { it.id == editor.sourceId }
                    },
                    onLinkProvider = { providerId, sourceId, priority ->
                        scope.launch {
                            runCatching { epgSourceRepository.linkSourceToProvider(providerId, sourceId, priority) }
                                .onSuccess { message = strEpgSourceAssigned }
                                .onFailure { error ->
                                    message = strEpgLinkFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    onUnlinkProvider = { providerId, sourceId ->
                        scope.launch {
                            runCatching { epgSourceRepository.unlinkSourceFromProvider(providerId, sourceId) }
                                .onSuccess { message = strEpgSourceUnlinked }
                                .onFailure { error ->
                                    message = strEpgUnlinkFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    onMoveProviderLink = { providerId, sourceId, direction ->
                        scope.launch {
                            runCatching { epgSourceRepository.moveSourcePriority(providerId, sourceId, direction) }
                                .onSuccess { message = strEpgPriorityUpdated }
                                .onFailure { error ->
                                    message = strEpgPriorityFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            } else {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_panel_title),
                    body = message ?: strEpgSourceSelect,
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
                            message = strEpgSourceDeleted
                        }
                        .onFailure { error ->
                            pendingDelete = null
                            message = strEpgDeleteFailed.format(error.message ?: strUnknownError)
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
            title = stringResource(R.string.settings_epg_interval),
            help = stringResource(R.string.settings_epg_help_interval),
            value = stringResource(R.string.common_hours, preferences.refreshIntervalHours),
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
            title = stringResource(R.string.settings_epg_past),
            help = stringResource(R.string.settings_epg_help_past),
            value = if (preferences.pastRetentionDays == 1) stringResource(R.string.common_days_singular, preferences.pastRetentionDays) else stringResource(R.string.common_days_plural, preferences.pastRetentionDays),
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
            title = stringResource(R.string.settings_epg_future),
            help = stringResource(R.string.settings_epg_help_cleanup_note),
            value = if (preferences.futureRetentionDays == 1) stringResource(R.string.common_days_singular, preferences.futureRetentionDays) else stringResource(R.string.common_days_plural, preferences.futureRetentionDays),
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
            title = stringResource(R.string.settings_epg_on_start),
            help = stringResource(R.string.settings_epg_help_background),
            value = if (preferences.refreshOnAppStartEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
            icon = { SettingsRowIcon("calendar_refresh") },
            onClick = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshOnAppStartEnabled = !preferences.refreshOnAppStartEnabled),
                )
            },
        )
        VivicastSettingsRow(
            title = stringResource(R.string.settings_epg_on_change),
            help = stringResource(R.string.settings_epg_help_on_playlist_change),
            value = if (preferences.refreshOnPlaylistChangeEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
            icon = { SettingsRowIcon("list_refresh") },
            onClick = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshOnPlaylistChangeEnabled = !preferences.refreshOnPlaylistChangeEnabled),
                )
            },
        )
        VivicastSettingsRow(
            title = stringResource(R.string.settings_epg_now),
            help = stringResource(R.string.settings_epg_help_run_now),
            value = stringResource(R.string.settings_epg_now_value),
            icon = { SettingsRowIcon("refresh") },
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
    val strMappingAllRequired = stringResource(R.string.settings_epg_msg_mapping_all_required)
    val strMappingFailed = stringResource(R.string.settings_epg_msg_mapping_failed)
    val strMappingSaved = stringResource(R.string.settings_epg_msg_mapping_saved)
    val strMappingRemoved = stringResource(R.string.settings_epg_msg_mapping_removed)
    val strRemoveFailed = stringResource(R.string.settings_epg_msg_remove_failed)
    val strSelectionRequired = stringResource(R.string.settings_epg_msg_selection_required)

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
            title = stringResource(R.string.common_no_provider),
            body = stringResource(R.string.settings_epg_no_providers_body),
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
                    onMessage(strMappingAllRequired)
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
                        onMessage(strMappingSaved)
                    }.onFailure { error ->
                        onMessage(strMappingFailed.format(error.message ?: "?"))
                    }
                }
            },
            onClear = {
                val providerId = selectedProviderId
                val channelId = selectedChannelId
                val sourceId = selectedSourceId
                if (providerId == null || channelId == null || sourceId == null) {
                    onMessage(strSelectionRequired)
                    return@ManualMappingDetail
                }
                scope.launch {
                    runCatching { repository.clearManualChannelMapping(providerId, channelId, sourceId) }
                        .onSuccess {
                            epgChannelId = ""
                            onMessage(strMappingRemoved)
                        }
                        .onFailure { error ->
                            onMessage(strRemoveFailed.format(error.message ?: "?"))
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
            title = stringResource(R.string.common_no_provider),
            body = stringResource(R.string.settings_epg_no_providers_body2),
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
                    BodyText(provider.status.localizedLabel(), maxLines = 1)
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
            title = stringResource(R.string.settings_epg_select_provider),
            body = stringResource(R.string.settings_epg_select_provider_body),
            modifier = modifier,
        )
        return
    }
    if (channels.isEmpty()) {
        InfoPanel(
            title = stringResource(R.string.settings_epg_no_channels_label),
            body = stringResource(R.string.settings_epg_no_channels_body),
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
                        BodyText(channel.channelNumber?.let { stringResource(R.string.settings_epg_channel_number, it) } ?: channel.remoteId, maxLines = 1)
                    }
                    if (hasManualMapping) {
                        StatusBadge(stringResource(R.string.settings_epg_badge_manual), tone = VivicastColors.Success)
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
                title = stringResource(R.string.settings_epg_manual_mapping),
                body = stringResource(R.string.settings_epg_manual_mapping_body),
                badge = selectedMapping?.let { if (it.isManual) stringResource(R.string.settings_epg_badge_manual) else stringResource(R.string.settings_epg_badge_auto) } ?: "EPG",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (selectedProviderId == null || selectedChannel == null) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_select_channel),
                    body = stringResource(R.string.settings_epg_select_channel_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }

        item {
            InfoPanel(
                title = selectedChannel.name,
                body = stringResource(R.string.settings_epg_remote_id_body, selectedChannel.remoteId),
                badge = selectedChannel.channelNumber?.let { stringResource(R.string.settings_epg_channel_number, it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (sources.isEmpty()) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_no_source_linked),
                    body = stringResource(R.string.settings_epg_no_source_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }

        item {
            SectionTitle(stringResource(R.string.settings_epg_source_section))
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
                        BodyText(stringResource(R.string.settings_epg_timeshift_format, source.timeShiftMinutes), maxLines = 1)
                    }
                    StatusBadge(if (source.isActive) stringResource(R.string.common_active) else stringResource(R.string.common_inactive), tone = if (source.isActive) VivicastColors.Success else VivicastColors.SurfaceHigh)
                }
            }
        }

        item {
            ProviderTextField(
                label = stringResource(R.string.settings_epg_channel_id_label),
                value = epgChannelId,
                placeholder = stringResource(R.string.settings_epg_channel_id_hint),
                onValueChange = onEpgChannelIdChange,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                ActionPill(stringResource(R.string.common_save), modifier = Modifier.width(150.dp), selected = true, onClick = onSave)
                ActionPill(stringResource(R.string.settings_epg_manual_delete), modifier = Modifier.width(190.dp), onClick = onClear)
                }
        }

        if (message != null) {
            item {
                InfoPanel(title = stringResource(R.string.common_note), body = message, modifier = Modifier.fillMaxWidth())
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
            title = stringResource(R.string.settings_epg_no_sources),
            body = stringResource(R.string.settings_epg_no_sources_body),
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
                        StatusBadge(if (source.isActive) stringResource(R.string.common_active) else stringResource(R.string.common_inactive), tone = if (source.isActive) VivicastColors.Success else VivicastColors.SurfaceHigh)
                    }
                    BodyText(stringResource(R.string.settings_epg_timeshift_format, source.timeShiftMinutes), maxLines = 1)
                    BodyText(stringResource(R.string.settings_epg_assignment_info), maxLines = 1)
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
                title = if (editor.isEditing) stringResource(R.string.settings_epg_source_edit_title) else stringResource(R.string.settings_epg_source_new_title),
                body = if (editor.isEditing) stringResource(R.string.settings_epg_source_edit_body) else stringResource(R.string.settings_epg_source_new_body),
                badge = if (editor.isActive) stringResource(R.string.common_active) else stringResource(R.string.common_inactive),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            ProviderTextField(
                label = stringResource(R.string.settings_provider_name_label),
                value = editor.name,
                placeholder = stringResource(R.string.settings_epg_source_section),
                onValueChange = { onEditorChange(editor.copy(name = it)) },
            )
        }

        item {
            ProviderTextField(
                label = stringResource(R.string.m3u_source_url),
                value = editor.url,
                placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else "https://...",
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
                        BasicText(stringResource(R.string.settings_epg_timeshift_label), style = VivicastTypography.LabelLarge)
                        BodyText(stringResource(R.string.settings_epg_timeshift_help), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill("-30", modifier = Modifier.width(82.dp), onClick = {
                            onEditorChange(editor.copy(timeShiftMinutes = (editor.timeShiftMinutes - 30).coerceAtLeast(-720)))
                        })
                        BasicText(stringResource(R.string.settings_epg_timeshift_value, editor.timeShiftMinutes), style = VivicastTypography.LabelLarge)
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
                    label = if (editor.isActive) stringResource(R.string.common_active) else stringResource(R.string.common_inactive),
                    modifier = Modifier.width(132.dp),
                    selected = editor.isActive,
                    onClick = { onEditorChange(editor.copy(isActive = !editor.isActive)) },
                )
                ActionPill(label = stringResource(R.string.common_save), modifier = Modifier.width(150.dp), selected = true, onClick = onSave)
                if (editor.isEditing) {
                    ActionPill(label = stringResource(R.string.settings_delete), modifier = Modifier.width(140.dp), onClick = onDelete)
                }
            }
        }

        if (editor.isEditing) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_provider_assignment_title),
                    body = stringResource(R.string.settings_epg_provider_assignment_body),
                    badge = "EPG",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (providers.isEmpty()) {
                item {
                    InfoPanel(
                        title = stringResource(R.string.common_no_provider),
                        body = stringResource(R.string.settings_epg_no_providers_for_source_body),
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
                                BodyText(provider.status.localizedLabel(), maxLines = 1)
                            }
                            val link = if (provider.id == selectedProviderId) {
                                providerLinks.firstOrNull { it.epgSourceId == editor.sourceId }
                            } else {
                                null
                            }
                            StatusBadge(
                                label = link?.let { stringResource(R.string.settings_epg_priority, it.priority) } ?: stringResource(R.string.settings_epg_not_assigned),
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
                                label = existingLink?.let { stringResource(R.string.settings_epg_priority, it.priority) } ?: stringResource(R.string.settings_epg_use_as_priority, nextPriority),
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
                                    label = stringResource(R.string.common_remove),
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
                                    label = if (canMoveUp) stringResource(R.string.settings_epg_higher) else stringResource(R.string.settings_epg_top),
                                    modifier = Modifier.width(128.dp),
                                    selected = !canMoveUp,
                                    onClick = {
                                        if (providerId != null && sourceId != null && canMoveUp) {
                                            onMoveProviderLink(providerId, sourceId, EpgSourcePriorityDirection.Up)
                                        }
                                    },
                                )
                                ActionPill(
                                    label = if (canMoveDown) stringResource(R.string.settings_epg_lower) else stringResource(R.string.settings_epg_bottom),
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
                    title = stringResource(R.string.common_note),
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

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocusRequester,
        modifier = Modifier.testTag(deleteEpgSourceDialogTag(source.id)),
    ) {
        InfoPanel(
            title = stringResource(R.string.settings_epg_delete_confirm),
            body = stringResource(R.string.settings_epg_delete_body),
            badge = source.name,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.settings_delete),
            onPrimary = onDelete,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = deleteEpgSourceConfirmTag(source.id),
            secondaryTestTag = deleteEpgSourceCancelTag(source.id),
            secondaryFocusRequester = cancelFocusRequester,
        )
    }
}

@Composable
private fun ProviderSettingsPanel(
    providerRepository: ProviderRepository,
    onTestProviderConnection: suspend (ProviderCreateRequest) -> String?,
    onPickM3uFile: ((String, String) -> Unit) -> Unit = {},
    onProviderSaved: (String) -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    val providers by remember { providerRepository.observeProviders() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(ProviderEditorState.newProvider(ProviderType.M3u)) }
    var editorStep by remember { mutableStateOf(ProviderEditorStep.Name) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }
    var connectionTestStatus by remember { mutableStateOf(ConnectionTestStatus.Idle) }
    val strProviderSaved = stringResource(R.string.settings_provider_msg_playlist_saved)
    val strProviderEnabled = stringResource(R.string.settings_provider_msg_enabled)
    val strProviderDisabled = stringResource(R.string.settings_provider_msg_disabled)
    val strProviderSaveFailed = stringResource(R.string.settings_provider_msg_save_failed)
    val strProviderStatusFailed = stringResource(R.string.settings_provider_msg_status_failed)
    val strProviderDeleteFailed = stringResource(R.string.settings_provider_msg_delete_failed)
    val strProviderDeleted = stringResource(R.string.settings_provider_msg_deleted)
    val strProviderChecking = stringResource(R.string.settings_provider_msg_checking)
    val strProviderConnected = stringResource(R.string.settings_provider_msg_connected)
    val strProviderDuplicate = stringResource(R.string.settings_provider_msg_name_check)
    val strProviderSectionBody = stringResource(R.string.settings_provider_section_body)
    val strValidationNameMissing = stringResource(R.string.validation_name_missing)
    val strValidationContentType = stringResource(R.string.validation_content_type_required)
    val strValidationXtreamServer = stringResource(R.string.validation_xtream_server_missing)
    val strValidationXtreamUser = stringResource(R.string.validation_xtream_username_missing)
    val strValidationXtreamPass = stringResource(R.string.validation_xtream_password_missing)
    val strValidationConnTest = stringResource(R.string.validation_connection_test_required)
    val strValidationM3uUrl = stringResource(R.string.validation_m3u_url_missing)
    val strValidationM3uFile = stringResource(R.string.validation_m3u_file_missing)
    fun ProviderEditorState.validationMessageResolved(requireConnectionTest: Boolean) = validationMessage(
        requireConnectionTest, strValidationNameMissing, strValidationContentType,
        strValidationXtreamServer, strValidationXtreamUser, strValidationXtreamPass,
        strValidationConnTest, strValidationM3uUrl, strValidationM3uFile,
    )
    fun ProviderEditorState.connectionTestRequestMessageResolved() = connectionTestRequestMessage(
        strValidationNameMissing, strValidationXtreamServer, strValidationXtreamUser, strValidationXtreamPass,
        strValidationM3uUrl, strValidationM3uFile,
    )

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

    var existingM3uUrls by remember { mutableStateOf<List<ProviderUrlEntry>>(emptyList()) }
    LaunchedEffect(providers) {
        existingM3uUrls = providers.mapNotNull { provider ->
            val credentials = runCatching { providerRepository.getCredentials(provider.id) }.getOrNull()
            (credentials as? ProviderCredentials.M3u)
                ?.takeIf { it.sourceMode == M3uSourceMode.Url }
                ?.url
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { ProviderUrlEntry(provider.id, it, provider.name) }
        }
    }
    val duplicateUrlName = if (editor.type == ProviderType.M3u &&
        editor.m3uSourceMode == M3uSourceMode.Url && editor.m3uUrl.isNotBlank()
    ) {
        existingM3uUrls.firstOrNull { it.providerId != editor.providerId && it.url == editor.m3uUrl.trim() }?.name
    } else {
        null
    }

    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
        ProviderOverviewPanel(
            providers = providers,
            message = message,
            firstFocusModifier = firstFocusModifier,
            onAddProvider = {
                selectedProviderId = null
                editor = ProviderEditorState.newProvider(ProviderType.M3u)
                editorStep = ProviderEditorStep.Name
                showEditor = true
                message = null
                connectionTestStatus = ConnectionTestStatus.Idle
            },
            onRefreshAll = {
                scope.launch {
                    if (providers.any { it.status == ProviderStatus.Refreshing }) return@launch
                    val refreshableProviders = providers.filter { provider ->
                        provider.isActive &&
                            when (val credentials = providerRepository.getCredentials(provider.id)) {
                                is ProviderCredentials.M3u -> credentials.sourceMode.isAutomaticallyRefreshable
                                is ProviderCredentials.Xtream -> true
                                null -> false
                            }
                    }
                    refreshableProviders.forEach { provider -> onProviderSaved(provider.id) }
                }
            },
            onOpenProvider = { provider ->
                selectedProviderId = provider.id
                editor = ProviderEditorState.from(provider)
                editorStep = ProviderEditorStep.Edit
                showEditor = true
                message = null
                scope.launch {
                    val credentials = runCatching { providerRepository.getCredentials(provider.id) }.getOrNull()
                    if (selectedProviderId == provider.id) {
                        editor = ProviderEditorState.from(provider, credentials)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showEditor) {
        val dismissEditor: () -> Unit = {
            selectedProviderId = null
            editor = ProviderEditorState.newProvider(ProviderType.M3u)
            editorStep = ProviderEditorStep.Name
            showEditor = false
            message = null
        }
        VivicastDialog(
            onDismiss = dismissEditor,
            width = VivicastDialogWidth.Wide,
            heightCap = 560.dp,
            modifier = Modifier.testTag("settings-provider-editor-dialog"),
        ) {
            if (!editor.isEditing) {
                    ProviderAddFlow(
                        editor = editor,
                        step = editorStep,
                        duplicateName = duplicateName,
                        duplicateUrlName = duplicateUrlName,
                        message = message,
                        onStepChange = {
                            editorStep = it
                            connectionTestStatus = ConnectionTestStatus.Idle
                        },
                        onEditorChange = {
                            editor = it
                            message = null
                            connectionTestStatus = ConnectionTestStatus.Idle
                        },
                        connectionTestStatus = connectionTestStatus,
                        onTestConnection = {
                            val validationMessage = editor.connectionTestRequestMessageResolved()
                            when {
                                duplicateName -> message = strProviderDuplicate
                                duplicateUrlName != null -> Unit
                                validationMessage != null -> message = validationMessage
                                else -> {
                                    message = null
                                    connectionTestStatus = ConnectionTestStatus.Testing
                                    scope.launch {
                                        val errorMessage = onTestProviderConnection(editor.toConnectionTestRequest())
                                        if (errorMessage == null) {
                                            editor = editor.copy(connectionTestPassed = true)
                                            connectionTestStatus = ConnectionTestStatus.Passed
                                            message = null
                                        } else {
                                            editor = editor.copy(connectionTestPassed = false)
                                            connectionTestStatus = ConnectionTestStatus.Failed
                                            message = errorMessage
                                        }
                                    }
                                }
                            }
                        },
                        onSave = {
                            if (duplicateName) {
                                message = strProviderDuplicate
                                return@ProviderAddFlow
                            }
                            if (duplicateUrlName != null) {
                                return@ProviderAddFlow
                            }
                            val validationMessage = editor.validationMessageResolved(requireConnectionTest = false)
                            if (validationMessage != null) {
                                message = validationMessage
                                return@ProviderAddFlow
                            }
                            scope.launch {
                                connectionTestStatus = ConnectionTestStatus.Testing
                                message = null
                                val errorMessage = onTestProviderConnection(editor.toConnectionTestRequest())
                                if (errorMessage != null) {
                                    editor = editor.copy(connectionTestPassed = false)
                                    connectionTestStatus = ConnectionTestStatus.Failed
                                    message = errorMessage
                                    return@launch
                                }
                                editor = editor.copy(connectionTestPassed = true)
                                connectionTestStatus = ConnectionTestStatus.Passed
                                runCatching { providerRepository.createProvider(editor.toCreateRequest()) }
                                    .onSuccess { result ->
                                        selectedProviderId = result.provider.id
                                        editor = ProviderEditorState.from(result.provider)
                                        editorStep = ProviderEditorStep.Edit
                                        connectionTestStatus = ConnectionTestStatus.Idle
                                        showEditor = false
                                        onProviderSaved(result.provider.id)
                                    }
                                    .onFailure { error ->
                                        message = strProviderSaveFailed.format(error.message ?: "?")
                                    }
                            }
                        },
                        onCancel = {
                            selectedProviderId = null
                            editor = ProviderEditorState.newProvider(ProviderType.M3u)
                            editorStep = ProviderEditorStep.Name
                            connectionTestStatus = ConnectionTestStatus.Idle
                            showEditor = false
                            message = null
                        },
                        onPickM3uFile = onPickM3uFile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                ProviderEditor(
                    editor = editor,
                    duplicateName = duplicateName,
                    message = message,
                    onEditorChange = {
                        editor = it
                        message = null
                    },
                    onTestConnection = {
                        val validationMessage = editor.connectionTestRequestMessageResolved()
                        when {
                            duplicateName -> message = strProviderDuplicate
                            validationMessage != null -> message = validationMessage
                            else -> {
                                message = strProviderChecking
                                scope.launch {
                                    val errorMessage = onTestProviderConnection(editor.toConnectionTestRequest())
                                    if (errorMessage == null) {
                                        editor = editor.copy(connectionTestPassed = true)
                                        message = strProviderConnected
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
                        message = strProviderDuplicate
                        return@ProviderEditor
                    }
                    val validationMessage = editor.validationMessageResolved(requireConnectionTest = true)
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
                            selectedProviderId = null
                            editor = ProviderEditorState.newProvider(ProviderType.M3u)
                            editorStep = ProviderEditorStep.Name
                            showEditor = false
                            onProviderSaved(result.provider.id)
                            message = strProviderSaved
                        }.onFailure { error ->
                            message = strProviderSaveFailed.format(error.message ?: "?")
                        }
                    }
                    },
                    onToggleEnabled = {
                    val provider = providers.firstOrNull { it.id == editor.providerId } ?: return@ProviderEditor
                    scope.launch {
                        val enabled = !provider.isActive
                        runCatching { providerRepository.setProviderEnabled(provider.id, enabled) }
                            .onSuccess {
                                message = if (enabled) strProviderEnabled else strProviderDisabled
                            }
                            .onFailure { error -> message = strProviderStatusFailed.format(error.message ?: "?") }
                    }
                    },
                    onDelete = {
                    pendingDelete = providers.firstOrNull { it.id == editor.providerId }
                    },
                    onPickM3uFile = onPickM3uFile,
                    modifier = Modifier.fillMaxWidth(),
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
                            message = strProviderDeleted
                        }
                        .onFailure { error ->
                            pendingDelete = null
                            message = strProviderDeleteFailed.format(error.message ?: "?")
                        }
                }
            },
        )
    }
}

@Composable
private fun ProviderOverviewPanel(
    providers: List<Provider>,
    message: String?,
    firstFocusModifier: Modifier,
    onAddProvider: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenProvider: (Provider) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_playlist_add),
                help = stringResource(R.string.settings_provider_help_add),
                value = stringResource(R.string.about_open_value),
                modifier = firstFocusModifier
                    .testTag("settings-playlist-add-action")
                    ,
                onClick = onAddProvider,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_playlist_refresh_all),
                help = stringResource(R.string.settings_provider_help_refresh_all),
                value = stringResource(R.string.settings_playlist_refresh_value),
                onClick = onRefreshAll,
            )
        }
        if (message != null) {
            item {
                InfoPanel(
                    title = stringResource(R.string.common_note),
                    body = message,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (providers.isEmpty()) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_playlist_empty_title),
                    body = stringResource(R.string.settings_playlist_empty_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(providers, key = { it.id }) { provider ->
                ProviderSourceCard(
                    provider = provider,
                    onClick = { onOpenProvider(provider) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProviderSourceCard(
    provider: Provider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        contentPadding = VivicastSpacing.Space4,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = provider.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = VivicastTypography.LabelLarge,
                    )
                    StatusBadge(provider.status.localizedLabel(), tone = provider.status.tone)
                    if (!provider.isActive) {
                        StatusBadge(stringResource(R.string.common_inactive), tone = VivicastColors.Warning)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    StatusBadge(provider.type.label)
                    StatusBadge(if (provider.includeLiveTv) stringResource(R.string.nav_live_tv) else stringResource(R.string.settings_provider_live_tv_off), tone = if (provider.includeLiveTv) VivicastColors.Info else VivicastColors.SurfaceHigh)
                    StatusBadge(if (provider.includeMovies) stringResource(R.string.nav_movies_label) else stringResource(R.string.settings_provider_movies_off), tone = if (provider.includeMovies) VivicastColors.Info else VivicastColors.SurfaceHigh)
                    StatusBadge(if (provider.includeSeries) stringResource(R.string.nav_series_label) else stringResource(R.string.settings_provider_series_off), tone = if (provider.includeSeries) VivicastColors.Info else VivicastColors.SurfaceHigh)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                BodyText(stringResource(R.string.settings_provider_updated_format, provider.updatedAt.toBackupTimestamp()), maxLines = 1)
            }
        }
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
            title = stringResource(R.string.common_no_playlists),
            body = stringResource(R.string.settings_provider_empty_body),
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
                        StatusBadge(provider.status.localizedLabel(), tone = provider.status.tone)
                        if (!provider.isActive) {
                            StatusBadge(stringResource(R.string.common_inactive), tone = VivicastColors.Warning)
                        }
                    }
                    BodyText(provider.importSummary(), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ProviderAddFlow(
    editor: ProviderEditorState,
    step: ProviderEditorStep,
    duplicateName: Boolean,
    duplicateUrlName: String?,
    message: String?,
    connectionTestStatus: ConnectionTestStatus,
    onStepChange: (ProviderEditorStep) -> Unit,
    onEditorChange: (ProviderEditorState) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onPickM3uFile: ((String, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    var nameError by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        runCatching { firstFocus.requestFocus() }
    }
    val errorText = when {
        duplicateName -> stringResource(R.string.settings_provider_name_exists_body)
        duplicateUrlName != null -> stringResource(R.string.settings_provider_url_exists, duplicateUrlName)
        else -> message
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item(key = "step-title") {
            SectionTitle(stringResource(step.titleRes))
        }
        item(key = "step-error") {
            VivicastDialogError(errorText)
        }
        when (step) {
            ProviderEditorStep.Name -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_name_required),
                        value = editor.name,
                        placeholder = stringResource(R.string.settings_provider_name_placeholder),
                        onValueChange = {
                            nameError = false
                            onEditorChange(editor.copy(name = it, connectionTestPassed = false))
                        },
                        focusRequester = firstFocus,
                        isError = nameError,
                        maxLength = 25,
                    )
                }
                item {
                    ProviderFlowActions(
                        primaryLabel = stringResource(R.string.common_next),
                        onPrimary = {
                            if (editor.name.isNotBlank() && !duplicateName) {
                                nameError = false
                                onStepChange(ProviderEditorStep.Type)
                            } else {
                                nameError = true
                                runCatching { firstFocus.requestFocus() }
                            }
                        },
                        secondaryLabel = stringResource(R.string.common_cancel),
                        onSecondary = onCancel,
                    )
                }
            }
            ProviderEditorStep.Type -> {
                item {
                    VivicastButtonRow {
                        ProviderChoiceButton(
                            label = "M3U",
                            modifier = Modifier.focusRequester(firstFocus),
                            selected = editor.type == ProviderType.M3u,
                            onClick = { onEditorChange(editor.copy(type = ProviderType.M3u, connectionTestPassed = false)) },
                        )
                        ProviderChoiceButton(
                            label = "Xtream Codes",
                            selected = editor.type == ProviderType.Xtream,
                            onClick = { onEditorChange(editor.copy(type = ProviderType.Xtream, connectionTestPassed = false)) },
                        )
                    }
                }
                item {
                    ProviderFlowActions(
                        primaryLabel = stringResource(R.string.common_next),
                        onPrimary = {
                            onStepChange(if (editor.type == ProviderType.M3u) ProviderEditorStep.M3uInput else ProviderEditorStep.Xtream)
                        },
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.Name) },
                        cancelLabel = stringResource(R.string.common_cancel),
                        onCancel = onCancel,
                    )
                }
            }
            ProviderEditorStep.M3uInput -> {
                item {
                    VivicastButtonRow {
                        M3uSourceMode.entries.forEachIndexed { index, mode ->
                            ProviderChoiceButton(
                                label = stringResource(mode.labelRes),
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                selected = editor.m3uSourceMode == mode,
                                onClick = {
                                    onEditorChange(
                                        editor.copy(
                                            m3uSourceMode = mode,
                                            m3uUrl = "",
                                            m3uContent = "",
                                            m3uHasExistingSource = false,
                                            connectionTestPassed = false,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                item {
                    ProviderFlowActions(
                        primaryLabel = stringResource(R.string.common_next),
                        onPrimary = { onStepChange(editor.m3uSourceMode.addStep) },
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.Type) },
                        cancelLabel = stringResource(R.string.common_cancel),
                        onCancel = onCancel,
                    )
                }
            }
            ProviderEditorStep.M3uUrl -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_url_required),
                        value = editor.m3uUrl,
                        placeholder = "https://...",
                        onValueChange = { onEditorChange(editor.copy(m3uUrl = it, connectionTestPassed = false)) },
                        focusRequester = firstFocus,
                        maxLength = 250,
                    )
                }
                if (editor.m3uUrl.startsWith("http://", ignoreCase = true)) {
                    item { InfoPanel(title = stringResource(R.string.common_insecure_connection), body = stringResource(R.string.common_insecure_body), badge = "HTTP", modifier = Modifier.fillMaxWidth()) }
                }
                item {
                    VivicastButtonRow {
                        ConnectionTestButton(
                            status = connectionTestStatus,
                            onClick = onTestConnection,
                        )
                    }
                }
                item {
                    VivicastDialogActions(
                        primaryLabel = stringResource(R.string.common_save),
                        onPrimary = onSave,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.M3uInput) },
                        tertiaryLabel = stringResource(R.string.common_cancel),
                        onTertiary = onCancel,
                    )
                }
            }
            ProviderEditorStep.M3uFile -> {
                item {
                    BodyText(
                        if (editor.m3uContent.isNotBlank() && editor.m3uFileName.isNotBlank()) {
                            stringResource(
                                R.string.settings_provider_file_label,
                                editor.m3uFileName,
                                m3uChannelCount(editor.m3uContent),
                            )
                        } else {
                            stringResource(R.string.settings_provider_file_label_empty)
                        },
                    )
                }
                item {
                    VivicastButtonRow {
                        ActionPill(
                            label = stringResource(R.string.settings_provider_file_pick),
                            modifier = Modifier.focusRequester(firstFocus),
                            selected = editor.m3uContent.isNotBlank(),
                            onClick = {
                                onPickM3uFile { fileName, content ->
                                    onEditorChange(
                                        editor.copy(
                                            m3uContent = content.take(MAX_M3U_INLINE_SOURCE_CHARS),
                                            m3uFileName = fileName,
                                            m3uHasExistingSource = false,
                                            connectionTestPassed = false,
                                        ),
                                    )
                                }
                            },
                        )
                        ActionPill(
                            label = when (connectionTestStatus) {
                                ConnectionTestStatus.Testing -> stringResource(R.string.settings_provider_msg_checking)
                                ConnectionTestStatus.Passed -> "✓ " + stringResource(R.string.settings_provider_file_test_ok)
                                ConnectionTestStatus.Failed -> "✗ " + stringResource(R.string.settings_provider_file_test_fail)
                                else -> stringResource(R.string.settings_provider_file_test)
                            },
                            selected = connectionTestStatus == ConnectionTestStatus.Passed,
                            onClick = onTestConnection,
                        )
                    }
                }
                item {
                    VivicastDialogActions(
                        primaryLabel = stringResource(R.string.common_save),
                        onPrimary = onSave,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.M3uInput) },
                        tertiaryLabel = stringResource(R.string.common_cancel),
                        onTertiary = onCancel,
                    )
                }
            }
            ProviderEditorStep.Xtream -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_server_required),
                        value = editor.xtreamServerUrl,
                        placeholder = "http://host:8080",
                        onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it, connectionTestPassed = false)) },
                        focusRequester = firstFocus,
                        maxLength = 250,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_username_required),
                        value = editor.xtreamUsername,
                        placeholder = stringResource(R.string.settings_provider_username_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamUsername = it, connectionTestPassed = false)) },
                        maxLength = 100,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_password_required),
                        value = editor.xtreamPassword,
                        placeholder = stringResource(R.string.settings_provider_password_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamPassword = it, connectionTestPassed = false)) },
                        secret = true,
                        maxLength = 100,
                    )
                }
                if (editor.xtreamServerUrl.startsWith("http://", ignoreCase = true)) {
                    item { InfoPanel(title = stringResource(R.string.common_insecure_connection), body = stringResource(R.string.common_insecure_body), badge = "HTTP", modifier = Modifier.fillMaxWidth()) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                        BodyText(stringResource(R.string.settings_provider_import_section), maxLines = 1)
                        VivicastButtonRow {
                            ActionPill(stringResource(R.string.nav_live_tv), selected = editor.includeLiveTv, onClick = { onEditorChange(editor.copy(includeLiveTv = !editor.includeLiveTv, connectionTestPassed = false)) })
                            ActionPill(stringResource(R.string.nav_movies_label), selected = editor.includeMovies, onClick = { onEditorChange(editor.copy(includeMovies = !editor.includeMovies, connectionTestPassed = false)) })
                            ActionPill(stringResource(R.string.nav_series_label), selected = editor.includeSeries, onClick = { onEditorChange(editor.copy(includeSeries = !editor.includeSeries, connectionTestPassed = false)) })
                        }
                    }
                }
                item {
                    VivicastButtonRow {
                        ConnectionTestButton(
                            status = connectionTestStatus,
                            onClick = onTestConnection,
                        )
                    }
                }
                item {
                    VivicastDialogActions(
                        primaryLabel = stringResource(R.string.common_save),
                        onPrimary = onSave,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.Type) },
                        tertiaryLabel = stringResource(R.string.common_cancel),
                        onTertiary = onCancel,
                    )
                }
            }
            ProviderEditorStep.Edit -> Unit
        }
    }
}

@Composable
private fun ProviderFlowActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    cancelLabel: String? = null,
    onCancel: () -> Unit = {},
) {
    VivicastDialogActions(
        primaryLabel = primaryLabel,
        onPrimary = onPrimary,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        tertiaryLabel = cancelLabel,
        onTertiary = onCancel,
    )
}

// ponytail: Näherung über #EXTINF-Zeilen; exakte Zahl liefert der Parser erst beim Import.
private fun m3uChannelCount(content: String): Int =
    content.lineSequence().count { it.trimStart().startsWith("#EXTINF", ignoreCase = true) }

private enum class ConnectionTestStatus { Idle, Testing, Passed, Failed }

@Composable
private fun ConnectionTestButton(
    status: ConnectionTestStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor: Color? = when (status) {
        ConnectionTestStatus.Passed -> VivicastColors.Success
        ConnectionTestStatus.Failed -> VivicastColors.Error
        else -> null
    }
    val glyph: String? = when (status) {
        ConnectionTestStatus.Passed -> "✓"
        ConnectionTestStatus.Failed -> "✗"
        else -> null
    }
    val labelRes = when (status) {
        ConnectionTestStatus.Testing -> R.string.settings_provider_msg_checking
        ConnectionTestStatus.Passed -> R.string.settings_provider_test_ok
        ConnectionTestStatus.Failed -> R.string.settings_provider_test_fail
        else -> R.string.settings_provider_test_connection
    }
    FocusPanel(
        onClick = onClick,
        modifier = modifier.then(
            if (statusColor != null) {
                Modifier.border(VivicastBorders.FocusWidth, statusColor, VivicastShapes.CardRadius)
            } else {
                Modifier
            },
        ),
    ) { _ ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                BasicText(
                    text = glyph,
                    style = VivicastTypography.LabelLarge.copy(color = statusColor ?: VivicastColors.TextPrimary),
                )
            }
            BasicText(
                text = stringResource(labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(color = statusColor ?: VivicastColors.TextPrimary),
            )
        }
    }
}

@Composable
private fun ProviderChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    ) { _ ->
        BasicText(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
        )
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
    onPickM3uFile: ((String, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item {
            InfoPanel(
                title = if (editor.isEditing) stringResource(R.string.common_edit) else stringResource(R.string.settings_provider_title_playlist),
                body = if (editor.isEditing) {
                    stringResource(R.string.settings_provider_xtream_stable)
                } else {
                    stringResource(R.string.settings_provider_xtream_credentials)
                },
                badge = editor.type.label,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (duplicateName) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_provider_msg_name_check),
                    body = stringResource(R.string.settings_provider_name_duplicate_body),
                    badge = stringResource(R.string.common_warning),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            ProviderTextField(
                label = stringResource(R.string.settings_provider_name_label),
                value = editor.name,
                placeholder = stringResource(R.string.settings_provider_name_placeholder),
                onValueChange = { onEditorChange(editor.copy(name = it)) },
                focusRequester = firstFocus,
                maxLength = 25,
            )
        }

        if (!editor.isEditing) {
            item {
                VivicastButtonRow {
                    ActionPill(
                        label = "M3U",
                        selected = editor.type == ProviderType.M3u,
                        onClick = { onEditorChange(ProviderEditorState.newProvider(ProviderType.M3u)) },
                    )
                    ActionPill(
                        label = "Xtream",
                        selected = editor.type == ProviderType.Xtream,
                        onClick = { onEditorChange(ProviderEditorState.newProvider(ProviderType.Xtream)) },
                    )
                }
            }
        }

        when (editor.type) {
            ProviderType.M3u -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                        BodyText(stringResource(R.string.settings_provider_source_section), maxLines = 1)
                        VivicastButtonRow {
                            M3uSourceMode.entries.forEach { mode ->
                                ActionPill(
                                    label = stringResource(mode.labelRes),
                                    selected = editor.m3uSourceMode == mode,
                                    onClick = {
                                        onEditorChange(
                                            editor.copy(
                                                m3uSourceMode = mode,
                                                m3uUrl = "",
                                                m3uContent = "",
                                                m3uHasExistingSource = false,
                                                connectionTestPassed = false,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    when (editor.m3uSourceMode) {
                        M3uSourceMode.Url -> ProviderTextField(
                            label = stringResource(R.string.settings_provider_m3u_url_label),
                            value = editor.m3uUrl,
                            placeholder = if (editor.m3uHasExistingSource) stringResource(R.string.settings_provider_placeholder_reset) else "https://...",
                            onValueChange = {
                                onEditorChange(editor.copy(m3uUrl = it, m3uHasExistingSource = false, connectionTestPassed = false))
                            },
                            secret = editor.isEditing,
                            maxLength = 250,
                        )
                        M3uSourceMode.File -> Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                            InfoPanel(
                                title = stringResource(R.string.settings_provider_m3u_file_label),
                                body = if (editor.m3uContent.isBlank()) {
                                    if (editor.m3uHasExistingSource) {
                                        stringResource(R.string.settings_provider_file_saved)
                                    } else {
                                        stringResource(R.string.settings_provider_file_none)
                                    }
                                } else {
                                    stringResource(R.string.settings_provider_file_stage)
                                },
                                badge = stringResource(R.string.settings_epg_badge_manual),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ActionPill(
                                label = stringResource(R.string.settings_provider_file_pick),
                                modifier = Modifier.width(190.dp),
                                onClick = {
                                    onPickM3uFile { fileName, content ->
                                        onEditorChange(
                                            editor.copy(
                                                m3uContent = content.take(MAX_M3U_INLINE_SOURCE_CHARS),
                                                m3uFileName = fileName,
                                                m3uHasExistingSource = false,
                                                connectionTestPassed = false,
                                            ),
                                        )
                                    }
                                },
                            )
                            if (editor.m3uContent.isNotBlank() && editor.m3uFileName.isNotBlank()) {
                                BodyText(
                                    stringResource(
                                        R.string.settings_provider_file_selected,
                                        editor.m3uFileName,
                                        m3uChannelCount(editor.m3uContent),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            ProviderType.Xtream -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_server_label),
                        value = editor.xtreamServerUrl,
                        placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else "https://server.example",
                        onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it, connectionTestPassed = false)) },
                        secret = editor.isEditing,
                        maxLength = 250,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_username_label),
                        value = editor.xtreamUsername,
                        placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_username_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamUsername = it, connectionTestPassed = false)) },
                        secret = editor.isEditing,
                        maxLength = 100,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_password_label),
                        value = editor.xtreamPassword,
                        placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_password_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamPassword = it, connectionTestPassed = false)) },
                        secret = true,
                        maxLength = 100,
                    )
                }
            }
        }

        if (editor.type == ProviderType.Xtream) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    BodyText(stringResource(R.string.settings_provider_content_section), maxLines = 1)
                    VivicastButtonRow {
                        ActionPill(
                            label = stringResource(R.string.nav_live_tv),
                            selected = editor.includeLiveTv,
                            onClick = { onEditorChange(editor.copy(includeLiveTv = !editor.includeLiveTv)) },
                        )
                        ActionPill(
                            label = stringResource(R.string.nav_movies_label),
                            selected = editor.includeMovies,
                            onClick = { onEditorChange(editor.copy(includeMovies = !editor.includeMovies)) },
                        )
                        ActionPill(
                            label = stringResource(R.string.nav_series_label),
                            selected = editor.includeSeries,
                            onClick = { onEditorChange(editor.copy(includeSeries = !editor.includeSeries)) },
                        )
                    }
                }
            }
        }

        item {
            if (editor.isAutomaticallyRefreshable) {
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
                        BasicText(stringResource(R.string.settings_provider_interval_label), style = VivicastTypography.LabelLarge)
                        BodyText(stringResource(R.string.settings_provider_body_refresh), maxLines = 1)
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
        }

        if (!editor.isAutomaticallyRefreshable) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_provider_update_title),
                    body = stringResource(R.string.settings_provider_file_no_auto),
                    badge = stringResource(R.string.settings_epg_badge_manual),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = stringResource(R.string.common_note),
                    body = message,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            VivicastButtonRow {
                ActionPill(
                    label = stringResource(R.string.settings_provider_test_connection),
                    selected = editor.connectionTestPassed,
                    onClick = onTestConnection,
                )
                ActionPill(label = stringResource(R.string.common_save), onClick = onSave)
                if (editor.isEditing) {
                    ActionPill(label = stringResource(R.string.settings_provider_toggle_enabled), onClick = onToggleEnabled)
                    ActionPill(label = stringResource(R.string.settings_delete), onClick = onDelete)
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
    singleLine: Boolean = true,
    height: Dp = 58.dp,
    trailingActionLabel: String? = null,
    onTrailingAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    isError: Boolean = false,
    maxLength: Int? = null,
) {
    VivicastTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        secret = secret,
        singleLine = singleLine,
        height = height,
        focusRequester = focusRequester,
        isError = isError,
        maxLength = maxLength,
        trailingActionLabel = trailingActionLabel,
        onTrailingAction = onTrailingAction,
    )
}

@Composable
fun DeleteProviderDialog(
    provider: Provider,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocusRequester,
        modifier = Modifier.testTag(deleteProviderDialogTag(provider.id)),
    ) {
        InfoPanel(
            title = stringResource(R.string.about_provider_delete_title),
            body = stringResource(R.string.settings_provider_delete_body),
            badge = provider.name,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.settings_delete),
            onPrimary = onDelete,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = deleteProviderConfirmTag(provider.id),
            secondaryTestTag = deleteProviderCancelTag(provider.id),
            secondaryFocusRequester = cancelFocusRequester,
        )
    }
}

@Composable
internal fun AppearanceSettingsPanel(
    state: AppearanceSettingsState = AppearanceSettingsState(),
    onAppearanceSettingsChanged: (AppearanceSettingsState) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    val cycleTheme = {
        onAppearanceSettingsChanged(state.copy(themeMode = state.themeMode.next()))
    }
    val cycleAccent = {
        onAppearanceSettingsChanged(state.copy(accentColor = state.accentColor.next()))
    }
    val cycleTransparency = {
        onAppearanceSettingsChanged(state.copy(transparency = state.transparency.next()))
    }
    val cycleFontScale = {
        onAppearanceSettingsChanged(state.copy(fontScale = state.fontScale.next()))
    }
    val cycleAnimationSpeed = {
        onAppearanceSettingsChanged(state.copy(animationSpeed = state.animationSpeed.next()))
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_theme),
                help = stringResource(R.string.settings_help_theme),
                value = state.themeMode.label(),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("moon") },
                onClick = cycleTheme,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_accent_color),
                help = stringResource(R.string.settings_help_accent),
                value = state.accentColor.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("palette") },
                onClick = cycleAccent,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_transparency),
                help = stringResource(R.string.settings_help_transparency),
                value = state.transparency.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("transparency") },
                onClick = cycleTransparency,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_font_size),
                help = stringResource(R.string.settings_help_font_scale),
                value = state.fontScale.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("font") },
                onClick = cycleFontScale,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_animations),
                help = stringResource(R.string.settings_help_animation),
                value = state.animationSpeed.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("animation") },
                onClick = cycleAnimationSpeed,
            )
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
    val strProtectSettings = stringResource(R.string.settings_protect_settings)
    val strProtectMovies = stringResource(R.string.settings_protect_movies)
    val strProtectSeries = stringResource(R.string.settings_protect_series)
    val strProtectAdult = stringResource(R.string.settings_protect_adult)
    val strPinFirst = stringResource(R.string.settings_pin_first)
    val strHelpProtectSettings = stringResource(R.string.settings_help_protect_settings)
    val strHelpProtectMovies = stringResource(R.string.settings_help_protect_movies)
    val strHelpProtectSeries = stringResource(R.string.settings_help_protect_series)
    val strHelpProtectAdult = stringResource(R.string.settings_help_protect_adult)

    fun changeProtection(area: ParentalProtectionArea, enabled: Boolean) {
        inlineError = if (state.hasPin) {
            onProtectionChanged(area, enabled)
        } else {
            strPinFirst
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        inlineError?.let { message ->
            item {
                InfoPanel(
                    title = stringResource(R.string.common_note),
                    body = message,
                )
            }
        }
        item {
            VivicastSettingsRow(
                title = if (state.hasPin) stringResource(R.string.settings_pin_change) else stringResource(R.string.settings_pin_set),
                help = stringResource(R.string.settings_help_pin_entry),
                value = if (state.hasPin) stringResource(R.string.settings_pin_change_value) else stringResource(R.string.settings_pin_set_value),
                icon = { SettingsRowIcon("key") },
                onClick = { dialog = if (state.hasPin) PinDialogMode.Change else PinDialogMode.Set },
            )
        }
        if (state.hasPin) {
            item {
                VivicastSettingsRow(
                    title = stringResource(R.string.settings_pin_disable),
                    help = stringResource(R.string.settings_help_pin_change),
                    value = stringResource(R.string.settings_pin_disable_value),
                    icon = { SettingsRowIcon("lock_off") },
                    onClick = { dialog = PinDialogMode.Disable },
                )
            }
        }
        protectionAreaItem(
            title = strProtectSettings,
            help = strHelpProtectSettings,
            enabled = state.protectSettings,
            onClick = { changeProtection(ParentalProtectionArea.Settings, !state.protectSettings) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = strProtectMovies,
            help = strHelpProtectMovies,
            enabled = state.protectMovies,
            onClick = { changeProtection(ParentalProtectionArea.Movies, !state.protectMovies) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = strProtectSeries,
            help = strHelpProtectSeries,
            enabled = state.protectSeries,
            onClick = { changeProtection(ParentalProtectionArea.Series, !state.protectSeries) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = strProtectAdult,
            help = strHelpProtectAdult,
            enabled = state.protectAdultContent,
            onClick = { changeProtection(ParentalProtectionArea.AdultContent, !state.protectAdultContent) },
            hasPin = state.hasPin,
        )
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_lock_session),
                help = stringResource(R.string.settings_help_protect_unlocks),
                value = stringResource(R.string.settings_lock_session_value),
                icon = { SettingsRowIcon("lock") },
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
                stringResource(R.string.settings_pin_required)
            } else if (enabled) {
                stringResource(R.string.value_on)
            } else {
                stringResource(R.string.value_off)
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
    val strPinCurrentRequired = stringResource(R.string.settings_pin_current_required)
    val strPinFourDigits = stringResource(R.string.settings_pin_four_digits)
    val strPinMismatch = stringResource(R.string.settings_pin_mismatch)
    val title = when (mode) {
        PinDialogMode.Set -> stringResource(R.string.settings_pin_set)
        PinDialogMode.Change -> stringResource(R.string.settings_pin_change)
        PinDialogMode.Disable -> stringResource(R.string.settings_pin_disable)
    }

    val pinKeyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    val pinPlaceholder = stringResource(R.string.settings_pin_placeholder)

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        title = title,
        initialFocus = firstFocusRequester,
        modifier = Modifier.testTag(pinDialogTag()),
    ) {
        if (mode != PinDialogMode.Set) {
            VivicastTextField(
                value = currentPin,
                onValueChange = { currentPin = it.pinInput() },
                label = stringResource(R.string.settings_pin_label_current),
                placeholder = pinPlaceholder,
                secret = true,
                keyboardOptions = pinKeyboard,
                focusRequester = firstFocusRequester,
                fieldModifier = Modifier.testTag(pinCurrentFieldTag()),
            )
        }
        if (mode != PinDialogMode.Disable) {
            VivicastTextField(
                value = newPin,
                onValueChange = { newPin = it.pinInput() },
                label = stringResource(R.string.settings_pin_label_new),
                placeholder = pinPlaceholder,
                secret = true,
                keyboardOptions = pinKeyboard,
                focusRequester = if (mode == PinDialogMode.Set) firstFocusRequester else null,
                fieldModifier = Modifier.testTag(pinNewFieldTag()),
            )
            VivicastTextField(
                value = repeatPin,
                onValueChange = { repeatPin = it.pinInput() },
                label = stringResource(R.string.settings_pin_label_repeat),
                placeholder = pinPlaceholder,
                secret = true,
                keyboardOptions = pinKeyboard,
                fieldModifier = Modifier.testTag(pinRepeatFieldTag()),
            )
        }
        error?.let { BodyText(it, color = VivicastColors.Error, maxLines = 2) }
        VivicastDialogActions(
            primaryLabel = if (mode == PinDialogMode.Disable) stringResource(R.string.settings_pin_disable_value) else stringResource(R.string.common_save),
            onPrimary = {
                error = validatePinDialog(mode, currentPin, newPin, repeatPin, strPinCurrentRequired, strPinFourDigits, strPinMismatch)
                    ?: onConfirm(currentPin, newPin)
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = pinConfirmTag(),
            secondaryTestTag = pinCancelTag(),
        )
    }
}

private fun validatePinDialog(
    mode: PinDialogMode,
    currentPin: String,
    newPin: String,
    repeatPin: String,
    msgCurrentRequired: String,
    msgFourDigits: String,
    msgMismatch: String,
): String? {
    if (mode != PinDialogMode.Set && currentPin.length != PIN_LENGTH) return msgCurrentRequired
    if (mode != PinDialogMode.Disable && newPin.length != PIN_LENGTH) return msgFourDigits
    if (mode != PinDialogMode.Disable && newPin != repeatPin) return msgMismatch
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
    state: BackupSettingsState = BackupSettingsState(),
    onBackupSettingsChanged: (BackupSettingsState) -> Unit = {},
    onExportStandardBackup: () -> Unit = {},
    onImportStandardBackup: () -> Unit = {},
    onExportEncryptedFullBackup: (String) -> Unit = {},
    onImportEncryptedFullBackup: (String) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    var pendingFullAction by remember { mutableStateOf<BackupFullAction?>(null) }
    val cycleTarget = {
        onBackupSettingsChanged(state.copy(target = state.target.next()))
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_export),
                help = stringResource(R.string.settings_help_backup_standard),
                value = stringResource(R.string.settings_backup_select_value),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("export") },
                onClick = onExportStandardBackup,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_import),
                help = stringResource(R.string.settings_help_backup_restore),
                value = stringResource(R.string.settings_backup_select_value),
                icon = { SettingsRowIcon("import") },
                onClick = onImportStandardBackup,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_target),
                help = stringResource(R.string.settings_help_backup_target),
                value = state.target.label(),
                icon = { SettingsRowIcon("folder") },
                onClick = cycleTarget,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_last),
                help = stringResource(R.string.settings_help_backup_last),
                value = state.lastBackupAtMillis?.toBackupTimestamp() ?: stringResource(R.string.settings_backup_never),
                enabled = false,
                icon = { SettingsRowIcon("clock") },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_full_export),
                help = stringResource(R.string.settings_help_backup_full_export),
                value = stringResource(R.string.settings_backup_full_value),
                icon = { SettingsRowIcon("shield") },
                onClick = { pendingFullAction = BackupFullAction.Export },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_full_import),
                help = stringResource(R.string.settings_help_backup_full_import),
                value = stringResource(R.string.settings_backup_full_value),
                icon = { SettingsRowIcon("shield") },
                onClick = { pendingFullAction = BackupFullAction.Import },
            )
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
private fun BackupTargetMode.label(): String = when (this) {
    BackupTargetMode.LocalStorage -> stringResource(R.string.common_local_storage)
    BackupTargetMode.Smb -> "SMB"
    BackupTargetMode.GoogleDrive -> "Google Drive"
}

private fun BackupTargetMode.next(): BackupTargetMode =
    when (this) {
        BackupTargetMode.LocalStorage -> BackupTargetMode.Smb
        BackupTargetMode.Smb -> BackupTargetMode.GoogleDrive
        BackupTargetMode.GoogleDrive -> BackupTargetMode.LocalStorage
    }

private fun Long.toBackupTimestamp(): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))

@Composable
private fun FullBackupPassphraseDialog(
    action: BackupFullAction,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val strPassphraseBody = stringResource(R.string.settings_backup_passphrase_body)
    val strPassphraseMissing = stringResource(R.string.settings_backup_passphrase_missing)
    val fieldFocus = remember { FocusRequester() }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = fieldFocus,
        modifier = Modifier.testTag(fullBackupPassphraseDialogTag()),
    ) {
        InfoPanel(
            title = stringResource(action.titleRes),
            body = error ?: strPassphraseBody,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastTextField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                error = null
            },
            secret = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            focusRequester = fieldFocus,
            fieldModifier = Modifier.testTag(fullBackupPassphraseFieldTag()),
            isError = error != null,
            maxLength = 100,
        )
        VivicastDialogActions(
            primaryLabel = stringResource(action.confirmLabelRes),
            onPrimary = {
                val value = passphrase.trim()
                if (value.isBlank()) {
                    error = strPassphraseMissing
                } else {
                    onConfirm(value)
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = fullBackupPassphraseConfirmTag(),
            secondaryTestTag = fullBackupPassphraseCancelTag(),
        )
    }
}

private enum class BackupFullAction(
    @get:StringRes val titleRes: Int,
    @get:StringRes val confirmLabelRes: Int,
) {
    Export(R.string.settings_backup_full_export, R.string.settings_backup_full_action_export),
    Import(R.string.settings_backup_full_import, R.string.settings_backup_full_action_import),
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
    val strDaysSingular = stringResource(R.string.common_days_singular)
    val strDaysPlural = stringResource(R.string.common_days_plural)
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
    var legalPage by remember { mutableStateOf<AboutLegalPage?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_app_version_title),
                help = stringResource(R.string.about_help_version),
                value = state.appVersion,
                modifier = firstFocusModifier,
            )
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_package_name), help = stringResource(R.string.about_help_package), value = state.packageName)
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_db_version), help = stringResource(R.string.about_help_db), value = state.databaseVersion.toString())
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_android_version), help = stringResource(R.string.about_help_android), value = state.androidVersion)
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_device_model), help = stringResource(R.string.about_help_device), value = state.deviceModel)
        }
        item {
            InfoPanel(
                title = stringResource(R.string.about_diagnostics_section),
                body = stringResource(R.string.about_diagnostics_body),
                badge = stringResource(R.string.about_diagnostics_badge),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_diagnostics_logging),
                help = stringResource(R.string.about_help_logging),
                value = if (diagnosticsSettingsState.diagnosticsLoggingEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                onClick = toggleDiagnostics,
            )
        }
        item {
            val days = diagnosticsSettingsState.retentionDays.coerceIn(1, 7)
            AdjustableSettingsRow(
                title = stringResource(R.string.about_retention),
                help = if (diagnosticsSettingsState.diagnosticsLoggingEnabled) {
                    stringResource(R.string.about_help_retention_on)
                } else {
                    stringResource(R.string.about_help_retention_off)
                },
                value = if (days == 1) strDaysSingular.format(days) else strDaysPlural.format(days),
                onDecrease = decreaseRetention,
                onIncrease = increaseRetention,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_export_diagnostics),
                help = stringResource(R.string.about_help_export_diagnostics),
                value = stringResource(R.string.common_export),
                modifier = Modifier,
                onClick = onExportDiagnostics,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_copy_support),
                help = stringResource(R.string.about_help_copy_support),
                value = stringResource(R.string.common_copy),
                modifier = Modifier,
                onClick = onCopySupportInformation,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_legal),
                help = stringResource(R.string.about_help_legal),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier,
                onClick = { legalPage = AboutLegalPage.Licenses },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_privacy),
                help = stringResource(R.string.about_help_privacy),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier,
                onClick = { legalPage = AboutLegalPage.Privacy },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_third_party),
                help = stringResource(R.string.about_help_third_party),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier,
                onClick = { legalPage = AboutLegalPage.ThirdParty },
            )
        }
    }

    legalPage?.let { page ->
        AboutLegalDialog(page = page, onDismiss = { legalPage = null })
    }
}

private enum class AboutLegalPage(
    @get:StringRes val titleRes: Int,
    @get:StringRes val bodyRes: Int,
) {
    Licenses(R.string.about_legal_licenses_title, R.string.about_legal_licenses_body),
    Privacy(R.string.about_legal_privacy_title, R.string.about_legal_privacy_body),
    ThirdParty(R.string.about_legal_third_party_title, R.string.about_legal_third_party_body),
}

@Composable
private fun AboutLegalDialog(
    page: AboutLegalPage,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = closeFocus,
    ) {
        InfoPanel(
            title = stringResource(page.titleRes),
            body = stringResource(page.bodyRes),
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastButtonRow {
            ActionPill(
                label = stringResource(R.string.common_close),
                modifier = Modifier.focusRequester(closeFocus),
                onClick = onDismiss,
            )
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
    val strCacheRefreshed = stringResource(R.string.settings_cache_refreshed)
    val strCacheCleared = stringResource(R.string.settings_cache_cleared)
    val strHistoryCleared = stringResource(R.string.settings_history_cleared)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_cache_info),
                help = stringResource(R.string.settings_cache_info_help, cacheSettingsState.fileCount),
                value = formatCacheSize(cacheSettingsState.totalSizeBytes),
                modifier = firstFocusModifier,
                onClick = {
                    onReloadCacheStats()
                    message = strCacheRefreshed
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_cache_clear),
                help = stringResource(R.string.settings_cache_clear_row_help),
                value = stringResource(R.string.settings_cache_clear_value),
                onClick = {
                    pendingAction = MaintenanceAction.ClearCache
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_history_clear),
                help = stringResource(R.string.settings_history_clear_row_help),
                value = stringResource(R.string.settings_history_clear_all_value),
                onClick = {
                    pendingAction = MaintenanceAction.ClearAllHistory
                },
            )
        }

        items(MaintenanceAction.SelectiveHistoryActions) { action ->
            VivicastSettingsRow(
                title = stringResource(action.rowTitleRes),
                help = stringResource(action.rowHelpRes),
                value = stringResource(R.string.common_delete),
                onClick = {
                    pendingAction = action
                },
            )
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = stringResource(R.string.common_note),
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
                        message = strCacheCleared
                    }
                    else -> {
                        onClearHistory(requireNotNull(action.historyTarget))
                        message = strHistoryCleared
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
    val cancelFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocus,
        modifier = Modifier.testTag(action.dialogTag),
    ) {
        InfoPanel(
            title = stringResource(action.confirmTitleRes),
            body = stringResource(action.bodyRes),
            badge = stringResource(R.string.settings_maintenance_confirm_badge),
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(action.confirmLabelRes),
            onPrimary = onConfirm,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            secondaryFocusRequester = cancelFocus,
        )
    }
}

private enum class MaintenanceAction(
    @get:StringRes val rowTitleRes: Int,
    @get:StringRes val rowHelpRes: Int,
    @get:StringRes val confirmTitleRes: Int,
    @get:StringRes val bodyRes: Int,
    @get:StringRes val confirmLabelRes: Int,
    val dialogTag: String,
    val historyTarget: HistoryClearTarget? = null,
) {
    ClearCache(
        rowTitleRes = R.string.maintenance_cache_clear_title,
        rowHelpRes = R.string.maintenance_cache_clear_row_help,
        confirmTitleRes = R.string.maintenance_cache_clear_confirm,
        bodyRes = R.string.maintenance_cache_clear_body,
        confirmLabelRes = R.string.maintenance_cache_clear_label,
        dialogTag = "settings-clear-cache-dialog",
    ),
    ClearLiveTvHistory(
        rowTitleRes = R.string.maintenance_live_tv_title,
        rowHelpRes = R.string.maintenance_live_tv_row_help,
        confirmTitleRes = R.string.maintenance_live_tv_confirm,
        bodyRes = R.string.maintenance_live_tv_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-live-tv-history-dialog",
        historyTarget = HistoryClearTarget.LiveTv,
    ),
    ClearMovieHistory(
        rowTitleRes = R.string.maintenance_movie_title,
        rowHelpRes = R.string.maintenance_movie_row_help,
        confirmTitleRes = R.string.maintenance_movie_confirm,
        bodyRes = R.string.maintenance_movie_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-movie-history-dialog",
        historyTarget = HistoryClearTarget.Movies,
    ),
    ClearSeriesHistory(
        rowTitleRes = R.string.maintenance_series_title,
        rowHelpRes = R.string.maintenance_series_row_help,
        confirmTitleRes = R.string.maintenance_series_confirm,
        bodyRes = R.string.maintenance_series_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-series-history-dialog",
        historyTarget = HistoryClearTarget.Series,
    ),
    ClearSearchHistory(
        rowTitleRes = R.string.maintenance_search_title,
        rowHelpRes = R.string.maintenance_search_row_help,
        confirmTitleRes = R.string.maintenance_search_confirm,
        bodyRes = R.string.maintenance_search_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-search-history-dialog",
        historyTarget = HistoryClearTarget.Search,
    ),
    ClearAllHistory(
        rowTitleRes = R.string.maintenance_all_title,
        rowHelpRes = R.string.maintenance_all_row_help,
        confirmTitleRes = R.string.maintenance_all_confirm,
        bodyRes = R.string.maintenance_all_body,
        confirmLabelRes = R.string.maintenance_all_label,
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

private enum class ProviderEditorStep {
    Name,
    Type,
    M3uInput,
    M3uUrl,
    M3uFile,
    Xtream,
    Edit,
}

@get:StringRes
private val ProviderEditorStep.titleRes: Int
    get() = when (this) {
        ProviderEditorStep.Name -> R.string.settings_provider_title_new
        ProviderEditorStep.Type -> R.string.settings_provider_step_type_title
        ProviderEditorStep.M3uInput -> R.string.settings_provider_step_m3u_input_title
        ProviderEditorStep.M3uUrl -> R.string.settings_provider_step_m3u_url_title
        ProviderEditorStep.M3uFile -> R.string.settings_provider_m3u_file_label
        ProviderEditorStep.Xtream -> R.string.settings_provider_step_xtream_title
        ProviderEditorStep.Edit -> R.string.settings_provider_title_edit
    }

@get:StringRes
private val ProviderEditorStep.bodyRes: Int
    get() = when (this) {
        ProviderEditorStep.Name -> R.string.settings_provider_step_name_body
        ProviderEditorStep.Type -> R.string.settings_provider_step_type_body
        ProviderEditorStep.M3uInput -> R.string.settings_provider_step_m3u_input_body
        ProviderEditorStep.M3uUrl -> R.string.settings_provider_step_m3u_url_body
        ProviderEditorStep.M3uFile -> R.string.settings_provider_file_import_only
        ProviderEditorStep.Xtream -> R.string.settings_provider_step_xtream_body
        ProviderEditorStep.Edit -> R.string.settings_provider_step_edit_body
    }

private data class ProviderEditorState(
    val providerId: String?,
    val type: ProviderType,
    val name: String,
    val m3uSourceMode: M3uSourceMode,
    val m3uUrl: String,
    val m3uContent: String,
    val m3uHasExistingSource: Boolean,
    val xtreamServerUrl: String,
    val xtreamUsername: String,
    val xtreamPassword: String,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
    val connectionTestPassed: Boolean,
    val m3uFileName: String = "",
) {
    val isEditing: Boolean get() = providerId != null
    val isAutomaticallyRefreshable: Boolean
        get() = type == ProviderType.Xtream || (type == ProviderType.M3u && m3uSourceMode.isAutomaticallyRefreshable)

    fun validationMessage(
        requireConnectionTest: Boolean,
        msgNameMissing: String,
        msgContentType: String,
        msgXtreamServer: String,
        msgXtreamUser: String,
        msgXtreamPass: String,
        msgConnTest: String,
        msgM3uUrl: String,
        msgM3uFile: String,
    ): String? {
        if (name.isBlank()) return msgNameMissing
        if (type == ProviderType.Xtream && !includeLiveTv && !includeMovies && !includeSeries) return msgContentType
        when (type) {
            ProviderType.M3u -> m3uSourceValidationMessage(allowExistingSource = isEditing, msgM3uUrl, msgM3uFile)?.let { return it }
            ProviderType.Xtream -> if (!isEditing) {
                if (xtreamServerUrl.isBlank()) return msgXtreamServer
                if (xtreamUsername.isBlank()) return msgXtreamUser
                if (xtreamPassword.isBlank()) return msgXtreamPass
            }
        }
        if (requireConnectionTest && !connectionTestPassed) return msgConnTest
        return null
    }

    fun connectionTestRequestMessage(
        msgNameMissing: String,
        msgXtreamServer: String,
        msgXtreamUser: String,
        msgXtreamPass: String,
        msgM3uUrl: String,
        msgM3uFile: String,
    ): String? {
        if (name.isBlank()) return msgNameMissing
        return when (type) {
            ProviderType.M3u -> m3uSourceValidationMessage(allowExistingSource = false, msgM3uUrl, msgM3uFile)
            ProviderType.Xtream -> when {
                xtreamServerUrl.isBlank() -> msgXtreamServer
                xtreamUsername.isBlank() -> msgXtreamUser
                xtreamPassword.isBlank() -> msgXtreamPass
                else -> null
            }
        }
    }

    fun toConnectionTestRequest(): ProviderCreateRequest =
        ProviderCreateRequest(
            name = name,
            type = type,
            m3uSourceMode = m3uSourceMode,
            m3uUrl = m3uUrl,
            m3uContent = m3uContent,
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
            m3uSourceMode = m3uSourceMode,
            m3uUrl = m3uUrl,
            m3uContent = m3uContent,
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
            m3uSourceMode = if (type == ProviderType.M3u && shouldReplaceM3uSource) m3uSourceMode else null,
            m3uUrl = m3uUrl.ifBlank { null },
            m3uContent = m3uContent.ifBlank { null },
            xtreamServerUrl = xtreamServerUrl.ifBlank { null },
            xtreamUsername = xtreamUsername.ifBlank { null },
            xtreamPassword = xtreamPassword.ifBlank { null },
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
        )

    private val shouldReplaceM3uSource: Boolean
        get() = !m3uHasExistingSource || m3uUrl.isNotBlank() || m3uContent.isNotBlank()

    private fun m3uSourceValidationMessage(allowExistingSource: Boolean, msgUrl: String, msgFile: String): String? {
        if (allowExistingSource && m3uHasExistingSource && m3uUrl.isBlank() && m3uContent.isBlank()) return null
        return when (m3uSourceMode) {
            M3uSourceMode.Url -> if (m3uUrl.isBlank()) msgUrl else null
            M3uSourceMode.File -> if (m3uContent.isBlank()) msgFile else null
        }
    }

    companion object {
        fun newProvider(type: ProviderType): ProviderEditorState =
            ProviderEditorState(
                providerId = null,
                type = type,
                name = "",
                m3uSourceMode = M3uSourceMode.Url,
                m3uUrl = "",
                m3uContent = "",
                m3uHasExistingSource = false,
                xtreamServerUrl = "",
                xtreamUsername = "",
                xtreamPassword = "",
                includeLiveTv = true,
                includeMovies = true,
                includeSeries = true,
                refreshIntervalHours = DEFAULT_REFRESH_INTERVAL_HOURS,
                connectionTestPassed = false,
            )

        fun from(provider: Provider, credentials: ProviderCredentials? = null): ProviderEditorState =
            ProviderEditorState(
                providerId = provider.id,
                type = provider.type,
                name = provider.name,
                m3uSourceMode = (credentials as? ProviderCredentials.M3u)?.sourceMode ?: M3uSourceMode.Url,
                m3uUrl = "",
                m3uContent = "",
                m3uHasExistingSource = credentials is ProviderCredentials.M3u,
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

@get:StringRes
private val M3uSourceMode.labelRes: Int
    get() = when (this) {
        M3uSourceMode.Url -> R.string.m3u_source_url
        M3uSourceMode.File -> R.string.m3u_source_file
    }

private val M3uSourceMode.addStep: ProviderEditorStep
    get() = when (this) {
        M3uSourceMode.Url -> ProviderEditorStep.M3uUrl
        M3uSourceMode.File -> ProviderEditorStep.M3uFile
    }

private data class ProviderUrlEntry(val providerId: String?, val url: String, val name: String)

private data class EpgSourceEditorState(
    val sourceId: String?,
    val name: String,
    val url: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
) {
    val isEditing: Boolean get() = sourceId != null

    fun validationMessage(msgNameMissing: String, msgUrlMissing: String): String? {
        if (name.isBlank()) return msgNameMissing
        if (!isEditing && url.isBlank()) return msgUrlMissing
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

private val ProviderStatus.tone: Color
    get() = when (this) {
        ProviderStatus.Active -> VivicastColors.Success
        ProviderStatus.ActiveWithPartialErrors -> VivicastColors.Warning
        ProviderStatus.Refreshing -> VivicastColors.Warning
        ProviderStatus.ConnectionError -> VivicastColors.Warning
        ProviderStatus.InvalidCredentials -> VivicastColors.Error
        ProviderStatus.Expired -> VivicastColors.Warning
        ProviderStatus.Disabled -> VivicastColors.SurfaceHigh
        ProviderStatus.CredentialsRequired -> VivicastColors.Error
    }

@Composable
private fun Provider.importSummary(): String = listOfNotNull(
    stringResource(R.string.nav_live_tv).takeIf { includeLiveTv },
    stringResource(R.string.nav_movies_label).takeIf { includeMovies },
    stringResource(R.string.nav_series_label).takeIf { includeSeries },
).takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: stringResource(R.string.settings_provider_no_content)
