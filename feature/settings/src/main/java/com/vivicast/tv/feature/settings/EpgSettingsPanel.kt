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
import androidx.compose.runtime.collectAsState
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
internal fun EpgSettingsPanel(
    epgSourceRepository: EpgSourceRepository,
    state: EpgSettingsState,
    sources: List<EpgSource>,
    providers: List<Provider>,
    selectedProviderId: String?,
    providerLinks: List<ProviderEpgSource>,
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    onRunGlobalRefresh: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSaveEpgSource: suspend (EpgSourceEditRequest) -> Result<EpgSource>,
    onDeleteEpgSource: suspend (String) -> Result<Unit>,
    onLinkProvider: suspend (providerId: String, sourceId: String, priority: Int) -> Result<Unit>,
    onUnlinkProvider: suspend (providerId: String, sourceId: String) -> Result<Unit>,
    onMoveProviderLink: suspend (providerId: String, sourceId: String, direction: EpgSourcePriorityDirection) -> Result<Unit>,
    firstFocusModifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(EpgSourceEditorState.newSource()) }
    var showEditor by remember { mutableStateOf(false) }
    var showManualMapping by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<EpgSource?>(null) }
    val strEpgScheduled = stringResource(R.string.settings_epg_msg_scheduled)
    val strEpgSourceSaved = stringResource(R.string.settings_epg_msg_source_saved)
    val strEpgSourceAssigned = stringResource(R.string.settings_epg_msg_source_assigned)
    val strEpgSourceUnlinked = stringResource(R.string.settings_epg_msg_source_unlinked)
    val strEpgSourceDeleted = stringResource(R.string.settings_epg_msg_source_deleted)
    val strEpgSourceSelect = stringResource(R.string.settings_epg_msg_source_select)
    val strEpgPriorityUpdated = stringResource(R.string.settings_epg_msg_priority_updated)
    val strValidationEpgNameMissing = stringResource(R.string.validation_name_missing)
    val strValidationEpgUrlMissing = stringResource(R.string.validation_epg_url_missing)
    val strUnknownError = stringResource(R.string.common_unknown_error)
    val strEpgSrcSaveFailed = stringResource(R.string.settings_epg_source_save_failed)
    val strEpgLinkFailed = stringResource(R.string.settings_epg_msg_mapping_failed)
    val strEpgUnlinkFailed = stringResource(R.string.settings_epg_msg_remove_failed)
    val strEpgPriorityFailed = stringResource(R.string.settings_epg_msg_priority_failed)
    val strEpgDeleteFailed = stringResource(R.string.settings_epg_msg_source_delete_failed)

    LaunchedEffect(sources) {
        val selectedSource = selectedSourceId?.let { id -> sources.firstOrNull { it.id == id } }
        if (selectedSource == null && selectedSourceId != null) {
            selectedSourceId = null
            editor = EpgSourceEditorState.newSource()
            showEditor = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
        EpgGlobalSettings(
            preferences = state,
            onEpgPreferencesChanged = onEpgPreferencesChanged,
            onRunGlobalRefresh = {
                onRunGlobalRefresh()
                message = strEpgScheduled
            },
            firstFocusModifier = firstFocusModifier,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(
                label = stringResource(R.string.settings_epg_add_source),
                modifier = Modifier.width(250.dp),
                selected = showEditor && !editor.isEditing,
                onClick = {
                    selectedSourceId = null
                    editor = EpgSourceEditorState.newSource()
                    showEditor = true
                    showManualMapping = false
                    message = null
                },
            )
            ActionPill(
                label = stringResource(R.string.settings_epg_manual_mapping),
                modifier = Modifier.width(230.dp),
                selected = showManualMapping,
                onClick = {
                    showManualMapping = true
                    showEditor = false
                    message = null
                },
            )
        }

        if (showManualMapping) {
            ManualEpgMappingPanel(
                providers = providers,
                sources = sources,
                selectedProviderId = selectedProviderId,
                providerLinks = providerLinks,
                repository = epgSourceRepository,
                message = message,
                onSelectProvider = onSelectProvider,
                onMessage = { message = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
            EpgSourceList(
                sources = sources,
                selectedSourceId = selectedSourceId,
                onSelectSource = { source ->
                    selectedSourceId = source.id
                    editor = EpgSourceEditorState.from(source)
                    showEditor = true
                    showManualMapping = false
                    message = null
                },
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
            )

            if (showEditor) {
                EpgSourceEditor(
                    editor = editor,
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    providerLinks = providerLinks,
                    message = message,
                    onEditorChange = { editor = it },
                    onSelectProvider = onSelectProvider,
                    onSave = {
                        val validationMessage = editor.validationMessage(strValidationEpgNameMissing, strValidationEpgUrlMissing)
                        if (validationMessage != null) {
                            message = validationMessage
                            return@EpgSourceEditor
                        }
                        scope.launch {
                            onSaveEpgSource(editor.toEditRequest())
                                .onSuccess { source ->
                                    selectedSourceId = source.id
                                    editor = EpgSourceEditorState.from(source)
                                    showEditor = true
                                    message = strEpgSourceSaved
                                }
                                .onFailure { error ->
                                    message = strEpgSrcSaveFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    onDelete = {
                        pendingDelete = sources.firstOrNull { it.id == editor.sourceId }
                    },
                    onLinkProvider = { providerId, sourceId, priority ->
                        scope.launch {
                            onLinkProvider(providerId, sourceId, priority)
                                .onSuccess { message = strEpgSourceAssigned }
                                .onFailure { error ->
                                    message = strEpgLinkFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    onUnlinkProvider = { providerId, sourceId ->
                        scope.launch {
                            onUnlinkProvider(providerId, sourceId)
                                .onSuccess { message = strEpgSourceUnlinked }
                                .onFailure { error ->
                                    message = strEpgUnlinkFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    onMoveProviderLink = { providerId, sourceId, direction ->
                        scope.launch {
                            onMoveProviderLink(providerId, sourceId, direction)
                                .onSuccess { message = strEpgPriorityUpdated }
                                .onFailure { error ->
                                    message = strEpgPriorityFailed.format(error.message ?: strUnknownError)
                                }
                        }
                    },
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            } else {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_panel_title),
                    body = message ?: strEpgSourceSelect,
                    badge = "Phase 04",
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
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
                            pendingDelete = null
                            selectedSourceId = null
                            editor = EpgSourceEditorState.newSource()
                            showEditor = false
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

