package com.vivicast.tv.feature.settings

import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource

/**
 * Immutable presentation state for the settings screen. Carries the DataStore-backed sections
 * migrated to the ViewModel: General, Appearance, Playback (P1-04f1) plus EPG-global and the
 * Diagnostics DataStore state (P1-04f2a) and the Maintenance/Cache stats (P1-04f2b). The EPG
 * sources list, the EPG-area provider list and the provider↔EPG-source links (P1-04f3a) plus the
 * manual-mapping channels/mappings (P1-04f3b) are collected from the repositories in the ViewModel.
 * Provider settings, Backup, Parental and About stay App-hoisted for now.
 */
internal data class SettingsUiState(
    val general: GeneralSettingsState = GeneralSettingsState(),
    val appearance: AppearanceSettingsState = AppearanceSettingsState(),
    val playback: PlaybackSettingsState = PlaybackSettingsState(),
    val epg: EpgSettingsState = EpgSettingsState(),
    val diagnostics: DiagnosticsSettingsState = DiagnosticsSettingsState(),
    val cache: CacheSettingsState = CacheSettingsState(),
    val epgSources: List<EpgSource> = emptyList(),
    val epgProviders: List<Provider> = emptyList(),
    val selectedEpgProviderId: String? = null,
    val providerEpgLinks: List<ProviderEpgSource> = emptyList(),
    val manualMappingChannels: List<Channel> = emptyList(),
    val selectedManualMappingChannelId: String? = null,
    val manualMappingsForSelectedChannel: List<EpgChannelMapping> = emptyList(),
)
