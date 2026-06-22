package com.vivicast.tv.feature.search

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.vivicast.tv.core.datastore.AppearancePreferences
import com.vivicast.tv.core.datastore.CachePreferences
import com.vivicast.tv.core.datastore.GeneralPreferences
import com.vivicast.tv.core.datastore.HistoryPreferences
import com.vivicast.tv.core.datastore.ParentalControlPreferences
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.SearchResults
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class SearchRouteFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchFieldHasInitialFocus() {
        compose.setContent {
            SearchRoute(
                mediaRepository = FakeMediaRepository(),
                userPreferencesStore = FakeUserPreferencesStore(),
            )
        }

        compose.onNodeWithTag(searchInputTag()).assertIsFocused()
    }

    @Test
    fun typingDebouncesLocalSearchAndPersistsHistory() {
        val mediaRepository = FakeMediaRepository()
        val preferencesStore = FakeUserPreferencesStore()

        compose.setContent {
            SearchRoute(
                mediaRepository = mediaRepository,
                userPreferencesStore = preferencesStore,
            )
        }

        compose.onNodeWithTag(searchInputTag()).performTextInput("dune")

        compose.waitUntil(timeoutMillis = 5_000) {
            "dune" in mediaRepository.queries && "dune" in preferencesStore.current.searchHistory
        }
        compose.onNodeWithTag(searchResultTag("Kanaele", "Dune TV")).assertIsDisplayed()
        compose.onNodeWithTag(searchResultTag("Filme", "Dune")).assertIsDisplayed()
        compose.onNodeWithTag(searchResultTag("Serien", "Dune: Prophecy")).assertIsDisplayed()
        compose.onNodeWithTag(searchGroupTag("EPG")).performScrollTo()
        compose.onNodeWithTag(searchResultTag("EPG", "Dune Special")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun historySelectionRunsSearch() {
        val mediaRepository = FakeMediaRepository()
        val preferencesStore = FakeUserPreferencesStore(searchHistory = listOf("ARD", "Dune"))

        compose.setContent {
            SearchRoute(
                mediaRepository = mediaRepository,
                userPreferencesStore = preferencesStore,
            )
        }

        compose.onNodeWithTag(searchHistoryTermTag("Dune")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { "Dune" in mediaRepository.queries }
        compose.onNodeWithTag(searchResultTag("Filme", "Dune")).assertIsDisplayed()
    }

    @Test
    fun historyDeleteAndClearUpdatePreferences() {
        val preferencesStore = FakeUserPreferencesStore(searchHistory = listOf("ARD", "Dune"))

        compose.setContent {
            SearchRoute(
                mediaRepository = FakeMediaRepository(),
                userPreferencesStore = preferencesStore,
            )
        }

        compose.onNodeWithTag(searchHistoryDeleteTag("Dune")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { "Dune" !in preferencesStore.current.searchHistory }
        compose.onAllNodesWithTag(searchHistoryTermTag("Dune")).assertCountEquals(0)

        compose.onNodeWithTag(searchClearHistoryTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { preferencesStore.current.searchHistory.isEmpty() }
        compose.onAllNodesWithTag(searchHistoryTermTag("ARD")).assertCountEquals(0)
    }
}

private class FakeUserPreferencesStore(searchHistory: List<String> = emptyList()) : UserPreferencesStore {
    private val state = MutableStateFlow(UserPreferences(searchHistory = searchHistory))
    override val values: Flow<UserPreferences> = state
    val current: UserPreferences get() = state.value

    override suspend fun updateSelectedProviderId(providerId: String?) {
        state.value = state.value.copy(selectedProviderId = providerId)
    }

    override suspend fun updateGeneral(general: GeneralPreferences) {
        state.value = state.value.copy(general = general)
    }

    override suspend fun updateAppearance(appearance: AppearancePreferences) {
        state.value = state.value.copy(appearance = appearance)
    }

    override suspend fun updatePlayback(playback: PlaybackPreferences) {
        state.value = state.value.copy(playback = playback)
    }

    override suspend fun updateHistory(history: HistoryPreferences) {
        state.value = state.value.copy(history = history)
    }

    override suspend fun updateSearchHistory(searchHistory: List<String>) {
        state.value = state.value.copy(searchHistory = searchHistory)
    }

    override suspend fun updateExpandedLiveTvProviderIds(providerIds: Set<String>) {
        state.value = state.value.copy(expandedLiveTvProviderIds = providerIds)
    }

    override suspend fun updateCache(cache: CachePreferences) {
        state.value = state.value.copy(cache = cache)
    }

    override suspend fun updateParentalControl(parentalControl: ParentalControlPreferences) {
        state.value = state.value.copy(parentalControl = parentalControl)
    }
}

private class FakeMediaRepository : MediaRepository {
    val queries = mutableListOf<String>()

    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> = flowOf(emptyList())
    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = flowOf(emptyList())
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = flowOf(emptyList())
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = flowOf(emptyList())
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = flowOf(emptyList())
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = flowOf(emptyList())

    override suspend fun search(query: String, limitPerType: Int): SearchResults {
        queries += query
        return if (query.equals("dune", ignoreCase = true)) TEST_RESULTS else SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

private val TEST_RESULTS = SearchResults(
    channels = listOf(
        Channel(
            id = "channel-dune-tv",
            providerId = "provider-a",
            categoryId = "category-a",
            remoteId = "dune-tv",
            channelNumber = "42",
            name = "Dune TV",
            logoUrl = null,
            isCatchupAvailable = false,
            catchupDays = 0,
        ),
    ),
    movies = listOf(
        Movie(
            id = "movie-dune",
            providerId = "provider-a",
            categoryId = "movies-a",
            remoteId = "dune",
            name = "Dune",
            originalName = null,
            containerExtension = "mp4",
            posterUrl = null,
            backdropUrl = null,
            rating = "8.1",
            year = "2021",
            genre = "Sci-Fi",
            duration = null,
            director = null,
            cast = null,
            plot = null,
            trailerUrl = null,
            addedAt = null,
        ),
    ),
    series = listOf(
        Series(
            id = "series-dune",
            providerId = "provider-a",
            categoryId = "series-a",
            remoteId = "dune-prophecy",
            name = "Dune: Prophecy",
            originalName = null,
            posterUrl = null,
            backdropUrl = null,
            rating = "7.4",
            year = "2024",
            genre = "Sci-Fi",
            director = null,
            cast = null,
            plot = null,
            addedAt = null,
        ),
    ),
    epgPrograms = listOf(
        EpgProgram(
            id = "program-dune",
            providerId = "provider-a",
            channelId = "channel-dune-tv",
            epgSourceId = "epg-a",
            externalChannelId = "dune-tv",
            title = "Dune Special",
            subtitle = "Heute 22:15",
            description = null,
            startTime = 1_000L,
            endTime = 2_000L,
            category = "Sci-Fi",
            iconUrl = null,
            isCatchupAvailable = false,
        ),
    ),
)
