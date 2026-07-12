package com.vivicast.tv.feature.settings

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
    onExportBackup: (String) -> Unit = {},
    onImportBackup: () -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    var showExportDialog by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_export),
                help = stringResource(R.string.settings_help_backup_export),
                value = stringResource(R.string.settings_backup_select_value),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("export") },
                onClick = { showExportDialog = true },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_backup_import),
                help = stringResource(R.string.settings_help_backup_import),
                value = stringResource(R.string.settings_backup_select_value),
                icon = { SettingsRowIcon("import") },
                // Import picks the file first (App-hoisted picker), then prompts for the passphrase.
                onClick = { onImportBackup() },
            )
        }
    }

    if (showExportDialog) {
        BackupExportPassphraseDialog(
            onCancel = { showExportDialog = false },
            onConfirm = { passphrase ->
                showExportDialog = false
                onExportBackup(passphrase)
            },
        )
    }
}

internal fun Long.toBackupTimestamp(): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))

private const val MIN_BACKUP_PASSPHRASE_LENGTH = 8

@Composable
private fun BackupExportPassphraseDialog(
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val strBody = stringResource(R.string.settings_backup_passphrase_export_body)
    val strMissing = stringResource(R.string.settings_backup_passphrase_missing)
    val strTooShort = stringResource(R.string.settings_backup_passphrase_too_short)
    val strMismatch = stringResource(R.string.settings_backup_passphrase_mismatch)
    val fieldFocus = remember { FocusRequester() }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = fieldFocus,
        modifier = Modifier.testTag(fullBackupPassphraseDialogTag()),
    ) {
        InfoPanel(
            title = stringResource(R.string.settings_backup_export),
            body = error ?: strBody,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastTextField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                error = null
            },
            label = stringResource(R.string.settings_backup_passphrase_label),
            secret = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            focusRequester = fieldFocus,
            fieldModifier = Modifier.testTag(fullBackupPassphraseFieldTag()),
            isError = error != null,
            maxLength = 100,
        )
        VivicastTextField(
            value = confirm,
            onValueChange = {
                confirm = it
                error = null
            },
            label = stringResource(R.string.settings_backup_passphrase_confirm_label),
            secret = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            fieldModifier = Modifier.testTag(fullBackupPassphraseConfirmFieldTag()),
            isError = error != null,
            maxLength = 100,
        )
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.settings_backup_full_action_export),
            onPrimary = {
                val value = passphrase.trim()
                when {
                    value.isBlank() -> error = strMissing
                    value.length < MIN_BACKUP_PASSPHRASE_LENGTH -> error = strTooShort
                    value != confirm.trim() -> error = strMismatch
                    else -> onConfirm(value)
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = fullBackupPassphraseConfirmTag(),
            secondaryTestTag = fullBackupPassphraseCancelTag(),
        )
    }
}

fun fullBackupPassphraseDialogTag(): String = "full-backup-passphrase-dialog"
fun fullBackupPassphraseFieldTag(): String = "full-backup-passphrase-field"
fun fullBackupPassphraseConfirmFieldTag(): String = "full-backup-passphrase-confirm-field"
fun fullBackupPassphraseCancelTag(): String = "full-backup-passphrase-cancel"
fun fullBackupPassphraseConfirmTag(): String = "full-backup-passphrase-confirm"

