package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

@Composable
fun VivicastTheme(
    scheme: VivicastColorScheme = VivicastColorScheme.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalVivicastColors provides scheme) {
        TvMaterialTheme(
            colorScheme = darkColorScheme(
                primary = scheme.accent,
                onPrimary = scheme.onAccent,
                secondary = scheme.accentSoft,
                background = scheme.surface(VivicastColors.Background),
                onBackground = VivicastColors.TextPrimary,
                surface = scheme.surface(VivicastColors.Surface),
                onSurface = VivicastColors.TextPrimary,
                surfaceVariant = scheme.surface(VivicastColors.SurfaceHigh),
                onSurfaceVariant = VivicastColors.TextSecondary,
                border = scheme.focusRing,
                error = VivicastColors.Error,
                onError = Color.White,
            ),
            content = content,
        )
    }
}

object VivicastColors {
    val Background = Color(0xFF050914)
    val BackgroundElevated = Color(0xFF07111F)
    val Surface = Color(0xFF0B1628)
    val SurfaceHigh = Color(0xFF101D33)
    val SurfaceFocus = Color(0xFF102B46)
    val SurfacePressed = Color(0xFF15395C)
    val SurfaceSelected = Color(0xFF102B46)
    val SurfaceDisabled = Color(0x80111827)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextTertiary = Color(0xFF94A3B8)
    val TextDisabled = Color(0xFF64748B)
    val TextOnAccent = Color(0xFF031525)

    val Accent = Color(0xFF00C8FF)
    val AccentSoft = Color(0xFF38D5FF)
    val FocusRing = Color(0xFF00D4FF)
    val FocusGlow = Color(0x6600BFFF)
    val Focus = FocusRing

    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Accent
    val Live = Color(0xFFFF2D3A)
    val Favorite = Color(0xFFFACC15)
    val CatchUp = Color(0xFF0EA5E9)
    val Progress = Color(0xFF159BFF)
}

object VivicastTypography {
    val DisplayLarge = TextStyle(color = VivicastColors.TextPrimary, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
    val DisplayMedium = TextStyle(color = VivicastColors.TextPrimary, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    val TitleLarge = TextStyle(color = VivicastColors.TextPrimary, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
    val TitleMedium = TextStyle(color = VivicastColors.TextPrimary, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val TitleSmall = TextStyle(color = VivicastColors.TextPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val BodyLarge = TextStyle(color = VivicastColors.TextPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)
    val BodyMedium = TextStyle(color = VivicastColors.TextSecondary, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
    val BodySmall = TextStyle(color = VivicastColors.TextTertiary, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val LabelLarge = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val LabelMedium = TextStyle(color = VivicastColors.TextPrimary, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val LabelSmall = TextStyle(color = VivicastColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

    val ScreenTitle = DisplayMedium
    val Body = BodyMedium
}

object VivicastSpacing {
    val Space0 = 0.dp
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 24.dp
    val Space6 = 32.dp
    val Space7 = 40.dp
    val Space8 = 48.dp
    val Space9 = 64.dp
    val Space10 = 80.dp

    val ScreenHorizontal = Space8       // 48dp — TV safe area
    val ScreenVertical = Space6         // 32dp
    val PanelPadding = Space5           // 24dp
    val CardPadding = Space4            // 16dp
    val ButtonHorizontal = Space5       // 24dp
    val ButtonVertical = Space3         // 12dp
    val ContentGap = Space4             // 16dp
    val RowGap = Space2                 // 8dp
    val SectionGap = Space6             // 32dp
    val ColumnGap = Space5              // 24dp
    val GridGap = Space5                // 24dp
    val SettingsRowHorizontal = Space5  // 24dp
    val SettingsRowVertical = Space4    // 16dp
    val PlayerHorizontalSafe = Space8   // 48dp
    val PlayerVerticalSafe = Space7     // 40dp
}

object VivicastShapes {
    val RadiusNone = 0.dp
    val RadiusSmall = 8.dp
    val RadiusMedium = 12.dp
    val RadiusLarge = 16.dp
    val RadiusXLarge = 24.dp
    val RadiusPill = 999.dp

    val FocusRadius = RoundedCornerShape(RadiusLarge)
    val CardRadius = RoundedCornerShape(RadiusLarge)
    val PosterRadius = RoundedCornerShape(RadiusMedium)
    val PanelRadius = RoundedCornerShape(RadiusXLarge)
    val PillRadius = RoundedCornerShape(RadiusPill)

    // Semantic aliases (matches compose-template)
    val Small = RoundedCornerShape(RadiusSmall)
    val Medium = RoundedCornerShape(RadiusMedium)
    val Large = RoundedCornerShape(RadiusLarge)
    val XLarge = RoundedCornerShape(RadiusXLarge)
    val Pill = PillRadius
    val Button = Medium
    val Dialog = XLarge
    val SettingsRow = Large
    val TopNavItem = Large
}

object VivicastBorders {
    val Hairline: Dp = 1.dp
    val FocusWidth: Dp = 2.dp
    val FocusInset: Dp = 0.dp
    val PanelWidth: Dp = 1.dp
}

object VivicastFocusDefaults {
    val ScaleSmall = 1.02f
    val ScaleButton = 1.03f
    val ScaleMedium = 1.06f
    val ScaleLarge = 1.08f
    val RingWidth: Dp = VivicastBorders.FocusWidth
    val RingGap: Dp = 3.dp
    val GlowElevation: Dp = 18.dp
    val GlowAlpha = 0.40f

    val BorderWidth: Dp = RingWidth
    val BorderColor = VivicastColors.FocusRing
}

object VivicastAlpha {
    const val Panel = 0.92f
    const val PanelStrong = 0.96f
    const val Overlay = 0.88f
    const val ScrimHeavy = 0.86f
    const val ScrimMedium = 0.62f
}

object VivicastCardSizes {
    val TopTabsHeight = 34.dp
    val TopTabMinWidth = 72.dp
    val ChannelItemHeight = 80.dp
    val ChannelLogoWidth = 64.dp
    val ChannelLogoHeight = 42.dp
    val ActionPillHeight = 36.dp
    val PosterWidth = 124.dp
    val PosterImageHeight = 112.dp
    const val PosterAspectRatio = 2f / 3f
    val SearchPosterWidth = 164.dp
    val SearchPosterHeight = 150.dp
    val SearchWideWidth = 320.dp
    val SearchWideHeight = 88.dp
    val HeroHeight = 190.dp
    val PlayerOverlayHeight = 246.dp
    val PlayerTimelineHeight = 50.dp
    val SettingsNavItemHeight = 48.dp
    val SettingsRowHeight = 58.dp
    // Uniform single-line settings-row height (toggle + text + stepper rows all match this).
    val CompactSettingsRowHeight = 43.dp
}

object VivicastMotion {
    const val InstantMillis = 0
    const val FastMillis = 120
    const val NormalMillis = 180
    const val SlowMillis = 260
}

val LocalVivicastColors = staticCompositionLocalOf { VivicastColorScheme.Default }

/**
 * Panel/overlay surface opacity multiplier from the "Transparenz" setting (App provides it at the
 * composition root: 0%→1.0, 25%→0.75, 50%→0.5). Panel surfaces multiply their baked fill alpha by
 * this; content stays opaque. Default 1.0 = fully as designed. Dialogs re-provide 1.0 to stay opaque.
 */
val LocalSurfaceOpacity = staticCompositionLocalOf { 1f }

/** Scales a color's alpha by [factor] (clamped) — used to fade panel fill by [LocalSurfaceOpacity]. */
fun Color.scaledAlpha(factor: Float): Color = copy(alpha = alpha * factor.coerceIn(0f, 1f))

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
            .padding(VivicastSpacing.Space6),
    ) {
        Column {
            Text(text = title, style = VivicastTypography.ScreenTitle)
            Text(
                text = description,
                style = VivicastTypography.Body,
                modifier = Modifier.padding(top = VivicastSpacing.ContentGap),
            )
        }
    }
}
