package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query(
        """
        SELECT * FROM favorites
        WHERE providerId = :providerId AND mediaType = :mediaType
        ORDER BY sortOrder, createdAt DESC
        """,
    )
    fun observeFavorites(providerId: String, mediaType: String): Flow<List<FavoriteEntity>>

    @Query(
        """
        SELECT * FROM favorites
        WHERE providerId = :providerId AND mediaType = :mediaType AND mediaId = :mediaId
        """,
    )
    suspend fun getFavorite(providerId: String, mediaType: String, mediaId: String): FavoriteEntity?

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :favoriteId")
    suspend fun deleteFavorite(favoriteId: String)

    @Query("DELETE FROM favorites WHERE providerId = :providerId")
    suspend fun deleteFavoritesForProvider(providerId: String)

    @Query("DELETE FROM favorites WHERE providerId = :providerId AND mediaType = :mediaType AND mediaId = :mediaId")
    suspend fun deleteFavoriteByMedia(providerId: String, mediaType: String, mediaId: String)

    @Query("DELETE FROM favorites WHERE providerId = :providerId AND mediaType = :mediaType AND mediaId IN (:mediaIds)")
    suspend fun deleteFavoritesByMediaIds(providerId: String, mediaType: String, mediaIds: List<String>)
}
