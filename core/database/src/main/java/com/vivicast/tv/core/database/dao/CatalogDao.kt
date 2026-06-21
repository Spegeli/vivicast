package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity
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
        """
        SELECT * FROM channels
        WHERE providerId = :providerId AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY COALESCE(channelNumber, ''), name COLLATE NOCASE
        """,
    )
    fun observeChannels(providerId: String, categoryId: String?): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM categories WHERE providerId = :providerId AND type = :type")
    suspend fun getCategories(providerId: String, type: String): List<CategoryEntity>

    @Query("SELECT * FROM channels WHERE providerId = :providerId")
    suspend fun getChannels(providerId: String): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM channels
        WHERE logoUrl IS NOT NULL AND TRIM(logoUrl) != ''
        ORDER BY providerId, name COLLATE NOCASE
        """,
    )
    suspend fun getChannelsWithLogoUrls(): List<ChannelEntity>

    @Query("SELECT * FROM movies WHERE providerId = :providerId")
    suspend fun getMovies(providerId: String): List<MovieEntity>

    @Query("SELECT * FROM series WHERE providerId = :providerId")
    suspend fun getSeries(providerId: String): List<SeriesEntity>

    @Query("SELECT * FROM seasons WHERE providerId = :providerId")
    suspend fun getSeasons(providerId: String): List<SeasonEntity>

    @Query("SELECT * FROM episodes WHERE providerId = :providerId")
    suspend fun getEpisodes(providerId: String): List<EpisodeEntity>

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
        SELECT * FROM series
        WHERE providerId = :providerId AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeSeries(providerId: String, categoryId: String?): Flow<List<SeriesEntity>>

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

    @Query("DELETE FROM episodes WHERE providerId = :providerId")
    suspend fun deleteEpisodesForProvider(providerId: String)

    @Query("DELETE FROM episodes WHERE providerId = :providerId AND id IN (:episodeIds)")
    suspend fun deleteEpisodes(providerId: String, episodeIds: List<String>)

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
