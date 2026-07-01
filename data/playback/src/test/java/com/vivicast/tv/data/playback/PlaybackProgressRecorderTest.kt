package com.vivicast.tv.data.playback

import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressRecorderTest {

    private val fixedClock = 5_000_000L

    private fun recorder(repository: FakeRecorderRepository) =
        PlaybackProgressRecorder(repository, clock = { fixedClock })

    // --- Channel history ---

    @Test
    fun channel_savesChannelHistoryWithIdFormatAndClock() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            state(PlaybackMediaType.Channel, PlaybackStatus.Playing, position = 30_000L),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        val history = requireNotNull(repo.savedHistory)
        assertEquals("p1:history:channel:c1", history.id)
        assertEquals("p1", history.providerId)
        assertEquals("c1", history.channelId)
        assertEquals(fixedClock, history.watchedAt)
        assertEquals(fixedClock, history.updatedAt)
        assertEquals(30_000L, history.durationWatchedMillis)
        assertEquals("c1", history.channelStableKey)
        assertNull(repo.savedProgress)
    }

    @Test
    fun channel_ignoredWhenNotPlayingOrPaused() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            state(PlaybackMediaType.Channel, PlaybackStatus.Idle),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        assertNull(repo.savedHistory)
    }

    // --- Movie / Episode progress ---

    @Test
    fun movie_savesProgressWhenRulesAllow() = runBlocking {
        val repo = FakeRecorderRepository()
        val times = mutableMapOf<String, Long>()
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Playing, position = 20_000L, duration = 100_000L, mediaId = "m1"),
            automaticProgressSaveTimes = times,
        )

        val progress = requireNotNull(repo.savedProgress)
        assertEquals("p1:progress:movie:m1", progress.id)
        assertEquals(MediaType.Movie, progress.mediaType)
        assertEquals(20_000L, progress.positionMillis)
        assertEquals(20, progress.progressPercent)
        assertEquals(false, progress.isCompleted)
        assertEquals(fixedClock, progress.createdAt)
        assertEquals(fixedClock, progress.lastWatchedAt)
        // Throttle map updated.
        assertEquals(fixedClock, times["pb-m1"])
    }

    @Test
    fun movie_notSavedWhenRulesPreventCreate() = runBlocking {
        val repo = FakeRecorderRepository()
        // Below minimum position and below 1% → no create for a new progress.
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Playing, position = 500L, duration = 100_000L),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        assertNull(repo.savedProgress)
    }

    @Test
    fun movie_completedWhenMediaEnded() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Ended, position = 1_000L, duration = 100_000L),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        assertEquals(true, requireNotNull(repo.savedProgress).isCompleted)
    }

    @Test
    fun movie_completedWhenAboveThreshold() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Playing, position = 96_000L, duration = 100_000L),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        val progress = requireNotNull(repo.savedProgress)
        assertEquals(96, progress.progressPercent)
        assertEquals(true, progress.isCompleted)
    }

    @Test
    fun movie_preservesExistingIdAndCreatedAt() = runBlocking {
        val existing = PlaybackProgress(
            id = "existing-id", providerId = "p1", mediaType = MediaType.Movie, mediaId = "m1",
            positionMillis = 10_000L, durationMillis = 100_000L, progressPercent = 10, isCompleted = false,
            lastWatchedAt = 100L, createdAt = 111L, updatedAt = 100L,
        )
        val repo = FakeRecorderRepository(existing = existing)
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Paused, position = 50_000L, duration = 100_000L, mediaId = "m1"),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        val progress = requireNotNull(repo.savedProgress)
        assertEquals("existing-id", progress.id)
        assertEquals(111L, progress.createdAt)
        assertEquals(fixedClock, progress.updatedAt)
    }

    @Test
    fun episode_savesProgressWithIdFormat() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            state(PlaybackMediaType.Episode, PlaybackStatus.Playing, position = 20_000L, duration = 100_000L, mediaId = "e1"),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        val progress = requireNotNull(repo.savedProgress)
        assertEquals("p1:progress:episode:e1", progress.id)
        assertEquals(MediaType.Episode, progress.mediaType)
    }

    // --- Throttle ---

    @Test
    fun movie_throttledWhenSavedRecentlyAndNotForced() = runBlocking {
        val existing = PlaybackProgress(
            id = "existing-id", providerId = "p1", mediaType = MediaType.Movie, mediaId = "m1",
            positionMillis = 10_000L, durationMillis = 100_000L, progressPercent = 10, isCompleted = false,
            lastWatchedAt = 100L, createdAt = 111L, updatedAt = 100L,
        )
        val repo = FakeRecorderRepository(existing = existing)
        val times = mutableMapOf("pb-m1" to fixedClock - 5_000L) // 5s < 10s interval
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Playing, position = 20_000L, duration = 100_000L, mediaId = "m1"),
            automaticProgressSaveTimes = times,
        )

        assertNull(repo.savedProgress)
    }

    @Test
    fun movie_savedWhenPausedForcesThroughThrottle() = runBlocking {
        val existing = PlaybackProgress(
            id = "existing-id", providerId = "p1", mediaType = MediaType.Movie, mediaId = "m1",
            positionMillis = 10_000L, durationMillis = 100_000L, progressPercent = 10, isCompleted = false,
            lastWatchedAt = 100L, createdAt = 111L, updatedAt = 100L,
        )
        val repo = FakeRecorderRepository(existing = existing)
        val times = mutableMapOf("pb-m1" to fixedClock - 5_000L)
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Paused, position = 20_000L, duration = 100_000L, mediaId = "m1"),
            automaticProgressSaveTimes = times,
        )

        assertTrue(repo.savedProgress != null)
    }

    // --- Guards ---

    @Test
    fun nothingSavedWhenRequestMissing() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            VivicastPlayerState(status = PlaybackStatus.Playing, request = null),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        assertNull(repo.savedProgress)
        assertNull(repo.savedHistory)
    }

    @Test
    fun catchUpMediaTypeSavesNothing() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            state(PlaybackMediaType.CatchUp, PlaybackStatus.Playing, position = 20_000L, duration = 100_000L),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        assertNull(repo.savedProgress)
        assertNull(repo.savedHistory)
    }

    @Test
    fun movie_ignoredWhenStatusNotPlayablePausedOrEnded() = runBlocking {
        val repo = FakeRecorderRepository()
        recorder(repo).record(
            state(PlaybackMediaType.Movie, PlaybackStatus.Idle, position = 20_000L, duration = 100_000L),
            automaticProgressSaveTimes = mutableMapOf(),
        )

        assertNull(repo.savedProgress)
    }

    private fun state(
        mediaType: PlaybackMediaType,
        status: PlaybackStatus,
        position: Long = 0L,
        duration: Long = 0L,
        providerId: String = "p1",
        mediaId: String = "m1",
    ) = VivicastPlayerState(
        status = status,
        request = PlaybackRequest(
            playbackId = "pb-$mediaId",
            providerId = providerId,
            mediaId = mediaId,
            mediaType = mediaType,
            mediaStableKey = if (mediaType == PlaybackMediaType.Channel) "c1" else mediaId,
            title = "Title",
            streamUrl = "http://stream",
            seekable = true,
        ),
        positionMillis = position,
        durationMillis = duration,
    ).let { s ->
        if (mediaType == PlaybackMediaType.Channel) {
            s.copy(request = s.request?.copy(mediaId = "c1"))
        } else {
            s
        }
    }
}

private class FakeRecorderRepository(private val existing: PlaybackProgress? = null) : PlaybackRepository {
    var savedProgress: PlaybackProgress? = null
        private set
    var savedHistory: ChannelHistory? = null
        private set

    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> = flowOf(emptyList())
    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> = flowOf(emptyList())
    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        existing?.takeIf { it.mediaId == mediaId && it.mediaType == mediaType }
    override suspend fun saveProgress(progress: PlaybackProgress) { savedProgress = progress }
    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun saveChannelHistory(history: ChannelHistory) { savedHistory = history }
    override suspend fun clearProviderPlayback(providerId: String) = Unit
    override suspend fun clearLiveTvHistory() = Unit
    override suspend fun clearMovieProgress() = Unit
    override suspend fun clearSeriesProgress() = Unit
}
