package com.vivicast.tv.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "providers",
    indices = [
        Index(value = ["stableKey"], unique = true),
        Index(value = ["type"]),
        Index(value = ["name"]),
        Index(value = ["status"]),
    ],
)
data class ProviderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
    val name: String,
    val type: String,
    val sourceConfigKey: String,
    val isActive: Boolean,
    val status: String,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
    val logoPriority: String,
    val createdAt: Long,
    val updatedAt: Long,
    // Xtream account info (from player_api.php user_info), refreshed on each import. Null for M3U.
    @ColumnInfo(defaultValue = "NULL") val xtreamExpiresAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val xtreamMaxConnections: Int? = null,
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "stableKey"], unique = true),
        Index(value = ["providerId", "type"]),
        Index(value = ["providerId", "type", "name"]),
        Index(value = ["providerId", "type", "remoteId"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
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
        Index(value = ["providerId", "stableKey"], unique = true),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "name"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val categoryId: String?,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
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
        Index(value = ["providerId", "stableKey"], unique = true),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "name"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val categoryId: String?,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
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
    val ageRating: String? = null,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "stableKey"], unique = true),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "name"]),
        Index(value = ["providerId", "remoteId"], unique = true),
    ],
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val categoryId: String?,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
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
    val ageRating: String? = null,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "seasons",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "stableKey"], unique = true),
        Index(value = ["providerId", "seriesId"]),
        Index(value = ["providerId", "seriesId", "seasonNumber"], unique = true),
    ],
)
data class SeasonEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val seriesId: String,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
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
        Index(value = ["providerId", "stableKey"], unique = true),
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
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
    val remoteId: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val plot: String?,
    val thumbnailUrl: String?,
    val containerExtension: String?,
    val duration: Long?,
    val airDate: String?,
    val ageRating: String? = null,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "epg_sources",
    indices = [
        Index(value = ["stableKey"], unique = true),
        Index(value = ["name"]),
        Index(value = ["sourceConfigKey"], unique = true),
    ],
)
data class EpgSourceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
    val name: String,
    val sourceConfigKey: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
    val lastRefreshAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val lastProgramCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "epg_channels",
    indices = [
        Index(value = ["epgSourceId"]),
        Index(value = ["epgSourceId", "stableKey"], unique = true),
        Index(value = ["epgSourceId", "remoteId"], unique = true),
    ],
)
data class EpgChannelEntity(
    @PrimaryKey val id: String,
    val epgSourceId: String,
    val stableKey: String,
    val remoteId: String,
    val displayName: String,
    val iconUrl: String?,
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
        Index(value = ["epgSourceId", "epgChannelId", "stableKey"], unique = true),
        Index(value = ["channelId"]),
        Index(value = ["epgSourceId"]),
        Index(value = ["epgSourceId", "epgChannelId", "startTime", "endTime"]),
        Index(value = ["channelId", "startTime", "endTime"]),
        Index(value = ["providerId", "title"]),
    ],
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val channelId: String,
    val epgSourceId: String,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
    val epgChannelId: String,
    val title: String,
    @ColumnInfo(defaultValue = "''") val normalizedTitle: String = title,
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
    @ColumnInfo(defaultValue = "''") val channelStableKey: String = channelId,
    val epgSourceId: String,
    @ColumnInfo(defaultValue = "''") val epgSourceStableKey: String = epgSourceId,
    val epgChannelId: String,
    @ColumnInfo(defaultValue = "''") val epgChannelStableKey: String = epgChannelId,
    val isManual: Boolean,
    @ColumnInfo(defaultValue = "0.0") val confidence: Float = 0f,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
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
    @ColumnInfo(defaultValue = "''") val mediaStableKey: String = mediaId,
    @ColumnInfo(defaultValue = "0") val isPending: Boolean = false,
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
    @ColumnInfo(defaultValue = "''") val mediaStableKey: String = mediaId,
    @ColumnInfo(defaultValue = "0") val isPending: Boolean = false,
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
    @ColumnInfo(defaultValue = "''") val channelStableKey: String = channelId,
    @ColumnInfo(defaultValue = "0") val isPending: Boolean = false,
    val watchedAt: Long,
    val durationWatchedMillis: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["normalizedQuery"], unique = true),
        Index(value = ["lastUsedAt"]),
    ],
)
data class SearchHistoryEntity(
    @PrimaryKey val id: String,
    val query: String,
    @ColumnInfo(defaultValue = "''") val normalizedQuery: String = query.lowercase(),
    @ColumnInfo(defaultValue = "0") val lastUsedAt: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
)
