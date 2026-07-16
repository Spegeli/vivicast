package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.EpgChannelEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgProgramStageEntity
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
        SELECT p.* FROM epg_programs p
        INNER JOIN epg_sources s ON p.epgSourceId = s.id
        WHERE p.providerId = :providerId
            AND p.channelId = :channelId
            AND p.endTime >= :fromMillis
            AND p.startTime <= :toMillis
            AND s.isActive = 1
        ORDER BY p.startTime
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

    // Recovery for a refresh cancelled/killed mid-run that left isRefreshing stuck at 1. Run once at startup.
    @Query("UPDATE epg_sources SET isRefreshing = 0 WHERE isRefreshing = 1")
    suspend fun clearStuckRefreshingState()

    @Upsert
    suspend fun upsertProviderEpgSources(sources: List<ProviderEpgSourceEntity>)

    @Upsert
    suspend fun upsertPrograms(programs: List<EpgProgramEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    // --- Staged delta-merge (non-blocking import; plans/nonblocking-db-imports.md) ---
    // Programmes are staged chunked into epg_programs_stage, then merged into live by the delta trio below
    // (delete-changed -> insert-missing -> delete-stale). Each op fires the matching search_epg_fts trigger,
    // so the FTS mirror stays consistent on the delta. INSERT OR REPLACE is deliberately NOT used: on a
    // conflict it would skip the fts delete trigger and desync the mirror. Stage is cleared around the import.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgramsStage(rows: List<EpgProgramStageEntity>)

    @Query("DELETE FROM epg_programs_stage WHERE providerId = :providerId AND epgSourceId = :epgSourceId")
    suspend fun clearProgramsStage(providerId: String, epgSourceId: String)

    @Query(
        """
        DELETE FROM epg_programs
        WHERE providerId = :providerId AND epgSourceId = :epgSourceId
            AND EXISTS (
                SELECT 1 FROM epg_programs_stage s
                WHERE s.id = epg_programs.id AND s.syncFingerprint <> epg_programs.syncFingerprint
            )
        """,
    )
    suspend fun deleteChangedProgramsFromStage(providerId: String, epgSourceId: String)

    @Query(
        """
        INSERT INTO epg_programs (
            id, providerId, channelId, epgSourceId, stableKey, epgChannelId, title, normalizedTitle,
            subtitle, description, startTime, endTime, category, iconUrl, isCatchupAvailable,
            createdAt, updatedAt, syncFingerprint
        )
        SELECT id, providerId, channelId, epgSourceId, stableKey, epgChannelId, title, normalizedTitle,
            subtitle, description, startTime, endTime, category, iconUrl, isCatchupAvailable,
            createdAt, updatedAt, syncFingerprint
        FROM epg_programs_stage s
        WHERE s.providerId = :providerId AND s.epgSourceId = :epgSourceId
            AND NOT EXISTS (SELECT 1 FROM epg_programs l WHERE l.id = s.id)
        """,
    )
    suspend fun insertMissingProgramsFromStage(providerId: String, epgSourceId: String)

    @Query(
        """
        DELETE FROM epg_programs
        WHERE providerId = :providerId AND epgSourceId = :epgSourceId
            AND NOT EXISTS (SELECT 1 FROM epg_programs_stage s WHERE s.id = epg_programs.id)
        """,
    )
    suspend fun deleteStaleProgramsFromStage(providerId: String, epgSourceId: String)

    // Startup crash-recovery: drop any staged programmes a killed refresh left behind.
    @Query("DELETE FROM epg_programs_stage")
    suspend fun clearAllProgramsStage()

    @Upsert
    suspend fun upsertMappings(mappings: List<EpgChannelMappingEntity>)

    // EPG channel <icon> store (per source). Read only via the effective-logo join in CatalogDao; there
    // is no direct query. Replaced per source on each import (delete + upsert).
    @Upsert
    suspend fun upsertEpgChannels(channels: List<EpgChannelEntity>)

    @Query("DELETE FROM epg_channels WHERE epgSourceId = :epgSourceId")
    suspend fun deleteEpgChannelsForSource(epgSourceId: String)

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

    // Past-only retention cleanup: future programmes have no upper bound (we keep whatever the feed gives).
    @Query("DELETE FROM epg_programs WHERE endTime < :beforeMillis")
    suspend fun deleteProgramsBefore(beforeMillis: Long): Int

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
