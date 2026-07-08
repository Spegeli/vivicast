package com.vivicast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
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
import com.vivicast.tv.core.designsystem.VivicastSpinner
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.LOGO_PRIORITY_EPG
import com.vivicast.tv.data.provider.LOGO_PRIORITY_PLAYLIST
import com.vivicast.tv.data.provider.REFRESH_INTERVAL_OFF
import com.vivicast.tv.data.provider.REFRESH_INTERVAL_OPTIONS_HOURS
import com.vivicast.tv.data.provider.ContentSummary
import com.vivicast.tv.data.provider.M3uContentSummarizer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ProviderEditor(
    editor: ProviderEditorState,
    duplicates: ProviderDuplicateInfo,
    isDuplicateName: (String) -> Boolean = { false },
    message: String?,
    connectionTestStatus: ConnectionTestStatus,
    connectionSummary: ContentSummary?,
    connectionError: String?,
    signals: ProviderEditorSignals,
    actions: ProviderEditorActions,
    epgLinks: ProviderEpgLinkInfo = ProviderEpgLinkInfo(),
    modifier: Modifier = Modifier,
) {
    val dialogs = remember { ProviderEditorDialogState() }
    val onEditorChange = actions.onEditorChange
    val onTestConnection = actions.onTestConnection
    val onSave = actions.onSave
    val onCancel = actions.onCancel
    val onToggleEnabled = actions.onToggleEnabled
    val onDelete = actions.onDelete
    val onPickM3uFile = actions.onPickM3uFile
    val firstFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val importFocus = remember { FocusRequester() }
    val fileFocus = remember { FocusRequester() }
    val serverFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val toggleFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Wait one frame before requesting focus, else it escapes to the top nav bar. Add starts on the
    // name field; edit starts on the enable toggle (the first item).
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { (if (editor.isEditing) toggleFocus else firstFocus).requestFocus() }
    }
    val focusByError = mapOf(
        ProviderEditorErrorFocus.Name to firstFocus, ProviderEditorErrorFocus.Url to urlFocus,
        ProviderEditorErrorFocus.File to fileFocus, ProviderEditorErrorFocus.Server to serverFocus,
        ProviderEditorErrorFocus.User to userFocus, ProviderEditorErrorFocus.Pass to passFocus, ProviderEditorErrorFocus.Import to importFocus,
    )
    var pendingErrorFocus by remember { mutableStateOf<ProviderEditorErrorFocus?>(null) }
    LaunchedEffect(pendingErrorFocus) {
        val target = pendingErrorFocus ?: return@LaunchedEffect
        // Name/URL sit at the top; scroll there first so the field is composed before focusing.
        if (target in topFocusTargets) {
            listState.scrollToItem(0)
        }
        awaitFrame()
        runCatching { focusByError[target]?.requestFocus() }
        pendingErrorFocus = null
    }
    // Panel bumps this to jump focus to the source field: on manual-test failure and on the
    // "Korrigieren" button of the save-time failure dialog (save failure itself shows the dialog).
    LaunchedEffect(signals.focusSource) {
        pendingErrorFocus = editor.sourceFocusTarget().takeIf { signals.focusSource > 0 }
    }
    // Empty required fields turn red only after an attempt. Name is checked on save only; the source
    // field is also checked on test (which is name-independent), so they use separate flags.
    var showNameBlankError by remember { mutableStateOf(false) }
    var showSourceBlankError by remember { mutableStateOf(false) }
    val nameBlank = editor.name.isBlank()
    val urlBlank = editor.isSourceBlank(M3uSourceMode.Url)
    val fileBlank = editor.isSourceBlank(M3uSourceMode.File)
    val serverBlank = editor.isXtreamFieldBlank(XtreamField.Server)
    val userBlank = editor.isXtreamFieldBlank(XtreamField.User)
    val passBlank = editor.isXtreamFieldBlank(XtreamField.Pass)
    val sourceBlankError = showSourceBlankError && (urlBlank || fileBlank)
    // Test is name-independent; a blank required source field reddens it and jumps focus (no note).
    val onTestClick: () -> Unit = {
        val blankFocus = testBlankSourceFocus(editor, serverBlank, userBlank, passBlank, urlBlank, fileBlank)
        if (blankFocus == null) {
            onTestConnection()
        } else {
            showSourceBlankError = true
            pendingErrorFocus = blankFocus
        }
    }
    val xtreamFields = xtreamFieldState(showSourceBlankError, serverBlank, userBlank, passBlank, serverFocus, userFocus, passFocus)
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        // Enable/disable toggle above the name — flips the playlist immediately (edit only, no save).
        providerActiveToggleItem(editor, toggleFocus, onToggleEnabled)

        // Edit mode: name row → popup (validation in the popup). Add mode: inline field. Stable key so
        // inserting the add-mode duplicate warning never re-creates the focused field.
        item(key = "name") {
            EditorNameField(
                isEditing = editor.isEditing,
                name = editor.name,
                placeholder = stringResource(R.string.settings_provider_name_placeholder),
                isError = duplicates.name || (showNameBlankError && nameBlank),
                duplicateMessage = if (duplicates.name) stringResource(R.string.settings_provider_name_exists_body) else null,
                focusRequester = firstFocus,
                onNameChange = { onEditorChange(editor.copy(name = it)) },
                onOpenNameDialog = { dialogs.showName = true },
            )
        }

        providerSourceChoiceItem(editor, onEditorChange)

        when (editor.type) {
            ProviderType.M3u -> providerM3uCredentialItems(
                editor = editor,
                duplicateUrlName = duplicates.urlName,
                sourceBlankError = sourceBlankError,
                sourceFocus = M3uSourceFocus(urlFocus, fileFocus),
                // Only preview the breakdown right after a passed test, so stale counts never linger.
                contentSummary = connectionSummary?.takeIf { connectionTestStatus == ConnectionTestStatus.Passed },
                connectionTestStatus = connectionTestStatus,
                onTestClick = onTestClick,
                onEditorChange = onEditorChange,
                onPickM3uFile = onPickM3uFile,
            )
            ProviderType.Xtream -> providerXtreamCredentialItems(
                editor = editor,
                fields = xtreamFields,
                contentSummary = connectionSummary?.takeIf { connectionTestStatus == ConnectionTestStatus.Passed },
                connectionTestStatus = connectionTestStatus,
                onTestClick = onTestClick,
                onEditorChange = onEditorChange,
            )
        }

        // Failed-test reason as a red hint (no note), shown just under the source fields.
        if (connectionError != null) item(key = "conn-error") { VivicastDialogError(connectionError) }

        providerImportItem(editor, importFocus, onEditorChange)

        // Update interval / app-start / User-Agent / EPG assignment are edit-only; a new playlist uses
        // the defaults until it has been saved (and thus has an id to assign EPG sources to).
        if (editor.isEditing) {
            providerEditControlItems(
                editor, onEditorChange, epgLinks.linkedIds.size,
                onOpenInterval = { dialogs.showInterval = true },
                onOpenUserAgent = { dialogs.showUserAgent = true },
                onOpenEpgSources = { dialogs.showEpgSources = true },
                onOpenLogoPriority = { dialogs.showLogoPriority = true },
            )
        }

        if (message != null) {
            item(key = "message") {
                InfoPanel(
                    title = stringResource(R.string.common_note),
                    body = message,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "actions") {
            VivicastButtonRow {
                ActionPill(label = stringResource(R.string.common_cancel), enabled = !signals.saving, onClick = onCancel)
                ActionPill(
                    label = stringResource(saveLabelRes(signals.saving)),
                    loading = signals.saving,
                    onClick = {
                        // A blocked save jumps focus to the first bad field (name → URL → import) and
                        // reddens it, instead of the real save raising a note.
                        val error = firstSaveError(editor, duplicates.name, duplicates.urlName, nameBlank, urlBlank, fileBlank, serverBlank, userBlank, passBlank)
                        if (error == null) {
                            onSave()
                        } else {
                            if (error != ProviderEditorErrorFocus.Import) {
                                showNameBlankError = true
                                showSourceBlankError = true
                            }
                            pendingErrorFocus = error
                        }
                    },
                )
                if (editor.isEditing) {
                    ActionPill(label = stringResource(R.string.settings_delete), enabled = !signals.saving, onClick = onDelete)
                }
            }
        }
    }

    ProviderEditorDialogs(dialogs, editor, epgLinks, isDuplicateName, onEditorChange, actions.onToggleEpgLink)
}

/** Open/closed flags for the editor's popups (name / interval / User-Agent / EPG sources). */
internal class ProviderEditorDialogState {
    var showName by mutableStateOf(false)
    var showInterval by mutableStateOf(false)
    var showUserAgent by mutableStateOf(false)
    var showEpgSources by mutableStateOf(false)
    var showLogoPriority by mutableStateOf(false)
}

@Composable
private fun ProviderEditorDialogs(
    dialogs: ProviderEditorDialogState,
    editor: ProviderEditorState,
    epgLinks: ProviderEpgLinkInfo,
    isDuplicateName: (String) -> Boolean,
    onEditorChange: (ProviderEditorState) -> Unit,
    onToggleEpgLink: (sourceId: String, link: Boolean) -> Unit,
) {
    if (dialogs.showName) {
        NameEditDialog(
            initialName = editor.name,
            isDuplicate = isDuplicateName,
            duplicateMessage = stringResource(R.string.settings_provider_name_exists_body),
            onCancel = { dialogs.showName = false },
            onSave = { value ->
                onEditorChange(editor.copy(name = value))
                dialogs.showName = false
            },
        )
    }
    if (dialogs.showInterval) {
        ProviderIntervalDialog(
            current = editor.refreshIntervalHours,
            onSelect = { hours ->
                if (hours != editor.refreshIntervalHours) onEditorChange(editor.copy(refreshIntervalHours = hours))
                dialogs.showInterval = false
            },
            onDismiss = { dialogs.showInterval = false },
        )
    }
    if (dialogs.showUserAgent) {
        ProviderUserAgentDialog(
            initialValue = editor.userAgent,
            onCancel = { dialogs.showUserAgent = false },
            onSave = { value ->
                onEditorChange(editor.copy(userAgent = value))
                dialogs.showUserAgent = false
            },
        )
    }
    if (dialogs.showEpgSources) {
        ProviderEpgSourcesDialog(
            sources = epgLinks.sources,
            linkedIds = epgLinks.linkedIds,
            onToggle = onToggleEpgLink,
            onDismiss = { dialogs.showEpgSources = false },
        )
    }
    if (dialogs.showLogoPriority) {
        ProviderLogoPriorityDialog(
            current = editor.logoPriority,
            onSelect = { value ->
                if (value != editor.logoPriority) onEditorChange(editor.copy(logoPriority = value))
                dialogs.showLogoPriority = false
            },
            onDismiss = { dialogs.showLogoPriority = false },
        )
    }
}

/** Duplicate-name / duplicate-URL flags, bundled to keep the editor under the arg limit. */
internal class ProviderDuplicateInfo(val name: Boolean, val urlName: String?)

/** Transient panel signals driving the editor UI: focus-jump trigger + save-in-progress flag. */
internal class ProviderEditorSignals(val focusSource: Int, val saving: Boolean)

private fun saveLabelRes(saving: Boolean): Int =
    if (saving) R.string.settings_provider_saving else R.string.common_save

/** Callbacks the inline provider editor raises. Bundled so the composable stays under the arg limit. */
internal class ProviderEditorActions(
    val onEditorChange: (ProviderEditorState) -> Unit,
    val onTestConnection: () -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val onToggleEnabled: () -> Unit,
    val onDelete: () -> Unit,
    val onPickM3uFile: ((String, String) -> Unit) -> Unit,
    // Links/unlinks an EPG source to the edited playlist immediately (no save button).
    val onToggleEpgLink: (sourceId: String, link: Boolean) -> Unit = { _, _ -> },
)

/** All EPG sources plus the ids linked to the edited playlist, for the "EPG Quellen" assignment popup. */
internal class ProviderEpgLinkInfo(
    val sources: List<EpgSource> = emptyList(),
    val linkedIds: Set<String> = emptySet(),
)

/** Edit mode only: an enable/disable toggle that flips the playlist immediately (no save needed). */
private fun LazyListScope.providerActiveToggleItem(
    editor: ProviderEditorState,
    focusRequester: FocusRequester,
    onToggle: () -> Unit,
) {
    if (!editor.isEditing) return
    item(key = "active") {
        VivicastSettingsRow(
            title = stringResource(R.string.settings_provider_active_label),
            help = "",
            value = if (editor.isActive) stringResource(R.string.value_on) else stringResource(R.string.value_off),
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}

/** Add mode only: the single-choice source-type row (M3U URL | M3U File | Xtream Codes). */
private fun LazyListScope.providerSourceChoiceItem(
    editor: ProviderEditorState,
    onEditorChange: (ProviderEditorState) -> Unit,
) {
    if (editor.isEditing) return
    item(key = "source-choice") {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            BodyText(stringResource(R.string.settings_provider_source_section), maxLines = 1)
            VivicastButtonRow {
                // Variant C: switching source type keeps the name and every other draft; only type /
                // source-mode change. Save persists exactly the active type (the repository writes
                // credentials by request.type + m3uSourceMode), so inactive drafts never persist.
                ProviderChoiceButton(
                    label = "M3U " + stringResource(R.string.m3u_source_url),
                    selected = editor.type == ProviderType.M3u && editor.m3uSourceMode == M3uSourceMode.Url,
                    onClick = { onEditorChange(editor.copy(type = ProviderType.M3u, m3uSourceMode = M3uSourceMode.Url)) },
                )
                ProviderChoiceButton(
                    label = "M3U " + stringResource(R.string.m3u_source_file),
                    selected = editor.type == ProviderType.M3u && editor.m3uSourceMode == M3uSourceMode.File,
                    onClick = { onEditorChange(editor.copy(type = ProviderType.M3u, m3uSourceMode = M3uSourceMode.File)) },
                )
                ProviderChoiceButton(
                    label = "Xtream Codes",
                    selected = editor.type == ProviderType.Xtream,
                    onClick = { onEditorChange(editor.copy(type = ProviderType.Xtream)) },
                )
            }
        }
    }
}

/** M3U credential fields (URL field + inline test, or the file picker), for add and edit. */
private fun LazyListScope.providerM3uCredentialItems(
    editor: ProviderEditorState,
    duplicateUrlName: String?,
    sourceBlankError: Boolean,
    sourceFocus: M3uSourceFocus,
    contentSummary: ContentSummary?,
    connectionTestStatus: ConnectionTestStatus,
    onTestClick: () -> Unit,
    onEditorChange: (ProviderEditorState) -> Unit,
    onPickM3uFile: ((String, String) -> Unit) -> Unit,
) {
    // Add mode picks URL vs File via the 3-way source choice above; edit mode keeps the URL/File
    // toggle here so an existing playlist can switch its source (clearing the old one).
    if (editor.isEditing) {
        item(key = "m3u-source-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                BodyText(stringResource(R.string.settings_provider_source_section), maxLines = 1)
                VivicastButtonRow {
                    M3uSourceMode.entries.forEach { mode ->
                        ActionPill(
                            label = stringResource(mode.labelRes),
                            selected = editor.m3uSourceMode == mode,
                            onClick = {
                                onEditorChange(
                                    editor.copy(
                                        m3uSourceMode = mode,
                                        m3uUrl = "",
                                        m3uContent = "",
                                        m3uHasExistingSource = false,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    item(key = "m3u-field") {
        when (editor.m3uSourceMode) {
            M3uSourceMode.Url -> Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                // URL field and its test button share one row.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_m3u_url_label),
                        value = editor.m3uUrl,
                        placeholder = if (editor.m3uHasExistingSource) stringResource(R.string.settings_provider_placeholder_reset) else "https://...",
                        onValueChange = {
                            onEditorChange(editor.copy(m3uUrl = it, m3uHasExistingSource = false))
                        },
                        modifier = Modifier.weight(1f),
                        focusRequester = sourceFocus.url,
                        secret = false,
                        isError = duplicateUrlName != null || sourceBlankError || connectionTestStatus == ConnectionTestStatus.Failed,
                        maxLength = 250,
                    )
                    ConnectionTestButton(status = connectionTestStatus, onClick = onTestClick)
                }
                // Duplicate or missing URL — red inline hint, no note.
                VivicastDialogError(
                    when {
                        duplicateUrlName != null -> stringResource(R.string.settings_provider_url_exists, duplicateUrlName)
                        sourceBlankError -> stringResource(R.string.validation_m3u_url_missing)
                        else -> null
                    },
                )
                ProviderContentSummary(contentSummary)
            }
            M3uSourceMode.File -> ProviderM3uFilePicker(
                editor = editor,
                blankError = sourceBlankError,
                fileFocusRequester = sourceFocus.file,
                contentSummary = contentSummary,
                connectionTestStatus = connectionTestStatus,
                onTestClick = onTestClick,
                onEditorChange = onEditorChange,
                onPickM3uFile = onPickM3uFile,
            )
        }
    }
}

/** Xtream server/username/password fields; the test button sits above the import row. */
private fun LazyListScope.providerXtreamCredentialItems(
    editor: ProviderEditorState,
    fields: XtreamFieldState,
    contentSummary: ContentSummary?,
    connectionTestStatus: ConnectionTestStatus,
    onTestClick: () -> Unit,
    onEditorChange: (ProviderEditorState) -> Unit,
) {
    item(key = "xtream-server") {
        ProviderTextField(
            label = stringResource(R.string.settings_provider_server_label),
            value = editor.xtreamServerUrl,
            placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else "https://server.example",
            onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it)) },
            focusRequester = fields.serverFocus,
            secret = false,
            isError = fields.serverError || connectionTestStatus == ConnectionTestStatus.Failed,
            maxLength = 250,
        )
    }
    item(key = "xtream-user") {
        ProviderTextField(
            label = stringResource(R.string.settings_provider_username_label),
            value = editor.xtreamUsername,
            placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_username_label),
            onValueChange = { onEditorChange(editor.copy(xtreamUsername = it)) },
            focusRequester = fields.userFocus,
            secret = false,
            isError = fields.userError || connectionTestStatus == ConnectionTestStatus.Failed,
            maxLength = 100,
        )
    }
    item(key = "xtream-pass") {
        ProviderTextField(
            label = stringResource(R.string.settings_provider_password_label),
            value = editor.xtreamPassword,
            placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_password_label),
            onValueChange = { onEditorChange(editor.copy(xtreamPassword = it)) },
            focusRequester = fields.passFocus,
            secret = true,
            allowReveal = true,
            isError = fields.passError || connectionTestStatus == ConnectionTestStatus.Failed,
            maxLength = 100,
        )
    }
    item(key = "xtream-test") {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            VivicastButtonRow {
                ConnectionTestButton(status = connectionTestStatus, onClick = onTestClick)
            }
            ProviderContentSummary(contentSummary)
        }
    }
}

@Composable
private fun ProviderM3uFilePicker(
    editor: ProviderEditorState,
    blankError: Boolean,
    fileFocusRequester: FocusRequester,
    contentSummary: ContentSummary?,
    connectionTestStatus: ConnectionTestStatus,
    onTestClick: () -> Unit,
    onEditorChange: (ProviderEditorState) -> Unit,
    onPickM3uFile: ((String, String) -> Unit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        // Chosen file name sits above the picker; the content breakdown (after a test) sits below it.
        if (editor.m3uFileName.isNotBlank()) {
            BodyText(stringResource(R.string.settings_provider_file_name, editor.m3uFileName))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionPill(
                label = stringResource(R.string.settings_provider_file_pick),
                modifier = Modifier.width(190.dp).focusRequester(fileFocusRequester),
                onClick = {
                    onPickM3uFile { fileName, content ->
                        onEditorChange(
                            editor.copy(
                                m3uContent = content.take(MAX_M3U_INLINE_SOURCE_CHARS),
                                m3uFileName = fileName,
                                m3uHasExistingSource = false,
                            ),
                        )
                    }
                },
            )
            ConnectionTestButton(status = connectionTestStatus, onClick = onTestClick)
        }
        // Missing file — red inline hint, no note.
        VivicastDialogError(if (blankError) stringResource(R.string.validation_m3u_file_missing) else null)
        ProviderContentSummary(contentSummary)
    }
}

/** Verbose channels/movies/series breakdown shown after a passed M3U test (URL and file alike). */
@Composable
private fun ProviderContentSummary(summary: ContentSummary?) {
    if (summary != null) {
        BodyText(
            stringResource(
                R.string.settings_provider_url_label,
                summary.channels,
                summary.movies,
                summary.series,
            ),
        )
    }
}

/** Import selection, shown for every source type (M3U classifies Live-TV/Movies/Series on import too). */
private fun LazyListScope.providerImportItem(
    editor: ProviderEditorState,
    importFocusRequester: FocusRequester,
    onEditorChange: (ProviderEditorState) -> Unit,
) {
    item(key = "import") {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            BodyText(stringResource(R.string.settings_provider_import_section), maxLines = 1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ImportCheckboxRow(
                    label = stringResource(R.string.nav_live_tv),
                    checked = editor.includeLiveTv,
                    onToggle = { onEditorChange(editor.copy(includeLiveTv = !editor.includeLiveTv)) },
                    modifier = Modifier.weight(1f).focusRequester(importFocusRequester),
                )
                ImportCheckboxRow(
                    label = stringResource(R.string.nav_movies_label),
                    checked = editor.includeMovies,
                    onToggle = { onEditorChange(editor.copy(includeMovies = !editor.includeMovies)) },
                    modifier = Modifier.weight(1f),
                )
                ImportCheckboxRow(
                    label = stringResource(R.string.nav_series_label),
                    checked = editor.includeSeries,
                    onToggle = { onEditorChange(editor.copy(includeSeries = !editor.includeSeries)) },
                    modifier = Modifier.weight(1f),
                )
            }
            // At least one content type is required — red inline error, same pattern as name/URL.
            VivicastDialogError(
                if (!editor.includeLiveTv && !editor.includeMovies && !editor.includeSeries) {
                    stringResource(R.string.validation_content_type_required)
                } else {
                    null
                },
            )
        }
    }
}

/** Edit-only rows: update interval (button → popup), refresh-on-app-start toggle, per-playlist
 * User-Agent (button → dialog), and EPG-source assignment (button → popup). */
private fun LazyListScope.providerEditControlItems(
    editor: ProviderEditorState,
    onEditorChange: (ProviderEditorState) -> Unit,
    linkedEpgCount: Int,
    onOpenInterval: () -> Unit,
    onOpenUserAgent: () -> Unit,
    onOpenEpgSources: () -> Unit,
    onOpenLogoPriority: () -> Unit,
) {
    // Auto-refresh (interval + app-start) only applies to source types that can actually be re-fetched.
    if (editor.isAutomaticallyRefreshable) {
        item(key = "update-interval") {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_provider_update_interval),
                help = "",
                value = intervalLabel(editor.refreshIntervalHours),
                forceTextValue = true,
                onClick = onOpenInterval,
            )
        }
        item(key = "refresh-on-start") {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_provider_refresh_on_start),
                help = "",
                value = if (editor.refreshOnAppStartEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                onClick = { onEditorChange(editor.copy(refreshOnAppStartEnabled = !editor.refreshOnAppStartEnabled)) },
            )
        }
    } else {
        item(key = "refresh-none") {
            InfoPanel(
                title = stringResource(R.string.settings_provider_update_title),
                body = stringResource(R.string.settings_provider_file_no_auto),
                badge = stringResource(R.string.settings_epg_badge_manual),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item(key = "user-agent") {
        VivicastSettingsRow(
            title = stringResource(R.string.settings_user_agent),
            help = "",
            value = editor.userAgent.ifBlank { stringResource(R.string.value_app_default) },
            onClick = onOpenUserAgent,
        )
    }
    item(key = "epg-sources") {
        VivicastSettingsRow(
            title = stringResource(R.string.settings_provider_epg_sources),
            help = "",
            value = if (linkedEpgCount > 0) linkedEpgCount.toString() else stringResource(R.string.about_open_value),
            onClick = onOpenEpgSources,
        )
    }
    item(key = "logo-priority") {
        VivicastSettingsRow(
            title = stringResource(R.string.settings_provider_logo_priority),
            help = "",
            value = logoPriorityLabel(editor.logoPriority),
            forceTextValue = true,
            onClick = onOpenLogoPriority,
        )
    }
}

@Composable
internal fun intervalLabel(hours: Int): String =
    if (hours <= REFRESH_INTERVAL_OFF) {
        stringResource(R.string.value_off)
    } else {
        stringResource(R.string.common_hours, hours)
    }

/** Interval picker — behaves like the language picker: focus starts on the current value, selecting a
 * different value saves it, selecting the current one just closes. Scrolls (≈5 rows tall) since the
 * option list is long. */
@Composable
internal fun ProviderIntervalDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentIndex = REFRESH_INTERVAL_OPTIONS_HOURS.indexOf(current).coerceAtLeast(0)
    val listState = rememberLazyListState()
    val selectedFocusRequester = remember { FocusRequester() }
    // Bring the current value into view (it may be far down the list) before focusing it.
    LaunchedEffect(Unit) {
        listState.scrollToItem(currentIndex)
        awaitFrame()
        runCatching { selectedFocusRequester.requestFocus() }
    }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_provider_update_interval),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = INTERVAL_DIALOG_MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
        ) {
            items(REFRESH_INTERVAL_OPTIONS_HOURS) { hours ->
                FocusPanel(
                    selected = hours == current,
                    onClick = { onSelect(hours) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (hours == current) Modifier.focusRequester(selectedFocusRequester) else Modifier),
                ) {
                    BasicText(
                        text = intervalLabel(hours),
                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                    )
                }
            }
        }
    }
}

private val INTERVAL_DIALOG_MAX_HEIGHT = 340.dp

@Composable
internal fun logoPriorityLabel(priority: String): String =
    if (priority == LOGO_PRIORITY_EPG) {
        stringResource(R.string.settings_provider_logo_priority_epg)
    } else {
        stringResource(R.string.settings_provider_logo_priority_playlist)
    }

/** Two-option logo-source picker: prefer the playlist's own logo (default) or a mapped EPG source's icon.
 * Focus starts on the current value; selecting a different value saves it, the current one just closes. */
@Composable
private fun ProviderLogoPriorityDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_provider_logo_priority),
        initialFocus = selectedFocusRequester,
    ) {
        // Panels sit directly in the dialog's Column (spaced by the dialog). wrapContentHeight(unbounded)
        // keeps each row at its text height instead of stretching to fill — mirrors LanguagePickerDialog.
        listOf(LOGO_PRIORITY_PLAYLIST, LOGO_PRIORITY_EPG).forEach { option ->
            FocusPanel(
                selected = option == current,
                onClick = { onSelect(option) },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true)
                    .then(if (option == current) Modifier.focusRequester(selectedFocusRequester) else Modifier),
            ) {
                BasicText(
                    text = logoPriorityLabel(option),
                    style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                )
            }
        }
    }
}

/** Per-playlist User-Agent editor — mirrors the global one; empty saves as "use the global UA". */
@Composable
private fun ProviderUserAgentDialog(
    initialValue: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    val strInvalidChars = stringResource(R.string.settings_ua_invalid_chars)
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_user_agent),
    ) {
        VivicastTextField(
            value = value,
            onValueChange = {
                value = it.take(200)
                error = null
            },
            focusRequester = fieldFocus,
            isError = error != null,
        )
        BodyText(stringResource(R.string.settings_provider_ua_hint), maxLines = 2)
        if (error != null) {
            BodyText(error!!, color = VivicastColors.Error)
        }
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.common_save),
            onPrimary = {
                val trimmed = value.trim()
                if (trimmed.any { it.isISOControl() }) {
                    error = strInvalidChars
                } else {
                    onSave(trimmed)
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
        )
    }
}

/** EPG-source assignment popup — every EPG source with a toggle; toggling links/unlinks immediately. */
@Composable
private fun ProviderEpgSourcesDialog(
    sources: List<EpgSource>,
    linkedIds: Set<String>,
    onToggle: (sourceId: String, link: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_provider_epg_sources),
    ) {
        if (sources.isEmpty()) {
            BodyText(stringResource(R.string.settings_provider_epg_none), maxLines = 2)
        } else {
            sources.forEach { source ->
                val linked = source.id in linkedIds
                VivicastSettingsRow(
                    title = source.name,
                    help = "",
                    value = if (linked) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                    onClick = { onToggle(source.id, !linked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.height(VivicastSpacing.Space2))
        VivicastButtonRow {
            ActionPill(label = stringResource(R.string.common_close), onClick = onDismiss)
        }
    }
}

@Composable
private fun ImportCheckboxRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        selected = checked,
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
    ) { _ ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (checked) VivicastColors.Accent else Color.Transparent,
                        shape = RoundedCornerShape(VivicastShapes.RadiusSmall),
                    )
                    .border(
                        width = if (checked) VivicastBorders.FocusWidth else VivicastBorders.Hairline,
                        color = if (checked) VivicastColors.Accent else VivicastColors.TextSecondary,
                        shape = RoundedCornerShape(VivicastShapes.RadiusSmall),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    BasicText(
                        text = "✓",
                        style = VivicastTypography.LabelMedium.copy(color = Color.White),
                    )
                }
            }
            BasicText(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
            )
        }
    }
}

@Composable
private fun ProviderChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    ) { _ ->
        BasicText(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium.copy(
                color = VivicastColors.TextPrimary,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

