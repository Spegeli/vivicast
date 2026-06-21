package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.SeriesEntity

@Dao
interface SearchDao {
    @Query(
        """
        SELECT * FROM channels
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchChannels(query: String, limit: Int): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM movies
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchMovies(query: String, limit: Int): List<MovieEntity>

    @Query(
        """
        SELECT * FROM series
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchSeries(query: String, limit: Int): List<SeriesEntity>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE title LIKE '%' || :query || '%'
            OR subtitle LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
        ORDER BY startTime DESC
        LIMIT :limit
        """,
    )
    suspend fun searchEpg(query: String, limit: Int): List<EpgProgramEntity>
}

