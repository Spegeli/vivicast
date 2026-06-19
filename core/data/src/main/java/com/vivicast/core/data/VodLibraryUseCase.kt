package com.vivicast.core.data

import com.vivicast.core.database.ViviCastDatabase
import com.vivicast.core.database.asModel
import com.vivicast.core.database.asEntity
import com.vivicast.core.domain.VodRepository
import com.vivicast.core.model.Episode
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.Movie
import com.vivicast.core.model.MovieCategory
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.Season
import com.vivicast.core.model.Series
import com.vivicast.core.model.SeriesCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VodLibraryUseCase(
    private val database: ViviCastDatabase
) : VodRepository {
    override fun observeMovieCategories(playlistId: String): Flow<List<MovieCategory>> {
        return database.vodDao().observeMovieCategories(playlistId)
            .map { categories -> categories.map { it.asModel() } }
    }

    override fun observeMovies(playlistId: String): Flow<List<Movie>> {
        return database.vodDao().observeMovies(playlistId)
            .map { movies -> movies.map { it.asModel() } }
    }

    override fun observeAllMovies(): Flow<List<Movie>> {
        return database.vodDao().observeAllMovies()
            .map { movies -> movies.map { it.asModel() } }
    }

    override fun observeSeriesCategories(playlistId: String): Flow<List<SeriesCategory>> {
        return database.vodDao().observeSeriesCategories(playlistId)
            .map { categories -> categories.map { it.asModel() } }
    }

    override fun observeSeries(playlistId: String): Flow<List<Series>> {
        return database.vodDao().observeSeries(playlistId)
            .map { series -> series.map { it.asModel() } }
    }

    override fun observeAllSeries(): Flow<List<Series>> {
        return database.vodDao().observeAllSeries()
            .map { series -> series.map { it.asModel() } }
    }

    override fun observeSeasons(seriesId: String): Flow<List<Season>> {
        return database.vodDao().observeSeasons(seriesId)
            .map { seasons -> seasons.map { it.asModel() } }
    }

    override fun observeEpisodes(seasonId: String): Flow<List<Episode>> {
        return database.vodDao().observeEpisodes(seasonId)
            .map { episodes -> episodes.map { it.asModel() } }
    }

    override fun observeMovieProgress(movieId: String): Flow<MoviePlaybackProgress?> {
        return database.vodDao().observeMovieProgress(movieId)
            .map { it?.asModel() }
    }

    override fun observeEpisodeProgress(episodeId: String): Flow<EpisodePlaybackProgress?> {
        return database.vodDao().observeEpisodeProgress(episodeId)
            .map { it?.asModel() }
    }

    override suspend fun saveMovieProgress(progress: MoviePlaybackProgress) {
        database.vodDao().upsertMovieProgress(progress.asEntity())
    }

    override suspend fun saveEpisodeProgress(progress: EpisodePlaybackProgress) {
        database.vodDao().upsertEpisodeProgress(progress.asEntity())
    }
}
