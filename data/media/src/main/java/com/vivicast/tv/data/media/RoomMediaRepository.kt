package com.vivicast.tv.data.media

import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.SearchResults
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMediaRepository(
    database: VivicastDatabase,
) : MediaRepository {
    private val catalogDao = database.catalogDao()
    private val epgDao = database.epgDao()

    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        catalogDao.observeVisibleCategories(providerId, type.storageValue).map { categories ->
            categories.map { it.toDomain() }
        }

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> =
        catalogDao.observeChannels(providerId, categoryId).map { channels ->
            channels.map { it.toDomain() }
        }

    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> =
        catalogDao.observeMovies(providerId, categoryId).map { movies ->
            movies.map { it.toDomain() }
        }

    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> =
        catalogDao.observeSeries(providerId, categoryId).map { series ->
            series.map { it.toDomain() }
        }

    override fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>> =
        catalogDao.observeSeasons(providerId, seriesId).map { seasons ->
            seasons.map { it.toDomain() }
        }

    override fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>> =
        catalogDao.observeEpisodes(providerId, seasonId).map { episodes ->
            episodes.map { it.toDomain() }
        }

    override suspend fun search(query: String, limitPerType: Int): SearchResults {
        val trimmed = query.trim()
        if (trimmed.isBlank() || limitPerType <= 0) {
            return SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
        }

        return SearchResults(
            channels = catalogDao.searchChannels(trimmed, limitPerType).map { it.toDomain() },
            movies = catalogDao.searchMovies(trimmed, limitPerType).map { it.toDomain() },
            series = catalogDao.searchSeries(trimmed, limitPerType).map { it.toDomain() },
            epgPrograms = epgDao.searchPrograms(trimmed, limitPerType).map { it.toDomain() },
        )
    }
}

private val CategoryType.storageValue: String
    get() = when (this) {
        CategoryType.LiveTv -> "LIVE"
        CategoryType.Movies -> "MOVIE"
        CategoryType.Series -> "SERIES"
    }

private fun String.toCategoryType(): CategoryType =
    when (this) {
        "LIVE" -> CategoryType.LiveTv
        "MOVIE" -> CategoryType.Movies
        "SERIES" -> CategoryType.Series
        else -> CategoryType.LiveTv
    }

private fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        providerId = providerId,
        type = type.toCategoryType(),
        remoteId = remoteId,
        name = name,
        sortOrder = sortOrder,
        isHidden = isHidden,
    )

private fun ChannelEntity.toDomain(): Channel =
    Channel(
        id = id,
        providerId = providerId,
        categoryId = categoryId,
        remoteId = remoteId,
        channelNumber = channelNumber,
        name = name,
        logoUrl = logoUrl,
        isCatchupAvailable = isCatchupAvailable,
        catchupDays = catchupDays,
    )

private fun MovieEntity.toDomain(): Movie =
    Movie(
        id = id,
        providerId = providerId,
        categoryId = categoryId,
        remoteId = remoteId,
        name = name,
        originalName = originalName,
        containerExtension = containerExtension,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        rating = rating,
        year = year,
        genre = genre,
        duration = duration,
        director = director,
        cast = cast,
        plot = plot,
        trailerUrl = trailerUrl,
        addedAt = addedAt,
    )

private fun SeriesEntity.toDomain(): Series =
    Series(
        id = id,
        providerId = providerId,
        categoryId = categoryId,
        remoteId = remoteId,
        name = name,
        originalName = originalName,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        rating = rating,
        year = year,
        genre = genre,
        director = director,
        cast = cast,
        plot = plot,
        addedAt = addedAt,
    )

private fun SeasonEntity.toDomain(): Season =
    Season(
        id = id,
        providerId = providerId,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        name = name,
        posterUrl = posterUrl,
    )

private fun EpisodeEntity.toDomain(): Episode =
    Episode(
        id = id,
        providerId = providerId,
        seriesId = seriesId,
        seasonId = seasonId,
        remoteId = remoteId,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        name = name,
        plot = plot,
        thumbnailUrl = thumbnailUrl,
        containerExtension = containerExtension,
        duration = duration,
        airDate = airDate,
    )

private fun EpgProgramEntity.toDomain(): EpgProgram =
    EpgProgram(
        id = id,
        providerId = providerId,
        channelId = channelId,
        epgSourceId = epgSourceId,
        externalChannelId = externalChannelId,
        title = title,
        subtitle = subtitle,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        iconUrl = iconUrl,
        isCatchupAvailable = isCatchupAvailable,
    )
