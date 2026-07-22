package com.vivicast.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.LocalVivicastColors
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.epg.EpgConnectionTestResult
import com.vivicast.tv.data.epg.EpgContentSummary
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.EpgSource
import androidx.compose.ui.res.stringResource
import com.vivicast.tv.core.designsystem.R
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------------------
// EPG sub-views as self-contained inner-nav destination screens. Split out of the former single
// EpgSettingsPanel (mirrors the Playlists D2 split): each owns its own state; the inner NavHost's back stack
// replaces the old shared showEditor/showManualMapping/showGlobalSettings booleans + focus-park +
// collapseSubViewSignal. Focus-return to the originating overview row/card is carried as a nav result
// (EPG_FOCUS_KEY, see SettingsRoute's EpgGraph wiring).
// ---------------------------------------------------------------------------------------------------------

/** Nav-result sentinels: which overview row to focus after a sub-view pops. Any OTHER token = a source id. */
internal const val EPG_FOCUS_GLOBAL = "__epg_global__"
internal const val EPG_FOCUS_ADD = "__epg_add__"
internal const val EPG_FOCUS_MANUAL = "__epg_manual__"

@Composable
internal fun EpgOverviewScreen(
    sources: List<EpgSource>,
    providers: List<Provider>,
    onRefreshEpgSource: (sourceId: String) -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onOpenAddSource: () -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenManualMapping: () -> Unit,
    // Focus-return target when popping back from a sub-view (null on a fresh section entry, so focus stays on
    // the rail until RIGHT). A sentinel => the matching header row; any other id => that source's card.
    pendingFocusToken: String?,
    onFocusHandled: () -> Unit,
    firstFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val globalSettingsRequester = remember { FocusRequester() }
    val addRequester = remember { FocusRequester() }
    val manualRequester = remember { FocusRequester() }
    val sourceRequesters = remember(sources) { sources.associate { it.id to FocusRequester() } }
    val overviewListState = rememberLazyListState()
    // Cross-lock "Refresh EPG now" while ANY refresh runs (playlist or EPG): a playlist refresh with
    // "refresh on playlist change" auto-chains an EPG refresh, so re-triggering here would stack a second
    // chain. Non-null = the label naming which refresh is active; null = idle/clickable. Playlist-first.
    val epgRefreshLabel: String? = when {
        providers.any { it.status == ProviderStatus.Refreshing } ->
            stringResource(R.string.settings_provider_action_refreshing_playlist)
        sources.any { it.isRefreshing } ->
            stringResource(R.string.settings_provider_action_refreshing_epg)
        else -> null
    }
    // Return focus onto the header row or the source card just left, else it escapes to the top nav. A source
    // card may be off-screen (the LazyColumn hasn't composed it) — scroll it into view first so its
    // FocusRequester attaches. Fixed header rows precede the sources: Global settings, Add source, Refresh
    // (present when sources exist), Manual mapping = 4.
    LaunchedEffect(pendingFocusToken, sources) {
        val token = pendingFocusToken ?: return@LaunchedEffect
        val sourceIndex = sources.indexOfFirst { it.id == token }
        if (sourceIndex >= 0) runCatching { overviewListState.scrollToItem(sourceIndex + 4) }
        val requester = when (token) {
            EPG_FOCUS_GLOBAL -> globalSettingsRequester
            EPG_FOCUS_ADD -> addRequester
            EPG_FOCUS_MANUAL -> manualRequester
            else -> sourceRequesters[token]
        }
        // Retry across a few frames: a just-scrolled off-screen source card can need >1 frame to attach its
        // FocusRequester. Clear the pending focus only once focus actually lands.
        repeat(30) {
            awaitFrame()
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                onFocusHandled()
                return@LaunchedEffect
            }
        }
        // Target row never rendered in time → add button, so focus can't orphan upward.
        runCatching { addRequester.requestFocus() }
        onFocusHandled()
    }
    SettingsDetailList(
        listState = overviewListState,
        modifier = modifier,
    ) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_epg_global_settings),
                help = stringResource(R.string.settings_epg_help_global_settings),
                value = stringResource(R.string.about_open_value),
                modifier = firstFocusModifier.focusRequester(globalSettingsRequester),
                onClick = onOpenGlobalSettings,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_epg_add_source),
                help = stringResource(R.string.settings_epg_help_add_source),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(addRequester),
                onClick = onOpenAddSource,
            )
        }
        // Nothing to refresh without an EPG source, so hide the action until one exists.
        if (sources.isNotEmpty()) {
            item {
                VivicastSettingsRow(
                    title = stringResource(R.string.settings_epg_now),
                    help = stringResource(R.string.settings_epg_help_run_now),
                    value = epgRefreshLabel ?: stringResource(R.string.settings_epg_now_value),
                    valueLoading = epgRefreshLabel != null,
                    // Kept enabled/focusable (disabling a focused row drops focus to the top nav); the onClick
                    // is gated instead. Enqueue is KEEP, so an in-flight source coalesces.
                    onClick = {
                        if (epgRefreshLabel == null) {
                            sources.filter { it.isActive }.forEach { onRefreshEpgSource(it.id) }
                        }
                    },
                )
            }
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_epg_manual_mapping),
                help = stringResource(R.string.settings_epg_manual_mapping_body),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(manualRequester),
                onClick = onOpenManualMapping,
            )
        }
        if (sources.isEmpty()) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_no_sources),
                    body = stringResource(R.string.settings_epg_no_sources_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(sources, key = { it.id }) { source ->
                EpgSourceOverviewCard(
                    source = source,
                    modifier = Modifier.focusRequester(sourceRequesters.getValue(source.id)),
                    onClick = { onOpenSource(source.id) },
                )
            }
        }
    }
}

@Composable
internal fun EpgSourceEditorScreen(
    sourceId: String?,
    sources: List<EpgSource>,
    onSaveEpgSource: suspend (EpgSourceEditRequest) -> Result<EpgSource>,
    onDeleteEpgSource: suspend (String) -> Result<Unit>,
    onRefreshEpgSource: (sourceId: String) -> Unit,
    onGetEpgSourceUrl: suspend (String) -> String?,
    onTestEpgConnection: suspend (String) -> EpgConnectionTestResult,
    onSaved: (focusToken: String) -> Unit,
    onDeleted: (focusToken: String) -> Unit,
    onCancel: () -> Unit,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Self-load the draft from sourceId (null = add). Metadata is set synchronously; the stored (secure) URL
    // is fetched async below and re-applied, mirroring the playlist editor.
    var editor by remember(sourceId) {
        mutableStateOf(
            sources.firstOrNull { it.id == sourceId }?.let { EpgSourceEditorState.from(it) }
                ?: EpgSourceEditorState.newSource(),
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    // Drives the editor Save spinner while the async save + auto-refresh runs (mirrors the playlist editor).
    var epgSaving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    // EPG-source URL connection test (fetch + XMLTV parse), mirroring the playlist editor test.
    var connectionTestStatus by remember { mutableStateOf(ConnectionTestStatus.Idle) }
    var connectionSummary by remember { mutableStateOf<EpgContentSummary?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val strUnknownError = stringResource(R.string.common_unknown_error)
    val strValidationEpgNameMissing = stringResource(R.string.validation_name_missing)
    val strValidationEpgUrlMissing = stringResource(R.string.validation_epg_url_missing)
    val strEpgSrcSaveFailed = stringResource(R.string.settings_epg_source_save_failed)
    val strEpgDeleteFailed = stringResource(R.string.settings_epg_msg_source_delete_failed)
    val strEpgTestUrlMissing = stringResource(R.string.validation_epg_url_missing)

    LaunchedEffect(sourceId) {
        if (sourceId != null) {
            val url = runCatching { onGetEpgSourceUrl(sourceId) }.getOrNull().orEmpty()
            val source = sources.firstOrNull { it.id == sourceId }
            if (source != null) editor = EpgSourceEditorState.from(source, url)
        }
    }

    // Existing source URLs (from the secure store) for duplicate detection over ALL sources. The .gz/.xz
    // compression suffix is ignored so the same file with a different extension counts as a duplicate.
    var existingEpgUrls by remember { mutableStateOf<List<EpgUrlEntry>>(emptyList()) }
    LaunchedEffect(sources) {
        existingEpgUrls = sources.mapNotNull { source ->
            onGetEpgSourceUrl(source.id)?.trim()?.takeIf { it.isNotBlank() }
                ?.let { EpgUrlEntry(source.id, normalizeSourceUrl(it), source.name) }
        }
    }
    val duplicateEpgUrl: String? = editor.url.trim().takeIf { it.isNotBlank() }?.let { url ->
        val normalized = normalizeSourceUrl(url)
        existingEpgUrls.firstOrNull { it.sourceId != editor.sourceId && it.normalizedUrl == normalized }?.name
    }

    val resetConnectionTest: () -> Unit = {
        connectionTestStatus = ConnectionTestStatus.Idle
        connectionSummary = null
        connectionError = null
    }

    BackHandler(onBack = onCancel)
    EpgSourceEditor(
        editor = editor,
        message = message,
        onEditorChange = {
            editor = it
            // Any field edit invalidates a previous test result (mirrors the playlist editor).
            resetConnectionTest()
        },
        onSave = {
            val validationMessage = editor.validationMessage(strValidationEpgNameMissing, strValidationEpgUrlMissing)
            if (validationMessage != null) {
                message = validationMessage
                return@EpgSourceEditor
            }
            scope.launch {
                epgSaving = true
                try {
                    onSaveEpgSource(editor.toEditRequest())
                        .onSuccess { source ->
                            // Auto-refresh the saved source (like a playlist refreshes on save). An inactive
                            // source is a no-op in the worker, so this is always safe.
                            onRefreshEpgSource(source.id)
                            onSaved(source.id)
                        }
                        .onFailure { error ->
                            message = strEpgSrcSaveFailed.format(error.message ?: strUnknownError)
                        }
                } finally {
                    epgSaving = false
                }
            }
        },
        onCancel = onCancel,
        onDelete = { pendingDelete = true },
        duplicateName = isDuplicateNameOf(editor.name, editor.sourceId, sources, { it.id }, { it.name }),
        isDuplicateName = { candidate ->
            isDuplicateNameOf(candidate, editor.sourceId, sources, { it.id }, { it.name })
        },
        duplicateUrlName = duplicateEpgUrl,
        connectionTestStatus = connectionTestStatus,
        connectionSummary = connectionSummary,
        connectionError = connectionError,
        onTestConnection = {
            if (connectionTestStatus != ConnectionTestStatus.Testing) {
                val url = editor.url.trim()
                if (url.isBlank()) {
                    connectionSummary = null
                    connectionError = strEpgTestUrlMissing
                    connectionTestStatus = ConnectionTestStatus.Failed
                } else {
                    connectionTestStatus = ConnectionTestStatus.Testing
                    connectionError = null
                    connectionSummary = null
                    scope.launch {
                        val result = onTestEpgConnection(url)
                        if (result.summary != null) {
                            connectionSummary = result.summary
                            connectionError = null
                            connectionTestStatus = ConnectionTestStatus.Passed
                        } else {
                            connectionSummary = null
                            connectionError = result.errorMessage
                            connectionTestStatus = ConnectionTestStatus.Failed
                        }
                    }
                }
            }
        },
        saving = epgSaving,
        entryFocusModifier = entryFocusModifier,
        modifier = modifier,
    )

    if (pendingDelete) {
        val source = sources.firstOrNull { it.id == editor.sourceId }
        if (source == null) {
            pendingDelete = false
        } else {
            DeleteEpgSourceDialog(
                source = source,
                onCancel = { pendingDelete = false },
                onDelete = {
                    scope.launch {
                        onDeleteEpgSource(source.id)
                            .onSuccess {
                                // Return focus to the next (or previous) source, or the add row if none remain.
                                val deletedIndex = sources.indexOfFirst { it.id == source.id }
                                val neighborId = sources.getOrNull(deletedIndex + 1)?.id
                                    ?: sources.getOrNull(deletedIndex - 1)?.id
                                pendingDelete = false
                                onDeleted(neighborId ?: EPG_FOCUS_ADD)
                            }
                            .onFailure { error ->
                                pendingDelete = false
                                message = strEpgDeleteFailed.format(error.message ?: strUnknownError)
                            }
                    }
                },
            )
        }
    }
}

@Composable
internal fun EpgManualMappingScreen(
    providers: List<Provider>,
    sources: List<EpgSource>,
    selectedProviderId: String?,
    providerLinks: List<ProviderEpgSource>,
    channels: List<Channel>,
    mappings: List<EpgChannelMapping>,
    selectedChannelId: String?,
    onSelectProvider: (String) -> Unit,
    onSelectChannel: (String) -> Unit,
    onSetManualMapping: suspend (ManualEpgChannelMappingRequest) -> Result<Unit>,
    onClearManualMapping: suspend (providerId: String, channelId: String, epgSourceId: String) -> Result<Unit>,
    onResetManualMappingChannel: () -> Unit,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    // Reset the VM channel selection on open (was done at the click site before the flag flip).
    LaunchedEffect(Unit) { onResetManualMappingChannel() }
    var message by remember { mutableStateOf<String?>(null) }
    // Land focus on the first provider row after the nav swap, else it escapes to the top nav.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }
    ManualEpgMappingPanel(
        providers = providers,
        sources = sources,
        selectedProviderId = selectedProviderId,
        providerLinks = providerLinks,
        channels = channels,
        mappings = mappings,
        selectedChannelId = selectedChannelId,
        message = message,
        onSelectProvider = onSelectProvider,
        onSelectChannel = onSelectChannel,
        onSetManualMapping = onSetManualMapping,
        onClearManualMapping = onClearManualMapping,
        onMessage = { message = it },
        // Carry the detail panel's entry requester (rail RIGHT re-entry) AND the local first-focus requester
        // onto the first provider row, so both the rail RIGHT and the nav-in initial focus land there.
        firstFocusModifier = entryFocusModifier.focusRequester(firstFocus),
        modifier = modifier,
    )
}

@Composable
internal fun EpgGlobalSettingsScreen(
    state: EpgSettingsState,
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    // Land focus on the first row after the nav swap, else it escapes to the top nav (mirrors the editor).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
    ) {
        item {
            EpgGlobalSettings(
                preferences = state,
                onEpgPreferencesChanged = onEpgPreferencesChanged,
                // Carry the entry requester onto the sub-panel's first row too, so RIGHT from the section
                // rail re-enters it (not only the overview).
                firstFocusModifier = entryFocusModifier.focusRequester(firstFocus),
            )
        }
    }
}

/** A single EPG-source card in the overview list; opening it navigates to the full-width editor. */
@Composable
private fun EpgSourceOverviewCard(
    source: EpgSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = VivicastSpacing.Space4,
    ) {
        // Same layout as the playlist provider card: name + status badge inline on the left, a badge row
        // (EPG + channel count) below, and the last-refresh timestamp right-aligned.
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = source.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = VivicastTypography.LabelLarge,
                    )
                    if (source.isRefreshing) {
                        StatusBadge(stringResource(R.string.livetv_status_refreshing), tone = VivicastColors.Warning)
                    } else if (source.isActive) {
                        StatusBadge(stringResource(R.string.common_active), tone = VivicastColors.Success)
                    } else {
                        // Match the playlist card: a disabled source shows an orange "Disabled" pill.
                        StatusBadge(stringResource(R.string.livetv_status_disabled), tone = VivicastColors.Warning)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    StatusBadge("EPG")
                    StatusBadge(stringResource(R.string.settings_epg_channel_count, source.lastChannelCount), tone = LocalVivicastColors.current.accent)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                source.lastRefreshAt?.let { refreshedAt ->
                    BodyText(stringResource(R.string.settings_provider_updated_format, refreshedAt.toBackupTimestamp()), maxLines = 1)
                }
            }
        }
    }
}

private data class EpgUrlEntry(val sourceId: String, val normalizedUrl: String, val name: String)
