package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

@Composable
fun VivicastScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    VivicastScreenBackground(
        modifier = modifier,
        content = content,
    )
}

@Composable
fun VivicastScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF02040A),
                        VivicastColors.Background,
                        Color(0xFF081525),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x00111827), Color(0x55101C30), Color(0x00111827)),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = VivicastTypography.TitleMedium,
    )
}

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VivicastColors.TextSecondary,
    maxLines: Int = 3,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = VivicastTypography.BodySmall.copy(color = color),
    )
}

@Composable
fun VivicastFocusSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    showIdleSurface: Boolean = true,
    onClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    contentPadding: Dp = VivicastSpacing.CardPadding,
    shape: RoundedCornerShape = VivicastShapes.CardRadius,
    focusScale: Float = VivicastFocusDefaults.ScaleMedium,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border = Border(
        border = BorderStroke(
            VivicastBorders.Hairline,
            when {
                selected -> VivicastColors.AccentSoft
                showIdleSurface -> Color(0xFF26384F)
                else -> Color.Transparent
            },
        ),
        shape = shape,
    )
    val focusedBorder = Border(
        border = BorderStroke(VivicastFocusDefaults.RingWidth, VivicastColors.FocusRing),
        inset = 0.dp,
        shape = shape,
    )
    val glow = Glow(
        elevation = VivicastFocusDefaults.GlowElevation,
        elevationColor = VivicastColors.FocusGlow,
    )

    TvSurface(
        onClick = onClick ?: {},
        modifier = modifier.onFocusChanged {
            val nowFocused = it.isFocused || it.hasFocus
            focused = nowFocused
            onFocusChanged(nowFocused)
            if (nowFocused) onFocused?.invoke()
        },
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape, pressedShape = shape),
        border = ClickableSurfaceDefaults.border(
            border = border,
            focusedBorder = focusedBorder,
            pressedBorder = focusedBorder,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (showIdleSurface) VivicastColors.Surface else Color.Transparent,
            contentColor = VivicastColors.TextPrimary,
            focusedContainerColor = VivicastColors.SurfaceFocus,
            focusedContentColor = Color.White,
            pressedContainerColor = VivicastColors.SurfacePressed,
            pressedContentColor = Color.White,
            disabledContainerColor = VivicastColors.SurfaceDisabled,
            disabledContentColor = VivicastColors.TextDisabled,
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = glow,
            pressedGlow = glow,
        ),
        scale = ClickableSurfaceDefaults.scale(
            scale = 1f,
            focusedScale = focusScale,
            pressedScale = focusScale,
            disabledScale = 1f,
            focusedDisabledScale = 1f,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(focusSurfaceBrush(focused = focused, selected = selected, enabled = enabled, showIdleSurface = showIdleSurface))
                .padding(contentPadding),
        ) {
            content(focused)
        }
    }
}

@Composable
fun FocusPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    contentPadding: Dp = VivicastSpacing.CardPadding,
    content: @Composable (focused: Boolean) -> Unit,
) {
    VivicastFocusSurface(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged,
        contentPadding = contentPadding,
        focusScale = 1.0f,
        content = content,
    )
}

@Composable
fun VivicastFocusedCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    contentPadding: Dp = VivicastSpacing.CardPadding,
    content: @Composable (focused: Boolean) -> Unit,
) {
    FocusPanel(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        onFocused = onFocused,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun ActionPill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    VivicastFocusSurface(
        modifier = modifier.widthIn(min = 80.dp, max = 260.dp).height(VivicastCardSizes.ActionPillHeight),
        selected = selected,
        onClick = onClick,
        contentPadding = VivicastSpacing.Space2,
        shape = VivicastShapes.PillRadius,
        focusScale = VivicastFocusDefaults.ScaleButton,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VivicastSpacing.Space2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(
                    color = if (focused || selected) Color.White else VivicastColors.TextSecondary,
                ),
            )
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = Color(0xFF1D4F63),
) {
    Box(
        modifier = modifier
            .clip(VivicastShapes.PillRadius)
            .background(tone.copy(alpha = 0.86f))
            .border(VivicastBorders.Hairline, Color(0x5538BDF8), VivicastShapes.PillRadius)
            .padding(horizontal = VivicastSpacing.Space3, vertical = VivicastSpacing.Space1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelSmall.copy(color = Color.White),
        )
    }
}

@Composable
fun VivicastStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = Color(0xFF1D4F63),
) {
    StatusBadge(label = label, modifier = modifier, tone = tone)
}

@Composable
fun VivicastStreamInfoBadge(label: String, modifier: Modifier = Modifier) {
    StatusBadge(label = label, modifier = modifier, tone = Color(0xB5143350))
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = VivicastSpacing.PanelPadding,
    content: @Composable () -> Unit,
) {
    VivicastGlassPanel(modifier = modifier, contentPadding = contentPadding, content = content)
}

@Composable
fun VivicastGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = VivicastSpacing.PanelPadding,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(VivicastShapes.PanelRadius)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF0152438),
                        Color(0xEA0B1626),
                        Color(0xDC07101C),
                    ),
                ),
            )
            .border(VivicastBorders.PanelWidth, Color(0xAA263D56), VivicastShapes.PanelRadius)
            .padding(contentPadding),
    ) {
        content()
    }
}

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
        BasicTextField(
            value = value,
            onValueChange = { if (maxLength != null) onValueChange(it.take(maxLength)) else onValueChange(it) },
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            textStyle = VivicastTypography.LabelLarge.copy(color = VivicastColors.TextPrimary),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
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
        if (trailingActionLabel != null) {
            ActionPill(
                label = trailingActionLabel,
                modifier = Modifier.width(150.dp),
                onClick = onTrailingAction,
            )
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
        val target = measurables.maxOf { it.maxIntrinsicWidth(constraints.maxHeight) }
            .coerceAtMost(constraints.maxWidth)
        val childConstraints = constraints.copy(minWidth = target, maxWidth = target)
        val placeables = measurables.map { it.measure(childConstraints) }
        val rowHeight = placeables.maxOf { it.height }
        val totalWidth = placeables.sumOf { it.width } + gapPx * (placeables.size - 1)
        val startX = ((constraints.maxWidth - totalWidth) / 2).coerceAtLeast(0)
        layout(constraints.maxWidth, rowHeight) {
            var x = startX
            placeables.forEach { placeable ->
                placeable.placeRelative(x, (rowHeight - placeable.height) / 2)
                x += placeable.width + gapPx
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

@Composable
fun VivicastContentCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = VivicastSpacing.CardPadding,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(VivicastShapes.CardRadius)
            .background(Brush.verticalGradient(listOf(Color(0xEF132034), Color(0xE20A1423))))
            .border(VivicastBorders.Hairline, Color(0xFF273B52), VivicastShapes.CardRadius)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun VivicastContentRow(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = VivicastSpacing.Space1),
    horizontalGap: Dp = VivicastSpacing.RowGap,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
            content = content,
        )
    }
}

@Composable
fun HeroPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    backdropResId: Int? = null,
    backdropModel: Any? = null,
    action: (@Composable () -> Unit)? = null,
) {
    VivicastHeroPanel(
        title = title,
        body = body,
        modifier = modifier,
        meta = meta,
        backdropResId = backdropResId,
        backdropModel = backdropModel,
        action = action,
    )
}

@Composable
fun VivicastHeroPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    backdropResId: Int? = null,
    backdropModel: Any? = null,
    action: (@Composable () -> Unit)? = null,
) {
    VivicastGlassPanel(modifier = modifier, contentPadding = VivicastSpacing.Space2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VivicastCardSizes.HeroHeight)
                .clip(VivicastShapes.PanelRadius)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF101B2B), Color(0xFF173552), Color(0xFF2A241C), Color(0xFF100C0A)),
                    ),
                )
                .border(VivicastBorders.Hairline, Color(0x554FC3F7), VivicastShapes.PanelRadius),
        ) {
            if (backdropModel != null || backdropResId != null) {
                if (backdropModel != null) {
                    AsyncImage(
                        model = backdropModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else if (backdropResId != null) {
                    Image(
                        painter = painterResource(backdropResId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF5070A12), Color(0xD20B1626), Color(0x772A170C), Color(0xF0070A12)),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xC9000000)))),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
            modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 860.dp)
                    .padding(horizontal = VivicastSpacing.Space5, vertical = VivicastSpacing.Space4),
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.DisplayMedium,
                )
                if (meta != null) {
                    BodyText(meta, color = VivicastColors.TextSecondary, maxLines = 1)
                }
                Text(
                    text = body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.BodyMedium,
                )
                if (action != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                        action()
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    rating: String,
    meta: String,
    hasPoster: Boolean,
    progressPercent: Int,
    favorite: Boolean,
    seen: Boolean,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    imageResId: Int? = null,
    imageModel: Any? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    VivicastPosterCard(
        title = title,
        rating = rating,
        meta = meta,
        hasPoster = hasPoster,
        progressPercent = progressPercent,
        favorite = favorite,
        seen = seen,
        modifier = modifier,
        surfaceModifier = surfaceModifier,
        imageResId = imageResId,
        imageModel = imageModel,
        onFocused = onFocused,
        onClick = onClick,
    )
}

@Composable
fun VivicastPosterCard(
    title: String,
    rating: String,
    meta: String,
    hasPoster: Boolean,
    progressPercent: Int,
    favorite: Boolean,
    seen: Boolean,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    imageResId: Int? = null,
    imageModel: Any? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.width(VivicastCardSizes.PosterWidth),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
    ) {
        VivicastFocusSurface(
            modifier = surfaceModifier
                .fillMaxWidth()
                .height(VivicastCardSizes.PosterImageHeight),
            onClick = onClick,
            onFocused = onFocused,
            onFocusChanged = { focused = it },
            contentPadding = VivicastSpacing.Space0,
            shape = VivicastShapes.PosterRadius,
            focusScale = VivicastFocusDefaults.ScaleMedium,
        ) { isFocused ->
            PosterArtwork(
                title = title,
                rating = rating,
                hasPoster = hasPoster,
                favorite = favorite,
                seen = seen,
                imageResId = imageResId,
                imageModel = imageModel,
                focused = isFocused,
                progressPercent = progressPercent,
            )
        }
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium.copy(
                color = if (focused) VivicastColors.TextPrimary else VivicastColors.TextSecondary,
            ),
        )
        BodyText(meta, maxLines = 1)
    }
}

@Composable
private fun PosterArtwork(
    title: String,
    rating: String,
    hasPoster: Boolean,
    favorite: Boolean,
    seen: Boolean,
    imageResId: Int?,
    imageModel: Any?,
    focused: Boolean,
    progressPercent: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (hasPoster) {
                    Brush.verticalGradient(listOf(Color(0xFF405973), Color(0xFF203148), Color(0xFF0D1420)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF212B3B), Color(0xFF111827), Color(0xFF0A101B)))
                },
            ),
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA8050910)))),
            )
        } else if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA8050910)))),
            )
        } else {
            Text(
                text = if (hasPoster) initialsFor(title) else "Kein Poster",
                modifier = Modifier.align(Alignment.Center).padding(horizontal = VivicastSpacing.Space4),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.TitleSmall.copy(
                    color = if (focused) Color.White else VivicastColors.TextSecondary,
                ),
            )
        }
        StatusBadge(rating, modifier = Modifier.align(Alignment.TopStart).padding(VivicastSpacing.Space2), tone = Color(0xD011445C))
        if (favorite || seen) {
            StatusBadge(
                label = if (favorite) "F" else "G",
                modifier = Modifier.align(Alignment.TopEnd).padding(VivicastSpacing.Space2),
                tone = if (favorite) Color(0xFF8A640A) else Color(0xFF1D5A3E),
            )
        }
        if (progressPercent > 0) {
            ProgressLine(
                progressPercent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(VivicastSpacing.Space3),
            )
        }
    }
}

@Composable
fun ProgressLine(progressPercent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(VivicastShapes.PillRadius)
            .background(Color(0xFF2A3548)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progressPercent.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .background(VivicastColors.Progress),
        )
    }
}

@Composable
fun InfoPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    VivicastDetailsPanel(title = title, body = body, modifier = modifier, badge = badge)
}

@Composable
fun VivicastDetailsPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Column(
        modifier = modifier
            .clip(VivicastShapes.CardRadius)
            .background(Brush.verticalGradient(listOf(Color(0xE6162335), Color(0xD90B1320))))
            .border(VivicastBorders.Hairline, Color(0xFF263C55), VivicastShapes.CardRadius)
            .padding(VivicastSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.TitleSmall,
            )
            if (badge != null) {
                Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
                StatusBadge(badge)
            }
        }
        BodyText(body, maxLines = 3)
    }
}

@Composable
fun VivicastSearchResultCard(
    title: String,
    subtitle: String,
    rating: String? = null,
    posterLike: Boolean = false,
    imageResId: Int? = null,
    imageModel: Any? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    VivicastFocusSurface(
        modifier = modifier
            .width(if (posterLike) VivicastCardSizes.SearchPosterWidth else VivicastCardSizes.SearchWideWidth)
            .height(if (posterLike) VivicastCardSizes.SearchPosterHeight else VivicastCardSizes.SearchWideHeight),
        onClick = onClick,
        contentPadding = VivicastSpacing.Space3,
    ) { focused ->
        if (posterLike) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                SearchPosterThumb(title = title, rating = rating, imageResId = imageResId, imageModel = imageModel, focused = focused)
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelMedium,
                )
                BodyText(subtitle, maxLines = 1)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelLarge,
                )
                if (rating != null) {
                    StatusBadge("Rating $rating")
                } else {
                    BodyText(subtitle, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SearchPosterThumb(
    title: String,
    rating: String?,
    imageResId: Int?,
    imageModel: Any?,
    focused: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(VivicastShapes.RadiusMediumShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF304860), Color(0xFF101A2B))))
            .border(VivicastBorders.Hairline, Color(0x334FC3F7), VivicastShapes.RadiusMediumShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99070A12)))),
            )
        } else if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99070A12)))),
            )
        } else {
            Text(
                text = initialsFor(title),
                style = VivicastTypography.TitleSmall.copy(color = if (focused) Color.White else VivicastColors.TextSecondary),
            )
        }
        if (rating != null) {
            StatusBadge("Rating $rating", modifier = Modifier.align(Alignment.TopStart).padding(VivicastSpacing.Space1))
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
    onClick: () -> Unit = {},
) {
    val disabledColor = VivicastColors.TextSecondary.copy(alpha = 0.55f)
    val titleColor = if (enabled) VivicastColors.TextPrimary else disabledColor
    val secondaryColor = if (enabled) VivicastColors.TextSecondary else disabledColor
    val strOn = stringResource(R.string.value_on)
    val strOff = stringResource(R.string.value_off)
    val isToggle = value == strOn || value == strOff
    val isOn = value == strOn

    @Composable
    fun RowContent(focused: Boolean) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
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
                .clip(VivicastShapes.CardRadius)
                .background(VivicastColors.SurfaceDisabled)
                .padding(horizontal = VivicastSpacing.Space3, vertical = VivicastSpacing.Space3),
            contentAlignment = Alignment.CenterStart,
        ) {
            RowContent(focused = false)
        }
        return
    }

    VivicastFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = VivicastSpacing.Space3,
        focusScale = 1.0f,
    ) { focused ->
        RowContent(focused = focused)
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

@Composable
fun VivicastTopNavigation(
    brand: String,
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    selectedFocusRequester: FocusRequester? = null,
    onItemFocusChanged: (Boolean) -> Unit = {},
    onSelected: (Int) -> Unit,
    onFocused: (Int) -> Unit,
) {
    val strSearch = stringResource(R.string.nav_search_label)
    val strSettings = stringResource(R.string.nav_settings_label)
    val iconOnlyLabels = setOf(strSearch, strSettings)

    @Composable
    fun NavItem(index: Int, label: String) {
        VivicastTopNavItem(
            label = label,
            selected = index == selectedIndex,
            modifier = Modifier
                .testTag(topNavItemTag(label))
                .then(
                    if (index == selectedIndex && selectedFocusRequester != null) {
                        Modifier.focusRequester(selectedFocusRequester)
                    } else {
                        Modifier
                    },
                ),
            minWidth = topNavItemWidth(label, iconOnlyLabels),
            onSelected = { onSelected(index) },
            onFocused = { onFocused(index) },
            onFocusChanged = onItemFocusChanged,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Left: brand
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VivicastBrandMark()
            Text(text = brand, style = VivicastTypography.TitleMedium)
        }

        // Center: main nav items
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                if (label !in iconOnlyLabels) NavItem(index, label)
            }
        }

        // Right: icon-only items
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                if (label in iconOnlyLabels) NavItem(index, label)
            }
        }
    }
}

fun topNavItemTag(label: String): String = "top-nav-item-$label"
fun playerTimelineTag(): String = "player-timeline"

private fun topNavItemWidth(label: String, iconOnlyLabels: Set<String>): Dp = when {
    label in iconOnlyLabels -> 44.dp
    label == "Home" -> 96.dp
    label == "Live-TV" -> 108.dp
    else -> VivicastCardSizes.TopTabMinWidth
}

@Composable
fun VivicastTopNavItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    minWidth: Dp = VivicastCardSizes.TopTabMinWidth,
    onSelected: () -> Unit,
    onFocused: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val isActive = selected || focused
    val strSearch = stringResource(R.string.nav_search_label)
    val strSettings = stringResource(R.string.nav_settings_label)
    val iconOnly = label == strSearch || label == strSettings
    TvSurface(
        onClick = onSelected,
        modifier = modifier
            .then(if (iconOnly) Modifier.width(minWidth) else Modifier.widthIn(min = minWidth))
            .height(VivicastCardSizes.TopTabsHeight)
            .onFocusChanged {
                val now = it.isFocused || it.hasFocus
                focused = now
                onFocusChanged(now)
                if (now) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(
            shape = VivicastShapes.CardRadius,
            focusedShape = VivicastShapes.CardRadius,
            pressedShape = VivicastShapes.CardRadius,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF0D273F) else Color.Transparent,
            contentColor = if (selected) Color.White else VivicastColors.TextSecondary,
            focusedContainerColor = Color(0xFF0E2A43),
            focusedContentColor = Color.White,
            pressedContainerColor = VivicastColors.SurfacePressed,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    VivicastBorders.FocusWidth,
                    if (selected) VivicastColors.AccentSoft else Color.Transparent,
                ),
                shape = VivicastShapes.CardRadius,
            ),
            focusedBorder = Border(
                border = BorderStroke(VivicastBorders.FocusWidth, VivicastColors.FocusRing),
                inset = 0.dp,
                shape = VivicastShapes.CardRadius,
            ),
            pressedBorder = Border(
                border = BorderStroke(VivicastBorders.FocusWidth, VivicastColors.FocusRing),
                shape = VivicastShapes.CardRadius,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = VivicastFocusDefaults.ScaleSmall),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevation = VivicastFocusDefaults.GlowElevation, elevationColor = VivicastColors.FocusGlow),
        ),
    ) {
        Box(
            modifier = Modifier
                .then(if (iconOnly) Modifier.fillMaxSize() else Modifier.defaultMinSize(minWidth = minWidth).fillMaxHeight())
                .padding(horizontal = if (iconOnly) VivicastSpacing.Space2 else VivicastSpacing.Space3),
            contentAlignment = Alignment.Center,
        ) {
            if (iconOnly) {
                TopNavIcon(label = label, selected = isActive)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    TopNavIcon(label = label, selected = isActive)
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = VivicastTypography.LabelSmall.copy(
                            color = if (isActive) Color.White else VivicastColors.TextSecondary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun VivicastBrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        val outer = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.08f)
            lineTo(size.width * 0.86f, size.height * 0.50f)
            lineTo(size.width * 0.16f, size.height * 0.92f)
            close()
        }
        val inner = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.28f)
            lineTo(size.width * 0.62f, size.height * 0.50f)
            lineTo(size.width * 0.30f, size.height * 0.72f)
            close()
        }
        drawPath(outer, Brush.linearGradient(listOf(Color(0xFF00C8FF), Color(0xFF2563EB))))
        drawPath(inner, Color(0xAA050914))
    }
}

@Composable
private fun TopNavIcon(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) VivicastColors.AccentSoft else VivicastColors.TextSecondary
    val strSearch = stringResource(R.string.nav_search_label)
    val strSettings = stringResource(R.string.nav_settings_label)
    val strMovies = stringResource(R.string.nav_movies_label)
    val strSeries = stringResource(R.string.nav_series_label)
    val iconSize = if (label == strSearch || label == strSettings) 18.dp else 15.dp
    Canvas(modifier = modifier.size(iconSize)) {
        val sw = 1.8f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (label) {
            "Home" -> {
                // Classic house: roof + body + door
                val roof = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.90f, h * 0.50f)
                    lineTo(w * 0.10f, h * 0.50f)
                    close()
                }
                drawPath(roof, color, style = stroke)
                val body = Path().apply {
                    moveTo(w * 0.22f, h * 0.50f)
                    lineTo(w * 0.22f, h * 0.90f)
                    lineTo(w * 0.78f, h * 0.90f)
                    lineTo(w * 0.78f, h * 0.50f)
                }
                drawPath(body, color, style = stroke)
                drawRect(color, topLeft = Offset(w * 0.40f, h * 0.65f), size = Size(w * 0.20f, h * 0.25f), style = stroke)
            }
            "Live-TV" -> {
                // Clean monitor screen + base + stand — no antenna
                drawRoundRect(color, topLeft = Offset(w * 0.08f, h * 0.16f), size = Size(w * 0.84f, h * 0.58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = stroke)
                drawLine(color, Offset(w * 0.36f, h * 0.74f), Offset(w * 0.64f, h * 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.74f), Offset(w * 0.50f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.30f, h * 0.86f), Offset(w * 0.70f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            strMovies -> {
                // Clapperboard: rectangle body + striped top bar
                drawRect(color, topLeft = Offset(w * 0.10f, h * 0.34f), size = Size(w * 0.80f, h * 0.54f), style = stroke)
                drawRect(color, topLeft = Offset(w * 0.10f, h * 0.18f), size = Size(w * 0.80f, h * 0.18f), style = stroke)
                // Diagonal lines in top bar (clapperboard stripes)
                for (i in 0..3) {
                    val x = w * (0.20f + i * 0.18f)
                    drawLine(color, Offset(x, h * 0.18f), Offset(x - w * 0.10f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                }
                // Play button in body
                val tri = Path().apply {
                    moveTo(w * 0.38f, h * 0.46f); lineTo(w * 0.38f, h * 0.76f); lineTo(w * 0.68f, h * 0.61f); close()
                }
                drawPath(tri, color, style = stroke)
            }
            strSeries -> {
                // Two stacked screens
                drawRoundRect(color, topLeft = Offset(w * 0.18f, h * 0.10f), size = Size(w * 0.64f, h * 0.42f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = stroke)
                drawRoundRect(color, topLeft = Offset(w * 0.06f, h * 0.44f), size = Size(w * 0.72f, h * 0.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = stroke)
            }
            strSearch -> {
                // Bold magnifier
                drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.40f, h * 0.40f), style = Stroke(width = sw + 0.6f, cap = StrokeCap.Round))
                drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.86f, h * 0.86f), strokeWidth = sw + 0.6f, cap = StrokeCap.Round)
            }
            else -> {
                // Classic gear: thick ring + 6 square teeth + center hole
                val cx = w * 0.50f; val cy = h * 0.50f
                drawCircle(color, radius = w * 0.30f, center = Offset(cx, cy),
                    style = Stroke(width = sw + 1.2f, cap = StrokeCap.Round))
                drawCircle(color, radius = w * 0.12f, center = Offset(cx, cy), style = stroke)
                repeat(6) { i ->
                    val angle = (Math.PI * 2.0 * i / 6.0).toFloat()
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    drawLine(
                        color,
                        Offset(cx + w * 0.30f * cos, cy + h * 0.30f * sin),
                        Offset(cx + w * 0.46f * cos, cy + h * 0.46f * sin),
                        strokeWidth = w * 0.16f,
                        cap = StrokeCap.Square,
                    )
                }
            }
        }
    }
}

@Composable
fun VivicastChannelCard(
    channelName: String,
    program: String,
    logoText: String,
    logoMissing: Boolean,
    selected: Boolean,
    progressPercent: Int,
    favorite: Boolean,
    catchUp: Boolean,
    logoResId: Int? = null,
    logoModel: Any? = null,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    VivicastFocusSurface(
        selected = selected,
        onFocused = onFocused,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(VivicastCardSizes.ChannelItemHeight),
        contentPadding = VivicastSpacing.Space3,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MiniLogo(logoText, logoMissing, imageResId = logoResId, imageModel = logoModel)
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.weight(1f)) {
                Text(
                    text = channelName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelLarge,
                )
                BodyText(program, maxLines = 1)
                ProgressLine(progressPercent)
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                    StatusBadge("Live", tone = Color(0xFF6D1D1D))
                    if (favorite) StatusBadge("Favorit", tone = Color(0xFF72520C))
                    if (catchUp) StatusBadge("Catch-Up", tone = Color(0xFF4D3A78))
                }
            }
        }
    }
}

@Composable
fun VivicastPlayerOverlay(
    title: String,
    subtitle: String,
    statusLabel: String,
    badges: List<String>,
    progress: Int,
    seekable: Boolean,
    focusedTimeline: Boolean,
    timelineFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onTimelineFocusChanged: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekLeft: () -> Unit,
    onSeekRight: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VivicastCardSizes.PlayerOverlayHeight)
            .clip(VivicastShapes.PanelRadius)
            .background(Color(0xE60A111D))
            .border(VivicastBorders.Hairline, Color(0x8838BDF8), VivicastShapes.PanelRadius)
            .padding(horizontal = VivicastSpacing.Space6, vertical = VivicastSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = VivicastTypography.TitleLarge)
                BodyText(subtitle, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                badges.forEach { VivicastStreamInfoBadge(it) }
                VivicastStatusBadge(statusLabel)
            }
        }

        VivicastPlayerTimeline(
            progress = progress,
            focused = focusedTimeline,
            seekable = seekable,
            focusRequester = timelineFocusRequester,
            onFocusChanged = onTimelineFocusChanged,
            onTogglePlay = onTogglePlay,
            onSeekLeft = onSeekLeft,
            onSeekRight = onSeekRight,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), content = actions)
        footer()
    }
}

@Composable
fun VivicastPlayerTimeline(
    progress: Int,
    focused: Boolean,
    seekable: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onTogglePlay: () -> Unit,
    onSeekLeft: () -> Unit,
    onSeekRight: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            BodyText("00:${progress.toString().padStart(2, '0')}", maxLines = 1)
            BodyText(if (seekable) "01:40" else "LIVE", maxLines = 1)
        }
        VivicastFocusSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(VivicastCardSizes.PlayerTimelineHeight)
                .testTag(playerTimelineTag())
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            onTogglePlay()
                            true
                        }
                        Key.DirectionLeft -> {
                            onSeekLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekRight()
                            true
                        }
                        else -> false
                    }
                },
            onClick = null,
            onFocusChanged = onFocusChanged,
            contentPadding = VivicastSpacing.Space3,
            shape = VivicastShapes.PillRadius,
            focusScale = VivicastFocusDefaults.ScaleSmall,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (focused) 14.dp else 10.dp)
                        .clip(VivicastShapes.PillRadius)
                        .background(Color(0xFF2A3548)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                        .height(if (focused) 14.dp else 10.dp)
                        .clip(VivicastShapes.PillRadius)
                        .background(VivicastColors.Progress),
                )
            }
        }
    }
}

@Composable
fun MiniLogo(
    text: String,
    missing: Boolean,
    modifier: Modifier = Modifier,
    imageResId: Int? = null,
    imageModel: Any? = null,
) {
    Box(
        modifier = modifier
            .width(VivicastCardSizes.ChannelLogoWidth)
            .height(VivicastCardSizes.ChannelLogoHeight)
            .clip(VivicastShapes.RadiusMediumShape)
            .background(
                if (missing) {
                    Brush.verticalGradient(listOf(Color(0xFF2A303A), Color(0xFF111827)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF0B66A5), Color(0xFF123A6A)))
                },
            )
            .border(VivicastBorders.Hairline, Color(0x554FC3F7), VivicastShapes.RadiusMediumShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(VivicastSpacing.Space2),
            )
        } else if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(VivicastSpacing.Space2),
            )
        } else {
            Text(
                text = if (missing) "?" else text.take(2).uppercase(),
                style = VivicastTypography.LabelMedium.copy(color = Color.White),
            )
        }
    }
}

private val VivicastShapes.RadiusMediumShape: RoundedCornerShape
    get() = RoundedCornerShape(RadiusMedium)

private fun focusSurfaceBrush(focused: Boolean, selected: Boolean, enabled: Boolean, showIdleSurface: Boolean): Brush {
    return when {
        !enabled -> Brush.verticalGradient(listOf(VivicastColors.SurfaceDisabled, VivicastColors.SurfaceDisabled))
        focused -> Brush.verticalGradient(listOf(Color(0xFF255077), Color(0xFF0E2A43), Color(0xFF081523)))
        selected -> Brush.verticalGradient(listOf(Color(0xFF173F64), Color(0xFF0D273F), Color(0xFF081522)))
        !showIdleSurface -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        else -> Brush.verticalGradient(listOf(Color(0xEF152238), Color(0xE80B1423), Color(0xDE08111D)))
    }
}

private fun initialsFor(text: String): String =
    text.split(' ', ':', '-', '.', '/', '|')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "VC" }
