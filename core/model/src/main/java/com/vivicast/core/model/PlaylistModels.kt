package com.vivicast.core.model

enum class PlaylistSourceType {
    M3U_URL,
    M3U_FILE,
    XTREAM_CODES
}

data class Playlist(
    val id: String,
    val name: String,
    val sourceType: PlaylistSourceType,
    val sourceUri: String?,
    val sourceUsername: String?,
    val sourcePassword: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

data class ChannelCategory(
    val id: String,
    val playlistId: String,
    val name: String,
    val sortIndex: Int
)

data class Channel(
    val id: String,
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

data class EpgSource(
    val id: String,
    val playlistId: String?,
    val name: String,
    val sourceUri: String,
    val lastImportedAtEpochMillis: Long?
)

data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startUtcEpochMillis: Long,
    val endUtcEpochMillis: Long,
    val iconUrl: String?
)

data class FavoriteChannel(
    val channelId: String,
    val addedAtEpochMillis: Long
)

data class RecentChannel(
    val channelId: String,
    val watchedAtEpochMillis: Long
)
