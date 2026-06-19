package com.vivicast.tv

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.RecentChannel
import java.util.Locale
@Composable
fun ProviderSettingsScreen(
    selectedSection: SettingsSection,
    onSelectSection: (SettingsSection) -> Unit,
    focusRestoreKey: Int,
    selectedEpgDetailSourceId: String?,
    onOpenEpgDetail: (String) -> Unit,
    onCloseEpgDetail: () -> Unit,
    playlists: List<Playlist>,
    channels: List<Channel>,
    categories: List<ChannelCategory>,
    epgSources: List<EpgSource>,
    epgProgramCount: Int,
    favorites: List<FavoriteChannel>,
    recents: List<RecentChannel>,
    providerSettings: Map<String, ProviderUiSettings>,
    providerSyncStates: Map<String, ProviderSyncState>,
    providerEpgAssignments: Map<String, List<String>>,
    appSettings: AppSettings,
    status: String?,
    epgStatus: String?,
    currentSection: TvSection,
    currentSettingsSection: SettingsSection,
    selectedPlaylistName: String?,
    selectedCategoryName: String?,
    navigationVisible: Boolean,
    liveTvFiltersVisible: Boolean,
    playingChannelName: String?,
    previewChannelName: String?,
    playbackStatus: PlaybackStatus,
    audioTrackCount: Int,
    subtitleTrackCount: Int,
    lastPlaybackError: String?,
    lastPlaybackEventAtMillis: Long?,
    lastPlaybackStartedAtMillis: Long?,
    lastPlaybackPausedAtMillis: Long?,
    lastPlaybackStoppedAtMillis: Long?,
    lastPlaybackAutoRetryAtMillis: Long?,
    lastRecoverablePlaybackErrorAtMillis: Long?,
    lastFatalPlaybackErrorAtMillis: Long?,
    playbackStartCount: Int,
    playbackBufferingCount: Int,
    playbackPauseCount: Int,
    playbackStopCount: Int,
    playbackManualRetryCount: Int,
    playbackAutoRetryCount: Int,
    playbackRecoverableErrorCount: Int,
    playbackFatalErrorCount: Int,
    currentPlaybackErrorStreak: Int,
    worstPlaybackErrorStreak: Int,
    lastEpgSweepAtMillis: Long?,
    lastEpgSweepReason: String?,
    lastEpgSweepAttempted: Int,
    lastEpgSweepSucceeded: Int,
    lastEpgSweepFailed: Int,
    lastEpgSweepSkipped: Int,
    lastEpgSweepUnconfigured: Int,
    lastEpgSweepNotDue: Int,
    epgSweepCount: Int,
    guideWindowOffset: Int,
    guideWindowStartMillis: Long,
    startupSurfaceHandled: Boolean,
    startupBehaviorHandled: Boolean,
    startupRefreshHandled: Boolean,
    startupEpgRefreshHandled: Boolean,
    onSetStartOnBoot: (Boolean) -> Unit,
    onSetResumeLastChannelOnStart: (Boolean) -> Unit,
    onSetConfirmExitOnBack: (Boolean) -> Unit,
    onSetAutoHidePrimaryNavigation: (Boolean) -> Unit,
    onSetAutoCollapseProviderFilters: (Boolean) -> Unit,
    onSetShowChannelNumbers: (Boolean) -> Unit,
    onSetShowChannelMetadata: (Boolean) -> Unit,
    onSetShowSourceLabels: (Boolean) -> Unit,
    onSetCompactChannelRows: (Boolean) -> Unit,
    onSetCompactProviderRows: (Boolean) -> Unit,
    onSetKeepScreenAwakeDuringPlayback: (Boolean) -> Unit,
    onSetShowPlaybackStatusBadge: (Boolean) -> Unit,
    onSetShowPlaybackClock: (Boolean) -> Unit,
    onSetShowPlaybackRecents: (Boolean) -> Unit,
    onSetShowPlaybackGuideAction: (Boolean) -> Unit,
    onSetShowPlaybackProgressBar: (Boolean) -> Unit,
    onSetShowPlaybackTrackActions: (Boolean) -> Unit,
    onSetShowPlaybackFavoriteAction: (Boolean) -> Unit,
    onSetShowPlaybackProgrammeDescription: (Boolean) -> Unit,
    onSetShowPlaybackNowNextPanel: (Boolean) -> Unit,
    onSetShowFavoriteFeedbackBanner: (Boolean) -> Unit,
    onCycleRecoverablePlaybackRetryDelay: () -> Unit,
    onSetRetryRecoverablePlaybackErrorsOnce: (Boolean) -> Unit,
    onCycleLeaveLiveTvPlaybackBehavior: () -> Unit,
    onSetOpenProviderFiltersWhenEnteringLiveTv: (Boolean) -> Unit,
    onSetBackClearsLiveTvFilters: (Boolean) -> Unit,
    onSetBackHidesLiveTvFiltersFirst: (Boolean) -> Unit,
    onSetGuideResetsToNowOnOpen: (Boolean) -> Unit,
    onSetGuideStartsWithCurrentChannel: (Boolean) -> Unit,
    onSetGuideUsesPlayingChannelProviderOnOpen: (Boolean) -> Unit,
    onSetGuideClearsCategoryOnOpen: (Boolean) -> Unit,
    onSetBackClearsProviderBeforeCategory: (Boolean) -> Unit,
    onSetBackClosesNavigationFirst: (Boolean) -> Unit,
    onSetBackClearsGuideWindowFirst: (Boolean) -> Unit,
    onSetBackClearsGuideProgrammeFirst: (Boolean) -> Unit,
    onSetBackLeavesSettingsToLiveTv: (Boolean) -> Unit,
    onSetBackClearsGuideFilters: (Boolean) -> Unit,
    onSetPreferProviderFiltersBeforeNavigation: (Boolean) -> Unit,
    onCycleEpgTimeOffset: () -> Unit,
    onSetEpgRefreshOnAppStart: (Boolean) -> Unit,
    onCycleEpgUpdateInterval: () -> Unit,
    onSetEpgRefreshDuringSession: (Boolean) -> Unit,
    onCycleEpgRetentionDays: () -> Unit,
    onOpenGlobalEpg: () -> Unit,
    onRefreshDueEpg: () -> Unit,
    onRefreshAllEpg: () -> Unit,
    refreshingAllEpg: Boolean,
    onAssignSourceToProvider: (String, String, Boolean) -> Unit,
    onMoveAssignedSourceToFront: (String, String) -> Unit,
    onDeleteEpgSource: (String) -> Unit,
    onShowNavigation: () -> Unit,
    onOpenM3uUrl: () -> Unit,
    onOpenXtream: () -> Unit,
    onRefreshAllProviders: () -> Unit,
    refreshingAllProviders: Boolean,
    onEditProvider: (Playlist) -> Unit
) {
    val channelCountByPlaylist = remember(channels) {
        channels.groupingBy { it.playlistId }.eachCount()
    }

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (selectedEpgDetailSourceId == null) {
            SettingsSectionRail(
                selectedSection = selectedSection,
                onSelectSection = onSelectSection,
                focusRestoreKey = focusRestoreKey,
                onMoveLeft = onShowNavigation
            )
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                SettingsHeader(
                    subtitle = selectedSection.label,
                    trailing = when (selectedSection) {
                    SettingsSection.Providers -> ""
                    SettingsSection.Epg -> ""
                    else -> "Android TV MVP"
                }
            )

            when (selectedSection) {
                SettingsSection.General -> GeneralSettingsPanel(
                    settings = appSettings,
                    onSetStartOnBoot = onSetStartOnBoot,
                    onSetResumeLastChannelOnStart = onSetResumeLastChannelOnStart,
                    onSetConfirmExitOnBack = onSetConfirmExitOnBack
                )

                SettingsSection.Providers -> ProvidersSettingsPanel(
                    playlists = playlists,
                    channelCountByPlaylist = channelCountByPlaylist,
                    epgSources = epgSources,
                    providerSettings = providerSettings,
                    providerSyncStates = providerSyncStates,
                    status = status,
                    onOpenM3uUrl = onOpenM3uUrl,
                    onOpenXtream = onOpenXtream,
                    onRefreshAllProviders = onRefreshAllProviders,
                    refreshingAllProviders = refreshingAllProviders,
                    compactProviderRows = appSettings.compactProviderRows,
                    showSourceLabels = appSettings.showSourceLabels,
                    onEditProvider = onEditProvider
                )

                SettingsSection.Epg -> EpgSettingsPanel(
                    epgSources = epgSources,
                    playlists = playlists,
                    providerEpgAssignments = providerEpgAssignments,
                    settings = appSettings,
                    status = epgStatus,
                    onCycleEpgTimeOffset = onCycleEpgTimeOffset,
                    onSetEpgRefreshOnAppStart = onSetEpgRefreshOnAppStart,
                    onCycleEpgUpdateInterval = onCycleEpgUpdateInterval,
                    onSetEpgRefreshDuringSession = onSetEpgRefreshDuringSession,
                    onCycleEpgRetentionDays = onCycleEpgRetentionDays,
                    onOpenGlobalEpg = onOpenGlobalEpg,
                    onRefreshDueEpg = onRefreshDueEpg,
                    onRefreshAllEpg = onRefreshAllEpg,
                    refreshingAllEpg = refreshingAllEpg,
                    selectedSourceId = selectedEpgDetailSourceId,
                    onOpenSourceDetail = onOpenEpgDetail,
                    onCloseSourceDetail = onCloseEpgDetail,
                    onAssignSourceToProvider = onAssignSourceToProvider,
                    onMoveAssignedSourceToFront = onMoveAssignedSourceToFront,
                    onDeleteEpgSource = onDeleteEpgSource
                )

                SettingsSection.Appearance -> AppearanceSettingsPanel(
                    settings = appSettings,
                    onSetAutoHidePrimaryNavigation = onSetAutoHidePrimaryNavigation,
                    onSetAutoCollapseProviderFilters = onSetAutoCollapseProviderFilters,
                    onSetShowChannelNumbers = onSetShowChannelNumbers,
                    onSetShowChannelMetadata = onSetShowChannelMetadata,
                    onSetShowSourceLabels = onSetShowSourceLabels,
                    onSetCompactChannelRows = onSetCompactChannelRows,
                    onSetCompactProviderRows = onSetCompactProviderRows
                )

                SettingsSection.Playback -> PlaybackSettingsPanel(
                    settings = appSettings,
                    onSetKeepScreenAwakeDuringPlayback = onSetKeepScreenAwakeDuringPlayback,
                    onSetShowPlaybackStatusBadge = onSetShowPlaybackStatusBadge,
                    onSetShowPlaybackClock = onSetShowPlaybackClock,
                    onSetShowPlaybackRecents = onSetShowPlaybackRecents,
                    onSetShowPlaybackGuideAction = onSetShowPlaybackGuideAction,
                    onSetShowPlaybackProgressBar = onSetShowPlaybackProgressBar,
                    onSetShowPlaybackTrackActions = onSetShowPlaybackTrackActions,
                    onSetShowPlaybackFavoriteAction = onSetShowPlaybackFavoriteAction,
                    onSetShowPlaybackProgrammeDescription = onSetShowPlaybackProgrammeDescription,
                    onSetShowPlaybackNowNextPanel = onSetShowPlaybackNowNextPanel,
                    onSetShowFavoriteFeedbackBanner = onSetShowFavoriteFeedbackBanner,
                    onCycleRecoverablePlaybackRetryDelay = onCycleRecoverablePlaybackRetryDelay,
                    onSetRetryRecoverablePlaybackErrorsOnce = onSetRetryRecoverablePlaybackErrorsOnce,
                    onCycleLeaveLiveTvPlaybackBehavior = onCycleLeaveLiveTvPlaybackBehavior
                )

                SettingsSection.Remote -> RemoteControlSettingsPanel(
                    settings = appSettings,
                    onSetOpenProviderFiltersWhenEnteringLiveTv = onSetOpenProviderFiltersWhenEnteringLiveTv,
                    onSetBackClearsLiveTvFilters = onSetBackClearsLiveTvFilters,
                    onSetBackHidesLiveTvFiltersFirst = onSetBackHidesLiveTvFiltersFirst,
                    onSetGuideResetsToNowOnOpen = onSetGuideResetsToNowOnOpen,
                    onSetGuideStartsWithCurrentChannel = onSetGuideStartsWithCurrentChannel,
                    onSetGuideUsesPlayingChannelProviderOnOpen = onSetGuideUsesPlayingChannelProviderOnOpen,
                    onSetGuideClearsCategoryOnOpen = onSetGuideClearsCategoryOnOpen,
                    onSetBackClearsProviderBeforeCategory = onSetBackClearsProviderBeforeCategory,
                    onSetBackClosesNavigationFirst = onSetBackClosesNavigationFirst,
                    onSetBackClearsGuideWindowFirst = onSetBackClearsGuideWindowFirst,
                    onSetBackClearsGuideProgrammeFirst = onSetBackClearsGuideProgrammeFirst,
                    onSetBackLeavesSettingsToLiveTv = onSetBackLeavesSettingsToLiveTv,
                    onSetBackClearsGuideFilters = onSetBackClearsGuideFilters,
                    onSetPreferProviderFiltersBeforeNavigation = onSetPreferProviderFiltersBeforeNavigation
                )

                SettingsSection.About -> AboutSettingsPanel(
                    playlists = playlists,
                    playlistCount = playlists.size,
                    enabledLiveTvProviderCount = playlists.count { providerSettings[it.id].isLiveTvActive() },
                    channelCount = channels.size,
                    categoryCount = categories.size,
                    epgSourceCount = epgSources.size,
                    epgProgramCount = epgProgramCount,
                    favoriteCount = favorites.size,
                    recentCount = recents.size,
                    epgSources = epgSources,
                    providerSettings = providerSettings,
                    providerSyncStates = providerSyncStates,
                    providerEpgAssignments = providerEpgAssignments,
                    appSettings = appSettings,
                    currentSection = currentSection,
                    currentSettingsSection = currentSettingsSection,
                    selectedPlaylistName = selectedPlaylistName,
                    selectedCategoryName = selectedCategoryName,
                    navigationVisible = navigationVisible,
                    liveTvFiltersVisible = liveTvFiltersVisible,
                    playingChannelName = playingChannelName,
                    previewChannelName = previewChannelName,
                    playbackStatus = playbackStatus,
                    audioTrackCount = audioTrackCount,
                    subtitleTrackCount = subtitleTrackCount,
                    lastPlaybackError = lastPlaybackError,
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
                    providerStatus = status,
                    epgStatus = epgStatus,
                    guideWindowOffset = guideWindowOffset,
                    guideWindowStartMillis = guideWindowStartMillis,
                    startupSurfaceHandled = startupSurfaceHandled,
                    startupBehaviorHandled = startupBehaviorHandled,
                    startupRefreshHandled = startupRefreshHandled,
                    startupEpgRefreshHandled = startupEpgRefreshHandled
                )
            }
        }
    }
}

