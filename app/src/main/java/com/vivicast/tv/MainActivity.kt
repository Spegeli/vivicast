package com.vivicast.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheType
import com.vivicast.tv.core.database.VIVICAST_DATABASE_VERSION
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.AccentColor
import com.vivicast.tv.core.datastore.AnimationSpeedPreference
import com.vivicast.tv.core.datastore.BufferSizePreference
import com.vivicast.tv.core.datastore.DecoderPreference
import com.vivicast.tv.core.datastore.ExternalPlayerPreference
import com.vivicast.tv.core.datastore.FontScalePreference
import com.vivicast.tv.core.datastore.LanguagePreference
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.TransparencyLevel
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.designsystem.LocalFontScale
import com.vivicast.tv.core.designsystem.LocalSurfaceOpacity
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.VivicastScreenBackground
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.core.designsystem.vivicastColorScheme
import com.vivicast.tv.core.designsystem.VivicastTopNavigation
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastTextField
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackReturnTarget
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.core.security.PinSecurity
import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.core.security.PinVerificationResult
import com.vivicast.tv.backup.StandardBackupRestorePreview
import com.vivicast.tv.backup.StandardBackupRestoreValidation
import com.vivicast.tv.backup.decryptBackupPayload
import com.vivicast.tv.backup.isEncryptedBackupContainer
import com.vivicast.tv.backup.validateFullBackupPayloadForRestore
import com.vivicast.tv.diagnostics.DiagnosticsAbout
import com.vivicast.tv.diagnostics.DiagnosticsStore
import com.vivicast.tv.diagnostics.PlaybackDiagnostics
import com.vivicast.tv.feature.settings.AboutAppState
import com.vivicast.tv.feature.settings.AppearanceSettingsState
import com.vivicast.tv.feature.settings.DiagnosticsSettingsState
import com.vivicast.tv.feature.settings.EpgSettingsState
import com.vivicast.tv.feature.settings.GeneralSettingsState
import com.vivicast.tv.feature.settings.HistoryClearTarget
import com.vivicast.tv.feature.settings.ParentalControlSettingsState
import com.vivicast.tv.feature.settings.ParentalProtectionArea
import com.vivicast.tv.feature.settings.PlaybackAudioLanguage
import com.vivicast.tv.feature.settings.PlaybackBufferSizeMode
import com.vivicast.tv.feature.settings.PlaybackDecoderMode
import com.vivicast.tv.feature.settings.PlaybackExternalPlayerMode
import com.vivicast.tv.feature.settings.PlaybackSettingsState
import com.vivicast.tv.feature.settings.PlaybackSubtitleLanguage
import com.vivicast.tv.feature.settings.SettingsAccentColor
import com.vivicast.tv.feature.settings.SettingsAnimationSpeed
import com.vivicast.tv.feature.settings.SettingsFontScale
import com.vivicast.tv.feature.settings.SettingsLanguage
import com.vivicast.tv.feature.settings.SettingsThemeMode
import com.vivicast.tv.feature.settings.SettingsTransparency
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
import com.vivicast.tv.worker.isRefreshDue
import com.vivicast.tv.worker.refreshDelayMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var deepLinkData by mutableStateOf<Uri?>(null)
    private lateinit var appContainer: AppContainer

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkData = intent?.data
        appContainer = (application as VivicastApplication).appContainer
        setContent {
            // Appearance is applied at the composition root (App layer, never the ViewModel). Font scale
            // overrides LocalDensity's fontScale — scaling all sp text app-wide (incl. player + dialogs,
            // which inherit the local) without touching any VivicastTypography call site, and without an
            // Activity recreate (it recomposes live). dp/layout/focus geometry stay fixed.
            val preferences by appContainer.userPreferencesStore.values.collectAsState(initial = UserPreferences())
            val baseDensity = LocalDensity.current
            val colorScheme = vivicastColorScheme(
                background = preferences.appearance.backgroundColor.toPaletteColor(),
                accent = preferences.appearance.accentColor.toPaletteColor(),
            )
            VivicastTheme(scheme = colorScheme) {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = baseDensity.fontScale * preferences.appearance.fontScale.toFontScaleFactor(),
                    ),
                    LocalSurfaceOpacity provides preferences.appearance.transparency.toSurfaceOpacity(),
                    // Carried to dialogs/popups (separate windows) which re-provide LocalDensity and thus
                    // lose the root fontScale override; VivicastDialog re-applies this factor.
                    LocalFontScale provides preferences.appearance.fontScale.toFontScaleFactor(),
                ) {
                    VivicastApp(
                        appContainer = appContainer,
                        deepLinkData = deepLinkData,
                        onDeepLinkConsumed = { deepLinkData = null },
                    )
                }
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
    var livePlaybackChannels by remember { mutableStateOf(emptyList<Channel>()) }
    var liveTvSearchTarget by remember { mutableStateOf<LiveTvSearchTarget?>(null) }
    var movieSearchTarget by remember { mutableStateOf<Movie?>(null) }
    var seriesSearchTarget by remember { mutableStateOf<SeriesTarget?>(null) }
    var pendingExternalPlaybackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var pendingProtectionUnlock by remember { mutableStateOf<PendingProtectionUnlock?>(null) }
    var pendingStandardRestore by remember { mutableStateOf<PendingStandardRestore?>(null) }
    // True while a restore runs — keeps the confirm dialog open with a button spinner.
    var restoreInProgress by remember { mutableStateOf(false) }
    var showParentalReactivationHint by remember { mutableStateOf(false) }
    var pendingSystemTargetUnavailable by remember { mutableStateOf<SystemTargetUnavailable?>(null) }
    // Set to (title, location) after a successful backup or diagnostics export; shown in a dialog.
    var savedFileInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Import picks the file first; the encrypted container bytes are held here until the user supplies
    // the passphrase in the App-hoisted BackupImportPassphraseDialog.
    var pendingImportContainerBytes by remember { mutableStateOf<ByteArray?>(null) }
    var unlockedProtectionAreas by remember { mutableStateOf(emptySet<ParentalProtectionArea>()) }
    var topNavigationFocused by remember { mutableStateOf(false) }
    var lastTopNavigationBackAt by remember { mutableStateOf(0L) }
    var regularStartApplied by remember { mutableStateOf(false) }
    var explicitSystemTargetSeen by remember { mutableStateOf(deepLinkData != null) }
    val topNavigationFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val activity = context as? Activity
    // Consumed once per Activity instance: recreate() (from a language change) reuses the same Intent,
    // so this is true right after the recreate and false on every normal launch.
    var reopenLanguageSettings by remember {
        mutableStateOf(
            (activity?.intent?.getBooleanExtra(EXTRA_REOPEN_LANGUAGE_SETTINGS, false) == true).also { flagged ->
                if (flagged) activity?.intent?.removeExtra(EXTRA_REOPEN_LANGUAGE_SETTINGS)
            },
        )
    }
    val scope = rememberCoroutineScope()
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        Toast.makeText(context, context.getString(R.string.main_external_player_error), Toast.LENGTH_SHORT).show()
    }
    // Single in-app picker (SAF replacement). Set to open it; onPick fires with the chosen File.
    var filePickerRequest by remember { mutableStateOf<FilePickerRequest?>(null) }
    // Drives the "Diagnoseprotokoll exportieren" row spinner while the export runs (after folder pick).
    var diagnosticsExporting by remember { mutableStateOf(false) }
    var rescanningLogos by remember { mutableStateOf(false) }
    // Backup export: the folder chosen in the picker, held while the passphrase dialog is shown.
    var pendingBackupExportDir by remember { mutableStateOf<File?>(null) }
    // True while the backup is being written — keeps the passphrase dialog open with a button spinner.
    var backupExporting by remember { mutableStateOf(false) }
    var loadedPreferences by remember { mutableStateOf<UserPreferences?>(null) }
    LaunchedEffect(appContainer) {
        appContainer.userPreferencesStore.values.collectLatest { loadedPreferences = it }
    }
    val preferences = loadedPreferences ?: UserPreferences()
    LaunchedEffect(
        loadedPreferences,
        preferences.diagnostics.diagnosticsLoggingEnabled,
    ) {
        if (loadedPreferences != null) {
            appContainer.diagnosticsStore.setConfig(
                enabled = preferences.diagnostics.diagnosticsLoggingEnabled,
            )
        }
    }
    // Rebuild the local-logo index on app-start (first loaded prefs) and whenever the folder pref changes.
    LaunchedEffect(loadedPreferences?.localLogoFolder) {
        val folder = loadedPreferences?.localLogoFolder?.let { File(it) }
        val count = appContainer.localLogoIndex.rebuild(folder)
        appContainer.diagnosticsStore.logLogoIndex(folder, count)
    }
    val playerState by appContainer.playerController.state.collectAsState()
    // Diagnostics: log playback errors (gated). ExoPlayer's message is generic — enrich with the recent
    // codec/audio error from logcat + a plain-language cause + the active format.
    LaunchedEffect(playerState.error?.playbackId, playerState.error?.retryCount) {
        val error = playerState.error ?: return@LaunchedEffect
        if (!appContainer.diagnosticsStore.isLoggingEnabled) return@LaunchedEffect
        val resolution = "${playerState.videoWidth}x${playerState.videoHeight}"
        val fps = playerState.videoFrameRate.toString()
        val audioCh = playerState.audioChannelCount.toString()
        withContext(Dispatchers.IO) {
            val codec = PlaybackDiagnostics.recentCodecError()
            val cause = codec?.let { PlaybackDiagnostics.causeHint(it) } ?: PlaybackDiagnostics.causeHint(error.message)
            val details = buildMap {
                put("message", error.message)
                put("retries", error.retryCount.toString())
                put("resolution", resolution)
                put("fps", fps)
                put("audioCh", audioCh)
                cause?.let { put("cause", it) }
                codec?.let { put("codec", it) }
            }
            appContainer.diagnosticsStore.log("player", "playback_error", details)
        }
    }
    // Diagnostics: rebuffering/stall — a live stream that hangs triggers a reconnect (often without a
    // hard error). Log the transition into reconnecting (gated).
    LaunchedEffect(playerState.isReconnecting) {
        if (playerState.isReconnecting) {
            appContainer.diagnosticsStore.log("player", "reconnecting")
        }
    }
    // Diagnostics: mid-stream rebuffering (STATE_BUFFERING while already Playing) — the common "stutter"
    // that isn't a hard error. Initial load buffers under status=Starting, so this only fires on a stall.
    LaunchedEffect(playerState.isBuffering) {
        if (playerState.isBuffering && playerState.status == PlaybackStatus.Playing) {
            appContainer.diagnosticsStore.log("player", "rebuffering")
        }
    }
    var pinSecurityState by remember { mutableStateOf(PinSecurityState()) }
    var pinSecurityLoaded by remember { mutableStateOf(false) }
    val automaticProgressSaveTimes = remember { mutableMapOf<String, Long>() }
    val lifecycleOwner = LocalLifecycleOwner.current
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

    fun runStandardRestore(restore: PendingStandardRestore) {
        scope.launch {
            restoreInProgress = true
            val result = try {
                if (restore.encryptedFull) {
                    appContainer.standardBackupRestorer.restoreFullPayload(jsonText = restore.jsonText)
                } else {
                    appContainer.standardBackupRestorer.restore(jsonText = restore.jsonText)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Storage / database / keystore IO failure mid-restore — surface it instead of letting the
                // coroutine crash with the confirm dialog stuck on its spinner.
                appContainer.diagnosticsStore.log("backup", "restore_failed", mapOf("reason" to (error.message ?: "io_error")))
                restoreInProgress = false
                pendingStandardRestore = null
                Toast.makeText(context, context.getString(R.string.main_backup_restore_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            when (result) {
                is StandardBackupRestoreValidation.Valid -> {
                    restoreInProgress = false
                    pendingStandardRestore = null
                    appContainer.diagnosticsStore.log("backup", "restore_ok")
                    pinSecurityState = PinSecurityState()
                    unlockedProtectionAreas = emptySet()
                    // Restored providers/EPG are config only; re-fetch so the catalog + EPG rebuild and the
                    // pending favorites/history/progress bind.
                    appContainer.providerRepository.observeProviders().first()
                        .filter { it.isActive }
                        .forEach { appContainer.refreshWorkScheduler.enqueuePlaylistRefresh(it.id) }
                    appContainer.epgSourceRepository.observeEpgSources().first()
                        .filter { it.isActive }
                        .forEach { appContainer.refreshWorkScheduler.enqueueEpgRefresh(it.id) }
                    appContainer.syncWatchNext()
                    if (restore.preview.parentalProtectionWasActive) {
                        showParentalReactivationHint = true
                    }
                    Toast.makeText(context, context.getString(R.string.main_backup_restored), Toast.LENGTH_SHORT).show()
                }
                is StandardBackupRestoreValidation.Invalid -> {
                    restoreInProgress = false
                    pendingStandardRestore = null
                    appContainer.diagnosticsStore.log("backup", "restore_failed", mapOf("reason" to result.message))
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun selectRoute(route: String) {
        if (!pinSecurityLoaded && route.canBeProtected()) return
        requestProtectionUnlock(pinSecurityState.protectionAreaForRoute(route), route.protectionTitle(context)) {
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
            // Set an explicit media MIME so players that filter by type (not just ACTION_VIEW) match too.
            val mimeType = when {
                request.streamUrl.contains(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
                request.streamUrl.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
                request.streamUrl.contains(".ts", ignoreCase = true) -> "video/mp2t"
                request.streamUrl.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
                else -> "video/*"
            }
            externalPlayerLauncher.launch(
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(request.streamUrl), mimeType),
            )
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.main_no_external_player), Toast.LENGTH_SHORT).show()
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
        requestProtectionUnlock(pinSecurityState.protectionAreaForMovie(movie), context.getString(R.string.main_unlock_movies)) {
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
        requestProtectionUnlock(pinSecurityState.protectionAreaForSeries(series), context.getString(R.string.main_unlock_series)) {
            seriesSearchTarget = series.toSeriesTarget()
            selectRoute("series")
        }
    }

    fun openEpisodeTarget(episode: Episode) {
        requestProtectionUnlock(pinSecurityState.protectionAreaForEpisode(episode), context.getString(R.string.main_unlock_generic)) {
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
        requestProtectionUnlock(pinSecurityState.protectionAreaForEpisode(episode), context.getString(R.string.main_unlock_generic)) {
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
                        requestProtectionUnlock(pinSecurityState.protectionAreaForMovie(movie), context.getString(R.string.main_unlock_movies)) {
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
                title = context.getString(R.string.main_content_unavailable_title),
                body = context.getString(R.string.main_content_unavailable_body),
            )
        }
        onDeepLinkConsumed()
    }

    LaunchedEffect(loadedPreferences, pinSecurityLoaded, explicitSystemTargetSeen) {
        val currentPreferences = loadedPreferences ?: return@LaunchedEffect
        if (regularStartApplied || explicitSystemTargetSeen || !pinSecurityLoaded) return@LaunchedEffect
        regularStartApplied = true
        if (reopenLanguageSettings) {
            // Language just changed: go back into Settings (protection still enforced by selectRoute)
            // instead of the default Home start. SettingsRoute focuses the language row on entry.
            selectRoute(ROUTE_SETTINGS)
        } else {
            selectRoute(ROUTE_HOME)
            // "Resume last watched channel on startup": open the last channel over Home when the option
            // is on, history is on, and it still resolves + its endpoint probes reachable (dead/removed
            // → null → stay on Home, no error flash). The reachability probe runs off the main thread;
            // Home is already visible meanwhile. Refresh keeps running in parallel — playback needs no
            // fresh catalog.
            val resumeChannel = if (
                currentPreferences.general.resumeLastChannelOnStart && currentPreferences.history.enabled
            ) {
                appContainer.resolveResumableLastChannel()
            } else {
                null
            }
            if (resumeChannel != null) {
                openChannel(resumeChannel)
            } else {
                topNavigationFocusRequester.requestFocus()
            }
        }
    }

    // Startup reconciler: the global logos+cache maintenance periodic follows the "Allow background
    // refresh" master switch, and — once per cold start, regardless of the switch — the "refresh on app
    // start" one-shots run (opening the app is a foreground action). Playlist/EPG interval refresh is
    // handled foreground vs background by the lifecycle effect below, not here.
    LaunchedEffect(loadedPreferences) {
        val currentPreferences = loadedPreferences ?: return@LaunchedEffect
        val master = currentPreferences.general.backgroundRefreshEnabled
        val scheduler = appContainer.refreshWorkScheduler
        scheduler.setMaintenancePeriodicEnabled(enabled = master)
        // Cold start only: appStartRefreshTriggered lives on the process-scoped AppContainer, so an
        // Activity recreate (e.g. language change) does not re-trigger it — only a real relaunch does.
        if (!appContainer.appStartRefreshTriggered) {
            appContainer.appStartRefreshTriggered = true
            // Clear any "refreshing" state a cancelled/killed refresh left stuck (else the badge lingers).
            appContainer.recoverStuckRefreshState()
            // "Refresh on app start" one-shots (cold start only, switch-independent). Interval-based
            // refresh is driven separately by the foreground loop / background periodics off each item's
            // persisted lastRefreshAt — so this does NOT touch items whose interval simply elapsed.
            val startProviders = appContainer.providerRepository.observeProviders().first()
                .filter { it.isActive && it.refreshOnAppStartEnabled }
            startProviders.forEach { scheduler.enqueuePlaylistRefresh(it.id) }
            if (currentPreferences.epg.refreshOnAppStartEnabled) {
                // Sources linked to a starting playlist will be refreshed by "refresh EPG on playlist
                // change" (correctly, after the channels update) — skip them here to avoid a double
                // refresh against stale channels. If that trigger is off, app-start must cover them.
                val coveredByPlaylistChange = if (currentPreferences.epg.refreshOnPlaylistChangeEnabled) {
                    startProviders.flatMap { provider ->
                        appContainer.epgSourceRepository.observeProviderEpgSources(provider.id).first().map { it.epgSourceId }
                    }.toSet()
                } else {
                    emptySet()
                }
                appContainer.epgSourceRepository.observeEpgSources().first()
                    .filter { it.isActive && it.id !in coveredByPlaylistChange }
                    .forEach { scheduler.enqueueEpgRefresh(it.id) }
            }
        }
    }

    // Foreground vs background refresh (C1). Per-item periodic workers are a pure background construct:
    // cancelled while the app is in the foreground, (re)enqueued when it leaves — and only then subject to
    // the master switch. While in the foreground an in-app loop drives interval refreshes directly,
    // ignoring the switch, so the foreground always refreshes on each item's own interval.
    // Keyed on the (stable) lifecycle owner only, so a preference change never restarts it (which would
    // briefly re-enqueue then re-cancel the periodics while still in the foreground). The master switch is
    // read fresh when leaving the foreground.
    LaunchedEffect(lifecycleOwner) {
        val scheduler = appContainer.refreshWorkScheduler
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Entering foreground: background periodics must not run here — the loop below owns intervals.
            appContainer.providerRepository.observeProviders().first().forEach { scheduler.cancelPlaylistPeriodic(it.id) }
            appContainer.epgSourceRepository.observeEpgSources().first().forEach { scheduler.cancelEpgPeriodic(it.id) }
            try {
                while (true) {
                    // Due-check reads each item's PERSISTED lastRefreshAt (written by the worker on success),
                    // so a recently-refreshed item is not re-refreshed just because a new session started
                    // (respects "refresh on app start = off"), and a background refresh is visible here too.
                    val now = System.currentTimeMillis()
                    appContainer.providerRepository.observeProviders().first()
                        .filter { it.isActive }
                        .forEach { provider ->
                            if (isRefreshDue(now, provider.lastRefreshAt ?: 0L, provider.refreshIntervalHours)) {
                                scheduler.enqueuePlaylistRefresh(provider.id)
                            }
                        }
                    // Global EPG interval applies to every source; each source keeps its own lastRefreshAt phase.
                    val epgIntervalHours = appContainer.userPreferencesStore.values.first().epg.refreshIntervalHours
                    appContainer.epgSourceRepository.observeEpgSources().first()
                        .filter { it.isActive }
                        .forEach { source ->
                            if (isRefreshDue(now, source.lastRefreshAt ?: 0L, epgIntervalHours)) {
                                scheduler.enqueueEpgRefresh(source.id)
                            }
                        }
                    delay(FOREGROUND_REFRESH_CHECK_INTERVAL_MS)
                }
            } finally {
                // Leaving foreground: hand intervals back to WorkManager periodics, gated by the switch
                // (read fresh here so a mid-session toggle is honoured). The first run is phased to the
                // remaining time since lastRefreshAt, so opening the app doesn't reset the countdown.
                withContext(NonCancellable) {
                    val preferences = appContainer.userPreferencesStore.values.first()
                    val master = preferences.general.backgroundRefreshEnabled
                    val epgIntervalHours = preferences.epg.refreshIntervalHours
                    val now = System.currentTimeMillis()
                    val providers = appContainer.providerRepository.observeProviders().first()
                    val epgSources = appContainer.epgSourceRepository.observeEpgSources().first()
                    providers.forEach { provider ->
                        if (master && provider.isActive && provider.refreshIntervalHours > 0) {
                            val delayMs = refreshDelayMillis(now, provider.lastRefreshAt ?: 0L, provider.refreshIntervalHours)
                            scheduler.enqueuePlaylistPeriodic(provider.id, provider.refreshIntervalHours, delayMs)
                        } else {
                            scheduler.cancelPlaylistPeriodic(provider.id)
                        }
                    }
                    epgSources.forEach { source ->
                        if (master && source.isActive && epgIntervalHours > 0) {
                            val delayMs = refreshDelayMillis(now, source.lastRefreshAt ?: 0L, epgIntervalHours)
                            scheduler.enqueueEpgPeriodic(source.id, epgIntervalHours, delayMs)
                        } else {
                            scheduler.cancelEpgPeriodic(source.id)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(preferences.general.globalUserAgent) {
        appContainer.updateGlobalUserAgent(preferences.general.globalUserAgent)
    }

    // Feed the player build-time tuning (buffer/decoder/passthrough/back-buffer). The engine reads this
    // snapshot at the next start() and rebuilds the ExoPlayer only when it changed — "applies at next stream".
    LaunchedEffect(preferences.playback) {
        appContainer.updatePlaybackTuning(preferences.playback.toPlaybackTuning())
    }

    LaunchedEffect(selectedRoute) {
        lastTopNavigationBackAt = 0L
    }

    LaunchedEffect(appContainer) {
        pinSecurityState = appContainer.pinSecurityStateStore.read()
        pinSecurityLoaded = true
    }

    // Android-TV system-search protection is enforced at read time (AndroidTvSearchSuggestionProvider
    // reads the live PIN flags, fail-closed), so no protection-triggered index rebuild is needed here.

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

    val strMovies = stringResource(R.string.nav_movies_label)
    val strSeries = stringResource(R.string.nav_series_label)
    val strSearch = stringResource(R.string.nav_search_label)
    val strSettings = stringResource(R.string.nav_settings_label)

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
        AppDestination(strMovies, "movies") {
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
        AppDestination(strSeries, "series") {
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
        AppDestination(strSearch, "search") {
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
        AppDestination(strSettings, "settings") {
            SettingsRoute(
                providerRepository = appContainer.providerRepository,
                epgSourceRepository = appContainer.epgSourceRepository,
                userPreferencesStore = appContainer.userPreferencesStore,
                mediaCacheStore = appContainer.mediaCacheStore,
                parentalControlSettingsState = ParentalControlSettingsState(
                    hasPin = pinSecurityState.hasPin,
                    lockedUntilMillis = pinSecurityState.lockedUntilMillis,
                    protectSettings = pinSecurityState.protectSettings,
                    protectMovies = pinSecurityState.protectMovies,
                    protectSeries = pinSecurityState.protectSeries,
                    protectAdultContent = pinSecurityState.protectAdultContent,
                ),
                aboutAppState = context.aboutAppState(),
                localLogoFolder = preferences.localLogoFolder,
                onPickLogoFolder = {
                    filePickerRequest = FilePickerRequest(
                        title = context.getString(R.string.settings_logos_folder),
                        mode = FilePickerMode.FOLDER,
                        startDir = preferences.localLogoFolder?.let { File(it) },
                        onPick = { folder ->
                            // Persisting the path triggers the rebuild via the localLogoFolder LaunchedEffect.
                            scope.launch { appContainer.userPreferencesStore.updateLocalLogoFolder(folder.absolutePath) }
                        },
                    )
                },
                onRescanLogos = {
                    // Same folder → the pref doesn't change, so rescan explicitly (for files added/removed live).
                    rescanningLogos = true
                    scope.launch {
                        try {
                            val folder = preferences.localLogoFolder?.let { File(it) }
                            val count = appContainer.localLogoIndex.rebuild(folder)
                            appContainer.diagnosticsStore.logLogoIndex(folder, count)
                            Toast.makeText(context, context.getString(R.string.main_logos_rescanned), Toast.LENGTH_SHORT).show()
                        } finally {
                            rescanningLogos = false
                        }
                    }
                },
                rescanningLogos = rescanningLogos,
                onRemoveLogoFolder = {
                    // Clearing the pref triggers the localLogoFolder LaunchedEffect, which empties the index.
                    scope.launch {
                        appContainer.userPreferencesStore.updateLocalLogoFolder(null)
                        Toast.makeText(context, context.getString(R.string.main_logos_folder_removed), Toast.LENGTH_SHORT).show()
                    }
                },
                topNavFocusRequester = topNavigationFocusRequester,
                focusLanguageRowOnEnter = reopenLanguageSettings,
                onInitialLanguageFocusApplied = { reopenLanguageSettings = false },
                onTestProviderConnection = { request ->
                    appContainer.testProviderConnection(request).also { result ->
                        result.errorMessage?.let {
                            appContainer.diagnosticsStore.log("connection", "provider_test_failed", mapOf("error" to it))
                        }
                    }
                },
                onTestEpgConnection = { url ->
                    appContainer.testEpgSourceConnection(url).also { result ->
                        result.errorMessage?.let {
                            appContainer.diagnosticsStore.log("connection", "epg_test_failed", mapOf("error" to it))
                        }
                    }
                },
                onPickM3uFile = { onContent: (String, String) -> Unit ->
                    filePickerRequest = FilePickerRequest(
                        title = context.getString(R.string.main_pick_m3u_title),
                        mode = FilePickerMode.FILE,
                        extensions = setOf("m3u", "m3u8"),
                        onPick = { file ->
                            scope.launch {
                                val text = withContext(Dispatchers.IO) {
                                    runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                                }
                                when {
                                    text.isNullOrBlank() ->
                                        Toast.makeText(context, context.getString(R.string.main_m3u_file_invalid), Toast.LENGTH_SHORT).show()
                                    text.length > MAX_M3U_INLINE_SOURCE_CHARS ->
                                        Toast.makeText(context, context.getString(R.string.main_m3u_file_too_large), Toast.LENGTH_SHORT).show()
                                    // Cheap structure sanity check on pick; full classification runs later on "Check file".
                                    !text.contains("#EXTINF", ignoreCase = true) ->
                                        Toast.makeText(context, context.getString(R.string.main_m3u_file_invalid), Toast.LENGTH_SHORT).show()
                                    else -> {
                                        onContent(file.name, text)
                                        Toast.makeText(context, context.getString(R.string.main_m3u_file_imported), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                },
                onProviderSaved = { providerId ->
                    // Saving is a foreground action: refresh once now (stamps lastRefreshAt on success, so
                    // the interval clock restarts from the save). The background periodic — with the
                    // possibly just-changed interval — is (re)applied when the app next goes to background.
                    appContainer.refreshWorkScheduler.enqueuePlaylistRefresh(providerId)
                },
                onLogProviderSaved = { descriptor, switchedFrom ->
                    appContainer.diagnosticsStore.log(
                        "provider",
                        "saved",
                        buildMap {
                            put("source", descriptor)
                            switchedFrom?.let { put("switchedFrom", it) }
                        },
                    )
                },
                onBackgroundRefreshChanged = { enabled ->
                    // Preference write moved to SettingsViewModel (P1-04f1); only the scheduler side effect stays here.
                    scope.launch {
                        appContainer.refreshWorkScheduler.setMaintenancePeriodicEnabled(enabled)
                    }
                },
                onLanguageChanged = { language ->
                    // Preference write moved to SettingsViewModel (P1-04f1); only Locale/recreate stay here.
                    LocaleHelper.save(context, language.toLocaleKey())
                    // Flag the reused Intent so the post-recreate start lands back on Settings (language row).
                    activity?.intent?.putExtra(EXTRA_REOPEN_LANGUAGE_SETTINGS, true)
                    activity?.recreate()
                },
                onDiagnosticsSettingsChanged = { diagnostics ->
                    // Preference write moved to SettingsViewModel (P1-04f2a); only the DiagnosticsStore
                    // system effect stays here.
                    scope.launch {
                        appContainer.diagnosticsStore.setConfig(
                            enabled = diagnostics.diagnosticsLoggingEnabled,
                        )
                    }
                },
                onExportDiagnostics = {
                    // Let the user pick a target folder (opens at the last one used for diagnostics).
                    filePickerRequest = FilePickerRequest(
                        title = context.getString(R.string.main_pick_export_folder_title),
                        mode = FilePickerMode.FOLDER,
                        startDir = preferences.diagnostics.lastExportDir?.let { File(it) },
                        onPick = { folder ->
                            scope.launch {
                                diagnosticsExporting = true
                                try {
                                    val location = runCatching {
                                        val about = context.diagnosticsAbout()
                                        val settings = JSONObject(appContainer.standardBackupExporter.exportSupportSettingsJson(indentSpaces = 0))
                                        val name = timestampedBackupName(context.aboutAppState().appVersion, "vivicast-diagnostics", "zip")
                                        withContext(Dispatchers.IO) {
                                            writeToUserFolderOrFallback(context, folder, "Diagnostics", name, "application/zip") { output ->
                                                appContainer.diagnosticsStore.exportZip(output = output, about = about, settings = settings)
                                            }
                                        }
                                    }.getOrNull()
                                    if (location != null) {
                                        appContainer.userPreferencesStore.updateDiagnostics(
                                            preferences.diagnostics.copy(lastExportDir = folder.absolutePath),
                                        )
                                        savedFileInfo = "Diagnose exportiert" to location
                                    } else {
                                        Toast.makeText(context, "Diagnose-Export fehlgeschlagen.", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    diagnosticsExporting = false
                                }
                            }
                        },
                    )
                },
                diagnosticsExporting = diagnosticsExporting,
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
                            context.getString(R.string.main_pin_locked)
                        }
                        is PinVerificationResult.MissingPin -> context.getString(R.string.main_pin_missing)
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
                            context.getString(R.string.main_pin_locked)
                        }
                        is PinVerificationResult.MissingPin -> context.getString(R.string.main_pin_missing)
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
                onExportBackup = {
                    requestProtectionUnlock(
                        area = ParentalProtectionArea.Settings.takeIf { pinSecurityState.protectSettings },
                        title = "Vollbackup exportieren",
                        forcePrompt = true,
                    ) {
                        // Pick the target folder first (opens at the last one used); the passphrase is asked after.
                        filePickerRequest = FilePickerRequest(
                            title = context.getString(R.string.main_pick_export_folder_title),
                            mode = FilePickerMode.FOLDER,
                            startDir = preferences.backup.lastExportDir?.let { File(it) },
                            onPick = { folder -> pendingBackupExportDir = folder },
                        )
                    }
                },
                onImportBackup = {
                    requestProtectionUnlock(
                        area = ParentalProtectionArea.Settings.takeIf { pinSecurityState.protectSettings },
                        title = "Vollbackup wiederherstellen",
                        forcePrompt = true,
                    ) {
                        // Pick the file first; the passphrase is asked afterwards in BackupImportPassphraseDialog.
                        filePickerRequest = FilePickerRequest(
                            title = context.getString(R.string.main_pick_backup_title),
                            mode = FilePickerMode.FILE,
                            extensions = setOf("vcbak"),
                            onPick = { file ->
                                scope.launch {
                                    val bytes = withContext(Dispatchers.IO) {
                                        runCatching { file.readBytes() }.getOrNull()
                                    }
                                    when {
                                        bytes == null || bytes.isEmpty() ->
                                            Toast.makeText(context, context.getString(R.string.main_backup_file_invalid), Toast.LENGTH_SHORT).show()
                                        // Reject a support/settings export (or any non-encrypted file) before asking for a passphrase.
                                        !isEncryptedBackupContainer(bytes) ->
                                            Toast.makeText(context, context.getString(R.string.main_backup_not_restorable), Toast.LENGTH_SHORT).show()
                                        else -> pendingImportContainerBytes = bytes
                                    }
                                }
                            },
                        )
                    }
                },
                onRefreshEpgSource = { sourceId ->
                    // Per-source EPG refresh (like per-provider playlist refresh). Stamps lastRefreshAt on
                    // success, so the interval clock restarts and the loop won't immediately re-refresh.
                    appContainer.refreshWorkScheduler.enqueueEpgRefresh(sourceId)
                },
                onClearHistory = { targets ->
                    // Suspends until every category is cleared so the confirm dialog spinner covers the work.
                    targets.forEach { appContainer.clearHistory(it) }
                },
                imageCacheSizeBytes = { appContainer.imageLoader.diskCache?.size ?: 0L },
                clearImageCache = {
                    appContainer.imageLoader.diskCache?.clear()
                    appContainer.imageLoader.memoryCache?.clear()
                    // Part of the user-initiated cache clear (mediaCacheStore.clear runs in the VM just before).
                    appContainer.diagnosticsStore.log("cache", "cleared")
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

        if (!preferences.general.doubleBackToExit) {
            activity?.finish()
            return@BackHandler
        }

        val now = System.currentTimeMillis()
        if (now - lastTopNavigationBackAt <= EXIT_CONFIRMATION_WINDOW_MILLIS) {
            activity?.finish()
            return@BackHandler
        }

        lastTopNavigationBackAt = now
        Toast.makeText(context, context.getString(R.string.main_exit_confirmation), Toast.LENGTH_SHORT).show()
    }

    VivicastScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VivicastSpacing.ScreenHorizontal, vertical = VivicastSpacing.ScreenVertical),
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
        ) {
            // Language-change handoff: selectedRoute is still Home until the start effect routes to
            // Settings (through the protection gate). Render nothing focusable for that frame so neither
            // Home content nor the Home nav item flashes/steals focus before Settings appears.
            if (!(reopenLanguageSettings && !regularStartApplied)) {
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
            afrEnabled = preferences.playback.afrEnabled,
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
                        context.getString(R.string.main_pin_locked)
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

    filePickerRequest?.let { request ->
        FilePickerDialog(
            title = request.title,
            mode = request.mode,
            startDir = request.startDir,
            fileExtensions = request.extensions,
            onDismiss = { filePickerRequest = null },
            onPick = { file ->
                filePickerRequest = null
                request.onPick(file)
            },
        )
    }

    // Backup export step 2: after the folder is chosen, ask for the passphrase, then write into it.
    // The dialog stays open with a button spinner while the backup is written in the background.
    pendingBackupExportDir?.let { folder ->
        BackupExportPassphraseDialog(
            exporting = backupExporting,
            onDismiss = { if (!backupExporting) pendingBackupExportDir = null },
            onSubmit = { passphrase ->
                backupExporting = true
                val secret = passphrase.toCharArray()
                scope.launch {
                    val location = runCatching {
                        val bytes = appContainer.standardBackupExporter.exportBackup(passphrase = secret)
                        val name = timestampedBackupName(context.aboutAppState().appVersion, "vivicast-backup", "vcbak")
                        withContext(Dispatchers.IO) {
                            writeToUserFolderOrFallback(context, folder, "Backups", name, "application/octet-stream") { it.write(bytes) }
                        }
                    }.getOrNull()
                    secret.fill(' ')
                    appContainer.diagnosticsStore.log("backup", if (location != null) "export_ok" else "export_failed")
                    backupExporting = false
                    pendingBackupExportDir = null
                    if (location != null) {
                        appContainer.userPreferencesStore.updateBackup(
                            preferences.backup.copy(lastExportDir = folder.absolutePath),
                        )
                        savedFileInfo = "Backup gespeichert" to location
                    } else {
                        Toast.makeText(context, "Backup konnte nicht gespeichert werden.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    pendingImportContainerBytes?.let { container ->
        BackupImportPassphraseDialog(
            onDismiss = { pendingImportContainerBytes = null },
            onSubmit = { entered ->
                pendingImportContainerBytes = null
                val passphrase = entered.toCharArray()
                val payload = decryptBackupPayload(container, passphrase)
                passphrase.fill(' ')
                when {
                    payload == null -> {
                        appContainer.diagnosticsStore.log("backup", "passphrase_wrong")
                        Toast.makeText(context, context.getString(R.string.main_passphrase_incorrect), Toast.LENGTH_SHORT).show()
                    }
                    else -> when (val validation = validateFullBackupPayloadForRestore(payload)) {
                        is StandardBackupRestoreValidation.Valid ->
                            pendingStandardRestore = PendingStandardRestore(
                                jsonText = payload,
                                preview = validation.preview,
                                encryptedFull = true,
                            )
                        is StandardBackupRestoreValidation.Invalid ->
                            Toast.makeText(context, validation.message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    savedFileInfo?.let { (title, location) ->
        FileSavedDialog(title = title, location = location, onDismiss = { savedFileInfo = null })
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
            restoring = restoreInProgress,
            onDismiss = { if (!restoreInProgress) pendingStandardRestore = null },
            onConfirm = { runStandardRestore(restore) },
        )
    }


    if (showParentalReactivationHint) {
        ParentalReactivationHintDialog(onDismiss = { showParentalReactivationHint = false })
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

internal data class PendingStandardRestore(
    val jsonText: String,
    val preview: StandardBackupRestorePreview,
    val encryptedFull: Boolean = false,
)

internal data class SystemTargetUnavailable(
    val title: String,
    val body: String,
)

// SAF suggested backup file name in the TiviMate-style layout: <prefix>_<yyyyMMdd>_<HHmmss>_v<version>.<ext>
private fun timestampedBackupName(appVersion: String, prefix: String, extension: String): String {
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
    val safeVersion = appVersion.trim().takeIf { it.isNotBlank() } ?: "unknown"
    return "${prefix}_${timestamp}_v$safeVersion.$extension"
}

private const val DOWNLOAD_ROOT = "Vivicast"

// Writes a file into the public Downloads/Vivicast/<subdir> folder a file manager can browse to (TV file
// managers offer no write picker). [block] fills the output stream. Returns the human-readable location,
// or null on failure. Used for backups, diagnostics exports and crash logs.
// ponytail: API 29+ uses MediaStore (no permission); older TVs fall back to the app-specific external dir —
// add a WRITE_EXTERNAL_STORAGE path if public Downloads on pre-Q devices ever matters.
private fun writeToDownloads(
    context: Context,
    subdir: String,
    fileName: String,
    mimeType: String,
    block: (OutputStream) -> Unit,
): String? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_ROOT/$subdir"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
            resolver.openOutputStream(uri)?.use(block) ?: return@runCatching null
            "$relative/$fileName"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$DOWNLOAD_ROOT/$subdir")
                .apply { mkdirs() }
            File(dir, fileName).also { file -> file.outputStream().use(block) }.absolutePath
        }
    }.getOrNull()

// Writes into the user-picked [folder] via the File API; on failure (no permission / not writable)
// falls back to the fixed public Downloads/Vivicast target. Returns the location string or null.
private fun writeToUserFolderOrFallback(
    context: Context,
    folder: File,
    subdir: String,
    fileName: String,
    mimeType: String,
    block: (OutputStream) -> Unit,
): String? {
    val direct = runCatching {
        if (!folder.exists()) folder.mkdirs()
        File(folder, fileName).also { it.outputStream().use(block) }.absolutePath
    }.getOrNull()
    return direct ?: writeToDownloads(context, subdir, fileName, mimeType, block)
}

// The in-app picker's open request. onPick fires with the chosen file (FILE) or folder (FOLDER).
private data class FilePickerRequest(
    val title: String,
    val mode: FilePickerMode,
    val onPick: (File) -> Unit,
    val startDir: File? = null,
    val extensions: Set<String>? = null,
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

private fun String.protectionTitle(context: Context): String =
    when (this) {
        "settings" -> context.getString(R.string.main_unlock_settings)
        "movies" -> context.getString(R.string.main_unlock_movies)
        "series" -> context.getString(R.string.main_unlock_series)
        else -> context.getString(R.string.main_unlock_generic)
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


private fun Int.floorMod(modulus: Int): Int =
    ((this % modulus) + modulus) % modulus

// Diagnostics for a local-logo-folder scan: logs/index_built (files=N), or logos/folder_unreadable when the
// folder was set but isn't a readable directory (rebuild returns -1). Sanitized count only — never the path.
// A null folder (folder removed / never set) logs nothing, so removing the folder doesn't record files=0.
private fun DiagnosticsStore.logLogoIndex(folder: File?, count: Int) {
    if (folder == null) return
    if (count < 0) log("logos", "folder_unreadable")
    else log("logos", "index_built", mapOf("files" to count.toString()))
}

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
// One-shot flag carried across the language-change recreate() so we land back on Settings with the
// language row focused instead of the default Home start.
private const val EXTRA_REOPEN_LANGUAGE_SETTINGS = "com.vivicast.tv.REOPEN_LANGUAGE_SETTINGS"
internal const val PIN_LENGTH = 4
private const val EXIT_CONFIRMATION_WINDOW_MILLIS = 2_000L
// How often the in-app foreground loop checks whether any playlist / EPG source is due for a refresh.
private const val FOREGROUND_REFRESH_CHECK_INTERVAL_MS = 15L * 60L * 1000L
