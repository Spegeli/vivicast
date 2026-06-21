package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Query(
        """
        SELECT * FROM playback_progress
        WHERE providerId = :providerId AND isCompleted = 0
        ORDER BY lastWatchedAt DESC
        """,
    )
    fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgressEntity>>

    @Query(
        """
        SELECT * FROM playback_progress
        WHERE providerId = :providerId AND mediaType = :mediaType AND mediaId = :mediaId
        """,
    )
    suspend fun getProgress(providerId: String, mediaType: String, mediaId: String): PlaybackProgressEntity?

    @Query(
        """
        SELECT * FROM channel_history
        WHERE providerId = :providerId
        ORDER BY watchedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistoryEntity>>

    @Upsert
    suspend fun upsertProgress(progress: PlaybackProgressEntity)

    @Upsert
    suspend fun upsertChannelHistory(history: ChannelHistoryEntity)

    @Query("DELETE FROM playback_progress WHERE providerId = :providerId")
    suspend fun deleteProgressForProvider(providerId: String)

    @Query("DELETE FROM channel_history WHERE providerId = :providerId")
    suspend fun deleteHistoryForProvider(providerId: String)
}

