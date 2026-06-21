package com.vivicast.tv.data.epg

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.iptv.xmltv.DefaultXmltvParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomEpgRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomEpgRepository
    private val parser = DefaultXmltvParser()
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomEpgRepository(database) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importXmltvMapsChannelsAndStoresPrograms() = runBlocking {
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-ard", remoteId = "ard.de", name = "ARD HD", catchup = true)
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-zdf", remoteId = "zdf.provider", name = "ZDF HD", catchup = false)
        val source = repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "epg-source-1",
                name = "Public EPG",
                urlKey = "secure:epg-source-1",
                timeShiftMinutes = 60,
                isActive = true,
            ),
        )
        repository.linkEpgSourceToProvider(PROVIDER_ID, source.id, priority = 1)

        val result = repository.importXmltv(PROVIDER_ID, source.id, xmltvFixture())

        assertEquals(2, result.programsImported)
        assertEquals(1, result.programsSkipped)
        assertEquals(2, result.mappingsAdded)
        assertEquals(0, result.mappingsUpdated)

        val ardPrograms = repository.observeProgramsForChannel(
            providerId = PROVIDER_ID,
            channelId = "channel-ard",
            fromMillis = 0L,
            toMillis = Long.MAX_VALUE,
        ).first()
        assertEquals(1, ardPrograms.size)
        assertEquals("Tagesschau", ardPrograms.single().title)
        assertEquals(1_672_606_800_000L, ardPrograms.single().startTime)
        assertTrue(ardPrograms.single().isCatchupAvailable)

        val zdfPrograms = repository.observeProgramsForChannel(
            providerId = PROVIDER_ID,
            channelId = "channel-zdf",
            fromMillis = 0L,
            toMillis = Long.MAX_VALUE,
        ).first()
        assertEquals(1, zdfPrograms.size)
        assertEquals("heute journal", zdfPrograms.single().title)
        assertFalse(zdfPrograms.single().isCatchupAvailable)
    }

    @Test
    fun importXmltvKeepsProgramsForOtherProvidersUsingSameSource() = runBlocking {
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "provider-1-ard", remoteId = "ard.de", name = "ARD HD", catchup = false)
        seedLiveChannel(providerId = OTHER_PROVIDER_ID, channelId = "provider-2-ard", remoteId = "ard.de", name = "ARD HD", catchup = false)
        repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "shared-source",
                name = "Shared EPG",
                urlKey = "secure:shared-source",
                timeShiftMinutes = 0,
                isActive = true,
            ),
        )
        seedProgramForOtherProvider()

        val result = repository.importXmltv(PROVIDER_ID, "shared-source", xmltvFixture())

        assertEquals(1, countPrograms(PROVIDER_ID, "shared-source"))
        assertEquals(1, countPrograms(OTHER_PROVIDER_ID, "shared-source"))
        assertEquals(1, result.programsImported)
    }

    private suspend fun seedLiveChannel(
        providerId: String,
        channelId: String,
        remoteId: String,
        name: String,
        catchup: Boolean,
    ) {
        val categoryId = "$providerId-live"
        database.catalogDao().upsertCategories(
            listOf(
                CategoryEntity(
                    id = categoryId,
                    providerId = providerId,
                    type = "LIVE",
                    remoteId = "news",
                    name = "News",
                    sortOrder = 0,
                    isHidden = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.catalogDao().upsertChannels(
            listOf(
                ChannelEntity(
                    id = channelId,
                    providerId = providerId,
                    categoryId = categoryId,
                    remoteId = remoteId,
                    channelNumber = null,
                    name = name,
                    logoUrl = null,
                    isCatchupAvailable = catchup,
                    catchupDays = if (catchup) 7 else 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private suspend fun seedProgramForOtherProvider() {
        database.epgDao().upsertPrograms(
            listOf(
                EpgProgramEntity(
                    id = "other-provider-program",
                    providerId = OTHER_PROVIDER_ID,
                    channelId = "provider-2-ard",
                    epgSourceId = "shared-source",
                    externalChannelId = "ard.de",
                    title = "Existing Other Provider Program",
                    subtitle = null,
                    description = null,
                    startTime = now,
                    endTime = now + 3_600_000L,
                    category = null,
                    iconUrl = null,
                    isCatchupAvailable = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun xmltvFixture() = parser.parse(
        """
        <tv>
            <channel id="ard.de">
                <display-name>Das Erste HD</display-name>
            </channel>
            <channel id="zdf.xml">
                <display-name>ZDF HD</display-name>
            </channel>
            <channel id="unused.xml">
                <display-name>Unused</display-name>
            </channel>
            <programme start="20230101200000 +0000" stop="20230101201500 +0000" channel="ard.de">
                <title>Tagesschau</title>
                <desc>Nachrichten</desc>
                <category>News</category>
            </programme>
            <programme start="20230101211500 +0000" stop="20230101214500 +0000" channel="zdf.xml">
                <title>heute journal</title>
            </programme>
            <programme start="20230101220000 +0000" stop="20230101230000 +0000" channel="unused.xml">
                <title>Unused Program</title>
            </programme>
        </tv>
        """.trimIndent(),
    )

    private fun countPrograms(providerId: String, epgSourceId: String): Int =
        database.query(
            SimpleSQLiteQuery(
                "SELECT COUNT(*) FROM epg_programs WHERE providerId = ? AND epgSourceId = ?",
                arrayOf<Any?>(providerId, epgSourceId),
            ),
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val OTHER_PROVIDER_ID = "provider-2"
    }
}
