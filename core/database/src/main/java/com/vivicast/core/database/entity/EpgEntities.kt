package com.vivicast.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_sources",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("playlistId")]
)
data class EpgSourceEntity(
    @PrimaryKey val id: String,
    val playlistId: String?,
    val name: String,
    val sourceUri: String,
    val lastImportedAtEpochMillis: Long?
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index("channelId"),
        Index(value = ["channelId", "startUtcEpochMillis", "endUtcEpochMillis"]),
        Index("startUtcEpochMillis"),
        Index("endUtcEpochMillis")
    ]
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startUtcEpochMillis: Long,
    val endUtcEpochMillis: Long,
    val iconUrl: String?
)
