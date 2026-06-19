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
fun LiveTvScreenContent(
    controller: ViviCastTvController,
    uiState: LiveTvUiState,
    scope: CoroutineScope,
    channels: List<Channel>,
    categories: List<ChannelCategory>,
    playlists: List<Playlist>,
    epgSources: List<EpgSource>,
    epgProgramCount: Int,
    favorites: List<FavoriteChannel>,
    recents: List<RecentChannel>,
    providerSettings: Map<String, ProviderUiSettings>,
    providerSyncStates: Map<String, ProviderSyncState>,
    providerEpgAssignments: Map<String, List<String>>,
    appSettings: AppSettings,
    playbackState: PlaybackState,
    selectedPlaylistName: String?,
    selectedCategoryName: String?,
    previewChannel: Channel?,
    previewIsFavorite: Boolean,
    playlistNameById: Map<String, String>,
    vodLibraryStatus: VodLibraryStatus,
    vodLibraryRefreshing: Boolean,
    visibleMovies: List<Movie>,
    visibleSeries: List<Series>,
    selectedMovie: Movie?,
    selectedSeries: Series?,
    seasons: List<Season>,
    episodes: List<Episode>,
    selectedEpisode: Episode?,
    movieProgress: MoviePlaybackProgress?,
    episodeProgress: EpisodePlaybackProgress?,
    movieEmptyState: Pair<String, String>,
    seriesEmptyState: Pair<String, String>,
    recentChannels: List<Channel>,
    guideChannels: List<Channel>,
    guidePrograms: List<EpgProgram>,
    visibleCategories: List<ChannelCategory>,
    visibleChannels: List<Channel>,
    channelCountByPlaylist: Map<String, Int>,
    guideWindowStartMillis: Long,
    guideWindowEndMillis: Long,
    nowNext: EpgNowNext,
    epgClockMillis: Long,
    enterGuide: () -> Unit,
    playChannel: (Channel) -> Unit,
    refreshConfiguredEpg: suspend (Boolean, String) -> Unit,
) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViviCastColors.Background)
    ) {
        if (providerToEdit == null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (navigationVisible) {
                    BrandNavigationPanel(
                        selectedSection = selectedSection,
                        focusRestoreKey = navigationFocusRestoreKey,
                        onSelectSection = { section ->
                            selectedSection = section
                            navigationVisible = if (section == TvSection.Settings) {
                                false
                            } else {
                                !appSettings.autoHidePrimaryNavigation
                            }
                            liveTvFiltersVisible = if (section == TvSection.LiveTv) {
                                appSettings.openProviderFiltersWhenEnteringLiveTv
                            } else {
                            true
                        }
                        if (section == TvSection.Guide) {
                            if (appSettings.guideResetsToNowOnOpen) {
                                guideWindowOffset = 0
                            }
                            selectedGuideProgram = null
                            if (selectedPlaylistId == null) {
                                selectedPlaylistId = playlists.maxByOrNull { playlist -> channelCountByPlaylist[playlist.id] ?: 0 }?.id
                            }
                        }
                    },
                )
            }

            if (selectedSection == TvSection.Settings) {
                ProviderSettingsScreen(
                    selectedSection = selectedSettingsSection,
                    onSelectSection = {
                        selectedSettingsSection = it
                        selectedEpgDetailSourceId = null
                    },
                    focusRestoreKey = settingsFocusRestoreKey,
                    selectedEpgDetailSourceId = selectedEpgDetailSourceId,
                    onOpenEpgDetail = { selectedEpgDetailSourceId = it },
                    onCloseEpgDetail = {
                        selectedEpgDetailSourceId = null
                        settingsFocusRestoreKey += 1
                    },
                    playlists = playlists,
                    channels = channels,
                    categories = categories,
                    epgSources = epgSources,
                    epgProgramCount = epgProgramCount,
                    favorites = favorites,
                    recents = recents,
                    providerSettings = providerSettings,
                    providerSyncStates = providerSyncStates,
                    providerEpgAssignments = providerEpgAssignments,
                    appSettings = appSettings,
                    status = providerEditStatus,
                    epgStatus = epgRefreshStatus,
                    currentSection = selectedSection,
                    currentSettingsSection = selectedSettingsSection,
                    selectedPlaylistName = selectedPlaylistName,
                    selectedCategoryName = selectedCategoryName,
                    navigationVisible = navigationVisible,
                    liveTvFiltersVisible = liveTvFiltersVisible,
                    playingChannelName = selectedChannel?.name,
                    previewChannelName = previewChannel?.name,
                    playbackStatus = playbackState.status,
                    audioTrackCount = playbackState.audioTracks.size,
                    subtitleTrackCount = playbackState.subtitleTracks.size,
                    lastPlaybackError = lastPlaybackErrorDebug,
                    lastPlaybackEventAtMillis = lastPlaybackEventAtMillis,
                    lastPlaybackStartedAtMillis = lastPlaybackStartedAtMillis,
                    lastPlaybackPausedAtMillis = lastPlaybackPausedAtMillis,
                    lastPlaybackStoppedAtMillis = lastPlaybackStoppedAtMillis,
                    lastPlaybackAutoRetryAtMillis = lastPlaybackAutoRetryAtMillis,
                    lastRecoverablePlaybackErrorAtMillis = lastRecoverablePlaybackErrorAtMillis,
                    lastFatalPlaybackErrorAtMillis = lastFatalPlaybackErrorAtMillis,
                    playbackStartCount = playbackStartCount,
                    playbackBufferingCount = playbackBufferingCount,
                    playbackPauseCount = playbackPauseCount,
                    playbackStopCount = playbackStopCount,
                    playbackManualRetryCount = playbackManualRetryCount,
                    playbackAutoRetryCount = playbackAutoRetryCount,
                    playbackRecoverableErrorCount = playbackRecoverableErrorCount,
                    playbackFatalErrorCount = playbackFatalErrorCount,
                    currentPlaybackErrorStreak = currentPlaybackErrorStreak,
                    worstPlaybackErrorStreak = worstPlaybackErrorStreak,
                    lastEpgSweepAtMillis = lastEpgSweepAtMillis,
                    lastEpgSweepReason = lastEpgSweepReason,
                    lastEpgSweepAttempted = lastEpgSweepAttempted,
                    lastEpgSweepSucceeded = lastEpgSweepSucceeded,
                    lastEpgSweepFailed = lastEpgSweepFailed,
                    lastEpgSweepSkipped = lastEpgSweepSkipped,
                    lastEpgSweepUnconfigured = lastEpgSweepUnconfigured,
                    lastEpgSweepNotDue = lastEpgSweepNotDue,
                    epgSweepCount = epgSweepCount,
                    guideWindowOffset = guideWindowOffset,
                    guideWindowStartMillis = guideWindowStartMillis,
                    startupSurfaceHandled = startupSurfaceHandled,
                    startupBehaviorHandled = startupBehaviorHandled,
                    startupRefreshHandled = startupRefreshHandled,
                    startupEpgRefreshHandled = startupEpgRefreshHandled,
                    onSetStartOnBoot = { enabled ->
                        scope.launch { controller.setStartOnBoot(enabled) }
                    },
                    onSetResumeLastChannelOnStart = { enabled ->
                        scope.launch { controller.setResumeLastChannelOnStart(enabled) }
                    },
                    onSetConfirmExitOnBack = { enabled ->
                        scope.launch { controller.setConfirmExitOnBack(enabled) }
                    },
                    onSetAutoHidePrimaryNavigation = { enabled ->
                        scope.launch { controller.setAutoHidePrimaryNavigation(enabled) }
                        navigationVisible = !enabled
                    },
                    onSetAutoCollapseProviderFilters = { enabled ->
                        scope.launch { controller.setAutoCollapseProviderFilters(enabled) }
                        if (!enabled) {
                            liveTvFiltersVisible = true
                        }
                    },
                    onSetShowChannelNumbers = { enabled ->
                        scope.launch { controller.setShowChannelNumbers(enabled) }
                    },
                    onSetShowChannelMetadata = { enabled ->
                        scope.launch { controller.setShowChannelMetadata(enabled) }
                    },
                    onSetShowSourceLabels = { enabled ->
                        scope.launch { controller.setShowSourceLabels(enabled) }
                    },
                    onSetCompactChannelRows = { enabled ->
                        scope.launch { controller.setCompactChannelRows(enabled) }
                    },
                    onSetCompactProviderRows = { enabled ->
                        scope.launch { controller.setCompactProviderRows(enabled) }
                    },
                    onSetKeepScreenAwakeDuringPlayback = { enabled ->
                        scope.launch { controller.setKeepScreenAwakeDuringPlayback(enabled) }
                    },
                    onSetShowPlaybackStatusBadge = { enabled ->
                        scope.launch { controller.setShowPlaybackStatusBadge(enabled) }
                    },
                    onSetShowPlaybackClock = { enabled ->
                        scope.launch { controller.setShowPlaybackClock(enabled) }
                    },
                    onSetShowPlaybackRecents = { enabled ->
                        scope.launch { controller.setShowPlaybackRecents(enabled) }
                    },
                    onSetShowPlaybackGuideAction = { enabled ->
                        scope.launch { controller.setShowPlaybackGuideAction(enabled) }
                    },
                    onSetShowPlaybackProgressBar = { enabled ->
                        scope.launch { controller.setShowPlaybackProgressBar(enabled) }
                    },
                    onSetShowPlaybackTrackActions = { enabled ->
                        scope.launch { controller.setShowPlaybackTrackActions(enabled) }
                    },
                    onSetShowPlaybackFavoriteAction = { enabled ->
                        scope.launch { controller.setShowPlaybackFavoriteAction(enabled) }
                    },
                    onSetShowPlaybackProgrammeDescription = { enabled ->
                        scope.launch { controller.setShowPlaybackProgrammeDescription(enabled) }
                    },
                    onSetShowPlaybackNowNextPanel = { enabled ->
                        scope.launch { controller.setShowPlaybackNowNextPanel(enabled) }
                    },
                    onSetShowFavoriteFeedbackBanner = { enabled ->
                        scope.launch { controller.setShowFavoriteFeedbackBanner(enabled) }
                    },
                    onCycleRecoverablePlaybackRetryDelay = {
                        scope.launch {
                            controller.setRecoverablePlaybackRetryDelay(
                                appSettings.recoverablePlaybackRetryDelay.next()
                            )
                        }
                    },
                    onSetRetryRecoverablePlaybackErrorsOnce = { enabled ->
                        scope.launch { controller.setRetryRecoverablePlaybackErrorsOnce(enabled) }
                    },
                    onCycleLeaveLiveTvPlaybackBehavior = {
                        scope.launch {
                            controller.setLeaveLiveTvPlaybackBehavior(
                                appSettings.leaveLiveTvPlaybackBehavior.next()
                            )
                        }
                    },
                    onSetOpenProviderFiltersWhenEnteringLiveTv = { enabled ->
                        scope.launch { controller.setOpenProviderFiltersWhenEnteringLiveTv(enabled) }
                    },
                    onSetBackClearsLiveTvFilters = { enabled ->
                        scope.launch { controller.setBackClearsLiveTvFilters(enabled) }
                    },
                    onSetBackHidesLiveTvFiltersFirst = { enabled ->
                        scope.launch { controller.setBackHidesLiveTvFiltersFirst(enabled) }
                    },
                    onSetGuideResetsToNowOnOpen = { enabled ->
                        scope.launch { controller.setGuideResetsToNowOnOpen(enabled) }
                    },
                    onSetGuideStartsWithCurrentChannel = { enabled ->
                        scope.launch { controller.setGuideStartsWithCurrentChannel(enabled) }
                    },
                    onSetGuideUsesPlayingChannelProviderOnOpen = { enabled ->
                        scope.launch { controller.setGuideUsesPlayingChannelProviderOnOpen(enabled) }
                    },
                    onSetGuideClearsCategoryOnOpen = { enabled ->
                        scope.launch { controller.setGuideClearsCategoryOnOpen(enabled) }
                    },
                    onSetBackClearsProviderBeforeCategory = { enabled ->
                        scope.launch { controller.setBackClearsProviderBeforeCategory(enabled) }
                    },
                    onSetBackClosesNavigationFirst = { enabled ->
                        scope.launch { controller.setBackClosesNavigationFirst(enabled) }
                    },
                    onSetBackClearsGuideWindowFirst = { enabled ->
                        scope.launch { controller.setBackClearsGuideWindowFirst(enabled) }
                    },
                    onSetBackClearsGuideProgrammeFirst = { enabled ->
                        scope.launch { controller.setBackClearsGuideProgrammeFirst(enabled) }
                    },
                    onSetBackLeavesSettingsToLiveTv = { enabled ->
                        scope.launch { controller.setBackLeavesSettingsToLiveTv(enabled) }
                    },
                    onSetBackClearsGuideFilters = { enabled ->
                        scope.launch { controller.setBackClearsGuideFilters(enabled) }
                    },
                    onSetPreferProviderFiltersBeforeNavigation = { enabled ->
                        scope.launch { controller.setPreferProviderFiltersBeforeNavigation(enabled) }
                    },
                    onCycleEpgTimeOffset = {
                        scope.launch { controller.setEpgTimeOffset(appSettings.epgTimeOffset.next()) }
                    },
                    onSetEpgRefreshOnAppStart = { enabled ->
                        scope.launch { controller.setEpgRefreshOnAppStart(enabled) }
                    },
                    onCycleEpgUpdateInterval = {
                        scope.launch { controller.setEpgUpdateInterval(appSettings.epgUpdateInterval.next()) }
                    },
                    onSetEpgRefreshDuringSession = { enabled ->
                        scope.launch { controller.setEpgRefreshDuringSession(enabled) }
                    },
                    onCycleEpgRetentionDays = {
                        scope.launch { controller.setEpgRetentionDays(appSettings.epgRetentionDays.next()) }
                    },
                    onOpenGlobalEpg = { showGlobalEpgDialog = true },
                    onRefreshDueEpg = {
                        scope.launch { refreshConfiguredEpg(false, "Manual EPG check") }
                    },
                    onRefreshAllEpg = {
                        scope.launch { refreshConfiguredEpg(true, "Manual EPG refresh") }
                    },
                    refreshingAllEpg = refreshingAllEpg,
                    onAssignSourceToProvider = { playlistId, sourceId, enabled ->
                        scope.launch {
                            val current = controller.getProviderEpgAssignments(playlistId).toMutableList()
                            if (enabled) {
                                if (sourceId !in current) current.add(sourceId)
                            } else {
                                current.remove(sourceId)
                            }
                            controller.setProviderEpgAssignments(playlistId, current)
                        }
                    },
                    onMoveAssignedSourceToFront = { playlistId, sourceId ->
                        scope.launch {
                            val current = controller.getProviderEpgAssignments(playlistId).toMutableList()
                            current.remove(sourceId)
                            current.add(0, sourceId)
                            controller.setProviderEpgAssignments(playlistId, current)
                        }
                    },
                    onDeleteEpgSource = { sourceId ->
                        scope.launch {
                            val result = controller.removeEpgSourceById(sourceId)
                            providerEditStatus = result.fold(
                                onSuccess = { "EPG source removed" },
                                onFailure = { error -> "EPG failed: ${error.message ?: error::class.simpleName}" }
                            )
                        }
                    },
                    onShowNavigation = {
                        navigationVisible = true
                        navigationFocusRestoreKey += 1
                    },
                    onOpenM3uUrl = { showM3uUrlDialog = true },
                    onOpenXtream = { showXtreamDialog = true },
                    onRefreshAllProviders = {
                        scope.launch {
                            refreshingAllProviders = true
                            providerEditStatus = "Refreshing all providers..."
                            val enabledProviders = playlists.filter { playlist ->
                                providerSettings[playlist.id].isImportActive()
                            }
                            val failures = mutableListOf<String>()
                            var refreshedProviders = 0
                            var totalChannels = 0
                            var totalCategories = 0
                            enabledProviders.forEach { playlist ->
                                val result = controller.refreshProvider(playlist)
                                result.fold(
                                    onSuccess = { importResult ->
                                        refreshedProviders += 1
                                        totalChannels += importResult.liveImport?.channelCount ?: 0
                                        totalCategories += importResult.liveImport?.categoryCount ?: 0
                                    },
                                    onFailure = { error ->
                                        failures += "${playlist.displayName()}: ${error.message ?: error::class.simpleName}"
                                    }
                                )
                            }
                            providerEditStatus = if (failures.isEmpty()) {
                                "Refreshed $refreshedProviders providers: $totalChannels channels in $totalCategories groups"
                            } else {
                                "Refresh all failed: ${failures.joinToString(" | ")}"
                            }
                            refreshingAllProviders = false
                        }
                    },
                    refreshingAllProviders = refreshingAllProviders,
                    onEditProvider = { providerToEdit = it }
                )
            } else if (selectedSection == TvSection.Search) {
                LibraryPlaceholderPanel(
                    title = "Search",
                    accentLabel = "Phase 3",
                    summary = "Global search comes after Movies and Series browsing.",
                    detail = "Next target: shared Room-backed search across Live TV, Movies, and Series."
                )
            } else if (selectedSection == TvSection.Movies) {
                val movieSections = remember(visibleMovies, playlists, playlistNameById) {
                    buildVodLibrarySections(
                        items = visibleMovies,
                        orderedPlaylistIds = playlists.map { it.id },
                        playlistNameById = playlistNameById
                    ) { movie ->
                        VodLibraryItemUi(
                            id = movie.id,
                            playlistId = movie.playlistId,
                            title = movie.title,
                            secondary = playlistNameById[movie.playlistId] ?: "Provider",
                            tertiary = buildString {
                                movie.durationMinutes?.let { append("$it min") }
                                movie.releaseDate?.takeIf { it.isNotBlank() }?.let {
                                    if (isNotEmpty()) append(" / ")
                                    append(it)
                                }
                            }.ifBlank { "Movie" },
                            description = movie.plot,
                            badge = "MOVIE",
                            posterUrl = movie.coverUrl
                        )
                    }
                }
                VodLibraryPanel(
                    title = "Movies",
                    accentLabel = if (vodLibraryRefreshing) "Syncing library" else "Imported library",
                    itemCount = visibleMovies.size,
                    sections = movieSections,
                    selectedItemId = selectedMovie?.id,
                    emptyTitle = movieEmptyState.first,
                    emptyBody = movieEmptyState.second,
                    onFocusItem = { selectedMovieId = it },
                    onShowNavigation = {
                        navigationVisible = true
                        navigationFocusRestoreKey += 1
                    },
                    detailContent = { selectedItem ->
                        MovieDetailPanel(
                            controller = controller,
                            selectedItem = selectedItem,
                            movie = selectedMovie,
                            progress = movieProgress,
                            playbackState = playbackState,
                            providerName = selectedMovie?.let { playlistNameById[it.playlistId] },
                            onPlay = { movie, startPositionMs ->
                                focusedChannel = null
                                selectedChannel = null
                                controller.playMovie(movie, startPositionMs)
                            }
                        )
                    }
                )
            } else if (selectedSection == TvSection.Series) {
                val seriesSections = remember(visibleSeries, playlists, playlistNameById) {
                    buildVodLibrarySections(
                        items = visibleSeries,
                        orderedPlaylistIds = playlists.map { it.id },
                        playlistNameById = playlistNameById
                    ) { item ->
                        VodLibraryItemUi(
                            id = item.id,
                            playlistId = item.playlistId,
                            title = item.title,
                            secondary = playlistNameById[item.playlistId] ?: "Provider",
                            tertiary = buildString {
                                item.episodeRunTimeMinutes?.let { append("$it min episodes") }
                                item.releaseDate?.takeIf { it.isNotBlank() }?.let {
                                    if (isNotEmpty()) append(" / ")
                                    append(it)
                                }
                            }.ifBlank { "Series" },
                            description = item.plot,
                            badge = "SERIES",
                            posterUrl = item.coverUrl
                        )
                    }
                }
                VodLibraryPanel(
                    title = "Series",
                    accentLabel = if (vodLibraryRefreshing) "Syncing library" else "Imported library",
                    itemCount = visibleSeries.size,
                    sections = seriesSections,
                    selectedItemId = selectedSeries?.id,
                    emptyTitle = seriesEmptyState.first,
                    emptyBody = seriesEmptyState.second,
                    onFocusItem = { selectedSeriesId = it },
                    onShowNavigation = {
                        navigationVisible = true
                        navigationFocusRestoreKey += 1
                    },
                    detailContent = { selectedItem ->
                        SeriesDetailPanel(
                            controller = controller,
                            selectedItem = selectedItem,
                            series = selectedSeries,
                            seasons = seasons,
                            selectedSeasonId = selectedSeasonId,
                            onSelectSeason = { selectedSeasonId = it },
                            episodes = episodes,
                            selectedEpisodeId = selectedEpisodeId,
                            onSelectEpisode = { selectedEpisodeId = it },
                            selectedEpisode = selectedEpisode,
                            episodeProgress = episodeProgress,
                            playbackState = playbackState,
                            providerName = selectedSeries?.let { playlistNameById[it.playlistId] },
                            onPlayEpisode = { episode, title, startPositionMs ->
                                focusedChannel = null
                                selectedChannel = null
                                controller.playEpisode(episode, title, startPositionMs)
                            }
                        )
                    }
                )
            } else if (selectedSection == TvSection.Guide) {
                EpgGuideScreen(
                    channels = guideChannels,
                    programs = guidePrograms,
                    playlists = playlists,
                    categories = visibleCategories,
                    selectedPlaylistId = selectedPlaylistId,
                    selectedCategoryId = selectedCategoryId,
                    selectedPlaylistName = selectedPlaylistName ?: "All playlists",
                    selectedCategoryName = selectedCategoryName,
                    windowStartMillis = guideWindowStartMillis,
                    windowEndMillis = guideWindowEndMillis,
                    currentTimeMillis = epgClockMillis,
                    selectedProgram = selectedGuideProgram,
                    onSelectPlaylist = { playlistId ->
                        selectedPlaylistId = playlistId
                        selectedCategoryId = null
                        selectedGuideProgram = null
                    },
                    onSelectCategory = { categoryId ->
                        selectedCategoryId = categoryId
                        selectedGuideProgram = null
                    },
                    onShiftWindow = { direction ->
                        guideWindowOffset += direction
                        selectedGuideProgram = null
                    },
                    onResetWindow = {
                        guideWindowOffset = 0
                        selectedGuideProgram = null
                    },
                    onFocusProgram = { selectedGuideProgram = it },
                    onPlayChannel = { channel -> playChannel(channel) }
                )
            } else {
                ChannelBrowserPanel(
                    section = selectedSection,
                    navigationVisible = navigationVisible,
                    filtersVisible = liveTvFiltersVisible,
                    preferProviderFiltersBeforeNavigation = appSettings.preferProviderFiltersBeforeNavigation,
                    autoCollapseProviderFilters = appSettings.autoCollapseProviderFilters,
                    channels = visibleChannels,
                    allChannelCount = channels.size,
                    playlists = playlists,
                    categories = visibleCategories,
                    selectedPlaylistId = selectedPlaylistId,
                    selectedCategoryId = selectedCategoryId,
                    selectedPlaylistName = selectedPlaylistName,
                    selectedCategoryName = selectedCategoryName,
                    selectedChannelId = selectedChannel?.id,
                    previewChannelId = previewChannel?.id,
                    showChannelNumbers = appSettings.showChannelNumbers,
                    showChannelMetadata = appSettings.showChannelMetadata,
                    compactChannelRows = appSettings.compactChannelRows,
                    onFocusChannel = { channel -> focusedChannel = channel },
                    onSelectPlaylist = { playlistId ->
                        selectedPlaylistId = playlistId
                        selectedCategoryId = null
                    },
                    onSelectCategory = { categoryId -> selectedCategoryId = categoryId },
                    onShowNavigation = {
                        navigationVisible = true
                        navigationFocusRestoreKey += 1
                    },
                    onFiltersVisibleChange = { visible -> liveTvFiltersVisible = visible },
                    onPlayChannel = { channel -> playChannel(channel) }
                )

                WatchPanel(
                    controller = controller,
                    playbackUiState = playbackState.toPlaybackUiState(
                        displayChannel = previewChannel,
                        playingChannel = selectedChannel,
                        channelPosition = previewChannel?.let { channel ->
                            visibleChannels.indexOfFirst { it.id == channel.id }
                                .takeIf { it >= 0 }
                                ?.plus(1)
                        },
                        nowNext = nowNext,
                        currentTimeMillis = epgClockMillis,
                        sourceName = previewChannel?.playlistId
                            ?.let { playlistId -> playlists.firstOrNull { it.id == playlistId }?.displayName() },
                        appSettings = appSettings,
                        isFavorite = previewIsFavorite,
                        favoriteFeedback = favoriteFeedback,
                        recentChannels = recentChannels.filterNot { it.id == selectedChannel?.id }.take(5)
                    ),
                    onToggleFavorite = {
                        previewChannel?.let { channel ->
                            scope.launch {
                                controller.setFavorite(
                                    channelId = channel.id,
                                    favorite = !previewIsFavorite
                                )
                                favoriteFeedback = if (previewIsFavorite) {
                                    "${channel.name} removed from favorites"
                                } else {
                                    "${channel.name} added to favorites"
                                }
                            }
                        }
                    },
                    onOpenGuide = {
                        enterGuide()
                    },
                    onPlayRecentChannel = { channel -> playChannel(channel) },
                    onSelectTrack = { track -> controller.selectTrack(track) },
                    onPausePlayback = {
                        playbackPauseCount += 1
                        lastPlaybackPausedAtMillis = System.currentTimeMillis()
                        lastPlaybackEventAtMillis = lastPlaybackPausedAtMillis
                        controller.pausePlayback()
                    },
                    onResumePlayback = {
                        if (playbackState.status is PlaybackStatus.Error) {
                            playbackManualRetryCount += 1
                            lastPlaybackEventAtMillis = System.currentTimeMillis()
                        }
                        selectedChannel?.let { channel -> playChannel(channel) }
                    },
                    onStopPlayback = {
                        playbackStopCount += 1
                        lastPlaybackStoppedAtMillis = System.currentTimeMillis()
                        lastPlaybackEventAtMillis = lastPlaybackStoppedAtMillis
                        controller.stopPlayback()
                    }
                )
            }
        }
        }

        LiveTvDialogOverlays(
            controller = controller,
            uiState = uiState,
            scope = scope,
            channels = channels,
            categories = categories,
            epgSources = epgSources,
            providerSettings = providerSettings,
            providerSyncStates = providerSyncStates,
        )
    }
}

