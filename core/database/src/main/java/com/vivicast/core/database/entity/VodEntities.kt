package com.vivicast.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movie_categories",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index(value = ["playlistId", "name"], unique = true)]
)
data class MovieCategoryEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val name: String,
    val sortIndex: Int
)

@Entity(
    tableName = "movies",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("categoryId"),
        Index(value = ["playlistId", "title"])
    ]
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val categoryId: String?,
    val title: String,
    val streamUrl: String,
    val coverUrl: String?,
    val plot: String?,
    val durationMinutes: Int?,
    val releaseDate: String?,
    val addedAtEpochSeconds: Long?,
    val sortIndex: Int
)

@Entity(
    tableName = "series_categories",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index(value = ["playlistId", "name"], unique = true)]
)
data class SeriesCategoryEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val name: String,
    val sortIndex: Int
)

@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("categoryId"),
        Index(value = ["playlistId", "title"])
    ]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val categoryId: String?,
    val title: String,
    val coverUrl: String?,
    val plot: String?,
    val episodeRunTimeMinutes: Int?,
    val releaseDate: String?,
    val addedAtEpochSeconds: Long?,
    val sortIndex: Int
)

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("seriesId"),
        Index(value = ["seriesId", "seasonNumber"], unique = true)
    ]
)
data class SeasonEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val seriesId: String,
    val title: String,
    val seasonNumber: Int,
    val coverUrl: String?,
    val plot: String?,
    val sortIndex: Int
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["id"],
            childColumns = ["seasonId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("seriesId"),
        Index("seasonId"),
        Index(value = ["seasonId", "episodeNumber"], unique = true)
    ]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val seriesId: String,
    val seasonId: String,
    val title: String,
    val streamUrl: String,
    val episodeNumber: Int,
    val plot: String?,
    val durationMinutes: Int?,
    val coverUrl: String?,
    val addedAtEpochSeconds: Long?,
    val sortIndex: Int
)

@Entity(tableName = "movie_playback_progress", indices = [Index("updatedAtEpochMillis")])
data class MoviePlaybackProgressEntity(
    @PrimaryKey val movieId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "episode_playback_progress", indices = [Index("updatedAtEpochMillis")])
data class EpisodePlaybackProgressEntity(
    @PrimaryKey val episodeId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val updatedAtEpochMillis: Long
)
