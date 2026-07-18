package com.vivicast.tv.feature.livetv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
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
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun providerTreeStartsExpandedAndCenterPersistsExpandedState() {
        val expandedProviderIds = mutableStateOf(emptySet<String>())

        compose.setContent {
            TestLiveTvRoute(
                expandedProviderIds = expandedProviderIds.value,
                onExpandedProviderIdsChanged = { expandedProviderIds.value = it },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) { PROVIDER_ID in expandedProviderIds.value }
        compose.onNodeWithText("Favoriten").assertIsDisplayed()
        compose.onAllNodesWithText("News").assertCountEquals(1)

        compose.onNodeWithTag(providerTreeProviderTag(PROVIDER_ID)).performSemanticsAction(SemanticsActions.OnClick)

        compose.runOnIdle {
            check(PROVIDER_ID !in expandedProviderIds.value)
        }
        compose.onAllNodesWithText("News").assertCountEquals(0)
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
        compose.onNodeWithTag(epgProgramRowTag(TEST_PROGRAM.id)).assertIsDisplayed()
        compose.onNodeWithTag(epgProgramRowTag(TEST_PROGRAM.id)).assertIsFocused()
    }

    @Test
    fun channelKeysMoveFocusWithinBrowserChannelList() {
        compose.setContent {
            TestLiveTvRoute(expandedProviderIds = setOf(PROVIDER_ID))
        }

        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).assertIsFocused()

        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).performKeyInput {
            pressKey(Key.ChannelDown)
        }
        compose.onNodeWithTag(channelRowTag(NEWS_SECOND_CHANNEL_ID)).assertIsFocused()

        compose.onNodeWithTag(channelRowTag(NEWS_SECOND_CHANNEL_ID)).performKeyInput {
            pressKey(Key.ChannelUp)
        }
        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).assertIsFocused()
    }

    @Test
    fun currentEpgStartsFullscreen() {
        var openedChannelId: String? = null

        compose.setContent {
            TestLiveTvRoute(
                expandedProviderIds = setOf(PROVIDER_ID),
                onOpenPlayer = { openedChannelId = it.id },
            )
        }

        compose.onNodeWithTag(providerTreeCategoryTag(NEWS_CATEGORY_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(epgProgramRowTag(TEST_PROGRAM.id)).performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { check(openedChannelId == NEWS_CHANNEL_ID) }
    }

    @Test
    fun searchTargetOpensChannelModeAndFocusesProgram() {
        var consumed = false

        compose.setContent {
            TestLiveTvRoute(
                expandedProviderIds = setOf(PROVIDER_ID),
                targetProviderId = PROVIDER_ID,
                targetCategoryId = NEWS_CATEGORY_ID,
                targetChannelId = NEWS_CHANNEL_ID,
                targetEpgProgramId = TEST_PROGRAM.id,
                targetEpgStartTime = TEST_PROGRAM.startTime,
                onTargetConsumed = { consumed = true },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) { consumed }
        compose.onNodeWithText("Sender-EPG").assertIsDisplayed()
        compose.onNodeWithTag(epgProgramRowTag(TEST_PROGRAM.id)).assertIsFocused()
    }


    @Test
    fun noEpgPlaceholderStartsFullscreen() {
        var openedChannelId: String? = null

        compose.setContent {
            TestLiveTvRoute(
                expandedProviderIds = setOf(PROVIDER_ID),
                onOpenPlayer = { openedChannelId = it.id },
            )
        }

        compose.onNodeWithTag(providerTreeCategoryTag(SPORTS_CATEGORY_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(channelRowTag(SPORTS_CHANNEL_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(noEpgPlaceholderTag(SPORTS_CHANNEL_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { check(openedChannelId == SPORTS_CHANNEL_ID) }
    }

    @Test
    fun backStepsFromEpgToChannelListThenProviderTree() {
        compose.setContent {
            TestLiveTvRoute(expandedProviderIds = setOf(PROVIDER_ID))
        }

        compose.onNodeWithTag(providerTreeCategoryTag(NEWS_CATEGORY_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(epgProgramRowTag(TEST_PROGRAM.id)).assertIsFocused()

        pressBack()

        compose.onNodeWithTag(channelRowTag(NEWS_CHANNEL_ID)).assertIsFocused()
        compose.onNodeWithText("Sender-EPG").assertIsDisplayed()

        pressBack()

        compose.onNodeWithTag(providerTreeCategoryTag(NEWS_CATEGORY_ID)).assertIsFocused()
        compose.onAllNodesWithText("Sender-EPG").assertCountEquals(0)
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }
}

@Composable
private fun TestLiveTvRoute(
    expandedProviderIds: Set<String>,
    onExpandedProviderIdsChanged: (Set<String>) -> Unit = {},
    onOpenPlayer: (Channel) -> Unit = {},
    targetProviderId: String? = null,
    targetCategoryId: String? = null,
    targetChannelId: String? = null,
    targetEpgProgramId: String? = null,
    targetEpgStartTime: Long? = null,
    onTargetConsumed: () -> Unit = {},
) {
    LiveTvRoute(
        providerRepository = FakeProviderRepository(),
        mediaRepository = FakeMediaRepository(),
        epgRepository = FakeEpgRepository(),
        favoritesRepository = FakeFavoritesRepository(),
        expandedProviderIds = expandedProviderIds,
        onExpandedProviderIdsChanged = onExpandedProviderIdsChanged,
        onOpenPlayer = onOpenPlayer,
        targetProviderId = targetProviderId,
        targetCategoryId = targetCategoryId,
        targetChannelId = targetChannelId,
        targetEpgProgramId = targetEpgProgramId,
        targetEpgStartTime = targetEpgStartTime,
        onTargetConsumed = onTargetConsumed,
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
    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) = Unit
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
    override suspend fun getChannel(providerId: String, channelId: String): Channel? =
        TEST_CHANNELS.firstOrNull { it.providerId == providerId && it.id == channelId }
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = null
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = null
    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakeEpgRepository : EpgRepository {
    override fun observeEpgSources(): Flow<List<EpgSource>> = flowOf(emptyList())
    override suspend fun getEpgSources(): List<EpgSource> = emptyList()
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
    override fun observeFavorites(mediaType: MediaType): Flow<List<Favorite>> = flowOf(emptyList())
    override suspend fun isFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = false
    override suspend fun addFavorite(favorite: Favorite) = Unit
    override suspend fun removeFavorite(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun toggleFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = true
}

private const val PROVIDER_ID = "provider-a"
private const val NEWS_CATEGORY_ID = "category-news"
private const val SPORTS_CATEGORY_ID = "category-sports"
private const val NEWS_CHANNEL_ID = "channel-news-one"
private const val NEWS_SECOND_CHANNEL_ID = "channel-news-two"
private const val SPORTS_CHANNEL_ID = "channel-sports-one"

private val TEST_PROVIDER = Provider(
    id = PROVIDER_ID,
    name = "Provider Alpha",
    type = ProviderType.M3u,
    sourceConfigKey = "credentials-provider-a",
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
    Channel(NEWS_SECOND_CHANNEL_ID, PROVIDER_ID, NEWS_CATEGORY_ID, "news-two", "2", "News Two", null, false, 0),
    Channel(SPORTS_CHANNEL_ID, PROVIDER_ID, SPORTS_CATEGORY_ID, "sports-one", "2", "Sports One", null, false, 0),
)

private val TEST_PROGRAM = EpgProgram(
    id = "program-news",
    providerId = PROVIDER_ID,
    channelId = NEWS_CHANNEL_ID,
    epgSourceId = "epg-a",
    epgChannelId = "news-one",
    title = "Morning News",
    subtitle = null,
    description = "Headlines.",
    startTime = System.currentTimeMillis() - 60_000L,
    endTime = System.currentTimeMillis() + 600_000L,
    category = "News",
    iconUrl = null,
    isCatchupAvailable = false,
)
