package com.vivicast.tv.data.epg

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.iptv.xmltv.DefaultXmltvParser
import com.vivicast.tv.iptv.xmltv.XmltvChannel
import com.vivicast.tv.iptv.xmltv.XmltvDocument
import com.vivicast.tv.iptv.xmltv.XmltvProgram
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.AbstractList
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class RoomEpgRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomEpgRepository
    private lateinit var secureValueStore: InMemorySecureValueStore
    private val parser = DefaultXmltvParser()
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomEpgRepository(database) { now }
        secureValueStore = InMemorySecureValueStore()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeChannelsForProviderReturnsProviderChannels() = runBlocking {
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-zdf", remoteId = "zdf.provider", name = "ZDF HD", catchup = false)
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-ard", remoteId = "ard.de", name = "ARD HD", catchup = true)
        seedLiveChannel(providerId = OTHER_PROVIDER_ID, channelId = "other-ard", remoteId = "ard.de", name = "ARD HD", catchup = false)

        val channels = repository.observeChannelsForProvider(PROVIDER_ID).first()

        assertEquals(listOf("ARD HD", "ZDF HD"), channels.map { it.name })
        assertEquals(listOf(PROVIDER_ID, PROVIDER_ID), channels.map { it.providerId })
        assertEquals(listOf(true, false), channels.map { it.isCatchupAvailable })
    }

    @Test
    fun importXmltvMapsChannelsAndStoresPrograms() = runBlocking {
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-ard", remoteId = "ard.de", name = "ARD HD", catchup = true)
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-zdf", remoteId = "zdf.provider", name = "ZDF HD", catchup = false)
        val source = repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "epg-source-1",
                name = "Public EPG",
                sourceConfigKey = "secure:epg-source-1",
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
                sourceConfigKey = "secure:shared-source",
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

    @Test
    fun cleanupProgramsOutsideRetentionKeepsSourcesLinksAndMappings() = runBlocking {
        now = 10L * MILLIS_PER_DAY
        repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "epg-source-1",
                name = "Public EPG",
                sourceConfigKey = "secure:epg-source-1",
                timeShiftMinutes = 0,
                isActive = true,
            ),
        )
        repository.linkEpgSourceToProvider(PROVIDER_ID, "epg-source-1", priority = 1)
        database.epgDao().upsertMappings(
            listOf(
                EpgChannelMappingEntity(
                    id = "mapping-1",
                    providerId = PROVIDER_ID,
                    channelId = "channel-ard",
                    epgSourceId = "epg-source-1",
                    epgChannelId = "ard.de",
                    isManual = true,
                    createdAt = now,
                ),
            ),
        )
        database.epgDao().upsertPrograms(
            listOf(
                epgProgram(id = "old", start = now - 3L * MILLIS_PER_DAY, end = now - 2L * MILLIS_PER_DAY),
                epgProgram(id = "current", start = now, end = now + 3_600_000L),
                epgProgram(id = "future", start = now + 8L * MILLIS_PER_DAY, end = now + 8L * MILLIS_PER_DAY + 3_600_000L),
            ),
        )

        val deleted = repository.cleanupProgramsOutsideRetention(
            nowMillis = now,
            pastDays = 1,
            futureDays = 7,
        )

        assertEquals(2, deleted)
        assertEquals(1, countTable("epg_programs"))
        assertEquals(1, countTable("epg_sources"))
        assertEquals(1, countTable("provider_epg_sources"))
        assertEquals(1, countTable("epg_channel_mappings"))
    }

    @Test
    fun largeFixtureEpgNowNextAndDayViewStayWithinPrd13Targets() = runBlocking {
        now = 3L * MILLIS_PER_DAY + 12L * MILLIS_PER_HOUR
        seedLargeEpgFixture(channelCount = 50, days = 7)

        val firstChannelPrograms = repository.observeProgramsForChannel(
            providerId = PROVIDER_ID,
            channelId = "channel-large-0001",
            fromMillis = now,
            toMillis = now + MILLIS_PER_DAY,
        ).first()
        assertEquals(50, firstChannelPrograms.size)

        val nowNextP95 = p95Millis {
            (1..50).forEach { index ->
                repository.observeProgramsForChannel(
                    providerId = PROVIDER_ID,
                    channelId = "channel-large-${index.toString().padStart(4, '0')}",
                    fromMillis = now,
                    toMillis = now + PROGRAM_SLOT_MILLIS,
                ).first()
            }
        }
        val dayViewP95 = p95Millis {
            repository.observeProgramsForChannel(
                providerId = PROVIDER_ID,
                channelId = "channel-large-0001",
                fromMillis = now,
                toMillis = now + MILLIS_PER_DAY,
            ).first()
        }

        Log.i(BENCHMARK_LOG_TAG, "largeFixture nowNextP95=${nowNextP95}ms dayViewP95=${dayViewP95}ms")

        assertTrue("EPG now/next p95 was ${nowNextP95}ms", nowNextP95 <= EPG_BUDGET_MS)
        assertTrue("EPG day view p95 was ${dayViewP95}ms", dayViewP95 <= EPG_BUDGET_MS)
    }

    @Test
    fun largeFixtureXmltvCommitAndCleanupStaysWithinPrd13Budget() = runBlocking {
        now = 7L * MILLIS_PER_DAY
        seedLargeXmltvChannels(XMLTV_CHANNEL_COUNT)
        repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "epg-large-xmltv",
                name = "Large XMLTV",
                sourceConfigKey = "secure:epg-large-xmltv",
                timeShiftMinutes = 0,
                isActive = true,
            ),
        )
        repository.linkEpgSourceToProvider(PROVIDER_ID, "epg-large-xmltv", priority = 1)

        lateinit var result: EpgImportResult
        val document = largeXmltvDocument(channelCount = XMLTV_CHANNEL_COUNT, programCount = XMLTV_PROGRAM_COUNT)
        val importMs = measureTimeMillis {
            result = repository.importXmltv(PROVIDER_ID, "epg-large-xmltv", document)
        }
        var programsDeletedByCleanup = 0
        val cleanupMs = measureTimeMillis {
            programsDeletedByCleanup = repository.cleanupProgramsOutsideRetention(
                nowMillis = now,
                pastDays = 1,
                futureDays = 7,
            )
        }
        val totalMs = importMs + cleanupMs
        val remainingPrograms = countTable("epg_programs")

        Log.i(
            BENCHMARK_LOG_TAG,
            "largeFixtureXmltv importMs=${importMs} cleanupMs=${cleanupMs} totalMs=${totalMs} " +
                "imported=${result.programsImported} deleted=${programsDeletedByCleanup} " +
                "remaining=${remainingPrograms}",
        )

        assertEquals(XMLTV_PROGRAM_COUNT, result.programsImported)
        assertEquals(XMLTV_CHANNEL_COUNT, result.mappingsAdded)
        assertEquals(XMLTV_PROGRAM_COUNT - remainingPrograms, programsDeletedByCleanup)
        assertTrue("XMLTV import plus cleanup was ${totalMs}ms", totalMs <= XMLTV_IMPORT_BUDGET_MS)
    }

    @Test
    fun manualMappingOverridesAutomaticMappingOnImport() = runBlocking {
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-ard", remoteId = "ard.de", name = "ARD HD", catchup = false)
        repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "epg-source-1",
                name = "Public EPG",
                sourceConfigKey = "secure:epg-source-1",
                timeShiftMinutes = 0,
                isActive = true,
            ),
        )

        val mapping = repository.setManualChannelMapping(
            ManualEpgChannelMappingRequest(
                providerId = PROVIDER_ID,
                channelId = "channel-ard",
                epgSourceId = "epg-source-1",
                epgChannelId = "zdf.xml",
            ),
        )
        val result = repository.importXmltv(PROVIDER_ID, "epg-source-1", xmltvFixture())

        assertTrue(mapping.isManual)
        assertEquals("zdf.xml", mapping.epgChannelId)
        assertEquals(1, result.programsImported)
        assertEquals(0, result.mappingsAdded)

        val programs = repository.observeProgramsForChannel(
            providerId = PROVIDER_ID,
            channelId = "channel-ard",
            fromMillis = 0L,
            toMillis = Long.MAX_VALUE,
        ).first()
        assertEquals(listOf("heute journal"), programs.map { it.title })
        assertEquals(listOf(true), repository.observeMappingsForChannel(PROVIDER_ID, "channel-ard").first().map { it.isManual })
    }

    @Test
    fun clearedManualMappingAllowsAutomaticMappingOnNextImport() = runBlocking {
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-ard", remoteId = "ard.de", name = "ARD HD", catchup = false)
        repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "epg-source-1",
                name = "Public EPG",
                sourceConfigKey = "secure:epg-source-1",
                timeShiftMinutes = 0,
                isActive = true,
            ),
        )
        repository.setManualChannelMapping(
            ManualEpgChannelMappingRequest(
                providerId = PROVIDER_ID,
                channelId = "channel-ard",
                epgSourceId = "epg-source-1",
                epgChannelId = "zdf.xml",
            ),
        )
        repository.importXmltv(PROVIDER_ID, "epg-source-1", xmltvFixture())

        repository.clearManualChannelMapping(PROVIDER_ID, "channel-ard", "epg-source-1")
        val result = repository.importXmltv(PROVIDER_ID, "epg-source-1", xmltvFixture())

        assertEquals(1, result.programsImported)
        assertEquals(1, result.mappingsAdded)
        val programs = repository.observeProgramsForChannel(
            providerId = PROVIDER_ID,
            channelId = "channel-ard",
            fromMillis = 0L,
            toMillis = Long.MAX_VALUE,
        ).first()
        assertEquals(listOf("Tagesschau"), programs.map { it.title })
        assertEquals(listOf(false), repository.observeMappingsForChannel(PROVIDER_ID, "channel-ard").first().map { it.isManual })
    }

    @Test
    fun clearingAutomaticMappingDoesNotRemovePrograms() = runBlocking {
        seedLiveChannel(providerId = PROVIDER_ID, channelId = "channel-ard", remoteId = "ard.de", name = "ARD HD", catchup = false)
        repository.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = "epg-source-1",
                name = "Public EPG",
                sourceConfigKey = "secure:epg-source-1",
                timeShiftMinutes = 0,
                isActive = true,
            ),
        )
        repository.importXmltv(PROVIDER_ID, "epg-source-1", xmltvFixture())

        repository.clearManualChannelMapping(PROVIDER_ID, "channel-ard", "epg-source-1")

        val programs = repository.observeProgramsForChannel(
            providerId = PROVIDER_ID,
            channelId = "channel-ard",
            fromMillis = 0L,
            toMillis = Long.MAX_VALUE,
        ).first()
        assertEquals(listOf("Tagesschau"), programs.map { it.title })
        assertEquals(listOf(false), repository.observeMappingsForChannel(PROVIDER_ID, "channel-ard").first().map { it.isManual })
    }

    @Test
    fun secureEpgSourceRepositoryStoresUrlOutsideRoomAndDeletesSideEffects() = runBlocking {
        val sourceRepository = SecureEpgSourceRepository(
            database = database,
            secureValueStore = secureValueStore,
            delegate = repository,
        )
        val source = sourceRepository.saveSource(
            EpgSourceEditRequest(
                sourceId = "epg-source-1",
                name = "Public EPG",
                url = "https://epg.example/file.xml?token=secret",
                timeShiftMinutes = 30,
                isActive = true,
            ),
        )
        sourceRepository.linkSourceToProvider(PROVIDER_ID, source.id, priority = 1)

        assertEquals("epg-source:epg-source-1:url", source.sourceConfigKey)
        assertEquals("https://epg.example/file.xml?token=secret", secureValueStore.values[source.sourceConfigKey])
        assertFalse(epgSourceTableContains("https://epg.example/file.xml?token=secret"))

        sourceRepository.saveSource(
            EpgSourceEditRequest(
                sourceId = source.id,
                name = "Public EPG Updated",
                url = null,
                timeShiftMinutes = 60,
                isActive = false,
            ),
        )

        assertEquals("https://epg.example/file.xml?token=secret", secureValueStore.values[source.sourceConfigKey])

        sourceRepository.deleteSource(source.id)

        assertEquals(null, secureValueStore.values[source.sourceConfigKey])
        assertEquals(0, database.epgDao().getEpgSources().size)
        assertEquals(0, database.epgDao().getProviderEpgSources(PROVIDER_ID).size)
    }

    @Test
    fun secureEpgSourceRepositoryReordersAndUnlinksProviderPriorities() = runBlocking {
        val sourceRepository = SecureEpgSourceRepository(
            database = database,
            secureValueStore = secureValueStore,
            delegate = repository,
        )
        listOf("one", "two", "three").forEachIndexed { index, id ->
            sourceRepository.saveSource(
                EpgSourceEditRequest(
                    sourceId = id,
                    name = "EPG $id",
                    url = "https://epg.example/$id.xml",
                ),
            )
            sourceRepository.linkSourceToProvider(PROVIDER_ID, id, priority = index + 1)
        }

        sourceRepository.moveSourcePriority(PROVIDER_ID, "three", EpgSourcePriorityDirection.Up)

        assertEquals(
            listOf("one" to 1, "three" to 2, "two" to 3),
            database.epgDao().getProviderEpgSources(PROVIDER_ID).map { it.epgSourceId to it.priority },
        )

        sourceRepository.unlinkSourceFromProvider(PROVIDER_ID, "three")

        assertEquals(
            listOf("one" to 1, "two" to 2),
            database.epgDao().getProviderEpgSources(PROVIDER_ID).map { it.epgSourceId to it.priority },
        )
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
                    epgChannelId = "ard.de",
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

    private suspend fun seedLargeEpgFixture(channelCount: Int, days: Int) {
        val categoryId = "$PROVIDER_ID-large-live"
        database.catalogDao().upsertCategories(
            listOf(
                CategoryEntity(
                    id = categoryId,
                    providerId = PROVIDER_ID,
                    type = "LIVE",
                    remoteId = "large-news",
                    name = "Large News",
                    sortOrder = 0,
                    isHidden = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.catalogDao().upsertChannels(
            (1..channelCount).map { index ->
                val suffix = index.toString().padStart(4, '0')
                ChannelEntity(
                    id = "channel-large-$suffix",
                    providerId = PROVIDER_ID,
                    categoryId = categoryId,
                    stableKey = "channel-large-$suffix",
                    remoteId = "channel-large-$suffix",
                    channelNumber = index.toString(),
                    name = "Large Channel $suffix",
                    logoUrl = null,
                    isCatchupAvailable = true,
                    catchupDays = 7,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        database.epgDao().upsertPrograms(
            (1..channelCount).flatMap { channelIndex ->
                val channelSuffix = channelIndex.toString().padStart(4, '0')
                val slotCount = days * 24 * 2
                (0 until slotCount).map { slot ->
                    val start = slot * PROGRAM_SLOT_MILLIS
                    EpgProgramEntity(
                        id = "program-large-$channelSuffix-$slot",
                        providerId = PROVIDER_ID,
                        channelId = "channel-large-$channelSuffix",
                        epgSourceId = "epg-large",
                        stableKey = "program-large-$channelSuffix-$slot",
                        epgChannelId = "channel-large-$channelSuffix",
                        title = "Program $channelSuffix $slot",
                        normalizedTitle = "program $channelSuffix $slot",
                        subtitle = null,
                        description = null,
                        startTime = start,
                        endTime = start + PROGRAM_SLOT_MILLIS,
                        category = null,
                        iconUrl = null,
                        isCatchupAvailable = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            },
        )
    }

    private suspend fun seedLargeXmltvChannels(channelCount: Int) {
        val categoryId = "$PROVIDER_ID-xmltv-live"
        database.catalogDao().upsertCategories(
            listOf(
                CategoryEntity(
                    id = categoryId,
                    providerId = PROVIDER_ID,
                    type = "LIVE",
                    remoteId = "xmltv-news",
                    name = "XMLTV News",
                    sortOrder = 0,
                    isHidden = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.catalogDao().upsertChannels(
            (1..channelCount).map { index ->
                val suffix = index.toString().padStart(4, '0')
                ChannelEntity(
                    id = "xmltv-channel-$suffix",
                    providerId = PROVIDER_ID,
                    categoryId = categoryId,
                    stableKey = "xmltv-channel-$suffix",
                    remoteId = "xmltv-channel-$suffix",
                    channelNumber = index.toString(),
                    name = "XMLTV Channel $suffix",
                    logoUrl = null,
                    isCatchupAvailable = false,
                    catchupDays = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }

    private fun largeXmltvDocument(channelCount: Int, programCount: Int): XmltvDocument =
        XmltvDocument(
            channels = (1..channelCount).map { index ->
                val suffix = index.toString().padStart(4, '0')
                XmltvChannel(
                    id = "xmltv-channel-$suffix",
                    displayNames = listOf("XMLTV Channel $suffix"),
                    iconUrl = null,
                )
            },
            programs = GeneratedXmltvPrograms(channelCount = channelCount, programCount = programCount),
            skippedPrograms = 0,
        )

    private suspend fun p95Millis(
        sampleCount: Int = 20,
        block: suspend () -> Unit,
    ): Long {
        val samples = (1..sampleCount).map {
            measureTimeMillis {
                block()
            }
        }.sorted()
        val p95Index = ((samples.size * 95 + 99) / 100 - 1).coerceIn(samples.indices)
        return samples[p95Index]
    }

    private fun epgProgram(id: String, start: Long, end: Long): EpgProgramEntity =
        EpgProgramEntity(
            id = id,
            providerId = PROVIDER_ID,
            channelId = "channel-ard",
            epgSourceId = "epg-source-1",
            epgChannelId = "ard.de",
            title = id,
            subtitle = null,
            description = null,
            startTime = start,
            endTime = end,
            category = null,
            iconUrl = null,
            isCatchupAvailable = false,
            createdAt = now,
            updatedAt = now,
        )

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

    private fun countTable(tableName: String): Int =
        database.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $tableName")).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun epgSourceTableContains(value: String): Boolean =
        database.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM epg_sources WHERE sourceConfigKey = ?", arrayOf<Any?>(value))).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) > 0
        }

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val OTHER_PROVIDER_ID = "provider-2"
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        const val MILLIS_PER_HOUR = 60L * 60L * 1000L
        const val PROGRAM_SLOT_MILLIS = 30L * 60L * 1000L
        const val EPG_BUDGET_MS = 500L
        const val BENCHMARK_LOG_TAG = "VivicastBenchmark"
        const val XMLTV_CHANNEL_COUNT = 50
        const val XMLTV_PROGRAM_COUNT = 50_000
        const val XMLTV_IMPORT_BUDGET_MS = 600_000L
    }

    private class GeneratedXmltvPrograms(
        private val channelCount: Int,
        private val programCount: Int,
    ) : AbstractList<XmltvProgram>() {
        override val size: Int = programCount

        override fun get(index: Int): XmltvProgram {
            val channelIndex = index % channelCount + 1
            val slot = index / channelCount
            val suffix = channelIndex.toString().padStart(4, '0')
            val start = slot * PROGRAM_SLOT_MILLIS
            return XmltvProgram(
                channelId = "xmltv-channel-$suffix",
                startTimeMillis = start,
                endTimeMillis = start + PROGRAM_SLOT_MILLIS,
                title = "XMLTV Program",
                subtitle = null,
                description = null,
                category = null,
                iconUrl = null,
                isCatchupAvailable = false,
            )
        }
    }
}

private class InMemorySecureValueStore : SecureValueStore {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: SecureKey): String? =
        values[key.value]

    override suspend fun write(key: SecureKey, value: String) {
        values[key.value] = value
    }

    override suspend fun delete(key: SecureKey) {
        values.remove(key.value)
    }
}
