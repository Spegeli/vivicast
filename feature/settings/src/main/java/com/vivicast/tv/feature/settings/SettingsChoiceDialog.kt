package com.vivicast.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography

/**
 * Generic single-choice popup picker (VivicastDialog + focusable option rows) that opens with focus on
 * the current value. Generalizes the former hand-rolled language / logo-priority pickers so every
 * settings enum uses one dialog. Option labels stay localized in the caller via the [label] lambda —
 * no strings in this shared composable.
 */
@Composable
internal fun <T> SettingsChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    width: VivicastDialogWidth = VivicastDialogWidth.Compact,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = width,
        title = title,
        initialFocus = selectedFocusRequester,
    ) {
        // Cap height to ~5 rows + scroll so long option lists (e.g. 11 transparency steps) don't fill the
        // screen. Start scrolled so the current value is already visible (not just at the bottom edge) —
        // one row above it, so it opens with context, not needing a scroll to find the selection.
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0)),
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
        ) {
            items(options) { option ->
                FocusPanel(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(unbounded = true)
                        .then(if (option == selected) Modifier.focusRequester(selectedFocusRequester) else Modifier),
                ) {
                    BasicText(
                        text = label(option),
                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                    )
                }
            }
        }
    }
}

/**
 * Colour picker: same VivicastDialog scaffold as [SettingsChoiceDialog], but each option is a filled
 * swatch circle + a small name, laid out in a 3-column grid so the whole predefined palette stays
 * compact. D-pad moves spatially across the grid; opens focused on the current value.
 */
@Composable
internal fun <T> SettingsColorChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    swatch: (T) -> Color,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    columns: Int = 3,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Compact,
        title = title,
        initialFocus = selectedFocusRequester,
    ) {
        // Start scrolled so the selected colour's row is already visible on open (one row above it),
        // instead of the list always opening at the top and hiding a lower selection.
        val rows = options.chunked(columns)
        val selectedRow = (options.indexOf(selected) / columns).coerceAtLeast(0)
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = (selectedRow - 1).coerceAtLeast(0)),
            modifier = Modifier.heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
        ) {
            items(rows) { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                ) {
                    rowOptions.forEach { option ->
                        FocusPanel(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            contentPadding = VivicastSpacing.Space2,
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentHeight(unbounded = true)
                                .then(
                                    if (option == selected) Modifier.focusRequester(selectedFocusRequester) else Modifier,
                                ),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(swatch(option))
                                        .border(1.dp, Color(0x55FFFFFF), CircleShape),
                                )
                                BasicText(
                                    text = label(option),
                                    style = VivicastTypography.LabelSmall.copy(
                                        color = VivicastColors.TextPrimary,
                                        textAlign = TextAlign.Center,
                                    ),
                                )
                            }
                        }
                    }
                    // Pad a short final row so cells keep equal width.
                    repeat(columns - rowOptions.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
