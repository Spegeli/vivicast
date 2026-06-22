package com.vivicast.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vivicast.tv.core.cache.MediaCacheStats
import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheType
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.TimeshiftStoragePreference
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.designsystem.VivicastScreenBackground
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.core.designsystem.VivicastTopNavigation
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.PlaybackTimeshiftConfig
import com.vivicast.tv.core.player.PlaybackTimeshiftStorage
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.feature.settings.CacheSettingsState
import com.vivicast.tv.feature.settings.GeneralSettingsState
import com.vivicast.tv.feature.settings.PlaybackSettingsState
import com.vivicast.tv.feature.livetv.LiveTvRoute
import com.vivicast.tv.feature.movies.MoviesRoute
import com.vivicast.tv.feature.player.PlayerRoute
import com.vivicast.tv.feature.search.SearchRoute
import com.vivicast.tv.feature.series.SeriesRoute
import com.vivicast.tv.feature.settings.SettingsRoute
import com.vivicast.tv.di.AppContainer
import com.vivicast.tv.data.playback.PlaybackStreamRequest
import com.vivicast.tv.data.playback.PlaybackStreamResult
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as VivicastApplication).appContainer
        setContent {
            VivicastTheme {
                VivicastApp(appContainer = appContainer)
            }
        }
    }
}

@Composable
private fun VivicastApp(appContainer: AppContainer) {
    var playerVisible by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf("live-tv") }
    var cacheStats by remember { mutableStateOf(MediaCacheStats(totalSizeBytes = 0L, fileCount = 0)) }
    var livePlaybackChannels by remember { mutableStateOf(emptyList<Channel>()) }
    val preferences by appContainer.userPreferencesStore.values.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    fun openChannel(channel: Channel) {
        scope.launch {
            appContainer.openChannelPlayback(channel, preferences.playback) {
                playerVisible = true
            }
        }
    }

    fun zapChannel(direction: Int) {
        if (direction == 0 || livePlaybackChannels.isEmpty()) return
        val currentRequest = appContainer.playerController.state.value.request
        if (currentRequest?.mediaType != PlaybackMediaType.Channel) return

        val currentIndex = livePlaybackChannels.indexOfFirst { it.id == currentRequest.mediaId }
        val nextIndex = if (currentIndex < 0) {
            0
        } else {
            (currentIndex + direction).floorMod(livePlaybackChannels.size)
        }
        openChannel(livePlaybackChannels[nextIndex])
    }

    fun openMovie(movie: Movie, resumeProgress: Boolean) {
        scope.launch {
            appContainer.openMoviePlayback(movie, resumeProgress) {
                playerVisible = true
            }
        }
    }

    fun openEpisode(episode: Episode) {
        scope.launch {
            appContainer.openEpisodePlayback(episode) {
                playerVisible = true
            }
        }
    }

    fun openCatchUp(channel: Channel, program: EpgProgram) {
        scope.launch {
            appContainer.openCatchUpPlayback(channel, program) {
                playerVisible = true
            }
        }
    }

    LaunchedEffect(preferences.general.backgroundRefreshEnabled) {
        appContainer.refreshWorkScheduler.setBackgroundRefreshEnabled(
            enabled = preferences.general.backgroundRefreshEnabled,
        )
    }

    LaunchedEffect(selectedRoute) {
        if (selectedRoute == "settings") {
            cacheStats = appContainer.mediaCacheStore.stats()
        }
    }

    val watchedThresholdPercent = preferences.history.watchedThresholdPercent.coerceIn(
        WATCHED_THRESHOLD_MIN_PERCENT,
        WATCHED_THRESHOLD_MAX_PERCENT,
    )

    LaunchedEffect(appContainer, watchedThresholdPercent) {
        appContainer.playerController.state.collectLatest { state ->
            appContainer.savePlaybackProgress(
                state = state,
                completedThresholdPercent = watchedThresholdPercent,
            )
        }
    }

    val destinations = listOf(
        AppDestination("Live-TV", "live-tv") {
            LiveTvRoute(
                providerRepository = appContainer.providerRepository,
                mediaRepository = appContainer.mediaRepository,
                epgRepository = appContainer.epgSourceRepository,
                favoritesRepository = appContainer.favoritesRepository,
                expandedProviderIds = preferences.expandedLiveTvProviderIds,
                onExpandedProviderIdsChanged = { providerIds ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateExpandedLiveTvProviderIds(providerIds)
                    }
                },
                resolveChannelLogoModel = { channel -> appContainer.resolveChannelLogoModel(channel) },
                onOpenPlayer = ::openChannel,
                onPlayableChannelsChanged = { channels -> livePlaybackChannels = channels },
                onOpenCatchUp = ::openCatchUp,
            )
        },
        AppDestination("Filme", "movies") {
            MoviesRoute(
                providerRepository = appContainer.providerRepository,
                mediaRepository = appContainer.mediaRepository,
                favoritesRepository = appContainer.favoritesRepository,
                playbackRepository = appContainer.playbackRepository,
                resolveMoviePosterModel = { movie -> appContainer.resolveMovieImageModel(movie, MediaCacheType.MoviePoster) },
                resolveMovieBackdropModel = { movie -> appContainer.resolveMovieImageModel(movie, MediaCacheType.MovieBackdrop) },
                onOpenPlayer = ::openMovie,
            )
        },
        AppDestination("Serien", "series") {
            SeriesRoute(
                providerRepository = appContainer.providerRepository,
                mediaRepository = appContainer.mediaRepository,
                favoritesRepository = appContainer.favoritesRepository,
                resolveSeriesPosterModel = { series -> appContainer.resolveSeriesImageModel(series, MediaCacheType.SeriesPoster) },
                resolveSeriesBackdropModel = { series -> appContainer.resolveSeriesImageModel(series, MediaCacheType.SeriesBackdrop) },
                onOpenPlayer = ::openEpisode,
            )
        },
        AppDestination("Suche", "search") {
            SearchRoute(
                mediaRepository = appContainer.mediaRepository,
                userPreferencesStore = appContainer.userPreferencesStore,
                autoFocusField = false,
            )
        },
        AppDestination("Einstellungen", "settings") {
            SettingsRoute(
                providerRepository = appContainer.providerRepository,
                epgSourceRepository = appContainer.epgSourceRepository,
                generalSettingsState = GeneralSettingsState(
                    launchOnBoot = preferences.general.launchOnBoot,
                    backgroundRefreshEnabled = preferences.general.backgroundRefreshEnabled,
                    rememberSorting = preferences.general.rememberSorting,
                ),
                cacheSettingsState = CacheSettingsState(
                    maxCacheSizeMb = preferences.cache.maxCacheSizeMb,
                    totalSizeBytes = cacheStats.totalSizeBytes,
                    fileCount = cacheStats.fileCount,
                ),
                playbackSettingsState = PlaybackSettingsState(
                    watchedThresholdPercent = watchedThresholdPercent,
                ),
                onBackgroundRefreshChanged = { enabled ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateGeneral(
                            preferences.general.copy(backgroundRefreshEnabled = enabled),
                        )
                        appContainer.refreshWorkScheduler.setBackgroundRefreshEnabled(enabled)
                    }
                },
                onRememberSortingChanged = { enabled ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateGeneral(
                            preferences.general.copy(rememberSorting = enabled),
                        )
                    }
                },
                onWatchedThresholdChanged = { threshold ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateHistory(
                            preferences.history.copy(watchedThresholdPercent = threshold),
                        )
                    }
                },
                onRunGlobalRefresh = {
                    appContainer.refreshWorkScheduler.enqueueGlobalRefresh()
                },
                onCacheSizeChanged = { maxSizeMb ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateCache(
                            preferences.cache.copy(maxCacheSizeMb = maxSizeMb),
                        )
                    }
                },
                onRunLogoRefresh = {
                    appContainer.refreshWorkScheduler.enqueueLogoRefresh()
                },
                onRunCacheCleanup = {
                    appContainer.refreshWorkScheduler.enqueueCacheCleanup()
                },
                onClearCache = {
                    scope.launch {
                        appContainer.mediaCacheStore.clear()
                        cacheStats = appContainer.mediaCacheStore.stats()
                    }
                },
                onReloadCacheStats = {
                    scope.launch {
                        cacheStats = appContainer.mediaCacheStore.stats()
                    }
                },
            )
        },
    )
    val selectedDestination = destinations.first { it.route == selectedRoute }
    val selectedIndex = destinations.indexOf(selectedDestination)

    VivicastScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VivicastSpacing.ScreenHorizontal, vertical = VivicastSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
        ) {
            VivicastTopNavigation(
                brand = "VIVICAST",
                items = destinations.map { it.label },
                selectedIndex = selectedIndex,
                onSelected = { index -> selectedRoute = destinations[index].route },
                onFocused = { index -> selectedRoute = destinations[index].route },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                selectedDestination.content()
            }
        }
    }

    if (playerVisible) {
        PlayerRoute(
            playerController = appContainer.playerController,
            onClose = { playerVisible = false },
            onChannelUp = { zapChannel(1) },
            onChannelDown = { zapChannel(-1) },
            onChooseAnotherChannel = {
                selectedRoute = "live-tv"
                playerVisible = false
            },
        )
    }
}

@Immutable
private data class AppDestination(
    val label: String,
    val route: String,
    val content: @Composable () -> Unit,
)

private suspend fun AppContainer.resolveChannelLogoModel(channel: Channel): Any? {
    val logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() } ?: return null
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = MediaCacheType.ChannelLogo,
            ownerId = channel.id,
            sourceUrl = logoUrl,
        ),
    )?.file
}

private suspend fun AppContainer.resolveMovieImageModel(movie: Movie, type: MediaCacheType): Any? {
    val sourceUrl = when (type) {
        MediaCacheType.MoviePoster -> movie.posterUrl
        MediaCacheType.MovieBackdrop -> movie.backdropUrl
        else -> null
    }?.takeIf { it.isNotBlank() } ?: return null
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = type,
            ownerId = movie.id,
            sourceUrl = sourceUrl,
        ),
    )?.file
}

private suspend fun AppContainer.resolveSeriesImageModel(series: Series, type: MediaCacheType): Any? {
    val sourceUrl = when (type) {
        MediaCacheType.SeriesPoster -> series.posterUrl
        MediaCacheType.SeriesBackdrop -> series.backdropUrl
        else -> null
    }?.takeIf { it.isNotBlank() } ?: return null
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = type,
            ownerId = series.id,
            sourceUrl = sourceUrl,
        ),
    )?.file
}

private suspend fun AppContainer.openChannelPlayback(
    channel: Channel,
    playbackPreferences: PlaybackPreferences,
    onStarted: () -> Unit,
) {
    val stream = playbackStreamResolver.resolve(
        PlaybackStreamRequest(
            providerId = channel.providerId,
            mediaId = channel.id,
            mediaType = MediaType.Channel,
            remoteId = channel.remoteId,
        ),
    ).resolvedStreamOrNull() ?: return
    val timeshift = playbackPreferences.timeshiftConfig()

    playerController.play(
        PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.Channel,
            title = channel.name,
            streamUrl = stream.url,
            seekable = timeshift != null,
            timeshift = timeshift,
        ),
    )
    onStarted()
}

private suspend fun AppContainer.openMoviePlayback(movie: Movie, resumeProgress: Boolean, onStarted: () -> Unit) {
    val stream = playbackStreamResolver.resolve(
        PlaybackStreamRequest(
            providerId = movie.providerId,
            mediaId = movie.id,
            mediaType = MediaType.Movie,
            remoteId = movie.remoteId,
            containerExtension = movie.containerExtension,
        ),
    ).resolvedStreamOrNull() ?: return
    val progress = if (resumeProgress) {
        playbackRepository.getProgress(movie.providerId, MediaType.Movie, movie.id)
            ?.takeUnless { it.isCompleted }
    } else {
        null
    }

    playerController.play(
        PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.Movie,
            title = movie.name,
            streamUrl = stream.url,
            seekable = true,
            startPositionMillis = progress?.positionMillis ?: 0L,
        ),
    )
    onStarted()
}

private suspend fun AppContainer.openEpisodePlayback(episode: Episode, onStarted: () -> Unit) {
    val stream = playbackStreamResolver.resolve(
        PlaybackStreamRequest(
            providerId = episode.providerId,
            mediaId = episode.id,
            mediaType = MediaType.Episode,
            remoteId = episode.remoteId,
            containerExtension = episode.containerExtension,
        ),
    ).resolvedStreamOrNull() ?: return
    val progress = playbackRepository.getProgress(episode.providerId, MediaType.Episode, episode.id)
        ?.takeUnless { it.isCompleted }

    playerController.play(
        PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.Episode,
            title = episode.name,
            streamUrl = stream.url,
            seekable = true,
            startPositionMillis = progress?.positionMillis ?: 0L,
        ),
    )
    onStarted()
}

private suspend fun AppContainer.openCatchUpPlayback(channel: Channel, program: EpgProgram, onStarted: () -> Unit) {
    if (!program.isCatchupAvailable) return
    val stream = playbackStreamResolver.resolve(
        PlaybackStreamRequest(
            providerId = channel.providerId,
            mediaId = channel.id,
            mediaType = MediaType.Channel,
            remoteId = channel.remoteId,
            catchupStartMillis = program.startTime,
            catchupEndMillis = program.endTime,
        ),
    ).resolvedStreamOrNull() ?: return

    playerController.play(
        PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.CatchUp,
            title = "${channel.name} - ${program.title}",
            streamUrl = stream.url,
            seekable = true,
        ),
    )
    onStarted()
}

private suspend fun AppContainer.savePlaybackProgress(
    state: VivicastPlayerState,
    completedThresholdPercent: Int,
) {
    val request = state.request ?: return
    val mediaType = request.mediaType.toDomainProgressMediaType() ?: return
    if (state.status != PlaybackStatus.Playing && state.status != PlaybackStatus.Paused) return
    if (state.positionMillis <= 0L) return

    val now = System.currentTimeMillis()
    val progressPercent = progressPercent(state.positionMillis, state.durationMillis)
    val thresholdPercent = completedThresholdPercent.coerceIn(
        WATCHED_THRESHOLD_MIN_PERCENT,
        WATCHED_THRESHOLD_MAX_PERCENT,
    )
    val existing = playbackRepository.getProgress(request.providerId, mediaType, request.mediaId)
    playbackRepository.saveProgress(
        PlaybackProgress(
            id = existing?.id ?: playbackProgressId(request.providerId, mediaType, request.mediaId),
            providerId = request.providerId,
            mediaType = mediaType,
            mediaId = request.mediaId,
            positionMillis = state.positionMillis,
            durationMillis = state.durationMillis,
            progressPercent = progressPercent,
            isCompleted = progressPercent >= thresholdPercent,
            lastWatchedAt = now,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        ),
    )
}

private fun PlaybackStreamResult.resolvedStreamOrNull() =
    (this as? PlaybackStreamResult.Resolved)?.stream

private fun playbackId(providerId: String, mediaType: MediaType, mediaId: String): String =
    "$providerId:${mediaType.name.lowercase()}:$mediaId:${System.currentTimeMillis()}"

private fun playbackProgressId(providerId: String, mediaType: MediaType, mediaId: String): String =
    "$providerId:progress:${mediaType.name.lowercase()}:$mediaId"

private fun PlaybackMediaType.toDomainProgressMediaType(): MediaType? =
    when (this) {
        PlaybackMediaType.Movie -> MediaType.Movie
        PlaybackMediaType.Episode -> MediaType.Episode
        PlaybackMediaType.Channel,
        PlaybackMediaType.CatchUp -> null
    }

private fun progressPercent(positionMillis: Long, durationMillis: Long): Int {
    if (durationMillis <= 0L) return 0
    return ((positionMillis.coerceAtLeast(0L) * 100L) / durationMillis).coerceIn(0L, 100L).toInt()
}

private fun PlaybackPreferences.timeshiftConfig(): PlaybackTimeshiftConfig? {
    val minutes = when (timeshiftMinutes) {
        15, 30, 60, 120 -> timeshiftMinutes
        else -> return null
    }
    return PlaybackTimeshiftConfig(
        storage = timeshiftStorage.toPlayerStorage(),
        windowMillis = minutes * 60_000L,
    )
}

private fun TimeshiftStoragePreference.toPlayerStorage(): PlaybackTimeshiftStorage =
    when (this) {
        TimeshiftStoragePreference.Automatic -> PlaybackTimeshiftStorage.Automatic
        TimeshiftStoragePreference.Ram -> PlaybackTimeshiftStorage.Ram
        TimeshiftStoragePreference.InternalStorage -> PlaybackTimeshiftStorage.InternalStorage
    }

private fun Int.floorMod(modulus: Int): Int =
    ((this % modulus) + modulus) % modulus

private const val WATCHED_THRESHOLD_MIN_PERCENT = 50
private const val WATCHED_THRESHOLD_MAX_PERCENT = 100
