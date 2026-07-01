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

@Composable
internal fun ProtectionUnlockDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> String?,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }

    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = fieldFocus,
    ) {
        InfoPanel(
            title = title,
            body = error ?: stringResource(R.string.main_unlock_body),
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastTextField(
            value = pin,
            onValueChange = {
                pin = it.filter(Char::isDigit).take(PIN_LENGTH)
                error = null
            },
            secret = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            focusRequester = fieldFocus,
            isError = error != null,
        )
        VivicastDialogActions(
            primaryLabel = "Freigeben",
            onPrimary = {
                error = if (pin.length == PIN_LENGTH) onSubmit(pin) else "PIN muss aus vier Ziffern bestehen."
            },
            secondaryLabel = "Abbrechen",
            onSecondary = onDismiss,
        )
    }
}

@Composable
internal fun SystemTargetUnavailableDialog(
    target: SystemTargetUnavailable,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = closeFocus,
    ) {
        InfoPanel(
            title = target.title,
            body = target.body,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastButtonRow {
            ActionPill(stringResource(R.string.main_close), modifier = Modifier.focusRequester(closeFocus), onClick = onDismiss)
        }
    }
}

// Auf Android TV löst ACTION_OPEN_DOCUMENT nur auf den Framework-Stub auf (keine echte
// Dateiauswahl). Echter Picker = mindestens ein nicht-Stub-Handler.
internal fun hasRealDocumentPicker(pm: PackageManager): Boolean {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
    @Suppress("DEPRECATION", "QueryPermissionsNeeded")
    return pm.queryIntentActivities(intent, 0)
        .any { it.activityInfo?.packageName != "com.android.tv.frameworkpackagestubs" }
}

internal fun queryDisplayName(context: Context, uri: Uri): String {
    val fromResolver = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
        }
    }.getOrNull()
    return fromResolver?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
}

internal fun openFileManagerSearch(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=file%20manager&c=apps"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=file%20manager&c=apps"))
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.settings_provider_file_no_store), Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
internal fun FileManagerMissingDialog(
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = firstFocus,
    ) {
        InfoPanel(
            title = stringResource(R.string.settings_provider_file_no_manager_title),
            body = stringResource(R.string.settings_provider_file_no_manager_body),
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastButtonRow {
            ActionPill(
                stringResource(R.string.settings_provider_file_install_manager),
                modifier = Modifier.focusRequester(firstFocus),
                onClick = onSearch,
            )
            ActionPill(stringResource(R.string.common_cancel), onClick = onDismiss)
        }
    }
}

@Composable
internal fun ExternalPlayerChoiceDialog(
    request: PlaybackRequest,
    onDismiss: () -> Unit,
    onInternal: () -> Unit,
    onExternal: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = firstFocus,
    ) {
        InfoPanel(
            title = stringResource(R.string.main_external_dialog_title),
            body = stringResource(R.string.main_external_dialog_body),
            badge = request.title,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastButtonRow {
            ActionPill("Intern", modifier = Modifier.focusRequester(firstFocus), onClick = onInternal)
            ActionPill("Extern", onClick = onExternal)
            ActionPill("Abbrechen", onClick = onDismiss)
        }
    }
}

@Composable
internal fun StandardRestoreConfirmDialog(
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
    val cancelFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocus,
    ) {
        InfoPanel(
            title = title,
            body = body,
            badge = "${preview.providerCount} Quellen, ${preview.favoriteCount} Favoriten",
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = "Wiederherstellen",
            onPrimary = onConfirm,
            secondaryLabel = "Abbrechen",
            onSecondary = onDismiss,
            secondaryFocusRequester = cancelFocus,
        )
    }
}

@Composable
internal fun StandardRestoreSafetyFailedDialog(
    restore: PendingStandardRestore,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val preview = restore.preview
    val cancelFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocus,
    ) {
        InfoPanel(
            title = "Sicherheitsbackup fehlgeschlagen",
            body = "Lokale Daten bleiben unveraendert. Du kannst abbrechen oder den Restore bewusst ohne internes Sicherheitsbackup fortsetzen.",
            badge = "${preview.providerCount} Quellen",
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = "Fortsetzen",
            onPrimary = onContinue,
            secondaryLabel = "Abbrechen",
            onSecondary = onDismiss,
            secondaryFocusRequester = cancelFocus,
        )
    }
}

