package com.vivicast.tv.core.database.dao

import androidx.room.Dao
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

    @Upsert
    suspend fun upsertEpgSources(sources: List<EpgSourceEntity>)

    @Upsert
    suspend fun upsertProviderEpgSources(sources: List<ProviderEpgSourceEntity>)

    @Upsert
    suspend fun upsertPrograms(programs: List<EpgProgramEntity>)

    @Upsert
    suspend fun upsertMappings(mappings: List<EpgChannelMappingEntity>)

    @Query("DELETE FROM provider_epg_sources WHERE providerId = :providerId")
    suspend fun deleteProviderEpgSources(providerId: String)

    @Query("DELETE FROM epg_channel_mappings WHERE providerId = :providerId")
    suspend fun deleteMappingsForProvider(providerId: String)

    @Query("DELETE FROM epg_programs WHERE epgSourceId = :epgSourceId")
    suspend fun deleteProgramsForSource(epgSourceId: String)
}

