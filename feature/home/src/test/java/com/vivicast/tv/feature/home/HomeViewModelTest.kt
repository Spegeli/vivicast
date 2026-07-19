package com.vivicast.tv.feature.home

import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic unit tests for [HomeViewModel] using fake repositories and an injected
 * unconfined scope (no real Room/network, no Main dispatcher / coroutines-test dependency).
 */
class HomeViewModelTest {

    private fun newViewModel(
        playback: FakePlaybackRepository = FakePlaybackRepository(),
        media: FakeMediaRepository = FakeMediaRepository(),
        provider: FakeProviderRepository = FakeProviderRepository(),
        scope: CoroutineScope,
    ): HomeViewModel = HomeViewModel(playback, media, provider, scope = scope)

    @Test
    fun noProviders_showsNoPlaylistEmptyState() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope = scope)

        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertEquals(HomeEmptyReason.NoPlaylist, state.emptyReason)
        scope.cancel()
    }

    @Test
    fun providersButAllDisabled_showsAllDisabled() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(
            provider = FakeProviderRepository(listOf(provider("prov", isActive = false))),
            scope = scope,
        )

        assertEquals(HomeEmptyReason.AllDisabled, vm.uiState.value.emptyReason)
        scope.cancel()
    }

    @Test
    fun activeProviderButNoCatalog_showsEmptyCatalog() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(
            provider = FakeProviderRepository(listOf(provider("prov", isActive = true))),
            media = FakeMediaRepository(hasLive = false, hasMovies = false, hasSeries = false),
            scope = scope,
        )

        assertEquals(HomeEmptyReason.EmptyCatalog, vm.uiState.value.emptyReason)
        scope.cancel()
    }

    @Test
    fun movieInProgress_activeProvider_appearsAndNoEmptyState() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(
            playback = FakePlaybackRepository(continueWatching = listOf(movieProgress("p1", "movie-1"))),
            media = FakeMediaRepository(movies = mapOf("movie-1" to movie("movie-1", "Dune")), hasMovies = true),
            provider = FakeProviderRepository(listOf(provider("prov", isActive = true))),
            scope = scope,
        )

        val state = vm.uiState.value
        assertNull(state.emptyReason)
        assertEquals(1, state.movieItems.size)
        assertEquals("Dune", state.movieItems.first().movie.name)
        scope.cancel()
    }

    @Test
    fun disabledProviderContent_isFilteredOut() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // Movie history exists and the movie resolves, but its provider is DISABLED -> not shown.
        val vm = newViewModel(
            playback = FakePlaybackRepository(continueWatching = listOf(movieProgress("p1", "movie-1"))),
            media = FakeMediaRepository(movies = mapOf("movie-1" to movie("movie-1", "Dune")), hasMovies = false),
            provider = FakeProviderRepository(listOf(provider("prov", isActive = false))),
            scope = scope,
        )

        assertTrue(vm.uiState.value.movieItems.isEmpty())
        assertEquals(HomeEmptyReason.AllDisabled, vm.uiState.value.emptyReason)
        scope.cancel()
    }

    @Test
    fun recentChannels_areResolvedAndActiveFiltered() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(
            playback = FakePlaybackRepository(recentChannels = listOf(channelHistory("h1", "ch-1"))),
            media = FakeMediaRepository(channels = mapOf("ch-1" to channel("ch-1", "ARD")), hasLive = true),
            provider = FakeProviderRepository(listOf(provider("prov", isActive = true))),
            scope = scope,
        )

        val recent = vm.uiState.value.recentChannels
        assertEquals(1, recent.size)
        assertEquals("ARD", recent.first().channel.name)
        scope.cancel()
    }

    @Test
    fun series_inProgressEpisode_appearsAsSeriesItem() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(
            playback = FakePlaybackRepository(episodeProgress = listOf(episodeProgress("p1", "ep-1"))),
            media = FakeMediaRepository(
                episodes = mapOf("ep-1" to episode("ep-1", "Chapter 1", seriesId = "s1")),
                series = mapOf("s1" to series("s1", "Foundation")),
                hasSeries = true,
            ),
            provider = FakeProviderRepository(listOf(provider("prov", isActive = true))),
            scope = scope,
        )

        val items = vm.uiState.value.seriesItems
        assertEquals(1, items.size)
        assertEquals("Foundation", items.first().series.name)
        assertEquals("ep-1", items.first().episode.id)
        scope.cancel()
    }

    @Test
    fun series_completedEpisodeWithNext_advancesToNext() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val ep1 = episode("ep-1", "Chapter 1", seriesId = "s1")
        val ep2 = episode("ep-2", "Chapter 2", seriesId = "s1")
        val vm = newViewModel(
            playback = FakePlaybackRepository(episodeProgress = listOf(episodeProgress("p1", "ep-1", completed = true))),
            media = FakeMediaRepository(
                episodes = mapOf("ep-1" to ep1, "ep-2" to ep2),
                series = mapOf("s1" to series("s1", "Foundation")),
                nextEpisodes = mapOf("ep-1" to ep2),
                hasSeries = true,
            ),
            provider = FakeProviderRepository(listOf(provider("prov", isActive = true))),
            scope = scope,
        )

        val items = vm.uiState.value.seriesItems
        assertEquals(1, items.size)
        assertEquals("ep-2", items.first().episode.id)
        assertEquals(0, items.first().progressPercent)
        scope.cancel()
    }

    @Test
    fun series_completedEpisodeNoNext_isDropped() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(
            playback = FakePlaybackRepository(episodeProgress = listOf(episodeProgress("p1", "ep-1", completed = true))),
            media = FakeMediaRepository(
                episodes = mapOf("ep-1" to episode("ep-1", "Finale", seriesId = "s1")),
                series = mapOf("s1" to series("s1", "Foundation")),
                hasSeries = true, // catalog still has the series -> the row shows a CTA, but no card
            ),
            provider = FakeProviderRepository(listOf(provider("prov", isActive = true))),
            scope = scope,
        )

        assertTrue(vm.uiState.value.seriesItems.isEmpty())
        assertNull(vm.uiState.value.emptyReason) // hasSeries=true -> a row (CTA) is shown, not the global empty
        scope.cancel()
    }

    @Test
    fun series_twoInProgressEpisodesSameSeries_dedupedToOne() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // Newest first: ep-2 is the most-recent activity for series s1 -> that's the resume target.
        val vm = newViewModel(
            playback = FakePlaybackRepository(
                episodeProgress = listOf(
                    episodeProgress("p2", "ep-2", lastWatchedAt = 2L),
                    episodeProgress("p1", "ep-1", lastWatchedAt = 1L),
                ),
            ),
            media = FakeMediaRepository(
                episodes = mapOf(
                    "ep-1" to episode("ep-1", "Chapter 1", seriesId = "s1"),
                    "ep-2" to episode("ep-2", "Chapter 2", seriesId = "s1"),
                ),
                series = mapOf("s1" to series("s1", "Foundation")),
                hasSeries = true,
            ),
            provider = FakeProviderRepository(listOf(provider("prov", isActive = true))),
            scope = scope,
        )

        val items = vm.uiState.value.seriesItems
        assertEquals(1, items.size)
        assertEquals("ep-2", items.first().episode.id)
        scope.cancel()
    }
}

private fun provider(id: String, isActive: Boolean) = Provider(
    id = id, name = id, type = ProviderType.M3u, sourceConfigKey = id, isActive = isActive,
    status = ProviderStatus.Active, includeLiveTv = true, includeMovies = true, includeSeries = true,
    refreshIntervalHours = 0, logoPriority = "", createdAt = 0L, updatedAt = 0L,
)

private fun movie(id: String, name: String) = Movie(
    id = id, providerId = "prov", categoryId = "cat", remoteId = id, name = name,
    originalName = null, containerExtension = "mp4", posterUrl = null, backdropUrl = null,
    rating = null, year = null, genre = null, duration = null, director = null, cast = null,
    plot = null, trailerUrl = null, addedAt = null,
)

private fun series(id: String, name: String) = Series(
    id = id, providerId = "prov", categoryId = "cat", remoteId = id, name = name,
    originalName = null, posterUrl = null, backdropUrl = null, rating = null, year = null,
    genre = null, director = null, cast = null, plot = null, addedAt = null,
)

private fun episode(id: String, name: String, seriesId: String = "series") = Episode(
    id = id, providerId = "prov", seriesId = seriesId, seasonId = "season", remoteId = id,
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

private fun episodeProgress(
    id: String,
    mediaId: String,
    completed: Boolean = false,
    lastWatchedAt: Long = 1L,
) = PlaybackProgress(
    id = id, providerId = "prov", mediaType = MediaType.Episode, mediaId = mediaId,
    positionMillis = 10L, durationMillis = 100L, progressPercent = 10, isCompleted = completed,
    lastWatchedAt = lastWatchedAt, createdAt = 1L, updatedAt = 1L,
)

private fun channelHistory(id: String, channelId: String) = ChannelHistory(
    id = id, providerId = "prov", channelId = channelId, watchedAt = 1L,
    durationWatchedMillis = 120_000L, updatedAt = 1L,
)

private class FakePlaybackRepository(
    continueWatching: List<PlaybackProgress> = emptyList(),
    episodeProgress: List<PlaybackProgress> = emptyList(),
    recentChannels: List<ChannelHistory> = emptyList(),
) : PlaybackRepository {
    val continueWatchingFlow = MutableStateFlow(continueWatching)
    val episodeProgressFlow = MutableStateFlow(episodeProgress)
    val recentChannelsFlow = MutableStateFlow(recentChannels)

    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> = continueWatchingFlow
    override fun observeAllEpisodeProgress(): Flow<List<PlaybackProgress>> = episodeProgressFlow
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
    private val series: Map<String, Series> = emptyMap(),
    private val channels: Map<String, Channel> = emptyMap(),
    private val nextEpisodes: Map<String, Episode> = emptyMap(),
    private val hasLive: Boolean = false,
    private val hasMovies: Boolean = false,
    private val hasSeries: Boolean = false,
) : MediaRepository {
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = movies[movieId]
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = episodes[episodeId]
    override suspend fun getSeries(providerId: String, seriesId: String): Series? = series[seriesId]
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = channels[channelId]
    override suspend fun getNextEpisode(episode: Episode): Episode? = nextEpisodes[episode.id]

    override fun observeHasLiveContent(): Flow<Boolean> = flowOf(hasLive)
    override fun observeHasMovieContent(): Flow<Boolean> = flowOf(hasMovies)
    override fun observeHasSeriesContent(): Flow<Boolean> = flowOf(hasSeries)

    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())

    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> = emptyFlow()
    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = emptyFlow()
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = emptyFlow()
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = emptyFlow()
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = emptyFlow()
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = emptyFlow()
}

private class FakeProviderRepository(
    providers: List<Provider> = emptyList(),
) : ProviderRepository {
    val providersFlow = MutableStateFlow(providers)
    override fun observeProviders(): Flow<List<Provider>> = providersFlow

    override suspend fun getProvider(providerId: String): Provider? = providersFlow.value.firstOrNull { it.id == providerId }
    override suspend fun getCredentials(providerId: String) = null
    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult = TODO("unused")
    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult = TODO("unused")
    override suspend fun saveProvider(provider: Provider) = Unit
    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit
    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit
    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit
    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
}
