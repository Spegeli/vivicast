package com.vivicast.tv.feature.series

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

class SeriesViewModelTest {

    private fun newViewModel(
        scope: CoroutineScope,
        providers: ProviderRepository = FakeProviderRepository(),
        media: MediaRepository = FakeMediaRepository(),
        favorites: FavoritesRepository = FakeFavoritesRepository(),
        playback: PlaybackRepository = FakePlaybackRepository(),
    ): SeriesViewModel = SeriesViewModel(providers, media, favorites, playback, scope = scope)

    private fun fullMedia() = FakeMediaRepository(
        categories = mapOf(PROVIDER to listOf(category("cat-1", "Drama"))),
        series = listOf(series("s1", "cat-1", "Alpha")),
        seasonsBySeries = mapOf("s1" to listOf(season("se1", "s1", 1), season("se2", "s1", 2))),
        episodesBySeason = mapOf("se1" to listOf(episode("e1", "s1", "se1", 1)), "se2" to listOf(episode("e2", "s1", "se2", 2))),
    )

    @Test
    fun initialState_withoutProviders_isEmpty() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope)
        val state = vm.uiState.value
        assertTrue(state.providers.isEmpty())
        assertTrue(state.seriesItems.isEmpty())
        assertNull(state.selectedProviderId)
        scope.cancel()
    }

    @Test
    fun defaults_selectProviderCategorySeriesSeasonEpisode() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = fullMedia())
        val state = vm.uiState.value
        assertEquals(PROVIDER, state.selectedProviderId)
        assertEquals("cat-1", state.selectedCategoryId)
        assertEquals(listOf("s1"), state.seriesItems.map { it.id })
        assertEquals("se1", state.selectedSeasonId)
        assertEquals(listOf("e1"), state.episodes.map { it.id })
        assertEquals("e1", state.selectedEpisodeId)
        scope.cancel()
    }

    @Test
    fun openAndCloseDetail() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = fullMedia())
        vm.onOpenSeriesDetail("s1")
        assertEquals("s1", vm.uiState.value.detailSeries?.id)
        vm.onCloseDetail()
        assertNull(vm.uiState.value.detailSeries)
        scope.cancel()
    }

    @Test
    fun onSeasonSelected_loadsEpisodesAndResetsEpisodeSelection() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = fullMedia())
        vm.onOpenSeriesDetail("s1")

        vm.onSeasonSelected("se2")

        assertEquals("se2", vm.uiState.value.selectedSeasonId)
        assertEquals(listOf("e2"), vm.uiState.value.episodes.map { it.id })
        assertEquals("e2", vm.uiState.value.selectedEpisodeId)
        scope.cancel()
    }

    @Test
    fun favorites_category_and_toggle() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val favorites = FakeFavoritesRepository(listOf(favorite("s1")))
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = fullMedia(), favorites = favorites)

        assertTrue("s1" in vm.uiState.value.favoriteSeriesIds)
        vm.onCategorySelected(FAVORITES_CATEGORY_ID)
        assertEquals(listOf("s1"), vm.uiState.value.seriesItems.map { it.id })

        vm.onToggleFavorite("s1")
        assertEquals(listOf("s1"), favorites.toggled)
        scope.cancel()
    }

    @Test
    fun continueTargets_resolvedAndCategoryShown() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val playback = FakePlaybackRepository(continueProgress = listOf(episodeProgress("e1", 42)))
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = fullMedia(), playback = playback)

        val state = vm.uiState.value
        assertTrue(state.hasContinueSeries)
        assertTrue(state.continueTargetsBySeriesId.containsKey("s1"))

        vm.onCategorySelected(CONTINUE_CATEGORY_ID)
        assertEquals(listOf("s1"), vm.uiState.value.seriesItems.map { it.id })
        scope.cancel()
    }

    @Test
    fun markEpisodeSeen_thenUnseen() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val playback = FakePlaybackRepository()
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = fullMedia(), playback = playback)
        vm.onOpenSeriesDetail("s1")

        vm.onMarkEpisodeSeen()
        assertEquals(true, vm.uiState.value.selectedEpisodeProgress?.isCompleted)
        assertTrue(playback.saved.any { it.mediaId == "e1" && it.isCompleted })

        vm.onMarkEpisodeUnseen()
        assertTrue(playback.deleted.contains("e1"))
        assertNull(vm.uiState.value.selectedEpisodeProgress)
        scope.cancel()
    }

    @Test
    fun target_series_season_episode_isConsumedAndSelected() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = fullMedia())

        vm.onTarget(PROVIDER, "cat-1", "s1", "se2", "e2")

        val state = vm.uiState.value
        assertEquals("s1", state.consumedTargetSeriesId)
        assertEquals("s1", state.detailSeries?.id)
        assertEquals("se2", state.selectedSeasonId)
        assertEquals("e2", state.selectedEpisodeId)
        scope.cancel()
    }

    @Test
    fun detail_autoClosesWhenSeriesLeavesNonEmptyList() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "Drama"), category("cat-2", "Comedy"))),
            series = listOf(series("s1", "cat-1", "Alpha"), series("s2", "cat-2", "Beta")),
            seasonsBySeries = mapOf("s1" to listOf(season("se1", "s1", 1))),
            episodesBySeason = mapOf("se1" to listOf(episode("e1", "s1", "se1", 1))),
        )
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media)
        vm.onOpenSeriesDetail("s1")
        assertEquals("s1", vm.uiState.value.detailSeries?.id)

        vm.onCategorySelected("cat-2")

        assertNull(vm.uiState.value.detailSeriesId)
        assertNull(vm.uiState.value.detailSeries)
        scope.cancel()
    }

    @Test
    fun providerFlowUpdates_areReflected() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providersRepo = FakeProviderRepository()
        val vm = newViewModel(scope, providers = providersRepo, media = fullMedia())
        assertTrue(vm.uiState.value.providers.isEmpty())

        providersRepo.providersFlow.value = listOf(provider())

        assertEquals(PROVIDER, vm.uiState.value.selectedProviderId)
        scope.cancel()
    }
}

private const val PROVIDER = "provider-1"

private fun provider(id: String = PROVIDER) = Provider(
    id = id, name = "Provider", type = ProviderType.Xtream, sourceConfigKey = "key",
    isActive = true, status = ProviderStatus.Active, includeLiveTv = false, includeMovies = false,
    includeSeries = true, refreshIntervalHours = 12, logoPriority = "provider", createdAt = 1L, updatedAt = 1L,
)

private fun category(id: String, name: String) = Category(id, PROVIDER, CategoryType.Series, id, name, 0, false)

private fun series(id: String, categoryId: String, name: String) = Series(
    id = id, providerId = PROVIDER, categoryId = categoryId, remoteId = id, name = name,
    originalName = null, posterUrl = null, backdropUrl = null, rating = null, year = null,
    genre = null, director = null, cast = null, plot = null, addedAt = null,
)

private fun season(id: String, seriesId: String, number: Int) = Season(
    id = id, providerId = PROVIDER, seriesId = seriesId, seasonNumber = number, name = "Season $number", posterUrl = null,
)

private fun episode(id: String, seriesId: String, seasonId: String, number: Int) = Episode(
    id = id, providerId = PROVIDER, seriesId = seriesId, seasonId = seasonId, remoteId = id,
    episodeNumber = number, seasonNumber = number, name = "Ep $number", plot = null, thumbnailUrl = null,
    containerExtension = "mp4", duration = null, airDate = null,
)

private fun favorite(mediaId: String) = Favorite(
    id = "fav-$mediaId", providerId = PROVIDER, mediaType = MediaType.Series, mediaId = mediaId,
    sortOrder = 0, createdAt = 1L, updatedAt = 1L,
)

private fun episodeProgress(mediaId: String, percent: Int) = PlaybackProgress(
    id = "progress-$mediaId", providerId = PROVIDER, mediaType = MediaType.Episode, mediaId = mediaId,
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
    private val series: List<Series> = emptyList(),
    private val seasonsBySeries: Map<String, List<Season>> = emptyMap(),
    private val episodesBySeason: Map<String, List<Episode>> = emptyMap(),
) : MediaRepository {
    private val allEpisodes = episodesBySeason.values.flatten()

    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        flowOf(if (type == CategoryType.Series) categories[providerId].orEmpty() else emptyList())

    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> =
        flowOf(series.filter { it.providerId == providerId && (categoryId == null || it.categoryId == categoryId) })

    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> =
        flowOf(seasonsBySeries[seriesId].orEmpty())

    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> =
        flowOf(episodesBySeason[seasonId].orEmpty())

    override suspend fun getSeries(providerId: String, seriesId: String): Series? =
        series.firstOrNull { it.providerId == providerId && it.id == seriesId }

    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? =
        allEpisodes.firstOrNull { it.providerId == providerId && it.id == episodeId }

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> = emptyFlow()
    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = emptyFlow()
    override suspend fun getChannel(providerId: String, channelId: String): Channel? = null
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = null
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
) : PlaybackRepository {
    val continueFlow = MutableStateFlow(continueProgress)
    val saved = mutableListOf<PlaybackProgress>()
    val deleted = mutableListOf<String>()

    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> = continueFlow
    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> = continueFlow
    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> = flowOf(emptyList())
    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        continueFlow.value.firstOrNull { it.providerId == providerId && it.mediaType == mediaType && it.mediaId == mediaId }
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
