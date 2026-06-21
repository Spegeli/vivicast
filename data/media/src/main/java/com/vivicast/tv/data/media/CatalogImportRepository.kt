package com.vivicast.tv.data.media

import com.vivicast.tv.iptv.m3u.M3uPlaylist

interface CatalogImportRepository {
    suspend fun importM3uLiveChannels(providerId: String, playlist: M3uPlaylist): CatalogImportResult
}

data class CatalogImportResult(
    val categoriesAdded: Int,
    val categoriesUpdated: Int,
    val categoriesRemoved: Int,
    val channelsAdded: Int,
    val channelsUpdated: Int,
    val channelsRemoved: Int,
    val skippedEntries: Int,
)
