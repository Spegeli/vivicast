package com.vivicast.tv.player

import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackProgressRecorder
import com.vivicast.tv.data.playback.PlaybackRequestFactory
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.playback.PlaybackStream
import com.vivicast.tv.data.playback.PlaybackStreamRequest
import com.vivicast.tv.data.playback.PlaybackStreamResolver
import com.vivicast.tv.data.playback.PlaybackStreamResult
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PROVIDER = "provider-1"

class PlayerViewModelTest {

    private fun newViewModel(
        scope: CoroutineScope,
        controller: FakePlayerController = FakePlayerController(),
        media: MediaRepository = FakeMediaRepository(),
    ): PlayerViewModel {
        val repo = FakePlaybackRepository()
        val factory = PlaybackRequestFactory(FakeStreamResolver(), repo, clock = { 1L })
        val recorder = PlaybackProgressRecorder(repo, clock = { 1L })
        return PlayerViewModel(controller, factory, recorder, media, scope = scope)
    }

    @Test
    fun autoNext_episodeRequest_resolvesNextEpisodeTitle() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller, media = FakeMediaRepository(nextEpisodeName = "Episode 2"))

        controller.state.value = playing(request("E1", PlaybackMediaType.Episode))

        assertEquals("Episode 2", vm.uiState.value.nextEpisodeTitle)
        scope.cancel()
    }

    @Test
    fun autoNext_nonEpisodeRequest_clearsTitle() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller, media = FakeMediaRepository(nextEpisodeName = "Episode 2"))

        controller.state.value = playing(request("c1", PlaybackMediaType.Channel))

        assertNull(vm.uiState.value.nextEpisodeTitle)
        scope.cancel()
    }

    @Test
    fun commitLivePreview_setsCommittedChannel_andPlays() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller)

        vm.commitLivePreview(channel("c1"))

        assertEquals("c1", vm.uiState.value.committedChannel?.id)
        assertEquals("c1", controller.played.single().mediaId)
        scope.cancel()
    }

    @Test
    fun stopLivePreview_clearsCommitted_andStops() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller)
        vm.commitLivePreview(channel("c1"))

        vm.stopLivePreview()

        assertNull(vm.uiState.value.committedChannel)
        assertEquals(1, controller.stopCount)
        scope.cancel()
    }

    @Test
    fun openLivePlayer_alreadyCommitted_doesNotReplay() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller)
        vm.commitLivePreview(channel("c1"))
        val playsAfterCommit = controller.played.size

        vm.openLivePlayer(channel("c1"))

        // Same channel already streaming in the preview → surface handoff (nav signal), never a 2nd play().
        assertEquals(playsAfterCommit, controller.played.size)
        scope.cancel()
    }

    @Test
    fun openLivePlayer_differentChannel_plays() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller)
        vm.commitLivePreview(channel("c1"))

        vm.openLivePlayer(channel("c2"))

        assertEquals("c2", controller.played.last().mediaId)
        scope.cancel()
    }

    @Test
    fun zap_wrapsForwardAndBackward() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller)
        vm.setPlayableChannels(listOf(channel("c1"), channel("c2"), channel("c3")))

        // Forward from the last channel wraps to the first.
        controller.state.value = playing(request("c3", PlaybackMediaType.Channel))
        vm.zap(1)
        assertEquals("c1", controller.played.last().mediaId)

        // Backward from the first channel wraps to the last.
        controller.state.value = playing(request("c1", PlaybackMediaType.Channel))
        vm.zap(-1)
        assertEquals("c3", controller.played.last().mediaId)
        scope.cancel()
    }

    @Test
    fun zap_isNoOp_forZeroDirection_emptyList_orNonChannel() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = FakePlayerController()
        val vm = newViewModel(scope, controller = controller)

        // No playable channels yet.
        controller.state.value = playing(request("c1", PlaybackMediaType.Channel))
        vm.zap(1)
        assertTrue(controller.played.isEmpty())

        // Zero direction never moves.
        vm.setPlayableChannels(listOf(channel("c1"), channel("c2")))
        vm.zap(0)
        assertTrue(controller.played.isEmpty())

        // A VOD stream (non-channel) can't be zapped.
        controller.state.value = playing(request("m1", PlaybackMediaType.Movie))
        vm.zap(1)
        assertTrue(controller.played.isEmpty())
        scope.cancel()
    }
}

private fun request(mediaId: String, mediaType: PlaybackMediaType) = PlaybackRequest(
    playbackId = "pb-$mediaId",
    providerId = PROVIDER,
    mediaId = mediaId,
    mediaType = mediaType,
    title = mediaId,
    streamUrl = "http://test/$mediaId",
    seekable = true,
)

private fun playing(request: PlaybackRequest) = VivicastPlayerState(request = request)

private fun channel(id: String) = Channel(
    id = id, providerId = PROVIDER, categoryId = "cat-1", remoteId = id, channelNumber = "1",
    name = id, logoUrl = null, isCatchupAvailable = false, catchupDays = 0,
)

private fun episode(id: String, name: String) = Episode(
    id = id, providerId = PROVIDER, seriesId = "s1", seasonId = "se1", remoteId = id,
    episodeNumber = 1, seasonNumber = 1, name = name, plot = null, thumbnailUrl = null,
    containerExtension = "mp4", duration = null, airDate = null,
)

private class FakePlayerController : VivicastPlayerController {
    override val state = MutableStateFlow(VivicastPlayerState())
    val played = mutableListOf<PlaybackRequest>()
    var stopCount = 0
    override fun play(request: PlaybackRequest) {
        played += request
        state.value = state.value.copy(request = request)
    }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekBy(deltaMillis: Long) = Unit
    override fun seekToLiveEdge() = Unit
    override fun stop() { stopCount++ }
    override fun release() = Unit
}

private class FakeMediaRepository(
    private val nextEpisodeName: String? = null,
) : MediaRepository {
    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> = emptyFlow()
    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = emptyFlow()
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = channel(channelId)
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = emptyFlow()
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = emptyFlow()
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = emptyFlow()
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = emptyFlow()
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = null
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = episode(episodeId, "current")
    override suspend fun getNextEpisode(episode: Episode): Episode? =
        nextEpisodeName?.let { episode("${episode.id}-next", it) }
    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakePlaybackRepository : PlaybackRepository {
    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> = flowOf(emptyList())
    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> = flowOf(emptyList())
    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? = null
    override suspend fun saveProgress(progress: PlaybackProgress) = Unit
    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun saveChannelHistory(history: ChannelHistory) = Unit
    override suspend fun clearProviderPlayback(providerId: String) = Unit
    override suspend fun clearLiveTvHistory() = Unit
    override suspend fun clearMovieProgress() = Unit
    override suspend fun clearSeriesProgress() = Unit
}

private class FakeStreamResolver : PlaybackStreamResolver {
    override suspend fun resolve(request: PlaybackStreamRequest): PlaybackStreamResult =
        PlaybackStreamResult.Resolved(
            PlaybackStream(
                providerId = request.providerId,
                mediaId = request.mediaId,
                mediaType = request.mediaType,
                url = "http://test/${request.mediaId}",
            ),
        )
}
