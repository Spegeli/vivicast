package com.vivicast.tv.feature.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.vivicast.tv.data.epg.EpgRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.domain.model.SearchResults
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class LiveTvRouteFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun providerTreeStartsCollapsedAndCenterPersistsExpandedState() {
        val expandedProviderIds = mutableStateOf(emptySet<String>())

        compose.setContent {
            TestLiveTvRoute(
                expandedProviderIds = expandedProviderIds.value,
                onExpandedProviderIdsChanged = { expandedProviderIds.value = it },
            )
        }

        compose.onAllNodesWithText("News").assertCountEquals(0)

        compose.onNodeWithTag(providerTreeProviderTag(PROVIDER_ID)).performSemanticsAction(SemanticsActions.OnClick)

        compose.runOnIdle {
            check(PROVIDER_ID in expandedProviderIds.value)
        }
        compose.onNodeWithText("Favoriten").assertIsDisplayed()
        compose.onNodeWithText("News").assertIsDisplayed()
    }

    @Test
    fun categorySelectionUpdatesChannelListWithoutPreviewStart() {
        compose.setContent {
            TestLiveTvRoute(expandedProviderIds = setOf(PROVIDER_ID))
        }

        compose.onNodeWithTag(providerTreeCategoryTag(NEWS_CATEGORY_ID)).performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).assertIsDisplayed()
        compose.onAllNodesWithTag(channelRowTag(SPORTS_CHANNEL_ID)).assertCountEquals(0)
        compose.onNodeWithText("OK startet Vorschau").assertIsDisplayed()
    }

    @Test
    fun channelSelectionOpensEpgColumnForFocusedChannel() {
        compose.setContent {
            TestLiveTvRoute(expandedProviderIds = setOf(PROVIDER_ID))
        }

        compose.onNodeWithTag(providerTreeCategoryTag(NEWS_CATEGORY_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).performSemanticsAction(SemanticsActions.OnClick)

        compose.waitForIdle()
        compose.onNodeWithText("Sender-EPG").assertIsDisplayed()
        compose.onNodeWithText("Morning News").assertIsDisplayed()
    }
}

@Composable
private fun TestLiveTvRoute(
    expandedProviderIds: Set<String>,
    onExpandedProviderIdsChanged: (Set<String>) -> Unit = {},
) {
    LiveTvRoute(
        providerRepository = FakeProviderRepository(),
        mediaRepository = FakeMediaRepository(),
        epgRepository = FakeEpgRepository(),
        favoritesRepository = FakeFavoritesRepository(),
        expandedProviderIds = expandedProviderIds,
        onExpandedProviderIdsChanged = onExpandedProviderIdsChanged,
    )
}

private class FakeProviderRepository : ProviderRepository {
    override fun observeProviders(): Flow<List<Provider>> = flowOf(listOf(TEST_PROVIDER))
    override suspend fun getProvider(providerId: String): Provider? = TEST_PROVIDER.takeIf { it.id == providerId }
    override suspend fun getCredentials(providerId: String): ProviderCredentials? = null
    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
        ProviderSaveResult(TEST_PROVIDER, hasDuplicateName = false)

    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
        ProviderSaveResult(TEST_PROVIDER, hasDuplicateName = false)
    override suspend fun saveProvider(provider: Provider) = Unit
    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit
    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit
    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
}

private class FakeMediaRepository : MediaRepository {
    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        flowOf(if (providerId == PROVIDER_ID && type == CategoryType.LiveTv) TEST_CATEGORIES else emptyList())

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> =
        flowOf(
            TEST_CHANNELS.filter { channel ->
                channel.providerId == providerId && (categoryId == null || channel.categoryId == categoryId)
            },
        )

    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = flowOf(emptyList())
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = flowOf(emptyList())
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = flowOf(emptyList())
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = flowOf(emptyList())
    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakeEpgRepository : EpgRepository {
    override fun observeEpgSources(): Flow<List<EpgSource>> = flowOf(emptyList())
    override fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>> = flowOf(emptyList())
    override fun observeChannelsForProvider(providerId: String): Flow<List<Channel>> = flowOf(TEST_CHANNELS)

    override fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>> =
        flowOf(if (channelId == NEWS_CHANNEL_ID) listOf(TEST_PROGRAM) else emptyList())

    override fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>> = flowOf(emptyList())
    override suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): EpgChannelMapping =
        error("Not used in Live-TV focus tests.")
    override suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String) = Unit
}

private class FakeFavoritesRepository : FavoritesRepository {
    override fun observeFavorites(providerId: String, mediaType: MediaType): Flow<List<Favorite>> = flowOf(emptyList())
    override suspend fun isFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = false
    override suspend fun addFavorite(favorite: Favorite) = Unit
    override suspend fun removeFavorite(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun toggleFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = true
}

private const val PROVIDER_ID = "provider-a"
private const val NEWS_CATEGORY_ID = "category-news"
private const val SPORTS_CATEGORY_ID = "category-sports"
private const val NEWS_CHANNEL_ID = "channel-news-one"
private const val SPORTS_CHANNEL_ID = "channel-sports-one"

private val TEST_PROVIDER = Provider(
    id = PROVIDER_ID,
    name = "Provider Alpha",
    type = ProviderType.M3u,
    credentialsKey = "credentials-provider-a",
    isActive = true,
    status = ProviderStatus.Active,
    includeLiveTv = true,
    includeMovies = false,
    includeSeries = false,
    refreshIntervalHours = 12,
    logoPriority = "provider",
    createdAt = 1L,
    updatedAt = 1L,
)

private val TEST_CATEGORIES = listOf(
    Category(NEWS_CATEGORY_ID, PROVIDER_ID, CategoryType.LiveTv, "news", "News", 0, false),
    Category(SPORTS_CATEGORY_ID, PROVIDER_ID, CategoryType.LiveTv, "sports", "Sport", 1, false),
)

private val TEST_CHANNELS = listOf(
    Channel(NEWS_CHANNEL_ID, PROVIDER_ID, NEWS_CATEGORY_ID, "news-one", "1", "News One", null, false, 0),
    Channel(SPORTS_CHANNEL_ID, PROVIDER_ID, SPORTS_CATEGORY_ID, "sports-one", "2", "Sports One", null, false, 0),
)

private val TEST_PROGRAM = EpgProgram(
    id = "program-news",
    providerId = PROVIDER_ID,
    channelId = NEWS_CHANNEL_ID,
    epgSourceId = "epg-a",
    externalChannelId = "news-one",
    title = "Morning News",
    subtitle = null,
    description = "Headlines.",
    startTime = 1_000L,
    endTime = 2_000L,
    category = "News",
    iconUrl = null,
    isCatchupAvailable = false,
)
