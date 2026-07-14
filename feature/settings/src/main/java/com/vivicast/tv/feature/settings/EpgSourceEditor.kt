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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.VivicastBorders
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastButtonRow
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
import com.vivicast.tv.data.epg.EpgContentSummary
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
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
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun EpgSourceEditor(
    editor: EpgSourceEditorState,
    message: String?,
    onEditorChange: (EpgSourceEditorState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    duplicateName: Boolean = false,
    isDuplicateName: (String) -> Boolean = { false },
    duplicateUrlName: String? = null,
    connectionTestStatus: ConnectionTestStatus = ConnectionTestStatus.Idle,
    connectionSummary: EpgContentSummary? = null,
    connectionError: String? = null,
    onTestConnection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Blank name reddens only after a save attempt (mirrors the playlist editor).
    var showNameBlankError by remember { mutableStateOf(false) }
    val nameBlank = editor.name.isBlank()
    // Add starts on the name field; edit starts on the enable toggle (the first item) — mirrors the
    // playlist editor. Wait one frame, else focus escapes to the top nav bar during the overview→editor swap.
    val firstFocus = remember { FocusRequester() }
    val toggleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { (if (editor.isEditing) toggleFocus else firstFocus).requestFocus() }
    }
    // URL is required in both add and edit; clearing a pre-filled URL blocks save, but a failed async
    // pre-fill (blank + hasExistingUrl) does not. Reddens only after a save attempt, mirroring M3U.
    val urlFocus = remember { FocusRequester() }
    var showUrlBlankError by remember { mutableStateOf(false) }
    val urlBlank = editor.urlRequiredMissing
    // On a blocked save, jump focus to the first bad field (name → URL), like the playlist editor.
    var pendingErrorFocus by remember { mutableStateOf<EpgEditorErrorFocus?>(null) }
    // Name editor popup (edit mode only — add keeps the inline field).
    var showNameDialog by remember { mutableStateOf(false) }
    LaunchedEffect(pendingErrorFocus) {
        val target = pendingErrorFocus ?: return@LaunchedEffect
        awaitFrame()
        runCatching { (if (target == EpgEditorErrorFocus.Name) firstFocus else urlFocus).requestFocus() }
        pendingErrorFocus = null
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        // Enable/disable toggle above the name (edit only, like the playlist editor). A new source is
        // active by default, so add mode needs no toggle.
        if (editor.isEditing) {
            item(key = "active") {
                VivicastSettingsRow(
                    title = stringResource(R.string.settings_provider_active_label),
                    help = "",
                    value = if (editor.isActive) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                    onClick = { onEditorChange(editor.copy(isActive = !editor.isActive)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(toggleFocus),
                )
            }
        }
        // Edit mode: name row → popup (validation in the popup). Add mode: inline field. Stable key so
        // inserting the add-mode duplicate warning never re-creates the focused field.
        item(key = "name") {
            EditorNameField(
                isEditing = editor.isEditing,
                name = editor.name,
                placeholder = stringResource(R.string.settings_epg_name_placeholder),
                isError = duplicateName || (showNameBlankError && nameBlank),
                duplicateMessage = if (duplicateName) stringResource(R.string.settings_epg_name_exists_body) else null,
                focusRequester = firstFocus,
                onNameChange = { onEditorChange(editor.copy(name = it)) },
                onOpenNameDialog = { showNameDialog = true },
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                // URL field and its connection-test button share one row (mirrors the playlist editor).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ProviderTextField(
                        label = stringResource(R.string.m3u_source_url),
                        value = editor.url,
                        placeholder = "https://...",
                        // Editing the field means the user is setting a new URL — stop falling back to the
                        // stored one, so an empty field is then a required-field error.
                        onValueChange = { onEditorChange(editor.copy(url = it, hasExistingUrl = false)) },
                        modifier = Modifier.weight(1f),
                        focusRequester = urlFocus,
                        // Show the pre-filled URL in plaintext when editing (mirrors the playlist M3U field).
                        secret = false,
                        isError = connectionTestStatus == ConnectionTestStatus.Failed ||
                            duplicateUrlName != null ||
                            (showUrlBlankError && urlBlank),
                        maxLength = 250,
                    )
                    // A blank URL reddens the field and jumps focus instead of running the test (mirrors
                    // the M3U editor), so an empty test can't add a second "URL missing" message.
                    ConnectionTestButton(
                        status = connectionTestStatus,
                        onClick = {
                            // Can't test an empty field (even if a stored URL exists) — redden + focus.
                            if (editor.url.isBlank()) {
                                showUrlBlankError = true
                                pendingErrorFocus = EpgEditorErrorFocus.Url
                            } else {
                                onTestConnection()
                            }
                        },
                    )
                }
                // Missing / duplicate URL — red inline hint (no note); .gz/.xz ignored for duplicates.
                when {
                    showUrlBlankError && urlBlank -> VivicastDialogError(stringResource(R.string.validation_epg_url_missing))
                    duplicateUrlName != null -> VivicastDialogError(stringResource(R.string.settings_epg_url_exists, duplicateUrlName))
                }
                // Failed-test reason as a red inline hint (no note).
                if (connectionError != null) VivicastDialogError(connectionError)
                // Parsed breakdown after a passed test, so stale counts never linger.
                if (connectionTestStatus == ConnectionTestStatus.Passed && connectionSummary != null) {
                    // Same styling as the playlist "Found in this playlist" summary (default BodyText color).
                    BodyText(stringResource(R.string.settings_epg_test_summary, connectionSummary.channels))
                }
            }
        }

        item {
            FocusPanel(
                modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                        BasicText(stringResource(R.string.settings_epg_timeshift_label), style = VivicastTypography.LabelLarge)
                        BodyText(stringResource(R.string.settings_epg_timeshift_help), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill("-30", modifier = Modifier.width(82.dp), onClick = {
                            onEditorChange(editor.copy(timeShiftMinutes = (editor.timeShiftMinutes - 30).coerceAtLeast(-720)))
                        })
                        BasicText(stringResource(R.string.settings_epg_timeshift_value, editor.timeShiftMinutes), style = VivicastTypography.LabelLarge)
                        ActionPill("+30", modifier = Modifier.width(82.dp), onClick = {
                            onEditorChange(editor.copy(timeShiftMinutes = (editor.timeShiftMinutes + 30).coerceAtMost(720)))
                        })
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ActionPill(
                    label = stringResource(R.string.common_cancel),
                    modifier = Modifier.width(132.dp),
                    onClick = onCancel,
                )
                ActionPill(
                    label = stringResource(R.string.common_save),
                    modifier = Modifier.width(150.dp),
                    onClick = {
                        // A blocked save reddens the offending field and jumps focus to it
                        // (name → URL), like the playlist editor.
                        val errorFocus = when {
                            nameBlank || duplicateName -> EpgEditorErrorFocus.Name
                            urlBlank || duplicateUrlName != null -> EpgEditorErrorFocus.Url
                            else -> null
                        }
                        if (errorFocus == null) {
                            onSave()
                        } else {
                            showNameBlankError = true
                            showUrlBlankError = true
                            pendingErrorFocus = errorFocus
                        }
                    },
                )
                if (editor.isEditing) {
                    ActionPill(label = stringResource(R.string.settings_delete), modifier = Modifier.width(140.dp), onClick = onDelete)
                }
            }
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = stringResource(R.string.common_note),
                    body = message,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }


    if (showNameDialog) {
        NameEditDialog(
            initialName = editor.name,
            isDuplicate = isDuplicateName,
            duplicateMessage = stringResource(R.string.settings_epg_name_exists_body),
            onCancel = { showNameDialog = false },
            onSave = {
                onEditorChange(editor.copy(name = it))
                showNameDialog = false
            },
        )
    }
}

@Composable
fun DeleteEpgSourceDialog(
    source: EpgSource,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    // Deletion runs async and the dialog only closes when it completes — show a spinner meanwhile.
    var deleting by remember { mutableStateOf(false) }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocusRequester,
        modifier = Modifier.testTag(deleteEpgSourceDialogTag(source.id)),
    ) {
        InfoPanel(
            title = stringResource(R.string.settings_epg_delete_confirm),
            body = stringResource(R.string.settings_epg_delete_body),
            badge = source.name,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(if (deleting) R.string.settings_deleting else R.string.settings_delete),
            onPrimary = {
                deleting = true
                onDelete()
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryEnabled = !deleting,
            primaryLoading = deleting,
            primaryTestTag = deleteEpgSourceConfirmTag(source.id),
            secondaryTestTag = deleteEpgSourceCancelTag(source.id),
            secondaryFocusRequester = cancelFocusRequester,
        )
    }
}

fun deleteEpgSourceDialogTag(sourceId: String): String = "settings-delete-epg-source-dialog-$sourceId"
fun deleteEpgSourceCancelTag(sourceId: String): String = "settings-delete-epg-source-cancel-$sourceId"
fun deleteEpgSourceConfirmTag(sourceId: String): String = "settings-delete-epg-source-confirm-$sourceId"

private enum class EpgEditorErrorFocus { Name, Url }

internal data class EpgSourceEditorState(
    val sourceId: String?,
    val name: String,
    val url: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
    // True when editing a source that has a stored URL, until the user edits the field. A blank field
    // then means "keep the stored URL" (so a failed async URL pre-fill doesn't block save), while
    // actively clearing it (which flips this false) is a required-field error.
    val hasExistingUrl: Boolean = false,
) {
    val isEditing: Boolean get() = sourceId != null

    /** URL missing in a way that blocks save: blank AND not falling back to a stored URL. */
    val urlRequiredMissing: Boolean get() = url.isBlank() && !hasExistingUrl

    fun validationMessage(msgNameMissing: String, msgUrlMissing: String): String? {
        if (name.isBlank()) return msgNameMissing
        // URL required in both add and edit (clearing a pre-filled URL blocks save), consistent with the
        // Save-button pre-check. Intentionally stricter than the M3U editor (D2). A failed pre-fill keeps
        // hasExistingUrl, so it's not treated as missing.
        if (urlRequiredMissing) return msgUrlMissing
        return null
    }

    fun toEditRequest(): EpgSourceEditRequest =
        EpgSourceEditRequest(
            sourceId = sourceId,
            name = name,
            url = url.ifBlank { null },
            timeShiftMinutes = timeShiftMinutes,
            isActive = isActive,
        )

    companion object {
        fun newSource(): EpgSourceEditorState =
            EpgSourceEditorState(
                sourceId = null,
                name = "",
                url = "",
                timeShiftMinutes = 0,
                isActive = true,
            )

        fun from(source: EpgSource, url: String = ""): EpgSourceEditorState =
            EpgSourceEditorState(
                sourceId = source.id,
                name = source.name,
                url = url,
                timeShiftMinutes = source.timeShiftMinutes,
                isActive = source.isActive,
                // Editing: there is a stored URL (even if the async pre-fill returned blank on failure).
                hasExistingUrl = true,
            )
    }
}

