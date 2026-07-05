package com.vivicast.tv.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vivicast.tv.core.security.PinCheckValue
import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.core.security.PinSecurityStateStore
import com.vivicast.tv.data.media.AndroidTvSearchSuggestion
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextIntegrationTest {
    @Test
    fun androidTvPublisherAcceptsEmptySyncOnDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val publisher = AndroidTvWatchNextPublisher(context)

        publisher.sync(emptyList())
    }

    @Test
    fun syncPublishesIncompleteMovieWithStableDeepLink() = runBlocking {
        val publisher = RecordingWatchNextPublisher()
        val synchronizer = synchronizer(
            mediaRepository = FakeMediaRepository(
                movies = listOf(movie()),
            ),
            playbackRepository = FakePlaybackRepository(
                progress = listOf(
                    progress(
                        mediaType = MediaType.Movie,
                        mediaId = "movie-1",
                        positionMillis = 25_000L,
                        durationMillis = 100_000L,
                    ),
                ),
            ),
            publisher = publisher,
        )

        synchronizer.sync()

        val candidate = publisher.lastCandidates.single()
        assertEquals("vivicast:MOVIE:provider-stable:movie-stable", candidate.internalProviderId)
        assertEquals("Movie", candidate.title)
        assertEquals("vivicast://movie/provider-stable/movie-stable", candidate.intentUri)
        assertEquals(WatchNextContentType.Movie, candidate.type)
        assertEquals(WatchNextKind.Continue, candidate.kind)
        assertEquals(25_000L, candidate.lastPlaybackPositionMillis)
    }

    @Test
    fun syncDoesNotPublishProtectedMovie() = runBlocking {
        val publisher = RecordingWatchNextPublisher()
        val synchronizer = synchronizer(
            mediaRepository = FakeMediaRepository(
                movies = listOf(movie(isAdult = true)),
            ),
            playbackRepository = FakePlaybackRepository(
                progress = listOf(progress(mediaType = MediaType.Movie, mediaId = "movie-1")),
            ),
            pinState = protectedPinState(protectAdultContent = true),
            publisher = publisher,
        )

        synchronizer.sync()

        assertTrue(publisher.lastCandidates.isEmpty())
    }

    @Test
    fun completedEpisodePublishesNextEpisodeAtStart() = runBlocking {
        val episodeOne = episode(id = "episode-1", stableKey = "episode-one", episodeNumber = 1)
        val episodeTwo = episode(id = "episode-2", stableKey = "episode-two", episodeNumber = 2)
        val publisher = RecordingWatchNextPublisher()
        val synchronizer = synchronizer(
            mediaRepository = FakeMediaRepository(
                series = listOf(series()),
                episodes = listOf(episodeOne, episodeTwo),
                nextEpisodes = mapOf(episodeOne.id to episodeTwo),
            ),
            playbackRepository = FakePlaybackRepository(
                progress = listOf(
                    progress(
                        mediaType = MediaType.Episode,
                        mediaId = episodeOne.id,
                        isCompleted = true,
                        positionMillis = 95_000L,
                        durationMillis = 100_000L,
                    ),
                ),
            ),
            publisher = publisher,
        )

        synchronizer.sync()

        val candidate = publisher.lastCandidates.single()
        assertEquals("vivicast:EPISODE:provider-stable:episode-two", candidate.internalProviderId)
        assertEquals("Series", candidate.title)
        assertEquals("vivicast://episode/provider-stable/episode-two", candidate.intentUri)
        assertEquals(WatchNextKind.Next, candidate.kind)
        assertEquals(0L, candidate.lastPlaybackPositionMillis)
        assertEquals(0L, candidate.durationMillis)
        assertEquals(1, candidate.seasonNumber)
        assertEquals(2, candidate.episodeNumber)
    }

    @Test
    fun repositoryWrappersSyncOnWatchNextInvalidatingChanges() = runBlocking {
        var syncCount = 0
        val playbackRepository = SystemIntegrationPlaybackRepository(FakePlaybackRepository()) { syncCount++ }
        val providerRepository = SystemIntegrationProviderRepository(FakeProviderRepository()) { syncCount++ }

        playbackRepository.saveProgress(progress(mediaType = MediaType.Movie, mediaId = "movie-1"))
        playbackRepository.deleteProgress("provider-1", MediaType.Episode, "episode-1")
        playbackRepository.clearMovieProgress()
        playbackRepository.clearSeriesProgress()
        playbackRepository.clearProviderPlayback("provider-1")
        playbackRepository.saveChannelHistory(
            ChannelHistory(
                id = "history-1",
                providerId = "provider-1",
                channelId = "channel-1",
                watchedAt = 1L,
                durationWatchedMillis = 1L,
                updatedAt = 1L,
            ),
        )
        playbackRepository.clearLiveTvHistory()
        providerRepository.setProviderActive("provider-1", isActive = false)
        providerRepository.deleteProvider("provider-1")

        assertEquals(7, syncCount)
    }
}

private fun synchronizer(
    mediaRepository: MediaRepository = FakeMediaRepository(),
    playbackRepository: PlaybackRepository = FakePlaybackRepository(),
    providerRepository: ProviderRepository = FakeProviderRepository(),
    pinState: PinSecurityState = PinSecurityState(),
    publisher: RecordingWatchNextPublisher = RecordingWatchNextPublisher(),
): WatchNextSynchronizer =
    WatchNextSynchronizer(
        providerRepository = providerRepository,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        pinSecurityStateStore = FakePinSecurityStateStore(pinState),
        publisher = publisher,
    )

private class RecordingWatchNextPublisher : WatchNextPublisher {
    var lastCandidates: List<WatchNextCandidate> = emptyList()

    override suspend fun sync(candidates: List<WatchNextCandidate>) {
        lastCandidates = candidates
    }
}

private class FakePinSecurityStateStore(
    private var state: PinSecurityState,
) : PinSecurityStateStore {
    override suspend fun read(): PinSecurityState = state

    override suspend fun write(state: PinSecurityState) {
        this.state = state
    }

    override suspend fun clear() {
        state = PinSecurityState()
    }
}

private class FakeProviderRepository(
    private val provider: Provider = provider(),
) : ProviderRepository {
    override fun observeProviders(): Flow<List<Provider>> = flowOf(listOf(provider))

    override suspend fun getProvider(providerId: String): Provider? =
        provider.takeIf { it.id == providerId }

    override suspend fun getCredentials(providerId: String): ProviderCredentials? = null

    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
        ProviderSaveResult(provider, hasDuplicateName = false)

    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
        ProviderSaveResult(provider, hasDuplicateName = false)

    override suspend fun saveProvider(provider: Provider) = Unit

    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit

    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit

    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit

    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) = Unit

    override suspend fun deleteProvider(providerId: String) = Unit
}

private class FakeMediaRepository(
    private val movies: List<Movie> = emptyList(),
    private val series: List<Series> = emptyList(),
    private val episodes: List<Episode> = emptyList(),
    private val nextEpisodes: Map<String, Episode> = emptyMap(),
) : MediaRepository {
    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        flowOf(emptyList())

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> =
        flowOf(emptyList())

    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> =
        flowOf(movies)

    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> =
        flowOf(series)

    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> =
        flowOf(emptyList())

    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> =
        flowOf(episodes.filter { it.seasonId == seasonId })

    override suspend fun getChannel(providerId: String, channelId: String): Channel? = null

    override suspend fun getMovie(providerId: String, movieId: String): Movie? =
        movies.firstOrNull { it.providerId == providerId && it.id == movieId }

    override suspend fun getSeries(providerId: String, seriesId: String): Series? =
        series.firstOrNull { it.providerId == providerId && it.id == seriesId }

    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? =
        episodes.firstOrNull { it.providerId == providerId && it.id == episodeId }

    override suspend fun getNextEpisode(episode: Episode): Episode? =
        nextEpisodes[episode.id]

    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(channels = emptyList(), movies = emptyList(), series = emptyList(), epgPrograms = emptyList())

    override suspend fun searchAndroidTvSuggestions(
        query: String,
        limit: Int,
        protectMovies: Boolean,
        protectSeries: Boolean,
        protectAdultContent: Boolean,
    ): List<AndroidTvSearchSuggestion> = emptyList()
}

private class FakePlaybackRepository(
    private val progress: List<PlaybackProgress> = emptyList(),
) : PlaybackRepository {
    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> =
        flowOf(progress)

    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> =
        flowOf(progress)

    override suspend fun getWatchNextProgress(): List<PlaybackProgress> =
        progress

    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> =
        flowOf(emptyList())

    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> =
        flowOf(emptyList())

    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        progress.firstOrNull { it.providerId == providerId && it.mediaType == mediaType && it.mediaId == mediaId }

    override suspend fun saveProgress(progress: PlaybackProgress) = Unit

    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) = Unit

    override suspend fun saveChannelHistory(history: ChannelHistory) = Unit

    override suspend fun clearProviderPlayback(providerId: String) = Unit

    override suspend fun clearLiveTvHistory() = Unit

    override suspend fun clearMovieProgress() = Unit

    override suspend fun clearSeriesProgress() = Unit
}

private fun provider(
    isActive: Boolean = true,
    status: ProviderStatus = ProviderStatus.Active,
    includeMovies: Boolean = true,
    includeSeries: Boolean = true,
): Provider =
    Provider(
        id = "provider-1",
        name = "Provider",
        type = ProviderType.M3u,
        sourceConfigKey = "source-key",
        isActive = isActive,
        status = status,
        includeLiveTv = true,
        includeMovies = includeMovies,
        includeSeries = includeSeries,
        refreshIntervalHours = 24,
        logoPriority = "playlist",
        createdAt = 1L,
        updatedAt = 1L,
        stableKey = "provider-stable",
    )

private fun movie(isAdult: Boolean = false): Movie =
    Movie(
        id = "movie-1",
        providerId = "provider-1",
        categoryId = null,
        remoteId = "remote-movie-1",
        name = "Movie",
        originalName = null,
        containerExtension = "mp4",
        posterUrl = "https://example.test/movie.jpg",
        backdropUrl = null,
        rating = null,
        year = "2026",
        genre = null,
        duration = 100_000L,
        director = null,
        cast = null,
        plot = "Movie plot",
        trailerUrl = null,
        addedAt = null,
        isAdult = isAdult,
        stableKey = "movie-stable",
    )

private fun series(isAdult: Boolean = false): Series =
    Series(
        id = "series-1",
        providerId = "provider-1",
        categoryId = null,
        remoteId = "remote-series-1",
        name = "Series",
        originalName = null,
        posterUrl = "https://example.test/series.jpg",
        backdropUrl = null,
        rating = null,
        year = null,
        genre = null,
        director = null,
        cast = null,
        plot = "Series plot",
        addedAt = null,
        isAdult = isAdult,
        stableKey = "series-stable",
    )

private fun episode(
    id: String = "episode-1",
    stableKey: String = "episode-stable",
    episodeNumber: Int = 1,
    isAdult: Boolean = false,
): Episode =
    Episode(
        id = id,
        providerId = "provider-1",
        seriesId = "series-1",
        seasonId = "season-1",
        remoteId = "remote-$id",
        episodeNumber = episodeNumber,
        seasonNumber = 1,
        name = "Episode $episodeNumber",
        plot = "Episode plot",
        thumbnailUrl = "https://example.test/$id.jpg",
        containerExtension = "mp4",
        duration = 100_000L,
        airDate = null,
        isAdult = isAdult,
        stableKey = stableKey,
    )

private fun progress(
    mediaType: MediaType,
    mediaId: String,
    isCompleted: Boolean = false,
    positionMillis: Long = 10_000L,
    durationMillis: Long = 100_000L,
): PlaybackProgress =
    PlaybackProgress(
        id = "progress-$mediaId",
        providerId = "provider-1",
        mediaType = mediaType,
        mediaId = mediaId,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        progressPercent = if (isCompleted) 95 else 10,
        isCompleted = isCompleted,
        lastWatchedAt = 10L,
        createdAt = 1L,
        updatedAt = 10L,
        mediaStableKey = mediaId,
    )

private fun protectedPinState(
    protectMovies: Boolean = false,
    protectSeries: Boolean = false,
    protectAdultContent: Boolean = false,
): PinSecurityState =
    PinSecurityState(
        checkValue = PinCheckValue(saltHex = "00", hashHex = "00", iterations = 1),
        protectMovies = protectMovies,
        protectSeries = protectSeries,
        protectAdultContent = protectAdultContent,
    )
