package com.vivicast.tv.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(
    tokenizer = "unicode61",
    notIndexed = ["mediaId", "providerId", "stableKey", "categoryId"],
)
@Entity(tableName = "search_channels_fts")
data class SearchChannelFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val mediaId: String,
    val providerId: String,
    val stableKey: String,
    val categoryId: String?,
    val name: String,
    val channelNumber: String?,
)

@Fts4(
    tokenizer = "unicode61",
    notIndexed = ["mediaId", "providerId", "stableKey", "categoryId"],
)
@Entity(tableName = "search_movies_fts")
data class SearchMovieFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val mediaId: String,
    val providerId: String,
    val stableKey: String,
    val categoryId: String?,
    val name: String,
    val originalName: String?,
    val genre: String?,
)

@Fts4(
    tokenizer = "unicode61",
    notIndexed = ["mediaId", "providerId", "stableKey", "categoryId"],
)
@Entity(tableName = "search_series_fts")
data class SearchSeriesFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val mediaId: String,
    val providerId: String,
    val stableKey: String,
    val categoryId: String?,
    val name: String,
    val originalName: String?,
    val genre: String?,
)

@Fts4(
    tokenizer = "unicode61",
    notIndexed = ["programId", "providerId", "channelId", "epgSourceId"],
)
@Entity(tableName = "search_epg_fts")
data class SearchEpgFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val programId: String,
    val providerId: String,
    val channelId: String,
    val epgSourceId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
)
