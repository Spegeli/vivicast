package com.vivicast.tv.data.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import com.vivicast.tv.domain.model.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMediaRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomMediaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomMediaRepository(database)
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
    fun searchesLocalCatalogAndEpgOnly() = runBlocking {
        seedCatalog()

        val result = repository.search("dune", limitPerType = 5)

        assertEquals(listOf("Dune TV"), result.channels.map { it.name })
        assertEquals(listOf("Dune Movie"), result.movies.map { it.name })
        assertEquals(listOf("Dune Series"), result.series.map { it.name })
        assertEquals(listOf("Dune Special"), result.epgPrograms.map { it.title })
    }

    private suspend fun seedCatalog() {
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
                    externalChannelId = "dune.tv",
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

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val LIVE_CATEGORY_ID = "provider-1:live:news"
        const val NOW = 1_000L
    }
}
