package com.vivicast.core.data

import androidx.room.withTransaction
import com.vivicast.core.database.ViviCastDatabase
import com.vivicast.core.database.entity.EpisodeEntity
import com.vivicast.core.database.entity.MovieCategoryEntity
import com.vivicast.core.database.entity.MovieEntity
import com.vivicast.core.database.entity.SeasonEntity
import com.vivicast.core.database.entity.SeriesCategoryEntity
import com.vivicast.core.database.entity.SeriesEntity
import com.vivicast.core.domain.XtreamVodImportResult
import com.vivicast.core.network.NetworkResult
import com.vivicast.core.network.XtreamCategory
import com.vivicast.core.network.XtreamClient
import com.vivicast.core.network.XtreamSeriesInfo
import com.vivicast.core.model.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class XtreamVodImportUseCase(
    private val database: ViviCastDatabase,
    private val xtreamClient: XtreamClient
) {
    suspend fun importPlaylist(
        playlistId: String,
        credentials: XtreamCredentials
    ): XtreamVodImportResult = withContext(Dispatchers.IO) {
        val movieCategoriesByRemoteId = when (val response = xtreamClient.getVodCategories(credentials)) {
            is NetworkResult.Success -> response.value.associateBy { it.id }
            is NetworkResult.Failure -> emptyMap()
        }
        val seriesCategoriesByRemoteId = when (val response = xtreamClient.getSeriesCategories(credentials)) {
            is NetworkResult.Success -> response.value.associateBy { it.id }
            is NetworkResult.Failure -> emptyMap()
        }
        val movieStreams = when (val response = xtreamClient.getVodStreams(credentials)) {
            is NetworkResult.Success -> response.value
            is NetworkResult.Failure -> throw response.cause ?: IllegalStateException(response.message)
        }
        val seriesItems = when (val response = xtreamClient.getSeries(credentials)) {
            is NetworkResult.Success -> response.value
            is NetworkResult.Failure -> throw response.cause ?: IllegalStateException(response.message)
        }

        val movieCategories = buildMovieCategories(playlistId, movieCategoriesByRemoteId.values.toList())
        val movieCategoryIdByRemoteId = movieCategoriesByRemoteId.mapValues { (_, category) ->
            stableId("playlist:$playlistId:movie-category:${category.id.ifBlank { category.name.lowercase() }}")
        }
        val movies = movieStreams.mapIndexed { index, movie ->
            MovieEntity(
                id = stableId("playlist:$playlistId:movie:${movie.id}:${movie.streamUrl}"),
                playlistId = playlistId,
                categoryId = movie.categoryId?.let(movieCategoryIdByRemoteId::get),
                title = movie.name,
                streamUrl = movie.streamUrl,
                coverUrl = movie.coverUrl,
                plot = movie.plot,
                durationMinutes = movie.durationMinutes,
                releaseDate = movie.releaseDate,
                addedAtEpochSeconds = movie.addedAtEpochSeconds,
                sortIndex = index
            )
        }

        val seriesCategories = buildSeriesCategories(playlistId, seriesCategoriesByRemoteId.values.toList())
        val seriesCategoryIdByRemoteId = seriesCategoriesByRemoteId.mapValues { (_, category) ->
            stableId("playlist:$playlistId:series-category:${category.id.ifBlank { category.name.lowercase() }}")
        }
        val series = seriesItems.mapIndexed { index, item ->
            SeriesEntity(
                id = stableId("playlist:$playlistId:series:${item.id}"),
                playlistId = playlistId,
                categoryId = item.categoryId?.let(seriesCategoryIdByRemoteId::get),
                title = item.name,
                coverUrl = item.coverUrl,
                plot = item.plot,
                episodeRunTimeMinutes = item.episodeRunTimeMinutes,
                releaseDate = item.releaseDate,
                addedAtEpochSeconds = item.addedAtEpochSeconds,
                sortIndex = index
            )
        }
        val localSeriesIdByRemoteId = seriesItems.associate { item ->
            item.id to stableId("playlist:$playlistId:series:${item.id}")
        }

        val seasons = mutableListOf<SeasonEntity>()
        val episodes = mutableListOf<EpisodeEntity>()
        var failedSeriesDetailCount = 0
        seriesItems.forEach { item ->
            when (val response = xtreamClient.getSeriesInfo(credentials, item.id)) {
                is NetworkResult.Success -> {
                    val localSeriesId = localSeriesIdByRemoteId.getValue(item.id)
                    val imported = buildSeriesChildren(
                        playlistId = playlistId,
                        localSeriesId = localSeriesId,
                        info = response.value
                    )
                    seasons += imported.seasons
                    episodes += imported.episodes
                }

                is NetworkResult.Failure -> failedSeriesDetailCount += 1
            }
        }

        database.withTransaction {
            database.vodDao().deleteEpisodesForPlaylist(playlistId)
            database.vodDao().deleteSeasonsForPlaylist(playlistId)
            database.vodDao().deleteSeriesForPlaylist(playlistId)
            database.vodDao().deleteSeriesCategoriesForPlaylist(playlistId)
            database.vodDao().deleteMoviesForPlaylist(playlistId)
            database.vodDao().deleteMovieCategoriesForPlaylist(playlistId)

            if (movieCategories.isNotEmpty()) {
                database.vodDao().upsertMovieCategories(movieCategories)
            }
            if (movies.isNotEmpty()) {
                database.vodDao().upsertMovies(movies)
            }
            if (seriesCategories.isNotEmpty()) {
                database.vodDao().upsertSeriesCategories(seriesCategories)
            }
            if (series.isNotEmpty()) {
                database.vodDao().upsertSeries(series)
            }
            if (seasons.isNotEmpty()) {
                database.vodDao().upsertSeasons(seasons)
            }
            if (episodes.isNotEmpty()) {
                database.vodDao().upsertEpisodes(episodes)
            }
        }

        XtreamVodImportResult(
            movieCategoryCount = movieCategories.size,
            movieCount = movies.size,
            seriesCategoryCount = seriesCategories.size,
            seriesCount = series.size,
            seasonCount = seasons.size,
            episodeCount = episodes.size,
            failedSeriesDetailCount = failedSeriesDetailCount
        )
    }

    private fun buildMovieCategories(
        playlistId: String,
        remoteCategories: List<XtreamCategory>
    ): List<MovieCategoryEntity> {
        return remoteCategories.mapIndexed { index, category ->
            MovieCategoryEntity(
                id = stableId("playlist:$playlistId:movie-category:${category.id.ifBlank { category.name.lowercase() }}"),
                playlistId = playlistId,
                name = category.name.ifBlank { "Movies" },
                sortIndex = index
            )
        }
    }

    private fun buildSeriesCategories(
        playlistId: String,
        remoteCategories: List<XtreamCategory>
    ): List<SeriesCategoryEntity> {
        return remoteCategories.mapIndexed { index, category ->
            SeriesCategoryEntity(
                id = stableId("playlist:$playlistId:series-category:${category.id.ifBlank { category.name.lowercase() }}"),
                playlistId = playlistId,
                name = category.name.ifBlank { "Series" },
                sortIndex = index
            )
        }
    }

    private fun buildSeriesChildren(
        playlistId: String,
        localSeriesId: String,
        info: XtreamSeriesInfo
    ): ImportedSeriesChildren {
        val explicitSeasons = info.seasons.mapIndexed { index, season ->
            SeasonEntity(
                id = stableId("playlist:$playlistId:series:$localSeriesId:season:${season.id}:${season.seasonNumber}"),
                playlistId = playlistId,
                seriesId = localSeriesId,
                title = season.name,
                seasonNumber = season.seasonNumber,
                coverUrl = season.coverUrl,
                plot = season.plot,
                sortIndex = index
            )
        }
        val explicitSeasonIdByRemoteId = info.seasons.associate { season ->
            season.id to stableId("playlist:$playlistId:series:$localSeriesId:season:${season.id}:${season.seasonNumber}")
        }
        val fallbackSeasonPairs = info.episodes
            .map { it.seasonId }
            .distinct()
            .filterNot(explicitSeasonIdByRemoteId::containsKey)
            .mapIndexed { offset, remoteSeasonId ->
                val seasonNumber = remoteSeasonId.substringAfterLast('-').toIntOrNull()
                    ?: (explicitSeasons.size + offset + 1)
                remoteSeasonId to SeasonEntity(
                    id = stableId("playlist:$playlistId:series:$localSeriesId:season:$remoteSeasonId:$seasonNumber"),
                    playlistId = playlistId,
                    seriesId = localSeriesId,
                    title = "Season $seasonNumber",
                    seasonNumber = seasonNumber,
                    coverUrl = null,
                    plot = null,
                    sortIndex = explicitSeasons.size + offset
                )
            }
        val seasons = (explicitSeasons + fallbackSeasonPairs.map { it.second })
            .sortedWith(compareBy(SeasonEntity::sortIndex, SeasonEntity::seasonNumber))
        val localSeasonIdByRemoteId = explicitSeasonIdByRemoteId + fallbackSeasonPairs.associate { (remoteSeasonId, season) ->
            remoteSeasonId to season.id
        }
        val episodeSortBySeason = mutableMapOf<String, Int>()
        val episodes = info.episodes.map { episode ->
            val localSeasonId = localSeasonIdByRemoteId[episode.seasonId]
                ?: stableId("playlist:$playlistId:series:$localSeriesId:season:${episode.seasonId}:0")
            val sortIndex = episodeSortBySeason.getOrDefault(localSeasonId, 0)
            episodeSortBySeason[localSeasonId] = sortIndex + 1
            EpisodeEntity(
                id = stableId("playlist:$playlistId:series:$localSeriesId:season:$localSeasonId:episode:${episode.id}:${episode.episodeNumber}"),
                playlistId = playlistId,
                seriesId = localSeriesId,
                seasonId = localSeasonId,
                title = episode.title,
                streamUrl = episode.streamUrl,
                episodeNumber = episode.episodeNumber,
                plot = episode.plot,
                durationMinutes = episode.durationMinutes,
                coverUrl = episode.coverUrl,
                addedAtEpochSeconds = episode.addedAtEpochSeconds,
                sortIndex = sortIndex
            )
        }
        return ImportedSeriesChildren(seasons = seasons, episodes = episodes)
    }

    private fun stableId(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

private data class ImportedSeriesChildren(
    val seasons: List<SeasonEntity>,
    val episodes: List<EpisodeEntity>
)
