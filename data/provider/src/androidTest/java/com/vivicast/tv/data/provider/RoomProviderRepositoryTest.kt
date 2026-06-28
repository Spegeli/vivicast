package com.vivicast.tv.data.provider

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomProviderRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var secureValueStore: FakeSecureValueStore
    private lateinit var repository: RoomProviderRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        secureValueStore = FakeSecureValueStore()
        repository = RoomProviderRepository(database, secureValueStore) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createM3uProviderStoresCredentialReferenceOnlyInRoom() = runBlocking {
        val result = repository.createProvider(
            ProviderCreateRequest(
                name = " Living Room ",
                type = ProviderType.M3u,
                m3uUrl = "https://example.invalid/list.m3u",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
                refreshIntervalHours = 12,
            ),
        )

        val provider = database.providerDao().getProvider(result.provider.id)

        assertEquals("Living Room", result.provider.name)
        assertEquals(ProviderStatus.Active, result.provider.status)
        assertEquals(ProviderCredentials.M3u("https://example.invalid/list.m3u"), repository.getCredentials(result.provider.id))
        assertEquals(result.provider.sourceConfigKey, provider?.sourceConfigKey)
        assertFalse(provider?.sourceConfigKey.orEmpty().contains("example.invalid"))
        assertFalse(provider?.name.orEmpty().contains("example.invalid"))
        assertTrue(secureValueStore.values.values.contains("https://example.invalid/list.m3u"))
    }

    @Test
    fun duplicateProviderNameIsNotSavedAndDoesNotWriteNewSecrets() = runBlocking {
        repository.createProvider(
            ProviderCreateRequest(
                name = "Provider",
                type = ProviderType.M3u,
                m3uUrl = "https://example.invalid/first.m3u",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
            ),
        )

        val result = runCatching {
            repository.createProvider(
                ProviderCreateRequest(
                    name = " provider ",
                    type = ProviderType.M3u,
                    m3uUrl = "https://example.invalid/duplicate.m3u",
                    includeLiveTv = true,
                    includeMovies = false,
                    includeSeries = false,
                ),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1, database.providerDao().getProviders().size)
        assertFalse(secureValueStore.values.values.contains("https://example.invalid/duplicate.m3u"))
    }

    @Test
    fun duplicateProviderRenameIsNotSavedAndKeepsExistingSecrets() = runBlocking {
        val first = repository.createProvider(
            ProviderCreateRequest(
                name = "Provider A",
                type = ProviderType.M3u,
                m3uUrl = "https://example.invalid/a.m3u",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
            ),
        ).provider
        val second = repository.createProvider(
            ProviderCreateRequest(
                name = "Provider B",
                type = ProviderType.M3u,
                m3uUrl = "https://example.invalid/b.m3u",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
            ),
        ).provider

        val result = runCatching {
            repository.updateProvider(
                ProviderUpdateRequest(
                    providerId = second.id,
                    name = "provider a",
                    m3uUrl = "https://example.invalid/changed.m3u",
                    includeLiveTv = true,
                    includeMovies = false,
                    includeSeries = false,
                ),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Provider B", requireNotNull(repository.getProvider(second.id)).name)
        assertEquals(ProviderCredentials.M3u("https://example.invalid/b.m3u"), repository.getCredentials(second.id))
        assertEquals(ProviderCredentials.M3u("https://example.invalid/a.m3u"), repository.getCredentials(first.id))
    }

    @Test
    fun updateDisableAndDeleteProviderKeepsIdentityAndRemovesProviderOwnedData() = runBlocking {
        val created = repository.createProvider(
            ProviderCreateRequest(
                name = "Provider",
                type = ProviderType.M3u,
                m3uUrl = "https://example.invalid/old.m3u",
                includeLiveTv = true,
                includeMovies = true,
                includeSeries = true,
                refreshIntervalHours = 12,
            ),
        ).provider

        now = 2_000L
        val updated = repository.updateProvider(
            ProviderUpdateRequest(
                providerId = created.id,
                name = "Renamed Provider",
                m3uUrl = "https://example.invalid/new.m3u",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = true,
                refreshIntervalHours = 24,
            ),
        ).provider

        assertEquals(created.id, updated.id)
        assertEquals(created.sourceConfigKey, updated.sourceConfigKey)
        assertEquals("Renamed Provider", updated.name)
        assertEquals(ProviderCredentials.M3u("https://example.invalid/new.m3u"), repository.getCredentials(created.id))

        repository.setProviderEnabled(created.id, false)
        val disabled = requireNotNull(repository.getProvider(created.id))
        assertFalse(disabled.isActive)
        assertEquals(ProviderStatus.Disabled, disabled.status)

        seedProviderOwnedData(created.id)

        repository.deleteProvider(created.id)

        assertNull(repository.getProvider(created.id))
        assertNull(repository.getCredentials(created.id))
        assertFalse(secureValueStore.values.keys.any { it.startsWith(created.sourceConfigKey) })
        assertEquals(0, countForProvider("categories", created.id))
        assertEquals(0, countForProvider("channels", created.id))
        assertEquals(0, countForProvider("favorites", created.id))
        assertEquals(0, countForProvider("playback_progress", created.id))
        assertEquals(0, countForProvider("channel_history", created.id))
        assertEquals(0, countForProvider("provider_epg_sources", created.id))
        assertEquals(0, countForProvider("epg_programs", created.id))
        assertEquals(0, countForProvider("epg_channel_mappings", created.id))
        assertEquals(1, countAll("epg_sources"))
    }

    private suspend fun seedProviderOwnedData(providerId: String) {
        val now = 3_000L
        database.catalogDao().upsertCategories(
            listOf(
                CategoryEntity(
                    id = "category-1",
                    providerId = providerId,
                    type = "LIVE",
                    remoteId = "remote-category-1",
                    name = "News",
                    sortOrder = 1,
                    isHidden = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.catalogDao().upsertChannels(
            listOf(
                ChannelEntity(
                    id = "channel-1",
                    providerId = providerId,
                    categoryId = "category-1",
                    remoteId = "remote-channel-1",
                    channelNumber = "1",
                    name = "Channel",
                    logoUrl = null,
                    isCatchupAvailable = false,
                    catchupDays = 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.epgDao().upsertEpgSources(
            listOf(
                EpgSourceEntity(
                    id = "epg-source-1",
                    name = "Independent EPG",
                    sourceConfigKey = "secure:epg-source-1",
                    timeShiftMinutes = 0,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.epgDao().upsertProviderEpgSources(
            listOf(
                ProviderEpgSourceEntity(
                    id = "provider-epg-source-1",
                    providerId = providerId,
                    epgSourceId = "epg-source-1",
                    priority = 1,
                    createdAt = now,
                ),
            ),
        )
        database.epgDao().upsertPrograms(
            listOf(
                EpgProgramEntity(
                    id = "program-1",
                    providerId = providerId,
                    channelId = "channel-1",
                    epgSourceId = "epg-source-1",
                    epgChannelId = "external-channel-1",
                    title = "Program",
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
        database.epgDao().upsertMappings(
            listOf(
                EpgChannelMappingEntity(
                    id = "mapping-1",
                    providerId = providerId,
                    channelId = "channel-1",
                    epgSourceId = "epg-source-1",
                    epgChannelId = "external-channel-1",
                    isManual = false,
                    createdAt = now,
                ),
            ),
        )
        database.favoritesDao().upsertFavorite(
            FavoriteEntity(
                id = "favorite-1",
                providerId = providerId,
                mediaType = "LIVE",
                mediaId = "channel-1",
                sortOrder = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.playbackDao().upsertProgress(
            PlaybackProgressEntity(
                id = "progress-1",
                providerId = providerId,
                mediaType = "MOVIE",
                mediaId = "movie-1",
                positionMillis = 10_000L,
                durationMillis = 100_000L,
                progressPercent = 10,
                isCompleted = false,
                lastWatchedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.playbackDao().upsertChannelHistory(
            ChannelHistoryEntity(
                id = "history-1",
                providerId = providerId,
                channelId = "channel-1",
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

    private class FakeSecureValueStore : SecureValueStore {
        val values = mutableMapOf<String, String>()

        override suspend fun read(key: SecureKey): String? = values[key.value]

        override suspend fun write(key: SecureKey, value: String) {
            values[key.value] = value
        }

        override suspend fun delete(key: SecureKey) {
            values.remove(key.value)
        }
    }
}
