package com.vivicast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastBorders
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogError
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastTextField
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastShapes
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourcePriorityDirection
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.data.provider.isAutomaticallyRefreshable
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.domain.model.EpgSource
import androidx.compose.ui.res.stringResource
import com.vivicast.tv.core.designsystem.R
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BackupSettingsPanel(
    state: BackupSettingsState = BackupSettingsState(),
    onBackupSettingsChanged: (BackupSettingsState) -> Unit = {},
    onExportStandardBackup: () -> Unit = {},
    onImportStandardBackup: () -> Unit = {},
    onExportEncryptedFullBackup: (String) -> Unit = {},
    onImportEncryptedFullBackup: (String) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    var pendingFullAction by remember { mutableStateOf<BackupFullAction?>(null) }
    val cycleTarget = {
        onBackupSettingsChanged(state.copy(target = state.target.next()))
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_export),
                help = stringResource(R.string.settings_help_backup_standard),
                value = stringResource(R.string.settings_backup_select_value),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("export") },
                onClick = onExportStandardBackup,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_import),
                help = stringResource(R.string.settings_help_backup_restore),
                value = stringResource(R.string.settings_backup_select_value),
                icon = { SettingsRowIcon("import") },
                onClick = onImportStandardBackup,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_target),
                help = stringResource(R.string.settings_help_backup_target),
                value = state.target.label(),
                icon = { SettingsRowIcon("folder") },
                onClick = cycleTarget,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_last),
                help = stringResource(R.string.settings_help_backup_last),
                value = state.lastBackupAtMillis?.toBackupTimestamp() ?: stringResource(R.string.settings_backup_never),
                enabled = false,
                icon = { SettingsRowIcon("clock") },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_full_export),
                help = stringResource(R.string.settings_help_backup_full_export),
                value = stringResource(R.string.settings_backup_full_value),
                icon = { SettingsRowIcon("shield") },
                onClick = { pendingFullAction = BackupFullAction.Export },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_full_import),
                help = stringResource(R.string.settings_help_backup_full_import),
                value = stringResource(R.string.settings_backup_full_value),
                icon = { SettingsRowIcon("shield") },
                onClick = { pendingFullAction = BackupFullAction.Import },
            )
        }
    }

    pendingFullAction?.let { action ->
        FullBackupPassphraseDialog(
            action = action,
            onCancel = { pendingFullAction = null },
            onConfirm = { passphrase ->
                pendingFullAction = null
                when (action) {
                    BackupFullAction.Export -> onExportEncryptedFullBackup(passphrase)
                    BackupFullAction.Import -> onImportEncryptedFullBackup(passphrase)
                }
            },
        )
    }
}

@Composable
private fun BackupTargetMode.label(): String = when (this) {
    BackupTargetMode.LocalStorage -> stringResource(R.string.common_local_storage)
    BackupTargetMode.Smb -> "SMB"
    BackupTargetMode.GoogleDrive -> "Google Drive"
}

// v1 offers only local backup (SMB/Google Drive are post-v1), so the target stays at LocalStorage.
private fun BackupTargetMode.next(): BackupTargetMode = BackupTargetMode.LocalStorage

internal fun Long.toBackupTimestamp(): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))

@Composable
private fun FullBackupPassphraseDialog(
    action: BackupFullAction,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val strPassphraseBody = stringResource(R.string.settings_backup_passphrase_body)
    val strPassphraseMissing = stringResource(R.string.settings_backup_passphrase_missing)
    val fieldFocus = remember { FocusRequester() }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = fieldFocus,
        modifier = Modifier.testTag(fullBackupPassphraseDialogTag()),
    ) {
        InfoPanel(
            title = stringResource(action.titleRes),
            body = error ?: strPassphraseBody,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastTextField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                error = null
            },
            secret = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            focusRequester = fieldFocus,
            fieldModifier = Modifier.testTag(fullBackupPassphraseFieldTag()),
            isError = error != null,
            maxLength = 100,
        )
        VivicastDialogActions(
            primaryLabel = stringResource(action.confirmLabelRes),
            onPrimary = {
                val value = passphrase.trim()
                if (value.isBlank()) {
                    error = strPassphraseMissing
                } else {
                    onConfirm(value)
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = fullBackupPassphraseConfirmTag(),
            secondaryTestTag = fullBackupPassphraseCancelTag(),
        )
    }
}

private enum class BackupFullAction(
    @get:StringRes val titleRes: Int,
    @get:StringRes val confirmLabelRes: Int,
) {
    Export(R.string.settings_backup_full_export, R.string.settings_backup_full_action_export),
    Import(R.string.settings_backup_full_import, R.string.settings_backup_full_action_import),
}

fun fullBackupPassphraseDialogTag(): String = "full-backup-passphrase-dialog"
fun fullBackupPassphraseFieldTag(): String = "full-backup-passphrase-field"
fun fullBackupPassphraseCancelTag(): String = "full-backup-passphrase-cancel"
fun fullBackupPassphraseConfirmTag(): String = "full-backup-passphrase-confirm"

