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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.ContentSummary
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderConnectionTestResult
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
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

// ---------------------------------------------------------------------------------------------------------
// Playlists sub-views as self-contained inner-nav destination screens (D2). Split out of the former single
// ProviderSettingsPanel: each owns its own state; the inner NavHost's back stack replaces the old shared
// showEditor/showGroups/actionsProviderId booleans + focus-park + collapseSubViewSignal. Focus-return to the
// originating overview card is carried as a nav result (see SettingsRoute's PlaylistsGraph wiring).
// ---------------------------------------------------------------------------------------------------------

/** Nav-result sentinel: overview should focus the "Add playlist" row (after an add, or deleting the last). */
internal const val OVERVIEW_FOCUS_ADD = "__add_button__"

@Composable
internal fun ProviderOverviewScreen(
    providers: List<Provider>,
    epgSources: List<EpgSource>,
    onGetProviderCredentials: suspend (String) -> ProviderCredentials?,
    onProviderSaved: (String) -> Unit,
    onAddProvider: () -> Unit,
    onOpenProvider: (String) -> Unit,
    pendingFocusProviderId: String?,
    onFocusHandled: () -> Unit,
    firstFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var providerSourceModes by remember { mutableStateOf<Map<String, M3uSourceMode>>(emptyMap()) }
    LaunchedEffect(providers) {
        providerSourceModes = providers
            .associateWith { runCatching { onGetProviderCredentials(it.id) }.getOrNull() }
            .mapNotNull { (provider, credentials) ->
                (credentials as? ProviderCredentials.M3u)?.let { provider.id to it.sourceMode }
            }
            .toMap()
    }
    // Focus-return target when popping back from actions/editor (null on a fresh section entry, so focus stays
    // on the rail until RIGHT). OVERVIEW_FOCUS_ADD => the add row; any other id => that provider's card.
    val pendingFocus = remember(pendingFocusProviderId) {
        when (pendingFocusProviderId) {
            null -> null
            OVERVIEW_FOCUS_ADD -> OverviewFocusTarget.AddButton
            else -> OverviewFocusTarget.Card(pendingFocusProviderId)
        }
    }
    ProviderOverviewPanel(
        providers = providers,
        providerSourceModes = providerSourceModes,
        message = null,
        firstFocusModifier = firstFocusModifier,
        onAddProvider = onAddProvider,
        onRefreshAll = {
            scope.launch {
                providers
                    .filter { it.isActive && onGetProviderCredentials(it.id) != null }
                    .forEach { onProviderSaved(it.id) }
            }
        },
        globalRefreshLabel = when {
            providers.any { it.status == ProviderStatus.Refreshing } ->
                stringResource(R.string.settings_provider_action_refreshing_playlist)
            epgSources.any { it.isRefreshing } ->
                stringResource(R.string.settings_provider_action_refreshing_epg)
            else -> null
        },
        onOpenProvider = { onOpenProvider(it.id) },
        pendingFocus = pendingFocus,
        onFocusHandled = onFocusHandled,
        modifier = modifier,
    )
}

@Composable
internal fun ProviderActionsScreen(
    providerId: String,
    providers: List<Provider>,
    epgSources: List<EpgSource>,
    providerEpgLinks: List<ProviderEpgSource>,
    onGetProviderCredentials: suspend (String) -> ProviderCredentials?,
    onGetProviderM3uContent: suspend (String) -> String?,
    onTestProviderConnection: suspend (ProviderCreateRequest) -> ProviderConnectionTestResult,
    onRefreshProvider: (String) -> Unit,
    onDeleteProvider: suspend (String) -> Result<Unit>,
    onLogProviderDeleted: (String, Long) -> Unit,
    onSelectEpgProvider: (String) -> Unit,
    onEdit: () -> Unit,
    onManageGroups: () -> Unit,
    onDeleted: (focusProviderId: String) -> Unit,
    focusGroupsRowOnEntry: Boolean = false,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val provider = providers.firstOrNull { it.id == providerId }
    // Observe THIS provider's EPG links so the refresh indicator reflects its own linked EPG refresh.
    LaunchedEffect(providerId) {
        android.util.Log.d("VCd", "ActionsScreen shown id=$providerId")
        onSelectEpgProvider(providerId)
    }
    var sourceMode by remember { mutableStateOf(M3uSourceMode.Url) }
    LaunchedEffect(providerId) {
        sourceMode = (runCatching { onGetProviderCredentials(providerId) }.getOrNull() as? ProviderCredentials.M3u)
            ?.sourceMode ?: M3uSourceMode.Url
    }
    var testStatus by remember { mutableStateOf(ConnectionTestStatus.Idle) }
    var testSummary by remember { mutableStateOf<ContentSummary?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf(false) }
    if (provider == null) return
    ProviderActionsPanel(
        provider = provider,
        sourceMode = sourceMode,
        isRefreshing = provider.status == ProviderStatus.Refreshing ||
            providerEpgLinks.any { link ->
                link.providerId == provider.id && epgSources.any { it.id == link.epgSourceId && it.isRefreshing }
            },
        refreshingLabel = if (provider.status == ProviderStatus.Refreshing) {
            stringResource(R.string.settings_provider_action_refreshing_playlist)
        } else {
            stringResource(R.string.settings_provider_action_refreshing_epg)
        },
        testStatus = testStatus,
        testSummary = testSummary,
        testError = testError,
        onEdit = onEdit,
        onTestConnection = {
            if (testStatus != ConnectionTestStatus.Testing) {
                testStatus = ConnectionTestStatus.Testing
                testSummary = null
                testError = null
                scope.launch {
                    val creds = runCatching { onGetProviderCredentials(provider.id) }.getOrNull()
                    val request = ProviderEditorState.from(provider, creds).resolveTestRequest(onGetProviderM3uContent)
                    val result = onTestProviderConnection(request)
                    if (result.errorMessage == null) {
                        testStatus = ConnectionTestStatus.Passed
                        testSummary = result.summary
                    } else {
                        testStatus = ConnectionTestStatus.Failed
                        testError = result.errorMessage
                    }
                }
            }
        },
        onRefresh = { onRefreshProvider(provider.id) },
        onManageGroups = onManageGroups,
        onDelete = { pendingDelete = true },
        focusGroupsRowOnEntry = focusGroupsRowOnEntry,
        entryFocusModifier = entryFocusModifier,
        modifier = modifier,
    )
    if (pendingDelete) {
        DeleteProviderDialog(
            provider = provider,
            onCancel = { pendingDelete = false },
            onDelete = {
                scope.launch {
                    val start = System.currentTimeMillis()
                    val index = providers.indexOfFirst { it.id == provider.id }
                    val neighborId = providers.getOrNull(index + 1)?.id ?: providers.getOrNull(index - 1)?.id
                    onDeleteProvider(provider.id)
                        .onSuccess {
                            onLogProviderDeleted(provider.id, System.currentTimeMillis() - start)
                            pendingDelete = false
                            onDeleted(neighborId ?: OVERVIEW_FOCUS_ADD)
                        }
                        // ponytail: on the rare delete failure just close the dialog; the playlist stays in
                        // the list (nothing removed) — no toast plumbing in this screen.
                        .onFailure { pendingDelete = false }
                }
            },
        )
    }
}

@Composable
internal fun ProviderGroupsScreen(
    providerId: String,
    providers: List<Provider>,
    groupsControls: ProviderGroupsControls,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    // Open group management for this provider while shown; close when it leaves composition (BACK/pop).
    DisposableEffect(providerId) {
        groupsControls.onOpen(providerId)
        onDispose { groupsControls.onClose() }
    }
    val provider = providers.firstOrNull { it.id == providerId } ?: return
    ProviderGroupsPanel(
        activeType = groupsControls.activeType,
        groups = groupsControls.groups,
        settings = groupsControls.settings,
        typeIncluded = when (groupsControls.activeType) {
            CategoryType.LiveTv -> provider.includeLiveTv
            CategoryType.Movies -> provider.includeMovies
            CategoryType.Series -> provider.includeSeries
        },
        onSelectType = groupsControls.onSelectType,
        onToggleGroupHidden = groupsControls.onToggleHidden,
        onSetAllHidden = { hidden -> groupsControls.onSetAllHidden(provider.id, groupsControls.activeType, hidden) },
        onReorder = groupsControls.onReorder,
        onResetOrder = { groupsControls.onResetOrder(provider.id, groupsControls.activeType) },
        onSetSortMode = { mode -> groupsControls.onSetSortMode(provider.id, groupsControls.activeType, mode) },
        onSetHideNewGroups = { hidden -> groupsControls.onSetHideNewGroups(provider.id, groupsControls.activeType, hidden) },
        entryFocusModifier = entryFocusModifier,
        modifier = modifier,
    )
}

@Composable
internal fun ProviderEditorScreen(
    providerId: String?,
    providers: List<Provider>,
    epgSources: List<EpgSource>,
    providerEpgLinks: List<ProviderEpgSource>,
    onGetProviderCredentials: suspend (String) -> ProviderCredentials?,
    onGetProviderM3uContent: suspend (String) -> String?,
    onCreateProvider: suspend (ProviderCreateRequest) -> Result<ProviderSaveResult>,
    onUpdateProvider: suspend (ProviderUpdateRequest) -> Result<ProviderSaveResult>,
    onSetProviderEnabled: suspend (String, Boolean) -> Result<Unit>,
    onDeleteProvider: suspend (String) -> Result<Unit>,
    onTestProviderConnection: suspend (ProviderCreateRequest) -> ProviderConnectionTestResult,
    onPickM3uFile: (((String, String) -> Unit)) -> Unit,
    onRefreshProvider: (String) -> Unit,
    onXtreamProviderSaved: (String) -> Unit,
    onLogProviderSaved: (String, String?) -> Unit,
    onLogProviderDeleted: (String, Long) -> Unit,
    onSelectEpgProvider: (String) -> Unit,
    onToggleEpgLink: (String, String, Boolean) -> Unit,
    onReorderEpgLink: (String, List<String>) -> Unit,
    onSaved: (focusProviderId: String) -> Unit,
    onDeleted: (focusProviderId: String) -> Unit,
    onCancel: () -> Unit,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val strProviderSaveFailed = stringResource(R.string.settings_provider_msg_save_failed)
    val strProviderDeleteFailed = stringResource(R.string.settings_provider_msg_delete_failed)
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

    var editor by remember(providerId) {
        mutableStateOf(
            providers.firstOrNull { it.id == providerId }?.let { ProviderEditorState.from(it) }
                ?: ProviderEditorState.newProvider(ProviderType.M3u),
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    var connectionTestStatus by remember { mutableStateOf(ConnectionTestStatus.Idle) }
    var connectionSummary by remember { mutableStateOf<ContentSummary?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var pendingSaveTestFailure by remember { mutableStateOf(false) }
    var pendingSourceConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var focusSourceSignal by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    // Existing provider: metadata is set synchronously above; load full credentials async (mirrors the old
    // card-open path). Also point the EPG-link observation at this provider.
    LaunchedEffect(providerId) {
        android.util.Log.d("VCd", "EditorScreen shown id=$providerId (null=add)")
        if (providerId != null) {
            onSelectEpgProvider(providerId)
            val credentials = runCatching { onGetProviderCredentials(providerId) }.getOrNull()
            val provider = providers.firstOrNull { it.id == providerId }
            if (provider != null) editor = ProviderEditorState.from(provider, credentials)
        }
    }
    // Duplicate-URL index (other M3U-URL providers) for the inline hint.
    var existingM3uUrls by remember { mutableStateOf<List<ProviderUrlEntry>>(emptyList()) }
    LaunchedEffect(providers) {
        existingM3uUrls = providers
            .associateWith { runCatching { onGetProviderCredentials(it.id) }.getOrNull() }
            .mapNotNull { (provider, credentials) ->
                (credentials as? ProviderCredentials.M3u)
                    ?.takeIf { it.sourceMode == M3uSourceMode.Url }
                    ?.url?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { ProviderUrlEntry(provider.id, normalizeSourceUrl(it), provider.name) }
            }
    }
    val duplicateName = isDuplicateNameOf(editor.name, editor.providerId, providers, { it.id }, { it.name })
    val duplicateUrlName = if (editor.type == ProviderType.M3u &&
        editor.m3uSourceMode == M3uSourceMode.Url && editor.m3uUrl.isNotBlank()
    ) {
        val normalized = normalizeSourceUrl(editor.m3uUrl)
        existingM3uUrls.firstOrNull { it.providerId != editor.providerId && it.url == normalized }?.name
    } else {
        null
    }

    // Writes the editor (update/create), applies the active toggle, restarts refresh, then pops (onSaved).
    suspend fun persistEditor() {
        val savedMode = editor.m3uSourceMode
        val sourceChanged = !editor.isSourceUnchanged
        val saveResult = if (editor.isEditing) onUpdateProvider(editor.toUpdateRequest()) else onCreateProvider(editor.toCreateRequest())
        saveResult
            .onSuccess { saved ->
                val descriptor = when (saved.provider.type) {
                    ProviderType.Xtream -> "XTREAM"
                    ProviderType.M3u -> if (savedMode == M3uSourceMode.File) "M3U_FILE" else "M3U_URL"
                }
                onLogProviderSaved(descriptor, saved.switchedFromType?.name)
                if (editor.isActive != saved.provider.isActive) onSetProviderEnabled(saved.provider.id, editor.isActive)
                // Save RESTARTS the refresh (REPLACE) so the actions group button stays gated until import finishes.
                onRefreshProvider(saved.provider.id)
                if (saved.provider.type == ProviderType.Xtream && sourceChanged) onXtreamProviderSaved(saved.provider.id)
                android.util.Log.d("VCd", "editor persist OK id=${saved.provider.id} isEditing=${editor.isEditing}")
                onSaved(saved.provider.id)
            }
            .onFailure { error ->
                android.util.Log.d("VCd", "editor persist FAIL: ${error.message}")
                message = strProviderSaveFailed.format(error.message ?: "?")
            }
    }

    // Same-source edit persists directly; otherwise the source is tested first and only a passing test
    // persists — a failing test opens the fix/force dialog.
    fun proceedSave() {
        if (editor.isSourceUnchanged) {
            scope.launch { message = null; saving = true; persistEditor(); saving = false }
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

    BackHandler(onBack = onCancel)
    ProviderEditor(
        editor = editor,
        duplicates = ProviderDuplicateInfo(duplicateName, duplicateUrlName),
        isDuplicateName = { candidate -> isDuplicateNameOf(candidate, editor.providerId, providers, { it.id }, { it.name }) },
        message = message,
        connectionTestStatus = connectionTestStatus,
        connectionSummary = connectionSummary,
        connectionError = connectionError.takeIf { connectionTestStatus == ConnectionTestStatus.Failed },
        signals = ProviderEditorSignals(focusSource = focusSourceSignal, saving = saving),
        entryFocusModifier = entryFocusModifier,
        actions = ProviderEditorActions(
            onEditorChange = {
                editor = it
                message = null
                connectionTestStatus = ConnectionTestStatus.Idle
                connectionSummary = null
            },
            onTestConnection = {
                if (connectionTestStatus != ConnectionTestStatus.Testing) {
                    val validationMessage = editor.connectionTestRequestMessageResolved()
                    if (validationMessage != null) {
                        message = validationMessage
                    } else {
                        message = null
                        connectionTestStatus = ConnectionTestStatus.Testing
                        val editorSnapshot = editor
                        scope.launch {
                            val result = onTestProviderConnection(editorSnapshot.resolveTestRequest(onGetProviderM3uContent))
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
                                focusSourceSignal++
                            }
                        }
                    }
                }
            },
            onSave = {
                when {
                    saving || connectionTestStatus == ConnectionTestStatus.Testing -> Unit
                    else -> {
                        val validationMessage = editor.validationMessageResolved()
                        when {
                            validationMessage != null -> message = validationMessage
                            !editor.isEditing || editor.sourceSwitched -> { message = null; pendingSourceConfirm = true }
                            else -> proceedSave()
                        }
                    }
                }
            },
            onCancel = onCancel,
            onToggleEnabled = { editor = editor.copy(isActive = !editor.isActive) },
            onDelete = { pendingDelete = true },
            onPickM3uFile = onPickM3uFile,
            onToggleEpgLink = { sourceId, link -> editor.providerId?.let { onToggleEpgLink(it, sourceId, link) } },
            onReorderEpg = { orderedIds -> editor.providerId?.let { onReorderEpgLink(it, orderedIds) } },
        ),
        epgLinks = ProviderEpgLinkInfo(
            sources = epgSources,
            linkedIds = providerEpgLinks.mapTo(mutableSetOf()) { it.epgSourceId },
            linkedInOrder = providerEpgLinks.mapNotNull { link -> epgSources.firstOrNull { it.id == link.epgSourceId } },
        ),
        modifier = modifier,
    )

    if (pendingDelete) {
        val deletingProvider = providers.firstOrNull { it.id == editor.providerId }
        if (deletingProvider == null) {
            pendingDelete = false
        } else {
            DeleteProviderDialog(
                provider = deletingProvider,
                onCancel = { pendingDelete = false },
                onDelete = {
                    scope.launch {
                        val start = System.currentTimeMillis()
                        val index = providers.indexOfFirst { it.id == deletingProvider.id }
                        val neighborId = providers.getOrNull(index + 1)?.id ?: providers.getOrNull(index - 1)?.id
                        onDeleteProvider(deletingProvider.id)
                            .onSuccess {
                                onLogProviderDeleted(deletingProvider.id, System.currentTimeMillis() - start)
                                pendingDelete = false
                                onDeleted(neighborId ?: OVERVIEW_FOCUS_ADD)
                            }
                            .onFailure { error -> pendingDelete = false; message = strProviderDeleteFailed.format(error.message ?: "?") }
                    }
                },
            )
        }
    }
    if (pendingSourceConfirm) {
        ProviderSourceConfirmDialog(
            isSwitch = editor.sourceSwitched,
            targetLabel = providerSourceLabel(editor.type, editor.m3uSourceMode),
            originalLabel = editor.originalType?.let { providerSourceLabel(it, editor.originalSourceMode ?: M3uSourceMode.Url) },
            onConfirm = { pendingSourceConfirm = false; proceedSave() },
            onCancel = { pendingSourceConfirm = false },
        )
    }
    if (pendingSaveTestFailure) {
        ProviderConnectionFailedDialog(
            reason = connectionError,
            isActive = editor.isActive,
            onCorrect = { pendingSaveTestFailure = false; focusSourceSignal++ },
            onSaveAnyway = {
                pendingSaveTestFailure = false
                editor = editor.copy(isActive = false)
                scope.launch { saving = true; persistEditor(); saving = false }
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
    // Non-null while ANY refresh runs (playlist or EPG) — the label names which; null = idle/clickable.
    // Cross-locks "Refresh Playlists now" so a re-click can't stack another playlist→EPG auto-chain.
    globalRefreshLabel: String?,
    onOpenProvider: (Provider) -> Unit,
    pendingFocus: OverviewFocusTarget?,
    onFocusHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addRequester = remember { FocusRequester() }
    val cardRequesters = remember(providers) { providers.associate { it.id to FocusRequester() } }
    val overviewListState = rememberLazyListState()
    // When returning from the inline editor, move focus onto the requested card (or the add button).
    LaunchedEffect(pendingFocus, providers) {
        val target = pendingFocus ?: return@LaunchedEffect
        // If the target card is off-screen, scroll it into view first so the LazyColumn composes it + its
        // FocusRequester attaches (else requestFocus is a no-op and focus is lost). Header rows before the
        // cards: Add (+ Refresh when providers exist, + a message row when one is set).
        if (target is OverviewFocusTarget.Card) {
            val idx = providers.indexOfFirst { it.id == target.providerId }
            if (idx >= 0) {
                val header = 1 + (if (providers.isNotEmpty()) 1 else 0) + (if (message != null) 1 else 0)
                runCatching { overviewListState.scrollToItem(header + idx) }
            }
        }
        // Retry across a few frames: after a pop the LazyColumn may need >1 frame to attach the target card's
        // FocusRequester, and for a just-saved provider `providers` can lag one emission (which re-launches
        // this effect with the new card in the map). Clear the pending focus only once focus actually lands.
        repeat(30) {
            awaitFrame()
            val requester = when (target) {
                is OverviewFocusTarget.Card -> cardRequesters[target.providerId]
                OverviewFocusTarget.AddButton -> addRequester
            }
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                android.util.Log.d("VCd", "overview focus-return $target -> landed")
                onFocusHandled()
                return@LaunchedEffect
            }
        }
        // Target card never rendered in time → add button, so focus can't orphan upward.
        android.util.Log.d("VCd", "overview focus-return $target -> FALLBACK add (card never rendered)")
        runCatching { addRequester.requestFocus() }
        onFocusHandled()
    }
    LazyColumn(
        state = overviewListState,
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
                    value = globalRefreshLabel ?: stringResource(R.string.settings_playlist_refresh_value),
                    valueLoading = globalRefreshLabel != null,
                    // Kept enabled/focusable (disabling a focused row drops focus to the top nav = Home);
                    // the onClick is gated instead.
                    onClick = { if (globalRefreshLabel == null) onRefreshAll() },
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

internal val ProviderStatus.tone: Color
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
