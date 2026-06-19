package com.vivicast.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.core.database.entity.EpgProgramEntity
import com.vivicast.core.database.entity.EpgSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_sources ORDER BY name")
    fun observeSources(): Flow<List<EpgSourceEntity>>

    @Query("SELECT COUNT(*) FROM epg_programs")
    fun observeProgramCount(): Flow<Int>

    @Query("SELECT * FROM epg_sources WHERE playlistId = :playlistId LIMIT 1")
    fun observeSourceForPlaylist(playlistId: String): Flow<EpgSourceEntity?>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE channelId = :channelId
        AND endUtcEpochMillis > :fromUtcEpochMillis
        AND startUtcEpochMillis < :toUtcEpochMillis
        ORDER BY startUtcEpochMillis
        """
    )
    fun observePrograms(
        channelId: String,
        fromUtcEpochMillis: Long,
        toUtcEpochMillis: Long
    ): Flow<List<EpgProgramEntity>>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE channelId IN (:channelIds)
        AND endUtcEpochMillis > :fromUtcEpochMillis
        AND startUtcEpochMillis < :toUtcEpochMillis
        ORDER BY channelId, startUtcEpochMillis
        """
    )
    fun observeProgramsForChannels(
        channelIds: List<String>,
        fromUtcEpochMillis: Long,
        toUtcEpochMillis: Long
    ): Flow<List<EpgProgramEntity>>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE channelId = :channelId
        AND startUtcEpochMillis <= :nowUtcEpochMillis
        AND endUtcEpochMillis > :nowUtcEpochMillis
        LIMIT 1
        """
    )
    fun observeNowProgram(channelId: String, nowUtcEpochMillis: Long): Flow<EpgProgramEntity?>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE channelId = :channelId
        AND startUtcEpochMillis > :nowUtcEpochMillis
        ORDER BY startUtcEpochMillis
        LIMIT 1
        """
    )
    fun observeNextProgram(channelId: String, nowUtcEpochMillis: Long): Flow<EpgProgramEntity?>

    @Upsert
    suspend fun upsertSource(source: EpgSourceEntity)

    @Query("DELETE FROM epg_sources WHERE playlistId = :playlistId")
    suspend fun deleteSourcesForPlaylist(playlistId: String)

    @Query("DELETE FROM epg_sources WHERE id = :sourceId")
    suspend fun deleteSourceById(sourceId: String)

    @Upsert
    suspend fun upsertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE channelId IN (:channelIds)")
    suspend fun deleteProgramsForChannels(channelIds: List<String>)

    @Query("DELETE FROM epg_programs WHERE endUtcEpochMillis < :beforeUtcEpochMillis")
    suspend fun deleteProgramsEndingBefore(beforeUtcEpochMillis: Long)
}
