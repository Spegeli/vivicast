package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.SearchHistoryEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {
    @Query(
        """
        SELECT * FROM search_history
        ORDER BY lastUsedAt DESC
        LIMIT :limit
        """,
    )
    fun observeSearchHistory(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query(
        """
        SELECT * FROM search_history
        ORDER BY lastUsedAt DESC
        """,
    )
    suspend fun getSearchHistory(): List<SearchHistoryEntity>

    @Query("SELECT * FROM search_history WHERE normalizedQuery = :normalizedQuery LIMIT 1")
    suspend fun getSearchHistory(normalizedQuery: String): SearchHistoryEntity?

    @Upsert
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE normalizedQuery = :normalizedQuery")
    suspend fun deleteSearchHistory(normalizedQuery: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query(
        """
        DELETE FROM search_history
        WHERE id NOT IN (
            SELECT id FROM search_history
            ORDER BY lastUsedAt DESC
            LIMIT :limit
        )
        """,
    )
    suspend fun trimSearchHistory(limit: Int)

    @Query(
        """
        SELECT channels.* FROM search_channels_fts
        INNER JOIN channels ON channels.id = search_channels_fts.mediaId
        LEFT JOIN categories ON categories.id = channels.categoryId
        WHERE search_channels_fts MATCH :matchQuery
        ORDER BY channels.providerId, COALESCE(categories.sortOrder, 0), COALESCE(channels.channelNumber, ''), channels.name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchChannels(matchQuery: String, limit: Int): List<ChannelEntity>

    @Query(
        """
        SELECT movies.* FROM search_movies_fts
        INNER JOIN movies ON movies.id = search_movies_fts.mediaId
        WHERE search_movies_fts MATCH :matchQuery
        ORDER BY movies.name COLLATE NOCASE, movies.year, movies.providerId
        LIMIT :limit
        """,
    )
    suspend fun searchMovies(matchQuery: String, limit: Int): List<MovieEntity>

    @Query(
        """
        SELECT series.* FROM search_series_fts
        INNER JOIN series ON series.id = search_series_fts.mediaId
        WHERE search_series_fts MATCH :matchQuery
        ORDER BY series.name COLLATE NOCASE, series.year, series.providerId
        LIMIT :limit
        """,
    )
    suspend fun searchSeries(matchQuery: String, limit: Int): List<SeriesEntity>

    @Query(
        """
        SELECT epg_programs.* FROM search_epg_fts
        INNER JOIN epg_programs ON epg_programs.id = search_epg_fts.programId
        INNER JOIN channels ON channels.id = epg_programs.channelId
        WHERE search_epg_fts MATCH :matchQuery
            AND epg_programs.epgSourceId = (
                SELECT m.epgSourceId FROM epg_channel_mappings m
                INNER JOIN epg_sources s ON s.id = m.epgSourceId
                INNER JOIN provider_epg_sources pes
                    ON pes.providerId = m.providerId AND pes.epgSourceId = m.epgSourceId
                WHERE m.providerId = epg_programs.providerId
                    AND m.channelId = epg_programs.channelId
                    AND s.isActive = 1
                ORDER BY m.isManual DESC, pes.priority ASC
                LIMIT 1
            )
        ORDER BY epg_programs.startTime, channels.name COLLATE NOCASE, epg_programs.title COLLATE NOCASE
        LIMIT :limit
        """,
    )
    // Only the priority winner per (provider, channel) is returned (manual first, else highest-priority
    // linked+active source that matched the channel) — mirrors observeProgramsForChannel, so a channel
    // matched by >=2 sources doesn't yield duplicate hits. Keep the two winner subqueries in sync.
    suspend fun searchEpg(matchQuery: String, limit: Int): List<EpgProgramEntity>
}
