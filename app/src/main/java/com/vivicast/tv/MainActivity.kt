package com.vivicast.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vivicast.tv.core.cache.MediaCacheStats
import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheType
import com.vivicast.tv.core.database.VIVICAST_DATABASE_VERSION
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.ExternalPlayerPreference
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.StartDestinationPreference
import com.vivicast.tv.core.datastore.TimeshiftStoragePreference
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.designsystem.VivicastScreenBackground
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.core.designsystem.VivicastTopNavigation
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackReturnTarget
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.PlaybackTimeshiftConfig
import com.vivicast.tv.core.player.PlaybackTimeshiftStorage
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.core.security.PinSecurity
import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.core.security.PinVerificationResult
import com.vivicast.tv.backup.StandardBackupRestorePreview
import com.vivicast.tv.backup.StandardBackupRestoreValidation
import com.vivicast.tv.backup.decryptFullBackupPayload
import com.vivicast.tv.backup.validateFullBackupPayloadForRestore
import com.vivicast.tv.backup.validateStandardBackupForRestore
import com.vivicast.tv.diagnostics.DiagnosticsAbout
import com.vivicast.tv.feature.settings.CacheSettingsState
import com.vivicast.tv.feature.settings.AboutAppState
import com.vivicast.tv.feature.settings.DiagnosticsSettingsState
import com.vivicast.tv.feature.settings.EpgSettingsState
import com.vivicast.tv.feature.settings.GeneralSettingsState
import com.vivicast.tv.feature.settings.HistoryClearTarget
import com.vivicast.tv.feature.settings.ParentalControlSettingsState
import com.vivicast.tv.feature.settings.ParentalProtectionArea
import com.vivicast.tv.feature.settings.PlaybackExternalPlayerMode
import com.vivicast.tv.feature.settings.PlaybackSettingsState
import com.vivicast.tv.feature.settings.PlaybackTimeshiftStorageMode
import com.vivicast.tv.feature.settings.SettingsStartDestination
import com.vivicast.tv.feature.home.HomeRoute
import com.vivicast.tv.feature.livetv.LiveTvRoute
import com.vivicast.tv.feature.movies.MoviesRoute
import com.vivicast.tv.feature.player.PlayerRoute
import com.vivicast.tv.feature.search.SearchRoute
import com.vivicast.tv.feature.series.SeriesRoute
import com.vivicast.tv.feature.settings.SettingsRoute
import com.vivicast.tv.di.AppContainer
import com.vivicast.tv.data.playback.PlaybackStreamRequest
import com.vivicast.tv.data.playback.PlaybackStreamResult
import com.vivicast.tv.data.playback.PLAYBACK_COMPLETION_THRESHOLD_PERCENT
import com.vivicast.tv.data.playback.automaticPlaybackProgressPercent
import com.vivicast.tv.data.playback.shouldSaveAutomaticPlaybackProgress
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var deepLinkData by mutableStateOf<Uri?>(null)
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkData = intent?.data
        appContainer = (application as VivicastApplication).appContainer
        setContent {
            VivicastTheme {
                VivicastApp(
                    appContainer = appContainer,
                    deepLinkData = deepLinkData,
                    onDeepLinkConsumed = { deepLinkData = null },
                )
            }
        }
    }

    override fun onStop() {
        lifecycleScope.launch {
            appContainer.savePlaybackProgress(
                state = appContainer.playerController.state.value,
                automaticProgressSaveTimes = mutableMapOf(),
                forceSave = true,
            )
            appContainer.playerController.stop()
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkData = intent.data
    }
}

@Composable
private fun VivicastApp(
    appContainer: AppContainer,
    deepLinkData: Uri?,
    onDeepLinkConsumed: () -> Unit,
) {
    var playerVisible by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf(ROUTE_HOME) }
    var cacheStats by remember { mutableStateOf(MediaCacheStats(totalSizeBytes = 0L, fileCount = 0)) }
    var livePlaybackChannels by remember { mutableStateOf(emptyList<Channel>()) }
    var liveTvSearchTarget by remember { mutableStateOf<LiveTvSearchTarget?>(null) }
    var movieSearchTarget by remember { mutableStateOf<Movie?>(null) }
    var seriesSearchTarget by remember { mutableStateOf<SeriesTarget?>(null) }
    var pendingExternalPlaybackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var pendingProtectionUnlock by remember { mutableStateOf<PendingProtectionUnlock?>(null) }
    var pendingStandardRestore by remember { mutableStateOf<PendingStandardRestore?>(null) }
    var pendingSafetyFailedRestore by remember { mutableStateOf<PendingStandardRestore?>(null) }
    var pendingSystemTargetUnavailable by remember { mutableStateOf<SystemTargetUnavailable?>(null) }
    var pendingEncryptedFullExportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var pendingEncryptedFullImportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var unlockedProtectionAreas by remember { mutableStateOf(emptySet<ParentalProtectionArea>()) }
    var topNavigationFocused by remember { mutableStateOf(false) }
    var lastTopNavigationBackAt by remember { mutableStateOf(0L) }
    var regularStartApplied by remember { mutableStateOf(false) }
    var explicitSystemTargetSeen by remember { mutableStateOf(deepLinkData != null) }
    val topNavigationFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        Toast.makeText(context, "Fortschritt konnte nicht automatisch ermittelt werden.", Toast.LENGTH_SHORT).show()
    }
    val standardBackupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val message = runCatching {
                    val json = appContainer.standardBackupExporter.exportJson()
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("backup output unavailable")
                    "Backup exportiert."
                }.getOrElse {
                    "Backup konnte nicht gespeichert werden."
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val standardBackupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, "Backup-Datei ungueltig.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                when (val validation = validateStandardBackupForRestore(text)) {
                    is StandardBackupRestoreValidation.Valid -> {
                        pendingStandardRestore = PendingStandardRestore(text, validation.preview)
                    }
                    is StandardBackupRestoreValidation.Invalid -> {
                        Toast.makeText(context, validation.message, Toast.LENGTH_SHORT).show()
                    }
                    is StandardBackupRestoreValidation.SafetyBackupFailed -> Unit
                }
            }
        }
    }
    val encryptedFullBackupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val passphrase = pendingEncryptedFullExportPassphrase
        pendingEncryptedFullExportPassphrase = null
        if (uri != null && passphrase != null) {
            scope.launch {
                val message = runCatching {
                    val json = appContainer.standardBackupExporter.exportEncryptedFullJson(
                        passphrase = passphrase,
                        appVersion = context.aboutAppState().appVersion,
                        packageName = context.packageName,
                    )
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("backup output unavailable")
                    "Vollbackup exportiert."
                }.getOrElse {
                    "Vollbackup konnte nicht gespeichert werden."
                }
                passphrase.fill('\u0000')
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } else {
            passphrase?.fill('\u0000')
        }
    }
    val encryptedFullBackupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val passphrase = pendingEncryptedFullImportPassphrase
        pendingEncryptedFullImportPassphrase = null
        if (uri != null && passphrase != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                if (text.isNullOrBlank()) {
                    passphrase.fill('\u0000')
                    Toast.makeText(context, "Backup-Datei ungültig.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val payload = decryptFullBackupPayload(text, passphrase)
                passphrase.fill('\u0000')
                if (payload == null) {
                    Toast.makeText(context, "Passphrase falsch oder Backup beschädigt.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                when (val validation = validateFullBackupPayloadForRestore(payload)) {
                    is StandardBackupRestoreValidation.Valid -> {
                        pendingStandardRestore = PendingStandardRestore(
                            jsonText = payload,
                            preview = validation.preview,
                            encryptedFull = true,
                        )
                    }
                    is StandardBackupRestoreValidation.Invalid -> {
                        Toast.makeText(context, validation.message, Toast.LENGTH_SHORT).show()
                    }
                    is StandardBackupRestoreValidation.SafetyBackupFailed -> Unit
                }
            }
        } else {
            passphrase?.fill('\u0000')
        }
    }
    var loadedPreferences by remember { mutableStateOf<UserPreferences?>(null) }
    LaunchedEffect(appContainer) {
        appContainer.userPreferencesStore.values.collectLatest { loadedPreferences = it }
    }
    val preferences = loadedPreferences ?: UserPreferences()
    LaunchedEffect(
        loadedPreferences,
        preferences.diagnostics.diagnosticsLoggingEnabled,
        preferences.diagnostics.retentionDays,
    ) {
        if (loadedPreferences != null && preferences.diagnostics.diagnosticsLoggingEnabled) {
            appContainer.diagnosticsStore.setLoggingEnabled(true, preferences.diagnostics.retentionDays)
        }
    }
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val message = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        appContainer.diagnosticsStore.exportZip(
                            output = output,
                            about = context.diagnosticsAbout(),
                            preferences = preferences.diagnostics,
                        )
                    } ?: error("diagnostics output unavailable")
                    "Diagnoseprotokoll exportiert."
                }.getOrElse {
                    "Diagnoseprotokoll konnte nicht exportiert werden."
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val playerState by appContainer.playerController.state.collectAsState()
    var pinSecurityState by remember { mutableStateOf(PinSecurityState()) }
    var pinSecurityLoaded by remember { mutableStateOf(false) }
    val automaticProgressSaveTimes = remember { mutableMapOf<String, Long>() }
    var nextAutoNextEpisode by remember { mutableStateOf<Episode?>(null) }

    fun requestProtectionUnlock(
        area: ParentalProtectionArea?,
        title: String,
        forcePrompt: Boolean = false,
        onUnlocked: () -> Unit,
    ) {
        if (area == null || !pinSecurityState.hasPin || (!forcePrompt && area in unlockedProtectionAreas)) {
            onUnlocked()
        } else {
            pendingProtectionUnlock = PendingProtectionUnlock(area, title, onUnlocked)
        }
    }

    fun runStandardRestore(
        restore: PendingStandardRestore,
        continueAfterSafetyBackupFailure: Boolean = false,
    ) {
        scope.launch {
            val result = if (restore.encryptedFull) {
                appContainer.standardBackupRestorer.restoreFullPayload(
                    jsonText = restore.jsonText,
                    continueAfterSafetyBackupFailure = continueAfterSafetyBackupFailure,
                )
            } else {
                appContainer.standardBackupRestorer.restore(
                    jsonText = restore.jsonText,
                    continueAfterSafetyBackupFailure = continueAfterSafetyBackupFailure,
                )
            }
            when (result) {
                is StandardBackupRestoreValidation.Valid -> {
                    pendingStandardRestore = null
                    pendingSafetyFailedRestore = null
                    pinSecurityState = PinSecurityState()
                    unlockedProtectionAreas = emptySet()
                    appContainer.syncWatchNext()
                    Toast.makeText(context, "Backup wiederhergestellt.", Toast.LENGTH_SHORT).show()
                }
                is StandardBackupRestoreValidation.SafetyBackupFailed -> {
                    pendingStandardRestore = null
                    pendingSafetyFailedRestore = PendingStandardRestore(
                        jsonText = restore.jsonText,
                        preview = result.preview,
                        encryptedFull = restore.encryptedFull,
                    )
                }
                is StandardBackupRestoreValidation.Invalid -> {
                    pendingStandardRestore = null
                    pendingSafetyFailedRestore = null
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun selectRoute(route: String) {
        if (!pinSecurityLoaded && route.canBeProtected()) return
        requestProtectionUnlock(pinSecurityState.protectionAreaForRoute(route), route.protectionTitle()) {
            selectedRoute = route
        }
    }

    fun focusRoute(route: String) {
        if (!pinSecurityLoaded && route.canBeProtected()) return
        val area = pinSecurityState.protectionAreaForRoute(route)
        if (area == null || !pinSecurityState.hasPin || area in unlockedProtectionAreas) {
            selectedRoute = route
        }
    }

    fun launchExternalPlayback(request: PlaybackRequest): Boolean {
        return try {
            appContainer.playerController.stop()
            externalPlayerLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(request.streamUrl)))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Kein externer Player verfuegbar.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun startInternalPlayback(request: PlaybackRequest) {
        scope.launch {
            appContainer.playerController.play(request)
            playerVisible = true
        }
    }

    fun playNextAutoNextEpisode() {
        val episode = nextAutoNextEpisode ?: return
        scope.launch {
            appContainer.openEpisodePlayback(
                episode = episode,
                origin = PlaybackOrigin.SeriesDetail,
            ) {
                playerVisible = true
            }
        }
    }

    fun openChannel(
        channel: Channel,
        origin: PlaybackOrigin = PlaybackOrigin.LiveTv,
    ) {
        scope.launch {
            appContainer.openChannelPlayback(
                channel = channel,
                playbackPreferences = preferences.playback,
                origin = origin,
            ) {
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

    fun openMovie(
        movie: Movie,
        resumeProgress: Boolean,
        origin: PlaybackOrigin = PlaybackOrigin.MovieDetail,
    ) {
        requestProtectionUnlock(pinSecurityState.protectionAreaForMovie(movie), "Film freigeben") {
            scope.launch {
                when (preferences.playback.externalPlayer) {
                    ExternalPlayerPreference.External -> {
                        appContainer.createMoviePlaybackRequest(movie, resumeProgress, origin)
                            ?.let(::launchExternalPlayback)
                    }
                    ExternalPlayerPreference.AskEveryTime -> {
                        pendingExternalPlaybackRequest = appContainer.createMoviePlaybackRequest(movie, resumeProgress, origin)
                    }
                    ExternalPlayerPreference.Internal -> {
                        appContainer.openMoviePlayback(
                            movie = movie,
                            resumeProgress = resumeProgress,
                            origin = origin,
                        ) {
                            playerVisible = true
                        }
                    }
                }
            }
        }
    }

    fun openSeriesTarget(series: Series) {
        requestProtectionUnlock(pinSecurityState.protectionAreaForSeries(series), "Serie freigeben") {
            seriesSearchTarget = series.toSeriesTarget()
            selectRoute("series")
        }
    }

    fun openEpisodeTarget(episode: Episode) {
        requestProtectionUnlock(pinSecurityState.protectionAreaForEpisode(episode), "Episode freigeben") {
            scope.launch {
                val series = appContainer.mediaRepository.getSeries(episode.providerId, episode.seriesId)
                seriesSearchTarget = episode.toSeriesTarget(categoryId = series?.categoryId)
                selectRoute("series")
            }
        }
    }

    fun openEpisode(
        episode: Episode,
        origin: PlaybackOrigin = PlaybackOrigin.SeriesDetail,
    ) {
        requestProtectionUnlock(pinSecurityState.protectionAreaForEpisode(episode), "Episode freigeben") {
            scope.launch {
                when (preferences.playback.externalPlayer) {
                    ExternalPlayerPreference.External -> {
                        appContainer.createEpisodePlaybackRequest(episode, origin)
                            ?.let(::launchExternalPlayback)
                    }
                    ExternalPlayerPreference.AskEveryTime -> {
                        pendingExternalPlaybackRequest = appContainer.createEpisodePlaybackRequest(episode, origin)
                    }
                    ExternalPlayerPreference.Internal -> {
                        appContainer.openEpisodePlayback(
                            episode = episode,
                            origin = origin,
                        ) {
                            playerVisible = true
                        }
                    }
                }
            }
        }
    }

    fun openCatchUp(
        channel: Channel,
        program: EpgProgram,
        origin: PlaybackOrigin = PlaybackOrigin.LiveTv,
    ) {
        scope.launch {
            appContainer.openCatchUpPlayback(
                channel = channel,
                program = program,
                origin = origin,
            ) {
                playerVisible = true
            }
        }
    }

    LaunchedEffect(deepLinkData, pinSecurityLoaded) {
        if (!pinSecurityLoaded) return@LaunchedEffect
        val uri = deepLinkData ?: return@LaunchedEffect
        explicitSystemTargetSeen = true
        val providerStableKey = uri.pathSegments.getOrNull(0)
        val mediaStableKey = uri.pathSegments.getOrNull(1)
        val opened = if (uri.scheme == "vivicast" && providerStableKey != null && mediaStableKey != null) {
            when (uri.host) {
                "channel" -> appContainer.mediaRepository
                    .getChannelByStableKeys(providerStableKey, mediaStableKey)
                    ?.let { channel ->
                        liveTvSearchTarget = LiveTvSearchTarget(channel = channel)
                        selectRoute("live-tv")
                        true
                    } == true
                "movie" -> appContainer.mediaRepository
                    .getMovieByStableKeys(providerStableKey, mediaStableKey)
                    ?.let { movie ->
                        requestProtectionUnlock(pinSecurityState.protectionAreaForMovie(movie), "Film freigeben") {
                            movieSearchTarget = movie
                            selectRoute("movies")
                        }
                        true
                    } == true
                "series" -> appContainer.mediaRepository
                    .getSeriesByStableKeys(providerStableKey, mediaStableKey)
                    ?.let { series ->
                        openSeriesTarget(series)
                        true
                    } == true
                "episode" -> appContainer.mediaRepository
                    .getEpisodeByStableKeys(providerStableKey, mediaStableKey)
                    ?.let { episode ->
                        openEpisodeTarget(episode)
                        true
                    } == true
                else -> false
            }
        } else {
            false
        }
        if (!opened) {
            pendingSystemTargetUnavailable = SystemTargetUnavailable(
                title = "Inhalt nicht verfügbar",
                body = "Das Android-TV-Ziel fehlt, ist deaktiviert oder benötigt aktualisierte Zugangsdaten.",
            )
        }
        onDeepLinkConsumed()
    }

    LaunchedEffect(loadedPreferences, pinSecurityLoaded, explicitSystemTargetSeen) {
        val currentPreferences = loadedPreferences ?: return@LaunchedEffect
        if (regularStartApplied || explicitSystemTargetSeen || !pinSecurityLoaded) return@LaunchedEffect
        regularStartApplied = true
        selectRoute(currentPreferences.general.startDestination.toRoute())
    }

    LaunchedEffect(preferences.general.backgroundRefreshEnabled) {
        appContainer.refreshWorkScheduler.setBackgroundRefreshEnabled(
            enabled = preferences.general.backgroundRefreshEnabled,
        )
    }

    LaunchedEffect(preferences.general.globalUserAgent) {
        appContainer.updateGlobalUserAgent(preferences.general.globalUserAgent)
    }

    LaunchedEffect(selectedRoute) {
        lastTopNavigationBackAt = 0L
        if (selectedRoute == "settings") {
            cacheStats = appContainer.mediaCacheStore.stats()
        }
    }

    LaunchedEffect(appContainer) {
        pinSecurityState = appContainer.pinSecurityStateStore.read()
        pinSecurityLoaded = true
    }

    LaunchedEffect(
        pinSecurityLoaded,
        pinSecurityState.protectMovies,
        pinSecurityState.protectSeries,
        pinSecurityState.protectAdultContent,
    ) {
        if (!pinSecurityLoaded) return@LaunchedEffect
        appContainer.mediaRepository.rebuildAndroidTvSearchIndex(
            protectMovies = pinSecurityState.protectMovies,
            protectSeries = pinSecurityState.protectSeries,
            protectAdultContent = pinSecurityState.protectAdultContent,
        )
    }

    LaunchedEffect(appContainer) {
        appContainer.playerController.state.collectLatest { state ->
            appContainer.savePlaybackProgress(
                state = state,
                automaticProgressSaveTimes = automaticProgressSaveTimes,
            )
        }
    }

    LaunchedEffect(playerState.request?.playbackId, playerState.request?.mediaId, playerState.request?.mediaType) {
        val request = playerState.request
        nextAutoNextEpisode = if (request?.mediaType == PlaybackMediaType.Episode) {
            appContainer.mediaRepository.getEpisode(request.providerId, request.mediaId)
                ?.let { appContainer.mediaRepository.getNextEpisode(it) }
        } else {
            null
        }
    }

    val destinations = listOf(
        AppDestination("Home", ROUTE_HOME) {
            HomeRoute(
                playbackRepository = appContainer.playbackRepository,
                mediaRepository = appContainer.mediaRepository,
                resolveChannelLogoModel = { channel -> appContainer.resolveChannelLogoModel(channel) },
                resolveMoviePosterModel = { movie -> appContainer.resolveMovieImageModel(movie, MediaCacheType.MoviePoster) },
                resolveEpisodeImageModel = { episode -> appContainer.resolveEpisodeImageModel(episode) },
                onOpenMovie = { movie -> openMovie(movie, resumeProgress = true, origin = PlaybackOrigin.Home) },
                onOpenEpisode = { episode -> openEpisode(episode, origin = PlaybackOrigin.Home) },
                onOpenChannel = { channel -> openChannel(channel, origin = PlaybackOrigin.Home) },
                onAddPlaylist = { selectRoute("settings") },
            )
        },
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
                onOpenPlayer = { channel -> openChannel(channel, origin = PlaybackOrigin.LiveTv) },
                onPlayableChannelsChanged = { channels -> livePlaybackChannels = channels },
                onOpenCatchUp = { channel, program -> openCatchUp(channel, program, origin = PlaybackOrigin.LiveTv) },
                targetProviderId = liveTvSearchTarget?.channel?.providerId,
                targetCategoryId = liveTvSearchTarget?.channel?.categoryId,
                targetChannelId = liveTvSearchTarget?.channel?.id,
                targetEpgProgramId = liveTvSearchTarget?.program?.id,
                targetEpgStartTime = liveTvSearchTarget?.program?.startTime,
                onTargetConsumed = { liveTvSearchTarget = null },
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
                onOpenPlayer = { movie, resumeProgress ->
                    openMovie(
                        movie = movie,
                        resumeProgress = resumeProgress,
                        origin = PlaybackOrigin.MovieDetail,
                    )
                },
                targetProviderId = movieSearchTarget?.providerId,
                targetCategoryId = movieSearchTarget?.categoryId,
                targetMovieId = movieSearchTarget?.id,
                onTargetConsumed = { movieSearchTarget = null },
            )
        },
        AppDestination("Serien", "series") {
            SeriesRoute(
                providerRepository = appContainer.providerRepository,
                mediaRepository = appContainer.mediaRepository,
                favoritesRepository = appContainer.favoritesRepository,
                playbackRepository = appContainer.playbackRepository,
                resolveSeriesPosterModel = { series -> appContainer.resolveSeriesImageModel(series, MediaCacheType.SeriesPoster) },
                resolveSeriesBackdropModel = { series -> appContainer.resolveSeriesImageModel(series, MediaCacheType.SeriesBackdrop) },
                onOpenPlayer = { episode -> openEpisode(episode, origin = PlaybackOrigin.SeriesDetail) },
                targetProviderId = seriesSearchTarget?.providerId,
                targetCategoryId = seriesSearchTarget?.categoryId,
                targetSeriesId = seriesSearchTarget?.seriesId,
                targetSeasonId = seriesSearchTarget?.seasonId,
                targetEpisodeId = seriesSearchTarget?.episodeId,
                onTargetConsumed = { seriesSearchTarget = null },
            )
        },
        AppDestination("Suche", "search") {
            SearchRoute(
                mediaRepository = appContainer.mediaRepository,
                autoFocusField = false,
                onOpenChannel = { channel ->
                    liveTvSearchTarget = LiveTvSearchTarget(channel = channel)
                    selectRoute("live-tv")
                },
                onOpenMovie = { movie ->
                    movieSearchTarget = movie
                    selectRoute("movies")
                },
                onOpenSeries = { series ->
                    seriesSearchTarget = series.toSeriesTarget()
                    selectRoute("series")
                },
                onOpenEpgProgram = { program ->
                    scope.launch {
                        appContainer.mediaRepository.getChannel(program.providerId, program.channelId)?.let { channel ->
                            liveTvSearchTarget = LiveTvSearchTarget(channel = channel, program = program)
                            selectRoute("live-tv")
                        }
                    }
                },
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
                    startDestination = preferences.general.startDestination.toSettingsStartDestination(),
                ),
                epgSettingsState = EpgSettingsState(
                    pastRetentionDays = preferences.epg.pastRetentionDays,
                    futureRetentionDays = preferences.epg.futureRetentionDays,
                    refreshIntervalHours = preferences.epg.refreshIntervalHours,
                    refreshOnAppStartEnabled = preferences.epg.refreshOnAppStartEnabled,
                    refreshOnPlaylistChangeEnabled = preferences.epg.refreshOnPlaylistChangeEnabled,
                ),
                playbackSettingsState = PlaybackSettingsState(
                    externalPlayer = preferences.playback.externalPlayer.toSettingsExternalPlayerMode(),
                    timeshiftEnabled = preferences.playback.timeshiftEnabled,
                    timeshiftMinutes = preferences.playback.timeshiftMinutes,
                    timeshiftStorage = preferences.playback.timeshiftStorage.toSettingsTimeshiftStorageMode(),
                    autoNextEnabled = preferences.playback.autoNextEnabled,
                    autoNextCountdownSeconds = preferences.playback.autoNextCountdownSeconds,
                ),
                parentalControlSettingsState = ParentalControlSettingsState(
                    hasPin = pinSecurityState.hasPin,
                    lockedUntilMillis = pinSecurityState.lockedUntilMillis,
                    protectSettings = pinSecurityState.protectSettings,
                    protectMovies = pinSecurityState.protectMovies,
                    protectSeries = pinSecurityState.protectSeries,
                    protectAdultContent = pinSecurityState.protectAdultContent,
                ),
                cacheSettingsState = CacheSettingsState(
                    totalSizeBytes = cacheStats.totalSizeBytes,
                    fileCount = cacheStats.fileCount,
                ),
                aboutAppState = context.aboutAppState(),
                diagnosticsSettingsState = preferences.diagnostics.toSettingsDiagnosticsState(),
                onTestProviderConnection = { request ->
                    appContainer.testProviderConnection(request)
                },
                onProviderSaved = { providerId ->
                    appContainer.refreshWorkScheduler.enqueuePlaylistRefresh(providerId)
                },
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
                onStartDestinationChanged = { startDestination ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateGeneral(
                            preferences.general.copy(
                                startDestination = startDestination.toDataStoreStartDestinationPreference(),
                            ),
                        )
                    }
                },
                onEpgPreferencesChanged = { epg ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateEpg(
                            preferences.epg.copy(
                                pastRetentionDays = epg.pastRetentionDays,
                                futureRetentionDays = epg.futureRetentionDays,
                                refreshIntervalHours = epg.refreshIntervalHours,
                                refreshOnAppStartEnabled = epg.refreshOnAppStartEnabled,
                                refreshOnPlaylistChangeEnabled = epg.refreshOnPlaylistChangeEnabled,
                            ),
                        )
                    }
                },
                onPlaybackPreferencesChanged = { playback ->
                    scope.launch {
                        appContainer.userPreferencesStore.updatePlayback(
                            preferences.playback.copy(
                                externalPlayer = playback.externalPlayer.toDataStoreExternalPlayerPreference(),
                                timeshiftEnabled = playback.timeshiftEnabled,
                                timeshiftMinutes = playback.timeshiftMinutes,
                                timeshiftStorage = playback.timeshiftStorage.toDataStoreTimeshiftStoragePreference(),
                                autoNextEnabled = playback.autoNextEnabled,
                                autoNextCountdownSeconds = playback.autoNextCountdownSeconds,
                            ),
                        )
                    }
                },
                onDiagnosticsSettingsChanged = { diagnostics ->
                    scope.launch {
                        val updated = preferences.diagnostics.copy(
                            diagnosticsLoggingEnabled = diagnostics.diagnosticsLoggingEnabled,
                            retentionDays = diagnostics.retentionDays.coerceIn(1, 7),
                        )
                        appContainer.userPreferencesStore.updateDiagnostics(updated)
                        appContainer.diagnosticsStore.setLoggingEnabled(
                            enabled = updated.diagnosticsLoggingEnabled,
                            retentionDays = updated.retentionDays,
                        )
                    }
                },
                onExportDiagnostics = {
                    diagnosticsExportLauncher.launch("vivicast-diagnostics.zip")
                },
                onCopySupportInformation = {
                    copySupportInformation(context, context.aboutAppState().supportInformationText)
                },
                onSetPin = { pin ->
                    runCatching { PinSecurity.setPin(pin) }.fold(
                        onSuccess = { newState ->
                            val updatedState = newState.copy(
                                protectSettings = pinSecurityState.protectSettings,
                                protectMovies = pinSecurityState.protectMovies,
                                protectSeries = pinSecurityState.protectSeries,
                                protectAdultContent = pinSecurityState.protectAdultContent,
                            )
                            pinSecurityState = updatedState
                            unlockedProtectionAreas = emptySet()
                            scope.launch { appContainer.pinSecurityStateStore.write(updatedState) }
                            null
                        },
                        onFailure = { "PIN muss aus vier Ziffern bestehen." },
                    )
                },
                onChangePin = { currentPin, newPin ->
                    when (val result = PinSecurity.verifyAndUpdate(currentPin, pinSecurityState, System.currentTimeMillis())) {
                        is PinVerificationResult.Success -> runCatching { PinSecurity.setPin(newPin) }.fold(
                            onSuccess = { newState ->
                                val updatedState = newState.copy(
                                    protectSettings = pinSecurityState.protectSettings,
                                    protectMovies = pinSecurityState.protectMovies,
                                    protectSeries = pinSecurityState.protectSeries,
                                    protectAdultContent = pinSecurityState.protectAdultContent,
                                )
                                pinSecurityState = updatedState
                                unlockedProtectionAreas = emptySet()
                                scope.launch { appContainer.pinSecurityStateStore.write(updatedState) }
                                null
                            },
                            onFailure = { "Neue PIN muss aus vier Ziffern bestehen." },
                        )
                        is PinVerificationResult.Failure -> {
                            pinSecurityState = result.state
                            scope.launch { appContainer.pinSecurityStateStore.write(result.state) }
                            "PIN falsch. Noch ${result.remainingAttempts} Versuche."
                        }
                        is PinVerificationResult.Locked -> {
                            pinSecurityState = result.state
                            scope.launch { appContainer.pinSecurityStateStore.write(result.state) }
                            "PIN ist vorübergehend gesperrt."
                        }
                        is PinVerificationResult.MissingPin -> "Keine PIN eingerichtet."
                    }
                },
                onDisablePin = { currentPin ->
                    when (val result = PinSecurity.verifyAndUpdate(currentPin, pinSecurityState, System.currentTimeMillis())) {
                        is PinVerificationResult.Success -> {
                            val newState = PinSecurity.clearPin()
                            pinSecurityState = newState
                            unlockedProtectionAreas = emptySet()
                            scope.launch {
                                appContainer.pinSecurityStateStore.clear()
                                appContainer.syncWatchNext()
                            }
                            null
                        }
                        is PinVerificationResult.Failure -> {
                            pinSecurityState = result.state
                            scope.launch { appContainer.pinSecurityStateStore.write(result.state) }
                            "PIN falsch. Noch ${result.remainingAttempts} Versuche."
                        }
                        is PinVerificationResult.Locked -> {
                            pinSecurityState = result.state
                            scope.launch { appContainer.pinSecurityStateStore.write(result.state) }
                            "PIN ist vorübergehend gesperrt."
                        }
                        is PinVerificationResult.MissingPin -> "Keine PIN eingerichtet."
                    }
                },
                onProtectionChanged = { area, enabled ->
                    if (!pinSecurityState.hasPin) {
                        "PIN zuerst setzen."
                    } else {
                        val newState = when (area) {
                            ParentalProtectionArea.Settings -> pinSecurityState.copy(protectSettings = enabled)
                            ParentalProtectionArea.Movies -> pinSecurityState.copy(protectMovies = enabled)
                            ParentalProtectionArea.Series -> pinSecurityState.copy(protectSeries = enabled)
                            ParentalProtectionArea.AdultContent -> pinSecurityState.copy(protectAdultContent = enabled)
                        }
                        pinSecurityState = newState
                        if (!enabled) {
                            unlockedProtectionAreas = unlockedProtectionAreas - area
                        }
                        scope.launch {
                            appContainer.pinSecurityStateStore.write(newState)
                            appContainer.syncWatchNext()
                        }
                        null
                    }
                },
                onExportStandardBackup = {
                    requestProtectionUnlock(
                        area = ParentalProtectionArea.Settings.takeIf { pinSecurityState.protectSettings },
                        title = "Backup exportieren",
                        forcePrompt = true,
                    ) {
                        standardBackupExportLauncher.launch("vivicast-standard-backup.json")
                    }
                },
                onImportStandardBackup = {
                    requestProtectionUnlock(
                        area = ParentalProtectionArea.Settings.takeIf { pinSecurityState.protectSettings },
                        title = "Backup wiederherstellen",
                        forcePrompt = true,
                    ) {
                        standardBackupImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
                    }
                },
                onExportEncryptedFullBackup = { passphrase ->
                    requestProtectionUnlock(
                        area = ParentalProtectionArea.Settings.takeIf { pinSecurityState.protectSettings },
                        title = "Vollbackup exportieren",
                        forcePrompt = true,
                    ) {
                        pendingEncryptedFullExportPassphrase?.fill('\u0000')
                        pendingEncryptedFullExportPassphrase = passphrase.toCharArray()
                        encryptedFullBackupExportLauncher.launch("vivicast-full-backup.json")
                    }
                },
                onImportEncryptedFullBackup = { passphrase ->
                    requestProtectionUnlock(
                        area = ParentalProtectionArea.Settings.takeIf { pinSecurityState.protectSettings },
                        title = "Vollbackup wiederherstellen",
                        forcePrompt = true,
                    ) {
                        pendingEncryptedFullImportPassphrase?.fill('\u0000')
                        pendingEncryptedFullImportPassphrase = passphrase.toCharArray()
                        encryptedFullBackupImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
                    }
                },
                onRunGlobalRefresh = {
                    appContainer.refreshWorkScheduler.enqueueGlobalRefresh()
                },
                onClearCache = {
                    scope.launch {
                        appContainer.mediaCacheStore.clear()
                        cacheStats = appContainer.mediaCacheStore.stats()
                    }
                },
                onClearHistory = { target ->
                    scope.launch {
                        appContainer.clearHistory(target)
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

    BackHandler(enabled = !playerVisible) {
        if (!topNavigationFocused) {
            topNavigationFocusRequester.requestFocus()
            lastTopNavigationBackAt = 0L
            return@BackHandler
        }

        val now = System.currentTimeMillis()
        if (now - lastTopNavigationBackAt <= EXIT_CONFIRMATION_WINDOW_MILLIS) {
            activity?.finish()
            return@BackHandler
        }

        lastTopNavigationBackAt = now
        Toast.makeText(context, "Zum Beenden erneut zurück", Toast.LENGTH_SHORT).show()
    }

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
                selectedFocusRequester = topNavigationFocusRequester,
                onItemFocusChanged = { focused -> topNavigationFocused = focused },
                onSelected = { index -> selectRoute(destinations[index].route) },
                onFocused = { index -> focusRoute(destinations[index].route) },
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
                selectRoute("live-tv")
                playerVisible = false
            },
            autoNextEnabled = preferences.playback.autoNextEnabled,
            autoNextCountdownSeconds = preferences.playback.autoNextCountdownSeconds,
            nextEpisodeTitle = nextAutoNextEpisode?.name,
            onPlayNextEpisode = ::playNextAutoNextEpisode,
            onAutoNextBack = {
                val request = playerState.request
                scope.launch {
                    val episode = request
                        ?.takeIf { it.mediaType == PlaybackMediaType.Episode }
                        ?.let { appContainer.mediaRepository.getEpisode(it.providerId, it.mediaId) }
                    val series = episode?.let { appContainer.mediaRepository.getSeries(it.providerId, it.seriesId) }
                    seriesSearchTarget = episode?.toSeriesTarget(categoryId = series?.categoryId)
                    selectRoute("series")
                    playerVisible = false
                }
            },
            onBeforeStop = { state ->
                if (state != null) {
                    scope.launch {
                        appContainer.savePlaybackProgress(
                            state = state,
                            automaticProgressSaveTimes = automaticProgressSaveTimes,
                            forceSave = true,
                        )
                    }
                }
            },
        )
    }

    pendingProtectionUnlock?.let { pending ->
        ProtectionUnlockDialog(
            title = pending.title,
            onDismiss = { pendingProtectionUnlock = null },
            onSubmit = { pin ->
                when (val result = PinSecurity.verifyAndUpdate(pin, pinSecurityState, System.currentTimeMillis())) {
                    is PinVerificationResult.Success -> {
                        pinSecurityState = result.state
                        unlockedProtectionAreas = unlockedProtectionAreas + pending.area
                        scope.launch { appContainer.pinSecurityStateStore.write(result.state) }
                        pendingProtectionUnlock = null
                        pending.onUnlocked()
                        null
                    }
                    is PinVerificationResult.Failure -> {
                        pinSecurityState = result.state
                        scope.launch { appContainer.pinSecurityStateStore.write(result.state) }
                        "PIN falsch. Noch ${result.remainingAttempts} Versuche."
                    }
                    is PinVerificationResult.Locked -> {
                        pinSecurityState = result.state
                        scope.launch { appContainer.pinSecurityStateStore.write(result.state) }
                        "PIN ist vorübergehend gesperrt."
                    }
                    is PinVerificationResult.MissingPin -> {
                        pendingProtectionUnlock = null
                        pending.onUnlocked()
                        null
                    }
                }
            },
        )
    }

    pendingSystemTargetUnavailable?.let { target ->
        SystemTargetUnavailableDialog(
            target = target,
            onDismiss = { pendingSystemTargetUnavailable = null },
        )
    }

    pendingExternalPlaybackRequest?.let { request ->
        ExternalPlayerChoiceDialog(
            request = request,
            onDismiss = { pendingExternalPlaybackRequest = null },
            onInternal = {
                pendingExternalPlaybackRequest = null
                startInternalPlayback(request)
            },
            onExternal = {
                pendingExternalPlaybackRequest = null
                launchExternalPlayback(request)
            },
        )
    }

    pendingStandardRestore?.let { restore ->
        StandardRestoreConfirmDialog(
            restore = restore,
            onDismiss = { pendingStandardRestore = null },
            onConfirm = { runStandardRestore(restore) },
        )
    }

    pendingSafetyFailedRestore?.let { restore ->
        StandardRestoreSafetyFailedDialog(
            restore = restore,
            onDismiss = { pendingSafetyFailedRestore = null },
            onContinue = { runStandardRestore(restore, continueAfterSafetyBackupFailure = true) },
        )
    }
}

@Immutable
private data class AppDestination(
    val label: String,
    val route: String,
    val content: @Composable () -> Unit,
)

private data class LiveTvSearchTarget(
    val channel: Channel,
    val program: EpgProgram? = null,
)

private data class SeriesTarget(
    val providerId: String,
    val categoryId: String?,
    val seriesId: String,
    val seasonId: String? = null,
    val episodeId: String? = null,
)

private data class PendingProtectionUnlock(
    val area: ParentalProtectionArea,
    val title: String,
    val onUnlocked: () -> Unit,
)

private data class PendingStandardRestore(
    val jsonText: String,
    val preview: StandardBackupRestorePreview,
    val encryptedFull: Boolean = false,
)

private data class SystemTargetUnavailable(
    val title: String,
    val body: String,
)

internal fun PinSecurityState.protectionAreaForRoute(route: String): ParentalProtectionArea? =
    when (route) {
        "settings" -> ParentalProtectionArea.Settings.takeIf { protectSettings }
        "movies" -> ParentalProtectionArea.Movies.takeIf { protectMovies }
        "series" -> ParentalProtectionArea.Series.takeIf { protectSeries }
        else -> null
    }

internal fun PinSecurityState.protectionAreaForMovie(movie: Movie): ParentalProtectionArea? =
    when {
        protectMovies -> ParentalProtectionArea.Movies
        protectAdultContent && movie.isAdult -> ParentalProtectionArea.AdultContent
        else -> null
    }

internal fun PinSecurityState.protectionAreaForEpisode(episode: Episode): ParentalProtectionArea? =
    when {
        protectSeries -> ParentalProtectionArea.Series
        protectAdultContent && episode.isAdult -> ParentalProtectionArea.AdultContent
        else -> null
    }

internal fun PinSecurityState.protectionAreaForSeries(series: Series): ParentalProtectionArea? =
    when {
        protectSeries -> ParentalProtectionArea.Series
        protectAdultContent && series.isAdult -> ParentalProtectionArea.AdultContent
        else -> null
    }

private fun String.canBeProtected(): Boolean =
    this == "settings" || this == "movies" || this == "series"

private fun String.protectionTitle(): String =
    when (this) {
        "settings" -> "Einstellungen freigeben"
        "movies" -> "Filme freigeben"
        "series" -> "Serien freigeben"
        else -> "Bereich freigeben"
    }

private fun Series.toSeriesTarget(): SeriesTarget =
    SeriesTarget(
        providerId = providerId,
        categoryId = categoryId,
        seriesId = id,
    )

private fun Episode.toSeriesTarget(categoryId: String?): SeriesTarget =
    SeriesTarget(
        providerId = providerId,
        categoryId = categoryId,
        seriesId = seriesId,
        seasonId = seasonId,
        episodeId = id,
    )

private fun StartDestinationPreference.toRoute(): String =
    when (this) {
        StartDestinationPreference.Home -> ROUTE_HOME
        StartDestinationPreference.LiveTv -> "live-tv"
        StartDestinationPreference.Movies -> "movies"
        StartDestinationPreference.Series -> "series"
    }

private fun StartDestinationPreference.toSettingsStartDestination(): SettingsStartDestination =
    when (this) {
        StartDestinationPreference.Home -> SettingsStartDestination.Home
        StartDestinationPreference.LiveTv -> SettingsStartDestination.LiveTv
        StartDestinationPreference.Movies -> SettingsStartDestination.Movies
        StartDestinationPreference.Series -> SettingsStartDestination.Series
    }

private fun SettingsStartDestination.toDataStoreStartDestinationPreference(): StartDestinationPreference =
    when (this) {
        SettingsStartDestination.Home -> StartDestinationPreference.Home
        SettingsStartDestination.LiveTv -> StartDestinationPreference.LiveTv
        SettingsStartDestination.Movies -> StartDestinationPreference.Movies
        SettingsStartDestination.Series -> StartDestinationPreference.Series
    }

@Composable
private fun ProtectionUnlockDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> String?,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = title,
                    body = error ?: "PIN eingeben, um den geschützten Bereich freizugeben.",
                    modifier = Modifier.fillMaxWidth(),
                )
                BasicTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(PIN_LENGTH) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Freigeben", modifier = Modifier.width(150.dp)) {
                        error = if (pin.length == PIN_LENGTH) onSubmit(pin) else "PIN muss aus vier Ziffern bestehen."
                    }
                    ActionPill("Abbrechen", modifier = Modifier.width(150.dp), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SystemTargetUnavailableDialog(
    target: SystemTargetUnavailable,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = target.title,
                    body = target.body,
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionPill("Schließen", modifier = Modifier.width(150.dp), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ExternalPlayerChoiceDialog(
    request: PlaybackRequest,
    onDismiss: () -> Unit,
    onInternal: () -> Unit,
    onExternal: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = "Wiedergabe starten",
                    body = "Film oder Episode extern abspielen? Externe Player liefern keinen automatischen Fortschritt zurueck.",
                    badge = request.title,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Intern", modifier = Modifier.width(140.dp), onClick = onInternal)
                    ActionPill("Extern", modifier = Modifier.width(140.dp), onClick = onExternal)
                    ActionPill("Abbrechen", modifier = Modifier.width(160.dp), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun StandardRestoreConfirmDialog(
    restore: PendingStandardRestore,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val preview = restore.preview
    val title = if (restore.encryptedFull) "Vollbackup wiederherstellen" else "Backup wiederherstellen"
    val body = if (restore.encryptedFull) {
        "Restore ersetzt lokale Quellen, EPG-Zuordnungen, Favoriten, Verlauf und Fortschritt. Enthaltene Zugangsdaten werden wiederhergestellt. Kindersicherung wird danach deaktiviert."
    } else {
        "Restore ersetzt lokale Quellen, EPG-Zuordnungen, Favoriten, Verlauf und Fortschritt. Kindersicherung wird danach deaktiviert."
    }
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = title,
                    body = body,
                    badge = "${preview.providerCount} Quellen, ${preview.favoriteCount} Favoriten",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Abbrechen", modifier = Modifier.width(150.dp), onClick = onDismiss)
                    ActionPill("Wiederherstellen", modifier = Modifier.width(190.dp), selected = true, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun StandardRestoreSafetyFailedDialog(
    restore: PendingStandardRestore,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val preview = restore.preview
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                InfoPanel(
                    title = "Sicherheitsbackup fehlgeschlagen",
                    body = "Lokale Daten bleiben unveraendert. Du kannst abbrechen oder den Restore bewusst ohne internes Sicherheitsbackup fortsetzen.",
                    badge = "${preview.providerCount} Quellen",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                    ActionPill("Abbrechen", modifier = Modifier.width(150.dp), onClick = onDismiss)
                    ActionPill("Fortsetzen", modifier = Modifier.width(160.dp), selected = true, onClick = onContinue)
                }
            }
        }
    }
}

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

private suspend fun AppContainer.resolveEpisodeImageModel(episode: Episode): Any? {
    val sourceUrl = episode.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return null
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = MediaCacheType.EpisodeImage,
            ownerId = episode.id,
            sourceUrl = sourceUrl,
        ),
    )?.file
}

private suspend fun AppContainer.openChannelPlayback(
    channel: Channel,
    playbackPreferences: PlaybackPreferences,
    origin: PlaybackOrigin,
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
            providerStableKey = stream.providerStableKey,
            mediaStableKey = channel.stableKey,
            origin = origin,
            returnTarget = PlaybackReturnTarget.LiveTv,
            title = channel.name,
            streamUrl = stream.url,
            seekable = timeshift != null,
            timeshift = timeshift,
        ),
    )
    onStarted()
}

private suspend fun AppContainer.openMoviePlayback(
    movie: Movie,
    resumeProgress: Boolean,
    origin: PlaybackOrigin,
    onStarted: () -> Unit,
) {
    val request = createMoviePlaybackRequest(movie, resumeProgress, origin) ?: return
    playerController.play(request)
    onStarted()
}

private suspend fun AppContainer.createMoviePlaybackRequest(
    movie: Movie,
    resumeProgress: Boolean,
    origin: PlaybackOrigin,
): PlaybackRequest? {
    val stream = playbackStreamResolver.resolve(
        PlaybackStreamRequest(
            providerId = movie.providerId,
            mediaId = movie.id,
            mediaType = MediaType.Movie,
            remoteId = movie.remoteId,
            containerExtension = movie.containerExtension,
        ),
    ).resolvedStreamOrNull() ?: return null
    val progress = if (resumeProgress) {
        playbackRepository.getProgress(movie.providerId, MediaType.Movie, movie.id)
            ?.takeUnless { it.isCompleted }
    } else {
        null
    }

    return PlaybackRequest(
        playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
        providerId = stream.providerId,
        mediaId = stream.mediaId,
        mediaType = PlaybackMediaType.Movie,
        providerStableKey = stream.providerStableKey,
        mediaStableKey = movie.stableKey,
        origin = origin,
        returnTarget = PlaybackReturnTarget.MovieDetail,
        title = movie.name,
        streamUrl = stream.url,
        seekable = true,
        startPositionMillis = progress?.positionMillis ?: 0L,
    )
}

private suspend fun AppContainer.openEpisodePlayback(
    episode: Episode,
    origin: PlaybackOrigin,
    onStarted: () -> Unit,
) {
    val request = createEpisodePlaybackRequest(episode, origin) ?: return
    playerController.play(request)
    onStarted()
}

private suspend fun AppContainer.createEpisodePlaybackRequest(
    episode: Episode,
    origin: PlaybackOrigin,
): PlaybackRequest? {
    val stream = playbackStreamResolver.resolve(
        PlaybackStreamRequest(
            providerId = episode.providerId,
            mediaId = episode.id,
            mediaType = MediaType.Episode,
            remoteId = episode.remoteId,
            containerExtension = episode.containerExtension,
        ),
    ).resolvedStreamOrNull() ?: return null
    val progress = playbackRepository.getProgress(episode.providerId, MediaType.Episode, episode.id)
        ?.takeUnless { it.isCompleted }

    return PlaybackRequest(
        playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
        providerId = stream.providerId,
        mediaId = stream.mediaId,
        mediaType = PlaybackMediaType.Episode,
        providerStableKey = stream.providerStableKey,
        mediaStableKey = episode.stableKey,
        origin = origin,
        returnTarget = PlaybackReturnTarget.SeriesDetail,
        title = episode.name,
        streamUrl = stream.url,
        seekable = true,
        startPositionMillis = progress?.positionMillis ?: 0L,
    )
}

private suspend fun AppContainer.openCatchUpPlayback(
    channel: Channel,
    program: EpgProgram,
    origin: PlaybackOrigin,
    onStarted: () -> Unit,
) {
    if (!channel.canStartCatchUp(program, nowMillis = System.currentTimeMillis())) return
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
            providerStableKey = stream.providerStableKey,
            mediaStableKey = channel.stableKey,
            origin = origin,
            returnTarget = PlaybackReturnTarget.LiveTv,
            title = "${channel.name} - ${program.title}",
            streamUrl = stream.url,
            seekable = true,
            epgProgramStableKey = program.stableKey,
        ),
    )
    onStarted()
}

private fun Channel.canStartCatchUp(program: EpgProgram, nowMillis: Long): Boolean {
    if (!isCatchupAvailable || !program.isCatchupAvailable) return false
    if (providerId != program.providerId || id != program.channelId) return false
    if (program.startTime >= program.endTime || program.endTime > nowMillis) return false
    if (catchupDays <= 0) return false
    val earliestAllowedStart = nowMillis - catchupDays * MILLIS_PER_DAY
    return program.startTime >= earliestAllowedStart
}

private suspend fun AppContainer.savePlaybackProgress(
    state: VivicastPlayerState,
    automaticProgressSaveTimes: MutableMap<String, Long>,
    forceSave: Boolean = false,
) {
    val request = state.request ?: return

    if (request.mediaType == PlaybackMediaType.Channel) {
        if (state.status != PlaybackStatus.Playing && state.status != PlaybackStatus.Paused) return
        val now = System.currentTimeMillis()
        playbackRepository.saveChannelHistory(
            ChannelHistory(
                id = channelHistoryId(request.providerId, request.mediaId),
                providerId = request.providerId,
                channelId = request.mediaId,
                watchedAt = now,
                durationWatchedMillis = state.positionMillis.coerceAtLeast(0L),
                updatedAt = now,
                channelStableKey = request.mediaStableKey,
            ),
        )
        return
    }

    val mediaType = request.mediaType.toDomainProgressMediaType() ?: return
    val mediaEnded = state.status == PlaybackStatus.Ended
    if (state.status != PlaybackStatus.Playing && state.status != PlaybackStatus.Paused && !mediaEnded) return
    val positionMillis = state.positionMillis.coerceAtLeast(0L)
    val durationMillis = state.durationMillis.coerceAtLeast(0L)

    val now = System.currentTimeMillis()
    val existing = playbackRepository.getProgress(request.providerId, mediaType, request.mediaId)
    if (!shouldSaveAutomaticPlaybackProgress(
            existing = existing,
            lastSavedAtMillis = automaticProgressSaveTimes[request.playbackId],
            nowMillis = now,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            force = state.status == PlaybackStatus.Paused || mediaEnded || forceSave,
            allowCreateBelowMinimum = mediaEnded,
        )
    ) {
        return
    }

    val progressPercent = automaticPlaybackProgressPercent(positionMillis, durationMillis)
    playbackRepository.saveProgress(
        PlaybackProgress(
            id = existing?.id ?: playbackProgressId(request.providerId, mediaType, request.mediaId),
            providerId = request.providerId,
            mediaType = mediaType,
            mediaId = request.mediaId,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            progressPercent = progressPercent,
            isCompleted = existing?.isCompleted == true ||
                mediaEnded ||
                progressPercent >= PLAYBACK_COMPLETION_THRESHOLD_PERCENT,
            lastWatchedAt = now,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            mediaStableKey = request.mediaStableKey,
        ),
    )
    automaticProgressSaveTimes[request.playbackId] = now
}

private suspend fun AppContainer.clearHistory(target: HistoryClearTarget) {
    when (target) {
        HistoryClearTarget.LiveTv -> playbackRepository.clearLiveTvHistory()
        HistoryClearTarget.Movies -> playbackRepository.clearMovieProgress()
        HistoryClearTarget.Series -> playbackRepository.clearSeriesProgress()
        HistoryClearTarget.Search -> mediaRepository.clearSearchHistory()
        HistoryClearTarget.All -> {
            playbackRepository.clearLiveTvHistory()
            playbackRepository.clearMovieProgress()
            playbackRepository.clearSeriesProgress()
            mediaRepository.clearSearchHistory()
        }
    }
}

@Suppress("DEPRECATION")
private fun Context.aboutAppState(): AboutAppState {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Unbekannt" }
    val languageTag = Locale.getDefault().toLanguageTag()
    val timeZoneId = TimeZone.getDefault().id
    val appVersion = packageInfo.versionName ?: "Unbekannt"
    return AboutAppState(
        appVersion = appVersion,
        packageName = packageName,
        databaseVersion = VIVICAST_DATABASE_VERSION,
        androidVersion = Build.VERSION.RELEASE ?: "Unbekannt",
        deviceModel = deviceModel,
        languageTag = languageTag,
        timeZoneId = timeZoneId,
        supportInformationText = buildSupportInformation(
            appVersion = appVersion,
            packageName = packageName,
            databaseVersion = VIVICAST_DATABASE_VERSION,
            androidVersion = Build.VERSION.RELEASE ?: "Unbekannt",
            deviceModel = deviceModel,
            languageTag = languageTag,
            timeZoneId = timeZoneId,
        ),
    )
}

private fun Context.diagnosticsAbout(): DiagnosticsAbout =
    aboutAppState().let { state ->
        DiagnosticsAbout(
            appVersion = state.appVersion,
            packageName = state.packageName,
            databaseVersion = state.databaseVersion,
            androidVersion = state.androidVersion,
            deviceModel = state.deviceModel,
            languageTag = state.languageTag,
            timeZoneId = state.timeZoneId,
        )
    }

private fun DiagnosticsPreferences.toSettingsDiagnosticsState(): DiagnosticsSettingsState =
    DiagnosticsSettingsState(
        diagnosticsLoggingEnabled = diagnosticsLoggingEnabled,
        retentionDays = retentionDays.coerceIn(1, 7),
    )

private fun copySupportInformation(context: Context, supportInformation: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Vivicast Support-Informationen", supportInformation))
    Toast.makeText(context, "Support-Informationen kopiert.", Toast.LENGTH_SHORT).show()
}

private fun buildSupportInformation(
    appVersion: String,
    packageName: String,
    databaseVersion: Int,
    androidVersion: String,
    deviceModel: String,
    languageTag: String,
    timeZoneId: String,
): String =
    listOf(
        "Vivicast Support-Informationen",
        "App-Version: $appVersion",
        "Paketname: $packageName",
        "Datenbank-Version: $databaseVersion",
        "Android-Version: $androidVersion",
        "Geraetemodell: $deviceModel",
        "Sprache: $languageTag",
        "Zeitzone: $timeZoneId",
    ).joinToString(separator = "\n")

private fun PlaybackStreamResult.resolvedStreamOrNull() =
    (this as? PlaybackStreamResult.Resolved)?.stream

private fun playbackId(providerId: String, mediaType: MediaType, mediaId: String): String =
    "$providerId:${mediaType.name.lowercase()}:$mediaId:${System.currentTimeMillis()}"

private fun playbackProgressId(providerId: String, mediaType: MediaType, mediaId: String): String =
    "$providerId:progress:${mediaType.name.lowercase()}:$mediaId"

private fun channelHistoryId(providerId: String, channelId: String): String =
    "$providerId:history:channel:$channelId"

private fun PlaybackMediaType.toDomainProgressMediaType(): MediaType? =
    when (this) {
        PlaybackMediaType.Movie -> MediaType.Movie
        PlaybackMediaType.Episode -> MediaType.Episode
        PlaybackMediaType.Channel,
        PlaybackMediaType.CatchUp -> null
    }

private fun ExternalPlayerPreference.toSettingsExternalPlayerMode(): PlaybackExternalPlayerMode =
    when (this) {
        ExternalPlayerPreference.Internal -> PlaybackExternalPlayerMode.Internal
        ExternalPlayerPreference.External -> PlaybackExternalPlayerMode.External
        ExternalPlayerPreference.AskEveryTime -> PlaybackExternalPlayerMode.AskEveryTime
    }

private fun PlaybackExternalPlayerMode.toDataStoreExternalPlayerPreference(): ExternalPlayerPreference =
    when (this) {
        PlaybackExternalPlayerMode.Internal -> ExternalPlayerPreference.Internal
        PlaybackExternalPlayerMode.External -> ExternalPlayerPreference.External
        PlaybackExternalPlayerMode.AskEveryTime -> ExternalPlayerPreference.AskEveryTime
    }

private fun PlaybackPreferences.timeshiftConfig(): PlaybackTimeshiftConfig? {
    if (!timeshiftEnabled) return null
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

private fun TimeshiftStoragePreference.toSettingsTimeshiftStorageMode(): PlaybackTimeshiftStorageMode =
    when (this) {
        TimeshiftStoragePreference.Automatic -> PlaybackTimeshiftStorageMode.Automatic
        TimeshiftStoragePreference.Ram -> PlaybackTimeshiftStorageMode.Ram
        TimeshiftStoragePreference.InternalStorage -> PlaybackTimeshiftStorageMode.InternalStorage
    }

private fun PlaybackTimeshiftStorageMode.toDataStoreTimeshiftStoragePreference(): TimeshiftStoragePreference =
    when (this) {
        PlaybackTimeshiftStorageMode.Automatic -> TimeshiftStoragePreference.Automatic
        PlaybackTimeshiftStorageMode.Ram -> TimeshiftStoragePreference.Ram
        PlaybackTimeshiftStorageMode.InternalStorage -> TimeshiftStoragePreference.InternalStorage
    }

private fun Int.floorMod(modulus: Int): Int =
    ((this % modulus) + modulus) % modulus

private const val ROUTE_HOME = "home"
private const val PIN_LENGTH = 4
private const val EXIT_CONFIRMATION_WINDOW_MILLIS = 2_000L
private const val MILLIS_PER_DAY = 86_400_000L
