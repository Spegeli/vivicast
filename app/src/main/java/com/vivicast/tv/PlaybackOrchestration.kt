package com.vivicast.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.cache.MediaCacheStats
import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheType
import com.vivicast.tv.core.database.VIVICAST_DATABASE_VERSION
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.AccentColor
import com.vivicast.tv.core.datastore.AnimationSpeedPreference
import com.vivicast.tv.core.datastore.BackupTargetPreference
import com.vivicast.tv.core.datastore.BufferSizePreference
import com.vivicast.tv.core.datastore.DecoderPreference
import com.vivicast.tv.core.datastore.ExternalPlayerPreference
import com.vivicast.tv.core.datastore.FontScalePreference
import com.vivicast.tv.core.datastore.LanguagePreference
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.TransparencyLevel
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.VivicastScreenBackground
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.core.designsystem.VivicastTopNavigation
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastTextField
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.core.security.PinSecurity
import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.core.security.PinVerificationResult
import com.vivicast.tv.backup.StandardBackupRestorePreview
import com.vivicast.tv.backup.StandardBackupRestoreValidation
import com.vivicast.tv.backup.validateFullBackupPayloadForRestore
import com.vivicast.tv.diagnostics.DiagnosticsAbout
import com.vivicast.tv.feature.settings.CacheSettingsState
import com.vivicast.tv.feature.settings.AboutAppState
import com.vivicast.tv.feature.settings.AppearanceSettingsState
import com.vivicast.tv.feature.settings.BackupSettingsState
import com.vivicast.tv.feature.settings.BackupTargetMode
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
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone

internal suspend fun AppContainer.resolveChannelLogoModel(channel: Channel): Any? {
    val logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() } ?: return null
    // Prefetched file if the refresh worker warmed it; otherwise the URL itself, which Coil now loads
    // directly (see AppContainer.imageLoader). Same key for both so a later prefetch stays consistent.
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = MediaCacheType.ChannelLogo,
            ownerId = channel.id,
            sourceUrl = logoUrl,
        ),
    )?.file ?: logoUrl
}

internal suspend fun AppContainer.resolveMovieImageModel(movie: Movie, type: MediaCacheType): Any? {
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
    )?.file ?: sourceUrl
}

internal suspend fun AppContainer.resolveSeriesImageModel(series: Series, type: MediaCacheType): Any? {
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
    )?.file ?: sourceUrl
}

internal suspend fun AppContainer.resolveEpisodeImageModel(episode: Episode): Any? {
    val sourceUrl = episode.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return null
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = MediaCacheType.EpisodeImage,
            ownerId = episode.id,
            sourceUrl = sourceUrl,
        ),
    )?.file ?: sourceUrl
}

internal suspend fun AppContainer.openChannelPlayback(
    channel: Channel,
    origin: PlaybackOrigin,
    onStarted: () -> Unit,
) {
    val request = playbackRequestFactory.channelRequest(
        channel = channel,
        origin = origin,
    ) ?: return
    playerController.play(request)
    onStarted()
}

/**
 * Resolves the last watched channel for the "resume on startup" option: the most recent history row
 * → its still-persisted [Channel] → a preflight liveness check on the resolved stream. Returns the
 * channel only when it still exists and its endpoint is reachable; otherwise null so the caller lands
 * on Home instead of opening the player on a dead stream. Refresh is not awaited — the stream URL
 * resolves straight from Room.
 */
internal suspend fun AppContainer.resolveResumableLastChannel(): Channel? {
    val lastWatched = playbackRepository.observeAllRecentChannels(limit = 1).first().firstOrNull() ?: return null
    val channel = mediaRepository.getChannel(lastWatched.providerId, lastWatched.channelId) ?: return null
    val request = playbackRequestFactory.channelRequest(
        channel = channel,
        origin = PlaybackOrigin.LiveTv,
    ) ?: return null
    return channel.takeIf { streamReachabilityProbe.isReachable(request.streamUrl, request.userAgent) }
}

internal suspend fun AppContainer.openMoviePlayback(
    movie: Movie,
    resumeProgress: Boolean,
    origin: PlaybackOrigin,
    onStarted: () -> Unit,
) {
    val request = createMoviePlaybackRequest(movie, resumeProgress, origin) ?: return
    playerController.play(request)
    onStarted()
}

internal suspend fun AppContainer.createMoviePlaybackRequest(
    movie: Movie,
    resumeProgress: Boolean,
    origin: PlaybackOrigin,
): PlaybackRequest? = playbackRequestFactory.movieRequest(movie, resumeProgress, origin)

internal suspend fun AppContainer.openEpisodePlayback(
    episode: Episode,
    origin: PlaybackOrigin,
    onStarted: () -> Unit,
) {
    val request = createEpisodePlaybackRequest(episode, origin) ?: return
    playerController.play(request)
    onStarted()
}

internal suspend fun AppContainer.createEpisodePlaybackRequest(
    episode: Episode,
    origin: PlaybackOrigin,
): PlaybackRequest? = playbackRequestFactory.episodeRequest(episode, origin)

internal suspend fun AppContainer.openCatchUpPlayback(
    channel: Channel,
    program: EpgProgram,
    origin: PlaybackOrigin,
    onStarted: () -> Unit,
) {
    val request = playbackRequestFactory.catchUpRequest(channel, program, origin) ?: return
    playerController.play(request)
    onStarted()
}

internal suspend fun AppContainer.savePlaybackProgress(
    state: VivicastPlayerState,
    automaticProgressSaveTimes: MutableMap<String, Long>,
    forceSave: Boolean = false,
) = playbackProgressRecorder.record(state, automaticProgressSaveTimes, forceSave)

internal suspend fun AppContainer.clearHistory(target: HistoryClearTarget) {
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

