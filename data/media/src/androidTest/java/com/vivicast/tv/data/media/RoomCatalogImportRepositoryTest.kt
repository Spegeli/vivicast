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
