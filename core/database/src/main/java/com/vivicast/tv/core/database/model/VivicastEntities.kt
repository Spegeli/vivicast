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
    // Per-provider User-Agent override; NULL/blank falls back to the global User-Agent.
    @ColumnInfo(defaultValue = "NULL") val userAgent: String? = null,
    // Refresh this playlist once on app start (independent of the hourly interval).
    @ColumnInfo(defaultValue = "1") val refreshOnAppStartEnabled: Boolean = true,
    // Wall-clock millis of the last successful refresh; NULL = never. Drives interval auto-refresh.
    @ColumnInfo(defaultValue = "NULL") val lastRefreshAt: Long? = null,
    // Xtream live output format: "hls" (default) or "ts". Ignored for M3U.
    @ColumnInfo(defaultValue = "'hls'") val xtreamOutputFormat: String = "hls",
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
    // Source-appearance order from the import (the "playlist order" sort mode). Rewritten every import.
    val sortOrder: Int,
    val isHidden: Boolean,
    // User's manual group order, used only in the MANUAL sort mode; NULL = never manually placed. Preserved
    // across import (keyed by remoteId, like isHidden) so a reorder survives a refresh. See D10.
    @ColumnInfo(defaultValue = "NULL") val manualSortOrder: Int? = null,
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
    // Raw EPG channel id (M3U tvg-id / Xtream epg_channel_id) used to match this channel to an XMLTV
    // <channel id>. Kept separate from the prefixed [remoteId] identity. Null/blank = no id to match on.
    @ColumnInfo(defaultValue = "NULL") val epgChannelId: String? = null,
    val isCatchupAvailable: Boolean,
    val catchupDays: Int,
    val createdAt: Long,
    val updatedAt: Long,
    // SHA-256 over content columns only (excludes user-state + createdAt/updatedAt). Drives the staged
    // delta-merge: a live row is rewritten only when its fingerprint differs from the staged one. See
    // plans/nonblocking-db-imports.md.
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
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
    // Content-only fingerprint for the staged delta-merge (see ChannelEntity / plans/nonblocking-db-imports.md).
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
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
    // Content-only fingerprint for the staged delta-merge (see ChannelEntity / plans/nonblocking-db-imports.md).
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
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
    // Content-only fingerprint for the staged delta-merge (see ChannelEntity / plans/nonblocking-db-imports.md).
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
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
    @ColumnInfo(defaultValue = "0") val lastChannelCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val isRefreshing: Boolean = false,
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

// Per-(provider, category type) group-management settings: the sort mode and whether newly discovered
// groups default to hidden. Provider-scoped user state (no FK, matching the other entities — cleaned up
// with the catalog on provider deletion). id = "$providerId:$type". See D10.
@Entity(
    tableName = "provider_category_settings",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "type"], unique = true),
    ],
)
data class ProviderCategorySettingsEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val type: String,
    // One of "PLAYLIST" (source order, default) | "NAME" (A→Z) | "MANUAL" (user order).
    @ColumnInfo(defaultValue = "'PLAYLIST'") val sortMode: String = "PLAYLIST",
    // When a refresh discovers a new group, default it to hidden (true) or shown (false, default).
    @ColumnInfo(defaultValue = "0") val hideNewGroups: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "epgSourceId", "epgChannelId", "stableKey"], unique = true),
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
    // Content-only fingerprint for the staged delta-merge (see ChannelEntity / plans/nonblocking-db-imports.md).
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
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
