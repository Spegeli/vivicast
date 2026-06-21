package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VivicastTheme(content: @Composable () -> Unit) {
    content()
}

object VivicastColors {
    val Background = Color(0xFF070A0F)
    val BackgroundElevated = Color(0xFF0B1220)
    val Surface = Color(0xFF111827)
    val SurfaceHigh = Color(0xFF172033)
    val SurfaceFocus = Color(0xFF1E3A5F)
    val SurfaceSelected = Color(0xFF142B44)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextTertiary = Color(0xFF94A3B8)
    val Focus = Color(0xFF7DD3FC)
    val Accent = Color(0xFF38BDF8)
    val AccentSoft = Color(0xFF0EA5E9)
    val Error = Color(0xFFEF4444)
    val Warning = Color(0xFFF59E0B)
    val Success = Color(0xFF22C55E)
}

object VivicastTypography {
    val ScreenTitle = TextStyle(
        color = VivicastColors.TextPrimary,
        fontSize = 32.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val Body = TextStyle(
        color = VivicastColors.TextSecondary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
    )
}

object VivicastSpacing {
    val ScreenHorizontal = 56.dp
    val ScreenVertical = 40.dp
    val ContentGap = 24.dp
}

object VivicastShapes {
    val FocusRadius = RoundedCornerShape(16.dp)
    val PanelRadius = RoundedCornerShape(24.dp)
    val PillRadius = RoundedCornerShape(999.dp)
}

object VivicastFocusDefaults {
    val BorderWidth: Dp = 2.dp
    val BorderColor = VivicastColors.Focus
}

val LocalVivicastColors = staticCompositionLocalOf { VivicastColors }

@Composable
fun VivicastPlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VivicastColors.Background)
            .border(
                width = VivicastFocusDefaults.BorderWidth,
                color = Color(0xFF26313A),
                shape = VivicastShapes.FocusRadius,
            )
            .padding(32.dp),
    ) {
        Column {
            BasicText(text = title, style = VivicastTypography.ScreenTitle)
            BasicText(
                text = description,
                style = VivicastTypography.Body,
                modifier = Modifier.padding(top = VivicastSpacing.ContentGap),
            )
        }
    }
}
