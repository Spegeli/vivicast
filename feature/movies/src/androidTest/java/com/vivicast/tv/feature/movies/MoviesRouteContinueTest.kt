package com.vivicast.tv.feature.movies

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
            )
        }

        compose.onAllNodesWithText("Fortsetzen").assertCountEquals(2)
        compose.onAllNodesWithText("Continue Movie").assertCountEquals(2)
        compose.onNodeWithText("42 %").assertIsDisplayed()
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
    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakeFavoritesRepository : FavoritesRepository {
    override fun observeFavorites(providerId: String, mediaType: MediaType): Flow<List<Favorite>> = flowOf(emptyList())
    override suspend fun isFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = false
    override suspend fun addFavorite(favorite: Favorite) = Unit
    override suspend fun removeFavorite(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun toggleFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean = true
}

private class FakePlaybackRepository : PlaybackRepository {
    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> =
        flowOf(listOf(TEST_PROGRESS).filter { it.providerId == providerId })

    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        TEST_PROGRESS.takeIf { it.providerId == providerId && it.mediaType == mediaType && it.mediaId == mediaId }

    override suspend fun saveProgress(progress: PlaybackProgress) = Unit
    override suspend fun saveChannelHistory(history: ChannelHistory) = Unit
    override suspend fun clearProviderPlayback(providerId: String) = Unit
}

private const val PROVIDER_ID = "provider-movies"
private const val MOVIE_CATEGORY_ID = "category-action"
private const val MOVIE_ID = "movie-continue"

private val TEST_PROVIDER = Provider(
    id = PROVIDER_ID,
    name = "Provider Movies",
    type = ProviderType.Xtream,
    credentialsKey = "credentials-provider-movies",
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
