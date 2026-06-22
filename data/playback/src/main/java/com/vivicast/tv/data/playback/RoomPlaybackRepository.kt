package com.vivicast.tv.data.playback

import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPlaybackRepository(
    database: VivicastDatabase,
) : PlaybackRepository {
    private val playbackDao = database.playbackDao()

    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> =
        playbackDao.observeContinueWatching(providerId).map { progress ->
            progress.map { it.toDomain() }
        }

    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> =
        playbackDao.observeRecentChannels(providerId, limit).map { history ->
            history.map { it.toDomain() }
        }

    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        playbackDao.getProgress(providerId, mediaType.storageValue, mediaId)?.toDomain()

    override suspend fun saveProgress(progress: PlaybackProgress) {
        playbackDao.upsertProgress(progress.toEntity())
    }

    override suspend fun saveChannelHistory(history: ChannelHistory) {
        playbackDao.upsertChannelHistory(history.toEntity())
    }

    override suspend fun clearProviderPlayback(providerId: String) {
        playbackDao.deleteProgressForProvider(providerId)
        playbackDao.deleteHistoryForProvider(providerId)
    }
}

private fun PlaybackProgressEntity.toDomain(): PlaybackProgress =
    PlaybackProgress(
        id = id,
        providerId = providerId,
        mediaType = mediaType.toMediaType(),
        mediaId = mediaId,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        progressPercent = progressPercent,
        isCompleted = isCompleted,
        lastWatchedAt = lastWatchedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun PlaybackProgress.toEntity(): PlaybackProgressEntity =
    PlaybackProgressEntity(
        id = id,
        providerId = providerId,
        mediaType = mediaType.storageValue,
        mediaId = mediaId,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        progressPercent = progressPercent,
        isCompleted = isCompleted,
        lastWatchedAt = lastWatchedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun ChannelHistoryEntity.toDomain(): ChannelHistory =
    ChannelHistory(
        id = id,
        providerId = providerId,
        channelId = channelId,
        watchedAt = watchedAt,
        durationWatchedMillis = durationWatchedMillis,
        updatedAt = updatedAt,
    )

private fun ChannelHistory.toEntity(): ChannelHistoryEntity =
    ChannelHistoryEntity(
        id = id,
        providerId = providerId,
        channelId = channelId,
        watchedAt = watchedAt,
        durationWatchedMillis = durationWatchedMillis,
        updatedAt = updatedAt,
    )

private val MediaType.storageValue: String
    get() = when (this) {
        MediaType.Channel -> "CHANNEL"
        MediaType.Movie -> "MOVIE"
        MediaType.Series -> "SERIES"
        MediaType.Episode -> "EPISODE"
    }

private fun String.toMediaType(): MediaType =
    when (this) {
        "CHANNEL" -> MediaType.Channel
        "MOVIE" -> MediaType.Movie
        "SERIES" -> MediaType.Series
        "EPISODE" -> MediaType.Episode
        else -> MediaType.Channel
    }
