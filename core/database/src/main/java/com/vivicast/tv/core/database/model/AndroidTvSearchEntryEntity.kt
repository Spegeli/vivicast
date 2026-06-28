package com.vivicast.tv.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "android_tv_search_entries",
    indices = [
        Index(value = ["mediaType"]),
        Index(value = ["providerStableKey", "mediaType", "mediaStableKey"], unique = true),
    ],
)
data class AndroidTvSearchEntryEntity(
    @PrimaryKey val id: String,
    val mediaType: String,
    val providerStableKey: String,
    val mediaStableKey: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val deepLink: String,
    val updatedAt: Long,
)
