package com.vivicast.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.XtreamCredentials
import java.io.File
@Composable
fun ProviderEditDialog(
    playlist: Playlist,
    channelCount: Int,
    catchupChannelCount: Int,
    epgSource: EpgSource?,
    assignedEpgSources: List<EpgSource>,
    globalEpgSources: List<EpgSource>,
    assignedGlobalSourceIds: List<String>,
    categories: List<ChannelCategory>,
    settings: ProviderUiSettings,
    syncState: ProviderSyncState,
    status: String?,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetLiveTvEnabled: (Boolean) -> Unit,
    onSetVodEnabled: (Boolean) -> Unit,
    onCycleLogoPriority: () -> Unit,
    onSetCategoryVisible: (String, Boolean) -> Unit,
    onMoveCategoryUp: (String) -> Unit,
    onMoveCategoryDown: (String) -> Unit,
    onShowAllCategories: () -> Unit,
    onHideAllCategories: () -> Unit,
    onResetCategoryOrder: () -> Unit,
    onCycleRefreshInterval: () -> Unit,
    onSetRefreshOnAppStart: (Boolean) -> Unit,
    onSetRefreshOnPlaylistChange: (Boolean) -> Unit,
    onSetUserAgent: (String) -> Unit,
    onCycleXtreamOutputFormat: () -> Unit,
    onCycleXtreamApiScope: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveConnection: (String, String?, String?) -> Unit,
    onSaveEpgUrl: (String) -> Unit,
    onRemoveEpgUrl: () -> Unit,
    onSetAssignedGlobalEpgSource: (String, Boolean) -> Unit,
    onMakeGlobalEpgSourcePrimary: (String) -> Unit,
    onClearAssignedGlobalEpgSources: () -> Unit,
    onResetProviderAdvancedOptions: () -> Unit,
    onResetProviderSyncState: () -> Unit,
    refreshingProvider: Boolean,
    onRefreshProvider: () -> Unit,
    importingEpg: Boolean,
    onImportEpg: (EpgSource) -> Unit,
    onDeleteProvider: () -> Unit
) {
    var displayName by remember(playlist.id) { mutableStateOf(playlist.displayName()) }
    var sourceUri by remember(playlist.id, playlist.sourceUri) { mutableStateOf(playlist.sourceUri.orEmpty()) }
    var sourceUsername by remember(playlist.id, playlist.sourceUsername) { mutableStateOf(playlist.sourceUsername.orEmpty()) }
    var sourcePassword by remember(playlist.id, playlist.sourcePassword) { mutableStateOf(playlist.sourcePassword.orEmpty()) }
    var epgUrl by remember(playlist.id, epgSource?.sourceUri) { mutableStateOf(epgSource?.sourceUri ?: "") }
    var userAgent by remember(playlist.id, settings.userAgent) { mutableStateOf(settings.userAgent) }
    var localError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDiagnostics by remember(playlist.id) { mutableStateOf(false) }
    var detailSection by remember(playlist.id) { mutableStateOf(ProviderDetailSection.Configuration) }
    val orderedCategories = remember(categories, settings.categoryOrderIds) {
        categories.sortedForProvider(settings.categoryOrderIds)
    }

    fun submit() {
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            localError = "Enter a provider name."
        } else {
            localError = null
            onSaveName(trimmedName)
        }
    }

    fun submitConnection() {
        val trimmedSourceUri = sourceUri.trim()
        when (playlist.sourceType) {
            com.vivicast.core.model.PlaylistSourceType.M3U_URL -> {
                val validationError = trimmedSourceUri.validatePlaylistUrl()
                if (validationError != null) {
                    localError = validationError
                } else {
                    localError = null
                    onSaveConnection(trimmedSourceUri, null, null)
                }
            }

            com.vivicast.core.model.PlaylistSourceType.XTREAM_CODES -> {
                when {
                    trimmedSourceUri.validatePlaylistUrl() != null -> {
                        localError = trimmedSourceUri.validatePlaylistUrl()
                    }
                    sourceUsername.trim().isBlank() -> {
                        localError = "Enter your Xtream username."
                    }
                    sourcePassword.trim().isBlank() -> {
                        localError = "Enter your Xtream password."
                    }
                    else -> {
                        localError = null
                        onSaveConnection(
                            trimmedSourceUri,
                            sourceUsername.trim(),
                            sourcePassword.trim()
                        )
                    }
                }
            }

            com.vivicast.core.model.PlaylistSourceType.M3U_FILE -> {
                val validationError = trimmedSourceUri.validatePlaylistFileSource()
                if (validationError != null) {
                    localError = validationError
                } else {
                    localError = null
                    onSaveConnection(trimmedSourceUri, null, null)
                }
            }
        }
    }

    fun submitEpg() {
        val trimmedUrl = epgUrl.trim()
        val validationError = trimmedUrl.validatePlaylistUrl()
        if (validationError != null) {
            localError = validationError
        } else {
            localError = null
            onSaveEpgUrl(trimmedUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViviCastColors.Background)
            .padding(horizontal = 56.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        val detailScrollModifier = if (detailSection == ProviderDetailSection.Configuration) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(ViviCastColors.Background)
                .then(detailScrollModifier)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = playlist.displayName(),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Provider settings",
                        color = ViviCastColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionChip(
                    text = "Back to providers",
                    selected = false,
                    requestInitialFocus = true,
                    onMoveLeft = onDismiss,
                    onClick = onDismiss
                )
                ProviderDetailSection.entries.forEach { section ->
                    SettingsActionChip(
                        text = section.label,
                        selected = detailSection == section,
                        onClick = { detailSection = section }
                    )
                }
            }

            if (detailSection == ProviderDetailSection.Configuration) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProviderDetailRow(
                        label = "Connection",
                        value = "${playlist.sourceType.name.replace('_', ' ')} - ${playlist.safeSourceLabel()}"
                    )
                    ProviderDetailRow(label = "Channels", value = "$channelCount imported")
                    ProviderDetailRow(label = "Enabled", value = if (settings.enabled) "Yes" else "No")
                    ProviderDetailRow(
                        label = "Content",
                        value = settings.contentSummary()
                    )
                    ProviderDetailRow(
                        label = "Import status",
                        value = settings.importStatusLabel()
                    )
                    ProviderDetailRow(label = "Logo priority", value = settings.logoPriority.label)
                    ProviderDetailRow(
                        label = "User-Agent",
                        value = settings.userAgent.ifBlank { "Default ViviCast" }
                    )
                    if (playlist.sourceType == com.vivicast.core.model.PlaylistSourceType.XTREAM_CODES) {
                        ProviderDetailRow(label = "Xtream output", value = settings.xtreamOutputFormat.label)
                        ProviderDetailRow(label = "Xtream API scope", value = settings.xtreamApiScope.label)
                    }
                    ProviderDetailRow(label = "EPG", value = epgSource?.sourceUri?.maskedDisplaySource() ?: "Not configured")
                    ProviderDetailRow(label = "Assigned EPG sources", value = assignedEpgSources.size.toString())
                    ProviderDetailRow(label = "Catch-up channels", value = catchupChannelCount.toString())
                    ProviderDetailRow(label = "Sync", value = syncState.summary())
                    ProviderInstantSettingRow(
                        label = "Diagnostics",
                        value = if (showDiagnostics) "Shown" else "Hidden",
                        toggleValue = showDiagnostics,
                        onClick = { showDiagnostics = !showDiagnostics }
                    )
                    if (showDiagnostics) {
                        ProviderDetailRow(
                            label = "Refresh health",
                            value = syncState.refreshHealthSummary()
                        )
                        ProviderDetailRow(
                            label = "EPG health",
                            value = syncState.epgHealthSummary()
                        )
                        ProviderDetailRow(
                            label = "Latest activity",
                            value = syncState.latestActivitySummary()
                        )
                        ProviderDetailRow(
                            label = "Observed capabilities",
                            value = syncState.capabilitySummary()
                        )
                        syncState.lastImportedChannelCount?.let { lastChannelCount ->
                            ProviderDetailRow(
                                label = "Last import",
                                value = "$lastChannelCount channels / ${syncState.lastImportedCategoryCount ?: 0} groups"
                            )
                        }
                        syncState.lastIgnoredLineCount?.let { ignoredLineCount ->
                            ProviderDetailRow(
                                label = "Ignored playlist lines",
                                value = ignoredLineCount.toString()
                            )
                        }
                        syncState.lastArchiveWindowDays?.let { archiveWindowDays ->
                            ProviderDetailRow(
                                label = "Archive window",
                                value = "$archiveWindowDays days max"
                            )
                        } ?: run {
                            if ((syncState.lastCatchupChannelCount ?: 0) > 0) {
                                ProviderDetailRow(
                                    label = "Archive window",
                                    value = "Detected, unknown"
                                )
                            }
                        }
                        if (playlist.sourceType == com.vivicast.core.model.PlaylistSourceType.XTREAM_CODES &&
                            settings.xtreamApiScope.includesVodMetadata
                        ) {
                            ProviderDetailRow(
                                label = "Xtream VOD categories",
                                value = syncState.lastXtreamVodCategoryCount?.toString() ?: "Pending"
                            )
                            ProviderDetailRow(
                                label = "Xtream series categories",
                                value = syncState.lastXtreamSeriesCategoryCount?.toString() ?: "Pending"
                            )
                            syncState.lastXtreamMovieCount?.let { movieCount ->
                                ProviderDetailRow(
                                    label = "Xtream movies",
                                    value = movieCount.toString()
                                )
                            }
                            syncState.lastXtreamSeriesCount?.let { seriesCount ->
                                ProviderDetailRow(
                                    label = "Xtream series",
                                    value = seriesCount.toString()
                                )
                            }
                            syncState.lastXtreamSeasonCount?.let { seasonCount ->
                                ProviderDetailRow(
                                    label = "Xtream seasons",
                                    value = seasonCount.toString()
                                )
                            }
                            syncState.lastXtreamEpisodeCount?.let { episodeCount ->
                                ProviderDetailRow(
                                    label = "Xtream episodes",
                                    value = episodeCount.toString()
                                )
                            }
                            syncState.lastXtreamSeriesDetailFailureCount?.takeIf { it > 0 }?.let { failedCount ->
                                ProviderDetailRow(
                                    label = "Series detail failures",
                                    value = failedCount.toString()
                                )
                            }
                        }
                        syncState.lastSyncedAtEpochMillis?.let { lastSyncedAt ->
                            ProviderDetailRow(label = "Last success", value = lastSyncedAt.asShortDateTime())
                        }
                        syncState.lastRefreshAttemptAtEpochMillis?.let { attemptedAt ->
                            ProviderDetailRow(label = "Last refresh attempt", value = attemptedAt.asShortDateTime())
                        }
                        syncState.lastRefreshDurationMillis?.let { durationMillis ->
                            ProviderDetailRow(
                                label = "Last refresh duration",
                                value = durationMillis.asDurationLabel()
                            )
                        }
                        ProviderDetailRow(
                            label = "Refresh counters",
                            value = "${syncState.refreshSuccessCount} ok / ${syncState.refreshFailureCount} failed / ${syncState.consecutiveRefreshFailureCount} consecutive"
                        )
                        syncState.lastRefreshSourceLabel?.takeIf { it.isNotBlank() }?.let { sourceLabel ->
                            ProviderDetailRow(label = "Last refresh source", value = sourceLabel)
                        }
                        syncState.lastErrorAtEpochMillis?.let { errorAt ->
                            ProviderDetailRow(label = "Last refresh error at", value = errorAt.asShortDateTime())
                        }
                        syncState.lastErrorMessage?.takeIf { it.isNotBlank() }?.let { lastError ->
                            ProviderDetailRow(label = "Last error", value = lastError)
                        }
                        syncState.lastEpgImportedAtEpochMillis?.let { importedAt ->
                            ProviderDetailRow(label = "Last EPG import", value = importedAt.asShortDateTime())
                        }
                        syncState.lastEpgAttemptAtEpochMillis?.let { attemptedAt ->
                            ProviderDetailRow(label = "Last EPG attempt", value = attemptedAt.asShortDateTime())
                        }
                        syncState.lastEpgImportDurationMillis?.let { durationMillis ->
                            ProviderDetailRow(label = "Last EPG duration", value = durationMillis.asDurationLabel())
                        }
                        ProviderDetailRow(
                            label = "EPG counters",
                            value = "${syncState.epgSuccessCount} ok / ${syncState.epgFailureCount} failed / ${syncState.consecutiveEpgFailureCount} consecutive"
                        )
                        syncState.lastEpgImportedProgramCount?.let { programCount ->
                            ProviderDetailRow(
                                label = "Last EPG result",
                                value = "$programCount matched / ${syncState.lastEpgUnmatchedProgramCount ?: 0} unmatched / ${syncState.lastEpgXmltvChannelCount ?: 0} XMLTV"
                            )
                        }
                        syncState.lastEpgErrorAtEpochMillis?.let { errorAt ->
                            ProviderDetailRow(label = "Last EPG error at", value = errorAt.asShortDateTime())
                        }
                        syncState.lastEpgErrorMessage?.takeIf { it.isNotBlank() }?.let { epgError ->
                            ProviderDetailRow(label = "Last EPG error", value = epgError)
                        }
                    }
                    ProviderDetailRow(label = "Refresh interval", value = settings.refreshIntervalHours.label)
                    ProviderDetailRow(label = "On app start", value = if (settings.refreshOnAppStart) "Enabled" else "Disabled")
                    ProviderDetailRow(label = "On playlist change", value = if (settings.refreshOnPlaylistChange) "Enabled" else "Disabled")
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TvTextInput(
                        value = displayName,
                        enabled = true,
                        placeholder = "Provider name",
                        error = localError,
                        onValueChange = {
                            displayName = it
                            localError = null
                        },
                        onSubmit = ::submit
                    )

                    when (playlist.sourceType) {
                        com.vivicast.core.model.PlaylistSourceType.M3U_URL -> {
                            TvTextInput(
                                value = sourceUri,
                                enabled = true,
                                placeholder = "Playlist URL",
                                error = null,
                                onValueChange = {
                                    sourceUri = it
                                    localError = null
                                },
                                onSubmit = ::submitConnection
                            )
                        }

                        com.vivicast.core.model.PlaylistSourceType.XTREAM_CODES -> {
                            TvTextInput(
                                value = sourceUri,
                                enabled = true,
                                placeholder = "Xtream server URL",
                                error = null,
                                onValueChange = {
                                    sourceUri = it
                                    localError = null
                                },
                                onSubmit = ::submitConnection
                            )
                            TvTextInput(
                                value = sourceUsername,
                                enabled = true,
                                placeholder = "Xtream username",
                                error = null,
                                onValueChange = {
                                    sourceUsername = it
                                    localError = null
                                },
                                onSubmit = ::submitConnection
                            )
                            TvTextInput(
                                value = sourcePassword,
                                enabled = true,
                                placeholder = "Xtream password",
                                error = null,
                                visualTransformation = PasswordVisualTransformation(),
                                onValueChange = {
                                    sourcePassword = it
                                    localError = null
                                },
                                onSubmit = ::submitConnection
                            )
                        }

                        com.vivicast.core.model.PlaylistSourceType.M3U_FILE -> {
                            TvTextInput(
                                value = sourceUri,
                                enabled = true,
                                placeholder = "content://... or file path",
                                error = null,
                                onValueChange = {
                                    sourceUri = it
                                    localError = null
                                },
                                onSubmit = ::submitConnection
                            )
                        }
                    }

                    TvTextInput(
                        value = epgUrl,
                        enabled = true,
                        placeholder = "EPG XMLTV URL",
                        error = null,
                        onValueChange = {
                            epgUrl = it
                            localError = null
                        },
                        onSubmit = ::submitEpg
                    )
                    TvTextInput(
                        value = userAgent,
                        enabled = true,
                        placeholder = "Custom User-Agent (optional)",
                        error = null,
                        onValueChange = {
                            userAgent = it
                            localError = null
                        },
                        onSubmit = { onSetUserAgent(userAgent) }
                    )

                    Text(
                        text = when {
                            localError != null -> localError ?: ""
                            confirmDelete -> "Press Delete again to remove this provider."
                            status != null -> status
                            else -> "Save changes, then refresh or import data."
                        },
                        color = if (localError == null) {
                            ViviCastColors.TextSecondary
                        } else {
                            ViviCastColors.Error
                        },
                        fontSize = 12.sp
                    )

                    ProviderInstantSettingRow(
                        label = "Provider active",
                        value = if (settings.enabled) "Enabled" else "Hidden",
                        toggleValue = settings.enabled,
                        onClick = { onSetEnabled(!settings.enabled) }
                    )
                    ProviderInstantSettingRow(
                        label = "Live TV",
                        value = if (settings.liveTvEnabled) "Enabled" else "Disabled",
                        toggleValue = settings.liveTvEnabled,
                        onClick = { onSetLiveTvEnabled(!settings.liveTvEnabled) }
                    )
                    ProviderInstantSettingRow(
                        label = "Movies / Series",
                        value = if (settings.vodEnabled) "Enabled" else "Disabled",
                        description = "Included in provider refresh.",
                        toggleValue = settings.vodEnabled,
                        onClick = { onSetVodEnabled(!settings.vodEnabled) }
                    )
                    ProviderInstantSettingRow(
                        label = "Logo priority",
                        value = settings.logoPriority.label,
                        onClick = onCycleLogoPriority
                    )
                    ProviderInstantSettingRow(
                        label = "Refresh interval",
                        value = settings.refreshIntervalHours.label,
                        onClick = onCycleRefreshInterval
                    )
                    ProviderInstantSettingRow(
                        label = "Refresh on app start",
                        value = if (settings.refreshOnAppStart) "Enabled" else "Disabled",
                        toggleValue = settings.refreshOnAppStart,
                        onClick = { onSetRefreshOnAppStart(!settings.refreshOnAppStart) }
                    )
                    ProviderInstantSettingRow(
                        label = "Refresh on playlist change",
                        value = if (settings.refreshOnPlaylistChange) "Enabled" else "Disabled",
                        toggleValue = settings.refreshOnPlaylistChange,
                        onClick = { onSetRefreshOnPlaylistChange(!settings.refreshOnPlaylistChange) }
                    )
                    if (playlist.sourceType == com.vivicast.core.model.PlaylistSourceType.XTREAM_CODES) {
                        ProviderInstantSettingRow(
                            label = "Xtream output",
                            value = settings.xtreamOutputFormat.label,
                            onClick = onCycleXtreamOutputFormat
                        )
                        ProviderInstantSettingRow(
                            label = "Xtream API scope",
                            value = settings.xtreamApiScope.label,
                            description = "Controls whether Xtream refresh also imports Movies and Series metadata.",
                            onClick = onCycleXtreamApiScope
                        )
                    }
                }
            }

            if (detailSection == ProviderDetailSection.Groups) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ViviCastColors.SurfaceRaised)
                    .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Groups",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsActionChip(text = "Show all", onClick = onShowAllCategories)
                    SettingsActionChip(text = "Hide all", onClick = onHideAllCategories)
                    SettingsActionChip(text = "Reset order", onClick = onResetCategoryOrder)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(orderedCategories, key = { it.id }) { category ->
                        val visible = category.id !in settings.hiddenCategoryIds
                        ProviderGroupToggleRow(
                            name = category.name,
                            visible = visible,
                            canMoveUp = orderedCategories.firstOrNull()?.id != category.id,
                            canMoveDown = orderedCategories.lastOrNull()?.id != category.id,
                            onClick = { onSetCategoryVisible(category.id, !visible) },
                            onMoveUp = { onMoveCategoryUp(category.id) },
                            onMoveDown = { onMoveCategoryDown(category.id) }
                        )
                    }
                }
            }

            if (detailSection == ProviderDetailSection.Epg) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ViviCastColors.SurfaceRaised)
                        .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "EPG sources",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (globalEpgSources.isEmpty()) {
                        SettingsEmptyState(
                            title = "No global EPG sources",
                            body = "Add a source in Settings > EPG."
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsActionChip(
                                text = "Clear assignments",
                                onClick = onClearAssignedGlobalEpgSources
                            )
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(globalEpgSources, key = { it.id }) { source ->
                                val assigned = source.id in assignedGlobalSourceIds
                                val primary = assignedGlobalSourceIds.firstOrNull() == source.id
                                EpgProviderAssignmentRow(
                                    playlistName = source.name,
                                    assigned = assigned,
                                    primary = primary,
                                    onToggle = { onSetAssignedGlobalEpgSource(source.id, !assigned) },
                                    onMakePrimary = { onMakeGlobalEpgSourcePrimary(source.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (detailSection == ProviderDetailSection.Configuration) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogActionButton(
                    text = "Save name",
                    enabled = true,
                    primary = true,
                    onClick = ::submit
                )
                DialogActionButton(
                    text = "Save connection",
                    enabled = true,
                    primary = false,
                    onClick = ::submitConnection
                )
                DialogActionButton(
                    text = "Save EPG",
                    enabled = epgUrl.isNotBlank(),
                    primary = false,
                    onClick = ::submitEpg
                )
                DialogActionButton(
                    text = "Save User-Agent",
                    enabled = true,
                    primary = false,
                    onClick = { onSetUserAgent(userAgent) }
                )
                DialogActionButton(
                    text = "Clear User-Agent",
                    enabled = userAgent.isNotBlank(),
                    primary = false,
                    onClick = {
                        userAgent = ""
                        onSetUserAgent("")
                    }
                )
                DialogActionButton(
                    text = if (refreshingProvider) "Refreshing" else "Refresh provider",
                    enabled = !refreshingProvider,
                    primary = false,
                    onClick = onRefreshProvider
                )
                DialogActionButton(
                    text = if (importingEpg) "Importing EPG" else "Import EPG",
                    enabled = epgSource != null && !importingEpg,
                    primary = false,
                    onClick = {
                        if (epgSource != null) {
                            onImportEpg(epgSource)
                        }
                    }
                )
            }

            if (detailSection == ProviderDetailSection.Maintenance) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogActionButton(
                    text = "Reset advanced",
                    enabled = true,
                    primary = false,
                    onClick = onResetProviderAdvancedOptions
                )
                DialogActionButton(
                    text = "Clear sync state",
                    enabled = true,
                    primary = false,
                    onClick = onResetProviderSyncState
                )
            }

            if (detailSection == ProviderDetailSection.Maintenance) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogActionButton(
                    text = if (confirmDelete) "Delete now" else "Delete",
                    enabled = true,
                    primary = false,
                    onClick = {
                        if (confirmDelete) {
                            onDeleteProvider()
                        } else {
                            confirmDelete = true
                        }
                    }
                )
                DialogActionButton(
                    text = "Remove EPG",
                    enabled = epgSource != null,
                    primary = false,
                    onClick = {
                        epgUrl = ""
                        onRemoveEpgUrl()
                    }
                )
                DialogActionButton(
                    text = "Cancel",
                    enabled = true,
                    primary = false,
                    onClick = onDismiss
                )
            }
        }
    }
}

