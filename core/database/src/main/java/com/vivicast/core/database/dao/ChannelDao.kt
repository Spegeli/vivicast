package com.vivicast.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.core.database.entity.CategoryEntity
import com.vivicast.core.database.entity.ChannelEntity
import com.vivicast.core.database.entity.FavoriteChannelEntity
import com.vivicast.core.database.entity.RecentChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId ORDER BY sortIndex, name")
    fun observeCategories(playlistId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortIndex, name")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortIndex, name")
    fun observeChannels(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortIndex, name")
    suspend fun getChannels(playlistId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY sortIndex, name")
    fun observeAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY sortIndex, name")
    fun observeChannelsForCategory(categoryId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannel(channelId: String): ChannelEntity?

    @Query("SELECT * FROM favorite_channels ORDER BY addedAtEpochMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteChannelEntity>>

    @Query("SELECT * FROM recent_channels ORDER BY watchedAtEpochMillis DESC")
    fun observeRecents(): Flow<List<RecentChannelEntity>>

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE channelId = :channelId")
    suspend fun deleteFavorite(channelId: String)

    @Upsert
    suspend fun upsertRecent(recent: RecentChannelEntity)
}
