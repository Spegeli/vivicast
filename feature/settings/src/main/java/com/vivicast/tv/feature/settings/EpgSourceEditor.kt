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
import com.vivicast.tv.data.epg.EpgContentSummary
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
internal fun EpgSourceEditor(
    editor: EpgSourceEditorState,
    providers: List<Provider>,
    selectedProviderId: String?,
    providerLinks: List<ProviderEpgSource>,
    message: String?,
    onEditorChange: (EpgSourceEditorState) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLinkProvider: (providerId: String, sourceId: String, priority: Int) -> Unit,
    onUnlinkProvider: (providerId: String, sourceId: String) -> Unit,
    onMoveProviderLink: (providerId: String, sourceId: String, direction: EpgSourcePriorityDirection) -> Unit,
    duplicateName: Boolean = false,
    duplicateUrlName: String? = null,
    connectionTestStatus: ConnectionTestStatus = ConnectionTestStatus.Idle,
    connectionSummary: EpgContentSummary? = null,
    connectionError: String? = null,
    onTestConnection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Blank name reddens only after a save attempt (mirrors the playlist editor).
    var showNameBlankError by remember { mutableStateOf(false) }
    val nameBlank = editor.name.isBlank()
    // Start focus on the name field (like the playlist editor). Wait one frame, else focus escapes to
    // the top nav bar during the overview→editor swap.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }
    // A blank URL is only required for a new source (edit keeps the stored one). Reddens after a save
    // attempt, mirroring the name field / playlist editor.
    val urlFocus = remember { FocusRequester() }
    var showUrlBlankError by remember { mutableStateOf(false) }
    val urlBlank = !editor.isEditing && editor.url.isBlank()
    // On a blocked save, jump focus to the first bad field (name → URL), like the playlist editor.
    var pendingErrorFocus by remember { mutableStateOf<EpgEditorErrorFocus?>(null) }
    LaunchedEffect(pendingErrorFocus) {
        val target = pendingErrorFocus ?: return@LaunchedEffect
        awaitFrame()
        runCatching { (if (target == EpgEditorErrorFocus.Name) firstFocus else urlFocus).requestFocus() }
        pendingErrorFocus = null
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        // Enable/disable toggle above the name (edit only, like the playlist editor). A new source is
        // active by default, so add mode needs no toggle.
        if (editor.isEditing) {
            item(key = "active") {
                VivicastSettingsRow(
                    title = stringResource(R.string.settings_provider_active_label),
                    help = "",
                    value = if (editor.isActive) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                    onClick = { onEditorChange(editor.copy(isActive = !editor.isActive)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Name field stays the first item with a stable key so inserting the duplicate warning below it
        // never re-creates the focused field (which would let focus escape to the top nav bar).
        item(key = "name") {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                ProviderTextField(
                    label = stringResource(R.string.settings_provider_name_label),
                    value = editor.name,
                    placeholder = stringResource(R.string.settings_epg_name_placeholder),
                    onValueChange = { onEditorChange(editor.copy(name = it)) },
                    focusRequester = firstFocus,
                    isError = duplicateName || (showNameBlankError && nameBlank),
                    maxLength = 25,
                )
                VivicastDialogError(
                    if (duplicateName) stringResource(R.string.settings_epg_name_exists_body) else null,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                // URL field and its connection-test button share one row (mirrors the playlist editor).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ProviderTextField(
                        label = stringResource(R.string.m3u_source_url),
                        value = editor.url,
                        placeholder = if (editor.isEditing) stringResource(R.string.settings_provider_placeholder_reset) else "https://...",
                        onValueChange = { onEditorChange(editor.copy(url = it)) },
                        modifier = Modifier.weight(1f),
                        focusRequester = urlFocus,
                        secret = editor.isEditing,
                        isError = connectionTestStatus == ConnectionTestStatus.Failed ||
                            duplicateUrlName != null ||
                            (showUrlBlankError && urlBlank),
                        maxLength = 250,
                    )
                    ConnectionTestButton(status = connectionTestStatus, onClick = onTestConnection)
                }
                // Missing / duplicate URL — red inline hint (no note); .gz/.xz ignored for duplicates.
                when {
                    showUrlBlankError && urlBlank -> VivicastDialogError(stringResource(R.string.validation_epg_url_missing))
                    duplicateUrlName != null -> VivicastDialogError(stringResource(R.string.settings_epg_url_exists, duplicateUrlName))
                }
                // Failed-test reason as a red inline hint (no note).
                if (connectionError != null) VivicastDialogError(connectionError)
                // Parsed breakdown after a passed test, so stale counts never linger.
                if (connectionTestStatus == ConnectionTestStatus.Passed && connectionSummary != null) {
                    // Same styling as the playlist "Found in this playlist" summary (default BodyText color).
                    BodyText(stringResource(R.string.settings_epg_test_summary, connectionSummary.channels))
                }
            }
        }

        item {
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
                        BasicText(stringResource(R.string.settings_epg_timeshift_label), style = VivicastTypography.LabelLarge)
                        BodyText(stringResource(R.string.settings_epg_timeshift_help), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill("-30", modifier = Modifier.width(82.dp), onClick = {
                            onEditorChange(editor.copy(timeShiftMinutes = (editor.timeShiftMinutes - 30).coerceAtLeast(-720)))
                        })
                        BasicText(stringResource(R.string.settings_epg_timeshift_value, editor.timeShiftMinutes), style = VivicastTypography.LabelLarge)
                        ActionPill("+30", modifier = Modifier.width(82.dp), onClick = {
                            onEditorChange(editor.copy(timeShiftMinutes = (editor.timeShiftMinutes + 30).coerceAtMost(720)))
                        })
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ActionPill(
                    label = stringResource(R.string.common_cancel),
                    modifier = Modifier.width(132.dp),
                    onClick = onCancel,
                )
                ActionPill(
                    label = stringResource(R.string.common_save),
                    modifier = Modifier.width(150.dp),
                    selected = true,
                    onClick = {
                        // A blocked save reddens the offending field and jumps focus to it
                        // (name → URL), like the playlist editor.
                        val errorFocus = when {
                            nameBlank || duplicateName -> EpgEditorErrorFocus.Name
                            urlBlank || duplicateUrlName != null -> EpgEditorErrorFocus.Url
                            else -> null
                        }
                        if (errorFocus == null) {
                            onSave()
                        } else {
                            showNameBlankError = true
                            showUrlBlankError = true
                            pendingErrorFocus = errorFocus
                        }
                    },
                )
                if (editor.isEditing) {
                    ActionPill(label = stringResource(R.string.settings_delete), modifier = Modifier.width(140.dp), onClick = onDelete)
                }
            }
        }

        if (editor.isEditing) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_provider_assignment_title),
                    body = stringResource(R.string.settings_epg_provider_assignment_body),
                    badge = "EPG",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (providers.isEmpty()) {
                item {
                    InfoPanel(
                        title = stringResource(R.string.common_no_provider),
                        body = stringResource(R.string.settings_epg_no_providers_for_source_body),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(providers, key = { it.id }) { provider ->
                    FocusPanel(
                        selected = provider.id == selectedProviderId,
                        onClick = { onSelectProvider(provider.id) },
                        onFocused = { onSelectProvider(provider.id) },
                        modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
                        contentPadding = VivicastSpacing.Space4,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                                BasicText(provider.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BodyText(provider.status.localizedLabel(), maxLines = 1)
                            }
                            val link = if (provider.id == selectedProviderId) {
                                providerLinks.firstOrNull { it.epgSourceId == editor.sourceId }
                            } else {
                                null
                            }
                            StatusBadge(
                                label = link?.let { stringResource(R.string.settings_epg_priority, it.priority) } ?: stringResource(R.string.settings_epg_not_assigned),
                                tone = if (link != null) VivicastColors.Success else VivicastColors.SurfaceHigh,
                            )
                        }
                    }
                }

                item {
                    val providerId = selectedProviderId
                    val sourceId = editor.sourceId
                    val existingLink = providerLinks.firstOrNull { it.epgSourceId == sourceId }
                    val nextPriority = existingLink?.priority ?: (providerLinks.maxOfOrNull { it.priority } ?: 0) + 1
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                            ActionPill(
                                label = existingLink?.let { stringResource(R.string.settings_epg_priority, it.priority) } ?: stringResource(R.string.settings_epg_use_as_priority, nextPriority),
                                modifier = Modifier.width(270.dp),
                                selected = existingLink != null,
                                onClick = {
                                    if (providerId != null && sourceId != null && existingLink == null) {
                                        onLinkProvider(providerId, sourceId, nextPriority)
                                    }
                                },
                            )
                            if (existingLink != null) {
                                ActionPill(
                                    label = stringResource(R.string.common_remove),
                                    modifier = Modifier.width(140.dp),
                                    onClick = {
                                        if (providerId != null && sourceId != null) {
                                            onUnlinkProvider(providerId, sourceId)
                                        }
                                    },
                                )
                            }
                        }
                        if (existingLink != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                                val canMoveUp = existingLink.priority > 1
                                val canMoveDown = existingLink.priority < providerLinks.size
                                ActionPill(
                                    label = if (canMoveUp) stringResource(R.string.settings_epg_higher) else stringResource(R.string.settings_epg_top),
                                    modifier = Modifier.width(128.dp),
                                    selected = !canMoveUp,
                                    onClick = {
                                        if (providerId != null && sourceId != null && canMoveUp) {
                                            onMoveProviderLink(providerId, sourceId, EpgSourcePriorityDirection.Up)
                                        }
                                    },
                                )
                                ActionPill(
                                    label = if (canMoveDown) stringResource(R.string.settings_epg_lower) else stringResource(R.string.settings_epg_bottom),
                                    modifier = Modifier.width(128.dp),
                                    selected = !canMoveDown,
                                    onClick = {
                                        if (providerId != null && sourceId != null && canMoveDown) {
                                            onMoveProviderLink(providerId, sourceId, EpgSourcePriorityDirection.Down)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
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
    }
}

@Composable
fun DeleteEpgSourceDialog(
    source: EpgSource,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocusRequester,
        modifier = Modifier.testTag(deleteEpgSourceDialogTag(source.id)),
    ) {
        InfoPanel(
            title = stringResource(R.string.settings_epg_delete_confirm),
            body = stringResource(R.string.settings_epg_delete_body),
            badge = source.name,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.settings_delete),
            onPrimary = onDelete,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = deleteEpgSourceConfirmTag(source.id),
            secondaryTestTag = deleteEpgSourceCancelTag(source.id),
            secondaryFocusRequester = cancelFocusRequester,
        )
    }
}

fun deleteEpgSourceDialogTag(sourceId: String): String = "settings-delete-epg-source-dialog-$sourceId"
fun deleteEpgSourceCancelTag(sourceId: String): String = "settings-delete-epg-source-cancel-$sourceId"
fun deleteEpgSourceConfirmTag(sourceId: String): String = "settings-delete-epg-source-confirm-$sourceId"

private enum class EpgEditorErrorFocus { Name, Url }

internal data class EpgSourceEditorState(
    val sourceId: String?,
    val name: String,
    val url: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
) {
    val isEditing: Boolean get() = sourceId != null

    fun validationMessage(msgNameMissing: String, msgUrlMissing: String): String? {
        if (name.isBlank()) return msgNameMissing
        if (!isEditing && url.isBlank()) return msgUrlMissing
        return null
    }

    fun toEditRequest(): EpgSourceEditRequest =
        EpgSourceEditRequest(
            sourceId = sourceId,
            name = name,
            url = url.ifBlank { null },
            timeShiftMinutes = timeShiftMinutes,
            isActive = isActive,
        )

    companion object {
        fun newSource(): EpgSourceEditorState =
            EpgSourceEditorState(
                sourceId = null,
                name = "",
                url = "",
                timeShiftMinutes = 0,
                isActive = true,
            )

        fun from(source: EpgSource): EpgSourceEditorState =
            EpgSourceEditorState(
                sourceId = source.id,
                name = source.name,
                url = "",
                timeShiftMinutes = source.timeShiftMinutes,
                isActive = source.isActive,
            )
    }
}

