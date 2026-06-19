package com.vivicast.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.core.database.entity.EpisodeEntity
import com.vivicast.core.database.entity.EpisodePlaybackProgressEntity
import com.vivicast.core.database.entity.MovieCategoryEntity
import com.vivicast.core.database.entity.MovieEntity
import com.vivicast.core.database.entity.MoviePlaybackProgressEntity
import com.vivicast.core.database.entity.SeasonEntity
import com.vivicast.core.database.entity.SeriesCategoryEntity
import com.vivicast.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VodDao {
    @Query("SELECT * FROM movie_categories WHERE playlistId = :playlistId ORDER BY sortIndex, name")
    fun observeMovieCategories(playlistId: String): Flow<List<MovieCategoryEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY sortIndex, title")
    fun observeMovies(playlistId: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY sortIndex, title")
    fun observeAllMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :movieId LIMIT 1")
    suspend fun getMovie(movieId: String): MovieEntity?

    @Query("SELECT * FROM series_categories WHERE playlistId = :playlistId ORDER BY sortIndex, name")
    fun observeSeriesCategories(playlistId: String): Flow<List<SeriesCategoryEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY sortIndex, title")
    fun observeSeries(playlistId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series ORDER BY sortIndex, title")
    fun observeAllSeries(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    suspend fun getSeries(seriesId: String): SeriesEntity?

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY sortIndex, seasonNumber")
    fun observeSeasons(seriesId: String): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY sortIndex, episodeNumber")
    fun observeEpisodes(seasonId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisode(episodeId: String): EpisodeEntity?

    @Query("SELECT * FROM movie_playback_progress WHERE movieId = :movieId LIMIT 1")
    fun observeMovieProgress(movieId: String): Flow<MoviePlaybackProgressEntity?>

    @Query("SELECT * FROM episode_playback_progress WHERE episodeId = :episodeId LIMIT 1")
    fun observeEpisodeProgress(episodeId: String): Flow<EpisodePlaybackProgressEntity?>

    @Upsert
    suspend fun upsertMovieCategories(categories: List<MovieCategoryEntity>)

    @Upsert
    suspend fun upsertMovies(movies: List<MovieEntity>)

    @Upsert
    suspend fun upsertSeriesCategories(categories: List<SeriesCategoryEntity>)

    @Upsert
    suspend fun upsertSeries(series: List<SeriesEntity>)

    @Upsert
    suspend fun upsertSeasons(seasons: List<SeasonEntity>)

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Upsert
    suspend fun upsertMovieProgress(progress: MoviePlaybackProgressEntity)

    @Upsert
    suspend fun upsertEpisodeProgress(progress: EpisodePlaybackProgressEntity)

    @Query("DELETE FROM movie_categories WHERE playlistId = :playlistId")
    suspend fun deleteMovieCategoriesForPlaylist(playlistId: String)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteMoviesForPlaylist(playlistId: String)

    @Query("DELETE FROM series_categories WHERE playlistId = :playlistId")
    suspend fun deleteSeriesCategoriesForPlaylist(playlistId: String)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteSeriesForPlaylist(playlistId: String)

    @Query("DELETE FROM seasons WHERE playlistId = :playlistId")
    suspend fun deleteSeasonsForPlaylist(playlistId: String)

    @Query("DELETE FROM episodes WHERE playlistId = :playlistId")
    suspend fun deleteEpisodesForPlaylist(playlistId: String)
}
