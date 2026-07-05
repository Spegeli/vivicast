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
import com.vivicast.tv.data.provider.M3uContentSummary
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
    message: String?,
    onEditorChange: (ProviderEditorState) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onPickM3uFile: ((String, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    // Inline panel (no dialog window): wait one frame so the field is placed before requesting focus,
    // otherwise focus escapes to the top nav bar and navigates away.
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }
    // M3U file classification runs off the main thread and only on "Verbindung testen", so picking a
    // large file in the edit dialog returns instantly instead of freezing on the parse+classify.
    var fileSummary by remember(editor.m3uContent) { mutableStateOf<M3uContentSummary?>(null) }
    var summarizingFile by remember(editor.m3uContent) { mutableStateOf(false) }
    val summaryScope = rememberCoroutineScope()
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item {
            InfoPanel(
                title = if (editor.isEditing) stringResource(R.string.common_edit) else stringResource(R.string.settings_provider_title_playlist),
                body = if (editor.isEditing) {
                    stringResource(R.string.settings_provider_xtream_stable)
                } else {
                    stringResource(R.string.settings_provider_xtream_credentials)
                },
                badge = editor.type.label,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (duplicateName) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_provider_msg_name_check),
                    body = stringResource(R.string.settings_provider_name_duplicate_body),
                    badge = stringResource(R.string.common_warning),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            ProviderTextField(
                label = stringResource(R.string.settings_provider_name_label),
                value = editor.name,
                placeholder = stringResource(R.string.settings_provider_name_placeholder),
                onValueChange = { onEditorChange(editor.copy(name = it)) },
                focusRequester = firstFocus,
                maxLength = 25,
            )
        }

        if (!editor.isEditing) {
            item {
                VivicastButtonRow {
                    ActionPill(
                        label = "M3U",
                        selected = editor.type == ProviderType.M3u,
                        onClick = { onEditorChange(ProviderEditorState.newProvider(ProviderType.M3u)) },
                    )
                    ActionPill(
                        label = "Xtream",
                        selected = editor.type == ProviderType.Xtream,
                        onClick = { onEditorChange(ProviderEditorState.newProvider(ProviderType.Xtream)) },
                    )
                }
            }
        }

        when (editor.type) {
            ProviderType.M3u -> {
                item {
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
                item {
                    when (editor.m3uSourceMode) {
                        M3uSourceMode.Url -> ProviderTextField(
                            label = stringResource(R.string.settings_provider_m3u_url_label),
                            value = editor.m3uUrl,
                            placeholder = if (editor.m3uHasExistingSource) stringResource(R.string.settings_provider_placeholder_reset) else "https://...",
                            onValueChange = {
                                onEditorChange(editor.copy(m3uUrl = it, m3uHasExistingSource = false, connectionTestPassed = false))
                            },
                            secret = editor.isEditing,
                            maxLength = 250,
                        )
                        M3uSourceMode.File -> Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                            InfoPanel(
                                title = stringResource(R.string.settings_provider_m3u_file_label),
                                body = if (editor.m3uContent.isBlank()) {
                                    if (editor.m3uHasExistingSource) {
                                        stringResource(R.string.settings_provider_file_saved)
                                    } else {
                                        stringResource(R.string.settings_provider_file_none)
                                    }
                                } else {
                                    stringResource(R.string.settings_provider_file_stage)
                                },
                                badge = stringResource(R.string.settings_epg_badge_manual),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ActionPill(
                                label = stringResource(R.string.settings_provider_file_pick),
                                modifier = Modifier.width(190.dp),
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
                            val currentSummary = fileSummary
                            if (editor.m3uFileName.isNotBlank()) {
                                BodyText(
                                    when {
                                        editor.m3uContent.isBlank() ->
                                            stringResource(R.string.settings_provider_file_label_empty)
                                        summarizingFile ->
                                            stringResource(R.string.settings_provider_file_analyzing, editor.m3uFileName)
                                        currentSummary != null ->
                                            stringResource(
                                                R.string.settings_provider_file_label,
                                                editor.m3uFileName,
                                                currentSummary.channels,
                                                currentSummary.movies,
                                                currentSummary.series,
                                            )
                                        else ->
                                            stringResource(R.string.settings_provider_file_needs_check, editor.m3uFileName)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            ProviderType.Xtream -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_server_label),
                        value = editor.xtreamServerUrl,
                        placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else "https://server.example",
                        onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it, connectionTestPassed = false)) },
                        secret = editor.isEditing,
                        maxLength = 250,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_username_label),
                        value = editor.xtreamUsername,
                        placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_username_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamUsername = it, connectionTestPassed = false)) },
                        secret = editor.isEditing,
                        maxLength = 100,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_password_label),
                        value = editor.xtreamPassword,
                        placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else stringResource(R.string.settings_provider_password_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamPassword = it, connectionTestPassed = false)) },
                        secret = true,
                        allowReveal = true,
                        maxLength = 100,
                    )
                }
            }
        }

        if (editor.type == ProviderType.Xtream) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    BodyText(stringResource(R.string.settings_provider_content_section), maxLines = 1)
                    VivicastButtonRow {
                        ActionPill(
                            label = stringResource(R.string.nav_live_tv),
                            selected = editor.includeLiveTv,
                            onClick = { onEditorChange(editor.copy(includeLiveTv = !editor.includeLiveTv)) },
                        )
                        ActionPill(
                            label = stringResource(R.string.nav_movies_label),
                            selected = editor.includeMovies,
                            onClick = { onEditorChange(editor.copy(includeMovies = !editor.includeMovies)) },
                        )
                        ActionPill(
                            label = stringResource(R.string.nav_series_label),
                            selected = editor.includeSeries,
                            onClick = { onEditorChange(editor.copy(includeSeries = !editor.includeSeries)) },
                        )
                    }
                }
            }
        }

        item {
            if (editor.isAutomaticallyRefreshable) {
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
        }

        if (!editor.isAutomaticallyRefreshable) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_provider_update_title),
                    body = stringResource(R.string.settings_provider_file_no_auto),
                    badge = stringResource(R.string.settings_epg_badge_manual),
                    modifier = Modifier.fillMaxWidth(),
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

        item {
            VivicastButtonRow {
                ActionPill(
                    label = stringResource(R.string.settings_provider_test_connection),
                    selected = editor.connectionTestPassed,
                    onClick = {
                        onTestConnection()
                        val content = editor.m3uContent
                        if (content.isNotBlank()) {
                            summarizingFile = true
                            summaryScope.launch {
                                val computed = withContext(Dispatchers.Default) { m3uContentSummary(content) }
                                fileSummary = computed
                                summarizingFile = false
                            }
                        }
                    },
                )
                ActionPill(label = stringResource(R.string.common_save), onClick = onSave)
                if (editor.isEditing) {
                    ActionPill(label = stringResource(R.string.settings_provider_toggle_enabled), onClick = onToggleEnabled)
                    ActionPill(label = stringResource(R.string.settings_delete), onClick = onDelete)
                }
            }
        }
    }
}

internal enum class ProviderEditorStep {
    Name,
    Type,
    M3uInput,
    M3uUrl,
    M3uFile,
    Xtream,
    Edit,
}

@get:StringRes
internal val ProviderEditorStep.titleRes: Int
    get() = when (this) {
        ProviderEditorStep.Name -> R.string.settings_provider_title_new
        ProviderEditorStep.Type -> R.string.settings_provider_step_type_title
        ProviderEditorStep.M3uInput -> R.string.settings_provider_step_m3u_input_title
        ProviderEditorStep.M3uUrl -> R.string.settings_provider_step_m3u_url_title
        ProviderEditorStep.M3uFile -> R.string.settings_provider_m3u_file_label
        ProviderEditorStep.Xtream -> R.string.settings_provider_step_xtream_title
        ProviderEditorStep.Edit -> R.string.settings_provider_title_edit
    }

@get:StringRes
private val ProviderEditorStep.bodyRes: Int
    get() = when (this) {
        ProviderEditorStep.Name -> R.string.settings_provider_step_name_body
        ProviderEditorStep.Type -> R.string.settings_provider_step_type_body
        ProviderEditorStep.M3uInput -> R.string.settings_provider_step_m3u_input_body
        ProviderEditorStep.M3uUrl -> R.string.settings_provider_step_m3u_url_body
        ProviderEditorStep.M3uFile -> R.string.settings_provider_file_import_only
        ProviderEditorStep.Xtream -> R.string.settings_provider_step_xtream_body
        ProviderEditorStep.Edit -> R.string.settings_provider_step_edit_body
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
        if (type == ProviderType.Xtream && !includeLiveTv && !includeMovies && !includeSeries) return msgContentType
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

    fun connectionTestRequestMessage(
        msgNameMissing: String,
        msgXtreamServer: String,
        msgXtreamUser: String,
        msgXtreamPass: String,
        msgM3uUrl: String,
        msgM3uFile: String,
    ): String? {
        if (name.isBlank()) return msgNameMissing
        return when (type) {
            ProviderType.M3u -> m3uSourceValidationMessage(allowExistingSource = false, msgM3uUrl, msgM3uFile)
            ProviderType.Xtream -> when {
                xtreamServerUrl.isBlank() -> msgXtreamServer
                xtreamUsername.isBlank() -> msgXtreamUser
                xtreamPassword.isBlank() -> msgXtreamPass
                else -> null
            }
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
                includeMovies = true,
                includeSeries = true,
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

internal val M3uSourceMode.addStep: ProviderEditorStep
    get() = when (this) {
        M3uSourceMode.Url -> ProviderEditorStep.M3uUrl
        M3uSourceMode.File -> ProviderEditorStep.M3uFile
    }

