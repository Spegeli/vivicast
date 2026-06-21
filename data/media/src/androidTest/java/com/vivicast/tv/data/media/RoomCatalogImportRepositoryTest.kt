package com.vivicast.tv.data.media

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogImportRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomCatalogImportRepository
    private val parser = DefaultM3uParser()
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCatalogImportRepository(database) { now }
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
        assertEquals("https://logos.example/ard.png", channels.first { it.remoteId == "ard.de" }.logoUrl)
        assertEquals("1", channels.first { it.remoteId == "ard.de" }.channelNumber)
        assertEquals(7, channels.first { it.remoteId == "ard.de" }.catchupDays)
    }

    @Test
    fun secondImportUpdatesChannelsAndDeletesRemovedChannelSideEffects() = kotlinx.coroutines.runBlocking {
        repository.importM3uLiveChannels(PROVIDER_ID, firstPlaylist())
        val sportChannel = database.catalogDao().getChannels(PROVIDER_ID).first { it.remoteId == "sport1.de" }
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
        #EXTINF:-1 tvg-id="ard.de" tvg-name="ARD HD" tvg-logo="https://logos.example/ard.png" group-title="News" tvg-chno="1" catchup="default" catchup-days="7",ARD
        https://streams.example/ard.m3u8
        #EXTINF:-1 tvg-id="sport1.de" tvg-name="Sport 1" group-title="Sport",Sport 1
        https://streams.example/sport1.m3u8
        """.trimIndent(),
    )

    private fun secondPlaylist() = parser.parse(
        """
        #EXTM3U
        #EXTINF:-1 tvg-id="ard.de" tvg-name="ARD HD Updated" tvg-logo="https://logos.example/ard-new.png" group-title="News" tvg-chno="1",ARD
        https://streams.example/ard.m3u8
        """.trimIndent(),
    )

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

    private suspend fun seedSideEffects(channelId: String) {
        assertNotNull(database.catalogDao().getChannels(PROVIDER_ID).firstOrNull { it.id == channelId })
        database.epgDao().upsertEpgSources(
            listOf(
                EpgSourceEntity(
                    id = "epg-source-1",
                    name = "Public EPG",
                    urlKey = "secure:public-epg",
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
                    externalChannelId = "sport1.de",
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
    }
}
