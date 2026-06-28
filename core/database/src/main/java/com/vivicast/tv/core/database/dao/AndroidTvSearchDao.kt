package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.vivicast.tv.core.database.model.AndroidTvSearchEntryEntity

@Dao
interface AndroidTvSearchDao {
    @Query("SELECT * FROM android_tv_search_entries ORDER BY mediaType, title COLLATE NOCASE")
    suspend fun getEntries(): List<AndroidTvSearchEntryEntity>

    @Query(
        """
        SELECT * FROM android_tv_search_entries
        WHERE :queryPattern = '%%'
            OR title LIKE :queryPattern ESCAPE '\'
            OR subtitle LIKE :queryPattern ESCAPE '\'
        ORDER BY
            CASE WHEN title LIKE :prefixPattern ESCAPE '\' THEN 0 ELSE 1 END,
            mediaType,
            title COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchEntries(
        queryPattern: String,
        prefixPattern: String,
        limit: Int,
    ): List<AndroidTvSearchEntryEntity>

    @Query("DELETE FROM android_tv_search_entries")
    suspend fun clearEntries()

    @Query(
        """
        INSERT INTO android_tv_search_entries (
            id,
            mediaType,
            providerStableKey,
            mediaStableKey,
            title,
            subtitle,
            imageUrl,
            deepLink,
            updatedAt
        )
        SELECT
            'CHANNEL:' || providers.stableKey || ':' || channels.stableKey,
            'CHANNEL',
            providers.stableKey,
            channels.stableKey,
            channels.name,
            'Live-TV',
            channels.logoUrl,
            'vivicast://channel/' || providers.stableKey || '/' || channels.stableKey,
            MAX(providers.updatedAt, channels.updatedAt)
        FROM channels
        INNER JOIN providers ON providers.id = channels.providerId
        WHERE providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeLiveTv = 1
        """,
    )
    suspend fun insertChannelEntries()

    @Query(
        """
        INSERT INTO android_tv_search_entries (
            id,
            mediaType,
            providerStableKey,
            mediaStableKey,
            title,
            subtitle,
            imageUrl,
            deepLink,
            updatedAt
        )
        SELECT
            'MOVIE:' || providers.stableKey || ':' || movies.stableKey,
            'MOVIE',
            providers.stableKey,
            movies.stableKey,
            movies.name,
            COALESCE(movies.year, 'Film'),
            COALESCE(movies.posterUrl, movies.backdropUrl),
            'vivicast://movie/' || providers.stableKey || '/' || movies.stableKey,
            MAX(providers.updatedAt, movies.updatedAt)
        FROM movies
        INNER JOIN providers ON providers.id = movies.providerId
        WHERE providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeMovies = 1
            AND :protectMovies = 0
            AND (:protectAdultContent = 0 OR movies.isAdult = 0)
        """,
    )
    suspend fun insertMovieEntries(
        protectMovies: Boolean,
        protectAdultContent: Boolean,
    )

    @Query(
        """
        INSERT INTO android_tv_search_entries (
            id,
            mediaType,
            providerStableKey,
            mediaStableKey,
            title,
            subtitle,
            imageUrl,
            deepLink,
            updatedAt
        )
        SELECT
            'SERIES:' || providers.stableKey || ':' || series.stableKey,
            'SERIES',
            providers.stableKey,
            series.stableKey,
            series.name,
            COALESCE(series.year, 'Serie'),
            COALESCE(series.posterUrl, series.backdropUrl),
            'vivicast://series/' || providers.stableKey || '/' || series.stableKey,
            MAX(providers.updatedAt, series.updatedAt)
        FROM series
        INNER JOIN providers ON providers.id = series.providerId
        WHERE providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeSeries = 1
            AND :protectSeries = 0
            AND (:protectAdultContent = 0 OR series.isAdult = 0)
        """,
    )
    suspend fun insertSeriesEntries(
        protectSeries: Boolean,
        protectAdultContent: Boolean,
    )

    @Transaction
    suspend fun rebuildEntries(
        protectMovies: Boolean = false,
        protectSeries: Boolean = false,
        protectAdultContent: Boolean = false,
    ) {
        clearEntries()
        insertChannelEntries()
        insertMovieEntries(protectMovies, protectAdultContent)
        insertSeriesEntries(protectSeries, protectAdultContent)
    }
}
