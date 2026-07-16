package com.vivicast.tv.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

// Staging tables for the chunked delta-merge import (see plans/nonblocking-db-imports.md). Rows are staged
// under the REAL providerId with final ids, merged into the live table, then cleared. Each stage entity
// mirrors its live counterpart column-for-column; only the table/class name and the indices differ (no live
// unique indices, so a stale import can be re-staged without conflicts).

@Entity(
    tableName = "channels_stage",
    indices = [
        Index(value = ["providerId"]),
    ],
)
data class ChannelStageEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val categoryId: String?,
    @ColumnInfo(defaultValue = "''") val stableKey: String = id,
    val remoteId: String,
    val channelNumber: String?,
    val name: String,
    val logoUrl: String?,
    @ColumnInfo(defaultValue = "NULL") val epgChannelId: String? = null,
    val isCatchupAvailable: Boolean,
    val catchupDays: Int,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
)

@Entity(
    tableName = "movies_stage",
    indices = [
        Index(value = ["providerId"]),
    ],
)
data class MovieStageEntity(
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
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
)

@Entity(
    tableName = "series_stage",
    indices = [
        Index(value = ["providerId"]),
    ],
)
data class SeriesStageEntity(
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
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
)

@Entity(
    tableName = "episodes_stage",
    indices = [
        Index(value = ["providerId"]),
    ],
)
data class EpisodeStageEntity(
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
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
)

@Entity(
    tableName = "epg_programs_stage",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "epgSourceId"]),
    ],
)
data class EpgProgramStageEntity(
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
    @ColumnInfo(defaultValue = "''") val syncFingerprint: String = "",
)
