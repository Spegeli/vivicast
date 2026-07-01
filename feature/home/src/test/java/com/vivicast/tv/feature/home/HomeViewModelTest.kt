package com.vivicast.tv.feature.home

import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.SearchResults
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic unit tests for [HomeViewModel] using fake repositories and an injected
 * unconfined scope (no real Room/network, no Main dispatcher / coroutines-test dependency).
 */
class HomeViewModelTest {

    private fun newViewModel(
        playback: FakePlaybackRepository,
        media: FakeMediaRepository,
        scope: CoroutineScope,
    ): HomeViewModel = HomeViewModel(playback, media, scope = scope)

    @Test
    fun initialState_isEmpty() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(FakePlaybackRepository(), FakeMediaRepository(), scope)

        val state = vm.uiState.value
        assertTrue(state.continueItems.isEmpty())
        assertTrue(state.recentChannels.isEmpty())
        scope.cancel()
    }

    @Test
    fun continueWatching_movieIsResolvedIntoUiState() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(movies = mapOf("movie-1" to movie("movie-1", "Dune")))
        val playback = FakePlaybackRepository(
            continueWatching = listOf(movieProgress("p1", "movie-1")),
        )
        val vm = newViewModel(playback, media, scope)

        val items = vm.uiState.value.continueItems
        assertEquals(1, items.size)
        val movieItem = items.first() as ContinueHomeItem.MovieItem
        assertEquals("Dune", movieItem.movie.name)
        scope.cancel()
    }

    @Test
    fun continueWatching_episodeIsResolvedIntoUiState() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(episodes = mapOf("ep-1" to episode("ep-1", "Chapter 1")))
        val playback = FakePlaybackRepository(
            continueWatching = listOf(episodeProgress("p2", "ep-1")),
        )
        val vm = newViewModel(playback, media, scope)

        val items = vm.uiState.value.continueItems
        assertEquals(1, items.size)
        assertTrue(items.first() is ContinueHomeItem.EpisodeItem)
        scope.cancel()
    }

    @Test
    fun unresolvableContinueEntries_areDropped() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // Media repository knows no movies -> getMovie returns null -> entry dropped.
        val playback = FakePlaybackRepository(
            continueWatching = listOf(movieProgress("p1", "missing")),
        )
        val vm = newViewModel(playback, FakeMediaRepository(), scope)

        assertTrue(vm.uiState.value.continueItems.isEmpty())
        scope.cancel()
    }

    @Test
    fun recentChannels_areResolvedIntoUiState() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(channels = mapOf("ch-1" to channel("ch-1", "ARD")))
        val playback = FakePlaybackRepository(
            recentChannels = listOf(channelHistory("h1", "ch-1")),
        )
        val vm = newViewModel(playback, media, scope)

        val recent = vm.uiState.value.recentChannels
        assertEquals(1, recent.size)
        assertEquals("ARD", recent.first().channel.name)
        scope.cancel()
    }

    @Test
    fun emptyRepositories_produceEmptyState() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(
            FakePlaybackRepository(continueWatching = emptyList(), recentChannels = emptyList()),
            FakeMediaRepository(),
            scope,
        )

        assertTrue(vm.uiState.value.continueItems.isEmpty())
        assertTrue(vm.uiState.value.recentChannels.isEmpty())
        scope.cancel()
    }

    @Test
    fun continueWatchingUpdates_areReflected() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(movies = mapOf("movie-1" to movie("movie-1", "Dune")))
        val playback = FakePlaybackRepository()
        val vm = newViewModel(playback, media, scope)
        assertTrue(vm.uiState.value.continueItems.isEmpty())

        playback.continueWatchingFlow.value = listOf(movieProgress("p1", "movie-1"))

        assertEquals(1, vm.uiState.value.continueItems.size)
        scope.cancel()
    }
}

private fun movie(id: String, name: String) = Movie(
    id = id, providerId = "prov", categoryId = "cat", remoteId = id, name = name,
    originalName = null, containerExtension = "mp4", posterUrl = null, backdropUrl = null,
    rating = null, year = null, genre = null, duration = null, director = null, cast = null,
    plot = null, trailerUrl = null, addedAt = null,
)

private fun episode(id: String, name: String) = Episode(
    id = id, providerId = "prov", seriesId = "series", seasonId = "season", remoteId = id,
    episodeNumber = 1, seasonNumber = 1, name = name, plot = null, thumbnailUrl = null,
    containerExtension = "mp4", duration = null, airDate = null,
)

private fun channel(id: String, name: String) = Channel(
    id = id, providerId = "prov", categoryId = "cat", remoteId = id, channelNumber = "1",
    name = name, logoUrl = null, isCatchupAvailable = false, catchupDays = 0,
)

private fun movieProgress(id: String, mediaId: String) = PlaybackProgress(
    id = id, providerId = "prov", mediaType = MediaType.Movie, mediaId = mediaId,
    positionMillis = 10L, durationMillis = 100L, progressPercent = 10, isCompleted = false,
    lastWatchedAt = 1L, createdAt = 1L, updatedAt = 1L,
)

private fun episodeProgress(id: String, mediaId: String) = PlaybackProgress(
    id = id, providerId = "prov", mediaType = MediaType.Episode, mediaId = mediaId,
    positionMillis = 10L, durationMillis = 100L, progressPercent = 10, isCompleted = false,
    lastWatchedAt = 1L, createdAt = 1L, updatedAt = 1L,
)

private fun channelHistory(id: String, channelId: String) = ChannelHistory(
    id = id, providerId = "prov", channelId = channelId, watchedAt = 1L,
    durationWatchedMillis = 120_000L, updatedAt = 1L,
)

private class FakePlaybackRepository(
    continueWatching: List<PlaybackProgress> = emptyList(),
    recentChannels: List<ChannelHistory> = emptyList(),
) : PlaybackRepository {
    val continueWatchingFlow = MutableStateFlow(continueWatching)
    val recentChannelsFlow = MutableStateFlow(recentChannels)

    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> = continueWatchingFlow
    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> = recentChannelsFlow

    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> = flowOf(emptyList())
    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? = null
    override suspend fun saveProgress(progress: PlaybackProgress) = Unit
    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun saveChannelHistory(history: ChannelHistory) = Unit
    override suspend fun clearProviderPlayback(providerId: String) = Unit
    override suspend fun clearLiveTvHistory() = Unit
    override suspend fun clearMovieProgress() = Unit
    override suspend fun clearSeriesProgress() = Unit
}

private class FakeMediaRepository(
    private val movies: Map<String, Movie> = emptyMap(),
    private val episodes: Map<String, Episode> = emptyMap(),
    private val channels: Map<String, Channel> = emptyMap(),
) : MediaRepository {
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = movies[movieId]
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = episodes[episodeId]
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = channels[channelId]

    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())

    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> = emptyFlow()
    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = emptyFlow()
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = emptyFlow()
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = emptyFlow()
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = emptyFlow()
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = emptyFlow()
}
