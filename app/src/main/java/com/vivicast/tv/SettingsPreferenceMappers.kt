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

internal fun LanguagePreference.toSettingsLanguage(): SettingsLanguage =
    when (this) {
        LanguagePreference.System -> SettingsLanguage.System
        LanguagePreference.German -> SettingsLanguage.German
        LanguagePreference.English -> SettingsLanguage.English
    }

internal fun SettingsLanguage.toDataStoreLanguagePreference(): LanguagePreference =
    when (this) {
        SettingsLanguage.System -> LanguagePreference.System
        SettingsLanguage.German -> LanguagePreference.German
        SettingsLanguage.English -> LanguagePreference.English
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
        BackupTargetPreference.Smb -> BackupTargetMode.Smb
        BackupTargetPreference.GoogleDrive -> BackupTargetMode.GoogleDrive
    }

internal fun BackupTargetMode.toDataStoreBackupTargetPreference(): BackupTargetPreference =
    when (this) {
        BackupTargetMode.LocalStorage -> BackupTargetPreference.LocalStorage
        BackupTargetMode.Smb -> BackupTargetPreference.Smb
        BackupTargetMode.GoogleDrive -> BackupTargetPreference.GoogleDrive
    }

internal fun ThemeColor.toSettingsThemeMode(): SettingsThemeMode =
    when (this) {
        ThemeColor.Dark -> SettingsThemeMode.StandardDark
        ThemeColor.HighContrastDark -> SettingsThemeMode.HighContrastDark
        ThemeColor.AmoledDark -> SettingsThemeMode.AmoledDark
    }

internal fun SettingsThemeMode.toDataStoreThemeColor(): ThemeColor =
    when (this) {
        SettingsThemeMode.StandardDark -> ThemeColor.Dark
        SettingsThemeMode.HighContrastDark -> ThemeColor.HighContrastDark
        SettingsThemeMode.AmoledDark -> ThemeColor.AmoledDark
    }

internal fun AccentColor.toSettingsAccentColor(): SettingsAccentColor =
    when (this) {
        AccentColor.Blue -> SettingsAccentColor.Blue
    }

internal fun SettingsAccentColor.toDataStoreAccentColor(): AccentColor =
    when (this) {
        SettingsAccentColor.Blue -> AccentColor.Blue
    }

internal fun TransparencyLevel.toSettingsTransparency(): SettingsTransparency =
    when (this) {
        TransparencyLevel.Percent0 -> SettingsTransparency.Percent0
        TransparencyLevel.Percent25 -> SettingsTransparency.Percent25
        TransparencyLevel.Percent50,
        TransparencyLevel.Percent75 -> SettingsTransparency.Percent50
    }

internal fun SettingsTransparency.toDataStoreTransparencyLevel(): TransparencyLevel =
    when (this) {
        SettingsTransparency.Percent0 -> TransparencyLevel.Percent0
        SettingsTransparency.Percent25 -> TransparencyLevel.Percent25
        SettingsTransparency.Percent50 -> TransparencyLevel.Percent50
    }

internal fun FontScalePreference.toSettingsFontScale(): SettingsFontScale =
    when (this) {
        FontScalePreference.Small -> SettingsFontScale.Small
        FontScalePreference.Medium -> SettingsFontScale.Medium
        FontScalePreference.Large -> SettingsFontScale.Large
        FontScalePreference.ExtraLarge -> SettingsFontScale.ExtraLarge
    }

internal fun SettingsFontScale.toDataStoreFontScalePreference(): FontScalePreference =
    when (this) {
        SettingsFontScale.Small -> FontScalePreference.Small
        SettingsFontScale.Medium -> FontScalePreference.Medium
        SettingsFontScale.Large -> FontScalePreference.Large
        SettingsFontScale.ExtraLarge -> FontScalePreference.ExtraLarge
    }

internal fun AnimationSpeedPreference.toSettingsAnimationSpeed(): SettingsAnimationSpeed =
    when (this) {
        AnimationSpeedPreference.Off -> SettingsAnimationSpeed.Off
        AnimationSpeedPreference.Fast -> SettingsAnimationSpeed.Fast
        AnimationSpeedPreference.Normal -> SettingsAnimationSpeed.Normal
        AnimationSpeedPreference.Slow -> SettingsAnimationSpeed.Slow
    }

internal fun SettingsAnimationSpeed.toDataStoreAnimationSpeedPreference(): AnimationSpeedPreference =
    when (this) {
        SettingsAnimationSpeed.Off -> AnimationSpeedPreference.Off
        SettingsAnimationSpeed.Fast -> AnimationSpeedPreference.Fast
        SettingsAnimationSpeed.Normal -> AnimationSpeedPreference.Normal
        SettingsAnimationSpeed.Slow -> AnimationSpeedPreference.Slow
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

internal fun DiagnosticsPreferences.toSettingsDiagnosticsState(): DiagnosticsSettingsState =
    DiagnosticsSettingsState(
        diagnosticsLoggingEnabled = diagnosticsLoggingEnabled,
        retentionDays = retentionDays.coerceIn(1, 7),
    )

internal fun copySupportInformation(context: Context, supportInformation: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Vivicast Support-Informationen", supportInformation))
    Toast.makeText(context, context.getString(R.string.main_support_info_copied), Toast.LENGTH_SHORT).show()
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

internal fun BufferSizePreference.toSettingsBufferSizeMode(): PlaybackBufferSizeMode =
    when (this) {
        BufferSizePreference.Off -> PlaybackBufferSizeMode.Off
        BufferSizePreference.Small -> PlaybackBufferSizeMode.Small
        BufferSizePreference.Medium -> PlaybackBufferSizeMode.Medium
        BufferSizePreference.Large -> PlaybackBufferSizeMode.Large
        BufferSizePreference.ExtraLarge -> PlaybackBufferSizeMode.ExtraLarge
    }

internal fun PlaybackBufferSizeMode.toDataStoreBufferSizePreference(): BufferSizePreference =
    when (this) {
        PlaybackBufferSizeMode.Off -> BufferSizePreference.Off
        PlaybackBufferSizeMode.Small -> BufferSizePreference.Small
        PlaybackBufferSizeMode.Medium -> BufferSizePreference.Medium
        PlaybackBufferSizeMode.Large -> BufferSizePreference.Large
        PlaybackBufferSizeMode.ExtraLarge -> BufferSizePreference.ExtraLarge
    }

internal fun DecoderPreference.toSettingsDecoderMode(): PlaybackDecoderMode =
    when (this) {
        DecoderPreference.Automatic,
        DecoderPreference.Hardware -> PlaybackDecoderMode.Hardware
        DecoderPreference.Software -> PlaybackDecoderMode.Software
    }

internal fun PlaybackDecoderMode.toDataStoreDecoderPreference(): DecoderPreference =
    when (this) {
        PlaybackDecoderMode.Hardware -> DecoderPreference.Hardware
        PlaybackDecoderMode.Software -> DecoderPreference.Software
    }

internal fun String?.toSettingsAudioLanguage(): PlaybackAudioLanguage =
    when (this) {
        "de" -> PlaybackAudioLanguage.German
        "en" -> PlaybackAudioLanguage.English
        "original" -> PlaybackAudioLanguage.Original
        else -> PlaybackAudioLanguage.SystemDefault
    }

internal fun PlaybackAudioLanguage.toDataStoreAudioLanguage(): String? =
    when (this) {
        PlaybackAudioLanguage.SystemDefault -> null
        PlaybackAudioLanguage.German -> "de"
        PlaybackAudioLanguage.English -> "en"
        PlaybackAudioLanguage.Original -> "original"
    }

internal fun String?.toSettingsSubtitleLanguage(): PlaybackSubtitleLanguage =
    when (this) {
        "system" -> PlaybackSubtitleLanguage.SystemDefault
        "de" -> PlaybackSubtitleLanguage.German
        "en" -> PlaybackSubtitleLanguage.English
        else -> PlaybackSubtitleLanguage.Off
    }

internal fun PlaybackSubtitleLanguage.toDataStoreSubtitleLanguage(): String? =
    when (this) {
        PlaybackSubtitleLanguage.Off -> null
        PlaybackSubtitleLanguage.SystemDefault -> "system"
        PlaybackSubtitleLanguage.German -> "de"
        PlaybackSubtitleLanguage.English -> "en"
    }

internal fun ExternalPlayerPreference.toSettingsExternalPlayerMode(): PlaybackExternalPlayerMode =
    when (this) {
        ExternalPlayerPreference.Internal -> PlaybackExternalPlayerMode.Internal
        ExternalPlayerPreference.External -> PlaybackExternalPlayerMode.External
        ExternalPlayerPreference.AskEveryTime -> PlaybackExternalPlayerMode.AskEveryTime
    }

internal fun PlaybackExternalPlayerMode.toDataStoreExternalPlayerPreference(): ExternalPlayerPreference =
    when (this) {
        PlaybackExternalPlayerMode.Internal -> ExternalPlayerPreference.Internal
        PlaybackExternalPlayerMode.External -> ExternalPlayerPreference.External
        PlaybackExternalPlayerMode.AskEveryTime -> ExternalPlayerPreference.AskEveryTime
    }

internal fun PlaybackPreferences.timeshiftConfig(): PlaybackTimeshiftConfig? {
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

internal fun TimeshiftStoragePreference.toSettingsTimeshiftStorageMode(): PlaybackTimeshiftStorageMode =
    when (this) {
        TimeshiftStoragePreference.Automatic -> PlaybackTimeshiftStorageMode.Automatic
        TimeshiftStoragePreference.Ram -> PlaybackTimeshiftStorageMode.Ram
        TimeshiftStoragePreference.InternalStorage -> PlaybackTimeshiftStorageMode.InternalStorage
    }

internal fun PlaybackTimeshiftStorageMode.toDataStoreTimeshiftStoragePreference(): TimeshiftStoragePreference =
    when (this) {
        PlaybackTimeshiftStorageMode.Automatic -> TimeshiftStoragePreference.Automatic
        PlaybackTimeshiftStorageMode.Ram -> TimeshiftStoragePreference.Ram
        PlaybackTimeshiftStorageMode.InternalStorage -> TimeshiftStoragePreference.InternalStorage
    }

