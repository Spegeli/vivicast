package com.vivicast.tv

import android.app.Application
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vivicast.core.domain.EpgNowNext
import com.vivicast.core.model.Channel
import com.vivicast.core.model.Series
import com.vivicast.core.model.Season
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.Movie
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Episode
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
@Composable
fun LiveTvScreenEffects(
    controller: ViviCastTvController,
    uiState: LiveTvUiState,
    scope: CoroutineScope,
    channels: List<Channel>,
    recents: List<RecentChannel>,
    playlists: List<Playlist>,
    providerSettings: Map<String, ProviderUiSettings>,
    epgSources: List<EpgSource>,
    providerEpgAssignments: Map<String, List<String>>,
    appSettings: AppSettings,
    playbackState: PlaybackState,
    selectedSeries: Series?,
    seasons: List<Season>,
    episodes: List<Episode>,
    enterLiveTv: () -> Unit,
    playChannel: (Channel) -> Unit,
    finishOrConfirmExit: () -> Unit,
    refreshConfiguredEpg: suspend (Boolean, String) -> Unit,
) {
    var selectedChannel by uiState.selectedChannel
    var selectedPlaylistId by uiState.selectedPlaylistId
    var selectedCategoryId by uiState.selectedCategoryId
    var selectedSection by uiState.selectedSection
    var selectedGuideProgram by uiState.selectedGuideProgram
    var guideWindowOffset by uiState.guideWindowOffset
    var selectedSeasonId by uiState.selectedSeasonId
    var selectedEpisodeId by uiState.selectedEpisodeId
    var navigationFocusRestoreKey by uiState.navigationFocusRestoreKey
    var navigationVisible by uiState.navigationVisible
    var liveTvFiltersVisible by uiState.liveTvFiltersVisible
    var showM3uUrlDialog by uiState.showM3uUrlDialog
    var showXtreamDialog by uiState.showXtreamDialog
    var showGlobalEpgDialog by uiState.showGlobalEpgDialog
    var providerToEdit by uiState.providerToEdit
    var selectedEpgDetailSourceId by uiState.selectedEpgDetailSourceId
    var settingsFocusRestoreKey by uiState.settingsFocusRestoreKey
    var importingM3uUrl by uiState.importingM3uUrl
    var importingXtream by uiState.importingXtream
    var importingGlobalEpg by uiState.importingGlobalEpg
    var importingEpg by uiState.importingEpg
    var refreshingProviderId by uiState.refreshingProviderId
    var favoriteFeedback by uiState.favoriteFeedback
    var lastAutoRetriedPlaybackToken by uiState.lastAutoRetriedPlaybackToken
    var lastCountedPlaybackErrorToken by uiState.lastCountedPlaybackErrorToken
    var lastCountedPlaybackBufferingToken by uiState.lastCountedPlaybackBufferingToken
    var lastCountedPlaybackStartToken by uiState.lastCountedPlaybackStartToken
    var lastPlaybackErrorDebug by uiState.lastPlaybackErrorDebug
    var lastPlaybackEventAtMillis by uiState.lastPlaybackEventAtMillis
    var lastPlaybackStartedAtMillis by uiState.lastPlaybackStartedAtMillis
    var lastPlaybackPausedAtMillis by uiState.lastPlaybackPausedAtMillis
    var lastPlaybackStoppedAtMillis by uiState.lastPlaybackStoppedAtMillis
    var lastPlaybackAutoRetryAtMillis by uiState.lastPlaybackAutoRetryAtMillis
    var lastRecoverablePlaybackErrorAtMillis by uiState.lastRecoverablePlaybackErrorAtMillis
    var lastFatalPlaybackErrorAtMillis by uiState.lastFatalPlaybackErrorAtMillis
    var playbackStartCount by uiState.playbackStartCount
    var playbackBufferingCount by uiState.playbackBufferingCount
    var playbackPauseCount by uiState.playbackPauseCount
    var playbackStopCount by uiState.playbackStopCount
    var playbackAutoRetryCount by uiState.playbackAutoRetryCount
    var playbackRecoverableErrorCount by uiState.playbackRecoverableErrorCount
    var playbackFatalErrorCount by uiState.playbackFatalErrorCount
    var currentPlaybackErrorStreak by uiState.currentPlaybackErrorStreak
    var worstPlaybackErrorStreak by uiState.worstPlaybackErrorStreak
    var previousSelectedSection by uiState.previousSelectedSection
    var startupSurfaceHandled by uiState.startupSurfaceHandled
    var startupBehaviorHandled by uiState.startupBehaviorHandled
    var startupRefreshHandled by uiState.startupRefreshHandled
    var startupEpgRefreshHandled by uiState.startupEpgRefreshHandled

    LaunchedEffect(favoriteFeedback) {
        if (favoriteFeedback != null) {
            delay(2_200)
            favoriteFeedback = null
        }
    }

    BackHandler {
        when {
            providerToEdit != null -> {
                if (!importingEpg && refreshingProviderId == null) {
                    providerToEdit = null
                }
            }

            selectedEpgDetailSourceId != null -> {
                selectedEpgDetailSourceId = null
                settingsFocusRestoreKey += 1
            }

            showGlobalEpgDialog -> {
                if (!importingGlobalEpg) {
                    showGlobalEpgDialog = false
                }
            }
            showXtreamDialog && !importingXtream -> showXtreamDialog = false
            showM3uUrlDialog && !importingM3uUrl -> showM3uUrlDialog = false
            selectedSection == TvSection.Settings -> {
                if (appSettings.backClosesNavigationFirst && navigationVisible) {
                    navigationVisible = false
                } else {
                    if (appSettings.backLeavesSettingsToLiveTv) {
                        enterLiveTv()
                    } else {
                        finishOrConfirmExit()
                    }
                }
            }

            selectedSection == TvSection.Search ||
            selectedSection == TvSection.Movies ||
            selectedSection == TvSection.Series -> {
                if (!navigationVisible) {
                    navigationVisible = true
                    navigationFocusRestoreKey += 1
                } else {
                    enterLiveTv()
                }
            }

            selectedSection == TvSection.Guide && appSettings.backClearsGuideProgrammeFirst && selectedGuideProgram != null -> {
                selectedGuideProgram = null
            }

            selectedSection == TvSection.Guide && appSettings.backClearsGuideWindowFirst && guideWindowOffset != 0 -> {
                guideWindowOffset = 0
            }

            selectedSection == TvSection.Guide &&
                appSettings.backClearsGuideFilters &&
                !appSettings.backClearsProviderBeforeCategory &&
                selectedCategoryId != null -> {
                selectedCategoryId = null
            }

            selectedSection == TvSection.Guide &&
                appSettings.backClearsGuideFilters &&
                selectedPlaylistId != null -> {
                selectedPlaylistId = null
            }

            selectedSection == TvSection.Guide &&
                appSettings.backClearsGuideFilters &&
                appSettings.backClearsProviderBeforeCategory &&
                selectedCategoryId != null -> {
                selectedCategoryId = null
            }

            selectedSection == TvSection.Guide ||
            selectedSection == TvSection.Favorites ||
            selectedSection == TvSection.Recent -> {
                enterLiveTv()
            }

            appSettings.backClosesNavigationFirst && navigationVisible -> {
                navigationVisible = false
            }

            appSettings.backHidesLiveTvFiltersFirst && liveTvFiltersVisible -> liveTvFiltersVisible = false
            appSettings.backClearsLiveTvFilters &&
                !appSettings.backClearsProviderBeforeCategory &&
                selectedCategoryId != null -> selectedCategoryId = null
            appSettings.backClearsLiveTvFilters && selectedPlaylistId != null -> selectedPlaylistId = null
            appSettings.backClearsLiveTvFilters &&
                appSettings.backClearsProviderBeforeCategory &&
                selectedCategoryId != null -> selectedCategoryId = null
            liveTvFiltersVisible -> liveTvFiltersVisible = false
            else -> finishOrConfirmExit()
        }
    }

    LaunchedEffect(
        startupSurfaceHandled,
        appSettings.resumeLastChannelOnStart
    ) {
        if (startupSurfaceHandled) return@LaunchedEffect
        if (appSettings.resumeLastChannelOnStart) return@LaunchedEffect

        enterLiveTv()
        startupSurfaceHandled = true
    }

    LaunchedEffect(
        startupBehaviorHandled,
        channels,
        recents,
        appSettings.resumeLastChannelOnStart,
        playbackState.channelId
    ) {
        if (startupBehaviorHandled) return@LaunchedEffect
        if (channels.isEmpty()) return@LaunchedEffect

        val startupChannel = if (appSettings.resumeLastChannelOnStart) {
            val recentChannelId = recents.firstOrNull()?.channelId
            recentChannelId?.let { channelId -> channels.firstOrNull { it.id == channelId } }
        } else {
            null
        }

        startupBehaviorHandled = true

        if (startupChannel != null && playbackState.channelId != startupChannel.id) {
            enterLiveTv()
            playChannel(startupChannel)
        } else if (appSettings.resumeLastChannelOnStart) {
            enterLiveTv()
        }
        startupSurfaceHandled = true
    }

    LaunchedEffect(startupRefreshHandled, playlists, providerSettings) {
        if (startupRefreshHandled) return@LaunchedEffect
        if (playlists.isEmpty()) return@LaunchedEffect
        if (playlists.any { playlist -> providerSettings[playlist.id] == null }) return@LaunchedEffect
        startupRefreshHandled = true

        playlists
            .filter { playlist -> providerSettings[playlist.id].isImportActive() }
            .filter { playlist -> providerSettings[playlist.id]?.refreshOnAppStart == true }
            .forEach { playlist ->
                scope.launch {
                    controller.refreshProvider(playlist)
                }
            }
    }

    LaunchedEffect(
        startupEpgRefreshHandled,
        playlists,
        providerSettings,
        epgSources,
        providerEpgAssignments,
        appSettings.epgUpdateInterval
    ) {
        if (startupEpgRefreshHandled) return@LaunchedEffect
        if (playlists.isEmpty()) return@LaunchedEffect
        startupEpgRefreshHandled = true

        if (!appSettings.epgRefreshOnAppStart) return@LaunchedEffect
        if (appSettings.epgUpdateInterval == EpgUpdateInterval.Manual) return@LaunchedEffect
        refreshConfiguredEpg(false, "Startup EPG check")
    }

    LaunchedEffect(
        playlists,
        providerSettings,
        epgSources,
        providerEpgAssignments,
        appSettings.epgUpdateInterval,
        appSettings.epgRefreshDuringSession
    ) {
        val interval = appSettings.epgUpdateInterval
        if (!appSettings.epgRefreshDuringSession) return@LaunchedEffect
        if (interval == EpgUpdateInterval.Manual) return@LaunchedEffect
        while (true) {
            delay(15 * 60 * 1000L)
            refreshConfiguredEpg(false, "Scheduled EPG check")
        }
    }

    LaunchedEffect(selectedPlaylistId, providerSettings, playlists) {
        val playlistId = selectedPlaylistId ?: return@LaunchedEffect
        val settings = providerSettings[playlistId] ?: return@LaunchedEffect
        if (!settings.isImportActive()) return@LaunchedEffect
        if (!settings.refreshOnPlaylistChange) return@LaunchedEffect
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return@LaunchedEffect
        controller.refreshProvider(playlist)
    }

    LaunchedEffect(selectedSeries?.id, seasons) {
        val firstSeasonId = seasons.firstOrNull()?.id
        selectedSeasonId = when {
            firstSeasonId == null -> null
            seasons.any { it.id == selectedSeasonId } -> selectedSeasonId
            else -> firstSeasonId
        }
    }

    LaunchedEffect(selectedSeasonId, episodes) {
        val firstEpisodeId = episodes.firstOrNull()?.id
        selectedEpisodeId = when {
            firstEpisodeId == null -> null
            episodes.any { it.id == selectedEpisodeId } -> selectedEpisodeId
            else -> firstEpisodeId
        }
    }

    LaunchedEffect(playbackState.contentType, playbackState.channelId, playbackState.status) {
        val contentId = playbackState.channelId ?: return@LaunchedEffect
        val contentType = playbackState.contentType ?: return@LaunchedEffect
        if (contentType != PlaybackContentType.MOVIE && contentType != PlaybackContentType.EPISODE) {
            return@LaunchedEffect
        }
        while (true) {
            val positionMs = controller.currentPositionMs()
            val durationMs = controller.currentDurationMs()
            val completed = durationMs != null &&
                durationMs > 0L &&
                (positionMs >= durationMs - 30_000L || positionMs >= (durationMs * 0.95f).toLong())
            when (contentType) {
                PlaybackContentType.MOVIE -> controller.saveMovieProgress(
                    movieId = contentId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    completed = completed
                )

                PlaybackContentType.EPISODE -> controller.saveEpisodeProgress(
                    episodeId = contentId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    completed = completed
                )

                PlaybackContentType.LIVE_TV -> Unit
            }
            if (playbackState.status == PlaybackStatus.Idle || playbackState.status is PlaybackStatus.Error) {
                break
            }
            delay(5_000L)
        }
    }

    LaunchedEffect(selectedSection, appSettings.leaveLiveTvPlaybackBehavior, playbackState.status) {
        val lastSection = previousSelectedSection
        previousSelectedSection = selectedSection
        if (lastSection != TvSection.LiveTv || selectedSection == TvSection.LiveTv) return@LaunchedEffect
        when (appSettings.leaveLiveTvPlaybackBehavior) {
            LeaveLiveTvPlaybackBehavior.KeepPlaying -> Unit
            LeaveLiveTvPlaybackBehavior.PausePlayback -> {
                if (playbackState.status != PlaybackStatus.Idle && playbackState.status != PlaybackStatus.Paused) {
                    playbackPauseCount += 1
                    lastPlaybackPausedAtMillis = System.currentTimeMillis()
                    lastPlaybackEventAtMillis = lastPlaybackPausedAtMillis
                    controller.pausePlayback()
                }
            }

            LeaveLiveTvPlaybackBehavior.StopPlayback -> {
                if (playbackState.status != PlaybackStatus.Idle) {
                    playbackStopCount += 1
                    lastPlaybackStoppedAtMillis = System.currentTimeMillis()
                    lastPlaybackEventAtMillis = lastPlaybackStoppedAtMillis
                    controller.stopPlayback()
                }
            }
        }
    }

    LaunchedEffect(selectedChannel?.id, playbackState.status, appSettings.retryRecoverablePlaybackErrorsOnce) {
        when (val status = playbackState.status) {
            PlaybackStatus.Buffering -> {
                val bufferingToken = "${selectedChannel?.id ?: "no-channel"}|buffering"
                if (lastCountedPlaybackBufferingToken != bufferingToken) {
                    lastCountedPlaybackBufferingToken = bufferingToken
                    playbackBufferingCount += 1
                    lastPlaybackEventAtMillis = System.currentTimeMillis()
                }
            }

            PlaybackStatus.Playing -> {
                lastAutoRetriedPlaybackToken = null
                currentPlaybackErrorStreak = 0
                val playingToken = "${selectedChannel?.id ?: "no-channel"}|playing"
                if (lastCountedPlaybackStartToken != playingToken) {
                    lastCountedPlaybackStartToken = playingToken
                    playbackStartCount += 1
                    lastPlaybackStartedAtMillis = System.currentTimeMillis()
                    lastPlaybackEventAtMillis = lastPlaybackStartedAtMillis
                }
            }

            is PlaybackStatus.Error -> {
                lastPlaybackErrorDebug = buildString {
                    append(status.message)
                    append(" / ")
                    append(if (status.recoverable) "recoverable" else "non-recoverable")
                }
                val channel = selectedChannel
                val errorToken = buildString {
                    append(channel?.id ?: "no-channel")
                    append("|")
                    append(status.message)
                    append("|")
                    append(status.recoverable)
                }
                if (lastCountedPlaybackErrorToken != errorToken) {
                    lastCountedPlaybackErrorToken = errorToken
                    lastPlaybackEventAtMillis = System.currentTimeMillis()
                    if (status.recoverable) {
                        playbackRecoverableErrorCount += 1
                        lastRecoverablePlaybackErrorAtMillis = lastPlaybackEventAtMillis
                    } else {
                        playbackFatalErrorCount += 1
                        lastFatalPlaybackErrorAtMillis = lastPlaybackEventAtMillis
                    }
                    currentPlaybackErrorStreak += 1
                    if (currentPlaybackErrorStreak > worstPlaybackErrorStreak) {
                        worstPlaybackErrorStreak = currentPlaybackErrorStreak
                    }
                }
                if (!appSettings.retryRecoverablePlaybackErrorsOnce || !status.recoverable) return@LaunchedEffect
                val retryChannel = channel ?: return@LaunchedEffect
                val retryToken = "${retryChannel.id}|${status.message}"
                if (lastAutoRetriedPlaybackToken == retryToken) return@LaunchedEffect
                lastAutoRetriedPlaybackToken = retryToken
                delay(appSettings.recoverablePlaybackRetryDelay.delayMillis)
                playbackAutoRetryCount += 1
                lastPlaybackAutoRetryAtMillis = System.currentTimeMillis()
                lastPlaybackEventAtMillis = lastPlaybackAutoRetryAtMillis
                playChannel(retryChannel)
            }

            else -> Unit
        }
    }

    DisposableEffect(appSettings.keepScreenAwakeDuringPlayback, playbackState.status) {
        val activity = controller.context.findActivity()
        val shouldKeepAwake = appSettings.keepScreenAwakeDuringPlayback &&
            playbackState.status != PlaybackStatus.Idle &&
            playbackState.status !is PlaybackStatus.Error
        if (shouldKeepAwake) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }


}
