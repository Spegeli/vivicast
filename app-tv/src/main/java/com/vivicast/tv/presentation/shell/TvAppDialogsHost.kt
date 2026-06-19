package com.vivicast.tv

import android.app.Application
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vivicast.core.domain.EpgNowNext
import com.vivicast.core.model.Channel
import com.vivicast.core.model.Series
import com.vivicast.core.model.Season
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.Movie
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Episode
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
@Composable
fun LiveTvDialogOverlays(
    controller: ViviCastTvController,
    uiState: LiveTvUiState,
    scope: CoroutineScope,
    channels: List<Channel>,
    categories: List<ChannelCategory>,
    epgSources: List<EpgSource>,
    providerSettings: Map<String, ProviderUiSettings>,
    providerSyncStates: Map<String, ProviderSyncState>,
) {
    var selectedChannel by uiState.selectedChannel
    var selectedPlaylistId by uiState.selectedPlaylistId
    var importStatus by uiState.importStatus
    var showM3uUrlDialog by uiState.showM3uUrlDialog
    var showXtreamDialog by uiState.showXtreamDialog
    var showGlobalEpgDialog by uiState.showGlobalEpgDialog
    var providerToEdit by uiState.providerToEdit
    var settingsFocusRestoreKey by uiState.settingsFocusRestoreKey
    var importingM3uUrl by uiState.importingM3uUrl
    var importingXtream by uiState.importingXtream
    var importingGlobalEpg by uiState.importingGlobalEpg
    var importingEpg by uiState.importingEpg
    var refreshingProviderId by uiState.refreshingProviderId
    var providerEditStatus by uiState.providerEditStatus

        if (showM3uUrlDialog) {
            M3uUrlImportDialog(
                importing = importingM3uUrl,
                onDismiss = {
                    if (!importingM3uUrl) {
                        showM3uUrlDialog = false
                    }
                },
                onImport = { url ->
                    scope.launch {
                        importingM3uUrl = true
                        val xtreamCredentials = url.extractXtreamCredentialsFromPlaylistUrl()
                        importStatus = if (xtreamCredentials != null) {
                            "Detected Xtream-style playlist URL. Connecting to Xtream..."
                        } else {
                            "Downloading playlist..."
                        }
                        val result = if (xtreamCredentials != null) {
                            controller.importXtream(xtreamCredentials)
                        } else {
                            controller.importM3uFromUrl(url)
                        }
                        importStatus = result.fold(
                            onSuccess = {
                                if (xtreamCredentials != null) {
                                    "Detected Xtream-style playlist URL. ${it.asStatusText()}"
                                } else {
                                    it.asStatusText()
                                }
                            },
                            onFailure = { error ->
                                if (xtreamCredentials != null) {
                                    "Xtream import failed: ${error.message ?: error::class.simpleName}"
                                } else {
                                    "Import failed: ${error.message ?: error::class.simpleName}"
                                }
                            }
                        )
                        importingM3uUrl = false
                        if (result.isSuccess) {
                            showM3uUrlDialog = false
                        }
                    }
                }
            )
        }

        if (showXtreamDialog) {
            XtreamLoginDialog(
                importing = importingXtream,
                onDismiss = {
                    if (!importingXtream) {
                        showXtreamDialog = false
                    }
                },
                onImport = { credentials ->
                    scope.launch {
                        importingXtream = true
                        importStatus = "Connecting to Xtream..."
                        val result = controller.importXtream(credentials)
                        importStatus = result.fold(
                            onSuccess = { it.asStatusText() },
                            onFailure = { error -> "Xtream failed: ${error.message ?: error::class.simpleName}" }
                        )
                        importingXtream = false
                        if (result.isSuccess) {
                            showXtreamDialog = false
                        }
                    }
                }
            )
        }

        if (showGlobalEpgDialog) {
            EpgSourceDialog(
                title = "Add EPG source",
                importing = importingGlobalEpg,
                onDismiss = {
                    if (!importingGlobalEpg) {
                        showGlobalEpgDialog = false
                    }
                },
                onSubmit = { name, url ->
                    scope.launch {
                        importingGlobalEpg = true
                        val result = controller.saveGlobalEpgSource(name, url)
                        providerEditStatus = result.fold(
                            onSuccess = { "Global EPG source saved" },
                            onFailure = { error -> "EPG failed: ${error.message ?: error::class.simpleName}" }
                        )
                        importingGlobalEpg = false
                        if (result.isSuccess) {
                            showGlobalEpgDialog = false
                        }
                    }
                }
            )
        }

        providerToEdit?.let { playlist ->
            val directEpgSource = epgSources.firstOrNull { it.playlistId == playlist.id }
            val assignedGlobalSourceIds = controller.getProviderEpgAssignments(playlist.id)
            val assignedGlobalSources = assignedGlobalSourceIds
                .mapNotNull { sourceId -> epgSources.firstOrNull { it.id == sourceId } }
            val globalEpgSources = epgSources.filter { it.playlistId == null }
            val effectiveEpgSource = directEpgSource ?: assignedGlobalSources.firstOrNull()
            ProviderEditDialog(
                playlist = playlist,
                channelCount = channels.count { it.playlistId == playlist.id },
                catchupChannelCount = channels.count { it.playlistId == playlist.id && it.catchupSupported },
                epgSource = effectiveEpgSource,
                assignedEpgSources = assignedGlobalSources,
                globalEpgSources = globalEpgSources,
                assignedGlobalSourceIds = assignedGlobalSourceIds,
                categories = categories.filter { it.playlistId == playlist.id },
                settings = providerSettings[playlist.id] ?: ProviderUiSettings(),
                syncState = providerSyncStates[playlist.id] ?: ProviderSyncState(),
                status = providerEditStatus,
                onDismiss = {
                    if (!importingEpg && refreshingProviderId != playlist.id) {
                        providerToEdit = null
                        settingsFocusRestoreKey += 1
                    }
                },
                onSetEnabled = { enabled ->
                    scope.launch { controller.setProviderEnabled(playlist.id, enabled) }
                },
                onSetLiveTvEnabled = { enabled ->
                    scope.launch { controller.setProviderLiveTvEnabled(playlist.id, enabled) }
                },
                onSetVodEnabled = { enabled ->
                    scope.launch {
                        controller.setProviderVodEnabled(playlist.id, enabled)
                        providerEditStatus = "Movies & Series preference saved"
                    }
                },
                onCycleLogoPriority = {
                    val next = (providerSettings[playlist.id] ?: ProviderUiSettings()).logoPriority.next()
                    scope.launch { controller.setProviderLogoPriority(playlist.id, next) }
                },
                onSetCategoryVisible = { categoryId, visible ->
                    scope.launch {
                        val current = providerSettings[playlist.id] ?: ProviderUiSettings()
                        val hidden = current.hiddenCategoryIds.toMutableSet()
                        if (visible) hidden.remove(categoryId) else hidden.add(categoryId)
                        controller.setHiddenCategories(playlist.id, hidden)
                    }
                },
                onMoveCategoryUp = { categoryId ->
                    scope.launch {
                        val current = providerSettings[playlist.id] ?: ProviderUiSettings()
                        val reordered = categories
                            .filter { it.playlistId == playlist.id }
                            .sortedForProvider(current.categoryOrderIds)
                            .moveCategory(categoryId, -1)
                        controller.setCategoryOrder(playlist.id, reordered.map { it.id })
                    }
                },
                onMoveCategoryDown = { categoryId ->
                    scope.launch {
                        val current = providerSettings[playlist.id] ?: ProviderUiSettings()
                        val reordered = categories
                            .filter { it.playlistId == playlist.id }
                            .sortedForProvider(current.categoryOrderIds)
                            .moveCategory(categoryId, 1)
                        controller.setCategoryOrder(playlist.id, reordered.map { it.id })
                    }
                },
                onShowAllCategories = {
                    scope.launch {
                        controller.setHiddenCategories(playlist.id, emptySet())
                        providerEditStatus = "All groups visible"
                    }
                },
                onHideAllCategories = {
                    scope.launch {
                        controller.setHiddenCategories(
                            playlist.id,
                            categories.filter { it.playlistId == playlist.id }.map { it.id }.toSet()
                        )
                        providerEditStatus = "All groups hidden"
                    }
                },
                onResetCategoryOrder = {
                    scope.launch {
                        controller.setCategoryOrder(playlist.id, emptyList())
                        providerEditStatus = "Group order reset"
                    }
                },
                onCycleRefreshInterval = {
                    val next = (providerSettings[playlist.id] ?: ProviderUiSettings()).refreshIntervalHours.next()
                    scope.launch { controller.setProviderRefreshInterval(playlist.id, next) }
                },
                onSetRefreshOnAppStart = { enabled ->
                    scope.launch { controller.setProviderRefreshOnAppStart(playlist.id, enabled) }
                },
                onSetRefreshOnPlaylistChange = { enabled ->
                    scope.launch { controller.setProviderRefreshOnPlaylistChange(playlist.id, enabled) }
                },
                onSetUserAgent = { userAgent ->
                    scope.launch {
                        controller.setProviderUserAgent(playlist.id, userAgent)
                        providerEditStatus = "Advanced provider options saved"
                    }
                },
                onCycleXtreamOutputFormat = {
                    val next = (providerSettings[playlist.id] ?: ProviderUiSettings()).xtreamOutputFormat.next()
                    scope.launch {
                        controller.setProviderXtreamOutputFormat(playlist.id, next)
                        providerEditStatus = "Xtream output format updated. Refresh the provider to rebuild stream URLs."
                    }
                },
                onCycleXtreamApiScope = {
                    val next = (providerSettings[playlist.id] ?: ProviderUiSettings()).xtreamApiScope.next()
                    scope.launch {
                        controller.setProviderXtreamApiScope(playlist.id, next)
                        providerEditStatus = "Xtream API scope updated. Refresh the provider to update capability metadata."
                    }
                },
                onSaveName = { name ->
                    scope.launch {
                        val result = controller.renameProvider(playlist.id, name)
                        providerEditStatus = result.fold(
                            onSuccess = { "Provider renamed" },
                            onFailure = { error -> "Rename failed: ${error.message ?: error::class.simpleName}" }
                        )
                        if (result.isSuccess) {
                            providerToEdit = null
                        }
                    }
                },
                onSaveConnection = { sourceUri, sourceUsername, sourcePassword ->
                    scope.launch {
                        val result = controller.updateProviderConnection(
                            playlistId = playlist.id,
                            sourceUri = sourceUri,
                            sourceUsername = sourceUsername,
                            sourcePassword = sourcePassword
                        )
                        providerEditStatus = result.fold(
                            onSuccess = { "Connection updated. Refresh the provider to import changes." },
                            onFailure = { error -> "Connection failed: ${error.message ?: error::class.simpleName}" }
                        )
                    }
                },
                onSaveEpgUrl = { epgUrl ->
                    scope.launch {
                        val result = controller.saveEpgSource(playlist, epgUrl)
                        providerEditStatus = result.fold(
                            onSuccess = { "EPG source saved" },
                            onFailure = { error -> "EPG failed: ${error.message ?: error::class.simpleName}" }
                        )
                    }
                },
                onRemoveEpgUrl = {
                    scope.launch {
                        val result = controller.removeEpgSource(playlist.id)
                        providerEditStatus = result.fold(
                            onSuccess = { "EPG source removed" },
                            onFailure = { error -> "EPG failed: ${error.message ?: error::class.simpleName}" }
                        )
                    }
                },
                onSetAssignedGlobalEpgSource = { sourceId, enabled ->
                    scope.launch {
                        val current = controller.getProviderEpgAssignments(playlist.id).toMutableList()
                        if (enabled) {
                            if (sourceId !in current) current.add(sourceId)
                        } else {
                            current.remove(sourceId)
                        }
                        controller.setProviderEpgAssignments(playlist.id, current)
                        providerEditStatus = if (enabled) {
                            "Global EPG source assigned"
                        } else {
                            "Global EPG source removed"
                        }
                    }
                },
                onMakeGlobalEpgSourcePrimary = { sourceId ->
                    scope.launch {
                        val current = controller.getProviderEpgAssignments(playlist.id).toMutableList()
                        current.remove(sourceId)
                        current.add(0, sourceId)
                        controller.setProviderEpgAssignments(playlist.id, current)
                        providerEditStatus = "Primary global EPG source updated"
                    }
                },
                onClearAssignedGlobalEpgSources = {
                    scope.launch {
                        controller.setProviderEpgAssignments(playlist.id, emptyList())
                        providerEditStatus = "Global EPG assignments cleared"
                    }
                },
                onResetProviderAdvancedOptions = {
                    scope.launch {
                        controller.resetProviderAdvancedOptions(playlist.id)
                        providerEditStatus = "Advanced provider options reset"
                    }
                },
                onResetProviderSyncState = {
                    scope.launch {
                        controller.clearProviderSyncState(playlist.id)
                        providerEditStatus = "Provider sync state cleared"
                    }
                },
                refreshingProvider = refreshingProviderId == playlist.id,
                onRefreshProvider = {
                    scope.launch {
                        refreshingProviderId = playlist.id
                        providerEditStatus = "Refreshing provider..."
                        val result = controller.refreshProvider(playlist)
                        providerEditStatus = result.fold(
                            onSuccess = { refreshed ->
                                "Provider refreshed: ${refreshed.summary()}"
                            },
                            onFailure = { error -> "Refresh failed: ${error.message ?: error::class.simpleName}" }
                        )
                        refreshingProviderId = null
                    }
                },
                importingEpg = importingEpg,
                onImportEpg = { epgSource ->
                    scope.launch {
                        importingEpg = true
                        providerEditStatus = "Importing EPG..."
                        val result = controller.importEpg(playlist, epgSource)
                        providerEditStatus = result.fold(
                            onSuccess = {
                                "EPG imported: ${it.importedProgramCount} programmes matched, ${it.xmltvChannelCount} XMLTV channels, ${it.unmatchedProgramCount} unmatched"
                            },
                            onFailure = { error -> "EPG failed: ${error.message ?: error::class.simpleName}" }
                        )
                        importingEpg = false
                    }
                },
                onDeleteProvider = {
                    scope.launch {
                        val result = controller.deleteProvider(playlist.id)
                        providerEditStatus = result.fold(
                            onSuccess = { "Provider deleted" },
                            onFailure = { error -> "Delete failed: ${error.message ?: error::class.simpleName}" }
                        )
                        if (result.isSuccess) {
                            if (selectedPlaylistId == playlist.id) {
                                selectedPlaylistId = null
                            }
                            if (selectedChannel?.playlistId == playlist.id) {
                                selectedChannel = null
                                controller.stopPlayback()
                            }
                            providerToEdit = null
                        }
                    }
                }
            )
        }

}
