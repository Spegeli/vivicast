package com.vivicast.tv.iptv.xtream

data class XtreamCategory(
    val remoteId: String,
    val name: String,
)

data class XtreamLiveStream(
    val remoteId: String,
    val name: String,
    val categoryRemoteId: String?,
    val channelNumber: String?,
    val logoUrl: String?,
    val epgChannelId: String?,
    val isCatchupAvailable: Boolean,
    val catchupDays: Int,
)

data class XtreamVodItem(
    val remoteId: String,
    val name: String,
    val categoryRemoteId: String?,
    val containerExtension: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: String?,
    val year: String?,
    val genre: String?,
    val durationSeconds: Long?,
    val director: String?,
    val cast: String?,
    val plot: String?,
    val trailerUrl: String?,
    val addedAtSeconds: Long?,
)

data class XtreamSeriesItem(
    val remoteId: String,
    val name: String,
    val categoryRemoteId: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: String?,
    val year: String?,
    val genre: String?,
    val director: String?,
    val cast: String?,
    val plot: String?,
    val addedAtSeconds: Long?,
)

data class XtreamSeriesInfo(
    val seriesRemoteId: String,
    val seasons: List<XtreamSeason>,
    val episodes: List<XtreamEpisode>,
)

data class XtreamSeason(
    val seasonNumber: Int,
    val name: String,
    val posterUrl: String?,
)

data class XtreamEpisode(
    val remoteId: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val plot: String?,
    val thumbnailUrl: String?,
    val containerExtension: String?,
    val durationSeconds: Long?,
    val airDate: String?,
)
