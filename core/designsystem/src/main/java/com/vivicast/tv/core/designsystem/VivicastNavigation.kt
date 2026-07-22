package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text

@OptIn(ExperimentalComposeUiApi::class)
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
        // Entering the top nav from below (D-pad UP out of the content) must land on the ACTIVE tab, not the
        // geometrically-nearest one (Home, leftmost) — otherwise focus-follows-selection would navigate the route
        // to Home when leaving Live-TV's category column upward.
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selectedFocusRequester != null) {
                    // focusGroup so the enter override actually intercepts: without a group boundary the spatial
                    // search from below targets the nearest tab (Home) directly instead of "entering" this Row.
                    Modifier
                        .focusProperties { enter = { selectedFocusRequester } }
                        .focusGroup()
                } else {
                    Modifier
                },
            ),
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
    val scheme = LocalVivicastColors.current
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
            containerColor = if (selected) scheme.surface(Color(0xFF0D273F)) else Color.Transparent,
            contentColor = if (selected) Color.White else VivicastColors.TextSecondary,
            focusedContainerColor = scheme.surface(Color(0xFF0E2A43)),
            focusedContentColor = Color.White,
            pressedContainerColor = scheme.surface(VivicastColors.SurfacePressed),
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    VivicastBorders.FocusWidth,
                    if (selected) scheme.accentSoft else Color.Transparent,
                ),
                shape = VivicastShapes.CardRadius,
            ),
            focusedBorder = Border(
                border = BorderStroke(VivicastBorders.FocusWidth, scheme.focusRing),
                inset = 0.dp,
                shape = VivicastShapes.CardRadius,
            ),
            pressedBorder = Border(
                border = BorderStroke(VivicastBorders.FocusWidth, scheme.focusRing),
                shape = VivicastShapes.CardRadius,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = VivicastFocusDefaults.ScaleSmall),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevation = VivicastFocusDefaults.GlowElevation, elevationColor = scheme.focusGlow),
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
    val scheme = LocalVivicastColors.current
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
        drawPath(outer, Brush.linearGradient(listOf(scheme.accentize(Color(0xFF00C8FF)), scheme.accentize(Color(0xFF2563EB)))))
        drawPath(inner, scheme.surface(Color(0xAA050914)))
    }
}

@Composable
private fun TopNavIcon(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) LocalVivicastColors.current.accentSoft else VivicastColors.TextSecondary
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
