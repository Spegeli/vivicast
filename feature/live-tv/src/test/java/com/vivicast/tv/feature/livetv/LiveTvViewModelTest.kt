package com.vivicast.tv.feature.livetv

import com.vivicast.tv.data.epg.EpgRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
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

private const val PROVIDER = "provider-1"
private const val NOW = 1_000_000L

class LiveTvViewModelTest {

    private fun newViewModel(
        scope: CoroutineScope,
        providers: ProviderRepository = FakeProviderRepository(),
        media: MediaRepository = FakeMediaRepository(),
        epg: EpgRepository = FakeEpgRepository(),
        favorites: FavoritesRepository = FakeFavoritesRepository(),
        now: Long = NOW,
    ): LiveTvViewModel = LiveTvViewModel(providers, media, epg, favorites, nowProvider = { now }, scope = scope)

    private fun mediaWithChannels() = FakeMediaRepository(
        categories = mapOf(PROVIDER to listOf(category("cat-1", "News"))),
        channels = listOf(channel("c1", "cat-1", "Alpha"), channel("c2", "cat-1", "Beta")),
    )

    @Test
    fun initialState_withoutProviders_isEmpty() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope)
        val state = vm.uiState.value
        assertTrue(state.providers.isEmpty())
        assertTrue(state.channels.isEmpty())
        assertNull(state.selectedProviderId)
        scope.cancel()
    }

    @Test
    fun providerDefault_andChannelsLoadedWithExpansion() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = mediaWithChannels())
        vm.onExpandedProvidersChanged(setOf(PROVIDER))

        val state = vm.uiState.value
        assertEquals(PROVIDER, state.selectedProviderId)
        assertEquals("cat-1", state.selectedCategoryId)
        assertEquals(listOf("c1", "c2"), state.channels.map { it.id })
        assertEquals("c1", state.selectedChannelId)
        scope.cancel()
    }

    @Test
    fun categoryDefault_onlyWhenProviderExpanded() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = mediaWithChannels())
        // No expansion -> category stays null (mirrors original guard).
        assertNull(vm.uiState.value.selectedCategoryId)

        vm.onExpandedProvidersChanged(setOf(PROVIDER))
        assertEquals("cat-1", vm.uiState.value.selectedCategoryId)
        scope.cancel()
    }

    @Test
    fun onCategorySelected_resetsChannelSelection() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "News"), category("cat-2", "Sport"))),
            channels = listOf(channel("c1", "cat-1", "Alpha"), channel("c2", "cat-2", "Beta")),
        )
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media)
        vm.onExpandedProvidersChanged(setOf(PROVIDER))
        assertEquals("c1", vm.uiState.value.selectedChannelId)

        vm.onCategorySelected("cat-2")
        assertEquals("cat-2", vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("c2"), vm.uiState.value.channels.map { it.id })
        assertEquals("c2", vm.uiState.value.selectedChannelId)
        scope.cancel()
    }

    @Test
    fun epgProgramsLoaded_currentAndNextComputed() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val current = program("p-current", "c1", NOW - 1_000L, NOW + 1_000L)
        val next = program("p-next", "c1", NOW + 2_000L, NOW + 4_000L)
        val epg = FakeEpgRepository(mapOf("c1" to listOf(current, next)))
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = mediaWithChannels(), epg = epg)
        vm.onExpandedProvidersChanged(setOf(PROVIDER))

        val state = vm.uiState.value
        assertEquals(NOW, state.nowMillis)
        assertEquals("p-current", state.currentProgram?.id)
        assertEquals("p-next", state.nextProgram?.id)
        scope.cancel()
    }

    @Test
    fun favorites_categoryShowsFavoriteChannels_andToggleDelegates() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "News"))),
            channels = listOf(channel("c1", "cat-1", "Alpha")),
        )
        val favorites = FakeFavoritesRepository(listOf(favorite("c1")))
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media, favorites = favorites)
        vm.onExpandedProvidersChanged(setOf(PROVIDER))

        assertTrue("c1" in vm.uiState.value.favoriteChannelIds)
        assertEquals(1, vm.uiState.value.favoriteChannelCount)

        vm.onGlobalFavoritesSelected()
        assertEquals(FAVORITES_CATEGORY_ID, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("c1"), vm.uiState.value.channels.map { it.id })

        vm.onToggleFavorite()
        assertEquals(listOf("c1"), favorites.toggled)
        scope.cancel()
    }

    @Test
    fun target_setsSelectionAndLoadsTargetChannel() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = mediaWithChannels())

        vm.onTarget(PROVIDER, "cat-1", "c2", null)

        val state = vm.uiState.value
        assertEquals(PROVIDER, state.selectedProviderId)
        assertEquals("cat-1", state.selectedCategoryId)
        assertEquals("c2", state.selectedChannelId)
        assertEquals("c2", state.selectedChannel?.id)
        scope.cancel()
    }

    @Test
    fun channelResetSignal_incrementsWhenSelectionLeavesList() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val media = FakeMediaRepository(
            categories = mapOf(PROVIDER to listOf(category("cat-1", "News"), category("cat-2", "Sport"))),
            channels = listOf(channel("c1", "cat-1", "Alpha"), channel("c2", "cat-2", "Beta")),
        )
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(provider())), media = media)
        vm.onExpandedProvidersChanged(setOf(PROVIDER))
        val before = vm.uiState.value.channelResetSignal

        vm.onCategorySelected("cat-2")

        assertTrue(vm.uiState.value.channelResetSignal > before)
        scope.cancel()
    }

    @Test
    fun providerFlowUpdates_areReflected() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val providersRepo = FakeProviderRepository()
        val vm = newViewModel(scope, providers = providersRepo, media = mediaWithChannels())
        assertTrue(vm.uiState.value.providers.isEmpty())

        providersRepo.providersFlow.value = listOf(provider())

        assertEquals(PROVIDER, vm.uiState.value.selectedProviderId)
        scope.cancel()
    }

    @Test
    fun inactiveProviders_areExcludedFromList() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val active = provider(id = "active", isActive = true)
        val inactive = provider(id = "inactive", isActive = false)
        val vm = newViewModel(scope, providers = FakeProviderRepository(listOf(inactive, active)), media = mediaWithChannels())

        val ids = vm.uiState.value.providers.map { it.id }
        assertEquals(listOf("active"), ids)
        assertEquals("active", vm.uiState.value.selectedProviderId)
        scope.cancel()
    }
}

private fun provider(id: String = PROVIDER, isActive: Boolean = true) = Provider(
    id = id, name = "Provider", type = ProviderType.Xtream, sourceConfigKey = "key",
    isActive = isActive, status = if (isActive) ProviderStatus.Active else ProviderStatus.Disabled,
    includeLiveTv = true, includeMovies = false,
    includeSeries = false, refreshIntervalHours = 12, logoPriority = "provider", createdAt = 1L, updatedAt = 1L,
)

private fun category(id: String, name: String) = Category(id, PROVIDER, CategoryType.LiveTv, id, name, 0, false)

private fun channel(id: String, categoryId: String, name: String) = Channel(
    id = id, providerId = PROVIDER, categoryId = categoryId, remoteId = id, channelNumber = "1",
    name = name, logoUrl = null, isCatchupAvailable = false, catchupDays = 0,
)

private fun favorite(mediaId: String) = Favorite(
    id = "fav-$mediaId", providerId = PROVIDER, mediaType = MediaType.Channel, mediaId = mediaId,
    sortOrder = 0, createdAt = 1L, updatedAt = 1L,
)

private fun program(id: String, channelId: String, start: Long, end: Long) = EpgProgram(
    id = id, providerId = PROVIDER, channelId = channelId, epgSourceId = "src", epgChannelId = channelId,
    title = "Program $id", subtitle = null, description = null, startTime = start, endTime = end,
    category = null, iconUrl = null, isCatchupAvailable = false,
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
    private val channels: List<Channel> = emptyList(),
) : MediaRepository {
    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        flowOf(if (type == CategoryType.LiveTv) categories[providerId].orEmpty() else emptyList())

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> =
        flowOf(channels.filter { it.providerId == providerId && (categoryId == null || it.categoryId == categoryId) })

    override suspend fun getChannel(providerId: String, channelId: String): Channel? =
        channels.firstOrNull { it.providerId == providerId && it.id == channelId }

    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> = emptyFlow()
    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> = emptyFlow()
    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> = emptyFlow()
    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> = emptyFlow()
    override suspend fun getMovie(providerId: String, movieId: String): Movie? = null
    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? = null
    override suspend fun search(query: String, limitPerType: Int): SearchResults =
        SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakeEpgRepository(
    private val programsByChannel: Map<String, List<EpgProgram>> = emptyMap(),
) : EpgRepository {
    override fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>> = flowOf(programsByChannel[channelId].orEmpty())

    override fun observeCurrentProgramsForChannels(
        providerId: String,
        channelIds: List<String>,
        nowMillis: Long,
    ): Flow<List<EpgProgram>> = flowOf(
        channelIds.flatMap { programsByChannel[it].orEmpty() }
            .filter { nowMillis >= it.startTime && nowMillis < it.endTime },
    )

    override fun observeEpgSources(): Flow<List<EpgSource>> = emptyFlow()
    override suspend fun getEpgSources(): List<EpgSource> = emptyList()
    override fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>> = emptyFlow()
    override fun observeChannelsForProvider(providerId: String): Flow<List<Channel>> = emptyFlow()
    override fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>> = emptyFlow()
    override suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): EpgChannelMapping =
        throw UnsupportedOperationException()
    override suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String) = Unit
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
