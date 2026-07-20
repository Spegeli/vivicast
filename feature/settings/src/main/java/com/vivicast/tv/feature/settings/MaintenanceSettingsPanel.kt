package com.vivicast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.VivicastCheckbox
import com.vivicast.tv.core.designsystem.VivicastFocusSurface
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
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
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

@Composable
internal fun MaintenanceSettingsPanel(
    cacheSettingsState: CacheSettingsState,
    onClearCache: suspend () -> Unit,
    onClearHistory: suspend (Set<HistoryClearTarget>) -> Unit,
    onReloadCacheStats: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var clearingHistory by remember { mutableStateOf(false) }
    // Move focus back onto the "Verlauf löschen" row after the history popup closes (cancel or delete),
    // matching the return-focus pattern used elsewhere (e.g. ProviderSettingsPanel).
    var restoreHistoryFocus by remember { mutableStateOf(false) }
    val historyRowFocus = remember { FocusRequester() }
    val strCacheCleared = stringResource(R.string.settings_cache_cleared)
    val strHistoryCleared = stringResource(R.string.settings_history_cleared)

    // Re-measure the cache each time this panel is shown (the storage section is opened), so the size /
    // file count is current — it only otherwise reloads on Settings open or a manual tap.
    LaunchedEffect(Unit) { onReloadCacheStats() }

    LaunchedEffect(restoreHistoryFocus) {
        if (restoreHistoryFocus) {
            awaitFrame()
            runCatching { historyRowFocus.requestFocus() }
            restoreHistoryFocus = false
        }
    }

    SettingsDetailList {

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_cache_info),
                help = stringResource(R.string.settings_cache_info_help, cacheSettingsState.fileCount),
                value = formatCacheSize(cacheSettingsState.totalSizeBytes),
                modifier = firstFocusModifier,
                // Reloads the size/file-count in place; the updated value is feedback enough (no note).
                onClick = { onReloadCacheStats() },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_cache_clear),
                help = stringResource(R.string.settings_cache_clear_row_help),
                value = stringResource(R.string.settings_cache_clear_value),
                onClick = { showCacheDialog = true },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_history_clear),
                help = stringResource(R.string.settings_history_clear_row_help),
                value = stringResource(R.string.settings_history_clear_value),
                modifier = Modifier.focusRequester(historyRowFocus),
                onClick = { showHistoryDialog = true },
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

    if (showCacheDialog) {
        CacheClearConfirmDialog(
            clearing = clearingCache,
            // Guarded so BACK / Cancel cannot dismiss the dialog mid-clear and hide the spinner.
            onCancel = { if (!clearingCache) showCacheDialog = false },
            onConfirm = {
                scope.launch {
                    clearingCache = true
                    onClearCache()
                    clearingCache = false
                    showCacheDialog = false
                    message = strCacheCleared
                }
            },
        )
    }

    if (showHistoryDialog) {
        HistoryClearDialog(
            clearing = clearingHistory,
            onCancel = {
                if (!clearingHistory) {
                    showHistoryDialog = false
                    restoreHistoryFocus = true
                }
            },
            onConfirm = { selection ->
                if (selection.isEmpty()) return@HistoryClearDialog
                scope.launch {
                    clearingHistory = true
                    onClearHistory(selection)
                    clearingHistory = false
                    showHistoryDialog = false
                    restoreHistoryFocus = true
                    message = strHistoryCleared
                }
            },
        )
    }
}

@Composable
private fun CacheClearConfirmDialog(
    clearing: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocus,
        modifier = Modifier.testTag("settings-clear-cache-dialog"),
    ) {
        InfoPanel(
            title = stringResource(R.string.maintenance_cache_clear_confirm),
            body = stringResource(R.string.maintenance_cache_clear_body),
            badge = stringResource(R.string.settings_maintenance_confirm_badge),
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(
                if (clearing) R.string.maintenance_cache_clearing else R.string.maintenance_cache_clear_label,
            ),
            onPrimary = onConfirm,
            primaryEnabled = !clearing,
            primaryLoading = clearing,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            secondaryFocusRequester = cancelFocus,
        )
    }
}

// The four selectable history categories, in display order. Live-TV first = initial popup focus.
private val HistoryCategories: List<Pair<HistoryClearTarget, Int>> = listOf(
    HistoryClearTarget.LiveTv to R.string.maintenance_history_category_live_tv,
    HistoryClearTarget.Movies to R.string.maintenance_history_category_movies,
    HistoryClearTarget.Series to R.string.maintenance_history_category_series,
    HistoryClearTarget.Search to R.string.maintenance_history_category_search,
)

@Composable
private fun HistoryClearDialog(
    clearing: Boolean,
    onCancel: () -> Unit,
    onConfirm: (Set<HistoryClearTarget>) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<HistoryClearTarget>()) }
    val firstFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        title = stringResource(R.string.settings_history_clear),
        initialFocus = firstFocus,
        modifier = Modifier.testTag("settings-clear-history-dialog"),
    ) {
        BodyText(stringResource(R.string.maintenance_history_dialog_body))
        HistoryCategories.forEachIndexed { index, (target, labelRes) ->
            HistoryCategoryRow(
                label = stringResource(labelRes),
                checked = target in selected,
                onToggle = {
                    if (!clearing) {
                        selected = if (target in selected) selected - target else selected + target
                    }
                },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        VivicastDialogActions(
            primaryLabel = stringResource(if (clearing) R.string.settings_deleting else R.string.common_delete),
            onPrimary = { onConfirm(selected) },
            primaryEnabled = selected.isNotEmpty() && !clearing,
            primaryLoading = clearing,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
        )
    }
}

@Composable
private fun HistoryCategoryRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VivicastFocusSurface(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .height(VivicastCardSizes.CompactSettingsRowHeight),
        contentPadding = 0.dp,
        focusScale = 1.0f,
    ) { _ ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VivicastSpacing.Space3),
        ) {
            VivicastCheckbox(checked = checked)
            Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
            BodyText(label, maxLines = 1)
        }
    }
}

private fun formatCacheSize(sizeBytes: Long): String {
    val megabytes = sizeBytes / (1024L * 1024L)
    if (megabytes < 1L) return "${sizeBytes / 1024L} KB"
    if (megabytes < 1024L) return "$megabytes MB"
    val gigabytes = megabytes / 1024L
    val remainingMegabytes = megabytes % 1024L
    return if (remainingMegabytes == 0L) {
        "$gigabytes GB"
    } else {
        "$gigabytes.${remainingMegabytes * 10L / 1024L} GB"
    }
}

