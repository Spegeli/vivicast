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
internal fun MaintenanceSettingsPanel(
    cacheSettingsState: CacheSettingsState,
    onClearCache: () -> Unit,
    onClearHistory: (HistoryClearTarget) -> Unit,
    onReloadCacheStats: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<MaintenanceAction?>(null) }
    val strCacheRefreshed = stringResource(R.string.settings_cache_refreshed)
    val strCacheCleared = stringResource(R.string.settings_cache_cleared)
    val strHistoryCleared = stringResource(R.string.settings_history_cleared)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_cache_info),
                help = stringResource(R.string.settings_cache_info_help, cacheSettingsState.fileCount),
                value = formatCacheSize(cacheSettingsState.totalSizeBytes),
                modifier = firstFocusModifier,
                onClick = {
                    onReloadCacheStats()
                    message = strCacheRefreshed
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_cache_clear),
                help = stringResource(R.string.settings_cache_clear_row_help),
                value = stringResource(R.string.settings_cache_clear_value),
                onClick = {
                    pendingAction = MaintenanceAction.ClearCache
                },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_history_clear),
                help = stringResource(R.string.settings_history_clear_row_help),
                value = stringResource(R.string.settings_history_clear_all_value),
                onClick = {
                    pendingAction = MaintenanceAction.ClearAllHistory
                },
            )
        }

        items(MaintenanceAction.SelectiveHistoryActions) { action ->
            VivicastSettingsRow(
                title = stringResource(action.rowTitleRes),
                help = stringResource(action.rowHelpRes),
                value = stringResource(R.string.common_delete),
                onClick = {
                    pendingAction = action
                },
            )
        }

        if (message != null) {
            item {
                InfoPanel(
                    title = stringResource(R.string.common_note),
                    body = message.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingAction?.let { action ->
        MaintenanceConfirmDialog(
            action = action,
            onCancel = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                when (action) {
                    MaintenanceAction.ClearCache -> {
                        onClearCache()
                        message = strCacheCleared
                    }
                    else -> {
                        onClearHistory(requireNotNull(action.historyTarget))
                        message = strHistoryCleared
                    }
                }
            },
        )
    }
}

@Composable
private fun MaintenanceConfirmDialog(
    action: MaintenanceAction,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocus,
        modifier = Modifier.testTag(action.dialogTag),
    ) {
        InfoPanel(
            title = stringResource(action.confirmTitleRes),
            body = stringResource(action.bodyRes),
            badge = stringResource(R.string.settings_maintenance_confirm_badge),
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(action.confirmLabelRes),
            onPrimary = onConfirm,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            secondaryFocusRequester = cancelFocus,
        )
    }
}

private enum class MaintenanceAction(
    @get:StringRes val rowTitleRes: Int,
    @get:StringRes val rowHelpRes: Int,
    @get:StringRes val confirmTitleRes: Int,
    @get:StringRes val bodyRes: Int,
    @get:StringRes val confirmLabelRes: Int,
    val dialogTag: String,
    val historyTarget: HistoryClearTarget? = null,
) {
    ClearCache(
        rowTitleRes = R.string.maintenance_cache_clear_title,
        rowHelpRes = R.string.maintenance_cache_clear_row_help,
        confirmTitleRes = R.string.maintenance_cache_clear_confirm,
        bodyRes = R.string.maintenance_cache_clear_body,
        confirmLabelRes = R.string.maintenance_cache_clear_label,
        dialogTag = "settings-clear-cache-dialog",
    ),
    ClearLiveTvHistory(
        rowTitleRes = R.string.maintenance_live_tv_title,
        rowHelpRes = R.string.maintenance_live_tv_row_help,
        confirmTitleRes = R.string.maintenance_live_tv_confirm,
        bodyRes = R.string.maintenance_live_tv_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-live-tv-history-dialog",
        historyTarget = HistoryClearTarget.LiveTv,
    ),
    ClearMovieHistory(
        rowTitleRes = R.string.maintenance_movie_title,
        rowHelpRes = R.string.maintenance_movie_row_help,
        confirmTitleRes = R.string.maintenance_movie_confirm,
        bodyRes = R.string.maintenance_movie_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-movie-history-dialog",
        historyTarget = HistoryClearTarget.Movies,
    ),
    ClearSeriesHistory(
        rowTitleRes = R.string.maintenance_series_title,
        rowHelpRes = R.string.maintenance_series_row_help,
        confirmTitleRes = R.string.maintenance_series_confirm,
        bodyRes = R.string.maintenance_series_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-series-history-dialog",
        historyTarget = HistoryClearTarget.Series,
    ),
    ClearSearchHistory(
        rowTitleRes = R.string.maintenance_search_title,
        rowHelpRes = R.string.maintenance_search_row_help,
        confirmTitleRes = R.string.maintenance_search_confirm,
        bodyRes = R.string.maintenance_search_body,
        confirmLabelRes = R.string.common_delete,
        dialogTag = "settings-clear-search-history-dialog",
        historyTarget = HistoryClearTarget.Search,
    ),
    ClearAllHistory(
        rowTitleRes = R.string.maintenance_all_title,
        rowHelpRes = R.string.maintenance_all_row_help,
        confirmTitleRes = R.string.maintenance_all_confirm,
        bodyRes = R.string.maintenance_all_body,
        confirmLabelRes = R.string.maintenance_all_label,
        dialogTag = "settings-clear-history-dialog",
        historyTarget = HistoryClearTarget.All,
    );

    companion object {
        val SelectiveHistoryActions = listOf(
            ClearLiveTvHistory,
            ClearMovieHistory,
            ClearSeriesHistory,
            ClearSearchHistory,
        )
    }
}

private fun formatCacheSize(sizeBytes: Long): String {
    val megabytes = sizeBytes / (1024L * 1024L)
    if (megabytes < 1024L) return "$megabytes MB"
    val gigabytes = megabytes / 1024L
    val remainingMegabytes = megabytes % 1024L
    return if (remainingMegabytes == 0L) {
        "$gigabytes GB"
    } else {
        "$gigabytes.${remainingMegabytes * 10L / 1024L} GB"
    }
}

