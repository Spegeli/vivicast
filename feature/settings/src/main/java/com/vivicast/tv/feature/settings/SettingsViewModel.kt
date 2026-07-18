package com.vivicast.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.datastore.AppearancePreferences
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.EpgPreferences
import com.vivicast.tv.core.datastore.GeneralPreferences
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.media.CategoryGroupRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryGroupSettings
import com.vivicast.tv.domain.model.CategorySortMode
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Presentation-state holder for the DataStore-backed settings sections (General, Appearance,
 * Playback). Observes [UserPreferencesStore.values],
 * maps to an immutable [SettingsUiState] and writes changes back via the store.
 *
 * No Android Context/Resources, no Compose types, no navigation, no localized strings. App/System
 * side effects (background-refresh scheduler, locale/recreate) stay in the composable/app layer.
 * [scope] lets unit tests inject a controlled scope; production uses [viewModelScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SettingsViewModel(
    private val userPreferencesStore: UserPreferencesStore,
    private val mediaCacheStore: MediaCacheStore,
    private val epgSourceRepository: EpgSourceRepository,
    private val providerRepository: ProviderRepository,
    private val categoryGroupRepository: CategoryGroupRepository,
    // Coil image cache is App-owned (Context/Coil types stay out of the VM): the App passes its size
    // and a clear action as suspend lambdas so the cache row reflects the whole app image cache.
    private val imageCacheSizeBytes: suspend () -> Long = { 0L },
    private val clearImageCache: suspend () -> Unit = {},
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private var currentPreferences: UserPreferences = UserPreferences()
    private var currentCache: CacheSettingsState = CacheSettingsState()
    private var currentEpgSources: List<EpgSource> = emptyList()
    private var currentProviders: List<Provider> = emptyList()
    private var currentProviderEpgLinks: List<ProviderEpgSource> = emptyList()
    private var currentManualMappingChannels: List<Channel> = emptyList()
    private var currentManualMappings: List<EpgChannelMapping> = emptyList()
    private var currentManageGroups: List<Category> = emptyList()
    private var currentManageGroupSettings: CategoryGroupSettings = CategoryGroupSettings()

    /** Selected provider in the EPG area; keys the provider↔EPG-source link + manual-mapping channel flows. */
    private val selectedEpgProviderId = MutableStateFlow<String?>(null)

    /** Selected channel in the manual-mapping area; keys the mappings flow. */
    private val selectedManualMappingChannelId = MutableStateFlow<String?>(null)

    /** Selected (provider, active type tab) for "Gruppen verwalten"; keys the managed-groups + settings flows. */
    private val selectedGroupProvider = MutableStateFlow<String?>(null)
    private val selectedGroupType = MutableStateFlow(CategoryType.LiveTv)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            userPreferencesStore.values.collect { preferences ->
                currentPreferences = preferences
                recomposeState()
            }
        }
        coroutineScope.launch {
            epgSourceRepository.observeEpgSources().collect { sources ->
                currentEpgSources = sources
                recomposeState()
            }
        }
        coroutineScope.launch {
            providerRepository.observeProviders().collect { providers ->
                currentProviders = providers
                if (selectedEpgProviderId.value != null && providers.none { it.id == selectedEpgProviderId.value }) {
                    selectedEpgProviderId.value = null
                }
                if (selectedGroupProvider.value != null && providers.none { it.id == selectedGroupProvider.value }) {
                    selectedGroupProvider.value = null
                }
                recomposeState()
            }
        }
        coroutineScope.launch {
            selectedEpgProviderId
                .flatMapLatest { providerId ->
                    if (providerId == null) flowOf(emptyList()) else epgSourceRepository.observeProviderEpgSources(providerId)
                }
                .collect { links ->
                    currentProviderEpgLinks = links
                    recomposeState()
                }
        }
        coroutineScope.launch {
            selectedEpgProviderId
                .flatMapLatest { providerId ->
                    if (providerId == null) flowOf(emptyList()) else epgSourceRepository.observeChannelsForProvider(providerId)
                }
                .collect { channels ->
                    currentManualMappingChannels = channels
                    if (selectedManualMappingChannelId.value != null && channels.none { it.id == selectedManualMappingChannelId.value }) {
                        selectedManualMappingChannelId.value = null
                    }
                    recomposeState()
                }
        }
        coroutineScope.launch {
            combine(selectedEpgProviderId, selectedManualMappingChannelId) { providerId, channelId -> providerId to channelId }
                .flatMapLatest { (providerId, channelId) ->
                    if (providerId != null && channelId != null) {
                        epgSourceRepository.observeMappingsForChannel(providerId, channelId)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { mappings ->
                    currentManualMappings = mappings
                    recomposeState()
                }
        }
        coroutineScope.launch {
            combine(selectedGroupProvider, selectedGroupType) { providerId, type -> providerId to type }
                .flatMapLatest { (providerId, type) ->
                    if (providerId == null) {
                        flowOf(emptyList<Category>() to CategoryGroupSettings())
                    } else {
                        combine(
                            categoryGroupRepository.observeManagedGroups(providerId, type),
                            categoryGroupRepository.observeGroupSettings(providerId, type),
                        ) { groups, settings -> groups to settings }
                    }
                }
                .collect { (groups, settings) ->
                    currentManageGroups = groups
                    currentManageGroupSettings = settings
                    recomposeState()
                }
        }
    }

    /** Rebuilds the immutable [SettingsUiState] from all currently held sources. */
    private fun recomposeState() {
        _uiState.value = currentPreferences.toSettingsUiState().copy(
            cache = currentCache,
            epgSources = currentEpgSources,
            providers = currentProviders,
            selectedEpgProviderId = selectedEpgProviderId.value,
            providerEpgLinks = currentProviderEpgLinks,
            manualMappingChannels = currentManualMappingChannels,
            selectedManualMappingChannelId = selectedManualMappingChannelId.value,
            manualMappingsForSelectedChannel = currentManualMappings,
            manageGroupsProviderId = selectedGroupProvider.value,
            manageGroupsType = selectedGroupType.value,
            manageGroups = currentManageGroups,
            manageGroupSettings = currentManageGroupSettings,
        )
    }

    /** Loads the combined image cache stats (prefetch file store + Coil disk cache) into the state. */
    fun onReloadCacheStats() {
        coroutineScope.launch {
            currentCache = loadCacheState()
            recomposeState()
        }
    }

    /**
     * Clears both the prefetch file store and the Coil image cache, then reloads the stats. Suspends
     * until done so the confirm dialog can show a spinner for the whole operation.
     */
    suspend fun onClearCache() {
        mediaCacheStore.clear()
        clearImageCache()
        currentCache = loadCacheState()
        recomposeState()
    }

    private suspend fun loadCacheState(): CacheSettingsState {
        val stats = mediaCacheStore.stats()
        return CacheSettingsState(
            totalSizeBytes = stats.totalSizeBytes + imageCacheSizeBytes(),
            fileCount = stats.fileCount,
        )
    }

    /** Selects the EPG-area provider whose links are observed (shared by editor + manual mapping). */
    fun onEpgProviderSelected(providerId: String) {
        // Mirror the panel's remember(selectedProviderId) reset: a provider switch clears the channel.
        selectedManualMappingChannelId.value = null
        selectedEpgProviderId.value = providerId
        recomposeState()
    }

    /** Selects the manual-mapping channel whose mappings are observed. */
    fun onManualMappingChannelSelected(channelId: String) {
        selectedManualMappingChannelId.value = channelId
        recomposeState()
    }

    /**
     * Clears the manual-mapping channel selection when the manual-mapping view is (re)opened.
     * Mirrors the pre-P1-04f3b panel-local `remember(selectedProviderId)` reset that dropped the
     * channel selection every time the panel re-mounted.
     */
    fun onManualMappingReset() {
        selectedManualMappingChannelId.value = null
        recomposeState()
    }

    /**
     * Manual-mapping set/clear run the repository call inside the ViewModel and return the outcome so
     * the panel keeps its localized success/error messaging (no localized strings in the ViewModel).
     */
    suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): Result<Unit> =
        runCatching { epgSourceRepository.setManualChannelMapping(request); Unit }

    suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String): Result<Unit> =
        runCatching { epgSourceRepository.clearManualChannelMapping(providerId, channelId, epgSourceId) }

    /**
     * Provider CRUD runs the repository call inside the ViewModel. Reads (credentials) delegate
     * directly; mutations return a [Result] so the panel keeps its localized messaging and local
     * wizard/editor reset. Connection-test, SAF file picking and the save-scheduler side effect
     * stay App-hoisted (the panel/route call them around these methods).
     */
    suspend fun getProviderCredentials(providerId: String): ProviderCredentials? =
        providerRepository.getCredentials(providerId)

    suspend fun getProviderM3uInlineContent(providerId: String): String? =
        providerRepository.getProviderM3uInlineContent(providerId)

    suspend fun createProvider(request: ProviderCreateRequest): Result<ProviderSaveResult> =
        runCatching { providerRepository.createProvider(request) }

    suspend fun updateProvider(request: ProviderUpdateRequest): Result<ProviderSaveResult> =
        runCatching { providerRepository.updateProvider(request) }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean): Result<Unit> =
        runCatching { providerRepository.setProviderEnabled(providerId, enabled) }

    suspend fun deleteProvider(providerId: String): Result<Unit> =
        runCatching { providerRepository.deleteProvider(providerId) }

    // Group management ("Gruppen verwalten") — mirror the EPG selection + runCatching pattern. The panel
    // reads manageGroups/manageGroupSettings from UiState and calls these; German strings stay panel-side.
    fun onManageGroups(providerId: String) {
        selectedGroupType.value = CategoryType.LiveTv
        selectedGroupProvider.value = providerId
    }

    fun onManageGroupsTypeSelected(type: CategoryType) {
        selectedGroupType.value = type
    }

    fun onCloseManageGroups() {
        selectedGroupProvider.value = null
    }

    suspend fun setGroupHidden(categoryId: String, hidden: Boolean): Result<Unit> =
        runCatching { categoryGroupRepository.setGroupHidden(categoryId, hidden) }

    suspend fun setAllGroupsHidden(providerId: String, type: CategoryType, hidden: Boolean): Result<Unit> =
        runCatching { categoryGroupRepository.setAllGroupsHidden(providerId, type, hidden) }

    suspend fun reorderGroups(orderedCategoryIds: List<String>): Result<Unit> =
        runCatching { categoryGroupRepository.reorderGroups(orderedCategoryIds) }

    suspend fun resetGroupOrder(providerId: String, type: CategoryType): Result<Unit> =
        runCatching { categoryGroupRepository.resetManualOrder(providerId, type) }

    suspend fun setGroupSortMode(providerId: String, type: CategoryType, mode: CategorySortMode): Result<Unit> =
        runCatching { categoryGroupRepository.setSortMode(providerId, type, mode) }

    suspend fun setHideNewGroups(providerId: String, type: CategoryType, hidden: Boolean): Result<Unit> =
        runCatching { categoryGroupRepository.setHideNewGroups(providerId, type, hidden) }

    /**
     * EPG-source CRUD + provider-link actions run the repository call inside the ViewModel and
     * return the outcome so the panel can keep its localized success/error messaging and its local
     * editor/selection state (no localized strings in the ViewModel).
     */
    suspend fun saveEpgSource(request: EpgSourceEditRequest): Result<EpgSource> =
        runCatching { epgSourceRepository.saveSource(request) }

    /** Reads an existing source's stored URL so the editor can flag duplicate URLs. */
    suspend fun getEpgSourceUrl(sourceId: String): String? =
        runCatching { epgSourceRepository.getSourceUrl(sourceId) }.getOrNull()

    suspend fun deleteEpgSource(sourceId: String): Result<Unit> =
        runCatching { epgSourceRepository.deleteSource(sourceId) }

    suspend fun linkEpgSourceToProvider(providerId: String, sourceId: String): Result<Unit> =
        runCatching { epgSourceRepository.linkSourceToProvider(providerId, sourceId) }

    suspend fun unlinkEpgSourceFromProvider(providerId: String, sourceId: String): Result<Unit> =
        runCatching { epgSourceRepository.unlinkSourceFromProvider(providerId, sourceId) }

    suspend fun reorderEpgSourcesForProvider(providerId: String, orderedSourceIds: List<String>): Result<Unit> =
        runCatching { epgSourceRepository.reorderProviderEpgSources(providerId, orderedSourceIds) }

    fun onLaunchOnBootChanged(enabled: Boolean) = updateGeneral { it.copy(launchOnBoot = enabled) }

    fun onDoubleBackToExitChanged(enabled: Boolean) = updateGeneral { it.copy(doubleBackToExit = enabled) }

    fun onGlobalUserAgentChanged(userAgent: String) = updateGeneral { it.copy(globalUserAgent = userAgent) }

    /** Persists the preference only; the scheduler side effect stays in the app layer. */
    fun onBackgroundRefreshChanged(enabled: Boolean) = updateGeneral { it.copy(backgroundRefreshEnabled = enabled) }

    fun onResumeLastChannelChanged(enabled: Boolean) = updateGeneral { it.copy(resumeLastChannelOnStart = enabled) }

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
            refreshIntervalHours = epg.refreshIntervalHours,
            pastRetentionDays = epg.pastRetentionDays,
            refreshOnAppStartEnabled = epg.refreshOnAppStartEnabled,
            refreshOnPlaylistChangeEnabled = epg.refreshOnPlaylistChangeEnabled,
        )
    }

    /** Persists the preference only; the DiagnosticsStore/system effect stays in the app layer. */
    fun onDiagnosticsSettingsChanged(diagnostics: DiagnosticsSettingsState) = updateDiagnostics {
        // Retention is fixed at 7 days in DiagnosticsStore; there is no persisted/UI retention field.
        it.copy(diagnosticsLoggingEnabled = diagnostics.diagnosticsLoggingEnabled)
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
        resumeLastChannelOnStart = general.resumeLastChannelOnStart,
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
        autoNextEnabled = playback.autoNextEnabled,
        autoNextCountdownSeconds = playback.autoNextCountdownSeconds,
    ),
    epg = EpgSettingsState(
        refreshIntervalHours = epg.refreshIntervalHours,
        pastRetentionDays = epg.pastRetentionDays,
        refreshOnAppStartEnabled = epg.refreshOnAppStartEnabled,
        refreshOnPlaylistChangeEnabled = epg.refreshOnPlaylistChangeEnabled,
    ),
    diagnostics = diagnostics.toSettingsDiagnosticsState(),
)

