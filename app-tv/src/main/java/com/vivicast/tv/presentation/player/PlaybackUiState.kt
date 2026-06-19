package com.vivicast.tv

import com.vivicast.core.domain.EpgNowNext
import com.vivicast.core.model.Channel
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.StreamTrack

data class PlaybackUiState(
    val displayChannel: Channel?,
    val playingChannel: Channel?,
    val channelPosition: Int?,
    val status: PlaybackStatus,
    val nowNext: EpgNowNext,
    val currentTimeMillis: Long,
    val sourceName: String?,
    val showChannelNumbers: Boolean,
    val showSourceLabels: Boolean,
    val isFavorite: Boolean,
    val favoriteFeedback: String?,
    val showStatusBadge: Boolean,
    val showClock: Boolean,
    val showRecentChannels: Boolean,
    val showGuideAction: Boolean,
    val showProgressBar: Boolean,
    val audioTracks: List<StreamTrack>,
    val subtitleTracks: List<StreamTrack>,
    val recentChannels: List<Channel>,
    val showFavoriteAction: Boolean,
    val showProgrammeDescription: Boolean,
    val showFavoriteFeedbackBanner: Boolean,
    val showNowNextPanel: Boolean
)

fun PlaybackState.toPlaybackUiState(
    displayChannel: Channel?,
    playingChannel: Channel?,
    channelPosition: Int?,
    nowNext: EpgNowNext,
    currentTimeMillis: Long,
    sourceName: String?,
    appSettings: AppSettings,
    isFavorite: Boolean,
    favoriteFeedback: String?,
    recentChannels: List<Channel>
): PlaybackUiState = PlaybackUiState(
    displayChannel = displayChannel,
    playingChannel = playingChannel,
    channelPosition = channelPosition,
    status = status,
    nowNext = nowNext,
    currentTimeMillis = currentTimeMillis,
    sourceName = sourceName,
    showChannelNumbers = appSettings.showChannelNumbers,
    showSourceLabels = appSettings.showSourceLabels,
    isFavorite = isFavorite,
    favoriteFeedback = favoriteFeedback,
    showStatusBadge = appSettings.showPlaybackStatusBadge,
    showClock = appSettings.showPlaybackClock,
    showRecentChannels = appSettings.showPlaybackRecents,
    showGuideAction = appSettings.showPlaybackGuideAction,
    showProgressBar = appSettings.showPlaybackProgressBar,
    audioTracks = if (appSettings.showPlaybackTrackActions) audioTracks else emptyList(),
    subtitleTracks = if (appSettings.showPlaybackTrackActions) subtitleTracks else emptyList(),
    recentChannels = recentChannels,
    showFavoriteAction = appSettings.showPlaybackFavoriteAction,
    showProgrammeDescription = appSettings.showPlaybackProgrammeDescription,
    showFavoriteFeedbackBanner = appSettings.showFavoriteFeedbackBanner,
    showNowNextPanel = appSettings.showPlaybackNowNextPanel
)
