package com.vivicast.core.network

import com.vivicast.core.model.XtreamCredentials

interface XtreamClient {
    suspend fun authenticate(credentials: XtreamCredentials): NetworkResult<XtreamAccountInfo>
    suspend fun getLiveCategories(credentials: XtreamCredentials): NetworkResult<List<XtreamCategory>>
    suspend fun getLiveStreams(credentials: XtreamCredentials): NetworkResult<List<XtreamLiveStream>>
    suspend fun getVodCategories(credentials: XtreamCredentials): NetworkResult<List<XtreamCategory>>
    suspend fun getVodStreams(credentials: XtreamCredentials): NetworkResult<List<XtreamVodStream>>
    suspend fun getSeriesCategories(credentials: XtreamCredentials): NetworkResult<List<XtreamCategory>>
    suspend fun getSeries(credentials: XtreamCredentials): NetworkResult<List<XtreamSeriesItem>>
    suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: String): NetworkResult<XtreamSeriesInfo>
}

data class XtreamAccountInfo(
    val username: String,
    val status: String,
    val expiresAtEpochSeconds: Long?
)

data class XtreamCategory(
    val id: String,
    val name: String
)

data class XtreamLiveStream(
    val id: String,
    val name: String,
    val categoryId: String?,
    val streamUrl: String,
    val logoUrl: String?,
    val epgChannelId: String?,
    val catchupSupported: Boolean,
    val archiveDurationHours: Int?
)

data class XtreamVodStream(
    val id: String,
    val name: String,
    val categoryId: String?,
    val streamUrl: String,
    val coverUrl: String?,
    val plot: String?,
    val durationMinutes: Int?,
    val releaseDate: String?,
    val addedAtEpochSeconds: Long?
)

data class XtreamSeriesItem(
    val id: String,
    val name: String,
    val categoryId: String?,
    val coverUrl: String?,
    val plot: String?,
    val releaseDate: String?,
    val addedAtEpochSeconds: Long?,
    val episodeRunTimeMinutes: Int?
)

data class XtreamSeriesInfo(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val plot: String?,
    val seasons: List<XtreamSeasonInfo>,
    val episodes: List<XtreamEpisodeInfo>
)

data class XtreamSeasonInfo(
    val id: String,
    val seasonNumber: Int,
    val name: String,
    val coverUrl: String?,
    val plot: String?
)

data class XtreamEpisodeInfo(
    val id: String,
    val seasonId: String,
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String,
    val coverUrl: String?,
    val plot: String?,
    val durationMinutes: Int?,
    val addedAtEpochSeconds: Long?
)
