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
import com.vivicast.tv.core.datastore.ParentalControlPreferences
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    private fun newViewModel(
        scope: CoroutineScope,
        store: FakeUserPreferencesStore,
        cacheStore: FakeMediaCacheStore = FakeMediaCacheStore(),
    ): SettingsViewModel =
        SettingsViewModel(store, cacheStore, scope = scope)

    @Test
    fun initialState_mapsGeneralAppearancePlaybackFromPreferences() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore(
            UserPreferences(
                general = GeneralPreferences(launchOnBoot = true, globalUserAgent = "UA/2"),
                appearance = AppearancePreferences(
                    backgroundColor = ThemeColor.AmoledDark,
                    language = LanguagePreference.English,
                    fontScale = FontScalePreference.Large,
                ),
                playback = PlaybackPreferences(bufferSize = BufferSizePreference.Large, timeshiftMinutes = 60),
            ),
        )
        val vm = newViewModel(scope, store)

        val state = vm.uiState.value
        assertEquals(true, state.general.launchOnBoot)
        assertEquals("UA/2", state.general.globalUserAgent)
        assertEquals(SettingsLanguage.English, state.general.appLanguage)
        assertEquals(SettingsThemeMode.AmoledDark, state.appearance.themeMode)
        assertEquals(SettingsFontScale.Large, state.appearance.fontScale)
        assertEquals(PlaybackBufferSizeMode.Large, state.playback.bufferSize)
        assertEquals(60, state.playback.timeshiftMinutes)
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
    fun onRememberSortingChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onRememberSortingChanged(false)

        assertEquals(false, store.flow.value.general.rememberSorting)
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
    fun onSelectedSectionChanged_persistsSection() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onSelectedSectionChanged("EPG")

        assertEquals("EPG", store.flow.value.general.lastSettingsSection)
        scope.cancel()
    }

    @Test
    fun onAppearanceSettingsChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onAppearanceSettingsChanged(
            AppearanceSettingsState(
                themeMode = SettingsThemeMode.HighContrastDark,
                fontScale = SettingsFontScale.ExtraLarge,
            ),
        )

        assertEquals(ThemeColor.HighContrastDark, store.flow.value.appearance.backgroundColor)
        assertEquals(FontScalePreference.ExtraLarge, store.flow.value.appearance.fontScale)
        scope.cancel()
    }

    @Test
    fun initialState_mapsEpgAndDiagnostics() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore(
            UserPreferences(
                epg = EpgPreferences(refreshIntervalHours = 12, refreshOnAppStartEnabled = false),
                diagnostics = DiagnosticsPreferences(diagnosticsLoggingEnabled = true, retentionDays = 9),
            ),
        )
        val vm = newViewModel(scope, store)

        val state = vm.uiState.value
        assertEquals(12, state.epg.refreshIntervalHours)
        assertEquals(false, state.epg.refreshOnAppStartEnabled)
        assertEquals(true, state.diagnostics.diagnosticsLoggingEnabled)
        // retentionDays coerced into 1..7
        assertEquals(7, state.diagnostics.retentionDays)
        scope.cancel()
    }

    @Test
    fun onEpgSettingsChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onEpgSettingsChanged(EpgSettingsState(refreshIntervalHours = 6, refreshOnPlaylistChangeEnabled = false))

        assertEquals(6, store.flow.value.epg.refreshIntervalHours)
        assertEquals(false, store.flow.value.epg.refreshOnPlaylistChangeEnabled)
        scope.cancel()
    }

    @Test
    fun onDiagnosticsSettingsChanged_writesOnlyCoercedPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onDiagnosticsSettingsChanged(DiagnosticsSettingsState(diagnosticsLoggingEnabled = true, retentionDays = 42))

        assertEquals(true, store.flow.value.diagnostics.diagnosticsLoggingEnabled)
        assertEquals(7, store.flow.value.diagnostics.retentionDays)
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
    fun onPlaybackSettingsChanged_writesPreference() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = FakeUserPreferencesStore()
        val vm = newViewModel(scope, store)

        vm.onPlaybackSettingsChanged(
            PlaybackSettingsState(bufferSize = PlaybackBufferSizeMode.ExtraLarge, autoNextEnabled = true, autoNextCountdownSeconds = 20),
        )

        assertEquals(BufferSizePreference.ExtraLarge, store.flow.value.playback.bufferSize)
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
    override suspend fun updateParentalControl(parentalControl: ParentalControlPreferences) = Unit
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
