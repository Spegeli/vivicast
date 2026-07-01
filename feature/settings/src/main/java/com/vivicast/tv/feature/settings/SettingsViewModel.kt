package com.vivicast.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.core.datastore.AppearancePreferences
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.EpgPreferences
import com.vivicast.tv.core.datastore.GeneralPreferences
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Presentation-state holder for the DataStore-backed settings sections (General, Appearance,
 * Playback, plus the persisted last-selected section). Observes [UserPreferencesStore.values],
 * maps to an immutable [SettingsUiState] and writes changes back via the store.
 *
 * No Android Context/Resources, no Compose types, no navigation, no localized strings. App/System
 * side effects (background-refresh scheduler, locale/recreate) stay in the composable/app layer.
 * [scope] lets unit tests inject a controlled scope; production uses [viewModelScope].
 */
internal class SettingsViewModel(
    private val userPreferencesStore: UserPreferencesStore,
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private var currentPreferences: UserPreferences = UserPreferences()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            userPreferencesStore.values.collect { preferences ->
                currentPreferences = preferences
                _uiState.value = preferences.toSettingsUiState()
            }
        }
    }

    fun onLaunchOnBootChanged(enabled: Boolean) = updateGeneral { it.copy(launchOnBoot = enabled) }

    fun onDoubleBackToExitChanged(enabled: Boolean) = updateGeneral { it.copy(doubleBackToExit = enabled) }

    fun onRememberSortingChanged(enabled: Boolean) = updateGeneral { it.copy(rememberSorting = enabled) }

    fun onGlobalUserAgentChanged(userAgent: String) = updateGeneral { it.copy(globalUserAgent = userAgent) }

    /** Persists the preference only; the scheduler side effect stays in the app layer. */
    fun onBackgroundRefreshChanged(enabled: Boolean) = updateGeneral { it.copy(backgroundRefreshEnabled = enabled) }

    fun onSelectedSectionChanged(section: String) = updateGeneral { it.copy(lastSettingsSection = section) }

    /** Persists the preference only; LocaleHelper/recreate stay in the app layer. */
    fun onLanguageChanged(language: SettingsLanguage) =
        updateAppearance { it.copy(language = language.toDataStoreLanguagePreference()) }

    fun onAppearanceSettingsChanged(appearance: AppearanceSettingsState) = updateAppearance {
        it.copy(
            backgroundColor = appearance.themeMode.toDataStoreThemeColor(),
            accentColor = appearance.accentColor.toDataStoreAccentColor(),
            transparency = appearance.transparency.toDataStoreTransparencyLevel(),
            fontScale = appearance.fontScale.toDataStoreFontScalePreference(),
            animationSpeed = appearance.animationSpeed.toDataStoreAnimationSpeedPreference(),
        )
    }

    fun onEpgSettingsChanged(epg: EpgSettingsState) = updateEpg {
        it.copy(
            pastRetentionDays = epg.pastRetentionDays,
            futureRetentionDays = epg.futureRetentionDays,
            refreshIntervalHours = epg.refreshIntervalHours,
            refreshOnAppStartEnabled = epg.refreshOnAppStartEnabled,
            refreshOnPlaylistChangeEnabled = epg.refreshOnPlaylistChangeEnabled,
        )
    }

    /** Persists the preference only; the DiagnosticsStore/system effect stays in the app layer. */
    fun onDiagnosticsSettingsChanged(diagnostics: DiagnosticsSettingsState) = updateDiagnostics {
        it.copy(
            diagnosticsLoggingEnabled = diagnostics.diagnosticsLoggingEnabled,
            retentionDays = diagnostics.retentionDays.coerceIn(1, 7),
        )
    }

    fun onPlaybackSettingsChanged(playback: PlaybackSettingsState) = updatePlayback {
        it.copy(
            bufferSize = playback.bufferSize.toDataStoreBufferSizePreference(),
            audioDecoder = playback.audioDecoder.toDataStoreDecoderPreference(),
            videoDecoder = playback.videoDecoder.toDataStoreDecoderPreference(),
            afrEnabled = playback.afrEnabled,
            preferredAudioLanguage = playback.preferredAudioLanguage.toDataStoreAudioLanguage(),
            preferredSubtitleLanguage = playback.preferredSubtitleLanguage.toDataStoreSubtitleLanguage(),
            audioPassthroughEnabled = playback.audioPassthroughEnabled,
            externalPlayer = playback.externalPlayer.toDataStoreExternalPlayerPreference(),
            timeshiftEnabled = playback.timeshiftEnabled,
            timeshiftMinutes = playback.timeshiftMinutes,
            timeshiftStorage = playback.timeshiftStorage.toDataStoreTimeshiftStoragePreference(),
            autoNextEnabled = playback.autoNextEnabled,
            autoNextCountdownSeconds = playback.autoNextCountdownSeconds,
        )
    }

    private fun updateGeneral(transform: (GeneralPreferences) -> GeneralPreferences) {
        coroutineScope.launch { userPreferencesStore.updateGeneral(transform(currentPreferences.general)) }
    }

    private fun updateAppearance(transform: (AppearancePreferences) -> AppearancePreferences) {
        coroutineScope.launch { userPreferencesStore.updateAppearance(transform(currentPreferences.appearance)) }
    }

    private fun updatePlayback(transform: (PlaybackPreferences) -> PlaybackPreferences) {
        coroutineScope.launch { userPreferencesStore.updatePlayback(transform(currentPreferences.playback)) }
    }

    private fun updateEpg(transform: (EpgPreferences) -> EpgPreferences) {
        coroutineScope.launch { userPreferencesStore.updateEpg(transform(currentPreferences.epg)) }
    }

    private fun updateDiagnostics(transform: (DiagnosticsPreferences) -> DiagnosticsPreferences) {
        coroutineScope.launch { userPreferencesStore.updateDiagnostics(transform(currentPreferences.diagnostics)) }
    }
}

private fun UserPreferences.toSettingsUiState(): SettingsUiState = SettingsUiState(
    general = GeneralSettingsState(
        launchOnBoot = general.launchOnBoot,
        doubleBackToExit = general.doubleBackToExit,
        backgroundRefreshEnabled = general.backgroundRefreshEnabled,
        rememberSorting = general.rememberSorting,
        appLanguage = appearance.language.toSettingsLanguage(),
        globalUserAgent = general.globalUserAgent,
    ),
    appearance = AppearanceSettingsState(
        themeMode = appearance.backgroundColor.toSettingsThemeMode(),
        accentColor = appearance.accentColor.toSettingsAccentColor(),
        transparency = appearance.transparency.toSettingsTransparency(),
        fontScale = appearance.fontScale.toSettingsFontScale(),
        animationSpeed = appearance.animationSpeed.toSettingsAnimationSpeed(),
    ),
    playback = PlaybackSettingsState(
        bufferSize = playback.bufferSize.toSettingsBufferSizeMode(),
        audioDecoder = playback.audioDecoder.toSettingsDecoderMode(),
        videoDecoder = playback.videoDecoder.toSettingsDecoderMode(),
        afrEnabled = playback.afrEnabled,
        preferredAudioLanguage = playback.preferredAudioLanguage.toSettingsAudioLanguage(),
        preferredSubtitleLanguage = playback.preferredSubtitleLanguage.toSettingsSubtitleLanguage(),
        audioPassthroughEnabled = playback.audioPassthroughEnabled,
        externalPlayer = playback.externalPlayer.toSettingsExternalPlayerMode(),
        timeshiftEnabled = playback.timeshiftEnabled,
        timeshiftMinutes = playback.timeshiftMinutes,
        timeshiftStorage = playback.timeshiftStorage.toSettingsTimeshiftStorageMode(),
        autoNextEnabled = playback.autoNextEnabled,
        autoNextCountdownSeconds = playback.autoNextCountdownSeconds,
    ),
    epg = EpgSettingsState(
        pastRetentionDays = epg.pastRetentionDays,
        futureRetentionDays = epg.futureRetentionDays,
        refreshIntervalHours = epg.refreshIntervalHours,
        refreshOnAppStartEnabled = epg.refreshOnAppStartEnabled,
        refreshOnPlaylistChangeEnabled = epg.refreshOnPlaylistChangeEnabled,
    ),
    diagnostics = diagnostics.toSettingsDiagnosticsState(),
)
