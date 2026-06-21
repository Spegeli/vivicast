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
    val Background = Color(0xFF101418)
    val Surface = Color(0xFF171D23)
    val SurfaceSelected = Color(0xFF26313A)
    val TextPrimary = Color(0xFFF2F5F7)
    val TextSecondary = Color(0xFFC7D0D8)
    val Focus = Color(0xFF51D7FF)
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
    val FocusRadius = RoundedCornerShape(8.dp)
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
