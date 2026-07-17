package com.vivicast.tv.feature.settings

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vivicast.tv.core.cache.MediaCacheCleanupResult
import com.vivicast.tv.core.cache.MediaCacheEntry
import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheStats
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.datastore.AppearancePreferences
import com.vivicast.tv.core.datastore.BackupPreferences
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.EpgPreferences
import com.vivicast.tv.core.datastore.GeneralPreferences
import com.vivicast.tv.core.datastore.HistoryPreferences
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.data.media.CategoryGroupRepository
import com.vivicast.tv.data.provider.ProviderConnectionTestResult
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
import com.vivicast.tv.domain.model.EpgChannelMapping as DomainEpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram as DomainEpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class SettingsRouteInitialSectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun initialSelectedSectionRestoresBackupSection() {
        setSettingsRouteContent(initialSelectedSection = "Backup")

        compose.onNodeWithText("BACKUP").assertIsDisplayed()
        compose.onNodeWithText("Backup-Ziel").assertIsDisplayed()
        compose.onNodeWithText("Letzte Sicherung").assertIsDisplayed()
    }

    @Test
    fun initialSelectedSectionRestoresPlaylistSection() {
        setSettingsRouteContent(initialSelectedSection = "Wiedergabelisten")

        compose.onNodeWithText("WIEDERGABELISTEN").assertIsDisplayed()
        compose.onNodeWithText("Wiedergabeliste hinzufügen").assertIsDisplayed()
        compose.onNodeWithText("Alle Wiedergabelisten aktualisieren").assertIsDisplayed()
    }

    @Test
    fun playlistAddActionOpensInlineForm() {
        setSettingsRouteContent(initialSelectedSection = "Wiedergabelisten")

        compose.onNodeWithTag("settings-playlist-add-action").performClick()

        // Inline single-page form (no step wizard): all three source types are offered at once,
        // which the old Name -> Type -> ... step flow never showed together.
        compose.onNodeWithText("M3U URL").assertIsDisplayed()
        compose.onNodeWithText("M3U Datei").assertIsDisplayed()
        compose.onNodeWithText("Xtream Codes").assertIsDisplayed()
    }

    @Test
    fun initialSelectedSectionRestoresEpgSection() {
        setSettingsRouteContent(initialSelectedSection = "EPG")

        compose.onNodeWithText("EPG Quelle hinzufügen").assertIsDisplayed()
    }

    private fun setSettingsRouteContent(initialSelectedSection: String? = null) {
        compose.setContent {
            SettingsRoute(
                providerRepository = EmptyProviderRepository,
                epgSourceRepository = EmptyEpgSourceRepository,
                categoryGroupRepository = EmptyCategoryGroupRepository,
                userPreferencesStore = EmptyUserPreferencesStore,
                mediaCacheStore = EmptyMediaCacheStore,
                aboutAppState = AboutAppState(),
                topNavFocusRequester = FocusRequester(),
                initialSelectedSection = initialSelectedSection,
                onTestProviderConnection = { ProviderConnectionTestResult(errorMessage = null, summary = null) },
                onProviderSaved = {},
                onBackgroundRefreshChanged = {},
                onRefreshEpgSource = {},
                onClearHistory = {},
            )
        }
    }
}

private object EmptyUserPreferencesStore : UserPreferencesStore {
    override val values: Flow<UserPreferences> = flowOf(UserPreferences())
    override suspend fun updateSelectedProviderId(providerId: String?) = Unit
    override suspend fun updateLocalLogoFolder(path: String?) = Unit
    override suspend fun updateGeneral(general: GeneralPreferences) = Unit
    override suspend fun updateAppearance(appearance: AppearancePreferences) = Unit
    override suspend fun updatePlayback(playback: PlaybackPreferences) = Unit
    override suspend fun updateHistory(history: HistoryPreferences) = Unit
    override suspend fun updateSearchHistory(searchHistory: List<String>) = Unit
    override suspend fun updateExpandedLiveTvProviderIds(providerIds: Set<String>) = Unit
    override suspend fun updateEpg(epg: EpgPreferences) = Unit
    override suspend fun updateBackup(backup: BackupPreferences) = Unit
    override suspend fun updateDiagnostics(diagnostics: DiagnosticsPreferences) = Unit
}

private object EmptyMediaCacheStore : MediaCacheStore {
    override suspend fun hasEntry(key: MediaCacheKey): Boolean = false
    override suspend fun getEntry(key: MediaCacheKey): MediaCacheEntry? = null
    override suspend fun put(key: MediaCacheKey, bytes: ByteArray): MediaCacheEntry =
        throw UnsupportedOperationException()
    override suspend fun stats(): MediaCacheStats = MediaCacheStats(totalSizeBytes = 0, fileCount = 0)
    override suspend fun cleanup(maxSizeBytes: Long): MediaCacheCleanupResult =
        MediaCacheCleanupResult(removedFiles = 0, removedBytes = 0, remainingBytes = 0)
    override suspend fun clear(): MediaCacheCleanupResult =
        MediaCacheCleanupResult(removedFiles = 0, removedBytes = 0, remainingBytes = 0)
}

private object EmptyProviderRepository : ProviderRepository {
    override fun observeProviders(): Flow<List<Provider>> = flowOf(emptyList())
    override suspend fun getProvider(providerId: String): Provider? = null
    override suspend fun getCredentials(providerId: String): ProviderCredentials? = null
    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
        ProviderSaveResult(provider = EMPTY_PROVIDER, hasDuplicateName = false)
    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
        ProviderSaveResult(provider = EMPTY_PROVIDER, hasDuplicateName = false)
    override suspend fun saveProvider(provider: Provider) = Unit
    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit
    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit
    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit
    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
}

private val EMPTY_PROVIDER = Provider(
    id = "empty-provider",
    name = "Provider",
    type = ProviderType.M3u,
    sourceConfigKey = "empty-provider-source",
    isActive = true,
    status = ProviderStatus.CredentialsRequired,
    includeLiveTv = true,
    includeMovies = true,
    includeSeries = true,
    refreshIntervalHours = 24,
    logoPriority = "playlist",
    createdAt = 0L,
    updatedAt = 0L,
)

private object EmptyCategoryGroupRepository : CategoryGroupRepository {
    override fun observeManagedGroups(providerId: String, type: CategoryType): Flow<List<Category>> = flowOf(emptyList())
    override fun observeGroupSettings(providerId: String, type: CategoryType): Flow<CategoryGroupSettings> =
        flowOf(CategoryGroupSettings())
    override suspend fun setSortMode(providerId: String, type: CategoryType, mode: CategorySortMode) = Unit
    override suspend fun setHideNewGroups(providerId: String, type: CategoryType, hidden: Boolean) = Unit
    override suspend fun setGroupHidden(categoryId: String, hidden: Boolean) = Unit
    override suspend fun setAllGroupsHidden(providerId: String, type: CategoryType, hidden: Boolean) = Unit
    override suspend fun reorderGroups(orderedCategoryIds: List<String>) = Unit
    override suspend fun resetManualOrder(providerId: String, type: CategoryType) = Unit
}

private object EmptyEpgSourceRepository : EpgSourceRepository {
    override fun observeEpgSources(): Flow<List<EpgSource>> = flowOf(emptyList())
    override fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>> = flowOf(emptyList())
    override fun observeChannelsForProvider(providerId: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<DomainEpgProgram>> = flowOf(emptyList())
    override fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<DomainEpgChannelMapping>> =
        flowOf(emptyList())
    override suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): DomainEpgChannelMapping =
        error("No EPG mapping in this test.")
    override suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String) = Unit
    override suspend fun saveSource(request: EpgSourceEditRequest): EpgSource = error("No EPG source in this test.")
    override suspend fun getSourceUrl(sourceId: String): String? = null
    override suspend fun deleteSource(sourceId: String) = Unit
    override suspend fun linkSourceToProvider(providerId: String, epgSourceId: String, priority: Int) = Unit
    override suspend fun unlinkSourceFromProvider(providerId: String, epgSourceId: String) = Unit
}

