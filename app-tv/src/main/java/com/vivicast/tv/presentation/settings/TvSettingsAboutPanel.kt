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
fun AboutSettingsPanel(
    playlists: List<Playlist>,
    playlistCount: Int,
    enabledLiveTvProviderCount: Int,
    channelCount: Int,
    categoryCount: Int,
    epgSourceCount: Int,
    epgProgramCount: Int,
    favoriteCount: Int,
    recentCount: Int,
    epgSources: List<EpgSource>,
    providerSettings: Map<String, ProviderUiSettings>,
    providerSyncStates: Map<String, ProviderSyncState>,
    providerEpgAssignments: Map<String, List<String>>,
    appSettings: AppSettings,
    currentSection: TvSection,
    currentSettingsSection: SettingsSection?,
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
    providerStatus: String?,
    epgStatus: String?,
    guideWindowOffset: Int,
    guideWindowStartMillis: Long,
    startupSurfaceHandled: Boolean,
    startupBehaviorHandled: Boolean,
    startupRefreshHandled: Boolean,
    startupEpgRefreshHandled: Boolean
) {
    val context = LocalContext.current
    val providerNameById = remember(playlists) { playlists.associate { it.id to it.displayName() } }
    val packageInfo = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.let { info ->
        info.longVersionCode.toString()
    } ?: "unknown"
    val isDebuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val providerSyncSummary = remember(providerSyncStates) {
        val states = providerSyncStates.values
        val refreshing = states.count { it.refreshing }
        val errors = states.count { !it.refreshing && !it.lastErrorMessage.isNullOrBlank() }
        val synced = states.count { !it.refreshing && it.lastErrorMessage.isNullOrBlank() && it.lastSyncedAtEpochMillis != null }
        "$synced synced / $errors errors / $refreshing refreshing"
    }
    val importTelemetrySummary = remember(providerSyncStates) {
        val states = providerSyncStates.values
        val catchupChannels = states.sumOf { it.lastCatchupChannelCount ?: 0 }
        val ignoredLines = states.sumOf { it.lastIgnoredLineCount ?: 0 }
        val archiveWindowMax = states.mapNotNull { it.lastArchiveWindowDays }.maxOrNull()
        val vodCategoryProbes = states.sumOf { it.lastXtreamVodCategoryCount ?: 0 }
        val seriesCategoryProbes = states.sumOf { it.lastXtreamSeriesCategoryCount ?: 0 }
        buildString {
            append("$catchupChannels catch-up / $ignoredLines ignored lines")
            archiveWindowMax?.let { append(" / up to $it archive days") }
            if (vodCategoryProbes > 0 || seriesCategoryProbes > 0) {
                append(" / Xtream probes $vodCategoryProbes movie + $seriesCategoryProbes series")
            }
        }
    }
    val providerTimingSummary = remember(providerSyncStates) {
        val states = providerSyncStates.values
        val refreshCount = states.count { it.lastRefreshDurationMillis != null }
        val epgCount = states.count { it.lastEpgImportDurationMillis != null }
        val averageRefreshMillis = states.mapNotNull { it.lastRefreshDurationMillis }.average().takeIf { !it.isNaN() }
        val averageEpgMillis = states.mapNotNull { it.lastEpgImportDurationMillis }.average().takeIf { !it.isNaN() }
        buildString {
            append("$refreshCount refresh timings")
            averageRefreshMillis?.let {
                append(" / avg ")
                append(it.toLong().asDurationLabel())
            }
            append(" / $epgCount EPG timings")
            averageEpgMillis?.let {
                append(" / avg ")
                append(it.toLong().asDurationLabel())
            }
        }
    }
    val providerTimingDetailSummary = remember(providerSyncStates, providerNameById) {
        val slowestRefresh = providerSyncStates.maxByOrNull { it.value.lastRefreshDurationMillis ?: -1L }
        val slowestEpg = providerSyncStates.maxByOrNull { it.value.lastEpgImportDurationMillis ?: -1L }
        buildString {
            append("refresh ")
            append(
                slowestRefresh?.let { entry ->
                    "${providerNameById[entry.key] ?: "Provider"} ${entry.value.lastRefreshDurationMillis?.asDurationLabel() ?: "n/a"}"
                } ?: "none"
            )
            append(" / epg ")
            append(
                slowestEpg?.let { entry ->
                    "${providerNameById[entry.key] ?: "Provider"} ${entry.value.lastEpgImportDurationMillis?.asDurationLabel() ?: "n/a"}"
                } ?: "none"
            )
        }
    }
    val providerFailureSummary = remember(providerSyncStates) {
        val states = providerSyncStates.values
        val refreshFailures = states.sumOf { it.refreshFailureCount }
        val refreshConsecutiveMax = states.maxOfOrNull { it.consecutiveRefreshFailureCount } ?: 0
        val epgFailures = states.sumOf { it.epgFailureCount }
        val epgConsecutiveMax = states.maxOfOrNull { it.consecutiveEpgFailureCount } ?: 0
        "$refreshFailures refresh failures / max $refreshConsecutiveMax consecutive / $epgFailures EPG failures / max $epgConsecutiveMax consecutive"
    }
    val providerFailureDetailSummary = remember(providerSyncStates, providerNameById) {
        val worstRefresh = providerSyncStates.maxByOrNull { it.value.consecutiveRefreshFailureCount }
        val worstEpg = providerSyncStates.maxByOrNull { it.value.consecutiveEpgFailureCount }
        buildString {
            append("refresh worst ")
            append(worstRefresh?.value?.consecutiveRefreshFailureCount ?: 0)
            worstRefresh?.let {
                append(" / ")
                append(providerNameById[it.key] ?: "Provider")
            }
            worstRefresh?.value?.lastRefreshSourceLabel?.takeIf { it.isNotBlank() }?.let {
                append(" @ ")
                append(it)
            }
            append(" / epg worst ")
            append(worstEpg?.value?.consecutiveEpgFailureCount ?: 0)
            worstEpg?.let {
                append(" / ")
                append(providerNameById[it.key] ?: "Provider")
            }
            worstEpg?.value?.lastEpgErrorMessage?.takeIf { it.isNotBlank() }?.let {
                append(" / ")
                append(it)
            }
        }
    }
    val providerHealthDetailSummary = remember(providerSyncStates, providerNameById) {
        val refreshWorstRate = providerSyncStates
            .mapNotNull { (playlistId, state) ->
                state.refreshSuccessRate()?.let { rate -> Triple(rate, providerNameById[playlistId] ?: "Provider", state.refreshAttemptCount()) }
            }
            .minByOrNull { it.first }
        val epgWorstRate = providerSyncStates
            .mapNotNull { (playlistId, state) ->
                state.epgSuccessRate()?.let { rate -> Triple(rate, providerNameById[playlistId] ?: "Provider", state.epgAttemptCount()) }
            }
            .minByOrNull { it.first }
        buildString {
            append("refresh ")
            append(
                refreshWorstRate?.let { "${it.second} ${it.first}% (${it.third})" } ?: "none"
            )
            append(" / epg ")
            append(
                epgWorstRate?.let { "${it.second} ${it.first}% (${it.third})" } ?: "none"
            )
        }
    }
    val latestProviderActivitySummary = remember(providerSyncStates, providerNameById) {
        val latestActivity = providerSyncStates
            .mapNotNull { (playlistId, state) ->
                state.latestActivityAt()?.let { at ->
                    Triple(at, providerNameById[playlistId] ?: "Provider", state.latestActivityLabel())
                }
            }
            .maxByOrNull { it.first }
        latestActivity?.let { activity ->
            "${activity.second} / ${activity.third} / ${activity.first.asShortDateTime()}"
        } ?: "No provider activity yet"
    }
    val epgTelemetrySummary = remember(providerSyncStates) {
        val states = providerSyncStates.values
        val matched = states.sumOf { it.lastEpgImportedProgramCount ?: 0 }
        val unmatched = states.sumOf { it.lastEpgUnmatchedProgramCount ?: 0 }
        val xmltvChannels = states.sumOf { it.lastEpgXmltvChannelCount ?: 0 }
        val epgErrors = states.count { !it.lastEpgErrorMessage.isNullOrBlank() }
        "$matched matched / $unmatched unmatched / $xmltvChannels XMLTV / $epgErrors EPG errors"
    }
    val epgCoverageSummary = remember(playlists, providerSettings, epgSources, providerEpgAssignments) {
        val activeProviders = playlists.filter { providerSettings[it.id].isLiveTvActive() }
        val configured = activeProviders.count { playlist ->
            epgSources.any { it.playlistId == playlist.id } ||
                (providerEpgAssignments[playlist.id] ?: emptyList()).isNotEmpty()
        }
        val unconfigured = activeProviders.size - configured
        "$configured configured / $unconfigured unconfigured / ${activeProviders.size} active providers"
    }
    val epgNextDueSummary = remember(playlists, providerSettings, epgSources, providerEpgAssignments, appSettings.epgUpdateInterval) {
        if (appSettings.epgUpdateInterval == EpgUpdateInterval.Manual) {
            "Manual only"
        } else {
            val activeProviders = playlists.filter { providerSettings[it.id].isLiveTvActive() }
            val nextDue = activeProviders.mapNotNull { playlist ->
                val direct = epgSources.firstOrNull { it.playlistId == playlist.id }
                val assigned = (providerEpgAssignments[playlist.id] ?: emptyList())
                    .mapNotNull { sourceId -> epgSources.firstOrNull { it.id == sourceId } }
                    .firstOrNull()
                val source = direct ?: assigned ?: return@mapNotNull null
                appSettings.epgUpdateInterval.nextDueAt(source.lastImportedAtEpochMillis)
            }.minOrNull()
            nextDue?.let { "next due ${it.asShortDateTime()}" } ?: "No configured EPG sources"
        }
    }
    val latestFailureSummary = remember(providerSyncStates, providerNameById) {
        val latestRefreshFailure = providerSyncStates.mapNotNull { (playlistId, state) ->
            state.lastErrorAtEpochMillis?.let { at ->
                Triple(at, providerNameById[playlistId] ?: "Provider", state.lastErrorMessage ?: "Refresh error")
            }
        }.maxByOrNull { it.first }
        val latestEpgFailure = providerSyncStates.mapNotNull { (playlistId, state) ->
            state.lastEpgErrorAtEpochMillis?.let { at ->
                Triple(at, providerNameById[playlistId] ?: "Provider", state.lastEpgErrorMessage ?: "EPG error")
            }
        }.maxByOrNull { it.first }
        buildString {
            append("refresh ")
            append(latestRefreshFailure?.first?.asShortDateTime() ?: "none")
            latestRefreshFailure?.second?.let {
                append(" / ")
                append(it)
            }
            latestRefreshFailure?.third?.let {
                append(" / ")
                append(it)
            }
            append(" / epg ")
            append(latestEpgFailure?.first?.asShortDateTime() ?: "none")
            latestEpgFailure?.second?.let {
                append(" / ")
                append(it)
            }
            latestEpgFailure?.third?.let {
                append(" / ")
                append(it)
            }
        }
    }
    val startupSummary = remember(appSettings) {
        buildString {
            append("Live TV")
            if (appSettings.resumeLastChannelOnStart) {
                append(" / resume last channel")
            }
            if (appSettings.startOnBoot) {
                append(" / boot flag on")
            }
        }
    }
    val epgPolicySummary = remember(appSettings) {
        buildString {
            append(appSettings.epgUpdateInterval.label)
            append(" / ")
            append(if (appSettings.epgRefreshOnAppStart) "startup on" else "startup off")
            append(" / ")
            append(if (appSettings.epgRefreshDuringSession) "session checks on" else "session checks off")
            append(" / ")
            append(appSettings.epgRetentionDays.label)
            append(" / ")
            append(appSettings.epgTimeOffset.label)
        }
    }
    val epgSweepSummary = remember(
        lastEpgSweepAtMillis,
        lastEpgSweepReason,
        lastEpgSweepAttempted,
        lastEpgSweepSucceeded,
        lastEpgSweepFailed,
        lastEpgSweepSkipped,
        lastEpgSweepUnconfigured,
        lastEpgSweepNotDue,
        epgSweepCount
    ) {
        buildString {
            append(lastEpgSweepReason ?: "No EPG sweep yet")
            if (lastEpgSweepReason != null) {
                append(" / ")
                append("$lastEpgSweepSucceeded ok")
                append(" / ")
                append("$lastEpgSweepFailed failed")
                append(" / ")
                append("$lastEpgSweepNotDue not due")
                append(" / ")
                append("$lastEpgSweepUnconfigured unconfigured")
                append(" / ")
                append("$lastEpgSweepSkipped skipped")
                append(" / ")
                append("$lastEpgSweepAttempted due")
                append(" / sweep ")
                append(epgSweepCount)
                lastEpgSweepAtMillis?.let {
                    append(" / ")
                    append(it.asShortDateTime())
                }
            }
        }
    }
    val playbackSettingsSummary = remember(appSettings) {
        buildString {
            append(if (appSettings.showPlaybackStatusBadge) "status" else "no status")
            append(" / ")
            append(if (appSettings.showPlaybackClock) "clock" else "no clock")
            append(" / ")
            append(if (appSettings.showPlaybackGuideAction) "guide chip" else "no guide chip")
            append(" / ")
            append(if (appSettings.showPlaybackProgressBar) "progress" else "no progress")
            append(" / ")
            append(if (appSettings.showPlaybackRecents) "recents" else "no recents")
            append(" / ")
            append(if (appSettings.showPlaybackTrackActions) "tracks" else "no tracks")
            append(" / ")
            append(if (appSettings.showPlaybackFavoriteAction) "favorite ui" else "no favorite ui")
            append(" / ")
            append(if (appSettings.showPlaybackProgrammeDescription) "descriptions" else "no descriptions")
            append(" / ")
            append(appSettings.leaveLiveTvPlaybackBehavior.label)
            append(" / ")
            append(appSettings.recoverablePlaybackRetryDelay.label)
        }
    }
    val tvUxSummary = remember(appSettings, providerSettings) {
        val vodEnabledProviders = providerSettings.values.count { it.vodEnabled }
        buildString {
            append(if (appSettings.autoHidePrimaryNavigation) "nav auto-hide" else "nav pinned")
            append(" / ")
            append(if (appSettings.autoCollapseProviderFilters) "filters collapse" else "filters stay open")
            append(" / ")
            append(if (appSettings.preferProviderFiltersBeforeNavigation) "left -> filters" else "left -> nav")
            if (vodEnabledProviders > 0) {
                append(" / $vodEnabledProviders VOD-scoped providers saved")
            }
        }
    }
    val sessionSurfaceSummary = remember(
        currentSection,
        currentSettingsSection,
        selectedPlaylistName,
        selectedCategoryName,
        navigationVisible,
        liveTvFiltersVisible
    ) {
        buildString {
            append(currentSection.label())
            currentSettingsSection?.let {
                append(" / ")
                append(it.label)
            }
            selectedPlaylistName?.let {
                append(" / provider ")
                append(it)
            }
            selectedCategoryName?.let {
                append(" / group ")
                append(it)
            }
            append(" / nav ")
            append(if (navigationVisible) "open" else "closed")
            append(" / filters ")
            append(if (liveTvFiltersVisible) "open" else "closed")
        }
    }
    val playbackDebugSummary = remember(
        playingChannelName,
        previewChannelName,
        playbackStatus,
        audioTrackCount,
        subtitleTrackCount,
        lastPlaybackError
    ) {
        buildString {
            append(playbackStatus.asText())
            playingChannelName?.let {
                append(" / playing ")
                append(it)
            }
            previewChannelName?.takeIf { it != playingChannelName }?.let {
                append(" / preview ")
                append(it)
            }
            append(" / ")
            append(audioTrackCount)
            append(" audio / ")
            append(subtitleTrackCount)
            append(" subtitle")
            lastPlaybackError?.let {
                append(" / ")
                append(it)
            }
        }
    }
    val playbackTelemetrySummary = remember(
        playbackStartCount,
        playbackBufferingCount,
        playbackPauseCount,
        playbackStopCount,
        playbackManualRetryCount,
        playbackAutoRetryCount,
        playbackRecoverableErrorCount,
        playbackFatalErrorCount,
        lastPlaybackEventAtMillis
    ) {
        buildString {
            append("$playbackStartCount starts / $playbackBufferingCount buffering")
            append(" / $playbackPauseCount pauses / $playbackStopCount stops")
            append(" / $playbackManualRetryCount manual retries")
            append(" / $playbackAutoRetryCount auto retries")
            append(" / $playbackRecoverableErrorCount recoverable errors")
            append(" / $playbackFatalErrorCount fatal errors")
            lastPlaybackEventAtMillis?.let {
                append(" / last event ")
                append(it.asShortDateTime())
            }
        }
    }
    val playbackLifecycleSummary = remember(playbackStartCount, playbackBufferingCount, lastPlaybackStartedAtMillis) {
        buildString {
            append("$playbackStartCount starts / $playbackBufferingCount buffering")
            lastPlaybackStartedAtMillis?.let {
                append(" / last start ")
                append(it.asShortDateTime())
            }
        }
    }
    val playbackControlSummary = remember(
        appSettings,
        lastPlaybackPausedAtMillis,
        lastPlaybackStoppedAtMillis,
        lastPlaybackAutoRetryAtMillis
    ) {
        buildString {
            append(appSettings.leaveLiveTvPlaybackBehavior.label)
            append(" / ")
            append(if (appSettings.keepScreenAwakeDuringPlayback) "keep awake" else "screen may sleep")
            append(" / retry ")
            append(if (appSettings.retryRecoverablePlaybackErrorsOnce) "once" else "off")
            append(" @ ")
            append(appSettings.recoverablePlaybackRetryDelay.label)
            lastPlaybackPausedAtMillis?.let {
                append(" / pause ")
                append(it.asShortDateTime())
            }
            lastPlaybackStoppedAtMillis?.let {
                append(" / stop ")
                append(it.asShortDateTime())
            }
            lastPlaybackAutoRetryAtMillis?.let {
                append(" / auto retry ")
                append(it.asShortDateTime())
            }
        }
    }
    val playbackFailureDetailSummary = remember(
        lastRecoverablePlaybackErrorAtMillis,
        lastFatalPlaybackErrorAtMillis,
        currentPlaybackErrorStreak,
        worstPlaybackErrorStreak
    ) {
        buildString {
            append("streak ")
            append(currentPlaybackErrorStreak)
            append(" / worst ")
            append(worstPlaybackErrorStreak)
            append(" / recoverable ")
            append(lastRecoverablePlaybackErrorAtMillis?.asShortDateTime() ?: "none")
            append(" / fatal ")
            append(lastFatalPlaybackErrorAtMillis?.asShortDateTime() ?: "none")
        }
    }
    val startupDebugSummary = remember(
        startupSurfaceHandled,
        startupBehaviorHandled,
        startupRefreshHandled,
        startupEpgRefreshHandled
    ) {
        buildString {
            append(if (startupSurfaceHandled) "surface ok" else "surface pending")
            append(" / ")
            append(if (startupBehaviorHandled) "startup ok" else "startup pending")
            append(" / ")
            append(if (startupRefreshHandled) "provider refresh checked" else "provider refresh pending")
            append(" / ")
            append(if (startupEpgRefreshHandled) "epg checked" else "epg pending")
        }
    }
    val guideDebugSummary = remember(guideWindowOffset, guideWindowStartMillis, appSettings) {
        buildString {
            append(if (appSettings.guideResetsToNowOnOpen) "reset-to-now" else "keep shifted window")
            append(" / ")
            append(if (appSettings.guideStartsWithCurrentChannel) "current-channel first" else "visible-order first")
            append(" / ")
            append(if (appSettings.guideUsesPlayingChannelProviderOnOpen) "playing-provider on open" else "keep provider on open")
            append(" / ")
            append("offset ")
            append(guideWindowOffset)
            append(" / ")
            append(guideWindowStartMillis.asShortDateTime())
        }
    }
    val backRoutingSummary = remember(appSettings) {
        buildString {
            append(if (appSettings.backClosesNavigationFirst) "nav closes first" else "nav stays in back chain")
            append(" / ")
            append(if (appSettings.backLeavesSettingsToLiveTv) "settings -> live tv" else "settings -> app exit")
            append(" / ")
            append(if (appSettings.backHidesLiveTvFiltersFirst) "hide filters first" else "clear filters first")
            append(" / ")
            append(if (appSettings.backClearsProviderBeforeCategory) "provider before group" else "group before provider")
            append(" / ")
            append(if (appSettings.backClearsGuideProgrammeFirst) "guide details first" else "skip details step")
            append(" / ")
            append(if (appSettings.backClearsGuideFilters) "guide filters clear" else "guide exits sooner")
        }
    }
    val lastOperationSummary = remember(providerStatus, epgStatus) {
        buildString {
            append(providerStatus ?: "No provider operation yet")
            append(" / ")
            append(epgStatus ?: "No EPG operation yet")
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsValueRow(
            label = "App",
            value = "ViviCast Android TV MVP",
            description = "Android-only IPTV player."
        )
        SettingsValueRow(
            label = "Player stack",
            value = "Media3 / ExoPlayer",
            description = "TextureView-backed TV player path."
        )
        SettingsValueRow(
            label = "Reference TV",
            value = "Xiaomi Mi Smart TV 4S"
        )
        SettingsValueRow(
            label = "Version",
            value = versionName,
            description = "Version code $versionCode"
        )
        SettingsValueRow(
            label = "Package",
            value = context.packageName,
            description = if (isDebuggable) "Debug build" else "Release build"
        )
        SettingsValueRow(
            label = "Database schema",
            value = "Room v2",
            description = "Provider state is stored locally."
        )
        SettingsValueRow(
            label = "Local library",
            value = "$channelCount channels / $categoryCount groups",
            description = "$playlistCount providers, $enabledLiveTvProviderCount active."
        )
        SettingsValueRow(
            label = "EPG store",
            value = "$epgProgramCount programmes",
            description = "$epgSourceCount sources."
        )
        SettingsValueRow(
            label = "User data",
            value = "$favoriteCount favorites / $recentCount recents",
            description = "Stored locally."
        )
        SettingsValueRow(
            label = "Provider sync health",
            value = providerSyncSummary,
            description = "Refresh state across providers."
        )
        SettingsValueRow(
            label = "Import telemetry",
            value = importTelemetrySummary,
            description = "Catch-up, ignored lines, archive hints, Xtream probes."
        )
        SettingsValueRow(
            label = "Provider timings",
            value = providerTimingSummary,
            description = "Average refresh and EPG durations."
        )
        SettingsValueRow(
            label = "Timing detail",
            value = providerTimingDetailSummary,
            description = "Slowest recent refresh and EPG providers."
        )
        SettingsValueRow(
            label = "Failure counters",
            value = providerFailureSummary,
            description = "Refresh and EPG failures."
        )
        SettingsValueRow(
            label = "Failure detail",
            value = providerFailureDetailSummary,
            description = "Worst local refresh and EPG streak context."
        )
        SettingsValueRow(
            label = "Provider health detail",
            value = providerHealthDetailSummary,
            description = "Weakest refresh and EPG success rates."
        )
        SettingsValueRow(
            label = "Latest provider activity",
            value = latestProviderActivitySummary,
            description = "Most recent refresh or EPG event."
        )
        SettingsValueRow(
            label = "EPG telemetry",
            value = epgTelemetrySummary,
            description = "Matched, unmatched, XMLTV, errors."
        )
        SettingsValueRow(
            label = "EPG coverage",
            value = epgCoverageSummary,
            description = "Active providers with configured EPG."
        )
        SettingsValueRow(
            label = "EPG policy",
            value = epgPolicySummary,
            description = "Stored refresh, retention, and offset policy."
        )
        SettingsValueRow(
            label = "Next EPG due",
            value = epgNextDueSummary,
            description = "Earliest next due import across active providers."
        )
        SettingsValueRow(
            label = "Latest EPG sweep",
            value = epgSweepSummary,
            description = "Most recent due-check or refresh result."
        )
        SettingsValueRow(
            label = "Startup behavior",
            value = startupSummary,
            description = "Used when no recent channel is resumed."
        )
        SettingsValueRow(
            label = "Playback settings",
            value = playbackSettingsSummary,
            description = "Current overlay behavior toggles."
        )
        SettingsValueRow(
            label = "TV UX mode",
            value = tvUxSummary,
            description = "Navigation, filters, provider mode."
        )
        SettingsValueRow(
            label = "Current surface",
            value = sessionSurfaceSummary,
            description = "Section, filters, shell state."
        )
        SettingsValueRow(
            label = "Playback debug",
            value = playbackDebugSummary,
            description = "State, channel context, track counts."
        )
        SettingsValueRow(
            label = "Playback lifecycle",
            value = playbackLifecycleSummary,
            description = "Starts, buffering, and last successful start."
        )
        SettingsValueRow(
            label = "Playback control",
            value = playbackControlSummary,
            description = "Leave-Live-TV policy, retry policy, latest control events."
        )
        SettingsValueRow(
            label = "Playback telemetry",
            value = playbackTelemetrySummary,
            description = "Current-session playback counters."
        )
        SettingsValueRow(
            label = "Playback failure detail",
            value = playbackFailureDetailSummary,
            description = "Recent playback error timing and streak depth."
        )
        SettingsValueRow(
            label = "Guide debug",
            value = guideDebugSummary,
            description = "Guide behavior and window anchor."
        )
        SettingsValueRow(
            label = "Back routing",
            value = backRoutingSummary,
            description = "Current Back-chain behavior across shell and Guide."
        )
        SettingsValueRow(
            label = "Startup debug",
            value = startupDebugSummary,
            description = "Startup handlers."
        )
        SettingsValueRow(
            label = "Last operations",
            value = lastOperationSummary,
            description = "Latest provider and EPG messages."
        )
        SettingsValueRow(
            label = "Latest failures",
            value = latestFailureSummary,
            description = "Most recent local refresh and EPG failure context."
        )
    }
}

