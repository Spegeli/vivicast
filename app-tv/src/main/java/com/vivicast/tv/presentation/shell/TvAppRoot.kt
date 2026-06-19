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
fun ViviCastTvApp() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: TvAppViewModel = viewModel(
        factory = remember(application) { TvAppViewModelFactory(application) }
    )

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LiveTvScreen(
                controller = viewModel.controller,
                uiState = viewModel.uiState
            )
        }
    }
}

@Composable
fun LiveTvScreen(
    controller: ViviCastTvController,
    uiState: LiveTvUiState = remember { LiveTvUiState() }
) {
    val scope = rememberCoroutineScope()
    val isDebugBuild = remember(controller) {
        (controller.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val channels by controller.channels.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by controller.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by controller.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val epgSources by controller.epgSources.collectAsStateWithLifecycle(initialValue = emptyList())
    val epgProgramCount by controller.epgProgramCount.collectAsStateWithLifecycle(initialValue = 0)
    val favorites by controller.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents by controller.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val movies by controller.movies.collectAsStateWithLifecycle(initialValue = emptyList())
    val series by controller.series.collectAsStateWithLifecycle(initialValue = emptyList())
    val providerSettings by controller.providerSettings.collectAsStateWithLifecycle()
    val providerSyncStates by controller.providerSyncStates.collectAsStateWithLifecycle()
    val providerEpgAssignments by controller.providerEpgAssignments.collectAsStateWithLifecycle()
    val appSettings by controller.appSettings.collectAsStateWithLifecycle()
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    var selectedChannel by uiState.selectedChannel
    var focusedChannel by uiState.focusedChannel
    var selectedPlaylistId by uiState.selectedPlaylistId
    var selectedCategoryId by uiState.selectedCategoryId
    var selectedSection by uiState.selectedSection
    var selectedMovieId by uiState.selectedMovieId
    var selectedSeriesId by uiState.selectedSeriesId
    var selectedSeasonId by uiState.selectedSeasonId
    var selectedEpisodeId by uiState.selectedEpisodeId
    var selectedSettingsSection by uiState.selectedSettingsSection
    var importStatus by uiState.importStatus
    var autoImportAttempted by uiState.autoImportAttempted
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
    var refreshingAllProviders by uiState.refreshingAllProviders
    var epgClockMillis by uiState.epgClockMillis
    var providerEditStatus by uiState.providerEditStatus
    var epgRefreshStatus by uiState.epgRefreshStatus
    var lastEpgSweepAtMillis by uiState.lastEpgSweepAtMillis
    var lastEpgSweepReason by uiState.lastEpgSweepReason
    var lastEpgSweepAttempted by uiState.lastEpgSweepAttempted
    var lastEpgSweepSucceeded by uiState.lastEpgSweepSucceeded
    var lastEpgSweepFailed by uiState.lastEpgSweepFailed
    var lastEpgSweepSkipped by uiState.lastEpgSweepSkipped
    var lastEpgSweepUnconfigured by uiState.lastEpgSweepUnconfigured
    var lastEpgSweepNotDue by uiState.lastEpgSweepNotDue
    var epgSweepCount by uiState.epgSweepCount
    var selectedGuideProgram by uiState.selectedGuideProgram
    var guideWindowOffset by uiState.guideWindowOffset
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
    var playbackManualRetryCount by uiState.playbackManualRetryCount
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
    var refreshingAllEpg by uiState.refreshingAllEpg
    var lastBackPressAtMillis by uiState.lastBackPressAtMillis

    fun enterLiveTv() {
        selectedSection = TvSection.LiveTv
        navigationVisible = !appSettings.autoHidePrimaryNavigation
        liveTvFiltersVisible = appSettings.openProviderFiltersWhenEnteringLiveTv
    }

    fun enterGuide() {
        selectedSection = TvSection.Guide
        navigationVisible = false
        if (appSettings.guideResetsToNowOnOpen) {
            guideWindowOffset = 0
        }
        if (appSettings.guideClearsCategoryOnOpen) {
            selectedCategoryId = null
        }
        selectedGuideProgram = null
        if (appSettings.guideUsesPlayingChannelProviderOnOpen) {
            selectedPlaylistId = selectedChannel?.playlistId ?: selectedPlaylistId
        }
        if (selectedPlaylistId == null) {
            selectedPlaylistId = playlists.maxByOrNull { playlist ->
                channels.count { channel -> channel.playlistId == playlist.id }
            }?.id
        }
    }

    fun playChannel(channel: Channel) {
        selectedChannel = channel
        focusedChannel = channel
        controller.play(channel)
        scope.launch { controller.markRecent(channel.id) }
    }

    fun finishOrConfirmExit() {
        if (appSettings.confirmExitOnBack) {
            val now = System.currentTimeMillis()
            if (now - lastBackPressAtMillis < 2_000L) {
                controller.context.findActivity()?.finish()
            } else {
                lastBackPressAtMillis = now
                Toast.makeText(controller.context, "Press Back again to exit", Toast.LENGTH_SHORT).show()
            }
        } else {
            controller.context.findActivity()?.finish()
        }
    }

    suspend fun refreshConfiguredEpg(force: Boolean, reasonLabel: String) {
        if (refreshingAllEpg) return
        refreshingAllEpg = true
        epgRefreshStatus = if (force) {
            "$reasonLabel: refreshing configured EPG sources..."
        } else {
            "$reasonLabel: checking due EPG sources..."
        }

        val interval = appSettings.epgUpdateInterval
        val activePlaylists = playlists.filter { playlist -> providerSettings[playlist.id].isLiveTvActive() }
        var attempted = 0
        var succeeded = 0
        var failed = 0
        var skipped = 0
        var unconfigured = 0
        var notDue = 0

        activePlaylists.forEach { playlist ->
            val directEpgSource = epgSources.firstOrNull { it.playlistId == playlist.id }
            val assignedGlobalSources = (providerEpgAssignments[playlist.id] ?: emptyList())
                .mapNotNull { sourceId -> epgSources.firstOrNull { it.id == sourceId } }
            val effectiveEpgSource = directEpgSource ?: assignedGlobalSources.firstOrNull()

            if (effectiveEpgSource == null) {
                skipped += 1
                unconfigured += 1
                return@forEach
            }

            val shouldImport = force || (
                interval != EpgUpdateInterval.Manual &&
                    interval.shouldRefreshSince(effectiveEpgSource.lastImportedAtEpochMillis)
                )

            if (!shouldImport) {
                skipped += 1
                notDue += 1
                return@forEach
            }

            attempted += 1
            val result = controller.importEpg(playlist, effectiveEpgSource)
            if (result.isSuccess) {
                succeeded += 1
            } else {
                failed += 1
            }
        }

        lastEpgSweepAtMillis = System.currentTimeMillis()
        lastEpgSweepReason = reasonLabel
        lastEpgSweepAttempted = attempted
        lastEpgSweepSucceeded = succeeded
        lastEpgSweepFailed = failed
        lastEpgSweepSkipped = skipped
        lastEpgSweepUnconfigured = unconfigured
        lastEpgSweepNotDue = notDue
        epgSweepCount += 1

        epgRefreshStatus = when {
            attempted == 0 -> "$reasonLabel: 0 due / $notDue not due / $unconfigured unconfigured."
            failed == 0 -> "$reasonLabel: $succeeded completed / $notDue not due / $unconfigured unconfigured."
            succeeded == 0 -> "$reasonLabel failed: $failed failed / $notDue not due / $unconfigured unconfigured."
            else -> "$reasonLabel: $succeeded completed / $failed failed / $notDue not due / $unconfigured unconfigured."
        }
        refreshingAllEpg = false
    }

    LaunchedEffect(playlists.isEmpty(), channels.isEmpty()) {
        if (playlists.isEmpty() && channels.isEmpty() && !autoImportAttempted) {
            autoImportAttempted = true
            if (isDebugBuild) {
                importStatus = "Importing demo playlist..."
                importStatus = runCatching { controller.importDemoPlaylist().asStatusText() }
                    .getOrElse { error -> "Import failed: ${error.message ?: error::class.simpleName}" }
            } else {
                importStatus = "Add a provider in Settings to start watching Live TV."
                startupBehaviorHandled = true
            }
        }
    }

    LaunchedEffect(playlists, selectedPlaylistId) {
        if (selectedPlaylistId != null && playlists.none { it.id == selectedPlaylistId }) {
            selectedPlaylistId = null
        }
    }

    LaunchedEffect(playlists.map { it.id }) {
        controller.hydrateProviderState(playlists.map { it.id })
    }

    LaunchedEffect(appSettings.autoHidePrimaryNavigation) {
        if (appSettings.autoHidePrimaryNavigation) {
            navigationVisible = false
        } else {
            navigationVisible = true
        }
    }

    LaunchedEffect(selectedSection, appSettings.autoHidePrimaryNavigation) {
        if (selectedSection == TvSection.Settings) {
            navigationVisible = false
        } else if (!appSettings.autoHidePrimaryNavigation) {
            navigationVisible = true
        }
    }

    LaunchedEffect(playlists, providerSettings, selectedPlaylistId) {
        if (selectedPlaylistId != null) {
            val settings = providerSettings[selectedPlaylistId]
            if (settings?.isLiveTvActive() == false) {
                selectedPlaylistId = null
            }
        }
    }

    LaunchedEffect(categories, selectedCategoryId, selectedPlaylistId) {
        if (selectedCategoryId != null && categories.none { category ->
                category.id == selectedCategoryId && (selectedPlaylistId == null || category.playlistId == selectedPlaylistId)
            }
        ) {
            selectedCategoryId = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            epgClockMillis = System.currentTimeMillis()
        }
    }

    val enabledPlaylistIds = remember(playlists, providerSettings) {
        playlists
            .filter { playlist -> providerSettings[playlist.id].isLiveTvActive() }
            .map { it.id }
            .toSet()
    }
    val visibleCategories = remember(categories, selectedPlaylistId, providerSettings, enabledPlaylistIds) {
        categories
            .filter { category -> category.playlistId in enabledPlaylistIds }
            .filter { category ->
                providerSettings[category.playlistId]
                    ?.hiddenCategoryIds
                    ?.contains(category.id) != true
            }
            .filter { category -> selectedPlaylistId == null || category.playlistId == selectedPlaylistId }
            .distinctBy { it.id }
            .sortedForProviders(providerSettings)
    }
    val selectedCategoryName = visibleCategories.firstOrNull { it.id == selectedCategoryId }?.name
    val favoriteChannelIds = remember(favorites) { favorites.map { it.channelId }.toSet() }
    val recentChannelIds = remember(recents) { recents.map { it.channelId } }
    val visibleChannels = remember(
        channels,
        selectedPlaylistId,
        selectedCategoryId,
        selectedSection,
        favoriteChannelIds,
        recentChannelIds,
        enabledPlaylistIds,
        providerSettings
    ) {
        val filteredChannels = channels
            .asSequence()
            .filter { channel -> channel.playlistId in enabledPlaylistIds }
            .filter { channel ->
                channel.categoryId?.let { categoryId ->
                    providerSettings[channel.playlistId]
                        ?.hiddenCategoryIds
                        ?.contains(categoryId) != true
                } ?: true
            }
            .filter { channel -> selectedPlaylistId == null || channel.playlistId == selectedPlaylistId }
            .filter { channel -> selectedCategoryId == null || channel.categoryId == selectedCategoryId }
            .toList()
        when (selectedSection) {
            TvSection.Favorites -> filteredChannels.filter { it.id in favoriteChannelIds }
            TvSection.Recent -> {
                val recentRank = recentChannelIds.withIndex().associate { it.value to it.index }
                filteredChannels
                    .filter { it.id in recentRank }
                    .sortedBy { recentRank[it.id] ?: Int.MAX_VALUE }
            }

            else -> filteredChannels
        }
    }
    val channelCountByPlaylist = remember(channels) {
        channels.groupingBy { it.playlistId }.eachCount()
    }
    val selectedPlaylistName = playlists.firstOrNull { it.id == selectedPlaylistId }?.displayName()
    val previewChannel = focusedChannel ?: selectedChannel
    val previewIsFavorite = previewChannel?.id?.let { it in favoriteChannelIds } == true
    val playlistNameById = remember(playlists) { playlists.associate { it.id to it.displayName() } }
    val vodLibraryStatus = remember(playlists, providerSettings, providerSyncStates) {
        computeVodLibraryStatus(playlists, providerSettings, providerSyncStates)
    }
    val vodEnabledPlaylistIds = vodLibraryStatus.enabledPlaylistIds
    val vodLibraryRefreshing = vodLibraryStatus.refreshing
    val visibleMovies = remember(movies, providerSettings) {
        movies.filter { movie ->
            val settings = providerSettings[movie.playlistId]
            settings?.enabled != false && settings?.vodEnabled == true
        }
    }
    val visibleSeries = remember(series, providerSettings) {
        series.filter { item ->
            val settings = providerSettings[item.playlistId]
            settings?.enabled != false && settings?.vodEnabled == true
        }
    }
    val selectedMovie = remember(selectedMovieId, visibleMovies) {
        visibleMovies.firstOrNull { it.id == selectedMovieId } ?: visibleMovies.firstOrNull()
    }
    val selectedSeries = remember(selectedSeriesId, visibleSeries) {
        visibleSeries.firstOrNull { it.id == selectedSeriesId } ?: visibleSeries.firstOrNull()
    }
    val seasons by remember(selectedSeries?.id) {
        selectedSeries?.id?.let { controller.observeSeasons(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val episodes by remember(selectedSeasonId) {
        selectedSeasonId?.let { controller.observeEpisodes(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedEpisode = remember(selectedEpisodeId, episodes) {
        episodes.firstOrNull { it.id == selectedEpisodeId } ?: episodes.firstOrNull()
    }
    val movieProgress by remember(selectedMovie?.id) {
        selectedMovie?.id?.let { controller.observeMovieProgress(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val episodeProgress by remember(selectedEpisode?.id) {
        selectedEpisode?.id?.let { controller.observeEpisodeProgress(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val movieEmptyState = remember(playlists.isNotEmpty(), vodLibraryStatus) {
        movieLibraryEmptyState(playlists.isNotEmpty(), vodLibraryStatus)
    }
    val seriesEmptyState = remember(playlists.isNotEmpty(), vodLibraryStatus) {
        seriesLibraryEmptyState(playlists.isNotEmpty(), vodLibraryStatus)
    }
    val recentChannels = remember(recents, channels) {
        val channelById = channels.associateBy { it.id }
        recents.mapNotNull { recent -> channelById[recent.channelId] }
    }
    val guideChannels = remember(visibleChannels, selectedChannel, appSettings.guideStartsWithCurrentChannel) {
        if (!appSettings.guideStartsWithCurrentChannel) {
            visibleChannels.take(12)
        } else {
            val currentChannel = selectedChannel
            if (currentChannel != null && visibleChannels.any { it.id == currentChannel.id }) {
                buildList {
                    add(currentChannel)
                    addAll(visibleChannels.filterNot { it.id == currentChannel.id })
                }.take(12)
            } else {
                visibleChannels.take(12)
            }
        }
    }
    val guideChannelIds = remember(guideChannels) { guideChannels.map { it.id } }
    val guideWindowStartMillis = remember(epgClockMillis, guideWindowOffset) {
        epgClockMillis.roundDownToHalfHour() + guideWindowOffset * GuideWindowMillis
    }
    val guideWindowEndMillis = guideWindowStartMillis + GuideWindowMillis
    val guidePrograms by remember(guideChannelIds, guideWindowStartMillis) {
        controller.observeGuidePrograms(
            channelIds = guideChannelIds,
            fromUtcEpochMillis = guideWindowStartMillis,
            toUtcEpochMillis = guideWindowEndMillis
        )
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val nowNext by remember(previewChannel?.id, epgClockMillis) {
        previewChannel?.id
            ?.let { controller.observeNowNext(it, epgClockMillis) }
            ?: flowOf(EpgNowNext(now = null, next = null))
    }.collectAsStateWithLifecycle(initialValue = EpgNowNext(now = null, next = null))

    LiveTvScreenEffects(
        controller = controller,
        uiState = uiState,
        scope = scope,
        channels = channels,
        recents = recents,
        playlists = playlists,
        providerSettings = providerSettings,
        epgSources = epgSources,
        providerEpgAssignments = providerEpgAssignments,
        appSettings = appSettings,
        playbackState = playbackState,
        selectedSeries = selectedSeries,
        seasons = seasons,
        episodes = episodes,
        enterLiveTv = ::enterLiveTv,
        playChannel = ::playChannel,
        finishOrConfirmExit = ::finishOrConfirmExit,
        refreshConfiguredEpg = ::refreshConfiguredEpg,
    )

    LiveTvScreenContent(
        controller = controller,
        uiState = uiState,
        scope = scope,
        channels = channels,
        categories = categories,
        playlists = playlists,
        epgSources = epgSources,
        epgProgramCount = epgProgramCount,
        favorites = favorites,
        recents = recents,
        providerSettings = providerSettings,
        providerSyncStates = providerSyncStates,
        providerEpgAssignments = providerEpgAssignments,
        appSettings = appSettings,
        playbackState = playbackState,
        selectedPlaylistName = selectedPlaylistName,
        selectedCategoryName = selectedCategoryName,
        previewChannel = previewChannel,
        previewIsFavorite = previewIsFavorite,
        playlistNameById = playlistNameById,
        vodLibraryStatus = vodLibraryStatus,
        vodLibraryRefreshing = vodLibraryRefreshing,
        visibleMovies = visibleMovies,
        visibleSeries = visibleSeries,
        selectedMovie = selectedMovie,
        selectedSeries = selectedSeries,
        seasons = seasons,
        episodes = episodes,
        selectedEpisode = selectedEpisode,
        movieProgress = movieProgress,
        episodeProgress = episodeProgress,
        movieEmptyState = movieEmptyState,
        seriesEmptyState = seriesEmptyState,
        recentChannels = recentChannels,
        guideChannels = guideChannels,
        guidePrograms = guidePrograms,
        visibleCategories = visibleCategories,
        visibleChannels = visibleChannels,
        channelCountByPlaylist = channelCountByPlaylist,
        guideWindowStartMillis = guideWindowStartMillis,
        guideWindowEndMillis = guideWindowEndMillis,
        nowNext = nowNext,
        epgClockMillis = epgClockMillis,
        enterGuide = ::enterGuide,
        playChannel = ::playChannel,
        refreshConfiguredEpg = ::refreshConfiguredEpg,
    )
}


