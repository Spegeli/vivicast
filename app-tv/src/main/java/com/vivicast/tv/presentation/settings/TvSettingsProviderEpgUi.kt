package com.vivicast.tv

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.RecentChannel
import java.util.Locale
@Composable
fun SettingsHeader(
    subtitle: String,
    trailing: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = subtitle,
                color = ViviCastColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = trailing,
            color = ViviCastColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SettingsSectionRail(
    selectedSection: SettingsSection,
    onSelectSection: (SettingsSection) -> Unit,
    focusRestoreKey: Int,
    onMoveLeft: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(218.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSection.entries.forEach { section ->
            SettingsRailRow(
                label = section.label,
                selected = section == selectedSection,
                requestInitialFocus = section == selectedSection,
                focusRestoreKey = focusRestoreKey,
                onMoveLeft = onMoveLeft,
                onClick = { onSelectSection(section) }
            )
        }
    }
}

@Composable
fun SettingsRailRow(
    label: String,
    selected: Boolean,
    requestInitialFocus: Boolean,
    focusRestoreKey: Int,
    onMoveLeft: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)
    LaunchedEffect(requestInitialFocus, focusRestoreKey) {
        if (requestInitialFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(
                when {
                    focused -> ViviCastColors.FocusFill
                    selected -> ViviCastColors.Selected
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    selected -> ViviCastColors.Accent
                    else -> Color.Transparent
                },
                shape = shape
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.DirectionLeft
                ) {
                    onMoveLeft()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Bold
        )
    }
}

@Composable
fun GeneralSettingsPanel(
    settings: AppSettings,
    onSetStartOnBoot: (Boolean) -> Unit,
    onSetResumeLastChannelOnStart: (Boolean) -> Unit,
    onSetConfirmExitOnBack: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingsToggleRow(
            label = "Start automatically after boot",
            value = settings.startOnBoot,
            description = "Uses stored boot-start flag.",
            onClick = { onSetStartOnBoot(!settings.startOnBoot) }
        )
        SettingsToggleRow(
            label = "Resume last channel on start",
            value = settings.resumeLastChannelOnStart,
            description = "When disabled, startup always lands in Live TV.",
            onClick = { onSetResumeLastChannelOnStart(!settings.resumeLastChannelOnStart) }
        )
        SettingsToggleRow(
            label = "Confirm exit with Back",
            value = settings.confirmExitOnBack,
            description = "Prevents accidental exits.",
            onClick = { onSetConfirmExitOnBack(!settings.confirmExitOnBack) }
        )
    }
}

@Composable
fun ProvidersSettingsPanel(
    playlists: List<Playlist>,
    channelCountByPlaylist: Map<String, Int>,
    epgSources: List<EpgSource>,
    providerSettings: Map<String, ProviderUiSettings>,
    providerSyncStates: Map<String, ProviderSyncState>,
    status: String?,
    onOpenM3uUrl: () -> Unit,
    onOpenXtream: () -> Unit,
    onRefreshAllProviders: () -> Unit,
    refreshingAllProviders: Boolean,
    compactProviderRows: Boolean,
    showSourceLabels: Boolean,
    onEditProvider: (Playlist) -> Unit
) {
    val providerItems = remember(
        playlists,
        channelCountByPlaylist,
        epgSources,
        providerSettings,
        providerSyncStates,
        compactProviderRows,
        showSourceLabels
    ) {
        playlists.map { playlist ->
            val settings = providerSettings[playlist.id] ?: ProviderUiSettings()
            val syncState = providerSyncStates[playlist.id] ?: ProviderSyncState()
            ProviderSettingsItemUi(
                id = playlist.id,
                name = playlist.displayName(),
                sourceType = playlist.sourceType.name.replace('_', ' '),
                sourceLabel = playlist.safeSourceLabel().takeIf { showSourceLabels },
                channelCount = channelCountByPlaylist[playlist.id] ?: 0,
                enabled = settings.enabled,
                liveTvEnabled = settings.liveTvEnabled,
                vodEnabled = settings.vodEnabled,
                epgLinked = epgSources.any { it.playlistId == playlist.id },
                syncLabel = syncState.summary(),
                syncTone = when {
                    syncState.refreshing -> SettingsStatusTone.Warning
                    syncState.lastErrorMessage != null -> SettingsStatusTone.Error
                    syncState.lastSyncedAtEpochMillis != null -> SettingsStatusTone.Success
                    else -> SettingsStatusTone.Muted
                },
                compact = compactProviderRows
            )
        }
    }
    ProvidersSettingsContent(
        providers = providerItems,
        status = status,
        refreshingAllProviders = refreshingAllProviders,
        onOpenM3uUrl = onOpenM3uUrl,
        onOpenXtream = onOpenXtream,
        onRefreshAllProviders = onRefreshAllProviders,
        onEditProvider = { providerId ->
            playlists.firstOrNull { it.id == providerId }?.let(onEditProvider)
        }
    )
}

@Composable
fun ProvidersSettingsContent(
    providers: List<ProviderSettingsItemUi>,
    status: String?,
    refreshingAllProviders: Boolean,
    onOpenM3uUrl: () -> Unit,
    onOpenXtream: () -> Unit,
    onRefreshAllProviders: () -> Unit,
    onEditProvider: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsWorkspaceGroup(
            title = "Add provider",
            summary = "Choose the connection type"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionChip(text = "M3U playlist", onClick = onOpenM3uUrl)
                SettingsActionChip(text = "Xtream account", onClick = onOpenXtream)
            }
        }

        status?.let {
            SettingsInlineStatus(
                text = it,
                tone = if (it.contains("failed", true) || it.contains("error", true)) {
                    SettingsStatusTone.Error
                } else {
                    SettingsStatusTone.Success
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsGroupHeading(
                title = "Configured providers",
                summary = "",
                modifier = Modifier.weight(1f)
            )
            SettingsActionChip(
                text = if (refreshingAllProviders) "Refreshing..." else "Refresh all",
                onClick = onRefreshAllProviders
            )
        }

        if (providers.isEmpty()) {
            SettingsEmptyState(
                title = "No providers configured",
                body = "Add an M3U playlist or Xtream account to begin."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderSettingsItemRow(
                        item = provider,
                        onClick = { onEditProvider(provider.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun EpgSettingsPanel(
    epgSources: List<EpgSource>,
    playlists: List<Playlist>,
    providerEpgAssignments: Map<String, List<String>>,
    settings: AppSettings,
    status: String?,
    onCycleEpgTimeOffset: () -> Unit,
    onSetEpgRefreshOnAppStart: (Boolean) -> Unit,
    onCycleEpgUpdateInterval: () -> Unit,
    onSetEpgRefreshDuringSession: (Boolean) -> Unit,
    onCycleEpgRetentionDays: () -> Unit,
    onOpenGlobalEpg: () -> Unit,
    onRefreshDueEpg: () -> Unit,
    onRefreshAllEpg: () -> Unit,
    refreshingAllEpg: Boolean,
    selectedSourceId: String?,
    onOpenSourceDetail: (String) -> Unit,
    onCloseSourceDetail: () -> Unit,
    onAssignSourceToProvider: (String, String, Boolean) -> Unit,
    onMoveAssignedSourceToFront: (String, String) -> Unit,
    onDeleteEpgSource: (String) -> Unit
) {
    val sourceItems = remember(epgSources, playlists, providerEpgAssignments) {
        epgSources.map { source ->
            EpgSourceItemUi(
                id = source.id,
                name = source.name,
                sourceLabel = source.sourceUri.maskedDisplaySource(),
                scopeLabel = if (source.playlistId == null) "Global" else "Provider",
                assignedProviderCount = playlists.count { playlist ->
                    source.id in (providerEpgAssignments[playlist.id] ?: emptyList())
                },
                lastImportLabel = source.lastImportedAtEpochMillis?.asShortDateTime() ?: "Pending"
            )
        }
    }
    val selectedSource = sourceItems.firstOrNull { it.id == selectedSourceId } ?: sourceItems.firstOrNull()
    val assignmentItems = remember(selectedSource?.id, playlists, providerEpgAssignments) {
        selectedSource?.let { source ->
            playlists.map { playlist ->
                val assignedIds = providerEpgAssignments[playlist.id] ?: emptyList()
                EpgAssignmentItemUi(
                    providerId = playlist.id,
                    providerName = playlist.displayName(),
                    assigned = source.id in assignedIds,
                    primary = assignedIds.firstOrNull() == source.id
                )
            }
        } ?: emptyList()
    }

    EpgSettingsContent(
        sources = sourceItems,
        selectedSourceId = selectedSourceId,
        assignments = assignmentItems,
        timeOffsetLabel = settings.epgTimeOffset.label,
        refreshOnAppStart = settings.epgRefreshOnAppStart,
        updateIntervalLabel = settings.epgUpdateInterval.label,
        refreshDuringSession = settings.epgRefreshDuringSession,
        retentionLabel = settings.epgRetentionDays.label,
        status = status,
        refreshingAllEpg = refreshingAllEpg,
        detailOpen = selectedSourceId != null,
        onSelectSource = onOpenSourceDetail,
        onCloseDetail = onCloseSourceDetail,
        onCycleEpgTimeOffset = onCycleEpgTimeOffset,
        onSetEpgRefreshOnAppStart = onSetEpgRefreshOnAppStart,
        onCycleEpgUpdateInterval = onCycleEpgUpdateInterval,
        onSetEpgRefreshDuringSession = onSetEpgRefreshDuringSession,
        onCycleEpgRetentionDays = onCycleEpgRetentionDays,
        onOpenGlobalEpg = onOpenGlobalEpg,
        onRefreshDueEpg = onRefreshDueEpg,
        onRefreshAllEpg = onRefreshAllEpg,
        onAssignSourceToProvider = { providerId, assigned ->
            selectedSource?.let { onAssignSourceToProvider(providerId, it.id, assigned) }
        },
        onMoveAssignedSourceToFront = { providerId ->
            selectedSource?.let { onMoveAssignedSourceToFront(providerId, it.id) }
        },
        onDeleteEpgSource = {
            selectedSource?.let { onDeleteEpgSource(it.id) }
        }
    )
}

@Composable
fun EpgSettingsContent(
    sources: List<EpgSourceItemUi>,
    selectedSourceId: String?,
    assignments: List<EpgAssignmentItemUi>,
    timeOffsetLabel: String,
    refreshOnAppStart: Boolean,
    updateIntervalLabel: String,
    refreshDuringSession: Boolean,
    retentionLabel: String,
    status: String?,
    refreshingAllEpg: Boolean,
    detailOpen: Boolean,
    onSelectSource: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onCycleEpgTimeOffset: () -> Unit,
    onSetEpgRefreshOnAppStart: (Boolean) -> Unit,
    onCycleEpgUpdateInterval: () -> Unit,
    onSetEpgRefreshDuringSession: (Boolean) -> Unit,
    onCycleEpgRetentionDays: () -> Unit,
    onOpenGlobalEpg: () -> Unit,
    onRefreshDueEpg: () -> Unit,
    onRefreshAllEpg: () -> Unit,
    onAssignSourceToProvider: (String, Boolean) -> Unit,
    onMoveAssignedSourceToFront: (String) -> Unit,
    onDeleteEpgSource: () -> Unit
) {
    val selectedSource = sources.firstOrNull { it.id == selectedSourceId }
    var confirmDelete by remember(selectedSourceId) { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf(EpgSettingsPage.Assignments) }
    val visibleStatusTone = status?.let { message ->
        when {
            message.contains("failed", true) || message.contains("error", true) -> SettingsStatusTone.Error
            refreshingAllEpg || message.contains("refreshing", true) || message.contains("checking", true) -> SettingsStatusTone.Warning
            else -> null
        }
    }
    if (!detailOpen) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsWorkspaceGroup(
                title = "Add source",
                summary = "Attach a global XMLTV feed"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsActionChip(text = "Add source", onClick = onOpenGlobalEpg)
                    SettingsActionChip(text = "Refresh due", onClick = onRefreshDueEpg)
                }
            }

            if (status != null && visibleStatusTone != null) {
                SettingsInlineStatus(
                    text = status,
                    tone = visibleStatusTone
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsGroupHeading(
                    title = "Configured sources",
                    summary = "",
                    modifier = Modifier.weight(1f)
                )
                SettingsActionChip(
                    text = if (refreshingAllEpg) "Refreshing..." else "Refresh all",
                    onClick = onRefreshAllEpg
                )
            }

            if (sources.isEmpty()) {
                SettingsEmptyState(
                    title = "No EPG sources",
                    body = "Add an XMLTV source to link programme data."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sources, key = { it.id }) { source ->
                        EpgSourceItemRow(
                            item = source,
                            selected = source.id == selectedSourceId,
                            onClick = { onSelectSource(source.id) }
                        )
                    }
                }
            }
        }
    }

    if (detailOpen) {
        SettingsWorkspaceGroup(
            title = when (selectedPage) {
                EpgSettingsPage.Assignments -> selectedSource?.name ?: "Provider assignment"
                EpgSettingsPage.UpdatePolicy -> "Update policy"
            },
            summary = when (selectedPage) {
                EpgSettingsPage.Assignments -> selectedSource?.let {
                    "${it.scopeLabel} - ${it.assignedProviderCount} assigned"
                } ?: "Select a source"
                EpgSettingsPage.UpdatePolicy -> if (refreshingAllEpg) {
                    "Refresh in progress"
                } else {
                    "Automatic and manual"
                }
            },
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsActionChip(
                    text = "Back to sources",
                    selected = false,
                    requestInitialFocus = true,
                    onMoveLeft = onCloseDetail,
                    onClick = onCloseDetail
                )
                SettingsActionChip(
                    text = "Assignments",
                    selected = selectedPage == EpgSettingsPage.Assignments,
                    onClick = { selectedPage = EpgSettingsPage.Assignments }
                )
                SettingsActionChip(
                    text = "Update policy",
                    selected = selectedPage == EpgSettingsPage.UpdatePolicy,
                    onClick = { selectedPage = EpgSettingsPage.UpdatePolicy }
                )
            }

            when (selectedPage) {
                EpgSettingsPage.Assignments -> {
                    if (selectedSource == null) {
                        SettingsEmptyState(
                            title = "No source selected",
                            body = "Choose a source to manage provider assignments."
                        )
                    } else {
                        SettingsSourceSummary(item = selectedSource)
                        SettingsGroupHeading(
                            title = "Provider assignment",
                            summary = "${assignments.count { it.assigned }} active"
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(assignments, key = { it.providerId }) { assignment ->
                                EpgAssignmentItemRow(
                                    item = assignment,
                                    onToggle = {
                                        onAssignSourceToProvider(
                                            assignment.providerId,
                                            !assignment.assigned
                                        )
                                    },
                                    onMakePrimary = {
                                        onMoveAssignedSourceToFront(assignment.providerId)
                                    }
                                )
                            }
                        }
                        SettingsDangerAction(
                            text = if (confirmDelete) "Confirm delete" else "Delete source",
                            onClick = {
                                if (confirmDelete) {
                                    onDeleteEpgSource()
                                } else {
                                    confirmDelete = true
                                }
                            }
                        )
                    }
                }

                EpgSettingsPage.UpdatePolicy -> {
                    ProviderInstantSettingRow(
                        label = "Time offset",
                        value = timeOffsetLabel,
                        onClick = onCycleEpgTimeOffset
                    )
                    SettingsToggleRow(
                        label = "Check on app start",
                        value = refreshOnAppStart,
                        onClick = { onSetEpgRefreshOnAppStart(!refreshOnAppStart) }
                    )
                    ProviderInstantSettingRow(
                        label = "Update interval",
                        value = updateIntervalLabel,
                        onClick = onCycleEpgUpdateInterval
                    )
                    SettingsToggleRow(
                        label = "Check during session",
                        value = refreshDuringSession,
                        onClick = { onSetEpgRefreshDuringSession(!refreshDuringSession) }
                    )
                    ProviderInstantSettingRow(
                        label = "Retention",
                        value = retentionLabel,
                        onClick = onCycleEpgRetentionDays
                    )
                    SettingsGroupHeading(
                        title = "Maintenance",
                        summary = "Run manually"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsActionChip(
                            text = if (refreshingAllEpg) "Checking..." else "Refresh due",
                            onClick = onRefreshDueEpg
                        )
                        SettingsActionChip(
                            text = if (refreshingAllEpg) "Refreshing..." else "Refresh all",
                            onClick = onRefreshAllEpg
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpgAssignmentItemRow(
    item: EpgAssignmentItemUi,
    onToggle: () -> Unit,
    onMakePrimary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProviderInstantSettingRow(
            label = item.providerName,
            value = if (item.assigned) "Assigned" else "Not assigned",
            toggleValue = item.assigned,
            onClick = onToggle
        )
        if (item.assigned) {
            ProviderInstantSettingRow(
                label = "Primary source for provider",
                value = if (item.primary) "Primary" else "Make primary",
                onClick = onMakePrimary
            )
        }
    }
}

@Composable
fun EpgProviderAssignmentRow(
    playlistName: String,
    assigned: Boolean,
    primary: Boolean,
    onToggle: () -> Unit,
    onMakePrimary: () -> Unit
) {
    EpgAssignmentItemRow(
        item = EpgAssignmentItemUi(
            providerId = playlistName,
            providerName = playlistName,
            assigned = assigned,
            primary = primary
        ),
        onToggle = onToggle,
        onMakePrimary = onMakePrimary
    )
}

data class ProviderSettingsItemUi(
    val id: String,
    val name: String,
    val sourceType: String,
    val sourceLabel: String?,
    val channelCount: Int,
    val enabled: Boolean,
    val liveTvEnabled: Boolean,
    val vodEnabled: Boolean,
    val epgLinked: Boolean,
    val syncLabel: String,
    val syncTone: SettingsStatusTone,
    val compact: Boolean
)

data class EpgSourceItemUi(
    val id: String,
    val name: String,
    val sourceLabel: String,
    val scopeLabel: String,
    val assignedProviderCount: Int,
    val lastImportLabel: String
)

data class EpgAssignmentItemUi(
    val providerId: String,
    val providerName: String,
    val assigned: Boolean,
    val primary: Boolean
)

enum class EpgSettingsPage {
    Assignments,
    UpdatePolicy
}

enum class ProviderDetailSection(val label: String) {
    Configuration("Configuration"),
    Groups("Groups"),
    Epg("EPG"),
    Maintenance("Maintenance")
}

