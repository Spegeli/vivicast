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
        WHERE mediaType = :mediaType
        ORDER BY sortOrder, createdAt DESC
        """,
    )
    fun observeFavorites(mediaType: String): Flow<List<FavoriteEntity>>

    @Query(
        """
        SELECT * FROM favorites
        WHERE providerId = :providerId AND mediaType = :mediaType AND mediaId = :mediaId
        """,
    )
    suspend fun getFavorite(providerId: String, mediaType: String, mediaId: String): FavoriteEntity?

    @Query(
        """
        SELECT * FROM favorites
        ORDER BY providerId, mediaType, sortOrder, createdAt DESC
        """,
    )
    suspend fun getFavorites(): List<FavoriteEntity>

    // Restored-but-unbound favorites (backup restore writes them keyed by stableKey with isPending=1); the
    // post-import reconcile binds them to the freshly-imported catalog row. See plans/backup-restore-groups-lost.md.
    @Query("SELECT * FROM favorites WHERE providerId = :providerId AND isPending = 1")
    suspend fun getPendingFavorites(providerId: String): List<FavoriteEntity>

    // #9: next insertion-order sort key for a (provider, mediaType) group. 0 when the group is empty → the
    // first favorite gets 1. Keeps observeFavorites' `sortOrder ASC` in real insertion order (oldest first).
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM favorites WHERE providerId = :providerId AND mediaType = :mediaType")
    suspend fun maxSortOrder(providerId: String, mediaType: String): Int

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
