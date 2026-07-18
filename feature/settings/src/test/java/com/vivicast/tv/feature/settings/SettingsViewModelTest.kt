package com.vivicast.tv.feature.settings

import com.vivicast.tv.core.cache.MediaCacheCleanupResult
import com.vivicast.tv.core.cache.MediaCacheEntry
import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheStats
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.datastore.AppearancePreferences
import com.vivicast.tv.core.datastore.BackupPreferences
import com.vivicast.tv.core.datastore.BufferSizePreference
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.EpgPreferences
import com.vivicast.tv.core.datastore.FontScalePreference
import com.vivicast.tv.core.datastore.GeneralPreferences
import com.vivicast.tv.core.datastore.HistoryPreferences
import com.vivicast.tv.core.datastore.LanguagePreference
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.data.media.CategoryGroupRepository
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryGroupSettings
import com.vivicast.tv.domain.model.CategorySortMode
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    private fun newViewModel(
        scope: CoroutineScope,
        store: FakeUserPreferencesStore,
        cacheStore: FakeMediaCacheStore = FakeMediaCacheStore(),
        epgRepo: FakeEpgSourceRepository = FakeEpgSourceRepository(),
        providerRepo: FakeProviderRepository = FakeProviderRepository(),
    ): SettingsViewModel =
        SettingsViewModel(store, cacheStore, epgRepo, providerRepo, FakeCategoryGroupRepository(), scope = scope)

    @Test
    fun initialState_mapsGeneralAppearancePlaybackFromPreferences() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore(
            UserPreferences(
                general = GeneralPreferences(launchOnBoot = true, globalUserAgent = "UA/2"),
                appearance = AppearancePreferences(
                    backgroundColor = ThemeColor.Green,
                    language = LanguagePreference.English,
                    fontScale = FontScalePreference.Large,
                ),
                playback = PlaybackPreferences(bufferSize = BufferSizePreference.Large),
            ),
        )
        val vm = newViewModel(scope, store)

        val state = vm.uiState.value
        assertEquals(true, state.general.launchOnBoot)
        assertEquals("UA/2", state.general.globalUserAgent)
        assertEquals(SettingsLanguage.English, state.general.appLanguage)
        assertEquals(SettingsThemeMode.Green, state.appearance.themeMode)
        assertEquals(SettingsFontScale.Large, state.appearance.fontScale)
        assertEquals(PlaybackBufferSizeMode.Large, state.playback.bufferSize)
        scope.cancel()
    }

    @Test
    fun onLaunchOnBootChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onLaunchOnBootChanged(true)

        assertEquals(true, store.flow.value.general.launchOnBoot)
        assertEquals(true, vm.uiState.value.general.launchOnBoot)
        scope.cancel()
    }

    @Test
    fun onDoubleBackToExitChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onDoubleBackToExitChanged(false)

        assertEquals(false, store.flow.value.general.doubleBackToExit)
        scope.cancel()
    }


    @Test
    fun onGlobalUserAgentChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onGlobalUserAgentChanged("Custom/9")

        assertEquals("Custom/9", store.flow.value.general.globalUserAgent)
        scope.cancel()
    }

    @Test
    fun onBackgroundRefreshChanged_writesOnlyPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onBackgroundRefreshChanged(false)

        assertEquals(false, store.flow.value.general.backgroundRefreshEnabled)
        scope.cancel()
    }

    @Test
    fun onLanguageChanged_writesOnlyPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onLanguageChanged(SettingsLanguage.German)

        assertEquals(LanguagePreference.German, store.flow.value.appearance.language)
        assertEquals(SettingsLanguage.German, vm.uiState.value.general.appLanguage)
        scope.cancel()
    }

    @Test
    fun onAppearanceSettingsChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onAppearanceSettingsChanged(
            AppearanceSettingsState(
                themeMode = SettingsThemeMode.Cyan,
                fontScale = SettingsFontScale.Large,
            ),
        )

        assertEquals(ThemeColor.Cyan, store.flow.value.appearance.backgroundColor)
        assertEquals(FontScalePreference.Large, store.flow.value.appearance.fontScale)
        scope.cancel()
    }

    @Test
    fun initialState_mapsEpgAndDiagnostics() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore(
            UserPreferences(
                epg = EpgPreferences(pastRetentionDays = 5, refreshOnAppStartEnabled = false),
                diagnostics = DiagnosticsPreferences(diagnosticsLoggingEnabled = true),
            ),
        )
        val vm = newViewModel(scope, store)

        val state = vm.uiState.value
        assertEquals(5, state.epg.pastRetentionDays)
        assertEquals(false, state.epg.refreshOnAppStartEnabled)
        assertEquals(true, state.diagnostics.diagnosticsLoggingEnabled)
        scope.cancel()
    }

    @Test
    fun onEpgSettingsChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onEpgSettingsChanged(EpgSettingsState(pastRetentionDays = 6, refreshOnPlaylistChangeEnabled = false))

        assertEquals(6, store.flow.value.epg.pastRetentionDays)
        assertEquals(false, store.flow.value.epg.refreshOnPlaylistChangeEnabled)
        scope.cancel()
    }

    @Test
    fun onDiagnosticsSettingsChanged_writesOnlyCoercedPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onDiagnosticsSettingsChanged(DiagnosticsSettingsState(diagnosticsLoggingEnabled = true))

        assertEquals(true, store.flow.value.diagnostics.diagnosticsLoggingEnabled)
        // Unrelated DataStore-only diagnostics field preserved.
        assertEquals(true, store.flow.value.diagnostics.keepLastSessionSummary)
        scope.cancel()
    }

    @Test
    fun onReloadCacheStats_loadsStatsFromStore() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val cacheStore = FakeMediaCacheStore(MediaCacheStats(totalSizeBytes = 2048, fileCount = 5))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), cacheStore)

        vm.onReloadCacheStats()

        assertEquals(2048L, vm.uiState.value.cache.totalSizeBytes)
        assertEquals(5, vm.uiState.value.cache.fileCount)
        scope.cancel()
    }

    @Test
    fun onClearCache_clearsThenReloadsStats() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val cacheStore = FakeMediaCacheStore(MediaCacheStats(totalSizeBytes = 4096, fileCount = 9))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), cacheStore)

        vm.onClearCache()

        assertEquals(true, cacheStore.cleared)
        assertEquals(0L, vm.uiState.value.cache.totalSizeBytes)
        assertEquals(0, vm.uiState.value.cache.fileCount)
        scope.cancel()
    }

    @Test
    fun cacheStats_addImageCacheSize_andClearInvokesImageClear() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val cacheStore = FakeMediaCacheStore(MediaCacheStats(totalSizeBytes = 1000, fileCount = 3))
        var imageCleared = false
        val vm = SettingsViewModel(
            FakeUserPreferencesStore(),
            cacheStore,
            FakeEpgSourceRepository(),
            FakeProviderRepository(),
            FakeCategoryGroupRepository(),
            imageCacheSizeBytes = { 500L },
            clearImageCache = { imageCleared = true },
            scope = scope,
        )

        vm.onReloadCacheStats()
        assertEquals(1500L, vm.uiState.value.cache.totalSizeBytes) // media 1000 + Coil 500
        assertEquals(3, vm.uiState.value.cache.fileCount)

        vm.onClearCache()
        assertEquals(true, cacheStore.cleared)
        assertEquals(true, imageCleared)
        scope.cancel()
    }

    @Test
    fun initialState_containsEpgSourcesAndProviders() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository(initialSources = listOf(epgSource("s1", "Source 1")))
        val providerRepo = FakeProviderRepository(initialProviders = listOf(provider("p1", "Provider 1")))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo, providerRepo = providerRepo)

        val state = vm.uiState.value
        assertEquals(listOf("s1"), state.epgSources.map { it.id })
        // Shared provider list, used by both the EPG area and the Provider-settings overview.
        assertEquals(listOf("p1"), state.providers.map { it.id })
        scope.cancel()
    }

    @Test
    fun providerFlowChange_updatesUiStateReactively() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providerRepo = FakeProviderRepository(initialProviders = listOf(provider("p1", "Provider 1")))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), providerRepo = providerRepo)

        assertEquals(listOf("p1"), vm.uiState.value.providers.map { it.id })
        providerRepo.providersFlow.value = listOf(provider("p1", "Provider 1"), provider("p2", "Provider 2"))

        assertEquals(listOf("p1", "p2"), vm.uiState.value.providers.map { it.id })
        scope.cancel()
    }

    @Test
    fun onEpgProviderSelected_exposesProviderLinks() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val link = ProviderEpgSource(id = "l1", providerId = "p1", epgSourceId = "s1", priority = 1)
        val epgRepo = FakeEpgSourceRepository(links = mapOf("p1" to listOf(link)))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        vm.onEpgProviderSelected("p1")

        assertEquals("p1", vm.uiState.value.selectedEpgProviderId)
        assertEquals(listOf("s1"), vm.uiState.value.providerEpgLinks.map { it.epgSourceId })
        scope.cancel()
    }

    @Test
    fun saveEpgSource_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        val result = vm.saveEpgSource(EpgSourceEditRequest(sourceId = null, name = "New", url = "http://x"))

        assertTrue(result.isSuccess)
        assertEquals("New", epgRepo.savedRequest?.name)
        scope.cancel()
    }

    @Test
    fun deleteEpgSource_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        vm.deleteEpgSource("s1")

        assertEquals("s1", epgRepo.deletedSourceId)
        scope.cancel()
    }

    @Test
    fun linkAndUnlinkEpgProvider_delegateToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        vm.linkEpgSourceToProvider("p1", "s1", 2)
        vm.unlinkEpgSourceFromProvider("p1", "s1")

        assertEquals(Triple("p1", "s1", 2), epgRepo.linkCall)
        assertEquals("p1" to "s1", epgRepo.unlinkCall)
        scope.cancel()
    }


    @Test
    fun epgSourceFlowChange_updatesUiStateReactively() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository(initialSources = listOf(epgSource("s1", "Source 1")))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        assertEquals(listOf("s1"), vm.uiState.value.epgSources.map { it.id })
        epgRepo.sourcesFlow.value = listOf(epgSource("s1", "Source 1"), epgSource("s2", "Source 2"))

        assertEquals(listOf("s1", "s2"), vm.uiState.value.epgSources.map { it.id })
        scope.cancel()
    }

    @Test
    fun providerRemoved_clearsSelectedEpgProvider() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providerRepo = FakeProviderRepository(initialProviders = listOf(provider("p1", "Provider 1")))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), providerRepo = providerRepo)
        vm.onEpgProviderSelected("p1")
        assertEquals("p1", vm.uiState.value.selectedEpgProviderId)

        providerRepo.providersFlow.value = emptyList()

        assertEquals(null, vm.uiState.value.selectedEpgProviderId)
        scope.cancel()
    }

    @Test
    fun manualMappingChannels_loadForSelectedProvider() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository(
            channelsByProvider = mapOf("p1" to MutableStateFlow(listOf(channel("c1", "p1", "Channel 1")))),
        )
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        assertTrue(vm.uiState.value.manualMappingChannels.isEmpty())
        vm.onEpgProviderSelected("p1")

        assertEquals(listOf("c1"), vm.uiState.value.manualMappingChannels.map { it.id })
        scope.cancel()
    }

    @Test
    fun onManualMappingChannelSelected_exposesMappings() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository(
            channelsByProvider = mapOf("p1" to MutableStateFlow(listOf(channel("c1", "p1", "Channel 1")))),
            mappingsByChannel = mapOf("c1" to listOf(mapping("p1", "c1", "s1"))),
        )
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)
        vm.onEpgProviderSelected("p1")

        vm.onManualMappingChannelSelected("c1")

        assertEquals("c1", vm.uiState.value.selectedManualMappingChannelId)
        assertEquals(listOf("s1"), vm.uiState.value.manualMappingsForSelectedChannel.map { it.epgSourceId })
        scope.cancel()
    }

    @Test
    fun providerChange_resetsManualMappingChannel() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository(
            channelsByProvider = mapOf(
                "p1" to MutableStateFlow(listOf(channel("c1", "p1", "Channel 1"))),
                "p2" to MutableStateFlow(listOf(channel("c2", "p2", "Channel 2"))),
            ),
        )
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)
        vm.onEpgProviderSelected("p1")
        vm.onManualMappingChannelSelected("c1")
        assertEquals("c1", vm.uiState.value.selectedManualMappingChannelId)

        vm.onEpgProviderSelected("p2")

        assertEquals(null, vm.uiState.value.selectedManualMappingChannelId)
        assertTrue(vm.uiState.value.manualMappingsForSelectedChannel.isEmpty())
        scope.cancel()
    }

    @Test
    fun channelDisappears_resetsSelectedManualMappingChannel() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val channelsFlow = MutableStateFlow(listOf(channel("c1", "p1", "Channel 1")))
        val epgRepo = FakeEpgSourceRepository(channelsByProvider = mapOf("p1" to channelsFlow))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)
        vm.onEpgProviderSelected("p1")
        vm.onManualMappingChannelSelected("c1")
        assertEquals("c1", vm.uiState.value.selectedManualMappingChannelId)

        channelsFlow.value = emptyList()

        assertEquals(null, vm.uiState.value.selectedManualMappingChannelId)
        scope.cancel()
    }

    @Test
    fun onManualMappingReset_clearsSelectedChannel() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository(
            channelsByProvider = mapOf("p1" to MutableStateFlow(listOf(channel("c1", "p1", "Channel 1")))),
            mappingsByChannel = mapOf("c1" to listOf(mapping("p1", "c1", "s1"))),
        )
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)
        vm.onEpgProviderSelected("p1")
        vm.onManualMappingChannelSelected("c1")
        assertEquals("c1", vm.uiState.value.selectedManualMappingChannelId)

        // Re-opening the manual-mapping view (parent calls this on remount).
        vm.onManualMappingReset()

        assertEquals(null, vm.uiState.value.selectedManualMappingChannelId)
        assertTrue(vm.uiState.value.manualMappingsForSelectedChannel.isEmpty())
        // The provider selection is untouched (channels stay available).
        assertEquals("p1", vm.uiState.value.selectedEpgProviderId)
        assertEquals(listOf("c1"), vm.uiState.value.manualMappingChannels.map { it.id })
        scope.cancel()
    }

    @Test
    fun setManualChannelMapping_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        val result = vm.setManualChannelMapping(
            ManualEpgChannelMappingRequest(providerId = "p1", channelId = "c1", epgSourceId = "s1", epgChannelId = "x"),
        )

        assertTrue(result.isSuccess)
        assertEquals("c1", epgRepo.setMappingRequest?.channelId)
        assertEquals("x", epgRepo.setMappingRequest?.epgChannelId)
        scope.cancel()
    }

    @Test
    fun clearManualChannelMapping_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val epgRepo = FakeEpgSourceRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), epgRepo = epgRepo)

        vm.clearManualChannelMapping("p1", "c1", "s1")

        assertEquals(Triple("p1", "c1", "s1"), epgRepo.clearMappingCall)
        scope.cancel()
    }

    @Test
    fun getProviderCredentials_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val creds = ProviderCredentials.M3u(url = "http://x", sourceMode = M3uSourceMode.Url)
        val providerRepo = FakeProviderRepository(credentials = mapOf("p1" to creds))
        val vm = newViewModel(scope, FakeUserPreferencesStore(), providerRepo = providerRepo)

        val result = vm.getProviderCredentials("p1")

        assertEquals("p1", providerRepo.credentialsProviderId)
        assertEquals(creds, result)
        scope.cancel()
    }

    @Test
    fun createProvider_delegatesToRepositoryAndReturnsResult() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providerRepo = FakeProviderRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), providerRepo = providerRepo)

        val result = vm.createProvider(ProviderCreateRequest(name = "New", type = ProviderType.M3u, m3uUrl = "http://x"))

        assertTrue(result.isSuccess)
        assertEquals("New", providerRepo.createRequest?.name)
        assertEquals("created", result.getOrNull()?.provider?.id)
        scope.cancel()
    }

    @Test
    fun updateProvider_delegatesToRepositoryAndReturnsResult() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providerRepo = FakeProviderRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), providerRepo = providerRepo)

        val result = vm.updateProvider(ProviderUpdateRequest(providerId = "p1", name = "Renamed", type = ProviderType.M3u))

        assertTrue(result.isSuccess)
        assertEquals("p1", providerRepo.updateRequest?.providerId)
        assertEquals("updated", result.getOrNull()?.provider?.id)
        scope.cancel()
    }

    @Test
    fun setProviderEnabled_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providerRepo = FakeProviderRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), providerRepo = providerRepo)

        val result = vm.setProviderEnabled("p1", false)

        assertTrue(result.isSuccess)
        assertEquals("p1" to false, providerRepo.setEnabledCall)
        scope.cancel()
    }

    @Test
    fun deleteProvider_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providerRepo = FakeProviderRepository()
        val vm = newViewModel(scope, FakeUserPreferencesStore(), providerRepo = providerRepo)

        val result = vm.deleteProvider("p1")

        assertTrue(result.isSuccess)
        assertEquals("p1", providerRepo.deletedProviderId)
        scope.cancel()
    }

    @Test
    fun onPlaybackSettingsChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onPlaybackSettingsChanged(
            PlaybackSettingsState(bufferSize = PlaybackBufferSizeMode.Large, autoNextEnabled = true, autoNextCountdownSeconds = 20),
        )

        assertEquals(BufferSizePreference.Large, store.flow.value.playback.bufferSize)
        assertEquals(true, store.flow.value.playback.autoNextEnabled)
        assertEquals(20, store.flow.value.playback.autoNextCountdownSeconds)
        scope.cancel()
    }
}

private class FakeUserPreferencesStore(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesStore {
    val flow = MutableStateFlow(initial)
    override val values: Flow<UserPreferences> = flow

    override suspend fun updateLocalLogoFolder(path: String?) = Unit
    override suspend fun updateGeneral(general: GeneralPreferences) {
        flow.value = flow.value.copy(general = general)
    }

    override suspend fun updateAppearance(appearance: AppearancePreferences) {
        flow.value = flow.value.copy(appearance = appearance)
    }

    override suspend fun updatePlayback(playback: PlaybackPreferences) {
        flow.value = flow.value.copy(playback = playback)
    }

    override suspend fun updateEpg(epg: EpgPreferences) {
        flow.value = flow.value.copy(epg = epg)
    }

    override suspend fun updateDiagnostics(diagnostics: DiagnosticsPreferences) {
        flow.value = flow.value.copy(diagnostics = diagnostics)
    }

    override suspend fun updateSelectedProviderId(providerId: String?) = Unit
    override suspend fun updateHistory(history: HistoryPreferences) = Unit
    override suspend fun updateSearchHistory(searchHistory: List<String>) = Unit
    override suspend fun updateExpandedLiveTvProviderIds(providerIds: Set<String>) = Unit
    override suspend fun updateBackup(backup: BackupPreferences) = Unit
}

private class FakeMediaCacheStore(
    private var currentStats: MediaCacheStats = MediaCacheStats(totalSizeBytes = 0, fileCount = 0),
) : MediaCacheStore {
    var cleared = false
        private set

    override suspend fun hasEntry(key: MediaCacheKey): Boolean = false
    override suspend fun getEntry(key: MediaCacheKey): MediaCacheEntry? = null
    override suspend fun put(key: MediaCacheKey, bytes: ByteArray): MediaCacheEntry =
        throw UnsupportedOperationException()

    override suspend fun stats(): MediaCacheStats = currentStats

    override suspend fun cleanup(maxSizeBytes: Long): MediaCacheCleanupResult =
        MediaCacheCleanupResult(removedFiles = 0, removedBytes = 0, remainingBytes = currentStats.totalSizeBytes)

    override suspend fun clear(): MediaCacheCleanupResult {
        cleared = true
        currentStats = MediaCacheStats(totalSizeBytes = 0, fileCount = 0)
        return MediaCacheCleanupResult(removedFiles = 0, removedBytes = 0, remainingBytes = 0)
    }
}

private fun epgSource(id: String, name: String): EpgSource =
    EpgSource(id = id, name = name, sourceConfigKey = "cfg-$id", timeShiftMinutes = 0, isActive = true)

private fun channel(id: String, providerId: String, name: String): Channel =
    Channel(
        id = id,
        providerId = providerId,
        categoryId = null,
        remoteId = "remote-$id",
        channelNumber = null,
        name = name,
        logoUrl = null,
        isCatchupAvailable = false,
        catchupDays = 0,
    )

private fun mapping(providerId: String, channelId: String, epgSourceId: String): EpgChannelMapping =
    EpgChannelMapping(
        id = "$providerId-$channelId-$epgSourceId",
        providerId = providerId,
        channelId = channelId,
        epgSourceId = epgSourceId,
        epgChannelId = "epg-$channelId",
        isManual = true,
    )

private fun provider(id: String, name: String): Provider =
    Provider(
        id = id,
        name = name,
        type = ProviderType.M3u,
        sourceConfigKey = "cfg-$id",
        isActive = true,
        status = ProviderStatus.Active,
        includeLiveTv = true,
        includeMovies = true,
        includeSeries = true,
        refreshIntervalHours = 24,
        logoPriority = "playlist",
        createdAt = 0L,
        updatedAt = 0L,
    )

private class FakeEpgSourceRepository(
    initialSources: List<EpgSource> = emptyList(),
    private val links: Map<String, List<ProviderEpgSource>> = emptyMap(),
    private val channelsByProvider: Map<String, MutableStateFlow<List<Channel>>> = emptyMap(),
    private val mappingsByChannel: Map<String, List<EpgChannelMapping>> = emptyMap(),
) : EpgSourceRepository {
    val sourcesFlow = MutableStateFlow(initialSources)
    var savedRequest: EpgSourceEditRequest? = null
        private set
    var deletedSourceId: String? = null
        private set
    var linkCall: Triple<String, String, Int>? = null
        private set
    var unlinkCall: Pair<String, String>? = null
        private set
    var setMappingRequest: ManualEpgChannelMappingRequest? = null
        private set
    var clearMappingCall: Triple<String, String, String>? = null
        private set

    override fun observeEpgSources(): Flow<List<EpgSource>> = sourcesFlow
    override suspend fun getEpgSources(): List<EpgSource> = sourcesFlow.value
    override fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>> =
        flowOf(links[providerId] ?: emptyList())
    override fun observeChannelsForProvider(providerId: String): Flow<List<Channel>> =
        channelsByProvider[providerId] ?: flowOf(emptyList())
    override fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>> = flowOf(emptyList())
    override fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>> =
        flowOf(mappingsByChannel[channelId] ?: emptyList())
    override suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): EpgChannelMapping {
        setMappingRequest = request
        return mapping(request.providerId, request.channelId, request.epgSourceId)
    }
    override suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String) {
        clearMappingCall = Triple(providerId, channelId, epgSourceId)
    }

    override suspend fun saveSource(request: EpgSourceEditRequest): EpgSource {
        savedRequest = request
        return epgSource(request.sourceId ?: "generated-id", request.name)
    }

    override suspend fun getSourceUrl(sourceId: String): String? = null

    override suspend fun deleteSource(sourceId: String) {
        deletedSourceId = sourceId
    }

    override suspend fun linkSourceToProvider(providerId: String, epgSourceId: String, priority: Int) {
        linkCall = Triple(providerId, epgSourceId, priority)
    }

    override suspend fun unlinkSourceFromProvider(providerId: String, epgSourceId: String) {
        unlinkCall = providerId to epgSourceId
    }

}

private class FakeProviderRepository(
    initialProviders: List<Provider> = emptyList(),
    private val credentials: Map<String, ProviderCredentials> = emptyMap(),
) : ProviderRepository {
    val providersFlow = MutableStateFlow(initialProviders)
    var credentialsProviderId: String? = null
        private set
    var createRequest: ProviderCreateRequest? = null
        private set
    var updateRequest: ProviderUpdateRequest? = null
        private set
    var setEnabledCall: Pair<String, Boolean>? = null
        private set
    var deletedProviderId: String? = null
        private set

    override fun observeProviders(): Flow<List<Provider>> = providersFlow
    override suspend fun getProvider(providerId: String): Provider? = null
    override suspend fun getCredentials(providerId: String): ProviderCredentials? {
        credentialsProviderId = providerId
        return credentials[providerId]
    }
    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult {
        createRequest = request
        return ProviderSaveResult(provider = provider("created", "Created"), hasDuplicateName = false)
    }
    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult {
        updateRequest = request
        return ProviderSaveResult(provider = provider("updated", "Updated"), hasDuplicateName = false)
    }
    override suspend fun saveProvider(provider: Provider) = Unit
    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit
    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit
    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) {
        setEnabledCall = providerId to isEnabled
    }
    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) { }
    override suspend fun deleteProvider(providerId: String) {
        deletedProviderId = providerId
    }
}

private class FakeCategoryGroupRepository : CategoryGroupRepository {
    override fun observeManagedGroups(providerId: String, type: CategoryType): Flow<List<Category>> =
        flowOf(emptyList())
    override fun observeGroupSettings(providerId: String, type: CategoryType): Flow<CategoryGroupSettings> =
        flowOf(CategoryGroupSettings())
    override suspend fun setSortMode(providerId: String, type: CategoryType, mode: CategorySortMode) = Unit
    override suspend fun setHideNewGroups(providerId: String, type: CategoryType, hidden: Boolean) = Unit
    override suspend fun setGroupHidden(categoryId: String, hidden: Boolean) = Unit
    override suspend fun setAllGroupsHidden(providerId: String, type: CategoryType, hidden: Boolean) = Unit
    override suspend fun reorderGroups(orderedCategoryIds: List<String>) = Unit
    override suspend fun resetManualOrder(providerId: String, type: CategoryType) = Unit
}
