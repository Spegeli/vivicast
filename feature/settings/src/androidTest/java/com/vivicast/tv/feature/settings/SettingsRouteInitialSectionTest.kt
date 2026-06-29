package com.vivicast.tv.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourcePriorityDirection
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
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
    fun playlistAddActionOpensNameStep() {
        setSettingsRouteContent(initialSelectedSection = "Wiedergabelisten")

        compose.onNodeWithTag("settings-playlist-add-action").performClick()

        compose.onNodeWithText("Name *").assertIsDisplayed()
        compose.onNodeWithText("Weiter").assertIsDisplayed()
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
                generalSettingsState = GeneralSettingsState(),
                epgSettingsState = EpgSettingsState(),
                playbackSettingsState = PlaybackSettingsState(),
                cacheSettingsState = CacheSettingsState(),
                aboutAppState = AboutAppState(),
                initialSelectedSection = initialSelectedSection,
                onTestProviderConnection = { null },
                onProviderSaved = {},
                onBackgroundRefreshChanged = {},
                onRememberSortingChanged = {},
                onEpgPreferencesChanged = {},
                onPlaybackPreferencesChanged = {},
                onRunGlobalRefresh = {},
                onClearCache = {},
                onClearHistory = {},
                onReloadCacheStats = {},
            )
        }
    }
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
    override suspend fun deleteSource(sourceId: String) = Unit
    override suspend fun linkSourceToProvider(providerId: String, epgSourceId: String, priority: Int) = Unit
    override suspend fun unlinkSourceFromProvider(providerId: String, epgSourceId: String) = Unit
    override suspend fun moveSourcePriority(
        providerId: String,
        epgSourceId: String,
        direction: EpgSourcePriorityDirection,
    ) = Unit
}

