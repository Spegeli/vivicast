package com.vivicast.tv.feature.search

import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.SearchResults
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private val EMPTY_RESULTS = SearchResults(emptyList(), emptyList(), emptyList(), emptyList())

/**
 * Deterministic unit tests for [SearchViewModel]. Uses a fake [MediaRepository] and an
 * injected unconfined scope with debouncing disabled, so no real Room/network access and
 * no Main dispatcher / coroutines-test dependency are required.
 */
class SearchViewModelTest {

    private fun newViewModel(
        fake: FakeMediaRepository,
        scope: CoroutineScope,
    ): SearchViewModel = SearchViewModel(fake, scope = scope, debounceMillis = 0L)

    @Test
    fun initialState_isEmpty() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(FakeMediaRepository(), scope)

        val state = vm.uiState.value
        assertEquals("", state.query)
        assertEquals("", state.debouncedQuery)
        assertTrue(state.history.isEmpty())
        assertTrue(state.results.channels.isEmpty())
        assertTrue(state.results.movies.isEmpty())

        scope.cancel()
    }

    @Test
    fun onQueryChanged_updatesQueryImmediately() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(FakeMediaRepository(), scope)

        vm.onQueryChanged("ab")

        assertEquals("ab", vm.uiState.value.query)
        scope.cancel()
    }

    @Test
    fun onQueryChanged_populatesResultsAndDebouncedQuery() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val fake = FakeMediaRepository()
        val marker = SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
        fake.resultsFor = { query -> if (query == "bat") marker else EMPTY_RESULTS }
        val vm = newViewModel(fake, scope)

        vm.onQueryChanged("bat")

        val state = vm.uiState.value
        assertEquals("bat", state.debouncedQuery)
        assertSame(marker, state.results)
        assertEquals("bat", fake.searchQueries.last())
        scope.cancel()
    }

    @Test
    fun blankQuery_keepsResultsEmpty() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(FakeMediaRepository(), scope)

        vm.onQueryChanged("")

        val state = vm.uiState.value
        assertEquals("", state.debouncedQuery)
        assertTrue(state.results.channels.isEmpty())
        scope.cancel()
    }

    @Test
    fun shortQuery_isNotAddedToHistory_longQueryIs() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val fake = FakeMediaRepository()
        val vm = newViewModel(fake, scope)

        vm.onQueryChanged("b")
        assertFalse(fake.addedHistory.contains("b"))

        vm.onQueryChanged("bat")
        assertTrue(fake.addedHistory.contains("bat"))
        assertTrue(vm.uiState.value.history.contains("bat"))
        scope.cancel()
    }

    @Test
    fun history_isReflectedFromRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val fake = FakeMediaRepository()
        fake.historyFlow.value = listOf("older", "newer")
        val vm = newViewModel(fake, scope)

        assertEquals(listOf("older", "newer"), vm.uiState.value.history)
        scope.cancel()
    }

    @Test
    fun onHistoryRemoved_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val fake = FakeMediaRepository()
        fake.historyFlow.value = listOf("x", "y")
        val vm = newViewModel(fake, scope)

        vm.onHistoryRemoved("x")

        assertTrue(fake.deletedHistory.contains("x"))
        assertEquals(listOf("y"), vm.uiState.value.history)
        scope.cancel()
    }

    @Test
    fun onClearHistory_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val fake = FakeMediaRepository()
        fake.historyFlow.value = listOf("x", "y")
        val vm = newViewModel(fake, scope)

        vm.onClearHistory()

        assertEquals(1, fake.clearCount)
        assertTrue(vm.uiState.value.history.isEmpty())
        scope.cancel()
    }

    @Test
    fun onHistorySelected_updatesQueryAndTriggersSearch() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val fake = FakeMediaRepository()
        val vm = newViewModel(fake, scope)

        vm.onHistorySelected("marvel")

        assertEquals("marvel", vm.uiState.value.query)
        assertEquals("marvel", vm.uiState.value.debouncedQuery)
        assertEquals("marvel", fake.searchQueries.last())
        scope.cancel()
    }
}

private class FakeMediaRepository : MediaRepository {
    val historyFlow = MutableStateFlow<List<String>>(emptyList())
    val searchQueries = mutableListOf<String>()
    val addedHistory = mutableListOf<String>()
    val deletedHistory = mutableListOf<String>()
    var clearCount = 0
    var resultsFor: (String) -> SearchResults = { EMPTY_RESULTS }

    override suspend fun search(query: String, limitPerType: Int): SearchResults {
        searchQueries += query
        return resultsFor(query)
    }

    override fun observeSearchHistory(limit: Int): Flow<List<String>> = historyFlow

    override suspend fun addSearchHistory(query: String) {
        addedHistory += query
        if (query !in historyFlow.value) {
            historyFlow.value = listOf(query) + historyFlow.value
        }
    }

    override suspend fun deleteSearchHistory(query: String) {
        deletedHistory += query
        historyFlow.value = historyFlow.value - query
    }

    override suspend fun clearSearchHistory() {
        clearCount++
        historyFlow.value = emptyList()
    }

    // Unused members required by the interface.
    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> = emptyFlow()
    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = emptyFlow()
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = emptyFlow()
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = emptyFlow()
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = emptyFlow()
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = emptyFlow()
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = null
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = null
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = null
}
