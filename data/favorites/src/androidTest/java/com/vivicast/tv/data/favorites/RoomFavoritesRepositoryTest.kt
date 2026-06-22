package com.vivicast.tv.data.favorites

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
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
class RoomFavoritesRepositoryTest {
    private lateinit var database: VivicastDatabase
    private lateinit var repository: RoomFavoritesRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomFavoritesRepository(database) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeFavoritesIsProviderAndMediaTypeScoped() = runBlocking {
        repository.addFavorite(favorite(id = "p1-channel", providerId = PROVIDER_ID, mediaType = MediaType.Channel, mediaId = "channel-1"))
        repository.addFavorite(favorite(id = "p1-movie", providerId = PROVIDER_ID, mediaType = MediaType.Movie, mediaId = "movie-1"))
        repository.addFavorite(favorite(id = "p2-channel", providerId = OTHER_PROVIDER_ID, mediaType = MediaType.Channel, mediaId = "channel-2"))

        val channelFavorites = repository.observeFavorites(PROVIDER_ID, MediaType.Channel).first()

        assertEquals(listOf("channel-1"), channelFavorites.map { it.mediaId })
        assertEquals(listOf(PROVIDER_ID), channelFavorites.map { it.providerId })
        assertEquals(listOf(MediaType.Channel), channelFavorites.map { it.mediaType })
    }

    @Test
    fun toggleFavoriteAddsThenRemovesFavorite() = runBlocking {
        assertFalse(repository.isFavorite(PROVIDER_ID, MediaType.Channel, "channel-1"))

        val added = repository.toggleFavorite(PROVIDER_ID, MediaType.Channel, "channel-1")

        assertTrue(added)
        assertTrue(repository.isFavorite(PROVIDER_ID, MediaType.Channel, "channel-1"))
        assertEquals(listOf("channel-1"), repository.observeFavorites(PROVIDER_ID, MediaType.Channel).first().map { it.mediaId })

        val removed = repository.toggleFavorite(PROVIDER_ID, MediaType.Channel, "channel-1")

        assertFalse(removed)
        assertFalse(repository.isFavorite(PROVIDER_ID, MediaType.Channel, "channel-1"))
        assertEquals(emptyList<String>(), repository.observeFavorites(PROVIDER_ID, MediaType.Channel).first().map { it.mediaId })
    }

    private fun favorite(
        id: String,
        providerId: String,
        mediaType: MediaType,
        mediaId: String,
    ): Favorite =
        Favorite(
            id = id,
            providerId = providerId,
            mediaType = mediaType,
            mediaId = mediaId,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
        )

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val OTHER_PROVIDER_ID = "provider-2"
    }
}
