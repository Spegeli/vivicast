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
internal fun ManualEpgMappingPanel(
    providers: List<Provider>,
    sources: List<EpgSource>,
    selectedProviderId: String?,
    providerLinks: List<ProviderEpgSource>,
    channels: List<Channel>,
    mappings: List<EpgChannelMapping>,
    selectedChannelId: String?,
    message: String?,
    onSelectProvider: (String) -> Unit,
    onSelectChannel: (String) -> Unit,
    onSetManualMapping: suspend (ManualEpgChannelMappingRequest) -> Result<Unit>,
    onClearManualMapping: suspend (providerId: String, channelId: String, epgSourceId: String) -> Result<Unit>,
    onMessage: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSourceId by remember(selectedProviderId) { mutableStateOf<String?>(null) }
    var epgChannelId by remember(selectedProviderId) { mutableStateOf("") }
    val linkedSources = remember(providerLinks, sources) {
        providerLinks
            .sortedBy { it.priority }
            .mapNotNull { link -> sources.firstOrNull { it.id == link.epgSourceId } }
    }
    val selectedMapping = mappings.firstOrNull { it.epgSourceId == selectedSourceId }
    val scope = rememberCoroutineScope()
    val strMappingAllRequired = stringResource(R.string.settings_epg_msg_mapping_all_required)
    val strMappingFailed = stringResource(R.string.settings_epg_msg_mapping_failed)
    val strMappingSaved = stringResource(R.string.settings_epg_msg_mapping_saved)
    val strMappingRemoved = stringResource(R.string.settings_epg_msg_mapping_removed)
    val strRemoveFailed = stringResource(R.string.settings_epg_msg_remove_failed)
    val strSelectionRequired = stringResource(R.string.settings_epg_msg_selection_required)

    LaunchedEffect(linkedSources) {
        if (selectedSourceId == null || linkedSources.none { it.id == selectedSourceId }) {
            selectedSourceId = linkedSources.firstOrNull()?.id
        }
    }

    LaunchedEffect(selectedProviderId, selectedChannelId, selectedSourceId, selectedMapping?.epgChannelId) {
        epgChannelId = selectedMapping?.epgChannelId.orEmpty()
    }

    if (providers.isEmpty()) {
        InfoPanel(
            title = stringResource(R.string.common_no_provider),
            body = stringResource(R.string.settings_epg_no_providers_body),
            badge = "EPG",
            modifier = modifier,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = modifier) {
        ManualMappingProviderList(
            providers = providers,
            selectedProviderId = selectedProviderId,
            onSelectProvider = {
                onSelectProvider(it)
                onMessage(null)
            },
            modifier = Modifier.weight(0.28f).fillMaxHeight(),
        )

        ManualMappingChannelList(
            channels = channels,
            mappings = mappings,
            selectedProviderId = selectedProviderId,
            selectedChannelId = selectedChannelId,
            onSelectChannel = {
                onSelectChannel(it)
                onMessage(null)
            },
            modifier = Modifier.weight(0.34f).fillMaxHeight(),
        )

        ManualMappingDetail(
            sources = linkedSources,
            selectedProviderId = selectedProviderId,
            selectedChannel = channels.firstOrNull { it.id == selectedChannelId },
            selectedSourceId = selectedSourceId,
            selectedMapping = selectedMapping,
            epgChannelId = epgChannelId,
            message = message,
            onSelectSource = {
                selectedSourceId = it
                onMessage(null)
            },
            onEpgChannelIdChange = { epgChannelId = it },
            onSave = {
                val providerId = selectedProviderId
                val channelId = selectedChannelId
                val sourceId = selectedSourceId
                val normalizedExternalId = epgChannelId.trim()
                if (providerId == null || channelId == null || sourceId == null || normalizedExternalId.isBlank()) {
                    onMessage(strMappingAllRequired)
                    return@ManualMappingDetail
                }
                scope.launch {
                    onSetManualMapping(
                        ManualEpgChannelMappingRequest(
                            providerId = providerId,
                            channelId = channelId,
                            epgSourceId = sourceId,
                            epgChannelId = normalizedExternalId,
                        ),
                    ).onSuccess {
                        onMessage(strMappingSaved)
                    }.onFailure { error ->
                        onMessage(strMappingFailed.format(error.message ?: "?"))
                    }
                }
            },
            onClear = {
                val providerId = selectedProviderId
                val channelId = selectedChannelId
                val sourceId = selectedSourceId
                if (providerId == null || channelId == null || sourceId == null) {
                    onMessage(strSelectionRequired)
                    return@ManualMappingDetail
                }
                scope.launch {
                    onClearManualMapping(providerId, channelId, sourceId)
                        .onSuccess {
                            epgChannelId = ""
                            onMessage(strMappingRemoved)
                        }
                        .onFailure { error ->
                            onMessage(strRemoveFailed.format(error.message ?: "?"))
                        }
                }
            },
            modifier = Modifier.weight(0.38f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ManualMappingProviderList(
    providers: List<Provider>,
    selectedProviderId: String?,
    onSelectProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (providers.isEmpty()) {
        InfoPanel(
            title = stringResource(R.string.common_no_provider),
            body = stringResource(R.string.settings_epg_no_providers_body2),
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        items(providers, key = { it.id }) { provider ->
            FocusPanel(
                selected = provider.id == selectedProviderId,
                onClick = { onSelectProvider(provider.id) },
                onFocused = { onSelectProvider(provider.id) },
                modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                    BasicText(provider.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BodyText(provider.status.localizedLabel(), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ManualMappingChannelList(
    channels: List<Channel>,
    mappings: List<EpgChannelMapping>,
    selectedProviderId: String?,
    selectedChannelId: String?,
    onSelectChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedProviderId == null) {
        InfoPanel(
            title = stringResource(R.string.settings_epg_select_provider),
            body = stringResource(R.string.settings_epg_select_provider_body),
            modifier = modifier,
        )
        return
    }
    if (channels.isEmpty()) {
        InfoPanel(
            title = stringResource(R.string.settings_epg_no_channels_label),
            body = stringResource(R.string.settings_epg_no_channels_body),
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        items(channels, key = { it.id }) { channel ->
            val hasManualMapping = mappings.any { it.channelId == channel.id && it.isManual }
            FocusPanel(
                selected = channel.id == selectedChannelId,
                onClick = { onSelectChannel(channel.id) },
                onFocused = { onSelectChannel(channel.id) },
                modifier = Modifier.fillMaxWidth().height(104.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                        BasicText(channel.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BodyText(channel.channelNumber?.let { stringResource(R.string.settings_epg_channel_number, it) } ?: channel.remoteId, maxLines = 1)
                    }
                    if (hasManualMapping) {
                        StatusBadge(stringResource(R.string.settings_epg_badge_manual), tone = VivicastColors.Success)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualMappingDetail(
    sources: List<EpgSource>,
    selectedProviderId: String?,
    selectedChannel: Channel?,
    selectedSourceId: String?,
    selectedMapping: EpgChannelMapping?,
    epgChannelId: String,
    message: String?,
    onSelectSource: (String) -> Unit,
    onEpgChannelIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(
                title = stringResource(R.string.settings_epg_manual_mapping),
                body = stringResource(R.string.settings_epg_manual_mapping_body),
                badge = selectedMapping?.let { if (it.isManual) stringResource(R.string.settings_epg_badge_manual) else stringResource(R.string.settings_epg_badge_auto) } ?: "EPG",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (selectedProviderId == null || selectedChannel == null) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_select_channel),
                    body = stringResource(R.string.settings_epg_select_channel_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }

        item {
            InfoPanel(
                title = selectedChannel.name,
                body = stringResource(R.string.settings_epg_remote_id_body, selectedChannel.remoteId),
                badge = selectedChannel.channelNumber?.let { stringResource(R.string.settings_epg_channel_number, it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (sources.isEmpty()) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_epg_no_source_linked),
                    body = stringResource(R.string.settings_epg_no_source_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }

        item {
            SectionTitle(stringResource(R.string.settings_epg_source_section))
        }

        items(sources, key = { it.id }) { source ->
            FocusPanel(
                selected = source.id == selectedSourceId,
                onClick = { onSelectSource(source.id) },
                onFocused = { onSelectSource(source.id) },
                modifier = Modifier.fillMaxWidth().height(92.dp),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                        BasicText(source.name, style = VivicastTypography.LabelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BodyText(stringResource(R.string.settings_epg_timeshift_format, source.timeShiftMinutes), maxLines = 1)
                    }
                    StatusBadge(if (source.isActive) stringResource(R.string.common_active) else stringResource(R.string.common_inactive), tone = if (source.isActive) VivicastColors.Success else VivicastColors.SurfaceHigh)
                }
            }
        }

        item {
            ProviderTextField(
                label = stringResource(R.string.settings_epg_channel_id_label),
                value = epgChannelId,
                placeholder = stringResource(R.string.settings_epg_channel_id_hint),
                onValueChange = onEpgChannelIdChange,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxWidth()) {
                ActionPill(stringResource(R.string.common_save), modifier = Modifier.width(150.dp), selected = true, onClick = onSave)
                ActionPill(stringResource(R.string.settings_epg_manual_delete), modifier = Modifier.width(190.dp), onClick = onClear)
                }
        }

        if (message != null) {
            item {
                InfoPanel(title = stringResource(R.string.common_note), body = message, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

