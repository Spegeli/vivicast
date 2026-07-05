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
import androidx.compose.foundation.shape.RoundedCornerShape
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
internal fun ProviderAddFlow(
    editor: ProviderEditorState,
    step: ProviderEditorStep,
    duplicateName: Boolean,
    duplicateUrlName: String?,
    message: String?,
    connectionTestStatus: ConnectionTestStatus,
    onStepChange: (ProviderEditorStep) -> Unit,
    onEditorChange: (ProviderEditorState) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onPickM3uFile: ((String, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    var nameError by remember { mutableStateOf(false) }
    // M3U file classification is deferred to "Check file" and runs off the main thread, so picking a
    // large file returns to the app instantly instead of blocking on the parse+classify.
    var fileSummary by remember(editor.m3uContent) { mutableStateOf<M3uContentSummary?>(null) }
    var summarizingFile by remember(editor.m3uContent) { mutableStateOf(false) }
    val summaryScope = rememberCoroutineScope()
    LaunchedEffect(step) {
        runCatching { firstFocus.requestFocus() }
    }
    val errorText = when {
        duplicateName -> stringResource(R.string.settings_provider_name_exists_body)
        duplicateUrlName != null -> stringResource(R.string.settings_provider_url_exists, duplicateUrlName)
        else -> message
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item(key = "step-title") {
            SectionTitle(stringResource(step.titleRes))
        }
        item(key = "step-error") {
            VivicastDialogError(errorText)
        }
        when (step) {
            ProviderEditorStep.Name -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_name_required),
                        value = editor.name,
                        placeholder = stringResource(R.string.settings_provider_name_placeholder),
                        onValueChange = {
                            nameError = false
                            onEditorChange(editor.copy(name = it, connectionTestPassed = false))
                        },
                        focusRequester = firstFocus,
                        isError = nameError,
                        maxLength = 25,
                    )
                }
                item {
                    ProviderFlowActions(
                        primaryLabel = stringResource(R.string.common_next),
                        onPrimary = {
                            if (editor.name.isNotBlank() && !duplicateName) {
                                nameError = false
                                onStepChange(ProviderEditorStep.Type)
                            } else {
                                nameError = true
                                runCatching { firstFocus.requestFocus() }
                            }
                        },
                        secondaryLabel = stringResource(R.string.common_cancel),
                        onSecondary = onCancel,
                    )
                }
            }
            ProviderEditorStep.Type -> {
                item {
                    VivicastButtonRow {
                        ProviderChoiceButton(
                            label = "M3U",
                            modifier = Modifier.focusRequester(firstFocus),
                            selected = editor.type == ProviderType.M3u,
                            onClick = { onEditorChange(editor.copy(type = ProviderType.M3u, connectionTestPassed = false)) },
                        )
                        ProviderChoiceButton(
                            label = "Xtream Codes",
                            selected = editor.type == ProviderType.Xtream,
                            onClick = { onEditorChange(editor.copy(type = ProviderType.Xtream, connectionTestPassed = false)) },
                        )
                    }
                }
                item {
                    ProviderFlowActions(
                        primaryLabel = stringResource(R.string.common_next),
                        onPrimary = {
                            onStepChange(if (editor.type == ProviderType.M3u) ProviderEditorStep.M3uInput else ProviderEditorStep.Xtream)
                        },
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.Name) },
                        cancelLabel = stringResource(R.string.common_cancel),
                        onCancel = onCancel,
                    )
                }
            }
            ProviderEditorStep.M3uInput -> {
                item {
                    VivicastButtonRow {
                        M3uSourceMode.entries.forEachIndexed { index, mode ->
                            ProviderChoiceButton(
                                label = stringResource(mode.labelRes),
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
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
                item {
                    ProviderFlowActions(
                        primaryLabel = stringResource(R.string.common_next),
                        onPrimary = { onStepChange(editor.m3uSourceMode.addStep) },
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.Type) },
                        cancelLabel = stringResource(R.string.common_cancel),
                        onCancel = onCancel,
                    )
                }
            }
            ProviderEditorStep.M3uUrl -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_url_required),
                        value = editor.m3uUrl,
                        placeholder = "https://...",
                        onValueChange = { onEditorChange(editor.copy(m3uUrl = it, connectionTestPassed = false)) },
                        focusRequester = firstFocus,
                        maxLength = 250,
                    )
                }
                if (editor.m3uUrl.startsWith("http://", ignoreCase = true)) {
                    item { InfoPanel(title = stringResource(R.string.common_insecure_connection), body = stringResource(R.string.common_insecure_body), badge = "HTTP", modifier = Modifier.fillMaxWidth()) }
                }
                item {
                    VivicastButtonRow {
                        ConnectionTestButton(
                            status = connectionTestStatus,
                            onClick = onTestConnection,
                        )
                    }
                }
                item {
                    VivicastDialogActions(
                        primaryLabel = stringResource(R.string.common_save),
                        onPrimary = onSave,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.M3uInput) },
                        tertiaryLabel = stringResource(R.string.common_cancel),
                        onTertiary = onCancel,
                    )
                }
            }
            ProviderEditorStep.M3uFile -> {
                item {
                    val currentSummary = fileSummary
                    BodyText(
                        when {
                            editor.m3uContent.isBlank() || editor.m3uFileName.isBlank() ->
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
                item {
                    VivicastButtonRow {
                        ActionPill(
                            label = stringResource(R.string.settings_provider_file_pick),
                            modifier = Modifier.focusRequester(firstFocus),
                            selected = editor.m3uContent.isNotBlank(),
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
                        ActionPill(
                            label = when (connectionTestStatus) {
                                ConnectionTestStatus.Testing -> stringResource(R.string.settings_provider_msg_checking)
                                ConnectionTestStatus.Passed -> "✓ " + stringResource(R.string.settings_provider_file_test_ok)
                                ConnectionTestStatus.Failed -> "✗ " + stringResource(R.string.settings_provider_file_test_fail)
                                else -> stringResource(R.string.settings_provider_file_test)
                            },
                            selected = connectionTestStatus == ConnectionTestStatus.Passed,
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
                    }
                }
                item {
                    VivicastDialogActions(
                        primaryLabel = stringResource(R.string.common_save),
                        onPrimary = onSave,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.M3uInput) },
                        tertiaryLabel = stringResource(R.string.common_cancel),
                        onTertiary = onCancel,
                    )
                }
            }
            ProviderEditorStep.Xtream -> {
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_server_required),
                        value = editor.xtreamServerUrl,
                        placeholder = "http://host:8080",
                        onValueChange = { onEditorChange(editor.copy(xtreamServerUrl = it, connectionTestPassed = false)) },
                        focusRequester = firstFocus,
                        maxLength = 250,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_username_required),
                        value = editor.xtreamUsername,
                        placeholder = stringResource(R.string.settings_provider_username_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamUsername = it, connectionTestPassed = false)) },
                        maxLength = 100,
                    )
                }
                item {
                    ProviderTextField(
                        label = stringResource(R.string.settings_provider_password_required),
                        value = editor.xtreamPassword,
                        placeholder = stringResource(R.string.settings_provider_password_label),
                        onValueChange = { onEditorChange(editor.copy(xtreamPassword = it, connectionTestPassed = false)) },
                        secret = true,
                        allowReveal = true,
                        maxLength = 100,
                    )
                }
                if (editor.xtreamServerUrl.startsWith("http://", ignoreCase = true)) {
                    item { InfoPanel(title = stringResource(R.string.common_insecure_connection), body = stringResource(R.string.common_insecure_body), badge = "HTTP", modifier = Modifier.fillMaxWidth()) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                        BodyText(stringResource(R.string.settings_provider_import_section), maxLines = 1)
                        ImportCheckboxRow(
                            label = stringResource(R.string.nav_live_tv),
                            checked = editor.includeLiveTv,
                            onToggle = { onEditorChange(editor.copy(includeLiveTv = !editor.includeLiveTv, connectionTestPassed = false)) },
                        )
                        ImportCheckboxRow(
                            label = stringResource(R.string.nav_movies_label),
                            checked = editor.includeMovies,
                            onToggle = { onEditorChange(editor.copy(includeMovies = !editor.includeMovies, connectionTestPassed = false)) },
                        )
                        ImportCheckboxRow(
                            label = stringResource(R.string.nav_series_label),
                            checked = editor.includeSeries,
                            onToggle = { onEditorChange(editor.copy(includeSeries = !editor.includeSeries, connectionTestPassed = false)) },
                        )
                    }
                }
                item {
                    VivicastButtonRow {
                        ConnectionTestButton(
                            status = connectionTestStatus,
                            onClick = onTestConnection,
                        )
                    }
                }
                item {
                    VivicastDialogActions(
                        primaryLabel = stringResource(R.string.common_save),
                        onPrimary = onSave,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = { onStepChange(ProviderEditorStep.Type) },
                        tertiaryLabel = stringResource(R.string.common_cancel),
                        onTertiary = onCancel,
                    )
                }
            }
            ProviderEditorStep.Edit -> Unit
        }
    }
}

@Composable
private fun ProviderFlowActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    cancelLabel: String? = null,
    onCancel: () -> Unit = {},
) {
    VivicastDialogActions(
        primaryLabel = primaryLabel,
        onPrimary = onPrimary,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        tertiaryLabel = cancelLabel,
        onTertiary = onCancel,
    )
}

private val m3uContentSummarizer = M3uContentSummarizer()

/** Classifies M3U content into channels/movies/series for the add/edit preview. Memoize on [content]. */
internal fun m3uContentSummary(content: String): M3uContentSummary = m3uContentSummarizer.summarize(content)

internal enum class ConnectionTestStatus { Idle, Testing, Passed, Failed }

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
        modifier = modifier.then(
            if (statusColor != null) {
                Modifier.border(VivicastBorders.FocusWidth, statusColor, VivicastShapes.CardRadius)
            } else {
                Modifier
            },
        ),
    ) { _ ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
        )
    }
}

