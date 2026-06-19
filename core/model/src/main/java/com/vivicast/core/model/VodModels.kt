package com.vivicast.core.model

data class MovieCategory(
    val id: String,
    val playlistId: String,
    val name: String,
    val sortIndex: Int
)

data class Movie(
    val id: String,
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

data class SeriesCategory(
    val id: String,
    val playlistId: String,
    val name: String,
    val sortIndex: Int
)

data class Series(
    val id: String,
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

data class Season(
    val id: String,
    val playlistId: String,
    val seriesId: String,
    val title: String,
    val seasonNumber: Int,
    val coverUrl: String?,
    val plot: String?,
    val sortIndex: Int
)

data class Episode(
    val id: String,
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

data class MoviePlaybackProgress(
    val movieId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val updatedAtEpochMillis: Long
)

data class EpisodePlaybackProgress(
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val updatedAtEpochMillis: Long
)
