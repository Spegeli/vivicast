package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Standard width tiers for [VivicastDialog]. Add a tier rather than tuning dialogs individually. */
enum class VivicastDialogWidth { Compact, Standard, Wide }

private fun Modifier.vivicastDialogWidth(width: VivicastDialogWidth): Modifier = when (width) {
    VivicastDialogWidth.Compact -> this.widthIn(min = 360.dp, max = 480.dp)
    VivicastDialogWidth.Standard -> this.widthIn(min = 560.dp, max = 720.dp)
    VivicastDialogWidth.Wide -> this.widthIn(min = 720.dp, max = 960.dp)
}

/**
 * Shared popup scaffold: Dialog + GlassPanel with standard width tier, BACK-to-dismiss, an
 * optional title, and a uniform content column spacing. Pass [title] = null for self-contained
 * content that renders its own heading (e.g. the multi-step provider flow).
 */
@Composable
fun VivicastDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: VivicastDialogWidth = VivicastDialogWidth.Compact,
    heightCap: Dp? = null,
    title: String? = null,
    initialFocus: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (initialFocus != null) {
        LaunchedEffect(Unit) { runCatching { initialFocus.requestFocus() } }
    }
    Dialog(onDismissRequest = onDismiss) {
        VivicastGlassPanel(
            modifier = modifier
                .vivicastDialogWidth(width)
                .then(if (heightCap != null) Modifier.heightIn(max = heightCap) else Modifier)
                .onPreviewKeyEvent {
                    if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            contentPadding = VivicastSpacing.Space5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4)) {
                if (title != null) {
                    SectionTitle(title)
                }
                content()
            }
        }
    }
}

/**
 * Standard dialog button row: equal-width buttons (widest button's width), centered, secondary
 * (and optional tertiary) on the left, primary on the right. Primary is highlighted only via focus.
 */
@Composable
fun VivicastDialogActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    tertiaryLabel: String? = null,
    onTertiary: () -> Unit = {},
    primaryTestTag: String? = null,
    secondaryTestTag: String? = null,
    tertiaryTestTag: String? = null,
    primaryFocusRequester: FocusRequester? = null,
    secondaryFocusRequester: FocusRequester? = null,
) {
    VivicastButtonRow(modifier = modifier) {
        ActionPill(
            label = secondaryLabel,
            modifier = Modifier
                .then(if (secondaryFocusRequester != null) Modifier.focusRequester(secondaryFocusRequester) else Modifier)
                .then(if (secondaryTestTag != null) Modifier.testTag(secondaryTestTag) else Modifier),
            onClick = onSecondary,
        )
        if (tertiaryLabel != null) {
            ActionPill(
                label = tertiaryLabel,
                modifier = if (tertiaryTestTag != null) Modifier.testTag(tertiaryTestTag) else Modifier,
                onClick = onTertiary,
            )
        }
        ActionPill(
            label = primaryLabel,
            modifier = Modifier
                .then(if (primaryFocusRequester != null) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                .then(if (primaryTestTag != null) Modifier.testTag(primaryTestTag) else Modifier),
            onClick = onPrimary,
        )
    }
}

/** Compact, consistent error/hint line for popups: small red text, same look everywhere. */
@Composable
fun VivicastDialogError(text: String?, modifier: Modifier = Modifier) {
    if (text != null) {
        BasicText(
            text = text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
            style = VivicastTypography.LabelSmall.copy(color = VivicastColors.Error),
        )
    }
}
