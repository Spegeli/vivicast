package com.vivicast.tv.domain.model

data class Provider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val credentialsKey: String,
    val isActive: Boolean,
    val status: ProviderStatus,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
    val logoPriority: String,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ProviderType { M3u, Xtream }

enum class ProviderStatus {
    Active,
    Refreshing,
    ConnectionError,
    InvalidCredentials,
    Expired,
    Disabled,
}

data class Category(
    val id: String,
    val providerId: String,
    val type: CategoryType,
    val remoteId: String,
    val name: String,
    val sortOrder: Int,
    val isHidden: Boolean,
)

enum class CategoryType { LiveTv, Movies, Series }

data class Channel(
    val id: String,
    val providerId: String,
    val categoryId: String?,
    val remoteId: String,
    val channelNumber: String?,
    val name: String,
    val logoUrl: String?,
    val isCatchupAvailable: Boolean,
    val catchupDays: Int,
)

data class Movie(
    val id: String,
    val providerId: String,
    val categoryId: String?,
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
)

data class Series(
    val id: String,
    val providerId: String,
    val categoryId: String?,
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
)

data class Season(
    val id: String,
    val providerId: String,
    val seriesId: String,
    val seasonNumber: Int,
    val name: String,
    val posterUrl: String?,
)

data class Episode(
    val id: String,
    val providerId: String,
    val seriesId: String,
    val seasonId: String,
    val remoteId: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val plot: String?,
    val thumbnailUrl: String?,
    val containerExtension: String?,
    val duration: Long?,
    val airDate: String?,
)

data class EpgSource(
    val id: String,
    val name: String,
    val urlKey: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
)

data class ProviderEpgSource(
    val id: String,
    val providerId: String,
    val epgSourceId: String,
    val priority: Int,
)

data class EpgProgram(
    val id: String,
    val providerId: String,
    val channelId: String,
    val epgSourceId: String,
    val externalChannelId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val category: String?,
    val iconUrl: String?,
    val isCatchupAvailable: Boolean,
)

data class EpgChannelMapping(
    val id: String,
    val providerId: String,
    val channelId: String,
    val epgSourceId: String,
    val epgChannelId: String,
    val isManual: Boolean,
)

data class Favorite(
    val id: String,
    val providerId: String,
    val mediaType: MediaType,
    val mediaId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class MediaType { Channel, Movie, Series, Episode }

data class PlaybackProgress(
    val id: String,
    val providerId: String,
    val mediaType: MediaType,
    val mediaId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val progressPercent: Int,
    val isCompleted: Boolean,
    val lastWatchedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ChannelHistory(
    val id: String,
    val providerId: String,
    val channelId: String,
    val watchedAt: Long,
    val durationWatchedMillis: Long,
    val updatedAt: Long,
)

data class SearchResults(
    val channels: List<Channel>,
    val movies: List<Movie>,
    val series: List<Series>,
    val epgPrograms: List<EpgProgram>,
)

