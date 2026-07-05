package com.vivicast.tv.feature.movies

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsActions
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
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.ChannelHistory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MoviesRouteContinueTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun openMovieProgressAddsContinueCategoryAndCardProgress() {
        compose.setContent {
            MoviesRoute(
                providerRepository = FakeProviderRepository(),
                mediaRepository = FakeMediaRepository(),
                favoritesRepository = FakeFavoritesRepository(),
                playbackRepository = FakePlaybackRepository(),
                openTrailer = { false },
            )
        }

        compose.onAllNodesWithText("Fortsetzen").assertCountEquals(1)
        compose.onAllNodesWithText("Continue Movie").assertCountEquals(2)
        compose.onAllNodesWithText("Von Anfang an").assertCountEquals(0)

        compose.onNodeWithTag(moviePosterTag(MOVIE_ID)).performSemanticsAction(SemanticsActions.OnClick)

        compose.onAllNodesWithText("Fortsetzen").assertCountEquals(1)
        compose.onAllNodesWithText("Filmdetails").assertCountEquals(1)
        compose.onAllNodesWithText("Von Anfang an").assertCountEquals(1)
        compose.onAllNodesWithText("42 %").assertCountEquals(1)
        compose.onAllNodesWithText("Trailer").assertCountEquals(1)

        compose.onNodeWithText("Trailer").performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("Für Trailer wird die YouTube-App benötigt.").assertCountEquals(1)

        compose.onNodeWithText("Als gesehen markieren").performSemanticsAction(SemanticsActions.OnClick)

        compose.onAllNodesWithText("Gesehen").assertCountEquals(2)
        compose.onAllNodesWithText("Als ungesehen markieren").assertCountEquals(1)
    }

    @Test
    fun youtubeUrlValidationAcceptsOnlyExpectedHosts() {
        assertTrue("https://youtube.com/watch?v=abc".isYouTubeUrl())
        assertTrue("https://www.youtube.com/watch?v=abc".isYouTubeUrl())
        assertTrue("https://youtu.be/abc".isYouTubeUrl())
        assertFalse("https://example.com/watch?v=abc".isYouTubeUrl())
        assertFalse("not a url".isYouTubeUrl())
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
        flowOf(if (providerId == PROVIDER_ID && type == CategoryType.Movies) TEST_CATEGORIES else emptyList())

    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> =
        flowOf(
            TEST_MOVIES.filter { movie ->
                movie.providerId == providerId && (categoryId == null || movie.categoryId == categoryId)
            },
        )

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = flowOf(emptyList())
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = flowOf(emptyList())
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = flowOf(emptyList())
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = flowOf(emptyList())
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = null
    override suspend fun getMovie(providerId: String, movieId: String): Movie? =
        TEST_MOVIES.firstOrNull { it.providerId == providerId && it.id == movieId }
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = null
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

private class FakePlaybackRepository : PlaybackRepository {
    private var progress: PlaybackProgress? = TEST_PROGRESS

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

private const val PROVIDER_ID = "provider-movies"
private const val MOVIE_CATEGORY_ID = "category-action"
private const val MOVIE_ID = "movie-continue"

private val TEST_PROVIDER = Provider(
    id = PROVIDER_ID,
    name = "Provider Movies",
    type = ProviderType.Xtream,
    sourceConfigKey = "credentials-provider-movies",
    isActive = true,
    status = ProviderStatus.Active,
    includeLiveTv = false,
    includeMovies = true,
    includeSeries = false,
    refreshIntervalHours = 12,
    logoPriority = "provider",
    createdAt = 1L,
    updatedAt = 1L,
)

private val TEST_CATEGORIES = listOf(
    Category(MOVIE_CATEGORY_ID, PROVIDER_ID, CategoryType.Movies, "action", "Action", 0, false),
)

private val TEST_MOVIES = listOf(
    Movie(
        id = MOVIE_ID,
        providerId = PROVIDER_ID,
        categoryId = MOVIE_CATEGORY_ID,
        remoteId = "movie-continue-remote",
        name = "Continue Movie",
        originalName = null,
        containerExtension = "mp4",
        posterUrl = null,
        backdropUrl = null,
        rating = "8.0",
        year = "2026",
        genre = "Action",
        duration = 7_200L,
        director = null,
        cast = null,
        plot = "A movie with stored progress.",
        trailerUrl = null,
        addedAt = null,
    ),
)

private val TEST_PROGRESS = PlaybackProgress(
    id = "progress-movie",
    providerId = PROVIDER_ID,
    mediaType = MediaType.Movie,
    mediaId = MOVIE_ID,
    positionMillis = 1_800_000L,
    durationMillis = 4_285_000L,
    progressPercent = 42,
    isCompleted = false,
    lastWatchedAt = 100L,
    createdAt = 1L,
    updatedAt = 100L,
)
