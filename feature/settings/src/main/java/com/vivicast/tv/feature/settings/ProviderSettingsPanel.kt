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
internal fun ProviderSettingsPanel(
    providerRepository: ProviderRepository,
    providers: List<Provider>,
    onTestProviderConnection: suspend (ProviderCreateRequest) -> String?,
    onPickM3uFile: ((String, String) -> Unit) -> Unit = {},
    onProviderSaved: (String) -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(ProviderEditorState.newProvider(ProviderType.M3u)) }
    var editorStep by remember { mutableStateOf(ProviderEditorStep.Name) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }
    var connectionTestStatus by remember { mutableStateOf(ConnectionTestStatus.Idle) }
    val strProviderSaved = stringResource(R.string.settings_provider_msg_playlist_saved)
    val strProviderEnabled = stringResource(R.string.settings_provider_msg_enabled)
    val strProviderDisabled = stringResource(R.string.settings_provider_msg_disabled)
    val strProviderSaveFailed = stringResource(R.string.settings_provider_msg_save_failed)
    val strProviderStatusFailed = stringResource(R.string.settings_provider_msg_status_failed)
    val strProviderDeleteFailed = stringResource(R.string.settings_provider_msg_delete_failed)
    val strProviderDeleted = stringResource(R.string.settings_provider_msg_deleted)
    val strProviderChecking = stringResource(R.string.settings_provider_msg_checking)
    val strProviderConnected = stringResource(R.string.settings_provider_msg_connected)
    val strProviderDuplicate = stringResource(R.string.settings_provider_msg_name_check)
    val strProviderSectionBody = stringResource(R.string.settings_provider_section_body)
    val strValidationNameMissing = stringResource(R.string.validation_name_missing)
    val strValidationContentType = stringResource(R.string.validation_content_type_required)
    val strValidationXtreamServer = stringResource(R.string.validation_xtream_server_missing)
    val strValidationXtreamUser = stringResource(R.string.validation_xtream_username_missing)
    val strValidationXtreamPass = stringResource(R.string.validation_xtream_password_missing)
    val strValidationConnTest = stringResource(R.string.validation_connection_test_required)
    val strValidationM3uUrl = stringResource(R.string.validation_m3u_url_missing)
    val strValidationM3uFile = stringResource(R.string.validation_m3u_file_missing)
    fun ProviderEditorState.validationMessageResolved(requireConnectionTest: Boolean) = validationMessage(
        requireConnectionTest, strValidationNameMissing, strValidationContentType,
        strValidationXtreamServer, strValidationXtreamUser, strValidationXtreamPass,
        strValidationConnTest, strValidationM3uUrl, strValidationM3uFile,
    )
    fun ProviderEditorState.connectionTestRequestMessageResolved() = connectionTestRequestMessage(
        strValidationNameMissing, strValidationXtreamServer, strValidationXtreamUser, strValidationXtreamPass,
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

    val duplicateName = editor.name.isNotBlank() &&
        providers.any { provider ->
            provider.id != editor.providerId && provider.name.equals(editor.name.trim(), ignoreCase = true)
        }

    var existingM3uUrls by remember { mutableStateOf<List<ProviderUrlEntry>>(emptyList()) }
    LaunchedEffect(providers) {
        existingM3uUrls = providers.mapNotNull { provider ->
            val credentials = runCatching { providerRepository.getCredentials(provider.id) }.getOrNull()
            (credentials as? ProviderCredentials.M3u)
                ?.takeIf { it.sourceMode == M3uSourceMode.Url }
                ?.url
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { ProviderUrlEntry(provider.id, it, provider.name) }
        }
    }
    val duplicateUrlName = if (editor.type == ProviderType.M3u &&
        editor.m3uSourceMode == M3uSourceMode.Url && editor.m3uUrl.isNotBlank()
    ) {
        existingM3uUrls.firstOrNull { it.providerId != editor.providerId && it.url == editor.m3uUrl.trim() }?.name
    } else {
        null
    }

    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
        ProviderOverviewPanel(
            providers = providers,
            message = message,
            firstFocusModifier = firstFocusModifier,
            onAddProvider = {
                selectedProviderId = null
                editor = ProviderEditorState.newProvider(ProviderType.M3u)
                editorStep = ProviderEditorStep.Name
                showEditor = true
                message = null
                connectionTestStatus = ConnectionTestStatus.Idle
            },
            onRefreshAll = {
                scope.launch {
                    if (providers.any { it.status == ProviderStatus.Refreshing }) return@launch
                    val refreshableProviders = providers.filter { provider ->
                        provider.isActive &&
                            when (val credentials = providerRepository.getCredentials(provider.id)) {
                                is ProviderCredentials.M3u -> credentials.sourceMode.isAutomaticallyRefreshable
                                is ProviderCredentials.Xtream -> true
                                null -> false
                            }
                    }
                    refreshableProviders.forEach { provider -> onProviderSaved(provider.id) }
                }
            },
            onOpenProvider = { provider ->
                selectedProviderId = provider.id
                editor = ProviderEditorState.from(provider)
                editorStep = ProviderEditorStep.Edit
                showEditor = true
                message = null
                scope.launch {
                    val credentials = runCatching { providerRepository.getCredentials(provider.id) }.getOrNull()
                    if (selectedProviderId == provider.id) {
                        editor = ProviderEditorState.from(provider, credentials)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showEditor) {
        val dismissEditor: () -> Unit = {
            selectedProviderId = null
            editor = ProviderEditorState.newProvider(ProviderType.M3u)
            editorStep = ProviderEditorStep.Name
            showEditor = false
            message = null
        }
        VivicastDialog(
            onDismiss = dismissEditor,
            width = VivicastDialogWidth.Wide,
            heightCap = 560.dp,
            modifier = Modifier.testTag("settings-provider-editor-dialog"),
        ) {
            if (!editor.isEditing) {
                    ProviderAddFlow(
                        editor = editor,
                        step = editorStep,
                        duplicateName = duplicateName,
                        duplicateUrlName = duplicateUrlName,
                        message = message,
                        onStepChange = {
                            editorStep = it
                            connectionTestStatus = ConnectionTestStatus.Idle
                        },
                        onEditorChange = {
                            editor = it
                            message = null
                            connectionTestStatus = ConnectionTestStatus.Idle
                        },
                        connectionTestStatus = connectionTestStatus,
                        onTestConnection = {
                            val validationMessage = editor.connectionTestRequestMessageResolved()
                            when {
                                duplicateName -> message = strProviderDuplicate
                                duplicateUrlName != null -> Unit
                                validationMessage != null -> message = validationMessage
                                else -> {
                                    message = null
                                    connectionTestStatus = ConnectionTestStatus.Testing
                                    scope.launch {
                                        val errorMessage = onTestProviderConnection(editor.toConnectionTestRequest())
                                        if (errorMessage == null) {
                                            editor = editor.copy(connectionTestPassed = true)
                                            connectionTestStatus = ConnectionTestStatus.Passed
                                            message = null
                                        } else {
                                            editor = editor.copy(connectionTestPassed = false)
                                            connectionTestStatus = ConnectionTestStatus.Failed
                                            message = errorMessage
                                        }
                                    }
                                }
                            }
                        },
                        onSave = {
                            if (duplicateName) {
                                message = strProviderDuplicate
                                return@ProviderAddFlow
                            }
                            if (duplicateUrlName != null) {
                                return@ProviderAddFlow
                            }
                            val validationMessage = editor.validationMessageResolved(requireConnectionTest = false)
                            if (validationMessage != null) {
                                message = validationMessage
                                return@ProviderAddFlow
                            }
                            scope.launch {
                                connectionTestStatus = ConnectionTestStatus.Testing
                                message = null
                                val errorMessage = onTestProviderConnection(editor.toConnectionTestRequest())
                                if (errorMessage != null) {
                                    editor = editor.copy(connectionTestPassed = false)
                                    connectionTestStatus = ConnectionTestStatus.Failed
                                    message = errorMessage
                                    return@launch
                                }
                                editor = editor.copy(connectionTestPassed = true)
                                connectionTestStatus = ConnectionTestStatus.Passed
                                runCatching { providerRepository.createProvider(editor.toCreateRequest()) }
                                    .onSuccess { result ->
                                        selectedProviderId = result.provider.id
                                        editor = ProviderEditorState.from(result.provider)
                                        editorStep = ProviderEditorStep.Edit
                                        connectionTestStatus = ConnectionTestStatus.Idle
                                        showEditor = false
                                        onProviderSaved(result.provider.id)
                                    }
                                    .onFailure { error ->
                                        message = strProviderSaveFailed.format(error.message ?: "?")
                                    }
                            }
                        },
                        onCancel = {
                            selectedProviderId = null
                            editor = ProviderEditorState.newProvider(ProviderType.M3u)
                            editorStep = ProviderEditorStep.Name
                            connectionTestStatus = ConnectionTestStatus.Idle
                            showEditor = false
                            message = null
                        },
                        onPickM3uFile = onPickM3uFile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                ProviderEditor(
                    editor = editor,
                    duplicateName = duplicateName,
                    message = message,
                    onEditorChange = {
                        editor = it
                        message = null
                    },
                    onTestConnection = {
                        val validationMessage = editor.connectionTestRequestMessageResolved()
                        when {
                            duplicateName -> message = strProviderDuplicate
                            validationMessage != null -> message = validationMessage
                            else -> {
                                message = strProviderChecking
                                scope.launch {
                                    val errorMessage = onTestProviderConnection(editor.toConnectionTestRequest())
                                    if (errorMessage == null) {
                                        editor = editor.copy(connectionTestPassed = true)
                                        message = strProviderConnected
                                    } else {
                                        editor = editor.copy(connectionTestPassed = false)
                                        message = errorMessage
                                    }
                                }
                            }
                        }
                    },
                    onSave = {
                    if (duplicateName) {
                        message = strProviderDuplicate
                        return@ProviderEditor
                    }
                    val validationMessage = editor.validationMessageResolved(requireConnectionTest = true)
                    if (validationMessage != null) {
                        message = validationMessage
                        return@ProviderEditor
                    }
                    scope.launch {
                        runCatching {
                            if (editor.isEditing) {
                                providerRepository.updateProvider(editor.toUpdateRequest())
                            } else {
                                providerRepository.createProvider(editor.toCreateRequest())
                            }
                        }.onSuccess { result ->
                            selectedProviderId = null
                            editor = ProviderEditorState.newProvider(ProviderType.M3u)
                            editorStep = ProviderEditorStep.Name
                            showEditor = false
                            onProviderSaved(result.provider.id)
                            message = strProviderSaved
                        }.onFailure { error ->
                            message = strProviderSaveFailed.format(error.message ?: "?")
                        }
                    }
                    },
                    onToggleEnabled = {
                    val provider = providers.firstOrNull { it.id == editor.providerId } ?: return@ProviderEditor
                    scope.launch {
                        val enabled = !provider.isActive
                        runCatching { providerRepository.setProviderEnabled(provider.id, enabled) }
                            .onSuccess {
                                message = if (enabled) strProviderEnabled else strProviderDisabled
                            }
                            .onFailure { error -> message = strProviderStatusFailed.format(error.message ?: "?") }
                    }
                    },
                    onDelete = {
                    pendingDelete = providers.firstOrNull { it.id == editor.providerId }
                    },
                    onPickM3uFile = onPickM3uFile,
                    modifier = Modifier.fillMaxWidth(),
                )
                }
            }
        }

    pendingDelete?.let { provider ->
        DeleteProviderDialog(
            provider = provider,
            onCancel = { pendingDelete = null },
            onDelete = {
                scope.launch {
                    runCatching { providerRepository.deleteProvider(provider.id) }
                        .onSuccess {
                            pendingDelete = null
                            selectedProviderId = null
                            editor = ProviderEditorState.newProvider(ProviderType.M3u)
                            showEditor = false
                            message = strProviderDeleted
                        }
                        .onFailure { error ->
                            pendingDelete = null
                            message = strProviderDeleteFailed.format(error.message ?: "?")
                        }
                }
            },
        )
    }
}

@Composable
private fun ProviderOverviewPanel(
    providers: List<Provider>,
    message: String?,
    firstFocusModifier: Modifier,
    onAddProvider: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenProvider: (Provider) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    .testTag("settings-playlist-add-action")
                    ,
                onClick = onAddProvider,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_playlist_refresh_all),
                help = stringResource(R.string.settings_provider_help_refresh_all),
                value = stringResource(R.string.settings_playlist_refresh_value),
                onClick = onRefreshAll,
            )
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
                    onClick = { onOpenProvider(provider) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProviderSourceCard(
    provider: Provider,
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
                    if (!provider.isActive) {
                        StatusBadge(stringResource(R.string.common_inactive), tone = VivicastColors.Warning)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    StatusBadge(provider.type.label)
                    StatusBadge(if (provider.includeLiveTv) stringResource(R.string.nav_live_tv) else stringResource(R.string.settings_provider_live_tv_off), tone = if (provider.includeLiveTv) VivicastColors.Info else VivicastColors.SurfaceHigh)
                    StatusBadge(if (provider.includeMovies) stringResource(R.string.nav_movies_label) else stringResource(R.string.settings_provider_movies_off), tone = if (provider.includeMovies) VivicastColors.Info else VivicastColors.SurfaceHigh)
                    StatusBadge(if (provider.includeSeries) stringResource(R.string.nav_series_label) else stringResource(R.string.settings_provider_series_off), tone = if (provider.includeSeries) VivicastColors.Info else VivicastColors.SurfaceHigh)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                BodyText(stringResource(R.string.settings_provider_updated_format, provider.updatedAt.toBackupTimestamp()), maxLines = 1)
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
                        if (!provider.isActive) {
                            StatusBadge(stringResource(R.string.common_inactive), tone = VivicastColors.Warning)
                        }
                    }
                    BodyText(provider.importSummary(), maxLines = 1)
                }
            }
        }
    }
}

private data class ProviderUrlEntry(val providerId: String?, val url: String, val name: String)

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
        ProviderStatus.Disabled -> VivicastColors.SurfaceHigh
        ProviderStatus.CredentialsRequired -> VivicastColors.Error
    }

@Composable
private fun Provider.importSummary(): String = listOfNotNull(
    stringResource(R.string.nav_live_tv).takeIf { includeLiveTv },
    stringResource(R.string.nav_movies_label).takeIf { includeMovies },
    stringResource(R.string.nav_series_label).takeIf { includeSeries },
).takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: stringResource(R.string.settings_provider_no_content)
