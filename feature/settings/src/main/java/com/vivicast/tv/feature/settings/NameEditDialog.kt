package com.vivicast.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogError
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTextField

/**
 * Name editor popup used by the playlist / EPG source editors in edit mode (mirrors the User-Agent
 * dialog). Validation happens on Save inside the dialog: blank and duplicate names keep it open with an
 * inline error; a valid name is committed via [onSave]. [duplicateMessage] is the editor-specific
 * "name already exists" text.
 */
/**
 * Name entry inside the playlist / EPG source editors. Edit mode shows a User-Agent-style row (name on
 * the left, current value on the right) that opens [NameEditDialog] via [onOpenNameDialog]. Add mode
 * keeps the inline text field with its live duplicate/blank error. Kept in one place so both editors
 * share the exact same behaviour (and to keep the editor Composables shorter).
 */
@Composable
internal fun EditorNameField(
    isEditing: Boolean,
    name: String,
    placeholder: String,
    isError: Boolean,
    duplicateMessage: String?,
    focusRequester: FocusRequester,
    onNameChange: (String) -> Unit,
    onOpenNameDialog: () -> Unit,
    // Attached to the focusable name node so the section rail's RIGHT can re-enter the editor (the name
    // field is present in both add and edit, so it is the stable entry target). See SettingsRoute.
    modifier: Modifier = Modifier,
) {
    if (isEditing) {
        VivicastSettingsRow(
            title = stringResource(R.string.settings_provider_name_label),
            help = "",
            value = name,
            // Force text so a name of "An"/"Aus" is not mistaken for a toggle value.
            forceTextValue = true,
            modifier = modifier,
            onClick = onOpenNameDialog,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            ProviderTextField(
                label = stringResource(R.string.settings_provider_name_label),
                value = name,
                placeholder = placeholder,
                onValueChange = onNameChange,
                focusRequester = focusRequester,
                isError = isError,
                maxLength = 25,
                modifier = modifier,
            )
            VivicastDialogError(duplicateMessage)
        }
    }
}

@Composable
internal fun NameEditDialog(
    initialName: String,
    isDuplicate: (String) -> Boolean,
    duplicateMessage: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    val strNameMissing = stringResource(R.string.validation_name_missing)
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_provider_name_label),
    ) {
        VivicastTextField(
            value = value,
            onValueChange = {
                value = it.take(25)
                error = null
            },
            focusRequester = fieldFocus,
            isError = error != null,
        )
        if (error != null) {
            BodyText(error!!, color = VivicastColors.Error)
        }
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.common_save),
            onPrimary = {
                val trimmed = value.trim()
                when {
                    trimmed.isBlank() -> error = strNameMissing
                    isDuplicate(trimmed) -> error = duplicateMessage
                    else -> onSave(trimmed)
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
        )
    }
}
