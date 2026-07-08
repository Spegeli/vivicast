package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_sources ORDER BY name COLLATE NOCASE")
    fun observeEpgSources(): Flow<List<EpgSourceEntity>>

    @Query("SELECT * FROM epg_sources ORDER BY name COLLATE NOCASE")
    suspend fun getEpgSources(): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_sources WHERE id = :sourceId")
    suspend fun getEpgSource(sourceId: String): EpgSourceEntity?

    @Query(
        """
        SELECT * FROM provider_epg_sources
        WHERE providerId = :providerId
        ORDER BY priority
        """,
    )
    fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSourceEntity>>

    @Query(
        """
        SELECT * FROM provider_epg_sources
        WHERE providerId = :providerId
        ORDER BY priority
        """,
    )
    suspend fun getProviderEpgSources(providerId: String): List<ProviderEpgSourceEntity>

    @Query(
        """
        SELECT * FROM provider_epg_sources
        WHERE epgSourceId = :epgSourceId
        ORDER BY providerId, priority
        """,
    )
    suspend fun getProviderEpgSourcesForSource(epgSourceId: String): List<ProviderEpgSourceEntity>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE providerId = :providerId
            AND channelId = :channelId
            AND endTime >= :fromMillis
            AND startTime <= :toMillis
        ORDER BY startTime
        """,
    )
    fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgramEntity>>

    @Query(
        """
        SELECT * FROM epg_channel_mappings
        WHERE providerId = :providerId AND channelId = :channelId
        ORDER BY isManual DESC, createdAt DESC
        """,
    )
    fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMappingEntity>>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE title LIKE '%' || :query || '%' COLLATE NOCASE
           OR subtitle LIKE '%' || :query || '%' COLLATE NOCASE
           OR description LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY startTime
        LIMIT :limit
        """,
    )
    suspend fun searchPrograms(query: String, limit: Int): List<EpgProgramEntity>

    @Query(
        """
        SELECT * FROM epg_channel_mappings
        WHERE providerId = :providerId AND epgSourceId = :epgSourceId
        """,
    )
    suspend fun getMappingsForProviderAndSource(providerId: String, epgSourceId: String): List<EpgChannelMappingEntity>

    @Query(
        """
        SELECT * FROM epg_channel_mappings
        WHERE providerId = :providerId
            AND channelId = :channelId
            AND epgSourceId = :epgSourceId
        """,
    )
    suspend fun getMappingForChannelSource(
        providerId: String,
        channelId: String,
        epgSourceId: String,
    ): EpgChannelMappingEntity?

    @Upsert
    suspend fun upsertEpgSources(sources: List<EpgSourceEntity>)

    @Query(
        "UPDATE epg_sources SET lastRefreshAt = :refreshedAt, lastChannelCount = :channelCount, " +
            "lastProgramCount = :programCount WHERE id = :sourceId",
    )
    suspend fun markEpgSourceRefreshed(sourceId: String, refreshedAt: Long, channelCount: Int, programCount: Int)

    @Query("UPDATE epg_sources SET isRefreshing = :refreshing WHERE id = :sourceId")
    suspend fun setEpgSourceRefreshing(sourceId: String, refreshing: Boolean)

    @Upsert
    suspend fun upsertProviderEpgSources(sources: List<ProviderEpgSourceEntity>)

    @Upsert
    suspend fun upsertPrograms(programs: List<EpgProgramEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Upsert
    suspend fun upsertMappings(mappings: List<EpgChannelMappingEntity>)

    @Query("DELETE FROM provider_epg_sources WHERE providerId = :providerId")
    suspend fun deleteProviderEpgSources(providerId: String)

    @Query("DELETE FROM provider_epg_sources WHERE providerId = :providerId AND epgSourceId = :epgSourceId")
    suspend fun deleteProviderEpgSource(providerId: String, epgSourceId: String)

    @Query("DELETE FROM provider_epg_sources WHERE epgSourceId = :epgSourceId")
    suspend fun deleteProviderEpgSourcesForSource(epgSourceId: String)

    @Query("DELETE FROM epg_channel_mappings WHERE providerId = :providerId")
    suspend fun deleteMappingsForProvider(providerId: String)

    @Query("DELETE FROM epg_channel_mappings WHERE epgSourceId = :epgSourceId")
    suspend fun deleteMappingsForSource(epgSourceId: String)

    @Query("DELETE FROM epg_channel_mappings WHERE providerId = :providerId AND channelId IN (:channelIds)")
    suspend fun deleteMappingsForChannels(providerId: String, channelIds: List<String>)

    @Query(
        """
        DELETE FROM epg_channel_mappings
        WHERE providerId = :providerId
            AND channelId = :channelId
            AND epgSourceId = :epgSourceId
            AND isManual = 1
        """,
    )
    suspend fun deleteManualMappingForChannelSource(
        providerId: String,
        channelId: String,
        epgSourceId: String,
    )

    @Query("DELETE FROM epg_programs WHERE epgSourceId = :epgSourceId")
    suspend fun deleteProgramsForSource(epgSourceId: String)

    @Query("DELETE FROM epg_programs WHERE providerId = :providerId AND epgSourceId = :epgSourceId")
    suspend fun deleteProgramsForProviderAndSource(providerId: String, epgSourceId: String)

    @Query("DELETE FROM epg_programs WHERE providerId = :providerId")
    suspend fun deleteProgramsForProvider(providerId: String)

    @Query("DELETE FROM epg_programs WHERE providerId = :providerId AND channelId IN (:channelIds)")
    suspend fun deleteProgramsForChannels(providerId: String, channelIds: List<String>)

    @Query(
        """
        DELETE FROM epg_programs
        WHERE endTime < :fromMillis OR startTime > :toMillis
        """,
    )
    suspend fun deleteProgramsOutsideWindow(fromMillis: Long, toMillis: Long): Int

    @Query(
        """
        DELETE FROM epg_programs
        WHERE providerId = :providerId
            AND channelId = :channelId
            AND epgSourceId = :epgSourceId
        """,
    )
    suspend fun deleteProgramsForChannelAndSource(
        providerId: String,
        channelId: String,
        epgSourceId: String,
    )

    @Query("DELETE FROM epg_sources WHERE id = :epgSourceId")
    suspend fun deleteEpgSource(epgSourceId: String)
}
