package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.ChannelStageEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.EpisodeStageEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.MovieStageEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import com.vivicast.tv.core.database.model.SeriesStageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query(
        """
        SELECT * FROM categories
        WHERE providerId = :providerId AND type = :type AND isHidden = 0
        ORDER BY sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeVisibleCategories(providerId: String, type: String): Flow<List<CategoryEntity>>

    @Query(
        "SELECT c.*, " + EFFECTIVE_LOGO_COLUMN + " AS effectiveLogoUrl " +
            "FROM channels c LEFT JOIN providers p ON p.id = c.providerId " +
            "WHERE c.providerId = :providerId AND (:categoryId IS NULL OR c.categoryId = :categoryId) " +
            "ORDER BY COALESCE(c.channelNumber, ''), c.name COLLATE NOCASE",
    )
    fun observeChannels(providerId: String, categoryId: String?): Flow<List<ChannelWithLogo>>

    @Query(
        "SELECT c.*, " + EFFECTIVE_LOGO_COLUMN + " AS effectiveLogoUrl " +
            "FROM channels c LEFT JOIN providers p ON p.id = c.providerId " +
            "WHERE c.providerId = :providerId AND (:categoryId IS NULL OR c.categoryId = :categoryId) " +
            "ORDER BY COALESCE(c.channelNumber, ''), c.name COLLATE NOCASE " +
            "LIMIT :limit OFFSET :offset",
    )
    fun observeChannelsPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<ChannelWithLogo>>

    @Query("SELECT * FROM categories WHERE providerId = :providerId AND type = :type")
    suspend fun getCategories(providerId: String, type: String): List<CategoryEntity>

    // All groups of a type INCLUDING hidden ones, for the group-management panel (the browse rails use
    // observeVisibleCategories). Base order is source order; the mode-aware final order (PLAYLIST / NAME /
    // MANUAL) is applied in the repository, which also carries the per-(provider,type) sort mode.
    @Query(
        """
        SELECT * FROM categories
        WHERE providerId = :providerId AND type = :type
        ORDER BY sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeAllCategories(providerId: String, type: String): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET isHidden = :hidden, updatedAt = :updatedAt WHERE id = :categoryId")
    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean, updatedAt: Long)

    @Query(
        "UPDATE categories SET isHidden = :hidden, updatedAt = :updatedAt " +
            "WHERE providerId = :providerId AND type = :type",
    )
    suspend fun setCategoriesHiddenForType(providerId: String, type: String, hidden: Boolean, updatedAt: Long)

    @Query("UPDATE categories SET manualSortOrder = :manualSortOrder, updatedAt = :updatedAt WHERE id = :categoryId")
    suspend fun updateManualSortOrder(categoryId: String, manualSortOrder: Int?, updatedAt: Long)

    // Clears the manual order for a (provider, type) so MANUAL mode falls back to source (playlist) order.
    @Query("UPDATE categories SET manualSortOrder = NULL, updatedAt = :updatedAt WHERE providerId = :providerId AND type = :type")
    suspend fun resetManualSortOrder(providerId: String, type: String, updatedAt: Long)

    @Query("SELECT * FROM channels WHERE providerId = :providerId")
    suspend fun getChannels(providerId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE providerId = :providerId AND id = :channelId")
    suspend fun getChannel(providerId: String, channelId: String): ChannelEntity?

    @Query(
        """
        SELECT channels.* FROM channels
        INNER JOIN providers ON providers.id = channels.providerId
        WHERE providers.stableKey = :providerStableKey
            AND channels.stableKey = :channelStableKey
            AND providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeLiveTv = 1
        LIMIT 1
        """,
    )
    suspend fun getChannelByStableKeys(
        providerStableKey: String,
        channelStableKey: String,
    ): ChannelEntity?

    // Raw logo candidates + the owning provider's order string, for the worker to pick the order-winning
    // remote URL to prefetch (local files aren't prefetched). Only channels that have some remote logo.
    @Query(
        "SELECT c.id AS channelId, p.logoPriority AS providerLogoPriority, " +
            "NULLIF(TRIM(c.logoUrl), '') AS playlistLogoUrl, " + EPG_ICON_SUBQUERY + " AS epgIconUrl " +
            "FROM channels c LEFT JOIN providers p ON p.id = c.providerId " +
            "WHERE (c.logoUrl IS NOT NULL AND TRIM(c.logoUrl) != '') " +
            "OR EXISTS (SELECT 1 FROM epg_channel_mappings m " +
            "JOIN epg_channels ec ON ec.epgSourceId = m.epgSourceId AND ec.remoteId = m.epgChannelId " +
            "WHERE m.channelId = c.id AND ec.iconUrl IS NOT NULL AND TRIM(ec.iconUrl) != '') " +
            "ORDER BY c.providerId, c.name COLLATE NOCASE",
    )
    suspend fun getChannelsWithLogoUrls(): List<ChannelLogoCandidatesRow>

    // The two remote logo candidates for one channel, for the App resolver to walk the provider's order.
    @Query(
        "SELECT NULLIF(TRIM(c.logoUrl), '') AS playlistLogoUrl, " + EPG_ICON_SUBQUERY + " AS epgIconUrl " +
            "FROM channels c WHERE c.id = :channelId",
    )
    suspend fun getLogoCandidates(channelId: String): LogoCandidates?

    @Query(
        """
        SELECT * FROM movies
        WHERE (posterUrl IS NOT NULL AND TRIM(posterUrl) != '')
           OR (backdropUrl IS NOT NULL AND TRIM(backdropUrl) != '')
        ORDER BY providerId, name COLLATE NOCASE
        """,
    )
    suspend fun getMoviesWithImageUrls(): List<MovieEntity>

    @Query(
        """
        SELECT * FROM series
        WHERE (posterUrl IS NOT NULL AND TRIM(posterUrl) != '')
           OR (backdropUrl IS NOT NULL AND TRIM(backdropUrl) != '')
        ORDER BY providerId, name COLLATE NOCASE
        """,
    )
    suspend fun getSeriesWithImageUrls(): List<SeriesEntity>

    @Query(
        """
        SELECT * FROM seasons
        WHERE posterUrl IS NOT NULL AND TRIM(posterUrl) != ''
        ORDER BY providerId, seriesId, seasonNumber
        """,
    )
    suspend fun getSeasonsWithImageUrls(): List<SeasonEntity>

    @Query(
        """
        SELECT * FROM episodes
        WHERE thumbnailUrl IS NOT NULL AND TRIM(thumbnailUrl) != ''
        ORDER BY providerId, seriesId, seasonNumber, episodeNumber
        """,
    )
    suspend fun getEpisodesWithImageUrls(): List<EpisodeEntity>

    @Query("SELECT * FROM movies WHERE providerId = :providerId")
    suspend fun getMovies(providerId: String): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE providerId = :providerId AND id = :movieId")
    suspend fun getMovie(providerId: String, movieId: String): MovieEntity?

    @Query(
        """
        SELECT movies.* FROM movies
        INNER JOIN providers ON providers.id = movies.providerId
        WHERE providers.stableKey = :providerStableKey
            AND movies.stableKey = :movieStableKey
            AND providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeMovies = 1
        LIMIT 1
        """,
    )
    suspend fun getMovieByStableKeys(
        providerStableKey: String,
        movieStableKey: String,
    ): MovieEntity?

    @Query("SELECT * FROM series WHERE providerId = :providerId")
    suspend fun getSeries(providerId: String): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE providerId = :providerId AND id = :seriesId")
    suspend fun getSeries(providerId: String, seriesId: String): SeriesEntity?

    @Query(
        """
        SELECT series.* FROM series
        INNER JOIN providers ON providers.id = series.providerId
        WHERE providers.stableKey = :providerStableKey
            AND series.stableKey = :seriesStableKey
            AND providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeSeries = 1
        LIMIT 1
        """,
    )
    suspend fun getSeriesByStableKeys(
        providerStableKey: String,
        seriesStableKey: String,
    ): SeriesEntity?

    @Query("SELECT * FROM seasons WHERE providerId = :providerId")
    suspend fun getSeasons(providerId: String): List<SeasonEntity>

    @Query("SELECT * FROM episodes WHERE providerId = :providerId")
    suspend fun getEpisodes(providerId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE providerId = :providerId AND id = :episodeId")
    suspend fun getEpisode(providerId: String, episodeId: String): EpisodeEntity?

    @Query(
        """
        SELECT episodes.* FROM episodes
        INNER JOIN providers ON providers.id = episodes.providerId
        WHERE providers.stableKey = :providerStableKey
            AND episodes.stableKey = :episodeStableKey
            AND providers.isActive = 1
            AND providers.status != 'DISABLED'
            AND providers.includeSeries = 1
        LIMIT 1
        """,
    )
    suspend fun getEpisodeByStableKeys(
        providerStableKey: String,
        episodeStableKey: String,
    ): EpisodeEntity?

    @Query(
        """
        SELECT * FROM episodes
        WHERE providerId = :providerId
            AND seriesId = :seriesId
            AND (
                seasonNumber > :seasonNumber
                OR (seasonNumber = :seasonNumber AND episodeNumber > :episodeNumber)
            )
        ORDER BY seasonNumber, episodeNumber
        LIMIT 1
        """,
    )
    suspend fun getNextEpisode(
        providerId: String,
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ): EpisodeEntity?

    @Query(
        """
        SELECT * FROM movies
        WHERE providerId = :providerId AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeMovies(providerId: String, categoryId: String?): Flow<List<MovieEntity>>

    @Query(
        """
        SELECT * FROM movies
        WHERE providerId = :providerId AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeMoviesPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<MovieEntity>>

    @Query(
        """
        SELECT * FROM series
        WHERE providerId = :providerId AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeSeries(providerId: String, categoryId: String?): Flow<List<SeriesEntity>>

    @Query(
        """
        SELECT * FROM series
        WHERE providerId = :providerId AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeSeriesPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM seasons WHERE providerId = :providerId AND seriesId = :seriesId ORDER BY seasonNumber")
    fun observeSeasons(providerId: String, seriesId: String): Flow<List<SeasonEntity>>

    @Query(
        """
        SELECT * FROM episodes
        WHERE providerId = :providerId AND seasonId = :seasonId
        ORDER BY episodeNumber
        """,
    )
    fun observeEpisodes(providerId: String, seasonId: String): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT * FROM channels
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchChannels(query: String, limit: Int): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM movies
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
           OR originalName LIKE '%' || :query || '%' COLLATE NOCASE
           OR genre LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchMovies(query: String, limit: Int): List<MovieEntity>

    @Query(
        """
        SELECT * FROM series
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
           OR originalName LIKE '%' || :query || '%' COLLATE NOCASE
           OR genre LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchSeries(query: String, limit: Int): List<SeriesEntity>

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Upsert
    suspend fun upsertMovies(movies: List<MovieEntity>)

    @Upsert
    suspend fun upsertSeries(series: List<SeriesEntity>)

    @Upsert
    suspend fun upsertSeasons(seasons: List<SeasonEntity>)

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    // --- Staged delta-merge (non-blocking import; plans/nonblocking-db-imports.md) ---
    // Catalog rows are staged chunked into <table>_stage under the real providerId, then merged into the
    // live table by the delta trio (delete-changed -> insert-missing -> delete-stale). Each op fires the
    // matching search FTS trigger, so the search mirror stays consistent on the delta. INSERT OR REPLACE is
    // deliberately NOT used: on a conflict it would skip the fts delete trigger and desync the mirror. Stage
    // is cleared around the import. removed*Ids lists the live ids with no stage match (what delete-stale
    // will drop) for pre-merge cleanup of dependent rows.

    // Channels
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelsStage(rows: List<ChannelStageEntity>)

    @Query("DELETE FROM channels_stage WHERE providerId = :providerId")
    suspend fun clearChannelsStage(providerId: String)

    @Query(
        """
        DELETE FROM channels
        WHERE providerId = :providerId
            AND EXISTS (
                SELECT 1 FROM channels_stage s
                WHERE s.id = channels.id AND s.syncFingerprint <> channels.syncFingerprint
            )
        """,
    )
    suspend fun deleteChangedChannelsFromStage(providerId: String): Int

    @Query(
        """
        INSERT INTO channels (
            id, providerId, categoryId, stableKey, remoteId, channelNumber, name, logoUrl,
            epgChannelId, isCatchupAvailable, catchupDays, createdAt, updatedAt, syncFingerprint
        )
        SELECT id, providerId, categoryId, stableKey, remoteId, channelNumber, name, logoUrl,
            epgChannelId, isCatchupAvailable, catchupDays, createdAt, updatedAt, syncFingerprint
        FROM channels_stage s
        WHERE s.providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM channels l WHERE l.id = s.id)
        """,
    )
    suspend fun insertMissingChannelsFromStage(providerId: String)

    @Query(
        """
        DELETE FROM channels
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM channels_stage s WHERE s.id = channels.id)
        """,
    )
    suspend fun deleteStaleChannelsFromStage(providerId: String)

    @Query(
        """
        SELECT id FROM channels
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM channels_stage s WHERE s.id = channels.id)
        """,
    )
    suspend fun removedChannelsIds(providerId: String): List<String>

    // Movies (`cast` is a SQL keyword — backticked)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoviesStage(rows: List<MovieStageEntity>)

    @Query("DELETE FROM movies_stage WHERE providerId = :providerId")
    suspend fun clearMoviesStage(providerId: String)

    @Query(
        """
        DELETE FROM movies
        WHERE providerId = :providerId
            AND EXISTS (
                SELECT 1 FROM movies_stage s
                WHERE s.id = movies.id AND s.syncFingerprint <> movies.syncFingerprint
            )
        """,
    )
    suspend fun deleteChangedMoviesFromStage(providerId: String): Int

    @Query(
        """
        INSERT INTO movies (
            id, providerId, categoryId, stableKey, remoteId, name, originalName, containerExtension,
            posterUrl, backdropUrl, rating, year, genre, duration, director, `cast`, plot, trailerUrl,
            addedAt, ageRating, isAdult, createdAt, updatedAt, syncFingerprint
        )
        SELECT id, providerId, categoryId, stableKey, remoteId, name, originalName, containerExtension,
            posterUrl, backdropUrl, rating, year, genre, duration, director, `cast`, plot, trailerUrl,
            addedAt, ageRating, isAdult, createdAt, updatedAt, syncFingerprint
        FROM movies_stage s
        WHERE s.providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM movies l WHERE l.id = s.id)
        """,
    )
    suspend fun insertMissingMoviesFromStage(providerId: String)

    @Query(
        """
        DELETE FROM movies
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM movies_stage s WHERE s.id = movies.id)
        """,
    )
    suspend fun deleteStaleMoviesFromStage(providerId: String)

    @Query(
        """
        SELECT id FROM movies
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM movies_stage s WHERE s.id = movies.id)
        """,
    )
    suspend fun removedMoviesIds(providerId: String): List<String>

    // Series (`cast` is a SQL keyword — backticked)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeriesStage(rows: List<SeriesStageEntity>)

    @Query("DELETE FROM series_stage WHERE providerId = :providerId")
    suspend fun clearSeriesStage(providerId: String)

    @Query(
        """
        DELETE FROM series
        WHERE providerId = :providerId
            AND EXISTS (
                SELECT 1 FROM series_stage s
                WHERE s.id = series.id AND s.syncFingerprint <> series.syncFingerprint
            )
        """,
    )
    suspend fun deleteChangedSeriesFromStage(providerId: String): Int

    @Query(
        """
        INSERT INTO series (
            id, providerId, categoryId, stableKey, remoteId, name, originalName, posterUrl, backdropUrl,
            rating, year, genre, director, `cast`, plot, addedAt, ageRating, isAdult, createdAt,
            updatedAt, syncFingerprint
        )
        SELECT id, providerId, categoryId, stableKey, remoteId, name, originalName, posterUrl, backdropUrl,
            rating, year, genre, director, `cast`, plot, addedAt, ageRating, isAdult, createdAt,
            updatedAt, syncFingerprint
        FROM series_stage s
        WHERE s.providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM series l WHERE l.id = s.id)
        """,
    )
    suspend fun insertMissingSeriesFromStage(providerId: String)

    @Query(
        """
        DELETE FROM series
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM series_stage s WHERE s.id = series.id)
        """,
    )
    suspend fun deleteStaleSeriesFromStage(providerId: String)

    @Query(
        """
        SELECT id FROM series
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM series_stage s WHERE s.id = series.id)
        """,
    )
    suspend fun removedSeriesIds(providerId: String): List<String>

    // Episodes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodesStage(rows: List<EpisodeStageEntity>)

    @Query("DELETE FROM episodes_stage WHERE providerId = :providerId")
    suspend fun clearEpisodesStage(providerId: String)

    @Query(
        """
        DELETE FROM episodes
        WHERE providerId = :providerId
            AND EXISTS (
                SELECT 1 FROM episodes_stage s
                WHERE s.id = episodes.id AND s.syncFingerprint <> episodes.syncFingerprint
            )
        """,
    )
    suspend fun deleteChangedEpisodesFromStage(providerId: String): Int

    @Query(
        """
        INSERT INTO episodes (
            id, providerId, seriesId, seasonId, stableKey, remoteId, episodeNumber, seasonNumber, name,
            plot, thumbnailUrl, containerExtension, duration, airDate, ageRating, isAdult, createdAt,
            updatedAt, syncFingerprint
        )
        SELECT id, providerId, seriesId, seasonId, stableKey, remoteId, episodeNumber, seasonNumber, name,
            plot, thumbnailUrl, containerExtension, duration, airDate, ageRating, isAdult, createdAt,
            updatedAt, syncFingerprint
        FROM episodes_stage s
        WHERE s.providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM episodes l WHERE l.id = s.id)
        """,
    )
    suspend fun insertMissingEpisodesFromStage(providerId: String)

    @Query(
        """
        DELETE FROM episodes
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM episodes_stage s WHERE s.id = episodes.id)
        """,
    )
    suspend fun deleteStaleEpisodesFromStage(providerId: String)

    @Query(
        """
        SELECT id FROM episodes
        WHERE providerId = :providerId
            AND NOT EXISTS (SELECT 1 FROM episodes_stage s WHERE s.id = episodes.id)
        """,
    )
    suspend fun removedEpisodesIds(providerId: String): List<String>

    // Count of staged rows with no live match = the genuinely-new rows (the "added" import count). Must be
    // read BEFORE delete-changed, which would otherwise turn changed rows into apparent new ones.
    @Query("SELECT COUNT(*) FROM channels_stage s WHERE s.providerId = :providerId AND NOT EXISTS (SELECT 1 FROM channels l WHERE l.id = s.id)")
    suspend fun countNewChannelsFromStage(providerId: String): Int

    @Query("SELECT COUNT(*) FROM movies_stage s WHERE s.providerId = :providerId AND NOT EXISTS (SELECT 1 FROM movies l WHERE l.id = s.id)")
    suspend fun countNewMoviesFromStage(providerId: String): Int

    @Query("SELECT COUNT(*) FROM series_stage s WHERE s.providerId = :providerId AND NOT EXISTS (SELECT 1 FROM series l WHERE l.id = s.id)")
    suspend fun countNewSeriesFromStage(providerId: String): Int

    @Query("SELECT COUNT(*) FROM episodes_stage s WHERE s.providerId = :providerId AND NOT EXISTS (SELECT 1 FROM episodes l WHERE l.id = s.id)")
    suspend fun countNewEpisodesFromStage(providerId: String): Int

    // Startup crash-recovery: drop any staged rows a killed import left behind (self-heals on the next
    // import too, since staging clears before use). Provider-agnostic.
    @Query("DELETE FROM channels_stage")
    suspend fun clearAllChannelsStage()

    @Query("DELETE FROM movies_stage")
    suspend fun clearAllMoviesStage()

    @Query("DELETE FROM series_stage")
    suspend fun clearAllSeriesStage()

    @Query("DELETE FROM episodes_stage")
    suspend fun clearAllEpisodesStage()

    @Query("DELETE FROM categories WHERE providerId = :providerId")
    suspend fun deleteCategoriesForProvider(providerId: String)

    @Query("DELETE FROM categories WHERE providerId = :providerId AND type = :type AND id IN (:categoryIds)")
    suspend fun deleteCategories(providerId: String, type: String, categoryIds: List<String>)

    @Query("DELETE FROM channels WHERE providerId = :providerId")
    suspend fun deleteChannelsForProvider(providerId: String)

    @Query("DELETE FROM channels WHERE providerId = :providerId AND id IN (:channelIds)")
    suspend fun deleteChannels(providerId: String, channelIds: List<String>)

    @Query("DELETE FROM movies WHERE providerId = :providerId")
    suspend fun deleteMoviesForProvider(providerId: String)

    @Query("DELETE FROM movies WHERE providerId = :providerId AND id IN (:movieIds)")
    suspend fun deleteMovies(providerId: String, movieIds: List<String>)

    @Query("DELETE FROM series WHERE providerId = :providerId")
    suspend fun deleteSeriesForProvider(providerId: String)

    @Query("DELETE FROM series WHERE providerId = :providerId AND id IN (:seriesIds)")
    suspend fun deleteSeries(providerId: String, seriesIds: List<String>)

    @Query("DELETE FROM seasons WHERE providerId = :providerId")
    suspend fun deleteSeasonsForProvider(providerId: String)

    @Query("DELETE FROM seasons WHERE providerId = :providerId AND id IN (:seasonIds)")
    suspend fun deleteSeasons(providerId: String, seasonIds: List<String>)

    @Query("DELETE FROM seasons WHERE providerId = :providerId AND seriesId IN (:seriesIds)")
    suspend fun deleteSeasonsForSeries(providerId: String, seriesIds: List<String>)

    @Query("DELETE FROM episodes WHERE providerId = :providerId")
    suspend fun deleteEpisodesForProvider(providerId: String)

    @Query("DELETE FROM episodes WHERE providerId = :providerId AND id IN (:episodeIds)")
    suspend fun deleteEpisodes(providerId: String, episodeIds: List<String>)

    @Query("DELETE FROM episodes WHERE providerId = :providerId AND seriesId IN (:seriesIds)")
    suspend fun deleteEpisodesForSeries(providerId: String, seriesIds: List<String>)

    @Query("SELECT id FROM episodes WHERE providerId = :providerId AND seriesId IN (:seriesIds)")
    suspend fun getEpisodeIdsForSeries(providerId: String, seriesIds: List<String>): List<String>

    @Transaction
    suspend fun deleteCatalogForProvider(providerId: String) {
        deleteEpisodesForProvider(providerId)
        deleteSeasonsForProvider(providerId)
        deleteSeriesForProvider(providerId)
        deleteMoviesForProvider(providerId)
        deleteChannelsForProvider(providerId)
        deleteCategoriesForProvider(providerId)
    }
}

/**
 * A channel plus its resolved [effectiveLogoUrl]: the logo to actually display, chosen at read time from
 * the channel's own playlist logo and any mapped EPG source <icon> according to the provider's
 * `logoPriority` (see [EFFECTIVE_LOGO_COLUMN]). The embedded [channel] keeps the raw playlist logoUrl.
 */
data class ChannelWithLogo(
    @Embedded val channel: ChannelEntity,
    val effectiveLogoUrl: String?,
)

/** The two raw remote logo candidates for a channel (local files are resolved App-side, not here). */
data class LogoCandidates(
    val playlistLogoUrl: String?,
    val epgIconUrl: String?,
)

/** A channel's raw remote logo candidates plus its provider's `logoPriority` order string. */
data class ChannelLogoCandidatesRow(
    val channelId: String,
    val providerLogoPriority: String?,
    val playlistLogoUrl: String?,
    val epgIconUrl: String?,
)

// The mapped EPG <icon> for channel `c` (a manual mapping preferred over an automatic one), or NULL.
// Assumes the enclosing query aliases channels AS c. Factored out so every logo query shares one definition.
private const val EPG_ICON_SUBQUERY =
    "(SELECT ec.iconUrl FROM epg_channel_mappings m " +
        "JOIN epg_channels ec ON ec.epgSourceId = m.epgSourceId AND ec.remoteId = m.epgChannelId " +
        // #33: mirror the program-winner (EpgDao.observeProgramsForChannel) — only active sources, ordered by
        // manual-then-priority — so the logo comes from the same source that wins the guide.
        "INNER JOIN epg_sources s ON s.id = m.epgSourceId " +
        "INNER JOIN provider_epg_sources pes ON pes.providerId = m.providerId AND pes.epgSourceId = m.epgSourceId " +
        "WHERE m.channelId = c.id AND s.isActive = 1 AND ec.iconUrl IS NOT NULL AND TRIM(ec.iconUrl) != '' " +
        "ORDER BY m.isManual DESC, pes.priority ASC LIMIT 1)"

// "Any remote logo" for channel `c`: the playlist's own logo, else the mapped EPG <icon>. Order-AGNOSTIC
// on purpose — it feeds only the display produceState key + the logoMissing heuristic. The user-ordered
// choice across playlist/EPG/local is resolved in Kotlin (App resolveChannelLogoModel + the worker
// prefetch) from the raw candidates (getLogoCandidates / getChannelsWithLogoUrls). Assumes channels AS c.
private const val EFFECTIVE_LOGO_COLUMN =
    "COALESCE(NULLIF(TRIM(c.logoUrl), ''), " + EPG_ICON_SUBQUERY + ")"
