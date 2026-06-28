package com.vivicast.tv.data.media

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.database.VivicastDatabaseCallbacks
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class RoomMediaRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomMediaRepository
    private var now = NOW

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .addCallback(VivicastDatabaseCallbacks.SearchFtsCallback)
            .allowMainThreadQueries()
            .build()
        repository = RoomMediaRepository(database) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observesProviderScopedLiveCategoriesAndChannels() = runBlocking {
        seedCatalog()

        val categories = repository.observeCategories(PROVIDER_ID, CategoryType.LiveTv).first()
        val channels = repository.observeChannels(PROVIDER_ID, LIVE_CATEGORY_ID).first()

        assertEquals(listOf("News"), categories.map { it.name })
        assertEquals(listOf("ARD HD", "Dune TV"), channels.map { it.name })
        assertEquals("https://logos.example/ard.png", channels.first { it.name == "ARD HD" }.logoUrl)
    }

    @Test
    fun returnsWindowedCatalogPagesAndDirectSeriesDetails() = runBlocking {
        seedCatalog()
        seedLargeCatalogSlice(itemCount = 1_000)

        val channels = repository.observeChannelsPage(PROVIDER_ID, LIVE_CATEGORY_ID, limit = 50).first()
        val movies = repository.observeMoviesPage(PROVIDER_ID, categoryId = null, limit = 50).first()
        val series = repository.observeSeriesPage(PROVIDER_ID, categoryId = null, limit = 50).first()
        val emptyChannels = repository.observeChannelsPage(PROVIDER_ID, LIVE_CATEGORY_ID, limit = -1).first()
        val directSeries = repository.getSeries(PROVIDER_ID, "series-bulk-1000")

        assertEquals(50, channels.size)
        assertEquals(50, movies.size)
        assertEquals(50, series.size)
        assertEquals(emptyList<String>(), emptyChannels.map { it.name })
        assertEquals("Paged Series 1000", directSeries?.name)

        val channelPageP95 = p95Millis {
            repository.observeChannelsPage(PROVIDER_ID, LIVE_CATEGORY_ID, limit = 50).first()
        }
        val moviePageP95 = p95Millis {
            repository.observeMoviesPage(PROVIDER_ID, categoryId = null, limit = 50).first()
        }
        val seriesPageP95 = p95Millis {
            repository.observeSeriesPage(PROVIDER_ID, categoryId = null, limit = 50).first()
        }
        val seriesDetailP95 = p95Millis {
            repository.getSeries(PROVIDER_ID, "series-bulk-1000")
        }

        assertTrue("channel first page p95 was ${channelPageP95}ms", channelPageP95 <= FIRST_PAGE_BUDGET_MS)
        assertTrue("movie first page p95 was ${moviePageP95}ms", moviePageP95 <= FIRST_PAGE_BUDGET_MS)
        assertTrue("series first page p95 was ${seriesPageP95}ms", seriesPageP95 <= FIRST_PAGE_BUDGET_MS)
        assertTrue("series detail p95 was ${seriesDetailP95}ms", seriesDetailP95 <= DETAIL_LOOKUP_BUDGET_MS)
    }

    @Test
    fun searchesLocalCatalogAndEpgOnly() = runBlocking {
        seedCatalog()

        val result = repository.search("dune", limitPerType = 5)

        assertEquals(listOf("Dune TV"), result.channels.map { it.name })
        assertEquals(listOf("Dune Movie"), result.movies.map { it.name })
        assertEquals(listOf("Dune Series"), result.series.map { it.name })
        assertEquals(listOf("Dune Special"), result.epgPrograms.map { it.title })

        val shortQueryResult = repository.search("du", limitPerType = 5)
        assertEquals(listOf("Dune TV"), shortQueryResult.channels.map { it.name })
        assertEquals(listOf("Dune Movie"), shortQueryResult.movies.map { it.name })
        assertEquals(listOf("Dune Series"), shortQueryResult.series.map { it.name })
        assertEquals(emptyList<String>(), shortQueryResult.epgPrograms.map { it.title })
    }

    @Test
    fun largeFixtureSearchAndDatabaseBudgetsStayWithinPrd13Targets() = runBlocking {
        seedCatalog()
        seedLargeCatalogSlice(itemCount = 10_000)

        assertEquals(20, repository.search("paged", limitPerType = 20).channels.size)
        assertEquals(20, repository.search("paged", limitPerType = 20).movies.size)
        assertEquals(20, repository.search("paged", limitPerType = 20).series.size)

        val searchP95 = p95Millis {
            repository.search("paged", limitPerType = 20)
        }
        val searchP99 = p99Millis {
            repository.search("paged", limitPerType = 20)
        }
        val channelPageP95 = p95Millis {
            repository.observeChannelsPage(PROVIDER_ID, LIVE_CATEGORY_ID, limit = 50).first()
        }
        val moviePageP95 = p95Millis {
            repository.observeMoviesPage(PROVIDER_ID, categoryId = null, limit = 50).first()
        }
        val seriesPageP95 = p95Millis {
            repository.observeSeriesPage(PROVIDER_ID, categoryId = null, limit = 50).first()
        }
        val movieDetailP95 = p95Millis {
            repository.getMovie(PROVIDER_ID, "movie-bulk-10000")
        }
        val seriesDetailP95 = p95Millis {
            repository.getSeries(PROVIDER_ID, "series-bulk-10000")
        }

        Log.i(
            BENCHMARK_LOG_TAG,
            "largeFixture searchP95=${searchP95}ms searchP99=${searchP99}ms " +
                "channelPageP95=${channelPageP95}ms moviePageP95=${moviePageP95}ms " +
                "seriesPageP95=${seriesPageP95}ms movieDetailP95=${movieDetailP95}ms " +
                "seriesDetailP95=${seriesDetailP95}ms",
        )

        assertTrue("search p95 was ${searchP95}ms", searchP95 <= SEARCH_P95_BUDGET_MS)
        assertTrue("search p99 was ${searchP99}ms", searchP99 <= SEARCH_P99_BUDGET_MS)
        assertTrue("channel first page p95 was ${channelPageP95}ms", channelPageP95 <= FIRST_PAGE_BUDGET_MS)
        assertTrue("movie first page p95 was ${moviePageP95}ms", moviePageP95 <= FIRST_PAGE_BUDGET_MS)
        assertTrue("series first page p95 was ${seriesPageP95}ms", seriesPageP95 <= FIRST_PAGE_BUDGET_MS)
        assertTrue("movie detail p95 was ${movieDetailP95}ms", movieDetailP95 <= DETAIL_LOOKUP_BUDGET_MS)
        assertTrue("series detail p95 was ${seriesDetailP95}ms", seriesDetailP95 <= DETAIL_LOOKUP_BUDGET_MS)
    }

    @Test
    fun storesSearchHistoryInRoom() = runBlocking {
        now = 1_000L
        repository.addSearchHistory("Dune")
        now = 2_000L
        repository.addSearchHistory("ARD")
        now = 3_000L
        repository.addSearchHistory(" dune ")

        assertEquals(listOf("dune", "ARD"), repository.observeSearchHistory(20).first())

        repository.deleteSearchHistory("DUNE")
        assertEquals(listOf("ARD"), repository.observeSearchHistory(20).first())

        repository.clearSearchHistory()
        assertEquals(emptyList<String>(), repository.observeSearchHistory(20).first())
    }

    @Test
    fun rebuildsAndroidTvSearchIndexFromSafeProductiveEntries() = runBlocking {
        seedCatalog()

        database.androidTvSearchDao().rebuildEntries()
        val entries = database.androidTvSearchDao().getEntries()

        assertEquals(listOf("CHANNEL", "CHANNEL", "MOVIE", "SERIES"), entries.map { it.mediaType }.sorted())
        assertEquals(
            listOf(
                "vivicast://channel/provider-stable/channel-ard",
                "vivicast://channel/provider-stable/channel-dune",
                "vivicast://movie/provider-stable/movie-dune",
                "vivicast://series/provider-stable/series-dune",
            ),
            entries.map { it.deepLink }.sorted(),
        )
        assertEquals(false, entries.any { it.title == "Dune Special" })

        database.providerDao().setProviderActive(PROVIDER_ID, false, NOW)
        database.androidTvSearchDao().rebuildEntries()

        assertEquals(emptyList<String>(), database.androidTvSearchDao().getEntries().map { it.title })
    }

    @Test
    fun rebuildsAndroidTvSearchIndexWithProtectionFilters() = runBlocking {
        seedCatalog()
        database.catalogDao().upsertMovies(listOf(adultMovieEntity()))
        database.catalogDao().upsertSeries(listOf(adultSeriesEntity()))

        database.androidTvSearchDao().rebuildEntries()
        assertEquals(
            listOf("ARD HD", "Adult Movie", "Adult Series", "Dune Movie", "Dune Series", "Dune TV"),
            database.androidTvSearchDao().getEntries().map { it.title }.sorted(),
        )

        database.androidTvSearchDao().rebuildEntries(protectAdultContent = true)
        assertEquals(false, database.androidTvSearchDao().getEntries().any { it.title.startsWith("Adult") })

        database.androidTvSearchDao().rebuildEntries(protectMovies = true, protectSeries = true)
        assertEquals(listOf("CHANNEL", "CHANNEL"), database.androidTvSearchDao().getEntries().map { it.mediaType })
    }

    @Test
    fun returnsAndroidTvSuggestionsAndResolvesStableDeepLinks() = runBlocking {
        seedCatalog()
        database.catalogDao().upsertEpisodes(
            listOf(episodeEntity(id = "episode-s1e1", seasonId = "season-1", seasonNumber = 1, episodeNumber = 1)),
        )
        database.androidTvSearchDao().rebuildEntries()

        val suggestions = repository.searchAndroidTvSuggestions("dune", limit = 10)

        assertEquals(listOf("CHANNEL", "MOVIE", "SERIES"), suggestions.map { it.mediaType }.sorted())
        assertEquals(false, suggestions.any { it.title == "Dune Special" })
        assertEquals(
            "vivicast://movie/provider-stable/movie-dune",
            suggestions.first { it.mediaType == "MOVIE" }.deepLink,
        )
        assertEquals(
            "channel-dune",
            repository.getChannelByStableKeys("provider-stable", "channel-dune")?.id,
        )
        assertEquals(
            "movie-dune",
            repository.getMovieByStableKeys("provider-stable", "movie-dune")?.id,
        )
        assertEquals(
            "series-dune",
            repository.getSeriesByStableKeys("provider-stable", "series-dune")?.id,
        )
        assertEquals(
            "episode-s1e1",
            repository.getEpisodeByStableKeys("provider-stable", "episode-s1e1")?.id,
        )

        database.catalogDao().upsertMovies(listOf(adultMovieEntity()))
        database.catalogDao().upsertSeries(listOf(adultSeriesEntity()))

        assertEquals(
            "movie-adult",
            repository.getMovieByStableKeys("provider-stable", "movie-adult")?.id,
        )
        assertEquals(
            "series-adult",
            repository.getSeriesByStableKeys("provider-stable", "series-adult")?.id,
        )

        database.providerDao().setProviderActive(PROVIDER_ID, false, NOW)

        assertEquals(null, repository.getMovieByStableKeys("provider-stable", "movie-dune"))
        assertEquals(null, repository.getEpisodeByStableKeys("provider-stable", "episode-s1e1"))
    }

    @Test
    fun returnsNextEpisodeAcrossSeasons() = runBlocking {
        seedCatalog()
        database.catalogDao().upsertEpisodes(
            listOf(
                episodeEntity(id = "episode-s1e2", seasonId = "season-1", seasonNumber = 1, episodeNumber = 2),
                episodeEntity(id = "episode-s2e1", seasonId = "season-2", seasonNumber = 2, episodeNumber = 1),
                episodeEntity(id = "episode-s2e2", seasonId = "season-2", seasonNumber = 2, episodeNumber = 2),
            ),
        )

        val nextEpisode = repository.getNextEpisode(episode(id = "episode-s1e1", seasonNumber = 1, episodeNumber = 1))
        val nextSeasonEpisode = repository.getNextEpisode(episode(id = "episode-s1e2", seasonNumber = 1, episodeNumber = 2))

        assertEquals("episode-s1e2", nextEpisode?.id)
        assertEquals("episode-s2e1", nextSeasonEpisode?.id)
    }

    private suspend fun seedCatalog() {
        database.providerDao().upsertProvider(
            ProviderEntity(
                id = PROVIDER_ID,
                stableKey = "provider-stable",
                name = "Provider 1",
                type = "M3U",
                sourceConfigKey = "provider:1:credentials",
                isActive = true,
                status = "ACTIVE",
                includeLiveTv = true,
                includeMovies = true,
                includeSeries = true,
                refreshIntervalHours = 24,
                logoPriority = "provider",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.catalogDao().upsertCategories(
            listOf(
                CategoryEntity(
                    id = LIVE_CATEGORY_ID,
                    providerId = PROVIDER_ID,
                    type = "LIVE",
                    remoteId = "news",
                    name = "News",
                    sortOrder = 1,
                    isHidden = false,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            ),
        )
        database.catalogDao().upsertChannels(
            listOf(
                ChannelEntity(
                    id = "channel-ard",
                    providerId = PROVIDER_ID,
                    categoryId = LIVE_CATEGORY_ID,
                    remoteId = "ard.de",
                    channelNumber = "1",
                    name = "ARD HD",
                    logoUrl = "https://logos.example/ard.png",
                    isCatchupAvailable = true,
                    catchupDays = 7,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
                ChannelEntity(
                    id = "channel-dune",
                    providerId = PROVIDER_ID,
                    categoryId = LIVE_CATEGORY_ID,
                    stableKey = "channel-dune",
                    remoteId = "dune.tv",
                    channelNumber = "2",
                    name = "Dune TV",
                    logoUrl = null,
                    isCatchupAvailable = false,
                    catchupDays = 0,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            ),
        )
        database.catalogDao().upsertMovies(
            listOf(
                MovieEntity(
                    id = "movie-dune",
                    providerId = PROVIDER_ID,
                    categoryId = null,
                    stableKey = "movie-dune",
                    remoteId = "movie-1",
                    name = "Dune Movie",
                    originalName = null,
                    containerExtension = "mp4",
                    posterUrl = null,
                    backdropUrl = null,
                    rating = "8.0",
                    year = "2024",
                    genre = "Sci-Fi",
                    duration = 7_200L,
                    director = null,
                    cast = null,
                    plot = null,
                    trailerUrl = null,
                    addedAt = null,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            ),
        )
        database.catalogDao().upsertSeries(
            listOf(
                SeriesEntity(
                    id = "series-dune",
                    providerId = PROVIDER_ID,
                    categoryId = null,
                    stableKey = "series-dune",
                    remoteId = "series-1",
                    name = "Dune Series",
                    originalName = null,
                    posterUrl = null,
                    backdropUrl = null,
                    rating = "7.5",
                    year = "2026",
                    genre = "Sci-Fi",
                    director = null,
                    cast = null,
                    plot = null,
                    addedAt = null,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            ),
        )
        database.epgDao().upsertPrograms(
            listOf(
                EpgProgramEntity(
                    id = "program-dune",
                    providerId = PROVIDER_ID,
                    channelId = "channel-dune",
                    epgSourceId = "epg-1",
                    epgChannelId = "dune.tv",
                    title = "Dune Special",
                    subtitle = null,
                    description = "Local EPG match",
                    startTime = NOW,
                    endTime = NOW + 3_600_000L,
                    category = "Movie",
                    iconUrl = null,
                    isCatchupAvailable = false,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            ),
        )
    }

    private suspend fun seedLargeCatalogSlice(itemCount: Int) {
        database.catalogDao().upsertChannels(
            (1..itemCount).map { index ->
                val suffix = index.toString().padStart(4, '0')
                ChannelEntity(
                    id = "channel-bulk-$suffix",
                    providerId = PROVIDER_ID,
                    categoryId = LIVE_CATEGORY_ID,
                    stableKey = "channel-bulk-$suffix",
                    remoteId = "channel-bulk-$suffix",
                    channelNumber = (index + 10).toString().padStart(4, '0'),
                    name = "Paged Channel $suffix",
                    logoUrl = null,
                    isCatchupAvailable = false,
                    catchupDays = 0,
                    createdAt = NOW,
                    updatedAt = NOW,
                )
            },
        )
        database.catalogDao().upsertMovies(
            (1..itemCount).map { index ->
                val suffix = index.toString().padStart(4, '0')
                MovieEntity(
                    id = "movie-bulk-$suffix",
                    providerId = PROVIDER_ID,
                    categoryId = null,
                    stableKey = "movie-bulk-$suffix",
                    remoteId = "movie-bulk-$suffix",
                    name = "Paged Movie $suffix",
                    originalName = null,
                    containerExtension = "mp4",
                    posterUrl = null,
                    backdropUrl = null,
                    rating = null,
                    year = null,
                    genre = null,
                    duration = null,
                    director = null,
                    cast = null,
                    plot = null,
                    trailerUrl = null,
                    addedAt = null,
                    createdAt = NOW,
                    updatedAt = NOW,
                )
            },
        )
        database.catalogDao().upsertSeries(
            (1..itemCount).map { index ->
                val suffix = index.toString().padStart(4, '0')
                SeriesEntity(
                    id = "series-bulk-$suffix",
                    providerId = PROVIDER_ID,
                    categoryId = null,
                    stableKey = "series-bulk-$suffix",
                    remoteId = "series-bulk-$suffix",
                    name = "Paged Series $suffix",
                    originalName = null,
                    posterUrl = null,
                    backdropUrl = null,
                    rating = null,
                    year = null,
                    genre = null,
                    director = null,
                    cast = null,
                    plot = null,
                    addedAt = null,
                    createdAt = NOW,
                    updatedAt = NOW,
                )
            },
        )
    }

    private suspend fun p95Millis(
        sampleCount: Int = 20,
        block: suspend () -> Unit,
    ): Long = percentileMillis(percentile = 95, sampleCount = sampleCount, block = block)

    private suspend fun p99Millis(
        sampleCount: Int = 20,
        block: suspend () -> Unit,
    ): Long = percentileMillis(percentile = 99, sampleCount = sampleCount, block = block)

    private suspend fun percentileMillis(
        percentile: Int,
        sampleCount: Int,
        block: suspend () -> Unit,
    ): Long {
        val samples = (1..sampleCount).map {
            measureTimeMillis {
                block()
            }
        }.sorted()
        val index = ((samples.size * percentile + 99) / 100 - 1).coerceIn(samples.indices)
        return samples[index]
    }

    private fun episodeEntity(
        id: String,
        seasonId: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ): EpisodeEntity =
        EpisodeEntity(
            id = id,
            providerId = PROVIDER_ID,
            seriesId = "series-dune",
            seasonId = seasonId,
            stableKey = id,
            remoteId = id,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            name = "Episode $seasonNumber.$episodeNumber",
            plot = null,
            thumbnailUrl = null,
            containerExtension = "mp4",
            duration = null,
            airDate = null,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun adultMovieEntity(): MovieEntity =
        MovieEntity(
            id = "movie-adult",
            providerId = PROVIDER_ID,
            categoryId = null,
            stableKey = "movie-adult",
            remoteId = "movie-adult",
            name = "Adult Movie",
            originalName = null,
            containerExtension = "mp4",
            posterUrl = null,
            backdropUrl = null,
            rating = null,
            year = null,
            genre = null,
            duration = null,
            director = null,
            cast = null,
            plot = null,
            trailerUrl = null,
            addedAt = null,
            isAdult = true,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun adultSeriesEntity(): SeriesEntity =
        SeriesEntity(
            id = "series-adult",
            providerId = PROVIDER_ID,
            categoryId = null,
            stableKey = "series-adult",
            remoteId = "series-adult",
            name = "Adult Series",
            originalName = null,
            posterUrl = null,
            backdropUrl = null,
            rating = null,
            year = null,
            genre = null,
            director = null,
            cast = null,
            plot = null,
            addedAt = null,
            isAdult = true,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun episode(
        id: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ): Episode =
        Episode(
            id = id,
            providerId = PROVIDER_ID,
            seriesId = "series-dune",
            seasonId = "season-$seasonNumber",
            remoteId = id,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            name = "Episode $seasonNumber.$episodeNumber",
            plot = null,
            thumbnailUrl = null,
            containerExtension = "mp4",
            duration = null,
            airDate = null,
        )

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val LIVE_CATEGORY_ID = "provider-1:live:news"
        const val NOW = 1_000L
        const val FIRST_PAGE_BUDGET_MS = 300L
        const val DETAIL_LOOKUP_BUDGET_MS = 150L
        const val SEARCH_P95_BUDGET_MS = 500L
        const val SEARCH_P99_BUDGET_MS = 1_000L
        const val BENCHMARK_LOG_TAG = "VivicastBenchmark"
    }
}
