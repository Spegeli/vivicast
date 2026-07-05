package com.vivicast.tv.feature.movies

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

class MoviesViewModelTest {

    private fun newViewModel(
        scope: CoroutineScope,
        providers: ProviderRepository = FakeProviderRepository(),
        media: MediaRepository = FakeMediaRepository(),
        favorites: FavoritesRepository = FakeFavoritesRepository(),
        playback: PlaybackRepository = FakePlaybackRepository(),
    ): MoviesViewModel = MoviesViewModel(providers, media, favorites, playback, scope = scope)

    @Test
    fun initialState_withoutProviders_isEmpty() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope)

        val state = vm.uiState.value
        assertTrue(state.providers.isEmpty())
        assertTrue(state.movies.isEmpty())
        assertNull(state.selectedProviderId)
        scope.cancel()
    }

    @Test
    fun providerAndCategoryDefaults_andMoviesAreLoaded() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Action"))),
            movies = listOf(movie("m1", "cat-1", "Alpha"), movie("m2", "cat-1", "Beta")),
        )
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media)

        val state = vm.uiState.value
        assertEquals(PROVIDER, state.selectedProviderId)
        assertEquals("cat-1", state.selectedCategoryId)
        assertEquals(listOf("m1", "m2"), state.movies.map { it.id })
        scope.cancel()
    }

    @Test
    fun favoritesCategory_showsFavoriteMovies_andFavoriteIds() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Action"))),
            movies = listOf(movie("m1", "cat-1", "Alpha")),
        )
        val favorites = FakeFavoritesRepository(listOf(favorite("m1")))
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media, favorites = favorites)

        assertTrue("m1" in vm.uiState.value.favoriteMovieIds)
        vm.onCategorySelected(FAVORITES_CATEGORY_ID)
        assertEquals(listOf("m1"), vm.uiState.value.movies.map { it.id })
        scope.cancel()
    }

    @Test
    fun onToggleFavorite_delegatesToRepository() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val favorites = FakeFavoritesRepository()
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), favorites = favorites)

        vm.onToggleFavorite("m1")

        assertEquals(listOf("m1"), favorites.toggled)
        scope.cancel()
    }

    @Test
    fun continueProgress_addsContinueCategoryAndProgressMap() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Action"))),
            movies = listOf(movie("m1", "cat-1", "Alpha")),
        )
        val playback = FakePlaybackRepository(continueProgress = listOf(movieProgress("m1", 42)))
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media, playback = playback)

        val state = vm.uiState.value
        assertTrue(state.hasContinueMovies)
        assertEquals(42, state.continueProgressByMovieId["m1"]?.progressPercent)

        vm.onCategorySelected(CONTINUE_CATEGORY_ID)
        assertEquals(listOf("m1"), vm.uiState.value.movies.map { it.id })
        scope.cancel()
    }

    @Test
    fun onOpenDetail_loadsDetailMovieAndProgress_onCloseClears() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Action"))),
            movies = listOf(movie("m1", "cat-1", "Alpha")),
        )
        val playback = FakePlaybackRepository(progressStore = mutableListOf(movieProgress("m1", 30)))
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media, playback = playback)

        vm.onOpenDetail("m1")
        assertEquals("m1", vm.uiState.value.detailMovie?.id)
        assertEquals(30, vm.uiState.value.detailProgress?.progressPercent)

        vm.onCloseDetail()
        assertNull(vm.uiState.value.detailMovie)
        scope.cancel()
    }

    @Test
    fun openDetail_autoClosesWhenMovieLeavesNonEmptyList() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Action"), category("cat-2", "Drama"))),
            movies = listOf(movie("m1", "cat-1", "Alpha"), movie("m2", "cat-2", "Beta")),
        )
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media)
        vm.onOpenDetail("m1")
        assertEquals("m1", vm.uiState.value.detailMovie?.id)

        vm.onCategorySelected("cat-2")

        assertNull(vm.uiState.value.detailMovieId)
        assertNull(vm.uiState.value.detailMovie)
        scope.cancel()
    }

    @Test
    fun openDetail_staysOpenWhenNewListIsEmpty() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Action"), category("cat-empty", "Empty"))),
            movies = listOf(movie("m1", "cat-1", "Alpha")),
        )
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media)
        vm.onOpenDetail("m1")
        assertEquals("m1", vm.uiState.value.detailMovie?.id)

        vm.onCategorySelected("cat-empty")

        assertEquals("m1", vm.uiState.value.detailMovie?.id)
        scope.cancel()
    }

    @Test
    fun onMarkSeen_savesCompletedProgress_onMarkUnseenDeletes() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val movie = movie("m1", "cat-1", "Alpha")
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Action"))),
            movies = listOf(movie),
        )
        val playback = FakePlaybackRepository()
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media, playback = playback)
        vm.onOpenDetail("m1")

        vm.onMarkSeen(movie)
        assertEquals(true, vm.uiState.value.detailProgress?.isCompleted)
        assertTrue(playback.saved.any { it.mediaId == "m1" && it.isCompleted })

        vm.onMarkUnseen(movie)
        assertTrue(playback.deleted.contains("m1"))
        scope.cancel()
    }

    @Test
    fun providerFlowUpdates_areReflected() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providersRepo = FakeProviderRepository()
        val vm = newViewModel(scope, providers = providersRepo)
        assertTrue(vm.uiState.value.providers.isEmpty())

        providersRepo.providersFlow.value = listOf(provider())

        assertEquals(PROVIDER, vm.uiState.value.selectedProviderId)
        scope.cancel()
    }
}

private const val PROVIDER = "provider-1"

private fun provider(id: String = PROVIDER, active: Boolean = true) = Provider(
    id = id, name = "Provider", type = ProviderType.Xtream, sourceConfigKey = "key",
    isActive = active, status = ProviderStatus.Active, includeLiveTv = false, includeMovies = true,
    includeSeries = false, refreshIntervalHours = 12, logoPriority = "provider", createdAt = 1L, updatedAt = 1L,
)

private fun category(id: String, name: String) = Category(id, PROVIDER, CategoryType.Movies, id, name, 0, false)

private fun movie(id: String, categoryId: String, name: String) = Movie(
    id = id, providerId = PROVIDER, categoryId = categoryId, remoteId = id, name = name,
    originalName = null, containerExtension = "mp4", posterUrl = null, backdropUrl = null,
    rating = null, year = null, genre = null, duration = null, director = null, cast = null,
    plot = null, trailerUrl = null, addedAt = null,
)

private fun favorite(mediaId: String) = Favorite(
    id = "fav-$mediaId", providerId = PROVIDER, mediaType = MediaType.Movie, mediaId = mediaId,
    sortOrder = 0, createdAt = 1L, updatedAt = 1L,
)

private fun movieProgress(mediaId: String, percent: Int) = PlaybackProgress(
    id = "progress-$mediaId", providerId = PROVIDER, mediaType = MediaType.Movie, mediaId = mediaId,
    positionMillis = 10L, durationMillis = 100L, progressPercent = percent, isCompleted = false,
    lastWatchedAt = 1L, createdAt = 1L, updatedAt = 1L,
)

private class FakeProviderRepository(providers: List<Provider> = emptyList()) : ProviderRepository {
    val providersFlow = MutableStateFlow(providers)
    override fun observeProviders(): Flow<List<Provider>> = providersFlow
    override suspend fun getProvider(providerId: String): Provider? = providersFlow.value.firstOrNull { it.id == providerId }
    override suspend fun getCredentials(providerId: String): ProviderCredentials? = null
    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
        ProviderSaveResult(provider(), hasDuplicateName = false)
    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
        ProviderSaveResult(provider(), hasDuplicateName = false)
    override suspend fun saveProvider(provider: Provider) = Unit
    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit
    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit
    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit
    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
}

private class FakeMediaRepository(
    private val categories: Map<String, List<Category>> = emptyMap(),
    private val movies: List<Movie> = emptyList(),
) : MediaRepository {
    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        flowOf(if (type == CategoryType.Movies) categories[providerId].orEmpty() else emptyList())

    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> =
        flowOf(movies.filter { it.providerId == providerId && (categoryId == null || it.categoryId == categoryId) })

    override suspend fun getMovie(providerId: String, movieId: String): Movie? =
        movies.firstOrNull { it.providerId == providerId && it.id == movieId }

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = emptyFlow()
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = emptyFlow()
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = emptyFlow()
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = emptyFlow()
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = null
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = null
    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakeFavoritesRepository(favorites: List<Favorite> = emptyList()) : FavoritesRepository {
    val favoritesFlow = MutableStateFlow(favorites)
    val toggled = mutableListOf<String>()
    override fun observeFavorites(providerId: String, mediaType: MediaType): Flow<List<Favorite>> = favoritesFlow
    override fun observeFavorites(mediaType: MediaType): Flow<List<Favorite>> = favoritesFlow
    override suspend fun isFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean =
        favoritesFlow.value.any { it.mediaId == mediaId }
    override suspend fun addFavorite(favorite: Favorite) = Unit
    override suspend fun removeFavorite(providerId: String, mediaType: MediaType, mediaId: String) = Unit
    override suspend fun toggleFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean {
        toggled += mediaId
        return true
    }
}

private class FakePlaybackRepository(
    continueProgress: List<PlaybackProgress> = emptyList(),
    private val progressStore: MutableList<PlaybackProgress> = mutableListOf(),
) : PlaybackRepository {
    val continueFlow = MutableStateFlow(continueProgress)
    val saved = mutableListOf<PlaybackProgress>()
    val deleted = mutableListOf<String>()

    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> = continueFlow
    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> = continueFlow
    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        progressStore.firstOrNull { it.providerId == providerId && it.mediaType == mediaType && it.mediaId == mediaId }
    override suspend fun saveProgress(progress: PlaybackProgress) {
        saved += progress
    }
    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) {
        deleted += mediaId
    }
    override suspend fun saveChannelHistory(history: ChannelHistory) = Unit
    override suspend fun clearProviderPlayback(providerId: String) = Unit
    override suspend fun clearLiveTvHistory() = Unit
    override suspend fun clearMovieProgress() = Unit
    override suspend fun clearSeriesProgress() = Unit
}
