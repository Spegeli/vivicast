package com.vivicast.tv.feature.settings

import androidx.activity.compose.BackHandler
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
import com.vivicast.tv.core.designsystem.LocalVivicastColors
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
import com.vivicast.tv.data.provider.ContentSummary
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderConnectionTestResult
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
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
internal fun ProviderSettingsPanel(
    providers: List<Provider>,
    onGetProviderCredentials: suspend (String) -> ProviderCredentials?,
    onGetProviderM3uContent: suspend (String) -> String? = { null },
    onCreateProvider: suspend (ProviderCreateRequest) -> Result<ProviderSaveResult>,
    onUpdateProvider: suspend (ProviderUpdateRequest) -> Result<ProviderSaveResult>,
    onSetProviderEnabled: suspend (String, Boolean) -> Result<Unit>,
    onDeleteProvider: suspend (String) -> Result<Unit>,
    onTestProviderConnection: suspend (ProviderCreateRequest) -> ProviderConnectionTestResult,
    localLogosConfigured: Boolean = false,
    onPickM3uFile: ((String, String) -> Unit) -> Unit = {},
    onProviderSaved: (String) -> Unit,
    // Sanitized diagnostics on save: source descriptor ("M3U_URL"/"M3U_FILE"/"XTREAM") + the previous type
    // name if the edit switched type (null otherwise). No secrets/URLs.
    onLogProviderSaved: (descriptor: String, switchedFromType: String?) -> Unit = { _, _ -> },
    epgSources: List<EpgSource> = emptyList(),
    providerEpgLinks: List<ProviderEpgSource> = emptyList(),
    onSelectEpgProvider: (String) -> Unit = {},
    onToggleEpgLink: (providerId: String, sourceId: String, link: Boolean) -> Unit = { _, _, _ -> },
    firstFocusModifier: Modifier = Modifier,
    onParkFocusBeforeEditor: () -> Unit = {},
    // Bumped when OK is pressed on the already-selected rail section: collapse the open editor back to
    // the overview. Focus stays on the rail (no park / overview-focus); the draft is discarded.
    collapseSubViewSignal: Int = 0,
) {
    val scope = rememberCoroutineScope()
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(ProviderEditorState.newProvider(ProviderType.M3u)) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }
    var connectionTestStatus by remember { mutableStateOf(ConnectionTestStatus.Idle) }
    // M3U content breakdown from the last passed test — previews the URL just like the file check.
    var connectionSummary by remember { mutableStateOf<ContentSummary?>(null) }
    // Reason a test failed, shown as a red hint under the source fields (never a note).
    var connectionError by remember { mutableStateOf<String?>(null) }
    // Where focus should land once the overview returns after leaving the inline editor.
    var pendingOverviewFocus by remember { mutableStateOf<OverviewFocusTarget?>(null) }
    // Save-time test failure → confirm dialog ("Korrigieren" / force-save). Only on Save, never a note.
    var pendingSaveTestFailure by remember { mutableStateOf(false) }
    // Source confirmation on Save: add mode ("saved as X") or an edit that switches type/mode.
    var pendingSourceConfirm by remember { mutableStateOf(false) }
    // Bumped to make ProviderEditor jump focus to the source field (manual-test fail / "Korrigieren").
    var focusSourceSignal by remember { mutableStateOf(0) }
    // Save in progress (test + write): drives the Save-button spinner; the test button stays idle.
    var saving by remember { mutableStateOf(false) }
    val strProviderSaveFailed = stringResource(R.string.settings_provider_msg_save_failed)
    val strProviderDeleteFailed = stringResource(R.string.settings_provider_msg_delete_failed)
    val strProviderSectionBody = stringResource(R.string.settings_provider_section_body)
    val strValidationNameMissing = stringResource(R.string.validation_name_missing)
    val strValidationContentType = stringResource(R.string.validation_content_type_required)
    val strValidationXtreamServer = stringResource(R.string.validation_xtream_server_missing)
    val strValidationXtreamUser = stringResource(R.string.validation_xtream_username_missing)
    val strValidationXtreamPass = stringResource(R.string.validation_xtream_password_missing)
    val strValidationM3uUrl = stringResource(R.string.validation_m3u_url_missing)
    val strValidationM3uFile = stringResource(R.string.validation_m3u_file_missing)
    fun ProviderEditorState.validationMessageResolved() = validationMessage(
        strValidationNameMissing, strValidationContentType,
        strValidationXtreamServer, strValidationXtreamUser, strValidationXtreamPass,
        strValidationM3uUrl, strValidationM3uFile,
    )
    fun ProviderEditorState.connectionTestRequestMessageResolved() = connectionTestRequestMessage(
        strValidationXtreamServer, strValidationXtreamUser, strValidationXtreamPass,
        strValidationM3uUrl, strValidationM3uFile,
    )

    LaunchedEffect(providers) {
        val selectedProvider = selectedProviderId?.let { id -> providers.firstOrNull { it.id == id } }
        if (selectedProvider == null && selectedProviderId != null) {
            selectedProviderId = null
            editor = ProviderEditorState.newProvider(ProviderType.M3u)
            showEditor = false
        }
    }

    val duplicateName = isDuplicateNameOf(editor.name, editor.providerId, providers, { it.id }, { it.name })

    var existingM3uUrls by remember { mutableStateOf<List<ProviderUrlEntry>>(emptyList()) }
    // Per-provider M3U source mode, for the overview badge ("M3U URL" vs "M3U Datei"). Loaded from the
    // (now cheap) credentials in the same pass as the duplicate-URL index.
    var providerSourceModes by remember { mutableStateOf<Map<String, M3uSourceMode>>(emptyMap()) }
    LaunchedEffect(providers) {
        val credentialsById = providers.associateWith { runCatching { onGetProviderCredentials(it.id) }.getOrNull() }
        existingM3uUrls = credentialsById.mapNotNull { (provider, credentials) ->
            (credentials as? ProviderCredentials.M3u)
                ?.takeIf { it.sourceMode == M3uSourceMode.Url }
                ?.url
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { ProviderUrlEntry(provider.id, normalizeSourceUrl(it), provider.name) }
        }
        providerSourceModes = credentialsById.mapNotNull { (provider, credentials) ->
            (credentials as? ProviderCredentials.M3u)?.let { provider.id to it.sourceMode }
        }.toMap()
    }
    val duplicateUrlName = if (editor.type == ProviderType.M3u &&
        editor.m3uSourceMode == M3uSourceMode.Url && editor.m3uUrl.isNotBlank()
    ) {
        val normalized = normalizeSourceUrl(editor.m3uUrl)
        existingM3uUrls.firstOrNull { it.providerId != editor.providerId && it.url == normalized }?.name
    } else {
        null
    }

    if (!showEditor) Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
        ProviderOverviewPanel(
            providers = providers,
            providerSourceModes = providerSourceModes,
            message = message,
            firstFocusModifier = firstFocusModifier,
            onAddProvider = {
                // Park focus on the (always-present) section button before the overview — and with it
                // the focused add row — is removed, else focus escapes to the top nav bar (jumps Home).
                onParkFocusBeforeEditor()
                selectedProviderId = null
                editor = ProviderEditorState.newProvider(ProviderType.M3u)
                showEditor = true
                message = null
                connectionTestStatus = ConnectionTestStatus.Idle
                connectionSummary = null
                connectionError = null
                focusSourceSignal = 0
                saving = false
            },
            onRefreshAll = {
                scope.launch {
                    // No "any refreshing → skip" guard: enqueuePlaylistRefresh uses KEEP, so a genuinely
                    // in-flight provider coalesces (no-op) while a stuck/idle one still starts. A single
                    // stuck-"Refreshing" provider must not block refreshing everything else.
                    // Every active provider with resolvable credentials — including File playlists, which
                    // re-import their stored content (rebuilds catalog + EPG mappings).
                    val refreshableProviders = providers.filter { provider ->
                        provider.isActive && onGetProviderCredentials(provider.id) != null
                    }
                    refreshableProviders.forEach { provider -> onProviderSaved(provider.id) }
                }
            },
            onOpenProvider = { provider ->
                // Move focus off the card (which is about to be removed) onto a node that survives
                // the swap; otherwise focus escapes to the top nav bar and navigates to Home.
                onParkFocusBeforeEditor()
                selectedProviderId = provider.id
                // Point the EPG-links observation at this provider so the "EPG Quellen" popup shows
                // its current assignments.
                onSelectEpgProvider(provider.id)
                editor = ProviderEditorState.from(provider)
                showEditor = true
                message = null
                connectionTestStatus = ConnectionTestStatus.Idle
                connectionSummary = null
                connectionError = null
                focusSourceSignal = 0
                saving = false
                scope.launch {
                    val credentials = runCatching { onGetProviderCredentials(provider.id) }.getOrNull()
                    if (selectedProviderId == provider.id) {
                        editor = ProviderEditorState.from(provider, credentials)
                    }
                }
            },
            pendingFocus = pendingOverviewFocus,
            onFocusHandled = { pendingOverviewFocus = null },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // OK on the rail section collapses the editor to the overview. Focus stays on the rail (the user is
    // focused there), so unlike dismissEditor this does NOT park / set an overview-focus target. The
    // draft is discarded, matching Cancel/BACK. Initial fire (showEditor == false) is a no-op.
    LaunchedEffect(collapseSubViewSignal) {
        if (showEditor) {
            selectedProviderId = null
            editor = ProviderEditorState.newProvider(ProviderType.M3u)
            showEditor = false
            message = null
            connectionTestStatus = ConnectionTestStatus.Idle
        }
    }

    val dismissEditor: () -> Unit = {
        // Park focus before the inline editor is removed, else focus escapes to the top nav bar.
        onParkFocusBeforeEditor()
        // Return focus to the card just edited, or the add button when adding (no providerId yet).
        pendingOverviewFocus = editor.providerId?.let(OverviewFocusTarget::Card) ?: OverviewFocusTarget.AddButton
        selectedProviderId = null
        editor = ProviderEditorState.newProvider(ProviderType.M3u)
        showEditor = false
        message = null
        connectionTestStatus = ConnectionTestStatus.Idle
    }

    // Writes the editor (update/create), applies the active toggle, closes. Shared by the passed-test
    // save path and the "trotzdem speichern" force-save; assumes the source is already decided.
    suspend fun persistEditor() {
        // Capture the chosen mode before onSuccess resets the editor (for the diagnostics descriptor).
        val savedMode = editor.m3uSourceMode
        val saveResult = if (editor.isEditing) {
            onUpdateProvider(editor.toUpdateRequest())
        } else {
            onCreateProvider(editor.toCreateRequest())
        }
        saveResult
            .onSuccess { saved ->
                val descriptor = when (saved.provider.type) {
                    ProviderType.Xtream -> "XTREAM"
                    ProviderType.M3u -> if (savedMode == M3uSourceMode.File) "M3U_FILE" else "M3U_URL"
                }
                onLogProviderSaved(descriptor, saved.switchedFromType?.name)
                // Apply the deferred active toggle only if it changed.
                if (editor.isActive != saved.provider.isActive) {
                    onSetProviderEnabled(saved.provider.id, editor.isActive)
                }
                onParkFocusBeforeEditor()
                pendingOverviewFocus = OverviewFocusTarget.Card(saved.provider.id)
                selectedProviderId = null
                editor = ProviderEditorState.newProvider(ProviderType.M3u)
                showEditor = false
                connectionTestStatus = ConnectionTestStatus.Idle
                onProviderSaved(saved.provider.id)
                message = null
            }
            .onFailure { error ->
                message = strProviderSaveFailed.format(error.message ?: "?")
            }
    }

    // Runs after validation (+ any source confirmation): a same-source edit persists directly, otherwise
    // the source is tested first and only a passing test persists; a failing test opens the fix/force dialog.
    fun proceedSave() {
        if (editor.isSourceUnchanged) {
            scope.launch {
                message = null
                saving = true
                persistEditor()
                saving = false
            }
        } else {
            scope.launch {
                message = null
                saving = true
                val result = onTestProviderConnection(editor.resolveTestRequest(onGetProviderM3uContent))
                if (result.errorMessage == null) {
                    connectionSummary = result.summary
                    connectionError = null
                    persistEditor()
                } else {
                    connectionTestStatus = ConnectionTestStatus.Failed
                    connectionSummary = null
                    connectionError = result.errorMessage
                    pendingSaveTestFailure = true
                }
                saving = false
            }
        }
    }

    if (showEditor) {
        BackHandler(onBack = dismissEditor)
        ProviderEditor(
            editor = editor,
            duplicates = ProviderDuplicateInfo(duplicateName, duplicateUrlName),
            isDuplicateName = { candidate ->
                isDuplicateNameOf(candidate, editor.providerId, providers, { it.id }, { it.name })
            },
            message = message,
            connectionTestStatus = connectionTestStatus,
            connectionSummary = connectionSummary,
            connectionError = connectionError.takeIf { connectionTestStatus == ConnectionTestStatus.Failed },
            signals = ProviderEditorSignals(focusSource = focusSourceSignal, saving = saving),
            entryFocusModifier = firstFocusModifier,
            actions = ProviderEditorActions(
            onEditorChange = {
                editor = it
                message = null
                connectionTestStatus = ConnectionTestStatus.Idle
                connectionSummary = null
            },
            onTestConnection = {
                // Test only checks the connection — name/URL duplicates are irrelevant here (they are
                // flagged inline on the fields and enforced at Save). Ignore repeat taps while running.
                if (connectionTestStatus != ConnectionTestStatus.Testing) {
                    val validationMessage = editor.connectionTestRequestMessageResolved()
                    if (validationMessage != null) {
                        message = validationMessage
                    } else {
                        message = null
                        connectionTestStatus = ConnectionTestStatus.Testing
                        val editorSnapshot = editor
                        scope.launch {
                            val request = editorSnapshot.resolveTestRequest(onGetProviderM3uContent)
                            val result = onTestProviderConnection(request)
                            if (result.errorMessage == null) {
                                connectionTestStatus = ConnectionTestStatus.Passed
                                connectionSummary = result.summary
                                connectionError = null
                                message = null
                            } else {
                                connectionTestStatus = ConnectionTestStatus.Failed
                                connectionSummary = null
                                connectionError = result.errorMessage
                                message = null
                                // Manual test: jump focus to the source field (save uses a dialog instead).
                                focusSourceSignal++
                            }
                        }
                    }
                }
            },
            onSave = {
                // Duplicates are guarded in ProviderEditor. After validation, a confirmation is shown when
                // adding (informational "saved as X") or when an edit switches the source type/mode
                // (destructive warning). Same-type/mode edits skip straight to proceedSave. Ignore while testing.
                when {
                    saving || connectionTestStatus == ConnectionTestStatus.Testing -> Unit
                    else -> {
                        val validationMessage = editor.validationMessageResolved()
                        when {
                            validationMessage != null -> message = validationMessage
                            !editor.isEditing || editor.sourceSwitched -> {
                                message = null
                                pendingSourceConfirm = true
                            }
                            else -> proceedSave()
                        }
                    }
                }
            },
            onCancel = dismissEditor,
            onToggleEnabled = {
                // Draft only — the active state is applied on Save, not immediately.
                editor = editor.copy(isActive = !editor.isActive)
            },
            onDelete = {
                pendingDelete = providers.firstOrNull { it.id == editor.providerId }
            },
            onPickM3uFile = onPickM3uFile,
            onToggleEpgLink = { sourceId, link ->
                selectedProviderId?.let { onToggleEpgLink(it, sourceId, link) }
            },
            ),
            epgLinks = ProviderEpgLinkInfo(
                sources = epgSources,
                linkedIds = providerEpgLinks.mapTo(mutableSetOf()) { it.epgSourceId },
            ),
            localLogosConfigured = localLogosConfigured,
            modifier = Modifier.fillMaxSize(),
        )
    }

    pendingDelete?.let { provider ->
        DeleteProviderDialog(
            provider = provider,
            onCancel = { pendingDelete = null },
            onDelete = {
                scope.launch {
                    onDeleteProvider(provider.id)
                        .onSuccess {
                            onParkFocusBeforeEditor()
                            // Focus the next playlist (or the previous one), or the add button if none remain.
                            val deletedIndex = providers.indexOfFirst { it.id == provider.id }
                            val neighborId = providers.getOrNull(deletedIndex + 1)?.id
                                ?: providers.getOrNull(deletedIndex - 1)?.id
                            pendingOverviewFocus = neighborId?.let(OverviewFocusTarget::Card)
                                ?: OverviewFocusTarget.AddButton
                            pendingDelete = null
                            selectedProviderId = null
                            editor = ProviderEditorState.newProvider(ProviderType.M3u)
                            showEditor = false
                            // No "deleted" note — the playlist disappearing is feedback enough.
                            message = null
                        }
                        .onFailure { error ->
                            pendingDelete = null
                            message = strProviderDeleteFailed.format(error.message ?: "?")
                        }
                }
            },
        )
    }

    if (pendingSourceConfirm) {
        ProviderSourceConfirmDialog(
            isSwitch = editor.sourceSwitched,
            targetLabel = providerSourceLabel(editor.type, editor.m3uSourceMode),
            originalLabel = editor.originalType?.let {
                providerSourceLabel(it, editor.originalSourceMode ?: M3uSourceMode.Url)
            },
            onConfirm = {
                pendingSourceConfirm = false
                proceedSave()
            },
            onCancel = { pendingSourceConfirm = false },
        )
    }

    if (pendingSaveTestFailure) {
        ProviderConnectionFailedDialog(
            reason = connectionError,
            isActive = editor.isActive,
            onCorrect = {
                pendingSaveTestFailure = false
                focusSourceSignal++
            },
            onSaveAnyway = {
                pendingSaveTestFailure = false
                // A broken source is never saved active — force-save always deactivates.
                editor = editor.copy(isActive = false)
                scope.launch {
                    saving = true
                    persistEditor()
                    saving = false
                }
            },
        )
    }
}

@Composable
private fun ProviderOverviewPanel(
    providers: List<Provider>,
    providerSourceModes: Map<String, M3uSourceMode>,
    message: String?,
    firstFocusModifier: Modifier,
    onAddProvider: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenProvider: (Provider) -> Unit,
    pendingFocus: OverviewFocusTarget?,
    onFocusHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addRequester = remember { FocusRequester() }
    val cardRequesters = remember(providers) { providers.associate { it.id to FocusRequester() } }
    // When returning from the inline editor, move focus onto the requested card (or the add button).
    LaunchedEffect(pendingFocus, providers) {
        val target = pendingFocus ?: return@LaunchedEffect
        awaitFrame()
        val requester = when (target) {
            is OverviewFocusTarget.Card -> cardRequesters[target.providerId] ?: addRequester
            OverviewFocusTarget.AddButton -> addRequester
        }
        runCatching { requester.requestFocus() }
        onFocusHandled()
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_playlist_add),
                help = stringResource(R.string.settings_provider_help_add),
                value = stringResource(R.string.about_open_value),
                modifier = firstFocusModifier
                    .focusRequester(addRequester)
                    .testTag("settings-playlist-add-action")
                    ,
                onClick = onAddProvider,
            )
        }
        // Nothing to refresh without a playlist, so hide the action until one exists.
        if (providers.isNotEmpty()) {
            item {
                VivicastSettingsRow(
                    title = stringResource(R.string.settings_playlist_refresh_all),
                    help = stringResource(R.string.settings_provider_help_refresh_all),
                    value = stringResource(R.string.settings_playlist_refresh_value),
                    onClick = onRefreshAll,
                )
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
        if (providers.isEmpty()) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_playlist_empty_title),
                    body = stringResource(R.string.settings_playlist_empty_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(providers, key = { it.id }) { provider ->
                ProviderSourceCard(
                    provider = provider,
                    sourceMode = providerSourceModes[provider.id] ?: M3uSourceMode.Url,
                    onClick = { onOpenProvider(provider) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(cardRequesters.getValue(provider.id)),
                )
            }
        }
    }
}

@Composable
private fun ProviderSourceCard(
    provider: Provider,
    sourceMode: M3uSourceMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        contentPadding = VivicastSpacing.Space4,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = provider.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = VivicastTypography.LabelLarge,
                    )
                    StatusBadge(provider.status.localizedLabel(), tone = provider.status.tone)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    StatusBadge(providerSourceLabel(provider.type, sourceMode))
                    StatusBadge(stringResource(R.string.nav_live_tv), tone = if (provider.includeLiveTv) LocalVivicastColors.current.accent else VivicastColors.SurfaceHigh)
                    StatusBadge(stringResource(R.string.nav_movies_label), tone = if (provider.includeMovies) LocalVivicastColors.current.accent else VivicastColors.SurfaceHigh)
                    StatusBadge(stringResource(R.string.nav_series_label), tone = if (provider.includeSeries) LocalVivicastColors.current.accent else VivicastColors.SurfaceHigh)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                BodyText(stringResource(R.string.settings_provider_updated_format, provider.updatedAt.toBackupTimestamp()), maxLines = 1)
                // Xtream account info (Detailinformationen, design screen 08) — shown only when known.
                provider.xtreamExpiresAtMillis?.let { expiresAt ->
                    BodyText(stringResource(R.string.settings_provider_expiry_format, expiresAt.toBackupTimestamp()), maxLines = 1)
                }
                provider.xtreamMaxConnections?.let { maxConnections ->
                    BodyText(stringResource(R.string.settings_provider_max_connections_format, maxConnections), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ProviderList(
    providers: List<Provider>,
    selectedProviderId: String?,
    onSelectProvider: (Provider) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (providers.isEmpty()) {
        InfoPanel(
            title = stringResource(R.string.common_no_playlists),
            body = stringResource(R.string.settings_provider_empty_body),
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        items(providers, key = { it.id }) { provider ->
            FocusPanel(
                selected = provider.id == selectedProviderId,
                onClick = { onSelectProvider(provider) },
                onFocused = { onSelectProvider(provider) },
                modifier = Modifier.fillMaxWidth().height(116.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            text = provider.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = VivicastTypography.LabelLarge,
                        )
                        Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
                        StatusBadge(provider.type.label)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                        StatusBadge(provider.status.localizedLabel(), tone = provider.status.tone)
                    }
                    BodyText(provider.importSummary(), maxLines = 1)
                }
            }
        }
    }
}

private data class ProviderUrlEntry(val providerId: String?, val url: String, val name: String)

/** Where focus should land in the provider overview after the inline editor closes. */
private sealed interface OverviewFocusTarget {
    data class Card(val providerId: String) : OverviewFocusTarget
    data object AddButton : OverviewFocusTarget
}

internal val ProviderType.label: String
    get() = when (this) {
        ProviderType.M3u -> "M3U"
        ProviderType.Xtream -> "Xtream"
    }

@Composable
internal fun ProviderStatus.localizedLabel(): String = when (this) {
    ProviderStatus.Active -> stringResource(R.string.livetv_status_active)
    ProviderStatus.ActiveWithPartialErrors -> stringResource(R.string.livetv_status_active_partial)
    ProviderStatus.Refreshing -> stringResource(R.string.livetv_status_refreshing)
    ProviderStatus.ConnectionError -> stringResource(R.string.livetv_status_connection_error)
    ProviderStatus.InvalidCredentials -> stringResource(R.string.livetv_status_invalid_credentials)
    ProviderStatus.Expired -> stringResource(R.string.livetv_status_expired)
    ProviderStatus.Disabled -> stringResource(R.string.livetv_status_disabled)
    ProviderStatus.CredentialsRequired -> stringResource(R.string.livetv_status_credentials_required)
}

private val ProviderStatus.tone: Color
    get() = when (this) {
        ProviderStatus.Active -> VivicastColors.Success
        ProviderStatus.ActiveWithPartialErrors -> VivicastColors.Warning
        ProviderStatus.Refreshing -> VivicastColors.Warning
        ProviderStatus.ConnectionError -> VivicastColors.Warning
        ProviderStatus.InvalidCredentials -> VivicastColors.Error
        ProviderStatus.Expired -> VivicastColors.Warning
        ProviderStatus.Disabled -> VivicastColors.Warning
        ProviderStatus.CredentialsRequired -> VivicastColors.Error
    }

@Composable
private fun Provider.importSummary(): String = listOfNotNull(
    stringResource(R.string.nav_live_tv).takeIf { includeLiveTv },
    stringResource(R.string.nav_movies_label).takeIf { includeMovies },
    stringResource(R.string.nav_series_label).takeIf { includeSeries },
).takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: stringResource(R.string.settings_provider_no_content)
