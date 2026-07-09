package com.vivicast.tv.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
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
        options.forEach { option ->
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
