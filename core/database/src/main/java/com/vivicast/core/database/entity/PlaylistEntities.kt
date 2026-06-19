package com.vivicast.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceType: String,
    val sourceUri: String?,
    val sourceUsername: String?,
    val sourcePassword: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "categories",
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
data class CategoryEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val name: String,
    val sortIndex: Int
)

@Entity(
    tableName = "channels",
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
        Index("tvgId"),
        Index(value = ["playlistId", "name"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val categoryId: String?,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val tvgId: String?,
    val tvgName: String?,
    val catchupSupported: Boolean,
    val sortIndex: Int
)

@Entity(
    tableName = "favorite_channels",
    indices = [Index("addedAtEpochMillis")]
)
data class FavoriteChannelEntity(
    @PrimaryKey val channelId: String,
    val addedAtEpochMillis: Long
)

@Entity(
    tableName = "recent_channels",
    indices = [Index("watchedAtEpochMillis")]
)
data class RecentChannelEntity(
    @PrimaryKey val channelId: String,
    val watchedAtEpochMillis: Long
)
