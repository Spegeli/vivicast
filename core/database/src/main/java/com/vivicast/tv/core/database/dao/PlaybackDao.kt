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
        WHERE isCompleted = 0 AND mediaType IN ('MOVIE', 'EPISODE')
        ORDER BY lastWatchedAt DESC
        """,
    )
    fun observeAllContinueWatching(): Flow<List<PlaybackProgressEntity>>

    // All EPISODE rows, completed AND in-progress, newest first. The Home "Serien fortsetzen" row is
    // series-centric and advances to the next episode after one completes, so it needs completed rows too
    // (observeAllContinueWatching is isCompleted=0 only).
    @Query(
        """
        SELECT * FROM playback_progress
        WHERE mediaType = 'EPISODE'
        ORDER BY lastWatchedAt DESC
        """,
    )
    fun observeAllEpisodeProgress(): Flow<List<PlaybackProgressEntity>>

    @Query(
        """
        SELECT * FROM playback_progress
        WHERE providerId = :providerId AND mediaType = :mediaType AND mediaId = :mediaId
        """,
    )
    suspend fun getProgress(providerId: String, mediaType: String, mediaId: String): PlaybackProgressEntity?

    @Query(
        """
        SELECT * FROM playback_progress
        ORDER BY providerId, mediaType, lastWatchedAt DESC
        """,
    )
    suspend fun getPlaybackProgress(): List<PlaybackProgressEntity>

    @Query(
        """
        SELECT * FROM channel_history
        WHERE providerId = :providerId
        ORDER BY watchedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistoryEntity>>

    @Query(
        """
        SELECT * FROM channel_history
        ORDER BY watchedAt DESC
        LIMIT :limit
        """,
    )
    fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistoryEntity>>

    @Query(
        """
        SELECT * FROM channel_history
        ORDER BY providerId, watchedAt DESC
        """,
    )
    suspend fun getChannelHistory(): List<ChannelHistoryEntity>

    @Upsert
    suspend fun upsertProgress(progress: PlaybackProgressEntity)

    @Upsert
    suspend fun upsertChannelHistory(history: ChannelHistoryEntity)

    @Query("DELETE FROM playback_progress WHERE providerId = :providerId")
    suspend fun deleteProgressForProvider(providerId: String)

    @Query("DELETE FROM channel_history WHERE providerId = :providerId")
    suspend fun deleteHistoryForProvider(providerId: String)

    @Query("DELETE FROM channel_history")
    suspend fun deleteAllChannelHistory()

    @Query("DELETE FROM playback_progress WHERE mediaType = :mediaType")
    suspend fun deleteProgressForMediaType(mediaType: String)

    @Query("DELETE FROM playback_progress WHERE mediaType IN (:mediaTypes)")
    suspend fun deleteProgressForMediaTypes(mediaTypes: List<String>)

    @Query("DELETE FROM playback_progress WHERE providerId = :providerId AND mediaType = :mediaType AND mediaId IN (:mediaIds)")
    suspend fun deleteProgressForMediaIds(providerId: String, mediaType: String, mediaIds: List<String>)

    @Query("DELETE FROM channel_history WHERE providerId = :providerId AND channelId IN (:channelIds)")
    suspend fun deleteHistoryForChannels(providerId: String, channelIds: List<String>)
}
