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
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.TimeshiftStoragePreference
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
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.core.security.PinVerificationResult
import com.vivicast.tv.backup.StandardBackupRestorePreview
import com.vivicast.tv.backup.StandardBackupRestoreValidation
import com.vivicast.tv.backup.decryptFullBackupPayload
import com.vivicast.tv.backup.validateFullBackupPayloadForRestore
import com.vivicast.tv.backup.validateStandardBackupForRestore
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
import com.vivicast.tv.feature.settings.PlaybackTimeshiftStorageMode
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone

internal suspend fun AppContainer.resolveChannelLogoModel(channel: Channel): Any? {
    val logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() } ?: return null
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = MediaCacheType.ChannelLogo,
            ownerId = channel.id,
            sourceUrl = logoUrl,
        ),
    )?.file
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
    )?.file
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
    )?.file
}

internal suspend fun AppContainer.resolveEpisodeImageModel(episode: Episode): Any? {
    val sourceUrl = episode.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return null
    return mediaCacheStore.getEntry(
        MediaCacheKey(
            type = MediaCacheType.EpisodeImage,
            ownerId = episode.id,
            sourceUrl = sourceUrl,
        ),
    )?.file
}

internal suspend fun AppContainer.openChannelPlayback(
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

internal suspend fun AppContainer.openCatchUpPlayback(
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

internal suspend fun AppContainer.savePlaybackProgress(
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

@Suppress("DEPRECATION")
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

