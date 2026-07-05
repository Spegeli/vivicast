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
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourcePriorityDirection
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.M3uContentSummary
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
    duplicateName: Boolean,
    duplicateUrlName: String?,
    message: String?,
    connectionTestStatus: ConnectionTestStatus,
    connectionSummary: M3uContentSummary?,
    connectionError: String?,
    actions: ProviderEditorActions,
    modifier: Modifier = Modifier,
) {
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
    val listState = rememberLazyListState()
    // Inline panel (no dialog window): wait one frame so the field is placed before requesting focus,
    // otherwise focus escapes to the top nav bar and navigates away.
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }
    val focusByError = remember {
        mapOf(
            ProviderEditorErrorFocus.Name to firstFocus, ProviderEditorErrorFocus.Url to urlFocus,
            ProviderEditorErrorFocus.File to fileFocus, ProviderEditorErrorFocus.Server to serverFocus,
            ProviderEditorErrorFocus.User to userFocus, ProviderEditorErrorFocus.Pass to passFocus,
            ProviderEditorErrorFocus.Import to importFocus,
        )
    }
    // A blocked action moves focus to the first bad field (and reddens it) instead of showing a note.
    var pendingErrorFocus by remember { mutableStateOf<ProviderEditorErrorFocus?>(null) }
    LaunchedEffect(pendingErrorFocus) {
        val target = pendingErrorFocus ?: return@LaunchedEffect
        // Name/URL sit at the top; scroll there first so the field is composed before focusing.
        if (target == ProviderEditorErrorFocus.Name || target == ProviderEditorErrorFocus.Url) {
            listState.scrollToItem(0)
        }
        awaitFrame()
        runCatching { focusByError[target]?.requestFocus() }
        pendingErrorFocus = null
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
        val blankFocus = if (editor.type == ProviderType.Xtream) {
            firstBlankXtreamFocus(serverBlank, userBlank, passBlank)
        } else {
            sourceBlankFocus(urlBlank, fileBlank)
        }
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
        // Name field stays the first item with a stable key so inserting the duplicate warnings below
        // it never re-creates the focused field (which would let focus escape to the top nav bar).
        item(key = "name") {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                ProviderTextField(
                    label = stringResource(R.string.settings_provider_name_label),
                    value = editor.name,
                    placeholder = stringResource(R.string.settings_provider_name_placeholder),
                    onValueChange = { onEditorChange(editor.copy(name = it)) },
                    focusRequester = firstFocus,
                    isError = duplicateName || (showNameBlankError && nameBlank),
                    maxLength = 25,
                )
                // Red inline error (no separate hint panel), rendered in the same item so the focused
                // field stays mounted and keeps focus while the duplicate check flips.
                VivicastDialogError(
                    if (duplicateName) stringResource(R.string.settings_provider_name_exists_body) else null,
                )
            }
        }

        providerSourceChoiceItem(editor, onEditorChange)

        when (editor.type) {
            ProviderType.M3u -> providerM3uCredentialItems(
                editor = editor,
                duplicateUrlName = duplicateUrlName,
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
                connectionTestStatus = connectionTestStatus,
                onTestClick = onTestClick,
                onEditorChange = onEditorChange,
            )
        }

        // Failed-test reason as a red hint (no note), shown just under the source fields.
        if (connectionError != null) {
            item(key = "conn-error") {
                VivicastDialogError(connectionError)
            }
        }

        providerImportItem(editor, importFocus, onEditorChange)

        // Refresh interval is an edit-only control; a new playlist uses the default until saved.
        if (editor.isEditing) {
            providerRefreshItems(editor, onEditorChange)
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
                ActionPill(label = stringResource(R.string.common_cancel), onClick = onCancel)
                ActionPill(
                    label = stringResource(R.string.common_save),
                    onClick = {
                        // A blocked save jumps focus to the first bad field (name → URL → import) and
                        // reddens it, instead of the real save raising a note.
                        val error = firstSaveError(editor, duplicateName, duplicateUrlName, nameBlank, urlBlank, fileBlank, serverBlank, userBlank, passBlank)
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
                    ActionPill(label = stringResource(R.string.settings_provider_toggle_enabled), onClick = onToggleEnabled)
                    ActionPill(label = stringResource(R.string.settings_delete), onClick = onDelete)
                }
            }
        }
    }
}

/** Callbacks the inline provider editor raises. Bundled so the composable stays under the arg limit. */
internal class ProviderEditorActions(
    val onEditorChange: (ProviderEditorState) -> Unit,
    val onTestConnection: () -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val onToggleEnabled: () -> Unit,
    val onDelete: () -> Unit,
    val onPickM3uFile: ((String, String) -> Unit) -> Unit,
)

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
                    onClick = { onEditorChange(editor.copy(type = ProviderType.M3u, m3uSourceMode = M3uSourceMode.Url, connectionTestPassed = false)) },
                )
                ProviderChoiceButton(
                    label = "M3U " + stringResource(R.string.m3u_source_file),
                    selected = editor.type == ProviderType.M3u && editor.m3uSourceMode == M3uSourceMode.File,
                    onClick = { onEditorChange(editor.copy(type = ProviderType.M3u, m3uSourceMode = M3uSourceMode.File, connectionTestPassed = false)) },
                )
                ProviderChoiceButton(
                    label = "Xtream Codes",
                    selected = editor.type == ProviderType.Xtream,
                    onClick = { onEditorChange(editor.copy(type = ProviderType.Xtream, connectionTestPassed = false)) },
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
    contentSummary: M3uContentSummary?,
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
                                        connectionTestPassed = false,
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
                            onEditorChange(editor.copy(m3uUrl = it, m3uHasExistingSource = false, connectionTestPassed = false))
                        },
                        modifier = Modifier.weight(1f),
                        focusRequester = sourceFocus.url,
                        secret = editor.isEditing,
                        isError = duplicateUrlName != null || sourceBlankError,
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
    connectionTestStatus: ConnectionTestStatus,
    onTestClick: () -> Unit,
    onEditorChange: (ProviderEditorState) -> Unit,
) {
    item(key = "xtream-server") {
        ProviderTextField(
            label = stringResource(R.string.settings_provider_server_label),
            value = editor.xtreamServerUrl,
            placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else "https://server.example",
            onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it, connectionTestPassed = false)) },
            focusRequester = fields.serverFocus,
            secret = editor.isEditing,
            isError = fields.serverError,
            maxLength = 250,
        )
    }
    item(key = "xtream-user") {
        ProviderTextField(
            label = stringResource(R.string.settings_provider_username_label),
            value = editor.xtreamUsername,
            placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_username_label),
            onValueChange = { onEditorChange(editor.copy(xtreamUsername = it, connectionTestPassed = false)) },
            focusRequester = fields.userFocus,
            secret = editor.isEditing,
            isError = fields.userError,
            maxLength = 100,
        )
    }
    item(key = "xtream-pass") {
        ProviderTextField(
            label = stringResource(R.string.settings_provider_password_label),
            value = editor.xtreamPassword,
            placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_password_label),
            onValueChange = { onEditorChange(editor.copy(xtreamPassword = it, connectionTestPassed = false)) },
            focusRequester = fields.passFocus,
            secret = true,
            allowReveal = true,
            isError = fields.passError,
            maxLength = 100,
        )
    }
    item(key = "xtream-test") {
        VivicastButtonRow {
            ConnectionTestButton(status = connectionTestStatus, onClick = onTestClick)
        }
    }
}

@Composable
private fun ProviderM3uFilePicker(
    editor: ProviderEditorState,
    blankError: Boolean,
    fileFocusRequester: FocusRequester,
    contentSummary: M3uContentSummary?,
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
                                connectionTestPassed = false,
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
private fun ProviderContentSummary(summary: M3uContentSummary?) {
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
                    onToggle = { onEditorChange(editor.copy(includeLiveTv = !editor.includeLiveTv, connectionTestPassed = false)) },
                    modifier = Modifier.weight(1f).focusRequester(importFocusRequester),
                )
                ImportCheckboxRow(
                    label = stringResource(R.string.nav_movies_label),
                    checked = editor.includeMovies,
                    onToggle = { onEditorChange(editor.copy(includeMovies = !editor.includeMovies, connectionTestPassed = false)) },
                    modifier = Modifier.weight(1f),
                )
                ImportCheckboxRow(
                    label = stringResource(R.string.nav_series_label),
                    checked = editor.includeSeries,
                    onToggle = { onEditorChange(editor.copy(includeSeries = !editor.includeSeries, connectionTestPassed = false)) },
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

/** Auto-refresh interval adjuster, or a manual-only note for source types that can't auto-refresh. */
private fun LazyListScope.providerRefreshItems(
    editor: ProviderEditorState,
    onEditorChange: (ProviderEditorState) -> Unit,
) {
    if (editor.isAutomaticallyRefreshable) {
        item(key = "refresh") {
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
                        BasicText(stringResource(R.string.settings_provider_interval_label), style = VivicastTypography.LabelLarge)
                        BodyText(stringResource(R.string.settings_provider_body_refresh), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill("-6h", modifier = Modifier.width(88.dp), onClick = {
                            onEditorChange(editor.copy(refreshIntervalHours = (editor.refreshIntervalHours - 6).coerceAtLeast(1)))
                        })
                        BasicText("${editor.refreshIntervalHours} h", style = VivicastTypography.LabelLarge)
                        ActionPill("+6h", modifier = Modifier.width(88.dp), onClick = {
                            onEditorChange(editor.copy(refreshIntervalHours = (editor.refreshIntervalHours + 6).coerceAtMost(168)))
                        })
                    }
                }
            }
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
}

internal data class ProviderEditorState(
    val providerId: String?,
    val type: ProviderType,
    val name: String,
    val m3uSourceMode: M3uSourceMode,
    val m3uUrl: String,
    val m3uContent: String,
    val m3uHasExistingSource: Boolean,
    val xtreamServerUrl: String,
    val xtreamUsername: String,
    val xtreamPassword: String,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
    val connectionTestPassed: Boolean,
    val m3uFileName: String = "",
) {
    val isEditing: Boolean get() = providerId != null
    val isAutomaticallyRefreshable: Boolean
        get() = type == ProviderType.Xtream || (type == ProviderType.M3u && m3uSourceMode.isAutomaticallyRefreshable)

    fun validationMessage(
        requireConnectionTest: Boolean,
        msgNameMissing: String,
        msgContentType: String,
        msgXtreamServer: String,
        msgXtreamUser: String,
        msgXtreamPass: String,
        msgConnTest: String,
        msgM3uUrl: String,
        msgM3uFile: String,
    ): String? {
        if (name.isBlank()) return msgNameMissing
        // Every source type imports selectable content now (M3U classifies too), and the repository
        // requires at least one type for all providers — surface that in the UI up front.
        if (!includeLiveTv && !includeMovies && !includeSeries) return msgContentType
        when (type) {
            ProviderType.M3u -> m3uSourceValidationMessage(allowExistingSource = isEditing, msgM3uUrl, msgM3uFile)?.let { return it }
            ProviderType.Xtream -> if (!isEditing) {
                if (xtreamServerUrl.isBlank()) return msgXtreamServer
                if (xtreamUsername.isBlank()) return msgXtreamUser
                if (xtreamPassword.isBlank()) return msgXtreamPass
            }
        }
        if (requireConnectionTest && !connectionTestPassed) return msgConnTest
        return null
    }

    // Connection test is independent of the name (blank or duplicate) — it only needs the
    // connection-relevant fields (M3U URL/file content, or Xtream server/user/password).
    fun connectionTestRequestMessage(
        msgXtreamServer: String,
        msgXtreamUser: String,
        msgXtreamPass: String,
        msgM3uUrl: String,
        msgM3uFile: String,
    ): String? =
        when (type) {
            ProviderType.M3u -> m3uSourceValidationMessage(allowExistingSource = false, msgM3uUrl, msgM3uFile)
            ProviderType.Xtream -> when {
                xtreamServerUrl.isBlank() -> msgXtreamServer
                xtreamUsername.isBlank() -> msgXtreamUser
                xtreamPassword.isBlank() -> msgXtreamPass
                else -> null
            }
        }

    fun toConnectionTestRequest(): ProviderCreateRequest =
        ProviderCreateRequest(
            name = name,
            type = type,
            m3uSourceMode = m3uSourceMode,
            m3uUrl = m3uUrl,
            m3uContent = m3uContent,
            xtreamServerUrl = xtreamServerUrl,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword,
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
        )

    fun toCreateRequest(): ProviderCreateRequest =
        ProviderCreateRequest(
            name = name,
            type = type,
            m3uSourceMode = m3uSourceMode,
            m3uUrl = m3uUrl,
            m3uContent = m3uContent,
            xtreamServerUrl = xtreamServerUrl,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword,
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
        )

    fun toUpdateRequest(): ProviderUpdateRequest =
        ProviderUpdateRequest(
            providerId = requireNotNull(providerId),
            name = name,
            m3uSourceMode = if (type == ProviderType.M3u && shouldReplaceM3uSource) m3uSourceMode else null,
            m3uUrl = m3uUrl.ifBlank { null },
            m3uContent = m3uContent.ifBlank { null },
            xtreamServerUrl = xtreamServerUrl.ifBlank { null },
            xtreamUsername = xtreamUsername.ifBlank { null },
            xtreamPassword = xtreamPassword.ifBlank { null },
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
        )

    private val shouldReplaceM3uSource: Boolean
        get() = !m3uHasExistingSource || m3uUrl.isNotBlank() || m3uContent.isNotBlank()

    private fun m3uSourceValidationMessage(allowExistingSource: Boolean, msgUrl: String, msgFile: String): String? {
        if (allowExistingSource && m3uHasExistingSource && m3uUrl.isBlank() && m3uContent.isBlank()) return null
        return when (m3uSourceMode) {
            M3uSourceMode.Url -> if (m3uUrl.isBlank()) msgUrl else null
            M3uSourceMode.File -> if (m3uContent.isBlank()) msgFile else null
        }
    }

    companion object {
        fun newProvider(type: ProviderType): ProviderEditorState =
            ProviderEditorState(
                providerId = null,
                type = type,
                name = "",
                m3uSourceMode = M3uSourceMode.Url,
                m3uUrl = "",
                m3uContent = "",
                m3uHasExistingSource = false,
                xtreamServerUrl = "",
                xtreamUsername = "",
                xtreamPassword = "",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
                refreshIntervalHours = DEFAULT_REFRESH_INTERVAL_HOURS,
                connectionTestPassed = false,
            )

        fun from(provider: Provider, credentials: ProviderCredentials? = null): ProviderEditorState =
            ProviderEditorState(
                providerId = provider.id,
                type = provider.type,
                name = provider.name,
                m3uSourceMode = (credentials as? ProviderCredentials.M3u)?.sourceMode ?: M3uSourceMode.Url,
                m3uUrl = "",
                m3uContent = "",
                m3uHasExistingSource = credentials is ProviderCredentials.M3u,
                xtreamServerUrl = "",
                xtreamUsername = "",
                xtreamPassword = "",
                includeLiveTv = provider.includeLiveTv,
                includeMovies = provider.includeMovies,
                includeSeries = provider.includeSeries,
                refreshIntervalHours = provider.refreshIntervalHours,
                connectionTestPassed = true,
            )
    }
}

@get:StringRes
internal val M3uSourceMode.labelRes: Int
    get() = when (this) {
        M3uSourceMode.Url -> R.string.m3u_source_url
        M3uSourceMode.File -> R.string.m3u_source_file
    }

internal enum class ConnectionTestStatus { Idle, Testing, Passed, Failed }

private enum class ProviderEditorErrorFocus { Name, Url, File, Server, User, Pass, Import }

/** The URL and file field focus requesters, bundled to keep the credential list under the arg limit. */
private class M3uSourceFocus(val url: FocusRequester, val file: FocusRequester)

/** True when the given M3U source's field is empty and required (not an edit that keeps its source). */
private fun ProviderEditorState.isSourceBlank(mode: M3uSourceMode): Boolean {
    if (type != ProviderType.M3u || m3uSourceMode != mode) return false
    if (isEditing && m3uHasExistingSource) return false
    return when (mode) {
        M3uSourceMode.Url -> m3uUrl.isBlank()
        M3uSourceMode.File -> m3uContent.isBlank()
    }
}

/** First blocking field error on save (name → URL/file → import), or null when the form may be saved. */
private fun firstSaveError(
    editor: ProviderEditorState,
    duplicateName: Boolean,
    duplicateUrlName: String?,
    nameBlank: Boolean,
    urlBlank: Boolean,
    fileBlank: Boolean,
    serverBlank: Boolean,
    userBlank: Boolean,
    passBlank: Boolean,
): ProviderEditorErrorFocus? {
    // Xtream credentials are required only when creating; an edit keeps its stored credentials.
    val xtreamRequired = !editor.isEditing
    return when {
        duplicateName || nameBlank -> ProviderEditorErrorFocus.Name
        duplicateUrlName != null || urlBlank -> ProviderEditorErrorFocus.Url
        fileBlank -> ProviderEditorErrorFocus.File
        xtreamRequired && serverBlank -> ProviderEditorErrorFocus.Server
        xtreamRequired && userBlank -> ProviderEditorErrorFocus.User
        xtreamRequired && passBlank -> ProviderEditorErrorFocus.Pass
        !editor.includeLiveTv && !editor.includeMovies && !editor.includeSeries -> ProviderEditorErrorFocus.Import
        else -> null
    }
}

/** Blank source field to jump to when testing (URL or file), or null when the test may run. */
private fun sourceBlankFocus(urlBlank: Boolean, fileBlank: Boolean): ProviderEditorErrorFocus? = when {
    urlBlank -> ProviderEditorErrorFocus.Url
    fileBlank -> ProviderEditorErrorFocus.File
    else -> null
}

/** Xtream fields for the credential list, bundled to keep its arg list under the limit. */
private class XtreamFieldState(
    val serverError: Boolean,
    val userError: Boolean,
    val passError: Boolean,
    val serverFocus: FocusRequester,
    val userFocus: FocusRequester,
    val passFocus: FocusRequester,
)

private enum class XtreamField { Server, User, Pass }

private fun xtreamFieldState(
    showSourceBlankError: Boolean,
    serverBlank: Boolean,
    userBlank: Boolean,
    passBlank: Boolean,
    serverFocus: FocusRequester,
    userFocus: FocusRequester,
    passFocus: FocusRequester,
): XtreamFieldState = XtreamFieldState(
    serverError = showSourceBlankError && serverBlank,
    userError = showSourceBlankError && userBlank,
    passError = showSourceBlankError && passBlank,
    serverFocus = serverFocus,
    userFocus = userFocus,
    passFocus = passFocus,
)

private fun ProviderEditorState.isXtreamFieldBlank(field: XtreamField): Boolean {
    if (type != ProviderType.Xtream) return false
    return when (field) {
        XtreamField.Server -> xtreamServerUrl.isBlank()
        XtreamField.User -> xtreamUsername.isBlank()
        XtreamField.Pass -> xtreamPassword.isBlank()
    }
}

/** First blank Xtream credential to jump to on test (server → user → password), or null. */
private fun firstBlankXtreamFocus(
    serverBlank: Boolean,
    userBlank: Boolean,
    passBlank: Boolean,
): ProviderEditorErrorFocus? = when {
    serverBlank -> ProviderEditorErrorFocus.Server
    userBlank -> ProviderEditorErrorFocus.User
    passBlank -> ProviderEditorErrorFocus.Pass
    else -> null
}

@Composable
private fun ConnectionTestButton(
    status: ConnectionTestStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor: Color? = when (status) {
        ConnectionTestStatus.Passed -> VivicastColors.Success
        ConnectionTestStatus.Failed -> VivicastColors.Error
        else -> null
    }
    val glyph: String? = when (status) {
        ConnectionTestStatus.Passed -> "✓"
        ConnectionTestStatus.Failed -> "✗"
        else -> null
    }
    val labelRes = when (status) {
        ConnectionTestStatus.Testing -> R.string.settings_provider_msg_checking
        ConnectionTestStatus.Passed -> R.string.settings_provider_test_ok
        ConnectionTestStatus.Failed -> R.string.settings_provider_test_fail
        else -> R.string.settings_provider_test_connection
    }
    FocusPanel(
        onClick = onClick,
        modifier = modifier
            .width(175.dp)
            .then(
                if (statusColor != null) {
                    Modifier.border(VivicastBorders.FocusWidth, statusColor, VivicastShapes.CardRadius)
                } else {
                    Modifier
                },
            ),
    ) { _ ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (glyph != null) {
                BasicText(
                    text = glyph,
                    style = VivicastTypography.LabelLarge.copy(color = statusColor ?: VivicastColors.TextPrimary),
                )
            }
            BasicText(
                text = stringResource(labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(color = statusColor ?: VivicastColors.TextPrimary),
            )
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

