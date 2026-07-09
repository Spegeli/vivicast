package com.vivicast.tv.data.playback

import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackReturnTarget
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRequestFactoryTest {

    private val fixedClock = 123456789L

    private fun factory(
        resolver: FakePlaybackStreamResolver,
        repository: FakePlaybackRepository = FakePlaybackRepository(),
    ) = PlaybackRequestFactory(resolver, repository, clock = { fixedClock })

    @Test
    fun movieRequest_builtWhenStreamResolves() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://movie/stream")
        val request = factory(resolver).movieRequest(movie(), resumeProgress = true, origin = PlaybackOrigin.Home)

        requireNotNull(request)
        assertEquals(PlaybackMediaType.Movie, request.mediaType)
        assertEquals(PlaybackOrigin.Home, request.origin)
        assertEquals(PlaybackReturnTarget.MovieDetail, request.returnTarget)
        assertEquals("http://movie/stream", request.streamUrl)
        assertEquals("Movie 1", request.title)
        assertEquals("m1", request.mediaId)
        assertEquals("p1", request.providerId)
        assertEquals("p1", request.providerStableKey)
        assertEquals("m1", request.mediaStableKey)
        assertEquals(true, request.seekable)
        assertEquals("p1:movie:m1:$fixedClock", request.playbackId)
        // No progress → start at 0.
        assertEquals(0L, request.startPositionMillis)
        // Resolver was asked for the Movie stream.
        assertEquals(MediaType.Movie, resolver.lastRequest?.mediaType)
    }

    @Test
    fun movieRequest_resumesFromNonCompletedProgress() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://movie/stream")
        val repo = FakePlaybackRepository(progress = progress("m1", MediaType.Movie, positionMillis = 42_000L, completed = false))
        val request = factory(resolver, repo).movieRequest(movie(), resumeProgress = true, origin = PlaybackOrigin.MovieDetail)

        assertEquals(42_000L, requireNotNull(request).startPositionMillis)
    }

    @Test
    fun movieRequest_ignoresCompletedProgress() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://movie/stream")
        val repo = FakePlaybackRepository(progress = progress("m1", MediaType.Movie, positionMillis = 42_000L, completed = true))
        val request = factory(resolver, repo).movieRequest(movie(), resumeProgress = true, origin = PlaybackOrigin.MovieDetail)

        assertEquals(0L, requireNotNull(request).startPositionMillis)
    }

    @Test
    fun movieRequest_ignoresProgressWhenResumeDisabled() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://movie/stream")
        val repo = FakePlaybackRepository(progress = progress("m1", MediaType.Movie, positionMillis = 42_000L, completed = false))
        val request = factory(resolver, repo).movieRequest(movie(), resumeProgress = false, origin = PlaybackOrigin.MovieDetail)

        assertEquals(0L, requireNotNull(request).startPositionMillis)
    }

    @Test
    fun movieRequest_nullWhenStreamUnresolved() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = null)
        val request = factory(resolver).movieRequest(movie(), resumeProgress = true, origin = PlaybackOrigin.Home)

        assertNull(request)
    }

    @Test
    fun episodeRequest_builtWhenStreamResolves() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://episode/stream")
        val request = factory(resolver).episodeRequest(episode(), origin = PlaybackOrigin.SeriesDetail)

        requireNotNull(request)
        assertEquals(PlaybackMediaType.Episode, request.mediaType)
        assertEquals(PlaybackOrigin.SeriesDetail, request.origin)
        assertEquals(PlaybackReturnTarget.SeriesDetail, request.returnTarget)
        assertEquals("http://episode/stream", request.streamUrl)
        assertEquals("Episode 1", request.title)
        assertEquals("e1", request.mediaId)
        assertEquals("e1", request.mediaStableKey)
        assertEquals("p1:episode:e1:$fixedClock", request.playbackId)
        assertEquals(0L, request.startPositionMillis)
        assertEquals(MediaType.Episode, resolver.lastRequest?.mediaType)
    }

    @Test
    fun episodeRequest_resumesFromNonCompletedProgress() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://episode/stream")
        val repo = FakePlaybackRepository(progress = progress("e1", MediaType.Episode, positionMillis = 7_000L, completed = false))
        val request = factory(resolver, repo).episodeRequest(episode(), origin = PlaybackOrigin.SeriesDetail)

        assertEquals(7_000L, requireNotNull(request).startPositionMillis)
    }

    @Test
    fun episodeRequest_nullWhenStreamUnresolved() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = null)
        val request = factory(resolver).episodeRequest(episode(), origin = PlaybackOrigin.SeriesDetail)

        assertNull(request)
    }

    // --- Channel ---

    @Test
    fun channelRequest_builtWhenStreamResolves() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://channel/stream")
        val request = factory(resolver).channelRequest(channel(), origin = PlaybackOrigin.LiveTv)

        requireNotNull(request)
        assertEquals(PlaybackMediaType.Channel, request.mediaType)
        assertEquals(PlaybackOrigin.LiveTv, request.origin)
        assertEquals(PlaybackReturnTarget.LiveTv, request.returnTarget)
        assertEquals("http://channel/stream", request.streamUrl)
        assertEquals("Channel 1", request.title)
        assertEquals("c1", request.mediaId)
        assertEquals("p1", request.providerId)
        assertEquals("c1", request.mediaStableKey)
        assertEquals("p1:channel:c1:$fixedClock", request.playbackId)
        // Live channels are always seekable; the controller auto-detects a native DVR window at playback.
        assertEquals(true, request.seekable)
        assertEquals(MediaType.Channel, resolver.lastRequest?.mediaType)
    }

    @Test
    fun channelRequest_nullWhenStreamUnresolved() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = null)
        val request = factory(resolver).channelRequest(channel(), origin = PlaybackOrigin.LiveTv)

        assertNull(request)
    }

    // --- Catch-up ---

    @Test
    fun catchUpRequest_builtForValidProgramWithinWindow() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://catchup/stream")
        val program = program(startTime = 100_000_000L, endTime = 110_000_000L)
        val request = factory(resolver).catchUpRequest(channel(catchupDays = 7), program, origin = PlaybackOrigin.LiveTv)

        requireNotNull(request)
        assertEquals(PlaybackMediaType.CatchUp, request.mediaType)
        assertEquals(PlaybackOrigin.LiveTv, request.origin)
        assertEquals(PlaybackReturnTarget.LiveTv, request.returnTarget)
        assertEquals("http://catchup/stream", request.streamUrl)
        assertEquals("Channel 1 - Program", request.title)
        assertEquals("prog-stable", request.epgProgramStableKey)
        assertEquals("c1", request.mediaStableKey)
        assertEquals("p1:channel:c1:$fixedClock", request.playbackId)
        // Catch-up window is forwarded to the stream resolver.
        assertEquals(100_000_000L, resolver.lastRequest?.catchupStartMillis)
        assertEquals(110_000_000L, resolver.lastRequest?.catchupEndMillis)
    }

    @Test
    fun catchUpRequest_nullWhenChannelHasNoCatchUp() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://catchup/stream")
        val program = program(startTime = 100_000_000L, endTime = 110_000_000L)
        val request = factory(resolver)
            .catchUpRequest(channel(isCatchupAvailable = false, catchupDays = 7), program, origin = PlaybackOrigin.LiveTv)

        assertNull(request)
    }

    @Test
    fun catchUpRequest_nullWhenCatchupDaysNotPositive() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://catchup/stream")
        val program = program(startTime = 100_000_000L, endTime = 110_000_000L)
        val request = factory(resolver).catchUpRequest(channel(catchupDays = 0), program, origin = PlaybackOrigin.LiveTv)

        assertNull(request)
    }

    @Test
    fun catchUpRequest_nullWhenProgramInFuture() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://catchup/stream")
        // endTime after the fixed clock → future.
        val program = program(startTime = 120_000_000L, endTime = 200_000_000L)
        val request = factory(resolver).catchUpRequest(channel(catchupDays = 7), program, origin = PlaybackOrigin.LiveTv)

        assertNull(request)
    }

    @Test
    fun catchUpRequest_nullWhenTimesInvalid() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://catchup/stream")
        val program = program(startTime = 110_000_000L, endTime = 100_000_000L)
        val request = factory(resolver).catchUpRequest(channel(catchupDays = 7), program, origin = PlaybackOrigin.LiveTv)

        assertNull(request)
    }

    @Test
    fun catchUpRequest_nullWhenOutsideCatchupWindow() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = "http://catchup/stream")
        // catchupDays=1 → earliestAllowedStart = fixedClock - 86_400_000; start below that.
        val program = program(startTime = 10_000_000L, endTime = 20_000_000L)
        val request = factory(resolver).catchUpRequest(channel(catchupDays = 1), program, origin = PlaybackOrigin.LiveTv)

        assertNull(request)
    }

    @Test
    fun catchUpRequest_nullWhenStreamUnresolved() = runBlocking {
        val resolver = FakePlaybackStreamResolver(url = null)
        val program = program(startTime = 100_000_000L, endTime = 110_000_000L)
        val request = factory(resolver).catchUpRequest(channel(catchupDays = 7), program, origin = PlaybackOrigin.LiveTv)

        assertNull(request)
    }

    private fun channel(isCatchupAvailable: Boolean = true, catchupDays: Int = 0) = Channel(
        id = "c1", providerId = "p1", categoryId = null, remoteId = "rc1", channelNumber = null,
        name = "Channel 1", logoUrl = null, isCatchupAvailable = isCatchupAvailable, catchupDays = catchupDays,
    )

    private fun program(startTime: Long, endTime: Long) = EpgProgram(
        id = "prog1", providerId = "p1", channelId = "c1", epgSourceId = "src1", epgChannelId = "ec1",
        title = "Program", subtitle = null, description = null, startTime = startTime, endTime = endTime,
        category = null, iconUrl = null, isCatchupAvailable = true, stableKey = "prog-stable",
    )

    private fun movie() = Movie(
        id = "m1", providerId = "p1", categoryId = null, remoteId = "rm1", name = "Movie 1",
        originalName = null, containerExtension = "mkv", posterUrl = null, backdropUrl = null,
        rating = null, year = null, genre = null, duration = null, director = null, cast = null,
        plot = null, trailerUrl = null, addedAt = null,
    )

    private fun episode() = Episode(
        id = "e1", providerId = "p1", seriesId = "s1", seasonId = "se1", remoteId = "re1",
        episodeNumber = 1, seasonNumber = 1, name = "Episode 1", plot = null, thumbnailUrl = null,
        containerExtension = "mkv", duration = null, airDate = null,
    )

    private fun progress(mediaId: String, mediaType: MediaType, positionMillis: Long, completed: Boolean) =
        PlaybackProgress(
            id = "p1:progress:${mediaType.name.lowercase()}:$mediaId",
            providerId = "p1", mediaType = mediaType, mediaId = mediaId,
            positionMillis = positionMillis, durationMillis = 100_000L, progressPercent = 0,
            isCompleted = completed, lastWatchedAt = 0L, createdAt = 0L, updatedAt = 0L,
        )
}

private class FakePlaybackStreamResolver(private val url: String?) : PlaybackStreamResolver {
    var lastRequest: PlaybackStreamRequest? = null
        private set

    override suspend fun resolve(request: PlaybackStreamRequest): PlaybackStreamResult {
        lastRequest = request
        return if (url == null) {
            PlaybackStreamResult.Failed(PlaybackStreamFailureReason.MissingStreamReference)
        } else {
            PlaybackStreamResult.Resolved(
                PlaybackStream(
                    providerId = request.providerId,
                    mediaId = request.mediaId,
                    mediaType = request.mediaType,
                    url = url,
                ),
            )
        }
    }
}

private class FakePlaybackRepository(private val progress: PlaybackProgress? = null) : PlaybackRepository {
    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> = flowOf(emptyList())
    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> = flowOf(emptyList())
    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        progress?.takeIf { it.mediaId == mediaId && it.mediaType == mediaType }
    override suspend fun saveProgress(progress: PlaybackProgress) = Unit
    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun saveChannelHistory(history: ChannelHistory) = Unit
    override suspend fun clearProviderPlayback(providerId: String) = Unit
    override suspend fun clearLiveTvHistory() = Unit
    override suspend fun clearMovieProgress() = Unit
    override suspend fun clearSeriesProgress() = Unit
}
