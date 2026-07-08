package com.vivicast.tv.data.media

import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.dao.ChannelWithLogo
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.SearchHistoryEntity
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
import java.util.Locale

class RoomMediaRepository(
    database: VivicastDatabase,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : MediaRepository {
    private val catalogDao = database.catalogDao()
    private val searchDao = database.searchDao()
    private val androidTvSearchDao = database.androidTvSearchDao()

    override fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>> =
        catalogDao.observeVisibleCategories(providerId, type.storageValue).map { categories ->
            categories.map { it.toDomain() }
        }

    override fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>> =
        catalogDao.observeChannels(providerId, categoryId).map { channels ->
            channels.map { it.toDomain() }
        }

    override fun observeChannelsPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<Channel>> =
        catalogDao.observeChannelsPage(
            providerId = providerId,
            categoryId = categoryId,
            limit = limit.coerceAtLeast(0),
            offset = offset.coerceAtLeast(0),
        ).map { channels ->
            channels.map { it.toDomain() }
        }

    override fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>> =
        catalogDao.observeMovies(providerId, categoryId).map { movies ->
            movies.map { it.toDomain() }
        }

    override fun observeMoviesPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<Movie>> =
        catalogDao.observeMoviesPage(
            providerId = providerId,
            categoryId = categoryId,
            limit = limit.coerceAtLeast(0),
            offset = offset.coerceAtLeast(0),
        ).map { movies ->
            movies.map { it.toDomain() }
        }

    override fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>> =
        catalogDao.observeSeries(providerId, categoryId).map { series ->
            series.map { it.toDomain() }
        }

    override fun observeSeriesPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<Series>> =
        catalogDao.observeSeriesPage(
            providerId = providerId,
            categoryId = categoryId,
            limit = limit.coerceAtLeast(0),
            offset = offset.coerceAtLeast(0),
        ).map { series ->
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

    override suspend fun getChannel(providerId: String, channelId: String): Channel? =
        catalogDao.getChannel(providerId, channelId)?.toDomain()

    override suspend fun getChannelByStableKeys(providerStableKey: String, channelStableKey: String): Channel? =
        catalogDao.getChannelByStableKeys(providerStableKey, channelStableKey)?.toDomain()

    override suspend fun getMovie(providerId: String, movieId: String): Movie? =
        catalogDao.getMovie(providerId, movieId)?.toDomain()

    override suspend fun getMovieByStableKeys(providerStableKey: String, movieStableKey: String): Movie? =
        catalogDao.getMovieByStableKeys(providerStableKey, movieStableKey)?.toDomain()

    override suspend fun getSeries(providerId: String, seriesId: String): Series? =
        catalogDao.getSeries(providerId, seriesId)?.toDomain()

    override suspend fun getSeriesByStableKeys(providerStableKey: String, seriesStableKey: String): Series? =
        catalogDao.getSeriesByStableKeys(providerStableKey, seriesStableKey)?.toDomain()

    override suspend fun getEpisode(providerId: String, episodeId: String): Episode? =
        catalogDao.getEpisode(providerId, episodeId)?.toDomain()

    override suspend fun getEpisodeByStableKeys(providerStableKey: String, episodeStableKey: String): Episode? =
        catalogDao.getEpisodeByStableKeys(providerStableKey, episodeStableKey)?.toDomain()

    override suspend fun getNextEpisode(episode: Episode): Episode? =
        catalogDao.getNextEpisode(
            providerId = episode.providerId,
            seriesId = episode.seriesId,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
        )?.toDomain()

    override suspend fun search(query: String, limitPerType: Int): SearchResults {
        val ftsQuery = query.toFtsPrefixQuery()
        if (ftsQuery.isBlank() || limitPerType <= 0) {
            return SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
        }
        val includeEpg = query.normalizedSearchQuery().filterNot { it.isWhitespace() }.length >= EPG_SEARCH_MIN_LENGTH

        return SearchResults(
            channels = searchDao.searchChannels(ftsQuery, limitPerType).map { it.toDomain() },
            movies = searchDao.searchMovies(ftsQuery, limitPerType).map { it.toDomain() },
            series = searchDao.searchSeries(ftsQuery, limitPerType).map { it.toDomain() },
            epgPrograms = if (includeEpg) {
                searchDao.searchEpg(ftsQuery, limitPerType).map { it.toDomain() }
            } else {
                emptyList()
            },
        )
    }

    override fun observeSearchHistory(limit: Int): Flow<List<String>> =
        searchDao.observeSearchHistory(limit.coerceAtLeast(0)).map { entries ->
            entries.map { it.query }
        }

    override suspend fun addSearchHistory(query: String) {
        val cleanedQuery = query.cleanSearchQuery()
        val normalizedQuery = cleanedQuery.normalizedSearchQuery()
        if (normalizedQuery.isBlank()) return

        val now = nowProvider()
        val existing = searchDao.getSearchHistory(normalizedQuery)
        searchDao.upsertSearchHistory(
            SearchHistoryEntity(
                id = existing?.id ?: "search:$normalizedQuery",
                query = cleanedQuery,
                normalizedQuery = normalizedQuery,
                lastUsedAt = now,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        searchDao.trimSearchHistory(MAX_SEARCH_HISTORY)
    }

    override suspend fun deleteSearchHistory(query: String) {
        searchDao.deleteSearchHistory(query.normalizedSearchQuery())
    }

    override suspend fun clearSearchHistory() {
        searchDao.clearSearchHistory()
    }

    override suspend fun searchAndroidTvSuggestions(
        query: String,
        limit: Int,
        protectMovies: Boolean,
        protectSeries: Boolean,
        protectAdultContent: Boolean,
    ): List<AndroidTvSearchSuggestion> {
        val cleanedLimit = limit.coerceIn(1, MAX_ANDROID_TV_SEARCH_RESULTS)
        return androidTvSearchDao.searchEntries(
            queryPattern = query.likePattern(),
            prefixPattern = query.trim().escapeLike() + "%",
            protectMovies = protectMovies,
            protectSeries = protectSeries,
            protectAdultContent = protectAdultContent,
            limit = cleanedLimit,
        ).map { entry ->
            AndroidTvSearchSuggestion(
                mediaType = entry.mediaType,
                title = entry.title,
                subtitle = entry.subtitle,
                imageUrl = entry.imageUrl,
                deepLink = entry.deepLink,
            )
        }
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
        stableKey = stableKey,
        type = type.toCategoryType(),
        remoteId = remoteId,
        name = name,
        sortOrder = sortOrder,
        isHidden = isHidden,
    )

private fun ChannelWithLogo.toDomain(): Channel =
    channel.toDomain().copy(logoUrl = effectiveLogoUrl)

private fun ChannelEntity.toDomain(): Channel =
    Channel(
        id = id,
        providerId = providerId,
        categoryId = categoryId,
        stableKey = stableKey,
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
        stableKey = stableKey,
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
        ageRating = ageRating,
        isAdult = isAdult,
    )

private fun SeriesEntity.toDomain(): Series =
    Series(
        id = id,
        providerId = providerId,
        categoryId = categoryId,
        stableKey = stableKey,
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
        ageRating = ageRating,
        isAdult = isAdult,
    )

private fun SeasonEntity.toDomain(): Season =
    Season(
        id = id,
        providerId = providerId,
        seriesId = seriesId,
        stableKey = stableKey,
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
        stableKey = stableKey,
        remoteId = remoteId,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        name = name,
        plot = plot,
        thumbnailUrl = thumbnailUrl,
        containerExtension = containerExtension,
        duration = duration,
        airDate = airDate,
        ageRating = ageRating,
        isAdult = isAdult,
    )

private fun EpgProgramEntity.toDomain(): EpgProgram =
    EpgProgram(
        id = id,
        providerId = providerId,
        channelId = channelId,
        epgSourceId = epgSourceId,
        stableKey = stableKey,
        epgChannelId = epgChannelId,
        title = title,
        normalizedTitle = normalizedTitle,
        subtitle = subtitle,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        iconUrl = iconUrl,
        isCatchupAvailable = isCatchupAvailable,
    )

private fun String.cleanSearchQuery(): String =
    trim().replace(Regex("\\s+"), " ")

private fun String.normalizedSearchQuery(): String =
    cleanSearchQuery().lowercase(Locale.ROOT)

private fun String.toFtsPrefixQuery(): String =
    normalizedSearchQuery()
        .split(' ')
        .map { token -> token.filter { it.isLetterOrDigit() } }
        .filter { it.isNotBlank() }
        .joinToString(separator = " ") { token -> "$token*" }

private fun String.likePattern(): String {
    val query = trim()
    return if (query.isBlank()) "%%" else "%${query.escapeLike()}%"
}

private fun String.escapeLike(): String =
    replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

private const val EPG_SEARCH_MIN_LENGTH = 3

private const val MAX_SEARCH_HISTORY = 20

private const val MAX_ANDROID_TV_SEARCH_RESULTS = 50
