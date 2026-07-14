package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text

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
    val scheme = LocalVivicastColors.current
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surface(Color(0xFF02040A)),
                        scheme.surface(VivicastColors.Background),
                        scheme.surface(Color(0xFF081525)),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.surface(Color(0x00111827)),
                            scheme.surface(Color(0x55101C30)),
                            scheme.surface(Color(0x00111827)),
                        ),
                    ),
                ),
        )
        content()
    }
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
    val scheme = LocalVivicastColors.current
    val border = Border(
        border = BorderStroke(
            VivicastBorders.Hairline,
            when {
                selected -> scheme.accentSoft
                showIdleSurface -> scheme.surface(Color(0xFF26384F))
                else -> Color.Transparent
            },
        ),
        shape = shape,
    )
    val focusedBorder = Border(
        border = BorderStroke(VivicastFocusDefaults.RingWidth, scheme.focusRing),
        inset = 0.dp,
        shape = shape,
    )
    val glow = Glow(
        elevation = VivicastFocusDefaults.GlowElevation,
        elevationColor = scheme.focusGlow,
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
            containerColor = if (showIdleSurface) scheme.surface(VivicastColors.Surface) else Color.Transparent,
            contentColor = VivicastColors.TextPrimary,
            focusedContainerColor = scheme.surface(VivicastColors.SurfaceFocus),
            focusedContentColor = Color.White,
            pressedContainerColor = scheme.surface(VivicastColors.SurfacePressed),
            pressedContentColor = Color.White,
            disabledContainerColor = scheme.surface(VivicastColors.SurfaceDisabled),
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
                .background(focusSurfaceBrush(scheme = scheme, focused = focused, selected = selected, enabled = enabled, showIdleSurface = showIdleSurface))
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
    // Fade only the fill by the user transparency; border + content stay opaque for readability.
    val opacity = LocalSurfaceOpacity.current
    val scheme = LocalVivicastColors.current
    Box(
        modifier = modifier
            .clip(VivicastShapes.PanelRadius)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surface(Color(0xF0152438)).scaledAlpha(opacity),
                        scheme.surface(Color(0xEA0B1626)).scaledAlpha(opacity),
                        scheme.surface(Color(0xDC07101C)).scaledAlpha(opacity),
                    ),
                ),
            )
            .border(VivicastBorders.PanelWidth, scheme.surface(Color(0xAA263D56)), VivicastShapes.PanelRadius)
            .padding(contentPadding),
    ) {
        content()
    }
}

private fun focusSurfaceBrush(
    scheme: VivicastColorScheme,
    focused: Boolean,
    selected: Boolean,
    enabled: Boolean,
    showIdleSurface: Boolean,
): Brush {
    fun tint(stops: List<Color>) = Brush.verticalGradient(stops.map(scheme::surface))
    return when {
        !enabled -> tint(listOf(VivicastColors.SurfaceDisabled, VivicastColors.SurfaceDisabled))
        focused -> tint(listOf(Color(0xFF255077), Color(0xFF0E2A43), Color(0xFF081523)))
        selected -> tint(listOf(Color(0xFF173F64), Color(0xFF0D273F), Color(0xFF081522)))
        !showIdleSurface -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        else -> tint(listOf(Color(0xEF152238), Color(0xE80B1423), Color(0xDE08111D)))
    }
}
