package com.vivicast.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.TransparencyLevel
import com.vivicast.tv.core.datastore.UserPreferences
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
import com.vivicast.tv.core.player.BufferTier
import com.vivicast.tv.core.player.DecoderMode
import com.vivicast.tv.core.player.PlaybackAudioOption
import com.vivicast.tv.core.player.PlaybackSubtitleOption
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackReturnTarget
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.PlaybackTuning
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.core.security.PinSecurity
import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.core.security.PinVerificationResult
import com.vivicast.tv.backup.StandardBackupRestorePreview
import com.vivicast.tv.backup.StandardBackupRestoreValidation
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

/**
 * Font-scale enum → LocalDensity fontScale multiplier (normative values from design-tokens.md:169-174).
 * Pure App-layer mapping (no Density/Context/Compose) so it stays unit-testable and out of the ViewModel;
 * the [androidx.compose.ui.unit.Density] itself is built at the composition root in MainActivity.
 */
internal fun FontScalePreference.toFontScaleFactor(): Float =
    when (this) {
        FontScalePreference.Small -> 0.90f
        FontScalePreference.Medium -> 1.00f
        FontScalePreference.Large -> 1.12f
        FontScalePreference.ExtraLarge -> 1.25f
    }

/**
 * Transparency enum → panel/overlay surface-opacity multiplier (normative: 0%→1.0, 25%→0.75, 50%→0.5).
 * Spec supports only 0/25/50; the stale `Percent75` datastore value is clamped to the 50% opacity.
 */
internal fun TransparencyLevel.toSurfaceOpacity(): Float =
    when (this) {
        TransparencyLevel.Percent0 -> 1.00f
        TransparencyLevel.Percent25 -> 0.75f
        TransparencyLevel.Percent50, TransparencyLevel.Percent75 -> 0.50f
    }

internal fun SettingsLanguage.toLocaleKey(): String =
    when (this) {
        SettingsLanguage.System -> "System"
        SettingsLanguage.German -> "German"
        SettingsLanguage.English -> "English"
    }

internal fun BackupTargetPreference.toSettingsBackupTargetMode(): BackupTargetMode =
    when (this) {
        BackupTargetPreference.LocalStorage -> BackupTargetMode.LocalStorage
        // v1 supports only local backup; SMB/Google Drive are reserved for post-v1. Coerce any older
        // persisted value to local so an unsupported target never surfaces (backwards-compatible read).
        BackupTargetPreference.Smb, BackupTargetPreference.GoogleDrive -> BackupTargetMode.LocalStorage
    }

internal fun BackupTargetMode.toDataStoreBackupTargetPreference(): BackupTargetPreference =
    when (this) {
        BackupTargetMode.LocalStorage -> BackupTargetPreference.LocalStorage
        BackupTargetMode.Smb -> BackupTargetPreference.Smb
        BackupTargetMode.GoogleDrive -> BackupTargetPreference.GoogleDrive
    }

internal fun Context.aboutAppState(): AboutAppState {
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
    )
}

internal fun Context.diagnosticsAbout(): DiagnosticsAbout =
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

/**
 * App-layer mapping of the persisted playback prefs onto the engine's [PlaybackTuning] build-time snapshot.
 * Preferred audio/subtitle language seeding is wired in Phase 2 (defaults here).
 */
internal fun PlaybackPreferences.toPlaybackTuning(): PlaybackTuning =
    PlaybackTuning(
        bufferSize = bufferSize.toBufferTier(),
        audioDecoder = audioDecoder.toDecoderMode(),
        videoDecoder = videoDecoder.toDecoderMode(),
        passthroughEnabled = audioPassthroughEnabled,
        preferredAudio = preferredAudioLanguage.toPlaybackAudioOption(),
        preferredSubtitle = preferredSubtitleLanguage.toPlaybackSubtitleOption(),
    )

// String keys mirror the feature-settings persistence (null = system/off).
private fun String?.toPlaybackAudioOption(): PlaybackAudioOption =
    when (this) {
        "de" -> PlaybackAudioOption.German
        "en" -> PlaybackAudioOption.English
        "original" -> PlaybackAudioOption.Original
        else -> PlaybackAudioOption.SystemDefault
    }

private fun String?.toPlaybackSubtitleOption(): PlaybackSubtitleOption =
    when (this) {
        "system" -> PlaybackSubtitleOption.SystemDefault
        "de" -> PlaybackSubtitleOption.German
        "en" -> PlaybackSubtitleOption.English
        else -> PlaybackSubtitleOption.Off
    }

private fun BufferSizePreference.toBufferTier(): BufferTier =
    when (this) {
        BufferSizePreference.Off -> BufferTier.Off
        BufferSizePreference.Small -> BufferTier.Small
        BufferSizePreference.Medium -> BufferTier.Medium
        BufferSizePreference.Large -> BufferTier.Large
        BufferSizePreference.ExtraLarge -> BufferTier.ExtraLarge
    }

private fun DecoderPreference.toDecoderMode(): DecoderMode =
    when (this) {
        DecoderPreference.Software -> DecoderMode.Software
        // Automatic is unreachable from the UI (spec only exposes HW/SW); treat it as Hardware.
        DecoderPreference.Hardware, DecoderPreference.Automatic -> DecoderMode.Hardware
    }

