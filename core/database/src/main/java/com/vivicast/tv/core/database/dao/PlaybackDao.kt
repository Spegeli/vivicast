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

    // Restored-but-unbound progress/history (backup restore writes them keyed by stableKey with isPending=1);
    // the post-import reconcile binds them to the freshly-imported catalog. See plans/backup-restore-groups-lost.md.
    @Query("SELECT * FROM playback_progress WHERE providerId = :providerId AND isPending = 1")
    suspend fun getPendingProgress(providerId: String): List<PlaybackProgressEntity>

    @Query("SELECT * FROM channel_history WHERE providerId = :providerId AND isPending = 1")
    suspend fun getPendingChannelHistory(providerId: String): List<ChannelHistoryEntity>

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

    // Keep only the newest [cap] channel-history rows (global — matches the global recent-channels query),
    // pruned after each write so the table can't grow unbounded. The Home "recently watched" row is the only
    // consumer, so older rows are pure ballast.
    @Query(
        """
        DELETE FROM channel_history
        WHERE id NOT IN (SELECT id FROM channel_history ORDER BY watchedAt DESC LIMIT :cap)
        """,
    )
    suspend fun pruneChannelHistory(cap: Int)

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

    // Delete-by-PK for the reconcile: when a pending row's corrected PK differs from its restore-format PK,
    // the old row is dropped and the corrected one upserted. See plans/backup-restore-groups-lost.md.
    @Query("DELETE FROM playback_progress WHERE id = :id")
    suspend fun deleteProgressById(id: String)

    @Query("DELETE FROM channel_history WHERE id = :id")
    suspend fun deleteChannelHistoryById(id: String)
}
