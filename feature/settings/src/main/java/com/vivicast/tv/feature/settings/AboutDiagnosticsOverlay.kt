package com.vivicast.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastCheckbox
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastContentCard
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastFocusSurface
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastShapes
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/**
 * "Diagnose & Protokolle" — a self-contained About sub-view destination: logging toggle, export, delete
 * (multi-select dialog), and a "view log" row that navigates to the [AboutDiagnosticsLogScreen] destination
 * (D1 = a: the log viewer is its own destination, not an internal mode). The delete confirmation stays a
 * dialog. See SettingsRoute's AboutGraph wiring.
 */
@Composable
internal fun AboutDiagnosticsScreen(
    loggingEnabled: Boolean,
    exporting: Boolean,
    // True when re-entering after BACK from the log viewer: focus the "view log" row instead of the toggle.
    returnFromLog: Boolean,
    onToggleLogging: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenLogViewer: () -> Unit,
    onDeleteLogs: suspend (Set<DiagnosticsLogKind>) -> Unit,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val toggleFocus = remember { FocusRequester() }
    val viewLogRowFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Land focus on entry: the toggle normally, the view-log row when coming back from the viewer.
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { (if (returnFromLog) viewLogRowFocus else toggleFocus).requestFocus() }
    }

    DiagnosticsRows(
        modifier = modifier,
        loggingEnabled = loggingEnabled,
        exporting = exporting,
        firstFocusModifier = entryFocusModifier.focusRequester(toggleFocus),
        viewLogRowFocus = viewLogRowFocus,
        onToggleLogging = onToggleLogging,
        onExportDiagnostics = onExportDiagnostics,
        onOpenDeleteDialog = { showDeleteDialog = true },
        onViewLog = onOpenLogViewer,
    )

    if (showDeleteDialog) {
        DiagnosticsDeleteDialog(
            deleting = deleting,
            onCancel = { if (!deleting) showDeleteDialog = false },
            onConfirm = { selected ->
                scope.launch {
                    deleting = true
                    runCatching { onDeleteLogs(selected) }
                    deleting = false
                    showDeleteDialog = false
                }
            },
        )
    }
}

@Composable
private fun DiagnosticsRows(
    loggingEnabled: Boolean,
    exporting: Boolean,
    firstFocusModifier: Modifier,
    viewLogRowFocus: FocusRequester,
    onToggleLogging: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
    onViewLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        SectionTitle(text = stringResource(R.string.about_diagnostics_section))
        VivicastSettingsRow(
            title = stringResource(R.string.about_diagnostics_logging),
            help = stringResource(R.string.about_help_logging),
            value = if (loggingEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
            // First focusable: carries the entry requester + entry focus, blocks Up (top-nav navigates on focus).
            modifier = firstFocusModifier.focusProperties { up = FocusRequester.Cancel },
            onClick = onToggleLogging,
        )
        VivicastSettingsRow(
            title = stringResource(R.string.about_diagnostics_view),
            help = "",
            value = stringResource(R.string.about_open_value),
            modifier = Modifier.focusRequester(viewLogRowFocus),
            onClick = onViewLog,
        )
        VivicastSettingsRow(
            title = stringResource(R.string.about_export_diagnostics),
            help = stringResource(R.string.about_help_export_diagnostics),
            value = stringResource(if (exporting) R.string.about_exporting_diagnostics else R.string.common_export),
            forceTextValue = true,
            valueLoading = exporting,
            onClick = { if (!exporting) onExportDiagnostics() },
        )
        VivicastSettingsRow(
            title = stringResource(R.string.about_diagnostics_delete),
            help = "",
            value = "",
            forceTextValue = true,
            onClick = onOpenDeleteDialog,
        )
        // Static info (non-focusable): what the toggle records, and the privacy guarantee that no private
        // data is ever logged. Compact text so both cards fit below the last focusable row (Delete logs) —
        // the focus-driven scroll rests there and can't reveal anything taller further down.
        DiagnosticsInfoCard(
            title = stringResource(R.string.about_diagnostics_info_logged_title),
            body = stringResource(R.string.about_diagnostics_info_logged_body),
        )
        DiagnosticsInfoCard(
            title = stringResource(R.string.about_diagnostics_info_privacy_title),
            body = stringResource(R.string.about_diagnostics_info_privacy_body),
        )
    }
}

// A small tinted hint card — just an informational note, so the text is deliberately smaller than a
// standard InfoPanel to keep both cards inside the viewport under the last focusable row.
@Composable
private fun DiagnosticsInfoCard(title: String, body: String) {
    VivicastContentCard(modifier = Modifier.fillMaxWidth(), contentPadding = VivicastSpacing.Space3) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
            BasicText(text = title, style = VivicastTypography.LabelMedium)
            BasicText(
                text = body,
                style = VivicastTypography.LabelSmall.copy(color = VivicastColors.TextSecondary),
                maxLines = MAX_INFO_LINES,
            )
        }
    }
}

// Diagnostics info paragraphs run to a few lines at TV width; keep them un-truncated.
private const val MAX_INFO_LINES = 6

/**
 * Diagnostics log viewer as its own destination (D1 = a): a fixed Normale-Logs / Crash-Logs switch (+ Refresh
 * for normal logs) over the log tail. The first pill carries the entry requester (rail RIGHT re-entry) + the
 * nav-in initial focus, and the log list redirects its Up-exit back to that first pill.
 */
@OptIn(ExperimentalComposeUiApi::class) // FocusProperties.exit (Up-exit redirect to the pills)
@Composable
internal fun AboutDiagnosticsLogScreen(
    onReadLog: suspend (DiagnosticsLogKind) -> String?,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(DiagnosticsLogKind.Events) }
    var content by remember { mutableStateOf<String?>(null) }
    val firstPillFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val reload: () -> Unit = { scope.launch { content = onReadLog(kind) } }
    LaunchedEffect(kind) { content = onReadLog(kind) }
    // Land focus on the first pill after the nav swap, else it escapes to the top nav.
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstPillFocus.requestFocus() }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        SectionTitle(text = stringResource(R.string.about_diagnostics_section))
        // Fixed widths: ActionPill otherwise fills its 300dp cap, so two tabs alone would push Refresh
        // off-screen. These fit the tabs + Refresh across the detail panel.
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            ActionPill(
                label = stringResource(R.string.about_diagnostics_kind_events),
                selected = kind == DiagnosticsLogKind.Events,
                // First focusable of the viewer: entry requester + entry focus, blocks Up.
                modifier = entryFocusModifier
                    .focusRequester(firstPillFocus)
                    .focusProperties { up = FocusRequester.Cancel }
                    .width(200.dp),
                onClick = { kind = DiagnosticsLogKind.Events },
            )
            ActionPill(
                label = stringResource(R.string.about_diagnostics_kind_crashes),
                selected = kind == DiagnosticsLogKind.Crashes,
                modifier = Modifier.width(200.dp),
                onClick = { kind = DiagnosticsLogKind.Crashes },
            )
            // Manual refresh only for the normal (growing) event log; crash logs don't change while open.
            if (kind == DiagnosticsLogKind.Events) {
                ActionPill(
                    label = stringResource(R.string.about_diagnostics_refresh),
                    modifier = Modifier.width(150.dp),
                    onClick = reload,
                )
            }
        }
        val text = content
        if (text.isNullOrBlank()) {
            BodyText(
                stringResource(
                    if (kind == DiagnosticsLogKind.Events) R.string.about_diagnostics_empty_events
                    else R.string.about_diagnostics_empty_crashes,
                ),
            )
        } else {
            // Newest-first: after a refresh the latest events sit right under the pills, no scroll to the
            // bottom. Blank lines dropped so a trailing newline doesn't leave an empty top row. The export
            // keeps chronological order — only this on-screen view is reversed.
            val lines = remember(text) { text.lines().filter { it.isNotBlank() }.asReversed() }
            LazyColumn(
                // UP from the top line must return to the pills. Exiting a lazy list upward to a non-lazy
                // sibling doesn't resolve reliably on TV, so redirect the Up-exit explicitly to the first pill.
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .focusGroup()
                    .focusProperties {
                        exit = { direction ->
                            if (direction == FocusDirection.Up) firstPillFocus else FocusRequester.Default
                        }
                    },
            ) {
                items(lines) { line -> DiagnosticsLogLine(line) }
            }
        }
    }
}

// One focusable log line so the D-pad scrolls the LazyColumn through the full tail.
@Composable
private fun DiagnosticsLogLine(text: String) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(
                color = if (focused) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                shape = VivicastShapes.CardRadius,
            )
            .padding(horizontal = VivicastSpacing.Space3, vertical = VivicastSpacing.Space1),
    ) {
        BasicText(
            text = text.ifBlank { " " },
            maxLines = Int.MAX_VALUE,
            style = VivicastTypography.LabelSmall.copy(color = VivicastColors.TextSecondary),
        )
    }
}

@Composable
private fun DiagnosticsDeleteDialog(
    deleting: Boolean,
    onCancel: () -> Unit,
    onConfirm: (Set<DiagnosticsLogKind>) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<DiagnosticsLogKind>()) }
    val firstFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        title = stringResource(R.string.about_diagnostics_delete_title),
        initialFocus = firstFocus,
    ) {
        BodyText(stringResource(R.string.about_diagnostics_delete_body))
        DiagnosticsDeleteCategories.forEachIndexed { index, (target, labelRes) ->
            DiagnosticsCategoryRow(
                label = stringResource(labelRes),
                checked = target in selected,
                onToggle = {
                    if (!deleting) {
                        selected = if (target in selected) selected - target else selected + target
                    }
                },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        VivicastDialogActions(
            primaryLabel = stringResource(if (deleting) R.string.settings_deleting else R.string.common_delete),
            onPrimary = { onConfirm(selected) },
            primaryEnabled = selected.isNotEmpty() && !deleting,
            primaryLoading = deleting,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
        )
    }
}

private val DiagnosticsDeleteCategories: List<Pair<DiagnosticsLogKind, Int>> = listOf(
    DiagnosticsLogKind.Events to R.string.about_diagnostics_kind_events,
    DiagnosticsLogKind.Crashes to R.string.about_diagnostics_kind_crashes,
)

@Composable
private fun DiagnosticsCategoryRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VivicastFocusSurface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth().height(VivicastCardSizes.CompactSettingsRowHeight),
        contentPadding = 0.dp,
        focusScale = 1.0f,
    ) { _ ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = VivicastSpacing.Space3),
        ) {
            VivicastCheckbox(checked = checked)
            Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
            BodyText(label, maxLines = 1)
        }
    }
}
