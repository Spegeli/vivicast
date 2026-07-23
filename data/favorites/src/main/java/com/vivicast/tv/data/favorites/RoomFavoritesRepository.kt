package com.vivicast.tv.data.favorites

import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.domain.ids.UserDataIds
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoritesRepository(
    database: VivicastDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : FavoritesRepository {
    private val favoritesDao = database.favoritesDao()

    override fun observeFavorites(providerId: String, mediaType: MediaType): Flow<List<Favorite>> =
        favoritesDao.observeFavorites(providerId, mediaType.storageValue).map { favorites ->
            favorites.map { it.toDomain() }
        }

    override fun observeFavorites(mediaType: MediaType): Flow<List<Favorite>> =
        favoritesDao.observeFavorites(mediaType.storageValue).map { favorites ->
            favorites.map { it.toDomain() }
        }

    override suspend fun isFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean =
        favoritesDao.getFavorite(providerId, mediaType.storageValue, mediaId) != null

    override suspend fun addFavorite(favorite: Favorite) {
        favoritesDao.upsertFavorite(favorite.toEntity())
    }

    override suspend fun removeFavorite(providerId: String, mediaType: MediaType, mediaId: String) {
        favoritesDao.deleteFavoriteByMedia(providerId, mediaType.storageValue, mediaId)
    }

    override suspend fun toggleFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean {
        val existing = favoritesDao.getFavorite(providerId, mediaType.storageValue, mediaId)
        if (existing != null) {
            favoritesDao.deleteFavorite(existing.id)
            return false
        }

        val now = clock()
        favoritesDao.upsertFavorite(
            FavoriteEntity(
                id = UserDataIds.favoriteId(providerId, mediaType, mediaId),
                providerId = providerId,
                mediaType = mediaType.storageValue,
                mediaId = mediaId,
                mediaStableKey = mediaStableKey(mediaId),
                isPending = false,
                // #9: real insertion-order key (max+1 per provider/mediaType group), not a constant epoch-clamp.
                sortOrder = favoritesDao.maxSortOrder(providerId, mediaType.storageValue) + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return true
    }
}

private fun FavoriteEntity.toDomain(): Favorite =
    Favorite(
        id = id,
        providerId = providerId,
        mediaType = mediaType.toMediaType(),
        mediaId = mediaId,
        mediaStableKey = mediaStableKey,
        isPending = isPending,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun Favorite.toEntity(): FavoriteEntity =
    FavoriteEntity(
        id = id,
        providerId = providerId,
        mediaType = mediaType.storageValue,
        mediaId = mediaId,
        mediaStableKey = mediaStableKey,
        isPending = isPending,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private val MediaType.storageValue: String
    get() = when (this) {
        MediaType.Channel -> "CHANNEL"
        MediaType.Movie -> "MOVIE"
        MediaType.Series -> "SERIES"
        MediaType.Episode -> "EPISODE"
    }

private fun String.toMediaType(): MediaType =
    when (this) {
        "CHANNEL" -> MediaType.Channel
        "MOVIE" -> MediaType.Movie
        "SERIES" -> MediaType.Series
        "EPISODE" -> MediaType.Episode
        else -> MediaType.Channel
    }

private fun mediaStableKey(mediaId: String): String =
    mediaId.substringAfterLast(':').ifBlank { UserDataIds.stableHash(mediaId) }
