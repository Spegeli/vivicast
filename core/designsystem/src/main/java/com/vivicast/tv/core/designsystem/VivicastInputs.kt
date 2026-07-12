package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

@Composable
fun ActionPill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    // fillWidth = stretch to the parent width and show the full label (for vertical option lists) instead of
    // the default content-sized pill capped at 300dp (which ellipsizes long labels in a horizontal row).
    fillWidth: Boolean = false,
    height: Dp = VivicastCardSizes.ActionPillHeight,
    onClick: () -> Unit = {},
) {
    VivicastFocusSurface(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.widthIn(min = 80.dp, max = 300.dp))
            .height(height)
            .then(if (enabled) Modifier else Modifier.alpha(0.5f)),
        selected = selected,
        onClick = { if (enabled && !loading) onClick() },
        contentPadding = 0.dp,
        shape = VivicastShapes.PillRadius,
        focusScale = VivicastFocusDefaults.ScaleButton,
    ) { focused ->
        val contentColor = if (focused || selected) Color.White else VivicastColors.TextSecondary
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VivicastSpacing.Space4),
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) VivicastSpinner(size = 18.dp, color = contentColor)
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(color = contentColor),
            )
        }
    }
}

/** Small indeterminate spinner (a rotating arc) for showing background work, e.g. an in-flight save. */
@Composable
fun VivicastSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = VivicastColors.FocusRing,
    strokeWidth: Dp = 2.dp,
) {
    // Driven by the raw frame clock (not an animation spec) so it still spins when the device has
    // system animations disabled — a progress spinner must indicate work regardless of that setting.
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle = ((now - startNanos) / 1_000_000f / 900f * 360f) % 360f
            }
        }
    }
    Canvas(modifier = modifier.size(size).rotate(angle)) {
        val stroke = strokeWidth.toPx()
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(this.size.width - stroke, this.size.height - stroke),
        )
    }
}

/**
 * Standard dialog text field: optional label, theme rounding, focus highlight, placeholder, and
 * error state (red border + label). [label] = null for single-field dialogs where the title labels it.
 */
@Composable
fun VivicastTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    secret: Boolean = false,
    allowReveal: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    height: Dp = 58.dp,
    focusRequester: FocusRequester? = null,
    isError: Boolean = false,
    maxLength: Int? = null,
    trailingActionLabel: String? = null,
    onTrailingAction: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        if (label != null) {
            BasicText(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(
                    color = if (isError) VivicastColors.Error else VivicastColors.TextSecondary,
                ),
            )
        }
        val field: @Composable (Modifier) -> Unit = { widthModifier ->
            BasicTextField(
                value = value,
                onValueChange = { if (maxLength != null) onValueChange(it.take(maxLength)) else onValueChange(it) },
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                textStyle = VivicastTypography.LabelLarge.copy(color = VivicastColors.TextPrimary),
                visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = widthModifier
                    .height(height)
                    .then(fieldModifier)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(VivicastShapes.RadiusMedium))
                    .background(if (focused) VivicastColors.SurfaceSelected else VivicastColors.Surface)
                    .border(
                        width = if (focused || isError) VivicastBorders.FocusWidth else VivicastBorders.Hairline,
                        color = when {
                            isError -> VivicastColors.Error
                            focused -> VivicastColors.FocusRing
                            else -> Color(0x66344A62)
                        },
                        shape = RoundedCornerShape(VivicastShapes.RadiusMedium),
                    )
                    .padding(horizontal = VivicastSpacing.Space4, vertical = VivicastSpacing.Space3),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            BasicText(
                                text = placeholder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = VivicastTypography.LabelLarge.copy(color = VivicastColors.TextTertiary),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (secret && allowReveal) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
            ) {
                field(Modifier.weight(1f))
                PasswordRevealToggle(revealed = revealed, onClick = { revealed = !revealed }, boxSize = height)
            }
        } else {
            field(Modifier.fillMaxWidth())
        }
        if (trailingActionLabel != null) {
            ActionPill(
                label = trailingActionLabel,
                modifier = Modifier.width(150.dp),
                onClick = onTrailingAction,
            )
        }
    }
}

/** Focusable eye toggle that reveals/hides a secret text field. Slash overlay = currently hidden. */
@Composable
private fun PasswordRevealToggle(
    revealed: Boolean,
    onClick: () -> Unit,
    boxSize: Dp,
) {
    VivicastFocusSurface(
        modifier = Modifier.size(boxSize),
        onClick = onClick,
        contentPadding = VivicastSpacing.Space2,
        shape = RoundedCornerShape(VivicastShapes.RadiusMedium),
        focusScale = VivicastFocusDefaults.ScaleButton,
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val color = if (focused) Color.White else VivicastColors.TextSecondary
                val sw = 1.8f
                val stroke = Stroke(width = sw, cap = StrokeCap.Round)
                val w = size.width
                val h = size.height
                drawOval(
                    color = color,
                    topLeft = Offset(w * 0.08f, h * 0.30f),
                    size = Size(w * 0.84f, h * 0.40f),
                    style = stroke,
                )
                drawCircle(color = color, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                if (!revealed) {
                    drawLine(
                        color = color,
                        start = Offset(w * 0.16f, h * 0.18f),
                        end = Offset(w * 0.84f, h * 0.82f),
                        strokeWidth = sw,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * Lays out its children (buttons) all at the width of the widest child and centers the group.
 * Opt-in: use inside popups to give a button row uniform widths. Not used by the top navigation.
 */
@Composable
fun VivicastButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = VivicastSpacing.Space3,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.maxWidth, 0) {}
        }
        val gapPx = spacing.roundToPx()
        val totalGap = gapPx * (measurables.size - 1)
        val avail = constraints.maxWidth
        val intrinsics = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        // Prefer equal-width (widest label) for symmetry; if that overflows the row, fall back to each
        // button's own width; if even that overflows, shrink all to an equal share (labels ellipsize).
        val widest = intrinsics.max()
        val placeables = when {
            widest * measurables.size + totalGap <= avail ->
                measurables.map { it.measure(constraints.copy(minWidth = widest, maxWidth = widest)) }
            intrinsics.sum() + totalGap <= avail ->
                measurables.mapIndexed { i, m -> m.measure(constraints.copy(minWidth = intrinsics[i], maxWidth = intrinsics[i])) }
            else -> {
                val share = ((avail - totalGap) / measurables.size).coerceAtLeast(0)
                measurables.map { it.measure(constraints.copy(minWidth = share, maxWidth = share)) }
            }
        }
        val rowHeight = placeables.maxOf { it.height }
        val totalWidth = placeables.sumOf { it.width } + totalGap
        val startX = ((avail - totalWidth) / 2).coerceAtLeast(0)
        layout(constraints.maxWidth, rowHeight) {
            var x = startX
            placeables.forEach { placeable ->
                placeable.placeRelative(x, (rowHeight - placeable.height) / 2)
                x += placeable.width + gapPx
            }
        }
    }
}

@Composable
fun VivicastSettingsRow(
    title: String,
    help: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    // Rows whose value happens to read "An"/"Aus" but are NOT on/off toggles (e.g. an interval set to
    // "off") set this to keep the value as plain text + chevron instead of rendering a switch.
    forceTextValue: Boolean = false,
    onClick: () -> Unit = {},
) {
    val disabledColor = VivicastColors.TextSecondary.copy(alpha = 0.55f)
    val titleColor = if (enabled) VivicastColors.TextPrimary else disabledColor
    val secondaryColor = if (enabled) VivicastColors.TextSecondary else disabledColor
    val strOn = stringResource(R.string.value_on)
    val strOff = stringResource(R.string.value_off)
    val isToggle = !forceTextValue && (value == strOn || value == strOff)
    val isOn = value == strOn

    @Composable
    fun RowContent(focused: Boolean) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = VivicastSpacing.Space3),
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(color = titleColor),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(VivicastSpacing.Space4))
            if (isToggle) {
                VivicastToggle(isOn = isOn, enabled = enabled)
            } else {
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelSmall.copy(
                        color = when {
                            !enabled -> disabledColor
                            focused -> VivicastColors.FocusRing
                            else -> VivicastColors.TextSecondary
                        },
                    ),
                )
                if (enabled) {
                    Spacer(modifier = Modifier.width(VivicastSpacing.Space2))
                    Text(
                        text = "›",
                        maxLines = 1,
                        style = VivicastTypography.LabelMedium.copy(color = secondaryColor),
                    )
                }
            }
        }
    }

    if (!enabled) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(VivicastCardSizes.CompactSettingsRowHeight)
                .clip(VivicastShapes.CardRadius)
                .background(VivicastColors.SurfaceDisabled),
            contentAlignment = Alignment.CenterStart,
        ) {
            RowContent(focused = false)
        }
        return
    }

    VivicastFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(VivicastCardSizes.CompactSettingsRowHeight),
        contentPadding = 0.dp,
        focusScale = 1.0f,
    ) { focused ->
        RowContent(focused = focused)
    }
}

/**
 * Multi-select checkbox visual (rounded box + checkmark). Pure visual, like [VivicastToggle]: put it
 * inside a focusable row (e.g. [VivicastFocusSurface]) that flips the [checked] state on click.
 */
@Composable
fun VivicastCheckbox(checked: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val boxColor = when {
        !enabled -> VivicastColors.SurfaceHigh
        checked -> VivicastColors.Accent
        else -> VivicastColors.Surface
    }
    val borderColor = if (checked && enabled) VivicastColors.Accent else Color(0x66344A62)
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(boxColor)
            .border(width = VivicastBorders.Hairline, color = borderColor, shape = RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val w = size.width
                val h = size.height
                val stroke = 2.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.12f, h * 0.55f),
                    end = Offset(w * 0.42f, h * 0.82f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.42f, h * 0.82f),
                    end = Offset(w * 0.88f, h * 0.20f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun VivicastToggle(isOn: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val trackColor = when {
        !enabled -> VivicastColors.SurfaceHigh
        isOn -> VivicastColors.Accent
        else -> VivicastColors.SurfaceHigh
    }
    val thumbColor = if (enabled) Color.White else VivicastColors.TextDisabled
    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor),
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(thumbColor),
        )
    }
}
