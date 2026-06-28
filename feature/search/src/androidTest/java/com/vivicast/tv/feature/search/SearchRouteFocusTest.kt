package com.vivicast.tv.feature.search

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
            )
        }

        compose.onNodeWithTag(searchInputTag()).assertIsFocused()
        compose.onNodeWithTag(searchVoiceTag()).assertTextEquals("Mikrofon")
    }

    @Test
    fun typingDebouncesLocalSearchAndPersistsHistory() {
        val mediaRepository = FakeMediaRepository()

        compose.setContent {
            SearchRoute(
                mediaRepository = mediaRepository,
            )
        }

        compose.onNodeWithTag(searchInputTag()).performTextInput("dune")

        compose.waitUntil(timeoutMillis = 5_000) {
            "dune" in mediaRepository.queries && "dune" in mediaRepository.currentHistory
        }
        compose.onNodeWithTag(searchResultTag("Kanäle", "Dune TV")).assertIsDisplayed()
        compose.onNodeWithTag(searchResultTag("Filme", "Dune")).assertIsDisplayed()
        compose.onNodeWithTag(searchGroupTag("Serien")).performScrollTo()
        compose.onNodeWithTag(searchResultTag("Serien", "Dune: Prophecy")).assertIsDisplayed()
        compose.onNodeWithTag(searchGroupTag("EPG")).performScrollTo()
        compose.onNodeWithTag(searchResultTag("EPG", "Dune Special")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun historySelectionRunsSearch() {
        val mediaRepository = FakeMediaRepository(searchHistory = listOf("ARD", "Dune"))

        compose.setContent {
            SearchRoute(
                mediaRepository = mediaRepository,
            )
        }

        compose.onNodeWithTag(searchHistoryTermTag("Dune")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { "Dune" in mediaRepository.queries }
        compose.onNodeWithTag(searchResultTag("Filme", "Dune")).assertIsDisplayed()
    }

    @Test
    fun historyDeleteAndClearUpdatePreferences() {
        val mediaRepository = FakeMediaRepository(searchHistory = listOf("ARD", "Dune"))

        compose.setContent {
            SearchRoute(
                mediaRepository = mediaRepository,
            )
        }

        compose.onNodeWithTag(searchHistoryDeleteTag("Dune")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { "Dune" !in mediaRepository.currentHistory }
        compose.onAllNodesWithTag(searchHistoryTermTag("Dune")).assertCountEquals(0)

        compose.onNodeWithTag(searchClearHistoryTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { mediaRepository.currentHistory.isEmpty() }
        compose.onAllNodesWithTag(searchHistoryTermTag("ARD")).assertCountEquals(0)
    }

    @Test
    fun resultClickCallsTypedAction() {
        val mediaRepository = FakeMediaRepository()
        var openedProgramId: String? = null

        compose.setContent {
            SearchRoute(
                mediaRepository = mediaRepository,
                onOpenEpgProgram = { openedProgramId = it.id },
            )
        }

        compose.onNodeWithTag(searchInputTag()).performTextInput("dune")
        compose.waitUntil(timeoutMillis = 5_000) { "dune" in mediaRepository.queries }
        compose.onNodeWithTag(searchGroupTag("EPG")).performScrollTo()
        compose.onNodeWithTag(searchResultTag("EPG", "Dune Special")).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        compose.runOnIdle { check(openedProgramId == "program-dune") }
    }
}

private class FakeMediaRepository(searchHistory: List<String> = emptyList()) : MediaRepository {
    val queries = mutableListOf<String>()
    private val history = MutableStateFlow(searchHistory)
    val currentHistory: List<String> get() = history.value

    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> = flowOf(emptyList())
    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = flowOf(emptyList())
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = flowOf(emptyList())
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = flowOf(emptyList())
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = flowOf(emptyList())
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = flowOf(emptyList())
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = null
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = null
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = null

    override suspend fun search(query: String, limitPerType: Int): SearchResults {
        queries += query
        return if (query.equals("dune", ignoreCase = true)) TEST_RESULTS else SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
    }

    override fun observeSearchHistory(limit: Int): Flow<List<String>> = history

    override suspend fun addSearchHistory(query: String) {
        history.value = (listOf(query.trim()) + history.value)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(20)
    }

    override suspend fun deleteSearchHistory(query: String) {
        history.value = history.value.filterNot { it.equals(query, ignoreCase = true) }
    }

    override suspend fun clearSearchHistory() {
        history.value = emptyList()
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
            epgChannelId = "dune-tv",
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
