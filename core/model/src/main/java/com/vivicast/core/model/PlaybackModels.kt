package com.vivicast.core.model

enum class TrackType {
    AUDIO,
    SUBTITLE
}

enum class PlaybackContentType {
    LIVE_TV,
    MOVIE,
    EPISODE
}

data class StreamTrack(
    val id: String,
    val type: TrackType,
    val label: String,
    val language: String?,
    val selected: Boolean
)

sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data object Buffering : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus
    data class Error(val message: String, val recoverable: Boolean) : PlaybackStatus
}

data class PlaybackState(
    val channelId: String?,
    val contentType: PlaybackContentType?,
    val contentTitle: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val status: PlaybackStatus,
    val audioTracks: List<StreamTrack>,
    val subtitleTracks: List<StreamTrack>
)
