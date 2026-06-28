package com.vivicast.tv.data.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPlaybackRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomPlaybackRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomPlaybackRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun progressIsProviderAndMediaScoped() = runBlocking {
        repository.saveProgress(progress(id = "movie-progress", providerId = PROVIDER_ID, mediaType = MediaType.Movie, mediaId = "movie-1"))
        repository.saveProgress(progress(id = "episode-progress", providerId = PROVIDER_ID, mediaType = MediaType.Episode, mediaId = "episode-1"))
        repository.saveProgress(progress(id = "other-progress", providerId = OTHER_PROVIDER_ID, mediaType = MediaType.Movie, mediaId = "movie-1"))

        val movieProgress = repository.getProgress(PROVIDER_ID, MediaType.Movie, "movie-1")

        assertEquals("movie-progress", movieProgress?.id)
        assertEquals(MediaType.Movie, movieProgress?.mediaType)
        assertEquals(42_000L, movieProgress?.positionMillis)
        assertNull(repository.getProgress(PROVIDER_ID, MediaType.Series, "movie-1"))
    }

    @Test
    fun continueWatchingExcludesCompletedAndOrdersNewestFirst() = runBlocking {
        repository.saveProgress(progress(id = "old", mediaId = "old", lastWatchedAt = 1_000L))
        repository.saveProgress(progress(id = "new", mediaId = "new", lastWatchedAt = 2_000L))
        repository.saveProgress(progress(id = "done", mediaId = "done", isCompleted = true, lastWatchedAt = 3_000L))

        val continueWatching = repository.observeContinueWatching(PROVIDER_ID).first()

        assertEquals(listOf("new", "old"), continueWatching.map { it.mediaId })
    }

    @Test
    fun recentChannelsAreProviderScopedAndLimited() = runBlocking {
        repository.saveChannelHistory(history(id = "old", channelId = "old", watchedAt = 1_000L))
        repository.saveChannelHistory(history(id = "new", channelId = "new", watchedAt = 2_000L))
        repository.saveChannelHistory(history(id = "other", providerId = OTHER_PROVIDER_ID, channelId = "other", watchedAt = 3_000L))

        val recentChannels = repository.observeRecentChannels(PROVIDER_ID, limit = 1).first()

        assertEquals(listOf("new"), recentChannels.map { it.channelId })
    }

    @Test
    fun homeHistoryObservesAcrossProviders() = runBlocking {
        repository.saveProgress(progress(id = "old-movie", mediaId = "old", lastWatchedAt = 1_000L))
        repository.saveProgress(progress(id = "new-episode", providerId = OTHER_PROVIDER_ID, mediaType = MediaType.Episode, mediaId = "episode", lastWatchedAt = 2_000L))
        repository.saveProgress(progress(id = "done", mediaId = "done", isCompleted = true, lastWatchedAt = 3_000L))
        repository.saveChannelHistory(history(id = "old-channel", channelId = "old", watchedAt = 1_000L))
        repository.saveChannelHistory(history(id = "new-channel", providerId = OTHER_PROVIDER_ID, channelId = "new", watchedAt = 2_000L))

        val continueWatching = repository.observeAllContinueWatching().first()
        val recentChannels = repository.observeAllRecentChannels(limit = 1).first()

        assertEquals(listOf("new-episode", "old-movie"), continueWatching.map { it.id })
        assertEquals(listOf("new"), recentChannels.map { it.channelId })
    }

    @Test
    fun watchNextProgressIncludesOnlyMoviesAndEpisodes() = runBlocking {
        repository.saveProgress(progress(id = "channel", mediaType = MediaType.Channel, mediaId = "channel"))
        repository.saveProgress(progress(id = "movie", mediaType = MediaType.Movie, mediaId = "movie"))
        repository.saveProgress(progress(id = "series", mediaType = MediaType.Series, mediaId = "series"))
        repository.saveProgress(
            progress(
                id = "completed-episode",
                mediaType = MediaType.Episode,
                mediaId = "episode",
                isCompleted = true,
            ),
        )

        val watchNextProgress = repository.getWatchNextProgress()

        assertEquals(listOf("completed-episode", "movie"), watchNextProgress.map { it.id }.sorted())
    }

    @Test
    fun clearProviderPlaybackRemovesProgressAndHistory() = runBlocking {
        repository.saveProgress(progress(id = "progress"))
        repository.saveChannelHistory(history(id = "history"))
        repository.saveProgress(progress(id = "other-progress", providerId = OTHER_PROVIDER_ID))
        repository.saveChannelHistory(history(id = "other-history", providerId = OTHER_PROVIDER_ID))

        repository.clearProviderPlayback(PROVIDER_ID)

        assertEquals(emptyList<PlaybackProgress>(), repository.observeContinueWatching(PROVIDER_ID).first())
        assertEquals(emptyList<ChannelHistory>(), repository.observeRecentChannels(PROVIDER_ID, limit = 10).first())
        assertEquals(listOf("other-progress"), repository.observeContinueWatching(OTHER_PROVIDER_ID).first().map { it.id })
        assertEquals(listOf("other-history"), repository.observeRecentChannels(OTHER_PROVIDER_ID, limit = 10).first().map { it.id })
    }

    @Test
    fun clearHistoryByTypeOnlyRemovesSelectedData() = runBlocking {
        repository.saveChannelHistory(history(id = "live"))
        repository.saveProgress(progress(id = "movie", mediaType = MediaType.Movie, mediaId = "movie"))
        repository.saveProgress(progress(id = "series", mediaType = MediaType.Series, mediaId = "series"))
        repository.saveProgress(progress(id = "episode", mediaType = MediaType.Episode, mediaId = "episode"))

        repository.clearLiveTvHistory()
        assertEquals(emptyList<ChannelHistory>(), repository.observeRecentChannels(PROVIDER_ID, limit = 10).first())
        assertEquals("movie", repository.getProgress(PROVIDER_ID, MediaType.Movie, "movie")?.id)

        repository.clearMovieProgress()
        assertNull(repository.getProgress(PROVIDER_ID, MediaType.Movie, "movie"))
        assertEquals("series", repository.getProgress(PROVIDER_ID, MediaType.Series, "series")?.id)

        repository.clearSeriesProgress()
        assertNull(repository.getProgress(PROVIDER_ID, MediaType.Series, "series"))
        assertNull(repository.getProgress(PROVIDER_ID, MediaType.Episode, "episode"))
    }

    @Test
    fun deleteProgressRemovesOnlySelectedMedia() = runBlocking {
        repository.saveProgress(progress(id = "movie", mediaType = MediaType.Movie, mediaId = "movie"))
        repository.saveProgress(progress(id = "episode", mediaType = MediaType.Episode, mediaId = "episode"))
        repository.saveProgress(progress(id = "other", providerId = OTHER_PROVIDER_ID, mediaType = MediaType.Movie, mediaId = "movie"))

        repository.deleteProgress(PROVIDER_ID, MediaType.Movie, "movie")

        assertNull(repository.getProgress(PROVIDER_ID, MediaType.Movie, "movie"))
        assertEquals("episode", repository.getProgress(PROVIDER_ID, MediaType.Episode, "episode")?.id)
        assertEquals("other", repository.getProgress(OTHER_PROVIDER_ID, MediaType.Movie, "movie")?.id)
    }

    private fun progress(
        id: String = "progress",
        providerId: String = PROVIDER_ID,
        mediaType: MediaType = MediaType.Movie,
        mediaId: String = "movie-1",
        positionMillis: Long = 42_000L,
        durationMillis: Long = 100_000L,
        progressPercent: Int = 42,
        isCompleted: Boolean = false,
        lastWatchedAt: Long = 1_000L,
    ): PlaybackProgress =
        PlaybackProgress(
            id = id,
            providerId = providerId,
            mediaType = mediaType,
            mediaId = mediaId,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            progressPercent = progressPercent,
            isCompleted = isCompleted,
            lastWatchedAt = lastWatchedAt,
            createdAt = 500L,
            updatedAt = lastWatchedAt,
        )

    private fun history(
        id: String,
        providerId: String = PROVIDER_ID,
        channelId: String = "channel-1",
        watchedAt: Long = 1_000L,
    ): ChannelHistory =
        ChannelHistory(
            id = id,
            providerId = providerId,
            channelId = channelId,
            watchedAt = watchedAt,
            durationWatchedMillis = 10_000L,
            updatedAt = watchedAt,
        )

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val OTHER_PROVIDER_ID = "provider-2"
    }
}
