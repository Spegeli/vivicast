package com.vivicast.tv.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "providers",
    indices = [
        Index(value = ["type"]),
        Index(value = ["name"]),
        Index(value = ["status"]),
    ],
)
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val credentialsKey: String,
    val isActive: Boolean,
    val status: String,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
    val logoPriority: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "type"]),
        Index(value = ["providerId", "type", "name"]),
        Index(value = ["providerId", "type", "remoteId"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val type: String,
    val remoteId: String,
    val name: String,
    val sortOrder: Int,
    val isHidden: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "name"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val categoryId: String?,
    val remoteId: String,
    val channelNumber: String?,
    val name: String,
    val logoUrl: String?,
    val isCatchupAvailable: Boolean,
    val catchupDays: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "name"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val categoryId: String?,
    val remoteId: String,
    val name: String,
    val originalName: String?,
    val containerExtension: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: String?,
    val year: String?,
    val genre: String?,
    val duration: Long?,
    val director: String?,
    val cast: String?,
    val plot: String?,
    val trailerUrl: String?,
    val addedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "name"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val categoryId: String?,
    val remoteId: String,
    val name: String,
    val originalName: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: String?,
    val year: String?,
    val genre: String?,
    val director: String?,
    val cast: String?,
    val plot: String?,
    val addedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "seasons",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "seriesId"]),
        Index(value = ["providerId", "seriesId", "seasonNumber"], unique = true),
    ],
)
data class SeasonEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val seriesId: String,
    val seasonNumber: Int,
    val name: String,
    val posterUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "seriesId"]),
        Index(value = ["providerId", "seasonId"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val seriesId: String,
    val seasonId: String,
    val remoteId: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val plot: String?,
    val thumbnailUrl: String?,
    val containerExtension: String?,
    val duration: Long?,
    val airDate: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "epg_sources",
    indices = [
        Index(value = ["name"]),
        Index(value = ["urlKey"], unique = true),
    ],
)
data class EpgSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val urlKey: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "provider_epg_sources",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["epgSourceId"]),
        Index(value = ["providerId", "priority"], unique = true),
        Index(value = ["providerId", "epgSourceId"], unique = true),
    ],
)
data class ProviderEpgSourceEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val epgSourceId: String,
    val priority: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["channelId"]),
        Index(value = ["epgSourceId"]),
        Index(value = ["channelId", "startTime", "endTime"]),
        Index(value = ["providerId", "title"]),
    ],
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val channelId: String,
    val epgSourceId: String,
    val externalChannelId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val category: String?,
    val iconUrl: String?,
    val isCatchupAvailable: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "epg_channel_mappings",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "channelId"]),
        Index(value = ["providerId", "channelId", "epgSourceId"], unique = true),
    ],
)
data class EpgChannelMappingEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val channelId: String,
    val epgSourceId: String,
    val epgChannelId: String,
    val isManual: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "mediaType"]),
        Index(value = ["providerId", "mediaType", "mediaId"], unique = true),
    ],
)
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val mediaType: String,
    val mediaId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playback_progress",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "mediaType", "mediaId"], unique = true),
        Index(value = ["providerId", "lastWatchedAt"]),
        Index(value = ["providerId", "isCompleted"]),
    ],
)
data class PlaybackProgressEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val mediaType: String,
    val mediaId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val progressPercent: Int,
    val isCompleted: Boolean,
    val lastWatchedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "channel_history",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "channelId"], unique = true),
        Index(value = ["providerId", "watchedAt"]),
    ],
)
data class ChannelHistoryEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val channelId: String,
    val watchedAt: Long,
    val durationWatchedMillis: Long,
    val updatedAt: Long,
)

