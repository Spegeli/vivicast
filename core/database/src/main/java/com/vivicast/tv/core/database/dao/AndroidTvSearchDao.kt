package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.vivicast.tv.core.database.model.AndroidTvSearchEntryEntity

@Dao
interface AndroidTvSearchDao {
    @Query("SELECT * FROM android_tv_search_entries ORDER BY mediaType, title COLLATE NOCASE")
    suspend fun getEntries(): List<AndroidTvSearchEntryEntity>

    /**
     * Read-time protection: the index is a pure content mirror, so protected/adult content is filtered
     * here against the caller's *current* PIN flags — never baked into the persisted index. Callers must
     * pass the live flags (fail-closed) so a protected item can never surface via system search.
     */
    @Query(
        """
        SELECT * FROM android_tv_search_entries
        WHERE (
            :queryPattern = '%%'
            OR title LIKE :queryPattern ESCAPE '\'
            OR subtitle LIKE :queryPattern ESCAPE '\'
        )
        AND (:protectMovies = 0 OR mediaType != 'MOVIE')
        AND (:protectSeries = 0 OR mediaType != 'SERIES')
        AND (:protectAdultContent = 0 OR isAdult = 0)
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
        protectMovies: Boolean,
        protectSeries: Boolean,
        protectAdultContent: Boolean,
        limit: Int,
    ): List<AndroidTvSearchEntryEntity>

    @Query("DELETE FROM android_tv_search_entries")
    suspend fun clearEntries()

    /** Targeted removal for a single deleted provider — avoids a full all-providers rebuild on delete. */
    @Query("DELETE FROM android_tv_search_entries WHERE providerStableKey = :providerStableKey")
    suspend fun deleteEntriesForProvider(providerStableKey: String)

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
            isAdult,
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
            0,
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
            isAdult,
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
            movies.isAdult,
            MAX(providers.updatedAt, movies.updatedAt)
        FROM movies
        INNER JOIN providers ON providers.id = movies.providerId
        WHERE providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeMovies = 1
        """,
    )
    suspend fun insertMovieEntries()

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
            isAdult,
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
            series.isAdult,
            MAX(providers.updatedAt, series.updatedAt)
        FROM series
        INNER JOIN providers ON providers.id = series.providerId
        WHERE providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeSeries = 1
        """,
    )
    suspend fun insertSeriesEntries()

    // Rebuilds the index as a pure content mirror (all active/included Channels/Movies/Series incl.
    // adult). Protection is applied at read time in [searchEntries], never here — so no rebuild trigger
    // (import, refresh, background) can leak protected content.
    @Transaction
    suspend fun rebuildEntries() {
        clearEntries()
        insertChannelEntries()
        insertMovieEntries()
        insertSeriesEntries()
    }
}
