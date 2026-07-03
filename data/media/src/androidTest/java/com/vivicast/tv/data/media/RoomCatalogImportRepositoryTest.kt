package com.vivicast.tv.data.media

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.cache.M3uStreamReference
import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.iptv.m3u.DefaultM3uParser
import com.vivicast.tv.iptv.xtream.XtreamCategory
import com.vivicast.tv.iptv.xtream.XtreamEpisode
import com.vivicast.tv.iptv.xtream.XtreamLiveStream
import com.vivicast.tv.iptv.xtream.XtreamSeason
import com.vivicast.tv.iptv.xtream.XtreamSeriesInfo
import com.vivicast.tv.iptv.xtream.XtreamSeriesItem
import com.vivicast.tv.iptv.xtream.XtreamVodItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class RoomCatalogImportRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomCatalogImportRepository
    private lateinit var streamReferenceStore: FakeM3uStreamReferenceStore
    private val parser = DefaultM3uParser()
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        streamReferenceStore = FakeM3uStreamReferenceStore()
        repository = RoomCatalogImportRepository(database, streamReferenceStore) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importM3uLiveChannelsAddsCategoriesAndChannels() = kotlinx.coroutines.runBlocking {
        val result = repository.importM3uLiveChannels(PROVIDER_ID, firstPlaylist())

        assertEquals(2, result.categoriesAdded)
        assertEquals(0, result.categoriesRemoved)
        assertEquals(2, result.channelsAdded)
        assertEquals(0, result.channelsRemoved)

        val categories = database.catalogDao().getCategories(PROVIDER_ID, "LIVE")
        val channels = database.catalogDao().getChannels(PROVIDER_ID)

        assertEquals(listOf("News", "Sport"), categories.map { it.name }.sorted())
        assertEquals(listOf("ARD HD", "Sport 1"), channels.map { it.name }.sorted())
        assertEquals("https://logos.example/ard.png", channels.first { it.remoteId == ARD_REMOTE_ID }.logoUrl)
        assertEquals("1", channels.first { it.remoteId == ARD_REMOTE_ID }.channelNumber)
        assertEquals(7, channels.first { it.remoteId == ARD_REMOTE_ID }.catchupDays)
        assertEquals("https://streams.example/ard.m3u8", streamReferenceStore.getStreamUrl(PROVIDER_ID, ARD_REMOTE_ID))
        assertEquals("default", streamReferenceStore.getReference(PROVIDER_ID, ARD_REMOTE_ID)?.catchupMode)
    }

    @Test
    fun secondImportUpdatesChannelsAndDeletesRemovedChannelSideEffects() = kotlinx.coroutines.runBlocking {
        repository.importM3uLiveChannels(PROVIDER_ID, firstPlaylist())
        val sportChannel = database.catalogDao().getChannels(PROVIDER_ID).first { it.remoteId == SPORT_REMOTE_ID }
        seedSideEffects(sportChannel.id)

        now = 2_000L
        val result = repository.importM3uLiveChannels(PROVIDER_ID, secondPlaylist())

        assertEquals(0, result.channelsAdded)
        assertEquals(1, result.channelsUpdated)
        assertEquals(1, result.channelsRemoved)
        assertEquals(1, result.categoriesRemoved)

        val channels = database.catalogDao().getChannels(PROVIDER_ID)
        assertEquals(1, channels.size)
        assertEquals("ARD HD Updated", channels.single().name)
        assertEquals("https://logos.example/ard-new.png", channels.single().logoUrl)
        assertFalse(channels.any { it.id == sportChannel.id })

        assertEquals(0, countForProvider("favorites", PROVIDER_ID))
        assertEquals(0, countForProvider("playback_progress", PROVIDER_ID))
        assertEquals(0, countForProvider("channel_history", PROVIDER_ID))
        assertEquals(0, countForProvider("epg_programs", PROVIDER_ID))
        assertEquals(0, countForProvider("epg_channel_mappings", PROVIDER_ID))
        assertEquals(1, countAll("epg_sources"))
    }

    @Test
    fun largeFixtureM3uParseAndCommitStaysWithinPrd13Budget() = kotlinx.coroutines.runBlocking {
        lateinit var result: CatalogImportResult
        lateinit var playlist: com.vivicast.tv.iptv.m3u.M3uPlaylist
        val content = largeM3uFixture(M3U_CHANNEL_COUNT)

        val parseMs = measureTimeMillis {
            playlist = parser.parse(content)
        }
        val importMs = measureTimeMillis {
            result = repository.importM3uLiveChannels(PROVIDER_ID, playlist)
        }
        val totalMs = parseMs + importMs

        val channelCount = countForProvider("channels", PROVIDER_ID)
        val categoryCount = countForProvider("categories", PROVIDER_ID)
        Log.i(
            BENCHMARK_LOG_TAG,
            "largeFixtureM3u parseMs=${parseMs} importMs=${importMs} totalMs=${totalMs} " +
                "channels=${channelCount} categories=${categoryCount}",
        )

        assertEquals(M3U_CHANNEL_COUNT, playlist.channels.size)
        assertEquals(0, playlist.skippedEntries)
        assertEquals(M3U_CHANNEL_COUNT, result.channelsAdded)
        assertEquals(0, result.channelsRemoved)
        assertEquals(M3U_CATEGORY_COUNT, result.categoriesAdded)
        assertEquals(M3U_CHANNEL_COUNT, channelCount)
        assertEquals(M3U_CATEGORY_COUNT, categoryCount)
        assertTrue(
            "M3U parse plus Room commit should stay within the PRD 13 10,000-channel import budget",
            totalMs <= M3U_IMPORT_BUDGET_MS,
        )
    }

    @Test
    fun importM3uCatalogClassifiesLiveMovieAndSeries() = kotlinx.coroutines.runBlocking {
        repository.importM3uCatalog(PROVIDER_ID, mixedPlaylist())

        val channels = database.catalogDao().getChannels(PROVIDER_ID)
        assertEquals(
            listOf("24/7 VIKINGS", "Das Erste HD", "Sky Cinema Action HD"),
            channels.map { it.name }.sorted(),
        )

        val movies = database.catalogDao().getMovies(PROVIDER_ID)
        assertEquals(listOf("Example Feature One", "Example Feature Two"), movies.map { it.name }.sorted())
        assertEquals("mkv", movies.first { it.name == "Example Feature One" }.containerExtension)

        val series = database.catalogDao().getSeries(PROVIDER_ID)
        val seasons = database.catalogDao().getSeasons(PROVIDER_ID)
        val episodes = database.catalogDao().getEpisodes(PROVIDER_ID)
        assertEquals(listOf("Example Show"), series.map { it.name })
        assertEquals(listOf(1), seasons.map { it.seasonNumber })
        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber }.sorted())

        // Categories are created with the correct type per bucket.
        assertTrue(database.catalogDao().getCategories(PROVIDER_ID, "LIVE").isNotEmpty())
        assertTrue(database.catalogDao().getCategories(PROVIDER_ID, "MOVIE").isNotEmpty())
        assertTrue(database.catalogDao().getCategories(PROVIDER_ID, "SERIES").isNotEmpty())

        // Stream references exist for channels, movies AND episodes, keyed by the entity remoteId.
        channels.forEach { assertNotNull(streamReferenceStore.getStreamUrl(PROVIDER_ID, it.remoteId)) }
        movies.forEach {
            assertTrue(it.remoteId.startsWith("movie:"))
            assertNotNull(streamReferenceStore.getStreamUrl(PROVIDER_ID, it.remoteId))
        }
        episodes.forEach {
            assertTrue(it.remoteId.startsWith("episode:"))
            assertNotNull(streamReferenceStore.getStreamUrl(PROVIDER_ID, it.remoteId))
        }

        // No raw stream URLs / tokens leak into ids or stableKeys.
        (movies.map { it.remoteId } + movies.map { it.stableKey } + episodes.map { it.remoteId }).forEach { key ->
            assertFalse(key.contains("example.test"))
            assertFalse(key.contains("token"))
        }
    }

    @Test
    fun secondM3uCatalogImportIsIdempotent() = kotlinx.coroutines.runBlocking {
        repository.importM3uCatalog(PROVIDER_ID, mixedPlaylist())
        now = 2_000L
        repository.importM3uCatalog(PROVIDER_ID, mixedPlaylist())

        assertEquals(3, database.catalogDao().getChannels(PROVIDER_ID).size)
        assertEquals(2, database.catalogDao().getMovies(PROVIDER_ID).size)
        assertEquals(1, database.catalogDao().getSeries(PROVIDER_ID).size)
        assertEquals(1, database.catalogDao().getSeasons(PROVIDER_ID).size)
        assertEquals(2, database.catalogDao().getEpisodes(PROVIDER_ID).size)
    }

    @Test
    fun importXtreamCatalogAddsLiveVodSeriesSeasonsAndEpisodes() = kotlinx.coroutines.runBlocking {
        val result = repository.importXtreamCatalog(PROVIDER_ID, firstXtreamCatalog())

        assertEquals(2, result.liveCategories.added)
        assertEquals(2, result.movieCategories.added)
        assertEquals(2, result.seriesCategories.added)
        assertEquals(2, result.channels.added)
        assertEquals(2, result.movies.added)
        assertEquals(2, result.series.added)
        assertEquals(3, result.seasons.added)
        assertEquals(3, result.episodes.added)

        assertEquals(listOf("News", "Sport"), database.catalogDao().getCategories(PROVIDER_ID, "LIVE").map { it.name }.sorted())
        assertEquals(listOf("Action", "Drama"), database.catalogDao().getCategories(PROVIDER_ID, "MOVIE").map { it.name }.sorted())
        assertEquals(listOf("Crime", "Drama"), database.catalogDao().getCategories(PROVIDER_ID, "SERIES").map { it.name }.sorted())

        val movies = database.catalogDao().getMovies(PROVIDER_ID)
        assertEquals(listOf("Movie One", "Movie Two"), movies.map { it.name }.sorted())
        assertEquals("https://posters.example/movie-1.jpg", movies.first { it.remoteId == "movie-1" }.posterUrl)
        assertEquals("mp4", movies.first { it.remoteId == "movie-1" }.containerExtension)

        val series = database.catalogDao().getSeries(PROVIDER_ID)
        val seasons = database.catalogDao().getSeasons(PROVIDER_ID)
        val episodes = database.catalogDao().getEpisodes(PROVIDER_ID)
        assertEquals(listOf("Series One", "Series Two"), series.map { it.name }.sorted())
        assertEquals(listOf(1, 1, 2), seasons.map { it.seasonNumber }.sorted())
        assertEquals(listOf("Episode One", "Episode Two", "Pilot"), episodes.map { it.name }.sorted())
    }

    @Test
    fun fastXtreamImportPreservesSeasonsAndEpisodesThenSeriesDetailsRepopulates() = kotlinx.coroutines.runBlocking {
        // Full catalog import populates seasons/episodes.
        repository.importXtreamCatalog(PROVIDER_ID, firstXtreamCatalog())
        val seasonsBefore = database.catalogDao().getSeasons(PROVIDER_ID).size
        val episodesBefore = database.catalogDao().getEpisodes(PROVIDER_ID).size
        assertTrue(seasonsBefore > 0)
        assertTrue(episodesBefore > 0)

        // Fast main refresh: same catalog but seriesInfos empty -> seasons/episodes MUST be preserved (no wipe).
        now = 2_000L
        repository.importXtreamCatalog(PROVIDER_ID, firstXtreamCatalog().copy(seriesInfos = emptyList()))
        assertEquals(seasonsBefore, database.catalogDao().getSeasons(PROVIDER_ID).size)
        assertEquals(episodesBefore, database.catalogDao().getEpisodes(PROVIDER_ID).size)

        // Background series-details import reconciles seasons/episodes from the (full) seriesInfos.
        repository.importXtreamSeriesDetails(PROVIDER_ID, firstXtreamCatalog().seriesInfos)
        assertEquals(seasonsBefore, database.catalogDao().getSeasons(PROVIDER_ID).size)
        assertEquals(episodesBefore, database.catalogDao().getEpisodes(PROVIDER_ID).size)
    }

    @Test
    fun largeFixtureXtreamMetadataCommitStaysWithinPrd13Budget() = kotlinx.coroutines.runBlocking {
        lateinit var result: XtreamCatalogImportResult
        val catalog = largeXtreamCatalog()

        val importMs = measureTimeMillis {
            result = repository.importXtreamCatalog(PROVIDER_ID, catalog)
        }

        val channelCount = countForProvider("channels", PROVIDER_ID)
        val movieCount = countForProvider("movies", PROVIDER_ID)
        val seriesCount = countForProvider("series", PROVIDER_ID)
        val categoryCount = countForProvider("categories", PROVIDER_ID)
        Log.i(
            BENCHMARK_LOG_TAG,
            "largeFixtureXtream importMs=${importMs} live=${channelCount} movies=${movieCount} " +
                "series=${seriesCount} categories=${categoryCount}",
        )

        assertEquals(XTREAM_LIVE_COUNT, result.channels.added)
        assertEquals(XTREAM_MOVIE_COUNT, result.movies.added)
        assertEquals(XTREAM_SERIES_COUNT, result.series.added)
        assertEquals(XTREAM_LIVE_CATEGORY_COUNT, result.liveCategories.added)
        assertEquals(XTREAM_MOVIE_CATEGORY_COUNT, result.movieCategories.added)
        assertEquals(XTREAM_SERIES_CATEGORY_COUNT, result.seriesCategories.added)
        assertEquals(XTREAM_LIVE_COUNT, channelCount)
        assertEquals(XTREAM_MOVIE_COUNT, movieCount)
        assertEquals(XTREAM_SERIES_COUNT, seriesCount)
        assertEquals(
            XTREAM_LIVE_CATEGORY_COUNT + XTREAM_MOVIE_CATEGORY_COUNT + XTREAM_SERIES_CATEGORY_COUNT,
            categoryCount,
        )
        assertTrue(
            "Xtream metadata Room commit should stay within the PRD 13 15-minute import budget",
            importMs <= XTREAM_IMPORT_BUDGET_MS,
        )
    }

    @Test
    fun secondXtreamImportUpdatesAndDeletesRemovedVodSeriesSideEffects() = kotlinx.coroutines.runBlocking {
        repository.importXtreamCatalog(PROVIDER_ID, firstXtreamCatalog())
        val removedMovie = database.catalogDao().getMovies(PROVIDER_ID).first { it.remoteId == "movie-2" }
        val removedSeries = database.catalogDao().getSeries(PROVIDER_ID).first { it.remoteId == "series-2" }
        val removedEpisode = database.catalogDao().getEpisodes(PROVIDER_ID).first { it.remoteId == "episode-2" }
        seedFavorite("favorite-movie", "MOVIE", removedMovie.id)
        seedFavorite("favorite-series", "SERIES", removedSeries.id)
        seedProgress("progress-movie", "MOVIE", removedMovie.id)
        seedProgress("progress-series", "SERIES", removedSeries.id)
        seedProgress("progress-episode", "EPISODE", removedEpisode.id)

        now = 2_000L
        val result = repository.importXtreamCatalog(PROVIDER_ID, secondXtreamCatalog())

        assertEquals(1, result.channels.updated)
        assertEquals(1, result.movies.updated)
        assertEquals(1, result.movies.removed)
        assertEquals(1, result.series.updated)
        assertEquals(1, result.series.removed)
        assertEquals(2, result.episodes.removed)

        val channels = database.catalogDao().getChannels(PROVIDER_ID)
        val movies = database.catalogDao().getMovies(PROVIDER_ID)
        val series = database.catalogDao().getSeries(PROVIDER_ID)
        val episodes = database.catalogDao().getEpisodes(PROVIDER_ID)
        assertEquals("ARD HD Updated", channels.first { it.remoteId == "live-1" }.name)
        assertEquals(listOf("Movie One Updated"), movies.map { it.name })
        assertEquals(listOf("Series One Updated"), series.map { it.name })
        assertEquals(listOf("Episode One Updated"), episodes.map { it.name })

        assertEquals(0, countForProvider("favorites", PROVIDER_ID))
        assertEquals(0, countForProvider("playback_progress", PROVIDER_ID))
    }

    private fun firstPlaylist() = parser.parse(
        """
        #EXTM3U
        #EXTINF:-1 tvg-id="ard.de" tvg-name="ARD HD" tvg-logo="https://logos.example/ard.png" group-title="News" tvg-chno="1" catchup="default" catchup-days="7" catchup-source="https://archive.example/ard?start={start}",ARD
        https://streams.example/ard.m3u8
        #EXTINF:-1 tvg-id="sport1.de" tvg-name="Sport 1" group-title="Sport",Sport 1
        https://streams.example/sport1.m3u8
        """.trimIndent(),
    )

    // Sanitized mixed playlist: 3 live (.ts incl. 24/7 + Cinema), 2 movies (.mkv w/ /movie/, .mp4 w/ query),
    // 2 episodes of the same series/season. example.test host only, dummy user/pass path segments.
    private fun mixedPlaylist() = parser.parse(
        """
        #EXTM3U
        #EXTINF:-1 tvg-name="Das Erste HD" group-title="News",Das Erste HD
        http://example.test/live/user/pass/100.ts
        #EXTINF:-1 tvg-name="24/7 VIKINGS" group-title="24/7",24/7 VIKINGS
        http://example.test/live/user/pass/900.ts
        #EXTINF:-1 tvg-name="Sky Cinema Action HD" group-title="Cinema",Sky Cinema Action HD
        http://example.test/live/user/pass/300.ts
        #EXTINF:-1 tvg-name="Example Feature One" group-title="Filme",Example Feature One
        http://example.test/movie/user/pass/4242.mkv
        #EXTINF:-1 tvg-name="Example Feature Two" group-title="Filme",Example Feature Two
        http://example.test/vod/9000.mp4?token=abc
        #EXTINF:-1 tvg-name="Example Show S01 E01" group-title="Serien",Example Show S01 E01
        http://example.test/series/user/pass/s01e01.mkv
        #EXTINF:-1 tvg-name="Example Show S01 E02" group-title="Serien",Example Show S01 E02
        http://example.test/series/user/pass/s01e02.mkv
        """.trimIndent(),
    )

    private fun secondPlaylist() = parser.parse(
        """
        #EXTM3U
        #EXTINF:-1 tvg-id="ard.de" tvg-name="ARD HD Updated" tvg-logo="https://logos.example/ard-new.png" group-title="News" tvg-chno="1",ARD
        https://streams.example/ard.m3u8
        """.trimIndent(),
    )

    private fun largeM3uFixture(channelCount: Int): String = buildString {
        appendLine("#EXTM3U")
        repeat(channelCount) { index ->
            val channelNumber = index + 1
            val suffix = channelNumber.toString().padStart(5, '0')
            val group = "Gruppe ${(index % M3U_CATEGORY_COUNT) + 1}"
            appendLine(
                "#EXTINF:-1 tvg-id=\"channel-$suffix\" tvg-name=\"Kanal $suffix\" " +
                    "tvg-logo=\"https://logos.example/$suffix.png\" group-title=\"$group\" " +
                    "tvg-chno=\"$channelNumber\",Kanal $suffix",
            )
            appendLine("https://streams.example/$suffix.m3u8")
        }
    }

    private fun firstXtreamCatalog() = XtreamCatalog(
        liveCategories = listOf(
            XtreamCategory(remoteId = "live-news", name = "News"),
            XtreamCategory(remoteId = "live-sport", name = "Sport"),
        ),
        liveStreams = listOf(
            XtreamLiveStream(
                remoteId = "live-1",
                name = "ARD HD",
                categoryRemoteId = "live-news",
                channelNumber = "1",
                logoUrl = "https://logos.example/ard.png",
                epgChannelId = "ard.de",
                isCatchupAvailable = true,
                catchupDays = 7,
            ),
            XtreamLiveStream(
                remoteId = "live-2",
                name = "Sport 1",
                categoryRemoteId = "live-sport",
                channelNumber = "2",
                logoUrl = null,
                epgChannelId = "sport1.de",
                isCatchupAvailable = false,
                catchupDays = 0,
            ),
        ),
        vodCategories = listOf(
            XtreamCategory(remoteId = "vod-action", name = "Action"),
            XtreamCategory(remoteId = "vod-drama", name = "Drama"),
        ),
        vodItems = listOf(
            XtreamVodItem(
                remoteId = "movie-1",
                name = "Movie One",
                categoryRemoteId = "vod-action",
                containerExtension = "mp4",
                posterUrl = "https://posters.example/movie-1.jpg",
                backdropUrl = "https://backdrops.example/movie-1.jpg",
                rating = "7.1",
                year = "2024",
                genre = "Action",
                durationSeconds = 5_400L,
                director = "Director",
                cast = "Cast",
                plot = "Plot",
                trailerUrl = null,
                addedAtSeconds = 1_700_000_000L,
            ),
            XtreamVodItem(
                remoteId = "movie-2",
                name = "Movie Two",
                categoryRemoteId = "vod-drama",
                containerExtension = "mkv",
                posterUrl = null,
                backdropUrl = null,
                rating = null,
                year = null,
                genre = "Drama",
                durationSeconds = null,
                director = null,
                cast = null,
                plot = null,
                trailerUrl = null,
                addedAtSeconds = null,
            ),
        ),
        seriesCategories = listOf(
            XtreamCategory(remoteId = "series-crime", name = "Crime"),
            XtreamCategory(remoteId = "series-drama", name = "Drama"),
        ),
        seriesItems = listOf(
            XtreamSeriesItem(
                remoteId = "series-1",
                name = "Series One",
                categoryRemoteId = "series-crime",
                posterUrl = "https://posters.example/series-1.jpg",
                backdropUrl = null,
                rating = "8.2",
                year = "2024",
                genre = "Crime",
                director = null,
                cast = null,
                plot = "Series plot",
                addedAtSeconds = 1_700_000_001L,
            ),
            XtreamSeriesItem(
                remoteId = "series-2",
                name = "Series Two",
                categoryRemoteId = "series-drama",
                posterUrl = null,
                backdropUrl = null,
                rating = null,
                year = null,
                genre = "Drama",
                director = null,
                cast = null,
                plot = null,
                addedAtSeconds = null,
            ),
        ),
        seriesInfos = listOf(
            XtreamSeriesInfo(
                seriesRemoteId = "series-1",
                seasons = listOf(
                    XtreamSeason(seasonNumber = 1, name = "Season 1", posterUrl = "https://posters.example/season-1.jpg"),
                    XtreamSeason(seasonNumber = 2, name = "Season 2", posterUrl = null),
                ),
                episodes = listOf(
                    XtreamEpisode(
                        remoteId = "episode-1",
                        episodeNumber = 1,
                        seasonNumber = 1,
                        name = "Episode One",
                        plot = "Episode plot",
                        thumbnailUrl = "https://episodes.example/episode-1.jpg",
                        containerExtension = "mkv",
                        durationSeconds = 2_700L,
                        airDate = "2024-01-01",
                    ),
                    XtreamEpisode(
                        remoteId = "episode-2",
                        episodeNumber = 1,
                        seasonNumber = 2,
                        name = "Episode Two",
                        plot = null,
                        thumbnailUrl = null,
                        containerExtension = "mkv",
                        durationSeconds = null,
                        airDate = null,
                    ),
                ),
            ),
            XtreamSeriesInfo(
                seriesRemoteId = "series-2",
                seasons = emptyList(),
                episodes = listOf(
                    XtreamEpisode(
                        remoteId = "episode-3",
                        episodeNumber = 1,
                        seasonNumber = 1,
                        name = "Pilot",
                        plot = null,
                        thumbnailUrl = null,
                        containerExtension = "mp4",
                        durationSeconds = 1_800L,
                        airDate = null,
                    ),
                ),
            ),
        ),
    )

    private fun secondXtreamCatalog() = firstXtreamCatalog().copy(
        liveStreams = firstXtreamCatalog().liveStreams.take(1).map { it.copy(name = "ARD HD Updated") },
        vodCategories = listOf(XtreamCategory(remoteId = "vod-action", name = "Action")),
        vodItems = listOf(firstXtreamCatalog().vodItems.first().copy(name = "Movie One Updated")),
        seriesCategories = listOf(XtreamCategory(remoteId = "series-crime", name = "Crime")),
        seriesItems = listOf(firstXtreamCatalog().seriesItems.first().copy(name = "Series One Updated")),
        seriesInfos = listOf(
            firstXtreamCatalog().seriesInfos.first().copy(
                seasons = listOf(XtreamSeason(seasonNumber = 1, name = "Season 1", posterUrl = "https://posters.example/season-1.jpg")),
        episodes = listOf(firstXtreamCatalog().seriesInfos.first().episodes.first().copy(name = "Episode One Updated")),
            ),
        ),
    )

    private fun largeXtreamCatalog() = XtreamCatalog(
        liveCategories = largeCategories("live-cat", "Live", XTREAM_LIVE_CATEGORY_COUNT),
        liveStreams = List(XTREAM_LIVE_COUNT) { index ->
            val number = index + 1
            val suffix = number.toString().padStart(5, '0')
            XtreamLiveStream(
                remoteId = "live-$suffix",
                name = "Live $suffix",
                categoryRemoteId = "live-cat-${index % XTREAM_LIVE_CATEGORY_COUNT}",
                channelNumber = number.toString(),
                logoUrl = null,
                epgChannelId = "channel-$suffix",
                isCatchupAvailable = false,
                catchupDays = 0,
            )
        },
        vodCategories = largeCategories("movie-cat", "Filme", XTREAM_MOVIE_CATEGORY_COUNT),
        vodItems = List(XTREAM_MOVIE_COUNT) { index ->
            val number = index + 1
            val suffix = number.toString().padStart(5, '0')
            XtreamVodItem(
                remoteId = "movie-$suffix",
                name = "Film $suffix",
                categoryRemoteId = "movie-cat-${index % XTREAM_MOVIE_CATEGORY_COUNT}",
                containerExtension = "mp4",
                posterUrl = null,
                backdropUrl = null,
                rating = null,
                year = null,
                genre = null,
                durationSeconds = null,
                director = null,
                cast = null,
                plot = null,
                trailerUrl = null,
                addedAtSeconds = null,
            )
        },
        seriesCategories = largeCategories("series-cat", "Serien", XTREAM_SERIES_CATEGORY_COUNT),
        seriesItems = List(XTREAM_SERIES_COUNT) { index ->
            val number = index + 1
            val suffix = number.toString().padStart(5, '0')
            XtreamSeriesItem(
                remoteId = "series-$suffix",
                name = "Serie $suffix",
                categoryRemoteId = "series-cat-${index % XTREAM_SERIES_CATEGORY_COUNT}",
                posterUrl = null,
                backdropUrl = null,
                rating = null,
                year = null,
                genre = null,
                director = null,
                cast = null,
                plot = null,
                addedAtSeconds = null,
            )
        },
    )

    private fun largeCategories(prefix: String, label: String, count: Int): List<XtreamCategory> =
        List(count) { index -> XtreamCategory(remoteId = "$prefix-$index", name = "$label ${index + 1}") }

    private suspend fun seedSideEffects(channelId: String) {
        assertNotNull(database.catalogDao().getChannels(PROVIDER_ID).firstOrNull { it.id == channelId })
        database.epgDao().upsertEpgSources(
            listOf(
                EpgSourceEntity(
                    id = "epg-source-1",
                    name = "Public EPG",
                    sourceConfigKey = "secure:public-epg",
                    timeShiftMinutes = 0,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.epgDao().upsertPrograms(
            listOf(
                EpgProgramEntity(
                    id = "program-1",
                    providerId = PROVIDER_ID,
                    channelId = channelId,
                    epgSourceId = "epg-source-1",
                    epgChannelId = "sport1.de",
                    title = "Sport News",
                    subtitle = null,
                    description = null,
                    startTime = now,
                    endTime = now + 3_600_000L,
                    category = "Sport",
                    iconUrl = null,
                    isCatchupAvailable = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.epgDao().upsertMappings(
            listOf(
                EpgChannelMappingEntity(
                    id = "mapping-1",
                    providerId = PROVIDER_ID,
                    channelId = channelId,
                    epgSourceId = "epg-source-1",
                    epgChannelId = "sport1.de",
                    isManual = false,
                    createdAt = now,
                ),
            ),
        )
        database.favoritesDao().upsertFavorite(
            FavoriteEntity(
                id = "favorite-1",
                providerId = PROVIDER_ID,
                mediaType = "CHANNEL",
                mediaId = channelId,
                sortOrder = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.playbackDao().upsertProgress(
            PlaybackProgressEntity(
                id = "progress-1",
                providerId = PROVIDER_ID,
                mediaType = "CHANNEL",
                mediaId = channelId,
                positionMillis = 0L,
                durationMillis = 0L,
                progressPercent = 0,
                isCompleted = false,
                lastWatchedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.playbackDao().upsertChannelHistory(
            ChannelHistoryEntity(
                id = "history-1",
                providerId = PROVIDER_ID,
                channelId = channelId,
                watchedAt = now,
                durationWatchedMillis = 30_000L,
                updatedAt = now,
            ),
        )
    }

    private suspend fun seedFavorite(id: String, mediaType: String, mediaId: String) {
        database.favoritesDao().upsertFavorite(
            FavoriteEntity(
                id = id,
                providerId = PROVIDER_ID,
                mediaType = mediaType,
                mediaId = mediaId,
                sortOrder = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun seedProgress(id: String, mediaType: String, mediaId: String) {
        database.playbackDao().upsertProgress(
            PlaybackProgressEntity(
                id = id,
                providerId = PROVIDER_ID,
                mediaType = mediaType,
                mediaId = mediaId,
                positionMillis = 1_000L,
                durationMillis = 10_000L,
                progressPercent = 10,
                isCompleted = false,
                lastWatchedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun countForProvider(table: String, providerId: String): Int =
        database.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $table WHERE providerId = ?", arrayOf<Any?>(providerId))).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun countAll(table: String): Int =
        database.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $table")).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val ARD_REMOTE_ID = "channel:tvg-id:ard.de"
        const val SPORT_REMOTE_ID = "channel:tvg-id:sport1.de"
        const val BENCHMARK_LOG_TAG = "VivicastBenchmark"
        const val M3U_CHANNEL_COUNT = 10_000
        const val M3U_CATEGORY_COUNT = 20
        const val M3U_IMPORT_BUDGET_MS = 120_000L
        const val XTREAM_LIVE_COUNT = 10_000
        const val XTREAM_MOVIE_COUNT = 50_000
        const val XTREAM_SERIES_COUNT = 20_000
        const val XTREAM_LIVE_CATEGORY_COUNT = 20
        const val XTREAM_MOVIE_CATEGORY_COUNT = 50
        const val XTREAM_SERIES_CATEGORY_COUNT = 20
        const val XTREAM_IMPORT_BUDGET_MS = 900_000L
    }

    private class FakeM3uStreamReferenceStore : M3uStreamReferenceStore {
        private val references = mutableMapOf<String, MutableMap<String, M3uStreamReference>>()

        override suspend fun replaceProviderReferences(providerId: String, references: Map<String, M3uStreamReference>) {
            this.references[providerId] = references.toMutableMap()
        }

        override suspend fun getReference(providerId: String, remoteId: String): M3uStreamReference? =
            references[providerId]?.get(remoteId)

        override suspend fun deleteProviderReferences(providerId: String) {
            references.remove(providerId)
        }
    }
}
