package com.vivicast.tv.feature.series

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Favorite
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SeriesRouteDetailTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun openSeriesShowsEpisodesAndEpisodeClickStartsPlayback() {
        var openedEpisodeId: String? = null

        compose.setContent {
            SeriesRoute(
                providerRepository = FakeProviderRepository(),
                mediaRepository = FakeMediaRepository(),
                favoritesRepository = FakeFavoritesRepository(),
                playbackRepository = FakePlaybackRepository(),
                onOpenPlayer = { openedEpisodeId = it.id },
            )
        }

        compose.onAllNodesWithText("S1E1 Pilot").assertCountEquals(0)

        compose.onNodeWithTag(seriesPosterTag(SERIES_ID)).performSemanticsAction(SemanticsActions.OnClick)

        compose.onAllNodesWithText("Staffel 1").assertCountEquals(1)
        compose.onAllNodesWithText("S1E1 Pilot").assertCountEquals(1)

        compose.onNodeWithTag(seriesEpisodeTag(EPISODE_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle {
            assertEquals(EPISODE_ID, openedEpisodeId)
        }

        compose.onNodeWithText("Als gesehen markieren").performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("Gesehen").assertCountEquals(1)
        compose.onAllNodesWithText("Als ungesehen markieren").assertCountEquals(1)

        compose.onNodeWithText("Als ungesehen markieren").performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("Als gesehen markieren").assertCountEquals(1)
    }

    @Test
    fun continueCategoryStartsStoredEpisodeProgress() {
        var openedEpisodeId: String? = null

        compose.setContent {
            SeriesRoute(
                providerRepository = FakeProviderRepository(),
                mediaRepository = FakeMediaRepository(),
                favoritesRepository = FakeFavoritesRepository(),
                playbackRepository = FakePlaybackRepository(TEST_PROGRESS),
                onOpenPlayer = { openedEpisodeId = it.id },
            )
        }

        compose.onAllNodesWithText("Fortsetzen").assertCountEquals(1)
        compose.onNodeWithText("Fortsetzen").performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("42 % | S1E1 Pilot").assertCountEquals(1)

        compose.onNodeWithTag(seriesPosterTag(SERIES_ID)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("Fortsetzen").assertCountEquals(1)
        compose.onNodeWithTag(seriesContinueActionTag(SERIES_ID)).performSemanticsAction(SemanticsActions.OnClick)

        compose.runOnIdle {
            assertEquals(EPISODE_ID, openedEpisodeId)
        }
    }

    @Test
    fun targetSeriesSeasonAndEpisodeOpensExactEpisodeDetail() {
        var targetConsumed = false

        compose.setContent {
            SeriesRoute(
                providerRepository = FakeProviderRepository(),
                mediaRepository = FakeMediaRepository(),
                favoritesRepository = FakeFavoritesRepository(),
                playbackRepository = FakePlaybackRepository(),
                targetProviderId = PROVIDER_ID,
                targetCategoryId = SERIES_CATEGORY_ID,
                targetSeriesId = SERIES_ID,
                targetSeasonId = SEASON_2_ID,
                targetEpisodeId = EPISODE_2_ID,
                onTargetConsumed = { targetConsumed = true },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) { targetConsumed }

        compose.onAllNodesWithText("Staffel 2").assertCountEquals(1)
        compose.onAllNodesWithText("S2E2 Finale").assertCountEquals(1)
        compose.onAllNodesWithText("S1E1 Pilot").assertCountEquals(0)
    }
}

private class FakeProviderRepository : ProviderRepository {
    override fun observeProviders(): Flow<List<Provider>> = flowOf(listOf(TEST_PROVIDER))
    override suspend fun getProvider(providerId: String): Provider? = TEST_PROVIDER.takeIf { it.id == providerId }
    override suspend fun getCredentials(providerId: String): ProviderCredentials? = null
    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
        ProviderSaveResult(TEST_PROVIDER, hasDuplicateName = false)

    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
        ProviderSaveResult(TEST_PROVIDER, hasDuplicateName = false)
    override suspend fun saveProvider(provider: Provider) = Unit
    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit
    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit
    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit
    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
}

private class FakeMediaRepository : MediaRepository {
    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        flowOf(if (providerId == PROVIDER_ID && type == CategoryType.Series) TEST_CATEGORIES else emptyList())

    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> =
        flowOf(
            TEST_SERIES.filter { series ->
                series.providerId == providerId && (categoryId == null || series.categoryId == categoryId)
            },
        )

    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> =
        flowOf(TEST_SEASONS.filter { it.providerId == providerId && it.seriesId == seriesId })

    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> =
        flowOf(TEST_EPISODES.filter { it.providerId == providerId && it.seasonId == seasonId })

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = flowOf(emptyList())
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = flowOf(emptyList())
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = null
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = null
    override suspend fun getSeries(providerId: String, seriesId: String): Series? =
        TEST_SERIES.firstOrNull { it.providerId == providerId && it.id == seriesId }
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? =
        TEST_EPISODES.firstOrNull { it.providerId == providerId && it.id == episodeId }
    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakeFavoritesRepository : FavoritesRepository {
    override fun observeFavorites(providerId: String, mediaType: MediaType): Flow<List<Favorite>> = flowOf(emptyList())
    override fun observeFavorites(mediaType: MediaType): Flow<List<Favorite>> = flowOf(emptyList())
    override suspend fun isFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = false
    override suspend fun addFavorite(favorite: Favorite) = Unit
    override suspend fun removeFavorite(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun toggleFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = true
}

private class FakePlaybackRepository(initialProgress: PlaybackProgress? = null) : PlaybackRepository {
    private var progress: PlaybackProgress? = initialProgress

    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> =
        flowOf(listOfNotNull(progress?.takeUnless { it.isCompleted }).filter { it.providerId == providerId })

    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> =
        flowOf(listOfNotNull(progress?.takeUnless { it.isCompleted }))

    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        progress?.takeIf { it.providerId == providerId && it.mediaType == mediaType && it.mediaId == mediaId }

    override suspend fun saveProgress(progress: PlaybackProgress) {
        this.progress = progress
    }

    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) {
        if (progress?.providerId == providerId && progress?.mediaType == mediaType && progress?.mediaId == mediaId) {
            progress = null
        }
    }

    override suspend fun saveChannelHistory(history: ChannelHistory) = Unit
    override suspend fun clearProviderPlayback(providerId: String) = Unit
    override suspend fun clearLiveTvHistory() = Unit
    override suspend fun clearMovieProgress() = Unit
    override suspend fun clearSeriesProgress() = Unit
}

private const val PROVIDER_ID = "provider-series"
private const val SERIES_CATEGORY_ID = "category-drama"
private const val SERIES_ID = "series-detail"
private const val SEASON_ID = "season-detail"
private const val EPISODE_ID = "episode-detail"
private const val SEASON_2_ID = "season-detail-2"
private const val EPISODE_2_ID = "episode-detail-2"

private val TEST_PROVIDER = Provider(
    id = PROVIDER_ID,
    name = "Provider Series",
    type = ProviderType.Xtream,
    sourceConfigKey = "credentials-provider-series",
    isActive = true,
    status = ProviderStatus.Active,
    includeLiveTv = false,
    includeMovies = false,
    includeSeries = true,
    refreshIntervalHours = 12,
    logoPriority = "provider",
    createdAt = 1L,
    updatedAt = 1L,
)

private val TEST_CATEGORIES = listOf(
    Category(SERIES_CATEGORY_ID, PROVIDER_ID, CategoryType.Series, "drama", "Drama", 0, false),
)

private val TEST_SERIES = listOf(
    Series(
        id = SERIES_ID,
        providerId = PROVIDER_ID,
        categoryId = SERIES_CATEGORY_ID,
        remoteId = "series-detail-remote",
        name = "Detail Series",
        originalName = null,
        posterUrl = null,
        backdropUrl = null,
        rating = "8.2",
        year = "2026",
        genre = "Drama",
        director = null,
        cast = null,
        plot = "Series with one episode.",
        addedAt = null,
    ),
)

private val TEST_SEASONS = listOf(
    Season(
        id = SEASON_ID,
        providerId = PROVIDER_ID,
        seriesId = SERIES_ID,
        seasonNumber = 1,
        name = "Staffel 1",
        posterUrl = null,
    ),
    Season(
        id = SEASON_2_ID,
        providerId = PROVIDER_ID,
        seriesId = SERIES_ID,
        seasonNumber = 2,
        name = "Staffel 2",
        posterUrl = null,
    ),
)

private val TEST_EPISODES = listOf(
    Episode(
        id = EPISODE_ID,
        providerId = PROVIDER_ID,
        seriesId = SERIES_ID,
        seasonId = SEASON_ID,
        remoteId = "episode-detail-remote",
        episodeNumber = 1,
        seasonNumber = 1,
        name = "Pilot",
        plot = "The first episode.",
        thumbnailUrl = null,
        containerExtension = "mp4",
        duration = 3_600L,
        airDate = null,
    ),
    Episode(
        id = EPISODE_2_ID,
        providerId = PROVIDER_ID,
        seriesId = SERIES_ID,
        seasonId = SEASON_2_ID,
        remoteId = "episode-detail-remote-2",
        episodeNumber = 2,
        seasonNumber = 2,
        name = "Finale",
        plot = "The second season finale.",
        thumbnailUrl = null,
        containerExtension = "mp4",
        duration = 3_600L,
        airDate = null,
    ),
)

private val TEST_PROGRESS = PlaybackProgress(
    id = "progress-episode-detail",
    providerId = PROVIDER_ID,
    mediaType = MediaType.Episode,
    mediaId = EPISODE_ID,
    positionMillis = 42_000L,
    durationMillis = 100_000L,
    progressPercent = 42,
    isCompleted = false,
    lastWatchedAt = 10L,
    createdAt = 1L,
    updatedAt = 10L,
)
