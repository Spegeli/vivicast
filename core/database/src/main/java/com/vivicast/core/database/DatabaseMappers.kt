package com.vivicast.core.database

import com.vivicast.core.database.entity.CategoryEntity
import com.vivicast.core.database.entity.ChannelEntity
import com.vivicast.core.database.entity.EpgProgramEntity
import com.vivicast.core.database.entity.EpgSourceEntity
import com.vivicast.core.database.entity.FavoriteChannelEntity
import com.vivicast.core.database.entity.MovieCategoryEntity
import com.vivicast.core.database.entity.MovieEntity
import com.vivicast.core.database.entity.MoviePlaybackProgressEntity
import com.vivicast.core.database.entity.PlaylistEntity
import com.vivicast.core.database.entity.RecentChannelEntity
import com.vivicast.core.database.entity.SeasonEntity
import com.vivicast.core.database.entity.SeriesCategoryEntity
import com.vivicast.core.database.entity.SeriesEntity
import com.vivicast.core.database.entity.EpisodeEntity
import com.vivicast.core.database.entity.EpisodePlaybackProgressEntity
import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.Episode
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.Movie
import com.vivicast.core.model.MovieCategory
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.PlaylistSourceType
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.model.Season
import com.vivicast.core.model.Series
import com.vivicast.core.model.SeriesCategory

fun PlaylistEntity.asModel(): Playlist = Playlist(
    id = id,
    name = name,
    sourceType = PlaylistSourceType.valueOf(sourceType),
    sourceUri = sourceUri,
    sourceUsername = sourceUsername,
    sourcePassword = sourcePassword,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun Playlist.asEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    sourceType = sourceType.name,
    sourceUri = sourceUri,
    sourceUsername = sourceUsername,
    sourcePassword = sourcePassword,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun CategoryEntity.asModel(): ChannelCategory = ChannelCategory(
    id = id,
    playlistId = playlistId,
    name = name,
    sortIndex = sortIndex
)

fun ChannelCategory.asEntity(): CategoryEntity = CategoryEntity(
    id = id,
    playlistId = playlistId,
    name = name,
    sortIndex = sortIndex
)

fun ChannelEntity.asModel(): Channel = Channel(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    name = name,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    tvgId = tvgId,
    tvgName = tvgName,
    catchupSupported = catchupSupported,
    sortIndex = sortIndex
)

fun Channel.asEntity(): ChannelEntity = ChannelEntity(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    name = name,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    tvgId = tvgId,
    tvgName = tvgName,
    catchupSupported = catchupSupported,
    sortIndex = sortIndex
)

fun EpgSourceEntity.asModel(): EpgSource = EpgSource(
    id = id,
    playlistId = playlistId,
    name = name,
    sourceUri = sourceUri,
    lastImportedAtEpochMillis = lastImportedAtEpochMillis
)

fun EpgProgramEntity.asModel(): EpgProgram = EpgProgram(
    id = id,
    channelId = channelId,
    title = title,
    description = description,
    startUtcEpochMillis = startUtcEpochMillis,
    endUtcEpochMillis = endUtcEpochMillis,
    iconUrl = iconUrl
)

fun FavoriteChannelEntity.asModel(): FavoriteChannel = FavoriteChannel(
    channelId = channelId,
    addedAtEpochMillis = addedAtEpochMillis
)

fun RecentChannelEntity.asModel(): RecentChannel = RecentChannel(
    channelId = channelId,
    watchedAtEpochMillis = watchedAtEpochMillis
)

fun MovieCategoryEntity.asModel(): MovieCategory = MovieCategory(
    id = id,
    playlistId = playlistId,
    name = name,
    sortIndex = sortIndex
)

fun MovieCategory.asEntity(): MovieCategoryEntity = MovieCategoryEntity(
    id = id,
    playlistId = playlistId,
    name = name,
    sortIndex = sortIndex
)

fun MovieEntity.asModel(): Movie = Movie(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    title = title,
    streamUrl = streamUrl,
    coverUrl = coverUrl,
    plot = plot,
    durationMinutes = durationMinutes,
    releaseDate = releaseDate,
    addedAtEpochSeconds = addedAtEpochSeconds,
    sortIndex = sortIndex
)

fun Movie.asEntity(): MovieEntity = MovieEntity(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    title = title,
    streamUrl = streamUrl,
    coverUrl = coverUrl,
    plot = plot,
    durationMinutes = durationMinutes,
    releaseDate = releaseDate,
    addedAtEpochSeconds = addedAtEpochSeconds,
    sortIndex = sortIndex
)

fun SeriesCategoryEntity.asModel(): SeriesCategory = SeriesCategory(
    id = id,
    playlistId = playlistId,
    name = name,
    sortIndex = sortIndex
)

fun SeriesCategory.asEntity(): SeriesCategoryEntity = SeriesCategoryEntity(
    id = id,
    playlistId = playlistId,
    name = name,
    sortIndex = sortIndex
)

fun SeriesEntity.asModel(): Series = Series(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    title = title,
    coverUrl = coverUrl,
    plot = plot,
    episodeRunTimeMinutes = episodeRunTimeMinutes,
    releaseDate = releaseDate,
    addedAtEpochSeconds = addedAtEpochSeconds,
    sortIndex = sortIndex
)

fun Series.asEntity(): SeriesEntity = SeriesEntity(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    title = title,
    coverUrl = coverUrl,
    plot = plot,
    episodeRunTimeMinutes = episodeRunTimeMinutes,
    releaseDate = releaseDate,
    addedAtEpochSeconds = addedAtEpochSeconds,
    sortIndex = sortIndex
)

fun SeasonEntity.asModel(): Season = Season(
    id = id,
    playlistId = playlistId,
    seriesId = seriesId,
    title = title,
    seasonNumber = seasonNumber,
    coverUrl = coverUrl,
    plot = plot,
    sortIndex = sortIndex
)

fun Season.asEntity(): SeasonEntity = SeasonEntity(
    id = id,
    playlistId = playlistId,
    seriesId = seriesId,
    title = title,
    seasonNumber = seasonNumber,
    coverUrl = coverUrl,
    plot = plot,
    sortIndex = sortIndex
)

fun EpisodeEntity.asModel(): Episode = Episode(
    id = id,
    playlistId = playlistId,
    seriesId = seriesId,
    seasonId = seasonId,
    title = title,
    streamUrl = streamUrl,
    episodeNumber = episodeNumber,
    plot = plot,
    durationMinutes = durationMinutes,
    coverUrl = coverUrl,
    addedAtEpochSeconds = addedAtEpochSeconds,
    sortIndex = sortIndex
)

fun Episode.asEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    playlistId = playlistId,
    seriesId = seriesId,
    seasonId = seasonId,
    title = title,
    streamUrl = streamUrl,
    episodeNumber = episodeNumber,
    plot = plot,
    durationMinutes = durationMinutes,
    coverUrl = coverUrl,
    addedAtEpochSeconds = addedAtEpochSeconds,
    sortIndex = sortIndex
)

fun MoviePlaybackProgressEntity.asModel(): MoviePlaybackProgress = MoviePlaybackProgress(
    movieId = movieId,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun MoviePlaybackProgress.asEntity(): MoviePlaybackProgressEntity = MoviePlaybackProgressEntity(
    movieId = movieId,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun EpisodePlaybackProgressEntity.asModel(): EpisodePlaybackProgress = EpisodePlaybackProgress(
    episodeId = episodeId,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun EpisodePlaybackProgress.asEntity(): EpisodePlaybackProgressEntity = EpisodePlaybackProgressEntity(
    episodeId = episodeId,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    updatedAtEpochMillis = updatedAtEpochMillis
)
