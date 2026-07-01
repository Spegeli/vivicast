package com.vivicast.tv.feature.settings

/**
 * Immutable presentation state for the settings screen. Carries the DataStore-backed sections
 * migrated to the ViewModel: General, Appearance, Playback (P1-04f1) plus EPG-global and the
 * Diagnostics DataStore state (P1-04f2a). EPG sources, Provider, Backup, Parental, About and
 * Maintenance/Cache stay App-hoisted for now.
 */
internal data class SettingsUiState(
    val general: GeneralSettingsState = GeneralSettingsState(),
    val appearance: AppearanceSettingsState = AppearanceSettingsState(),
    val playback: PlaybackSettingsState = PlaybackSettingsState(),
    val epg: EpgSettingsState = EpgSettingsState(),
    val diagnostics: DiagnosticsSettingsState = DiagnosticsSettingsState(),
)
