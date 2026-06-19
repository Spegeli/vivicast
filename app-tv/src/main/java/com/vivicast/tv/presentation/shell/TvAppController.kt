package com.vivicast.tv

import android.content.Context
import android.net.Uri
import com.vivicast.core.domain.EpgImportResult
import com.vivicast.core.domain.M3uImportResult
import com.vivicast.core.data.ViviCastDataGraph
import com.vivicast.core.model.Channel
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Episode
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.Movie
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.StreamTrack
import com.vivicast.core.model.XtreamCredentials
import com.vivicast.core.model.XtreamOutputFormat as CoreXtreamOutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class ViviCastTvController(context: Context) {
    val context: Context = context.applicationContext
    internal val dataGraph = ViviCastDataGraph(context)
    internal val importUseCase = dataGraph.playlistImportUseCase
    internal val vodLibraryUseCase = dataGraph.vodLibraryUseCase
    internal val xtreamVodImportUseCase = dataGraph.xtreamVodImportUseCase
    internal val settingsRepository = TvSettingsRepository(context)
    private val playbackRepository = TvPlaybackRepository(context)
    val playbackState = playbackRepository.playbackState
    val media3Player = playbackRepository.media3Player
    val channels = importUseCase.observeChannels()
    val categories = importUseCase.observeCategories()
    val playlists = importUseCase.observePlaylists()
    val epgSources = importUseCase.observeEpgSources()
    val epgProgramCount = importUseCase.observeEpgProgramCount()
    val favorites = importUseCase.observeFavorites()
    val recents = importUseCase.observeRecents()
    val movies = vodLibraryUseCase.observeAllMovies()
    val series = vodLibraryUseCase.observeAllSeries()
    internal val mutableProviderSettings = MutableStateFlow<Map<String, ProviderUiSettings>>(emptyMap())
    val providerSettings: StateFlow<Map<String, ProviderUiSettings>> = mutableProviderSettings
    internal val mutableProviderSyncStates = MutableStateFlow<Map<String, ProviderSyncState>>(emptyMap())
    val providerSyncStates: StateFlow<Map<String, ProviderSyncState>> = mutableProviderSyncStates
    internal val mutableProviderEpgAssignments = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val providerEpgAssignments: StateFlow<Map<String, List<String>>> = mutableProviderEpgAssignments
    internal val mutableAppSettings = MutableStateFlow(loadAppSettings())
    val appSettings: StateFlow<AppSettings> = mutableAppSettings

    fun hydrateProviderState(playlistIds: List<String>) {
        val ids = playlistIds.distinct()
        val currentSettings = mutableProviderSettings.value
        val currentSyncStates = mutableProviderSyncStates.value
        val currentAssignments = mutableProviderEpgAssignments.value
        mutableProviderSettings.value = ids.associateWith { playlistId ->
            currentSettings[playlistId] ?: loadProviderSettings(playlistId)
        }
        mutableProviderSyncStates.value = ids.associateWith { playlistId ->
            currentSyncStates[playlistId] ?: loadProviderSyncState(playlistId)
        }
        mutableProviderEpgAssignments.value = ids.associateWith { playlistId ->
            currentAssignments[playlistId] ?: loadProviderEpgAssignments(playlistId)
        }
    }

    fun play(channel: Channel) {
        playbackRepository.play(channel)
    }

    fun observeSeasons(seriesId: String) = vodLibraryUseCase.observeSeasons(seriesId)

    fun observeEpisodes(seasonId: String) = vodLibraryUseCase.observeEpisodes(seasonId)

    fun observeMovieProgress(movieId: String) = vodLibraryUseCase.observeMovieProgress(movieId)

    fun observeEpisodeProgress(episodeId: String) = vodLibraryUseCase.observeEpisodeProgress(episodeId)

    fun playMovie(movie: Movie, startPositionMs: Long = 0L) {
        playbackRepository.playStream(
            contentId = movie.id,
            title = movie.title,
            streamUrl = movie.streamUrl,
            contentType = PlaybackContentType.MOVIE,
            startPositionMs = startPositionMs
        )
    }

    fun playEpisode(episode: Episode, title: String, startPositionMs: Long = 0L) {
        playbackRepository.playStream(
            contentId = episode.id,
            title = title,
            streamUrl = episode.streamUrl,
            contentType = PlaybackContentType.EPISODE,
            startPositionMs = startPositionMs
        )
    }

    suspend fun saveMovieProgress(
        movieId: String,
        positionMs: Long,
        durationMs: Long?,
        completed: Boolean
    ) {
        withContext(Dispatchers.IO) {
            vodLibraryUseCase.saveMovieProgress(
                MoviePlaybackProgress(
                    movieId = movieId,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs,
                    completed = completed,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveEpisodeProgress(
        episodeId: String,
        positionMs: Long,
        durationMs: Long?,
        completed: Boolean
    ) {
        withContext(Dispatchers.IO) {
            vodLibraryUseCase.saveEpisodeProgress(
                EpisodePlaybackProgress(
                    episodeId = episodeId,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs,
                    completed = completed,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun markRecent(channelId: String) {
        withContext(Dispatchers.IO) {
            importUseCase.markRecent(channelId)
        }
    }

    suspend fun setFavorite(channelId: String, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            if (favorite) {
                importUseCase.addFavorite(channelId)
            } else {
                importUseCase.removeFavorite(channelId)
            }
        }
    }

    suspend fun setProviderEnabled(playlistId: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit().putBoolean(providerEnabledKey(playlistId), enabled).apply()
            updateProviderSettings(playlistId) { it.copy(enabled = enabled) }
        }
    }

    suspend fun setProviderLiveTvEnabled(playlistId: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit().putBoolean(providerLiveTvEnabledKey(playlistId), enabled).apply()
            updateProviderSettings(playlistId) { it.copy(liveTvEnabled = enabled) }
        }
    }

    suspend fun setProviderVodEnabled(playlistId: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val current = mutableProviderSettings.value[playlistId] ?: loadProviderSettings(playlistId)
            val nextXtreamApiScope = if (enabled && current.xtreamApiScope == XtreamApiScope.LiveOnly) {
                XtreamApiScope.LiveAndVodMetadata
            } else {
                current.xtreamApiScope
            }
            settingsRepository.edit()
                .putBoolean(providerVodEnabledKey(playlistId), enabled)
                .putString(providerXtreamApiScopeKey(playlistId), nextXtreamApiScope.name)
                .apply()
            updateProviderSettings(playlistId) {
                it.copy(
                    vodEnabled = enabled,
                    xtreamApiScope = nextXtreamApiScope
                )
            }
        }
    }

    suspend fun setProviderLogoPriority(playlistId: String, logoPriority: LogoPriority) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit().putString(providerLogoPriorityKey(playlistId), logoPriority.name).apply()
            updateProviderSettings(playlistId) { it.copy(logoPriority = logoPriority) }
        }
    }

    suspend fun setHiddenCategories(playlistId: String, hiddenCategoryIds: Set<String>) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerHiddenCategoriesKey(playlistId), hiddenCategoryIds.joinToString("|"))
                .apply()
            updateProviderSettings(playlistId) { it.copy(hiddenCategoryIds = hiddenCategoryIds) }
        }
    }

    suspend fun setCategoryOrder(playlistId: String, orderedCategoryIds: List<String>) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerCategoryOrderKey(playlistId), orderedCategoryIds.distinct().joinToString("|"))
                .apply()
            updateProviderSettings(playlistId) { it.copy(categoryOrderIds = orderedCategoryIds.distinct()) }
        }
    }

    suspend fun setProviderRefreshInterval(playlistId: String, refreshIntervalHours: RefreshIntervalHours) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerRefreshIntervalKey(playlistId), refreshIntervalHours.name)
                .apply()
            updateProviderSettings(playlistId) { it.copy(refreshIntervalHours = refreshIntervalHours) }
        }
    }

    suspend fun setProviderRefreshOnAppStart(playlistId: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putBoolean(providerRefreshOnAppStartKey(playlistId), enabled)
                .apply()
            updateProviderSettings(playlistId) { it.copy(refreshOnAppStart = enabled) }
        }
    }

    suspend fun setProviderRefreshOnPlaylistChange(playlistId: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putBoolean(providerRefreshOnPlaylistChangeKey(playlistId), enabled)
                .apply()
            updateProviderSettings(playlistId) { it.copy(refreshOnPlaylistChange = enabled) }
        }
    }

    suspend fun setProviderUserAgent(playlistId: String, userAgent: String) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerUserAgentKey(playlistId), userAgent.trim())
                .apply()
            updateProviderSettings(playlistId) { it.copy(userAgent = userAgent.trim()) }
        }
    }

    suspend fun setProviderXtreamOutputFormat(playlistId: String, outputFormat: XtreamOutputFormat) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerXtreamOutputFormatKey(playlistId), outputFormat.name)
                .apply()
            updateProviderSettings(playlistId) { it.copy(xtreamOutputFormat = outputFormat) }
        }
    }

    suspend fun setProviderXtreamApiScope(playlistId: String, apiScope: XtreamApiScope) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerXtreamApiScopeKey(playlistId), apiScope.name)
                .apply()
            updateProviderSettings(playlistId) { it.copy(xtreamApiScope = apiScope) }
        }
    }

    suspend fun resetProviderAdvancedOptions(playlistId: String) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerLogoPriorityKey(playlistId), LogoPriority.Playlist.name)
                .putString(providerUserAgentKey(playlistId), "")
                .putString(providerXtreamOutputFormatKey(playlistId), XtreamOutputFormat.Ts.name)
                .putString(providerXtreamApiScopeKey(playlistId), XtreamApiScope.LiveAndVodMetadata.name)
                .putString(providerRefreshIntervalKey(playlistId), RefreshIntervalHours.Hours24.name)
                .putBoolean(providerRefreshOnAppStartKey(playlistId), true)
                .putBoolean(providerRefreshOnPlaylistChangeKey(playlistId), true)
                .apply()
            updateProviderSettings(playlistId) {
                it.copy(
                    logoPriority = LogoPriority.Playlist,
                    userAgent = "",
                    xtreamOutputFormat = XtreamOutputFormat.Ts,
                    xtreamApiScope = XtreamApiScope.LiveAndVodMetadata,
                    refreshIntervalHours = RefreshIntervalHours.Hours24,
                    refreshOnAppStart = true,
                    refreshOnPlaylistChange = true
                )
            }
        }
    }

    suspend fun setProviderEpgAssignments(playlistId: String, sourceIds: List<String>) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit()
                .putString(providerEpgAssignmentsKey(playlistId), sourceIds.distinct().joinToString("|"))
                .apply()
            mutableProviderEpgAssignments.value =
                mutableProviderEpgAssignments.value + (playlistId to sourceIds.distinct())
        }
    }

    suspend fun clearProviderSyncState(playlistId: String) {
        withContext(Dispatchers.IO) {
            val clearedState = ProviderSyncState()
            persistProviderSyncState(playlistId, clearedState)
            mutableProviderSyncStates.value = mutableProviderSyncStates.value + (playlistId to clearedState)
        }
    }

    suspend fun setStartOnBoot(enabled: Boolean) = updateAppSettings(
        { putBoolean(startOnBootKey, enabled) },
        { it.copy(startOnBoot = enabled) }
    )

    suspend fun setResumeLastChannelOnStart(enabled: Boolean) = updateAppSettings(
        { putBoolean(resumeLastChannelOnStartKey, enabled) },
        { it.copy(resumeLastChannelOnStart = enabled) }
    )

    suspend fun setConfirmExitOnBack(enabled: Boolean) = updateAppSettings(
        { putBoolean(confirmExitOnBackKey, enabled) },
        { it.copy(confirmExitOnBack = enabled) }
    )

    suspend fun setAutoHidePrimaryNavigation(enabled: Boolean) = updateAppSettings(
        { putBoolean(autoHidePrimaryNavigationKey, enabled) },
        { it.copy(autoHidePrimaryNavigation = enabled) }
    )

    suspend fun setAutoCollapseProviderFilters(enabled: Boolean) = updateAppSettings(
        { putBoolean(autoCollapseProviderFiltersKey, enabled) },
        { it.copy(autoCollapseProviderFilters = enabled) }
    )

    suspend fun setShowChannelNumbers(enabled: Boolean) = updateAppSettings(
        { putBoolean(showChannelNumbersKey, enabled) },
        { it.copy(showChannelNumbers = enabled) }
    )

    suspend fun setShowChannelMetadata(enabled: Boolean) = updateAppSettings(
        { putBoolean(showChannelMetadataKey, enabled) },
        { it.copy(showChannelMetadata = enabled) }
    )

    suspend fun setShowSourceLabels(enabled: Boolean) = updateAppSettings(
        { putBoolean(showSourceLabelsKey, enabled) },
        { it.copy(showSourceLabels = enabled) }
    )

    suspend fun setCompactChannelRows(enabled: Boolean) = updateAppSettings(
        { putBoolean(compactChannelRowsKey, enabled) },
        { it.copy(compactChannelRows = enabled) }
    )

    suspend fun setCompactProviderRows(enabled: Boolean) = updateAppSettings(
        { putBoolean(compactProviderRowsKey, enabled) },
        { it.copy(compactProviderRows = enabled) }
    )

    suspend fun setKeepScreenAwakeDuringPlayback(enabled: Boolean) = updateAppSettings(
        { putBoolean(keepScreenAwakeDuringPlaybackKey, enabled) },
        { it.copy(keepScreenAwakeDuringPlayback = enabled) }
    )

    suspend fun setShowPlaybackStatusBadge(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackStatusBadgeKey, enabled) },
        { it.copy(showPlaybackStatusBadge = enabled) }
    )

    suspend fun setShowPlaybackClock(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackClockKey, enabled) },
        { it.copy(showPlaybackClock = enabled) }
    )

    suspend fun setShowPlaybackRecents(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackRecentsKey, enabled) },
        { it.copy(showPlaybackRecents = enabled) }
    )

    suspend fun setShowPlaybackGuideAction(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackGuideActionKey, enabled) },
        { it.copy(showPlaybackGuideAction = enabled) }
    )

    suspend fun setShowPlaybackProgressBar(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackProgressBarKey, enabled) },
        { it.copy(showPlaybackProgressBar = enabled) }
    )

    suspend fun setShowPlaybackTrackActions(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackTrackActionsKey, enabled) },
        { it.copy(showPlaybackTrackActions = enabled) }
    )

    suspend fun setShowPlaybackFavoriteAction(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackFavoriteActionKey, enabled) },
        { it.copy(showPlaybackFavoriteAction = enabled) }
    )

    suspend fun setShowPlaybackProgrammeDescription(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackProgrammeDescriptionKey, enabled) },
        { it.copy(showPlaybackProgrammeDescription = enabled) }
    )

    suspend fun setShowPlaybackNowNextPanel(enabled: Boolean) = updateAppSettings(
        { putBoolean(showPlaybackNowNextPanelKey, enabled) },
        { it.copy(showPlaybackNowNextPanel = enabled) }
    )

    suspend fun setShowFavoriteFeedbackBanner(enabled: Boolean) = updateAppSettings(
        { putBoolean(showFavoriteFeedbackBannerKey, enabled) },
        { it.copy(showFavoriteFeedbackBanner = enabled) }
    )

    suspend fun setRecoverablePlaybackRetryDelay(delay: PlaybackRetryDelay) = updateAppSettings(
        { putString(recoverablePlaybackRetryDelayKey, delay.name) },
        { it.copy(recoverablePlaybackRetryDelay = delay) }
    )

    suspend fun setRetryRecoverablePlaybackErrorsOnce(enabled: Boolean) = updateAppSettings(
        { putBoolean(retryRecoverablePlaybackErrorsOnceKey, enabled) },
        { it.copy(retryRecoverablePlaybackErrorsOnce = enabled) }
    )

    suspend fun setLeaveLiveTvPlaybackBehavior(behavior: LeaveLiveTvPlaybackBehavior) = updateAppSettings(
        { putString(leaveLiveTvPlaybackBehaviorKey, behavior.name) },
        { it.copy(leaveLiveTvPlaybackBehavior = behavior) }
    )

    suspend fun setOpenProviderFiltersWhenEnteringLiveTv(enabled: Boolean) = updateAppSettings(
        { putBoolean(openProviderFiltersWhenEnteringLiveTvKey, enabled) },
        { it.copy(openProviderFiltersWhenEnteringLiveTv = enabled) }
    )

    suspend fun setBackClearsLiveTvFilters(enabled: Boolean) = updateAppSettings(
        { putBoolean(backClearsLiveTvFiltersKey, enabled) },
        { it.copy(backClearsLiveTvFilters = enabled) }
    )

    suspend fun setBackHidesLiveTvFiltersFirst(enabled: Boolean) = updateAppSettings(
        { putBoolean(backHidesLiveTvFiltersFirstKey, enabled) },
        { it.copy(backHidesLiveTvFiltersFirst = enabled) }
    )

    suspend fun setGuideResetsToNowOnOpen(enabled: Boolean) = updateAppSettings(
        { putBoolean(guideResetsToNowOnOpenKey, enabled) },
        { it.copy(guideResetsToNowOnOpen = enabled) }
    )

    suspend fun setGuideStartsWithCurrentChannel(enabled: Boolean) = updateAppSettings(
        { putBoolean(guideStartsWithCurrentChannelKey, enabled) },
        { it.copy(guideStartsWithCurrentChannel = enabled) }
    )

    suspend fun setGuideUsesPlayingChannelProviderOnOpen(enabled: Boolean) = updateAppSettings(
        { putBoolean(guideUsesPlayingChannelProviderOnOpenKey, enabled) },
        { it.copy(guideUsesPlayingChannelProviderOnOpen = enabled) }
    )

    suspend fun setGuideClearsCategoryOnOpen(enabled: Boolean) = updateAppSettings(
        { putBoolean(guideClearsCategoryOnOpenKey, enabled) },
        { it.copy(guideClearsCategoryOnOpen = enabled) }
    )

    suspend fun setBackClearsProviderBeforeCategory(enabled: Boolean) = updateAppSettings(
        { putBoolean(backClearsProviderBeforeCategoryKey, enabled) },
        { it.copy(backClearsProviderBeforeCategory = enabled) }
    )

    suspend fun setBackClosesNavigationFirst(enabled: Boolean) = updateAppSettings(
        { putBoolean(backClosesNavigationFirstKey, enabled) },
        { it.copy(backClosesNavigationFirst = enabled) }
    )

    suspend fun setBackClearsGuideWindowFirst(enabled: Boolean) = updateAppSettings(
        { putBoolean(backClearsGuideWindowFirstKey, enabled) },
        { it.copy(backClearsGuideWindowFirst = enabled) }
    )

    suspend fun setBackClearsGuideProgrammeFirst(enabled: Boolean) = updateAppSettings(
        { putBoolean(backClearsGuideProgrammeFirstKey, enabled) },
        { it.copy(backClearsGuideProgrammeFirst = enabled) }
    )

    suspend fun setBackLeavesSettingsToLiveTv(enabled: Boolean) = updateAppSettings(
        { putBoolean(backLeavesSettingsToLiveTvKey, enabled) },
        { it.copy(backLeavesSettingsToLiveTv = enabled) }
    )

    suspend fun setBackClearsGuideFilters(enabled: Boolean) = updateAppSettings(
        { putBoolean(backClearsGuideFiltersKey, enabled) },
        { it.copy(backClearsGuideFilters = enabled) }
    )

    suspend fun setPreferProviderFiltersBeforeNavigation(enabled: Boolean) = updateAppSettings(
        { putBoolean(preferProviderFiltersBeforeNavigationKey, enabled) },
        { it.copy(preferProviderFiltersBeforeNavigation = enabled) }
    )

    suspend fun setEpgTimeOffset(value: EpgTimeOffset) = updateAppSettings(
        { putString(epgTimeOffsetKey, value.name) },
        { it.copy(epgTimeOffset = value) }
    )

    suspend fun setEpgRefreshOnAppStart(enabled: Boolean) = updateAppSettings(
        { putBoolean(epgRefreshOnAppStartKey, enabled) },
        { it.copy(epgRefreshOnAppStart = enabled) }
    )

    suspend fun setEpgUpdateInterval(value: EpgUpdateInterval) = updateAppSettings(
        { putString(epgUpdateIntervalKey, value.name) },
        { it.copy(epgUpdateInterval = value) }
    )

    suspend fun setEpgRefreshDuringSession(enabled: Boolean) = updateAppSettings(
        { putBoolean(epgRefreshDuringSessionKey, enabled) },
        { it.copy(epgRefreshDuringSession = enabled) }
    )

    suspend fun setEpgRetentionDays(value: EpgRetentionDays) = updateAppSettings(
        { putString(epgRetentionDaysKey, value.name) },
        { it.copy(epgRetentionDays = value) }
    )

    suspend fun renameProvider(playlistId: String, name: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching { importUseCase.renamePlaylist(playlistId, name) }
        }
    }

    suspend fun updateProviderConnection(
        playlistId: String,
        sourceUri: String,
        sourceUsername: String?,
        sourcePassword: String?
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                importUseCase.updatePlaylistConnection(
                    playlistId = playlistId,
                    sourceUri = sourceUri,
                    sourceUsername = sourceUsername,
                    sourcePassword = sourcePassword
                )
            }
        }
    }

    suspend fun deleteProvider(playlistId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching { importUseCase.deletePlaylist(playlistId) }
        }
    }

    suspend fun saveEpgSource(playlist: Playlist, sourceUri: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                importUseCase.upsertEpgSource(
                    playlistId = playlist.id,
                    name = "${playlist.displayName()} EPG",
                    sourceUri = sourceUri
                )
            }
        }
    }

    suspend fun saveGlobalEpgSource(name: String, sourceUri: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                importUseCase.upsertGlobalEpgSource(
                    name = name,
                    sourceUri = sourceUri
                )
            }
        }
    }

    suspend fun removeEpgSource(playlistId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching { importUseCase.removeEpgSource(playlistId) }
        }
    }

    suspend fun removeEpgSourceById(sourceId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                importUseCase.removeEpgSourceById(sourceId)
                val updatedAssignments = mutableProviderEpgAssignments.value.mapValues { (_, ids) ->
                    ids.filterNot { it == sourceId }
                }
                settingsRepository.edit().apply {
                    updatedAssignments.forEach { (playlistId, ids) ->
                        putString(providerEpgAssignmentsKey(playlistId), ids.joinToString("|"))
                    }
                }.apply()
                mutableProviderEpgAssignments.value = updatedAssignments
            }
        }
    }

    fun observeNowNext(channelId: String, nowUtcEpochMillis: Long) =
        importUseCase.observeNowNext(channelId, nowUtcEpochMillis)

    fun observeGuidePrograms(
        channelIds: List<String>,
        fromUtcEpochMillis: Long,
        toUtcEpochMillis: Long
    ) = importUseCase.observeGuidePrograms(channelIds, fromUtcEpochMillis, toUtcEpochMillis)

    fun release() {
        playbackRepository.release()
        dataGraph.close()
    }


    fun pausePlayback() {
        playbackRepository.pause()
    }

    fun stopPlayback() {
        playbackRepository.stop()
    }

    fun selectTrack(track: StreamTrack) {
        playbackRepository.selectTrack(track)
    }

    fun currentPositionMs(): Long = playbackRepository.currentPositionMs()

    fun currentDurationMs(): Long? = playbackRepository.currentDurationMs()


    private suspend fun updateAppSettings(
        write: android.content.SharedPreferences.Editor.() -> Unit,
        transform: (AppSettings) -> AppSettings
    ) {
        withContext(Dispatchers.IO) {
            settingsRepository.edit().apply {
                write()
                apply()
            }
            mutableAppSettings.value = transform(mutableAppSettings.value)
        }
    }

    internal fun updateProviderSettings(
        playlistId: String,
        transform: (ProviderUiSettings) -> ProviderUiSettings
    ) {
        val current = mutableProviderSettings.value[playlistId] ?: loadProviderSettings(playlistId)
        mutableProviderSettings.value = mutableProviderSettings.value + (playlistId to transform(current))
    }

    internal fun providerUserAgent(playlistId: String): String {
        return (mutableProviderSettings.value[playlistId] ?: loadProviderSettings(playlistId)).userAgent
    }

    internal fun providerXtreamOutputFormat(playlistId: String): XtreamOutputFormat {
        return (mutableProviderSettings.value[playlistId] ?: loadProviderSettings(playlistId)).xtreamOutputFormat
    }

    internal fun providerXtreamApiScope(playlistId: String): XtreamApiScope {
        return (mutableProviderSettings.value[playlistId] ?: loadProviderSettings(playlistId)).xtreamApiScope
    }

    internal fun providerRequestHeaders(playlistId: String): Map<String, String> {
        val userAgent = providerUserAgent(playlistId).trim()
        return if (userAgent.isBlank()) emptyMap() else mapOf("User-Agent" to userAgent)
    }

    internal fun readTextFromLocalPlaylistSource(sourceUri: String): Result<String> {
        return runCatching {
            val normalized = sourceUri.trim()
            val content = when {
                normalized.startsWith("content://", ignoreCase = true) -> {
                    context.contentResolver.openInputStream(Uri.parse(normalized))
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("Could not open content URI.")
                }
                normalized.startsWith("file://", ignoreCase = true) -> {
                    val path = Uri.parse(normalized).path
                        ?: throw IllegalStateException("Invalid file URI.")
                    File(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                else -> {
                    File(normalized).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }
            if (content.isBlank()) {
                throw IllegalStateException("Playlist file is empty.")
            }
            content
        }
    }

    internal fun updateProviderSyncState(
        playlistId: String,
        transform: (ProviderSyncState) -> ProviderSyncState
    ) {
        val current = mutableProviderSyncStates.value[playlistId] ?: loadProviderSyncState(playlistId)
        val updated = transform(current)
        persistProviderSyncState(playlistId, updated)
        mutableProviderSyncStates.value = mutableProviderSyncStates.value + (playlistId to updated)
    }

    internal fun applyRefreshResult(
        playlistId: String,
        result: Result<ProviderRefreshResult>,
        startedAtMillis: Long,
        sourceLabel: String
    ) {
        val now = System.currentTimeMillis()
        val durationMillis = (now - startedAtMillis).coerceAtLeast(0L)
        updateProviderSyncState(playlistId) { current ->
            result.fold(
                onSuccess = {
                    current.copy(
                        refreshing = false,
                        lastRefreshAttemptAtEpochMillis = now,
                        lastSyncedAtEpochMillis = now,
                        lastErrorMessage = null,
                        lastErrorAtEpochMillis = null,
                        lastRefreshDurationMillis = durationMillis,
                        lastRefreshSourceLabel = sourceLabel,
                        lastImportedChannelCount = it.liveImport?.channelCount,
                        lastImportedCategoryCount = it.liveImport?.categoryCount,
                        lastIgnoredLineCount = it.liveImport?.ignoredLineCount,
                        lastCatchupChannelCount = it.liveImport?.catchupChannelCount,
                        lastArchiveWindowDays = it.liveImport?.archiveWindowDaysMax,
                        lastXtreamVodCategoryCount = it.vodImport?.movieCategoryCount,
                        lastXtreamSeriesCategoryCount = it.vodImport?.seriesCategoryCount,
                        lastXtreamMovieCount = it.vodImport?.movieCount,
                        lastXtreamSeriesCount = it.vodImport?.seriesCount,
                        lastXtreamSeasonCount = it.vodImport?.seasonCount,
                        lastXtreamEpisodeCount = it.vodImport?.episodeCount,
                        lastXtreamSeriesDetailFailureCount = it.vodImport?.failedSeriesDetailCount,
                        refreshSuccessCount = current.refreshSuccessCount + 1,
                        consecutiveRefreshFailureCount = 0
                    )
                },
                onFailure = { error ->
                    current.copy(
                        refreshing = false,
                        lastRefreshAttemptAtEpochMillis = now,
                        lastErrorMessage = error.message ?: error::class.simpleName,
                        lastErrorAtEpochMillis = now,
                        lastRefreshDurationMillis = durationMillis,
                        lastRefreshSourceLabel = sourceLabel,
                        refreshFailureCount = current.refreshFailureCount + 1,
                        consecutiveRefreshFailureCount = current.consecutiveRefreshFailureCount + 1
                    )
                }
            )
        }
    }

    internal fun loadProviderSettings(playlistId: String): ProviderUiSettings {
        return settingsRepository.loadProviderSettings(playlistId)
    }

    private fun loadProviderSyncState(playlistId: String): ProviderSyncState {
        return settingsRepository.loadProviderSyncState(playlistId)
    }

    private fun persistProviderSyncState(playlistId: String, state: ProviderSyncState) {
        settingsRepository.persistProviderSyncState(playlistId, state)
    }

    fun getProviderEpgAssignments(playlistId: String): List<String> {
        return mutableProviderEpgAssignments.value[playlistId] ?: loadProviderEpgAssignments(playlistId).also { loaded ->
            mutableProviderEpgAssignments.value = mutableProviderEpgAssignments.value + (playlistId to loaded)
        }
    }

    private fun loadProviderEpgAssignments(playlistId: String): List<String> {
        return settingsRepository.loadProviderEpgAssignments(playlistId)
    }

    private fun loadAppSettings(): AppSettings {
        return settingsRepository.loadAppSettings()
    }
}
