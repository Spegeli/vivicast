package com.vivicast.tv.worker

import com.vivicast.tv.data.epg.EpgImportRepository
import com.vivicast.tv.data.epg.EpgImportResult
import com.vivicast.tv.data.epg.EpgStreamSource
import com.vivicast.tv.data.epg.EpgSourceSaveRequest
import com.vivicast.tv.data.media.CatalogImportRepository
import com.vivicast.tv.data.media.CatalogImportResult
import com.vivicast.tv.data.media.XtreamCatalog
import com.vivicast.tv.data.media.XtreamCatalogImportResult
import com.vivicast.tv.data.media.ImportCount
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.core.cache.MediaCacheCleanupResult
import com.vivicast.tv.core.cache.MediaCacheEntry
import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheStats
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.cache.MediaCacheType
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.iptv.m3u.DefaultM3uParser
import com.vivicast.tv.iptv.m3u.M3uPlaylist
import com.vivicast.tv.iptv.xmltv.DefaultXmltvParser
import com.vivicast.tv.iptv.xmltv.XmltvDocument
import com.vivicast.tv.iptv.xtream.XtreamCategory
import com.vivicast.tv.iptv.xtream.XtreamClient
import com.vivicast.tv.iptv.xtream.XtreamCredentials
import com.vivicast.tv.iptv.xtream.XtreamLiveStream
import com.vivicast.tv.iptv.xtream.XtreamParser
import com.vivicast.tv.iptv.xtream.XtreamSeriesInfo
import com.vivicast.tv.iptv.xtream.XtreamSeriesItem
import com.vivicast.tv.iptv.xtream.XtreamUserInfo
import com.vivicast.tv.iptv.xtream.XtreamVodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshExecutionTest {
    @Test
    fun m3uPlaylistRefreshFetchesSecretUrlImportsPlaylistAndUpdatesStatus() = runBlocking {
        val provider = provider(type = ProviderType.M3u)
        val providerRepository = FakeProviderRepository(
            providers = listOf(provider),
            credentials = mapOf(provider.id to ProviderCredentials.M3u(url = "https://playlist.example/list.m3u?token=secret")),
        )
        val catalogRepository = FakeCatalogImportRepository()
        val textFetcher = FakeTextFetcher(
            "https://playlist.example/list.m3u?token=secret" to """
                #EXTM3U
                #EXTINF:-1 tvg-id="ard.de" tvg-name="ARD" group-title="News",ARD
                https://stream.example/ard.m3u8
            """.trimIndent(),
        )
        val refresher = DefaultPlaylistRefresher(
            providerRepository = providerRepository,
            catalogImportRepository = catalogRepository,
            m3uParser = DefaultM3uParser(),
            textFetcher = textFetcher,
            xtreamClient = EmptyXtreamClient,
            xtreamParser = EmptyXtreamParser,
            epgSourceReader = FakeEpgSourceReader(sourceIds = listOf("epg-1")),
        )

        val outcome = refresher.refresh(PlaylistRefreshTarget(provider.id))

        assertEquals(
            PlaylistRefreshOutcome(provider.id, success = true, epgSourceIds = listOf("epg-1"), channelsImported = 1),
            outcome,
        )
        assertEquals(listOf(ProviderStatus.Refreshing, ProviderStatus.Active), providerRepository.statuses)
        assertEquals(listOf("https://playlist.example/list.m3u?token=secret"), textFetcher.urls)
        assertEquals(provider.id, catalogRepository.m3uProviderId)
        assertEquals("ARD", catalogRepository.m3uPlaylist?.channels?.single()?.name)
    }

    @Test
    fun playlistRefreshSkipsWhenAlreadyRunningInProcess() = runBlocking {
        val provider = provider(type = ProviderType.M3u)
        val guard = RefreshRunGuard()
        // Simulate an in-flight refresh already holding the per-provider lock.
        guard.tryEnter(provider.id)
        val refresher = DefaultPlaylistRefresher(
            providerRepository = FakeProviderRepository(
                providers = listOf(provider),
                credentials = mapOf(provider.id to ProviderCredentials.M3u(url = "https://x")),
            ),
            catalogImportRepository = FakeCatalogImportRepository(),
            m3uParser = DefaultM3uParser(),
            textFetcher = FakeTextFetcher(),
            xtreamClient = EmptyXtreamClient,
            xtreamParser = EmptyXtreamParser,
            epgSourceReader = FakeEpgSourceReader(),
            refreshRunGuard = guard,
        )

        val outcome = refresher.refresh(PlaylistRefreshTarget(provider.id))

        // Skipped (not failed): the runner must map this to Success, not Retry.
        assertEquals(false, outcome.success)
        assertEquals(true, outcome.skipped)
    }

    @Test
    fun seriesDetailsRefresherImportsForActiveXtreamProvider() = runBlocking {
        val xtreamProvider = provider(id = "xtream", type = ProviderType.Xtream)
        val catalog = FakeCatalogImportRepository()
        val refresher = DefaultSeriesDetailsRefresher(
            providerRepository = FakeProviderRepository(
                providers = listOf(xtreamProvider),
                credentials = mapOf(
                    xtreamProvider.id to ProviderCredentials.Xtream(
                        serverUrl = "https://xtream.example",
                        username = "user",
                        password = "pass",
                    ),
                ),
            ),
            catalogImportRepository = catalog,
            xtreamClient = EmptyXtreamClient,
            xtreamParser = StubXtreamParser(seriesCount = 2),
        )

        val outcome = refresher.refresh(xtreamProvider.id)

        assertEquals(true, outcome.success)
        assertEquals(xtreamProvider.id, catalog.seriesDetailsProviderId)
        // One getSeriesInfo per series -> all infos handed to the (background) series-details import.
        assertEquals(2, catalog.seriesDetailsInfos?.size)
    }

    @Test
    fun seriesDetailsRefresherSkipsNonXtreamProviderWithoutImporting() = runBlocking {
        val m3uProvider = provider(id = "m3u", type = ProviderType.M3u)
        val catalog = FakeCatalogImportRepository()
        val refresher = DefaultSeriesDetailsRefresher(
            providerRepository = FakeProviderRepository(
                providers = listOf(m3uProvider),
                credentials = mapOf(m3uProvider.id to ProviderCredentials.M3u(url = "https://playlist.example/list.m3u")),
            ),
            catalogImportRepository = catalog,
            xtreamClient = EmptyXtreamClient,
            xtreamParser = StubXtreamParser(seriesCount = 0),
        )

        val outcome = refresher.refresh(m3uProvider.id)

        assertEquals(true, outcome.success)
        assertEquals(null, catalog.seriesDetailsProviderId)
    }

    @Test
    fun m3uPlaylistRefreshImportsManualInlineContentWithoutFetchingUrl() = runBlocking {
        val provider = provider(type = ProviderType.M3u)
        val providerRepository = FakeProviderRepository(
            providers = listOf(provider),
            credentials = mapOf(
                provider.id to ProviderCredentials.M3u(
                    sourceMode = M3uSourceMode.File,
                    inlineContent = """
                        #EXTM3U
                        #EXTINF:-1 tvg-name="ARD",ARD
                        https://stream.example/ard.m3u8
                    """.trimIndent(),
                ),
            ),
        )
        val catalogRepository = FakeCatalogImportRepository()
        val textFetcher = FakeTextFetcher()
        val refresher = DefaultPlaylistRefresher(
            providerRepository = providerRepository,
            catalogImportRepository = catalogRepository,
            m3uParser = DefaultM3uParser(),
            textFetcher = textFetcher,
            xtreamClient = EmptyXtreamClient,
            xtreamParser = EmptyXtreamParser,
            epgSourceReader = FakeEpgSourceReader(),
        )

        val outcome = refresher.refresh(PlaylistRefreshTarget(provider.id))

        assertEquals(
            PlaylistRefreshOutcome(provider.id, success = true, epgSourceIds = emptyList(), channelsImported = 1),
            outcome,
        )
        assertEquals(emptyList<String>(), textFetcher.urls)
        assertEquals("ARD", catalogRepository.m3uPlaylist?.channels?.single()?.name)
    }

    @Test
    fun playlistRefreshMarksAuthenticationFailuresWithoutImporting() = runBlocking {
        val provider = provider(type = ProviderType.M3u)
        val providerRepository = FakeProviderRepository(
            providers = listOf(provider),
            credentials = mapOf(provider.id to ProviderCredentials.M3u(url = "https://playlist.example/private.m3u")),
        )
        val catalogRepository = FakeCatalogImportRepository()
        val refresher = DefaultPlaylistRefresher(
            providerRepository = providerRepository,
            catalogImportRepository = catalogRepository,
            m3uParser = DefaultM3uParser(),
            textFetcher = object : TextFetcher {
                override suspend fun fetch(url: String, userAgent: String?): String = throw RefreshHttpException(401)
            },
            xtreamClient = EmptyXtreamClient,
            xtreamParser = EmptyXtreamParser,
            epgSourceReader = FakeEpgSourceReader(),
        )

        runCatching { refresher.refresh(PlaylistRefreshTarget(provider.id)) }

        assertEquals(listOf(ProviderStatus.Refreshing, ProviderStatus.InvalidCredentials), providerRepository.statuses)
        assertEquals(null, catalogRepository.m3uPlaylist)
    }

    @Test
    fun playlistRefreshMarksSuccessfulM3uImportWithSkippedEntriesAsPartialError() = runBlocking {
        val provider = provider(type = ProviderType.M3u)
        val providerRepository = FakeProviderRepository(
            providers = listOf(provider),
            credentials = mapOf(provider.id to ProviderCredentials.M3u(url = "https://playlist.example/list.m3u")),
        )
        val catalogRepository = FakeCatalogImportRepository()
        val refresher = DefaultPlaylistRefresher(
            providerRepository = providerRepository,
            catalogImportRepository = catalogRepository,
            m3uParser = DefaultM3uParser(),
            textFetcher = FakeTextFetcher(
                "https://playlist.example/list.m3u" to """
                    #EXTM3U
                    https://stream.example/orphan.m3u8
                    #EXTINF:-1 tvg-id="ard.de" tvg-name="ARD" group-title="News",ARD
                    https://stream.example/ard.m3u8
                """.trimIndent(),
            ),
            xtreamClient = EmptyXtreamClient,
            xtreamParser = EmptyXtreamParser,
            epgSourceReader = FakeEpgSourceReader(),
        )

        refresher.refresh(PlaylistRefreshTarget(provider.id))

        assertEquals(listOf(ProviderStatus.Refreshing, ProviderStatus.ActiveWithPartialErrors), providerRepository.statuses)
        assertEquals(1, catalogRepository.m3uPlaylist?.skippedEntries)
    }

    @Test
    fun playlistRefreshMarksMissingCredentialsAsRequiredWithoutImporting() = runBlocking {
        val provider = provider(type = ProviderType.M3u)
        val providerRepository = FakeProviderRepository(providers = listOf(provider))
        val catalogRepository = FakeCatalogImportRepository()
        val refresher = DefaultPlaylistRefresher(
            providerRepository = providerRepository,
            catalogImportRepository = catalogRepository,
            m3uParser = DefaultM3uParser(),
            textFetcher = FakeTextFetcher(),
            xtreamClient = EmptyXtreamClient,
            xtreamParser = EmptyXtreamParser,
            epgSourceReader = FakeEpgSourceReader(),
        )

        runCatching { refresher.refresh(PlaylistRefreshTarget(provider.id)) }

        assertEquals(listOf(ProviderStatus.Refreshing, ProviderStatus.CredentialsRequired), providerRepository.statuses)
        assertEquals(null, catalogRepository.m3uPlaylist)
    }

    @Test
    fun epgRefreshParsesOnceAndImportsForActiveLinkedProviders() = runBlocking {
        val activeProvider = provider(id = "provider-active")
        val disabledProvider = provider(id = "provider-disabled", isActive = false)
        val importRepository = FakeEpgImportRepository()
        val refresher = DefaultEpgRefresher(
            epgSourceReader = FakeEpgSourceReader(
                resolved = ResolvedEpgSource(
                    id = "epg-1",
                    url = "https://epg.example/file.xml?token=secret",
                    providerIds = listOf(activeProvider.id, disabledProvider.id),
                ),
            ),
            providerRepository = FakeProviderRepository(listOf(activeProvider, disabledProvider)),
            epgStreamSource = FakeEpgStreamSource(
                "https://epg.example/file.xml?token=secret" to """
                    <tv>
                      <channel id="ard.de"><display-name>ARD</display-name></channel>
                      <programme channel="ard.de" start="20260101080000 +0000" stop="20260101090000 +0000">
                        <title>Morning News</title>
                      </programme>
                    </tv>
                """.trimIndent(),
            ),
            xmltvParser = DefaultXmltvParser(),
            epgImportRepository = importRepository,
            epgPastRetentionDaysProvider = { 2 },
        )

        val outcome = refresher.refresh(EpgRefreshTarget("epg-1"))

        assertEquals(EpgRefreshOutcome("epg-1", success = true, channels = 1, programsImported = 1), outcome)
        assertEquals(listOf(activeProvider.id), importRepository.providerIds)
        assertEquals("Morning News", importRepository.documents.single().programs.single().title)
        assertEquals(listOf(2), importRepository.retentionRequests)
        // Feed-level metadata is recorded once for the source (channel + programme counts from the feed).
        val metadata = importRepository.refreshedMetadata.single()
        assertEquals("epg-1", metadata.sourceId)
        assertEquals(1, metadata.channelCount)
        assertEquals(1, metadata.programCount)
        // Refreshing flag is raised at the start and cleared afterwards.
        assertEquals(listOf("epg-1" to true, "epg-1" to false), importRepository.refreshingFlags)
    }

    @Test
    fun epgRefreshFailsBeforeImportWhenXmltvHasNoPrograms() = runBlocking {
        val provider = provider(id = "provider-active")
        val importRepository = FakeEpgImportRepository()
        val refresher = DefaultEpgRefresher(
            epgSourceReader = FakeEpgSourceReader(
                resolved = ResolvedEpgSource(
                    id = "epg-1",
                    url = "https://epg.example/file.xml",
                    providerIds = listOf(provider.id),
                ),
            ),
            providerRepository = FakeProviderRepository(listOf(provider)),
            epgStreamSource = FakeEpgStreamSource(
                "https://epg.example/file.xml" to """
                    <tv>
                      <channel id="ard.de"><display-name>ARD</display-name></channel>
                    </tv>
                """.trimIndent(),
            ),
            xmltvParser = DefaultXmltvParser(),
            epgImportRepository = importRepository,
        )

        runCatching { refresher.refresh(EpgRefreshTarget("epg-1")) }

        assertEquals(emptyList<String>(), importRepository.providerIds)
        assertEquals(emptyList<Int>(), importRepository.retentionRequests)
        // No valid feed → no metadata write, but the refreshing flag is still cleared in the finally block.
        assertEquals(emptyList<RefreshedMetadata>(), importRepository.refreshedMetadata)
        assertEquals(listOf("epg-1" to true, "epg-1" to false), importRepository.refreshingFlags)
    }

    @Test
    fun logoRefreshCachesMissingMediaImagesAndSkipsUnchangedCachedImages() = runBlocking {
        val cacheStore = FakeMediaCacheStore(
            cached = setOf(MediaCacheKey(MediaCacheType.ChannelLogo, "channel-cached", "https://logos.example/cached.png")),
        )
        val fetcher = FakeBinaryFetcher(
            "https://logos.example/new.png" to byteArrayOf(1, 2, 3),
            "https://posters.example/movie.png" to byteArrayOf(4, 5, 6),
            "https://backdrops.example/movie.png" to byteArrayOf(7, 8, 9),
            "https://episodes.example/episode.png" to byteArrayOf(10, 11, 12),
            "https://logos.example/broken.png" to null,
        )
        val refresher = DefaultLogoRefresher(
            mediaImageRefreshSource = object : MediaImageRefreshSource {
                override suspend fun collectImageTargets(): List<MediaImageRefreshTarget> =
                    listOf(
                        MediaImageRefreshTarget(MediaCacheType.ChannelLogo, "channel-cached", "https://logos.example/cached.png"),
                        MediaImageRefreshTarget(MediaCacheType.ChannelLogo, "channel-new", "https://logos.example/new.png"),
                        MediaImageRefreshTarget(MediaCacheType.MoviePoster, "movie-1", "https://posters.example/movie.png"),
                        MediaImageRefreshTarget(MediaCacheType.MovieBackdrop, "movie-1", "https://backdrops.example/movie.png"),
                        MediaImageRefreshTarget(MediaCacheType.EpisodeImage, "episode-1", "https://episodes.example/episode.png"),
                        MediaImageRefreshTarget(MediaCacheType.ChannelLogo, "channel-broken", "https://logos.example/broken.png"),
                    )
            },
            mediaCacheStore = cacheStore,
            binaryFetcher = fetcher,
        )

        refresher.refreshLogos()

        assertEquals(
            listOf(
                "https://logos.example/new.png",
                "https://posters.example/movie.png",
                "https://backdrops.example/movie.png",
                "https://episodes.example/episode.png",
                "https://logos.example/broken.png",
            ),
            fetcher.urls,
        )
        assertEquals(
            listOf(
                MediaCacheType.ChannelLogo to "channel-new",
                MediaCacheType.MoviePoster to "movie-1",
                MediaCacheType.MovieBackdrop to "movie-1",
                MediaCacheType.EpisodeImage to "episode-1",
            ),
            cacheStore.putKeys.map { it.type to it.ownerId },
        )
    }

    @Test
    fun cacheCleanerUsesConfiguredSizeLimit() = runBlocking {
        val cacheStore = FakeMediaCacheStore()
        val cleaner = DefaultCacheCleaner(cacheStore) { 250L * 1024L * 1024L }

        cleaner.cleanup()

        assertEquals(listOf(250L * 1024L * 1024L), cacheStore.cleanupLimits)
    }

    @Test
    fun refreshRunGuardRejectsDuplicateKeyUntilExit() {
        val guard = RefreshRunGuard()

        assertTrue(guard.tryEnter("provider-1"))
        assertFalse(guard.tryEnter("provider-1"))
        guard.exit("provider-1")
        assertTrue(guard.tryEnter("provider-1"))
    }

    private fun provider(
        id: String = "provider-1",
        type: ProviderType = ProviderType.M3u,
        isActive: Boolean = true,
    ): Provider =
        Provider(
            id = id,
            name = "Provider",
            type = type,
            sourceConfigKey = "provider:$id:credentials",
            isActive = isActive,
            status = if (isActive) ProviderStatus.Active else ProviderStatus.Disabled,
            includeLiveTv = true,
            includeMovies = true,
            includeSeries = true,
            refreshIntervalHours = 12,
            logoPriority = "provider",
            createdAt = 1L,
            updatedAt = 1L,
        )
}

private class FakeProviderRepository(
    providers: List<Provider>,
    private val credentials: Map<String, ProviderCredentials> = emptyMap(),
) : ProviderRepository {
    private val providersById = providers.associateBy { it.id }
    val statuses = mutableListOf<ProviderStatus>()

    override fun observeProviders(): Flow<List<Provider>> = flowOf(providersById.values.toList())

    override suspend fun getProvider(providerId: String): Provider? = providersById[providerId]

    override suspend fun getCredentials(providerId: String): ProviderCredentials? = credentials[providerId]

    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
        error("Not used.")

    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
        error("Not used.")

    override suspend fun saveProvider(provider: Provider) = Unit

    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) {
        statuses += status
    }

    override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit

    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit

    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) = Unit

    override suspend fun deleteProvider(providerId: String) = Unit
}

private class FakeCatalogImportRepository : CatalogImportRepository {
    var m3uProviderId: String? = null
    var m3uPlaylist: M3uPlaylist? = null

    override suspend fun importM3uCatalog(providerId: String, playlist: M3uPlaylist): CatalogImportResult {
        m3uProviderId = providerId
        m3uPlaylist = playlist
        return CatalogImportResult(
            categoriesAdded = 1,
            categoriesUpdated = 0,
            categoriesRemoved = 0,
            channelsAdded = playlist.channels.size,
            channelsUpdated = 0,
            channelsRemoved = 0,
            skippedEntries = playlist.skippedEntries,
        )
    }

    override suspend fun importM3uLiveChannels(providerId: String, playlist: M3uPlaylist): CatalogImportResult =
        importM3uCatalog(providerId, playlist)

    override suspend fun importXtreamCatalog(providerId: String, catalog: XtreamCatalog): XtreamCatalogImportResult =
        XtreamCatalogImportResult(
            liveCategories = ImportCount(0, 0, 0),
            movieCategories = ImportCount(0, 0, 0),
            seriesCategories = ImportCount(0, 0, 0),
            channels = ImportCount(0, 0, 0),
            movies = ImportCount(0, 0, 0),
            series = ImportCount(0, 0, 0),
            seasons = ImportCount(0, 0, 0),
            episodes = ImportCount(0, 0, 0),
        )

    var seriesDetailsProviderId: String? = null
    var seriesDetailsInfos: List<com.vivicast.tv.iptv.xtream.XtreamSeriesInfo>? = null

    override suspend fun importXtreamSeriesDetails(
        providerId: String,
        seriesInfos: List<com.vivicast.tv.iptv.xtream.XtreamSeriesInfo>,
    ): com.vivicast.tv.data.media.XtreamSeriesDetailsImportResult {
        seriesDetailsProviderId = providerId
        seriesDetailsInfos = seriesInfos
        return com.vivicast.tv.data.media.XtreamSeriesDetailsImportResult(
            seasons = ImportCount(0, 0, 0),
            episodes = ImportCount(0, 0, 0),
        )
    }
}

private class StubXtreamParser(private val seriesCount: Int) : com.vivicast.tv.iptv.xtream.XtreamParser {
    override fun parseUserInfo(json: String) =
        com.vivicast.tv.iptv.xtream.XtreamUserInfo(authenticated = true, expiresAtSeconds = null, maxConnections = null)
    override fun parseCategories(json: String) = emptyList<com.vivicast.tv.iptv.xtream.XtreamCategory>()
    override fun parseLiveStreams(json: String) = emptyList<com.vivicast.tv.iptv.xtream.XtreamLiveStream>()
    override fun parseVodItems(json: String) = emptyList<com.vivicast.tv.iptv.xtream.XtreamVodItem>()
    override fun parseSeries(json: String) = (1..seriesCount).map {
        com.vivicast.tv.iptv.xtream.XtreamSeriesItem(
            remoteId = "s$it", name = "Series $it", categoryRemoteId = null, posterUrl = null,
            backdropUrl = null, rating = null, year = null, genre = null, director = null,
            cast = null, plot = null, addedAtSeconds = null,
        )
    }

    override fun parseSeriesInfo(seriesRemoteId: String, json: String) =
        com.vivicast.tv.iptv.xtream.XtreamSeriesInfo(seriesRemoteId = seriesRemoteId, seasons = emptyList(), episodes = emptyList())
}

private class FakeEpgImportRepository : EpgImportRepository {
    val providerIds = mutableListOf<String>()
    val documents = mutableListOf<XmltvDocument>()
    val retentionRequests = mutableListOf<Int>()
    val refreshedMetadata = mutableListOf<RefreshedMetadata>()
    val refreshingFlags = mutableListOf<Pair<String, Boolean>>()

    override suspend fun saveEpgSource(request: EpgSourceSaveRequest): EpgSource =
        error("Not used.")

    override suspend fun linkEpgSourceToProvider(providerId: String, epgSourceId: String, priority: Int) = Unit

    override suspend fun importXmltv(providerId: String, epgSourceId: String, document: XmltvDocument): EpgImportResult {
        providerIds += providerId
        documents += document
        return EpgImportResult(
            programsImported = document.programs.size,
            programsSkipped = document.skippedPrograms,
            mappingsAdded = 0,
            mappingsUpdated = 0,
        )
    }

    override suspend fun cleanupProgramsOutsideRetention(nowMillis: Long, pastDays: Int): Int {
        retentionRequests += pastDays
        return 0
    }

    override suspend fun markEpgSourceRefreshed(
        sourceId: String,
        refreshedAt: Long,
        channelCount: Int,
        programCount: Int,
    ) {
        refreshedMetadata += RefreshedMetadata(sourceId, refreshedAt, channelCount, programCount)
    }

    override suspend fun setEpgSourceRefreshing(sourceId: String, refreshing: Boolean) {
        refreshingFlags += sourceId to refreshing
    }
}

private data class RefreshedMetadata(
    val sourceId: String,
    val refreshedAt: Long,
    val channelCount: Int,
    val programCount: Int,
)

private class FakeTextFetcher(
    private vararg val responses: Pair<String, String>,
) : TextFetcher {
    private val responseMap = responses.toMap()
    val urls = mutableListOf<String>()

    override suspend fun fetch(url: String, userAgent: String?): String {
        urls += url
        return responseMap.getValue(url)
    }
}

private class FakeEpgStreamSource(
    private vararg val responses: Pair<String, String>,
) : EpgStreamSource {
    private val responseMap = responses.toMap()
    val urls = mutableListOf<String>()

    override suspend fun open(url: String, block: (java.io.InputStream) -> Unit) {
        urls += url
        block(java.io.ByteArrayInputStream(responseMap.getValue(url).toByteArray()))
    }
}

private class FakeBinaryFetcher(
    private vararg val responses: Pair<String, ByteArray?>,
) : BinaryFetcher {
    private val responseMap = responses.toMap()
    val urls = mutableListOf<String>()

    override suspend fun fetch(url: String): ByteArray {
        urls += url
        return responseMap[url] ?: error("Failed to fetch logo")
    }
}

private class FakeMediaCacheStore(
    cached: Set<MediaCacheKey> = emptySet(),
) : MediaCacheStore {
    private val cachedKeys = cached.toMutableSet()
    val putKeys = mutableListOf<MediaCacheKey>()
    val cleanupLimits = mutableListOf<Long>()

    override suspend fun hasEntry(key: MediaCacheKey): Boolean =
        key in cachedKeys

    override suspend fun getEntry(key: MediaCacheKey): MediaCacheEntry? =
        if (key in cachedKeys) {
            MediaCacheEntry(
                key = key,
                file = File("unused"),
                sizeBytes = 1L,
                createdAt = 1L,
                lastAccessedAt = 1L,
            )
        } else {
            null
        }

    override suspend fun put(key: MediaCacheKey, bytes: ByteArray): MediaCacheEntry {
        putKeys += key
        cachedKeys += key
        return MediaCacheEntry(
            key = key,
            file = File("unused"),
            sizeBytes = bytes.size.toLong(),
            createdAt = 1L,
            lastAccessedAt = 1L,
        )
    }

    override suspend fun stats(): MediaCacheStats =
        MediaCacheStats(totalSizeBytes = 0L, fileCount = cachedKeys.size)

    override suspend fun cleanup(maxSizeBytes: Long): MediaCacheCleanupResult {
        cleanupLimits += maxSizeBytes
        return MediaCacheCleanupResult(removedFiles = 0, removedBytes = 0L, remainingBytes = 0L)
    }

    override suspend fun clear(): MediaCacheCleanupResult =
        MediaCacheCleanupResult(removedFiles = 0, removedBytes = 0L, remainingBytes = 0L)
}

private class FakeEpgSourceReader(
    private val sourceIds: List<String> = emptyList(),
    private val resolved: ResolvedEpgSource? = null,
) : EpgSourceReader {
    override suspend fun getActiveSourceIdsForProvider(providerId: String): List<String> = sourceIds

    override suspend fun getActiveSource(epgSourceId: String): ResolvedEpgSource? =
        resolved?.takeIf { it.id == epgSourceId }
}

private object EmptyXtreamClient : XtreamClient {
    override suspend fun getLiveCategories(credentials: XtreamCredentials): String = "[]"
    override suspend fun getUserInfo(credentials: XtreamCredentials): String = "{}"
    override suspend fun getLiveStreams(credentials: XtreamCredentials, categoryId: String?): String = "[]"
    override suspend fun getVodCategories(credentials: XtreamCredentials): String = "[]"
    override suspend fun getVodStreams(credentials: XtreamCredentials, categoryId: String?): String = "[]"
    override suspend fun getSeriesCategories(credentials: XtreamCredentials): String = "[]"
    override suspend fun getSeries(credentials: XtreamCredentials, categoryId: String?): String = "[]"
    override suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: String): String = "{}"
}

private object EmptyXtreamParser : XtreamParser {
    override fun parseUserInfo(json: String): XtreamUserInfo =
        XtreamUserInfo(authenticated = true, expiresAtSeconds = null, maxConnections = null)
    override fun parseCategories(json: String): List<XtreamCategory> = emptyList()
    override fun parseLiveStreams(json: String): List<XtreamLiveStream> = emptyList()
    override fun parseVodItems(json: String): List<XtreamVodItem> = emptyList()
    override fun parseSeries(json: String): List<XtreamSeriesItem> = emptyList()
    override fun parseSeriesInfo(seriesRemoteId: String, json: String): XtreamSeriesInfo =
        XtreamSeriesInfo(seriesRemoteId = seriesRemoteId, seasons = emptyList(), episodes = emptyList())
}
