package com.vivicast.tv

import android.content.Context
import android.content.SharedPreferences

class TvSettingsRepository(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences("vivicast-tv", Context.MODE_PRIVATE)

    fun edit(): SharedPreferences.Editor = preferences.edit()

    fun loadProviderSettings(playlistId: String): ProviderUiSettings {
        val enabled = preferences.getBoolean(providerEnabledKey(playlistId), true)
        val liveTvEnabled = preferences.getBoolean(providerLiveTvEnabledKey(playlistId), true)
        val vodEnabled = preferences.getBoolean(providerVodEnabledKey(playlistId), false)
        val logoPriority = preferences
            .getString(providerLogoPriorityKey(playlistId), LogoPriority.Playlist.name)
            ?.let { runCatching { LogoPriority.valueOf(it) }.getOrNull() }
            ?: LogoPriority.Playlist
        val hiddenCategoryIds = preferences
            .getString(providerHiddenCategoriesKey(playlistId), "")
            .orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
            .toSet()
        val categoryOrderIds = preferences
            .getString(providerCategoryOrderKey(playlistId), "")
            .orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
            .distinct()
        val userAgent = preferences.getString(providerUserAgentKey(playlistId), "").orEmpty()
        val xtreamOutputFormat = preferences
            .getString(providerXtreamOutputFormatKey(playlistId), XtreamOutputFormat.Ts.label)
            ?.let { stored ->
                XtreamOutputFormat.entries.firstOrNull { it.label == stored || it.name == stored }
            }
            ?: XtreamOutputFormat.Ts
        val xtreamApiScope = preferences
            .getString(providerXtreamApiScopeKey(playlistId), XtreamApiScope.LiveAndVodMetadata.name)
            ?.let { runCatching { XtreamApiScope.valueOf(it) }.getOrNull() }
            ?: XtreamApiScope.LiveAndVodMetadata
        val refreshIntervalHours = preferences
            .getString(providerRefreshIntervalKey(playlistId), RefreshIntervalHours.Hours24.name)
            ?.let { runCatching { RefreshIntervalHours.valueOf(it) }.getOrNull() }
            ?: RefreshIntervalHours.Hours24
        val refreshOnAppStart = preferences.getBoolean(providerRefreshOnAppStartKey(playlistId), true)
        val refreshOnPlaylistChange = preferences.getBoolean(providerRefreshOnPlaylistChangeKey(playlistId), true)
        return ProviderUiSettings(
            enabled = enabled,
            liveTvEnabled = liveTvEnabled,
            vodEnabled = vodEnabled,
            logoPriority = logoPriority,
            hiddenCategoryIds = hiddenCategoryIds,
            categoryOrderIds = categoryOrderIds,
            userAgent = userAgent,
            xtreamOutputFormat = xtreamOutputFormat,
            xtreamApiScope = xtreamApiScope,
            refreshIntervalHours = refreshIntervalHours,
            refreshOnAppStart = refreshOnAppStart,
            refreshOnPlaylistChange = refreshOnPlaylistChange
        )
    }

    fun loadProviderSyncState(playlistId: String): ProviderSyncState {
        return ProviderSyncState(
            lastSyncedAtEpochMillis = preferences.getLong(providerLastSyncedAtKey(playlistId), 0L)
                .takeIf { it > 0L },
            lastErrorMessage = preferences.getString(providerLastErrorKey(playlistId), null),
            lastRefreshAttemptAtEpochMillis = preferences.getLong(providerLastRefreshAttemptAtKey(playlistId), 0L)
                .takeIf { it > 0L },
            lastErrorAtEpochMillis = preferences.getLong(providerLastErrorAtKey(playlistId), 0L)
                .takeIf { it > 0L },
            lastRefreshDurationMillis = preferences.getLong(providerLastRefreshDurationKey(playlistId), 0L)
                .takeIf { it > 0L },
            lastRefreshSourceLabel = preferences.getString(providerLastRefreshSourceKey(playlistId), null),
            lastImportedChannelCount = preferences.getInt(providerLastChannelCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastImportedCategoryCount = preferences.getInt(providerLastCategoryCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastIgnoredLineCount = preferences.getInt(providerLastIgnoredLineCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastCatchupChannelCount = preferences.getInt(providerLastCatchupChannelCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastArchiveWindowDays = preferences.getInt(providerLastArchiveWindowDaysKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastXtreamVodCategoryCount = preferences.getInt(providerLastXtreamVodCategoryCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastXtreamSeriesCategoryCount = preferences.getInt(providerLastXtreamSeriesCategoryCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastXtreamMovieCount = preferences.getInt(providerLastXtreamMovieCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastXtreamSeriesCount = preferences.getInt(providerLastXtreamSeriesCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastXtreamSeasonCount = preferences.getInt(providerLastXtreamSeasonCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastXtreamEpisodeCount = preferences.getInt(providerLastXtreamEpisodeCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastXtreamSeriesDetailFailureCount = preferences.getInt(providerLastXtreamSeriesDetailFailureCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            refreshSuccessCount = preferences.getInt(providerRefreshSuccessCountKey(playlistId), 0),
            refreshFailureCount = preferences.getInt(providerRefreshFailureCountKey(playlistId), 0),
            consecutiveRefreshFailureCount = preferences.getInt(providerConsecutiveRefreshFailureCountKey(playlistId), 0),
            lastEpgAttemptAtEpochMillis = preferences.getLong(providerLastEpgAttemptAtKey(playlistId), 0L)
                .takeIf { it > 0L },
            lastEpgImportedAtEpochMillis = preferences.getLong(providerLastEpgImportedAtKey(playlistId), 0L)
                .takeIf { it > 0L },
            lastEpgImportDurationMillis = preferences.getLong(providerLastEpgImportDurationKey(playlistId), 0L)
                .takeIf { it > 0L },
            lastEpgXmltvChannelCount = preferences.getInt(providerLastEpgXmltvChannelCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastEpgImportedProgramCount = preferences.getInt(providerLastEpgImportedProgramCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastEpgUnmatchedProgramCount = preferences.getInt(providerLastEpgUnmatchedProgramCountKey(playlistId), -1)
                .takeIf { it >= 0 },
            lastEpgErrorMessage = preferences.getString(providerLastEpgErrorKey(playlistId), null),
            lastEpgErrorAtEpochMillis = preferences.getLong(providerLastEpgErrorAtKey(playlistId), 0L)
                .takeIf { it > 0L },
            epgSuccessCount = preferences.getInt(providerEpgSuccessCountKey(playlistId), 0),
            epgFailureCount = preferences.getInt(providerEpgFailureCountKey(playlistId), 0),
            consecutiveEpgFailureCount = preferences.getInt(providerConsecutiveEpgFailureCountKey(playlistId), 0),
            refreshing = false
        )
    }

    fun persistProviderSyncState(playlistId: String, state: ProviderSyncState) {
        preferences.edit().apply {
            putLong(providerLastSyncedAtKey(playlistId), state.lastSyncedAtEpochMillis ?: 0L)
            putString(providerLastErrorKey(playlistId), state.lastErrorMessage)
            putLong(providerLastRefreshAttemptAtKey(playlistId), state.lastRefreshAttemptAtEpochMillis ?: 0L)
            putLong(providerLastErrorAtKey(playlistId), state.lastErrorAtEpochMillis ?: 0L)
            putLong(providerLastRefreshDurationKey(playlistId), state.lastRefreshDurationMillis ?: 0L)
            putString(providerLastRefreshSourceKey(playlistId), state.lastRefreshSourceLabel)
            putInt(providerLastChannelCountKey(playlistId), state.lastImportedChannelCount ?: -1)
            putInt(providerLastCategoryCountKey(playlistId), state.lastImportedCategoryCount ?: -1)
            putInt(providerLastIgnoredLineCountKey(playlistId), state.lastIgnoredLineCount ?: -1)
            putInt(providerLastCatchupChannelCountKey(playlistId), state.lastCatchupChannelCount ?: -1)
            putInt(providerLastArchiveWindowDaysKey(playlistId), state.lastArchiveWindowDays ?: -1)
            putInt(providerLastXtreamVodCategoryCountKey(playlistId), state.lastXtreamVodCategoryCount ?: -1)
            putInt(providerLastXtreamSeriesCategoryCountKey(playlistId), state.lastXtreamSeriesCategoryCount ?: -1)
            putInt(providerLastXtreamMovieCountKey(playlistId), state.lastXtreamMovieCount ?: -1)
            putInt(providerLastXtreamSeriesCountKey(playlistId), state.lastXtreamSeriesCount ?: -1)
            putInt(providerLastXtreamSeasonCountKey(playlistId), state.lastXtreamSeasonCount ?: -1)
            putInt(providerLastXtreamEpisodeCountKey(playlistId), state.lastXtreamEpisodeCount ?: -1)
            putInt(providerLastXtreamSeriesDetailFailureCountKey(playlistId), state.lastXtreamSeriesDetailFailureCount ?: -1)
            putInt(providerRefreshSuccessCountKey(playlistId), state.refreshSuccessCount)
            putInt(providerRefreshFailureCountKey(playlistId), state.refreshFailureCount)
            putInt(providerConsecutiveRefreshFailureCountKey(playlistId), state.consecutiveRefreshFailureCount)
            putLong(providerLastEpgAttemptAtKey(playlistId), state.lastEpgAttemptAtEpochMillis ?: 0L)
            putLong(providerLastEpgImportedAtKey(playlistId), state.lastEpgImportedAtEpochMillis ?: 0L)
            putLong(providerLastEpgImportDurationKey(playlistId), state.lastEpgImportDurationMillis ?: 0L)
            putInt(providerLastEpgXmltvChannelCountKey(playlistId), state.lastEpgXmltvChannelCount ?: -1)
            putInt(providerLastEpgImportedProgramCountKey(playlistId), state.lastEpgImportedProgramCount ?: -1)
            putInt(providerLastEpgUnmatchedProgramCountKey(playlistId), state.lastEpgUnmatchedProgramCount ?: -1)
            putString(providerLastEpgErrorKey(playlistId), state.lastEpgErrorMessage)
            putLong(providerLastEpgErrorAtKey(playlistId), state.lastEpgErrorAtEpochMillis ?: 0L)
            putInt(providerEpgSuccessCountKey(playlistId), state.epgSuccessCount)
            putInt(providerEpgFailureCountKey(playlistId), state.epgFailureCount)
            putInt(providerConsecutiveEpgFailureCountKey(playlistId), state.consecutiveEpgFailureCount)
        }.apply()
    }

    fun loadProviderEpgAssignments(playlistId: String): List<String> {
        return preferences
            .getString(providerEpgAssignmentsKey(playlistId), "")
            .orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun loadAppSettings(): AppSettings {
        return AppSettings(
            startOnBoot = preferences.getBoolean(startOnBootKey, false),
            resumeLastChannelOnStart = preferences.getBoolean(resumeLastChannelOnStartKey, true),
            confirmExitOnBack = preferences.getBoolean(confirmExitOnBackKey, true),
            autoHidePrimaryNavigation = preferences.getBoolean(autoHidePrimaryNavigationKey, true),
            autoCollapseProviderFilters = preferences.getBoolean(autoCollapseProviderFiltersKey, true),
            showChannelNumbers = preferences.getBoolean(showChannelNumbersKey, true),
            showChannelMetadata = preferences.getBoolean(showChannelMetadataKey, true),
            showSourceLabels = preferences.getBoolean(showSourceLabelsKey, true),
            compactChannelRows = preferences.getBoolean(compactChannelRowsKey, false),
            compactProviderRows = preferences.getBoolean(compactProviderRowsKey, false),
            keepScreenAwakeDuringPlayback = preferences.getBoolean(keepScreenAwakeDuringPlaybackKey, true),
            showPlaybackStatusBadge = preferences.getBoolean(showPlaybackStatusBadgeKey, true),
            showPlaybackClock = preferences.getBoolean(showPlaybackClockKey, true),
            showPlaybackRecents = preferences.getBoolean(showPlaybackRecentsKey, true),
            showPlaybackGuideAction = preferences.getBoolean(showPlaybackGuideActionKey, true),
            showPlaybackProgressBar = preferences.getBoolean(showPlaybackProgressBarKey, true),
            showPlaybackTrackActions = preferences.getBoolean(showPlaybackTrackActionsKey, true),
            showPlaybackFavoriteAction = preferences.getBoolean(showPlaybackFavoriteActionKey, true),
            showPlaybackProgrammeDescription = preferences.getBoolean(showPlaybackProgrammeDescriptionKey, true),
            showPlaybackNowNextPanel = preferences.getBoolean(showPlaybackNowNextPanelKey, true),
            showFavoriteFeedbackBanner = preferences.getBoolean(showFavoriteFeedbackBannerKey, true),
            recoverablePlaybackRetryDelay = preferences.getString(
                recoverablePlaybackRetryDelayKey,
                PlaybackRetryDelay.Millis900.name
            )?.let { runCatching { PlaybackRetryDelay.valueOf(it) }.getOrNull() } ?: PlaybackRetryDelay.Millis900,
            retryRecoverablePlaybackErrorsOnce = preferences.getBoolean(retryRecoverablePlaybackErrorsOnceKey, true),
            leaveLiveTvPlaybackBehavior = preferences.getString(
                leaveLiveTvPlaybackBehaviorKey,
                null
            )?.let { runCatching { LeaveLiveTvPlaybackBehavior.valueOf(it) }.getOrNull() }
                ?: if (preferences.getBoolean(stopPlaybackWhenLeavingLiveTvKey, false)) {
                    LeaveLiveTvPlaybackBehavior.StopPlayback
                } else {
                    LeaveLiveTvPlaybackBehavior.KeepPlaying
                },
            openProviderFiltersWhenEnteringLiveTv = preferences.getBoolean(openProviderFiltersWhenEnteringLiveTvKey, true),
            backClearsLiveTvFilters = preferences.getBoolean(backClearsLiveTvFiltersKey, true),
            backHidesLiveTvFiltersFirst = preferences.getBoolean(backHidesLiveTvFiltersFirstKey, false),
            guideResetsToNowOnOpen = preferences.getBoolean(guideResetsToNowOnOpenKey, true),
            guideStartsWithCurrentChannel = preferences.getBoolean(guideStartsWithCurrentChannelKey, true),
            guideUsesPlayingChannelProviderOnOpen = preferences.getBoolean(guideUsesPlayingChannelProviderOnOpenKey, false),
            guideClearsCategoryOnOpen = preferences.getBoolean(guideClearsCategoryOnOpenKey, false),
            backClearsProviderBeforeCategory = preferences.getBoolean(backClearsProviderBeforeCategoryKey, false),
            backClosesNavigationFirst = preferences.getBoolean(backClosesNavigationFirstKey, true),
            backClearsGuideWindowFirst = preferences.getBoolean(backClearsGuideWindowFirstKey, true),
            backClearsGuideProgrammeFirst = preferences.getBoolean(backClearsGuideProgrammeFirstKey, true),
            backLeavesSettingsToLiveTv = preferences.getBoolean(backLeavesSettingsToLiveTvKey, true),
            backClearsGuideFilters = preferences.getBoolean(backClearsGuideFiltersKey, true),
            preferProviderFiltersBeforeNavigation = preferences.getBoolean(
                preferProviderFiltersBeforeNavigationKey,
                true
            ),
            epgTimeOffset = preferences.getString(epgTimeOffsetKey, EpgTimeOffset.Hours0.name)
                ?.let { runCatching { EpgTimeOffset.valueOf(it) }.getOrNull() }
                ?: EpgTimeOffset.Hours0,
            epgRefreshOnAppStart = preferences.getBoolean(epgRefreshOnAppStartKey, true),
            epgUpdateInterval = preferences.getString(epgUpdateIntervalKey, EpgUpdateInterval.Hours12.name)
                ?.let { runCatching { EpgUpdateInterval.valueOf(it) }.getOrNull() }
                ?: EpgUpdateInterval.Hours12,
            epgRefreshDuringSession = preferences.getBoolean(epgRefreshDuringSessionKey, true),
            epgRetentionDays = preferences.getString(epgRetentionDaysKey, EpgRetentionDays.Days7.name)
                ?.let { runCatching { EpgRetentionDays.valueOf(it) }.getOrNull() }
                ?: EpgRetentionDays.Days7
        )
    }
}
