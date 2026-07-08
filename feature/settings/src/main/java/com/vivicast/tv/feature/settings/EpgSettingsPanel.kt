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
import com.vivicast.tv.data.epg.EpgConnectionTestResult
import com.vivicast.tv.data.epg.EpgContentSummary
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
internal fun EpgSettingsPanel(
    state: EpgSettingsState,
    sources: List<EpgSource>,
    providers: List<Provider>,
    selectedProviderId: String?,
    providerLinks: List<ProviderEpgSource>,
    manualMappingChannels: List<Channel>,
    manualMappings: List<EpgChannelMapping>,
    selectedManualMappingChannelId: String?,
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    onRefreshEpgSource: (sourceId: String) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSaveEpgSource: suspend (EpgSourceEditRequest) -> Result<EpgSource>,
    onDeleteEpgSource: suspend (String) -> Result<Unit>,
    onSelectManualMappingChannel: (String) -> Unit,
    onResetManualMappingChannel: () -> Unit,
    onSetManualMapping: suspend (ManualEpgChannelMappingRequest) -> Result<Unit>,
    onClearManualMapping: suspend (providerId: String, channelId: String, epgSourceId: String) -> Result<Unit>,
    onGetEpgSourceUrl: suspend (String) -> String? = { null },
    onTestEpgConnection: suspend (String) -> EpgConnectionTestResult = { EpgConnectionTestResult(null, null) },
    firstFocusModifier: Modifier = Modifier,
    onParkFocusBeforeEditor: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(EpgSourceEditorState.newSource()) }
    var showEditor by remember { mutableStateOf(false) }
    var showManualMapping by remember { mutableStateOf(false) }
    var showGlobalSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<EpgSource?>(null) }
    // Where focus lands once the overview returns after leaving the inline editor / manual mapping.
    var pendingOverviewFocus by remember { mutableStateOf<EpgOverviewFocusTarget?>(null) }
    // EPG-source URL connection test (fetch + XMLTV parse), mirroring the playlist editor test.
    var connectionTestStatus by remember { mutableStateOf(ConnectionTestStatus.Idle) }
    var connectionSummary by remember { mutableStateOf<EpgContentSummary?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val strEpgScheduled = stringResource(R.string.settings_epg_msg_scheduled)
    val strEpgSourceSaved = stringResource(R.string.settings_epg_msg_source_saved)
    val strEpgSourceDeleted = stringResource(R.string.settings_epg_msg_source_deleted)
    val strValidationEpgNameMissing = stringResource(R.string.validation_name_missing)
    val strValidationEpgUrlMissing = stringResource(R.string.validation_epg_url_missing)
    val strUnknownError = stringResource(R.string.common_unknown_error)
    val strEpgSrcSaveFailed = stringResource(R.string.settings_epg_source_save_failed)
    val strEpgDeleteFailed = stringResource(R.string.settings_epg_msg_source_delete_failed)
    val strEpgTestUrlMissing = stringResource(R.string.validation_epg_url_missing)

    LaunchedEffect(sources) {
        val selectedSource = selectedSourceId?.let { id -> sources.firstOrNull { it.id == id } }
        if (selectedSource == null && selectedSourceId != null) {
            selectedSourceId = null
            editor = EpgSourceEditorState.newSource()
            showEditor = false
        }
    }

    // Existing source URLs (from the secure store) for duplicate detection. The .gz/.xz compression
    // suffix is ignored so the same file with a different extension counts as a duplicate.
    var existingEpgUrls by remember { mutableStateOf<List<EpgUrlEntry>>(emptyList()) }
    LaunchedEffect(sources) {
        existingEpgUrls = sources.mapNotNull { source ->
            onGetEpgSourceUrl(source.id)?.trim()?.takeIf { it.isNotBlank() }
                ?.let { EpgUrlEntry(source.id, normalizeEpgUrl(it), source.name) }
        }
    }
    val duplicateEpgUrl: String? = editor.url.trim().takeIf { it.isNotBlank() }?.let { url ->
        val normalized = normalizeEpgUrl(url)
        existingEpgUrls.firstOrNull { it.sourceId != editor.sourceId && it.normalizedUrl == normalized }?.name
    }

    val resetConnectionTest: () -> Unit = {
        connectionTestStatus = ConnectionTestStatus.Idle
        connectionSummary = null
        connectionError = null
    }
    val openEditorForNew: () -> Unit = {
        onParkFocusBeforeEditor()
        selectedSourceId = null
        editor = EpgSourceEditorState.newSource()
        showEditor = true
        showManualMapping = false
        showGlobalSettings = false
        message = null
        resetConnectionTest()
    }
    val openGlobalSettings: () -> Unit = {
        onParkFocusBeforeEditor()
        showGlobalSettings = true
        showEditor = false
        showManualMapping = false
        message = null
    }
    val dismissGlobalSettings: () -> Unit = {
        onParkFocusBeforeEditor()
        pendingOverviewFocus = EpgOverviewFocusTarget.GlobalSettingsButton
        showGlobalSettings = false
        message = null
    }
    val dismissEditor: () -> Unit = {
        // Park focus before the inline editor is removed, else focus escapes to the top nav bar (Home).
        onParkFocusBeforeEditor()
        pendingOverviewFocus = editor.sourceId?.let(EpgOverviewFocusTarget::Source) ?: EpgOverviewFocusTarget.AddButton
        selectedSourceId = null
        editor = EpgSourceEditorState.newSource()
        showEditor = false
        message = null
    }
    val dismissManualMapping: () -> Unit = {
        onParkFocusBeforeEditor()
        pendingOverviewFocus = EpgOverviewFocusTarget.ManualButton
        showManualMapping = false
        message = null
    }

    // Full-width infill swap (like the Playlists panel): overview, source editor and manual mapping
    // are mutually exclusive, so none can be pushed off-screen by a shared column.
    when {
        showEditor -> {
            BackHandler(onBack = dismissEditor)
            EpgSourceEditor(
                editor = editor,
                message = message,
                onEditorChange = {
                    editor = it
                    // Any field edit invalidates a previous test result (mirrors the playlist editor).
                    connectionTestStatus = ConnectionTestStatus.Idle
                    connectionSummary = null
                    connectionError = null
                },
                onSave = {
                    val validationMessage = editor.validationMessage(strValidationEpgNameMissing, strValidationEpgUrlMissing)
                    if (validationMessage != null) {
                        message = validationMessage
                        return@EpgSourceEditor
                    }
                    scope.launch {
                        onSaveEpgSource(editor.toEditRequest())
                            .onSuccess { source ->
                                // Auto-refresh the saved source (like a playlist refreshes on save). An
                                // inactive source is a no-op in the worker, so this is always safe.
                                onRefreshEpgSource(source.id)
                                // Close the editor and return to the overview focused on the saved
                                // source (like the playlist editor), instead of leaving focus on a
                                // control that recomposition removes → escaping to the top nav (Home).
                                onParkFocusBeforeEditor()
                                pendingOverviewFocus = EpgOverviewFocusTarget.Source(source.id)
                                selectedSourceId = null
                                editor = EpgSourceEditorState.newSource()
                                showEditor = false
                                resetConnectionTest()
                                message = strEpgSourceSaved
                            }
                            .onFailure { error ->
                                message = strEpgSrcSaveFailed.format(error.message ?: strUnknownError)
                            }
                    }
                },
                onCancel = dismissEditor,
                onDelete = {
                    pendingDelete = sources.firstOrNull { it.id == editor.sourceId }
                },
                duplicateName = editor.name.isNotBlank() && sources.any {
                    it.id != editor.sourceId && it.name.trim().equals(editor.name.trim(), ignoreCase = true)
                },
                isDuplicateName = { candidate ->
                    sources.any { it.id != editor.sourceId && it.name.trim().equals(candidate.trim(), ignoreCase = true) }
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        showManualMapping -> {
            BackHandler(onBack = dismissManualMapping)
            ManualEpgMappingPanel(
                providers = providers,
                sources = sources,
                selectedProviderId = selectedProviderId,
                providerLinks = providerLinks,
                channels = manualMappingChannels,
                mappings = manualMappings,
                selectedChannelId = selectedManualMappingChannelId,
                message = message,
                onSelectProvider = onSelectProvider,
                onSelectChannel = onSelectManualMappingChannel,
                onSetManualMapping = onSetManualMapping,
                onClearManualMapping = onClearManualMapping,
                onMessage = { message = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        showGlobalSettings -> {
            BackHandler(onBack = dismissGlobalSettings)
            // Land focus on the first row after the swap, else it escapes to the top nav (mirrors editor).
            val globalFirstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                awaitFrame()
                runCatching { globalFirstFocus.requestFocus() }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
            ) {
                item {
                    EpgGlobalSettings(
                        preferences = state,
                        onEpgPreferencesChanged = onEpgPreferencesChanged,
                        firstFocusModifier = Modifier.focusRequester(globalFirstFocus),
                    )
                }
            }
        }

        else -> {
            val globalSettingsRequester = remember { FocusRequester() }
            val addRequester = remember { FocusRequester() }
            val manualRequester = remember { FocusRequester() }
            val sourceRequesters = remember(sources) { sources.associate { it.id to FocusRequester() } }
            // Return focus onto the add/manual row or the source card just left, else it escapes to top nav.
            LaunchedEffect(pendingOverviewFocus, sources) {
                val target = pendingOverviewFocus ?: return@LaunchedEffect
                awaitFrame()
                val requester = when (target) {
                    EpgOverviewFocusTarget.GlobalSettingsButton -> globalSettingsRequester
                    EpgOverviewFocusTarget.AddButton -> addRequester
                    EpgOverviewFocusTarget.ManualButton -> manualRequester
                    is EpgOverviewFocusTarget.Source -> sourceRequesters[target.sourceId] ?: addRequester
                }
                runCatching { requester.requestFocus() }
                pendingOverviewFocus = null
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Match the playlist overview's row spacing (Space3); EPG previously used the larger Space4.
                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
            ) {
                item {
                    VivicastSettingsRow(
                        title = stringResource(R.string.settings_epg_global_settings),
                        help = stringResource(R.string.settings_epg_help_global_settings),
                        value = stringResource(R.string.about_open_value),
                        modifier = firstFocusModifier.focusRequester(globalSettingsRequester),
                        onClick = openGlobalSettings,
                    )
                }
                item {
                    VivicastSettingsRow(
                        title = stringResource(R.string.settings_epg_add_source),
                        help = stringResource(R.string.settings_epg_help_add_source),
                        value = stringResource(R.string.about_open_value),
                        modifier = Modifier.focusRequester(addRequester),
                        onClick = openEditorForNew,
                    )
                }
                // Nothing to refresh without an EPG source, so hide the action until one exists.
                if (sources.isNotEmpty()) {
                    item {
                        VivicastSettingsRow(
                            title = stringResource(R.string.settings_epg_now),
                            help = stringResource(R.string.settings_epg_help_run_now),
                            value = stringResource(R.string.settings_epg_now_value),
                            onClick = {
                                // Enqueue a scoped per-source EPG refresh for each active source. No
                                // "any refreshing → skip" guard: enqueueEpgRefresh uses KEEP, so an
                                // in-flight source coalesces while a stuck/idle one still starts — one
                                // stuck-"Refreshing" source must not block refreshing everything else.
                                sources.filter { it.isActive }.forEach { onRefreshEpgSource(it.id) }
                                message = strEpgScheduled
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
                        onClick = {
                            onParkFocusBeforeEditor()
                            onResetManualMappingChannel()
                            showManualMapping = true
                            showEditor = false
                            message = null
                        },
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
                            onClick = {
                                onParkFocusBeforeEditor()
                                selectedSourceId = source.id
                                editor = EpgSourceEditorState.from(source)
                                showEditor = true
                                showManualMapping = false
                                message = null
                                resetConnectionTest()
                                // Pre-fill the URL field with the stored (secure) URL, mirroring the
                                // playlist editor. Fetched async, so re-apply only if still editing this source.
                                scope.launch {
                                    val url = runCatching { onGetEpgSourceUrl(source.id) }.getOrNull().orEmpty()
                                    if (selectedSourceId == source.id) {
                                        editor = EpgSourceEditorState.from(source, url)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { source ->
        DeleteEpgSourceDialog(
            source = source,
            onCancel = { pendingDelete = null },
            onDelete = {
                scope.launch {
                    onDeleteEpgSource(source.id)
                        .onSuccess {
                            // Return focus to the next (or previous) source, or the add button if none
                            // remain — else focus escapes to the top nav (Home). Mirrors the playlist editor.
                            onParkFocusBeforeEditor()
                            val deletedIndex = sources.indexOfFirst { it.id == source.id }
                            val neighborId = sources.getOrNull(deletedIndex + 1)?.id
                                ?: sources.getOrNull(deletedIndex - 1)?.id
                            pendingOverviewFocus = neighborId?.let(EpgOverviewFocusTarget::Source)
                                ?: EpgOverviewFocusTarget.AddButton
                            pendingDelete = null
                            selectedSourceId = null
                            editor = EpgSourceEditorState.newSource()
                            showEditor = false
                            resetConnectionTest()
                            message = strEpgSourceDeleted
                        }
                        .onFailure { error ->
                            pendingDelete = null
                            message = strEpgDeleteFailed.format(error.message ?: strUnknownError)
                        }
                }
            },
        )
    }
}

/** A single EPG-source card in the overview list; opening it swaps in the full-width editor. */
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
                    StatusBadge(stringResource(R.string.settings_epg_channel_count, source.lastChannelCount), tone = VivicastColors.Info)
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

private sealed interface EpgOverviewFocusTarget {
    data object GlobalSettingsButton : EpgOverviewFocusTarget
    data object AddButton : EpgOverviewFocusTarget
    data object ManualButton : EpgOverviewFocusTarget
    data class Source(val sourceId: String) : EpgOverviewFocusTarget
}

private data class EpgUrlEntry(val sourceId: String, val normalizedUrl: String, val name: String)

/** Ignore the compression suffix so e.g. `epg-de.xml` and `epg-de.xml.gz` count as the same URL. */
private fun normalizeEpgUrl(url: String): String =
    url.trim().removeSuffix(".gz").removeSuffix(".xz")

