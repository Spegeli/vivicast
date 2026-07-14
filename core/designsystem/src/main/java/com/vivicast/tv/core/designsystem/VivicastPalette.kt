package com.vivicast.tv.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Dynamic colour scheme for D17 (background + accent). Two independent user choices:
 *  - background hue re-tints the whole surface family (Variante B) via [surface] — each hardcoded navy
 *    fill is re-hued to the chosen hue, keeping its lightness/alpha so gradients keep their shape.
 *  - accent hue drives highlight states (focus/selected/toggle/badge) via the explicit accent fields, and
 *    re-hues the few cyan literal borders + the logo via [accentize].
 *
 * The default (Blue background + Blue accent) uses `null` hue shifts → identity → byte-identical to the
 * pre-D17 look. Static tokens (text, semantic Success/Warning/Error/Live/Favorite, black scrims) never
 * pass through here.
 */
@Immutable
data class VivicastColorScheme(
    val accent: Color,
    val accentSoft: Color,
    val focusRing: Color,
    val focusGlow: Color,
    val onAccent: Color,
    // Surface re-tint (null hue = no shift = default navy). Each navy literal's lightness is remapped into
    // a saturated-but-legible band [floor, floor+span] so warm hues read true (not olive/brown) while white
    // text stays readable; hue + sat are hand-curated per palette colour.
    val surfaceHue: Float?,
    val surfaceSat: Float,
    val surfaceLightFloor: Float,
    val surfaceLightSpan: Float,
    // Accent re-tint for cyan literal borders + logo (null = no shift = default cyan).
    val accentHue: Float?,
    val accentSat: Float,
) {
    /** Re-tint a surface literal to the chosen background hue (identity at default). */
    fun surface(base: Color): Color {
        if (surfaceHue == null) return base
        val l = (surfaceLightFloor + base.toHsl()[2] * surfaceLightSpan).coerceIn(0f, 1f)
        return hslColor(surfaceHue, surfaceSat, l, base.alpha)
    }

    /** Re-hue an accent-family literal (cyan border / logo) to the chosen accent hue (identity at default). */
    fun accentize(base: Color): Color =
        if (accentHue == null) base else base.reHue(accentHue, accentSat, 1f)

    companion object {
        val Default = VivicastColorScheme(
            accent = VivicastColors.Accent,
            accentSoft = VivicastColors.AccentSoft,
            focusRing = VivicastColors.FocusRing,
            focusGlow = VivicastColors.FocusGlow,
            onAccent = VivicastColors.TextOnAccent,
            surfaceHue = null,
            surfaceSat = 0f,
            surfaceLightFloor = 0f,
            surfaceLightSpan = 1f,
            accentHue = null,
            accentSat = 0f,
        )
    }
}

/**
 * Predefined hues. `swatch` is the vivid picker circle AND the applied accent source. Names match the
 * datastore `ThemeColor`/`AccentColor` cases 1:1 (mapped by `.name`). `Blue` reproduces the brand cyan so
 * the default stays identical.
 *
 * ponytail: hex values are a first pass (Material-ish A200/500) — the whole scheme is expected to be
 * fine-tuned after the first on-device pass; the structure is the point, not the exact hexes.
 */
enum class VivicastPaletteColor(val swatch: Color) {
    Red(Color(0xFFFF5252)),
    Pink(Color(0xFFFF4081)),
    Purple(Color(0xFFE040FB)),
    Indigo(Color(0xFF536DFE)),
    Blue(Color(0xFF00C8FF)),
    Cyan(Color(0xFF00E5FF)),
    Teal(Color(0xFF1DE9B6)),
    Green(Color(0xFF00E676)),
    Lime(Color(0xFFC6FF00)),
    Yellow(Color(0xFFFFEA00)),
    Amber(Color(0xFFFFC400)),
    Orange(Color(0xFFFF9100)),
    Brown(Color(0xFFA1887F)),
    Grey(Color(0xFFBDBDBD)),
    BlueGrey(Color(0xFF90A4AE)),
    DarkGrey(Color(0xFF607D8B)),
    Black(Color(0xFF000000)),
}

private class SurfaceParams(val hue: Float?, val sat: Float, val floor: Float, val span: Float)

/**
 * Hand-curated background tint per hue: true hue angle + saturation + a legible lightness band
 * `[floor, floor+span]` that the navy literals are remapped into. "Satt" vividness, but warm hues use a
 * slightly narrower/lower band so white text keeps contrast (dark yellow would otherwise go olive). Greys
 * are near-neutral; Black is near-black AMOLED. Blue = identity (default → unchanged navy look).
 * ponytail: values are tuned by eye — the one knob per hue that a minimal model can't infer.
 */
private fun surfaceParamsFor(bg: VivicastPaletteColor): SurfaceParams = when (bg) {
    VivicastPaletteColor.Blue -> SurfaceParams(null, 0f, 0f, 1f)
    VivicastPaletteColor.Red -> SurfaceParams(2f, 0.72f, 0.12f, 0.60f)
    VivicastPaletteColor.Pink -> SurfaceParams(335f, 0.64f, 0.13f, 0.60f)
    VivicastPaletteColor.Purple -> SurfaceParams(278f, 0.58f, 0.13f, 0.60f)
    VivicastPaletteColor.Indigo -> SurfaceParams(230f, 0.62f, 0.12f, 0.62f)
    VivicastPaletteColor.Cyan -> SurfaceParams(187f, 0.72f, 0.12f, 0.62f)
    VivicastPaletteColor.Teal -> SurfaceParams(166f, 0.70f, 0.12f, 0.60f)
    VivicastPaletteColor.Green -> SurfaceParams(140f, 0.66f, 0.12f, 0.58f)
    VivicastPaletteColor.Lime -> SurfaceParams(80f, 0.78f, 0.13f, 0.60f)
    VivicastPaletteColor.Yellow -> SurfaceParams(54f, 0.92f, 0.17f, 0.78f)
    VivicastPaletteColor.Amber -> SurfaceParams(43f, 0.92f, 0.16f, 0.72f)
    VivicastPaletteColor.Orange -> SurfaceParams(30f, 0.90f, 0.16f, 0.66f)
    VivicastPaletteColor.Brown -> SurfaceParams(22f, 0.45f, 0.11f, 0.52f)
    VivicastPaletteColor.Grey -> SurfaceParams(210f, 0.02f, 0.12f, 0.70f)
    VivicastPaletteColor.BlueGrey -> SurfaceParams(210f, 0.15f, 0.12f, 0.66f)
    VivicastPaletteColor.DarkGrey -> SurfaceParams(210f, 0.05f, 0.07f, 0.55f)
    VivicastPaletteColor.Black -> SurfaceParams(0f, 0f, 0.02f, 0.30f)
}

/** Build a scheme from the two user choices. Blue is the default → identity (no re-tint). */
fun vivicastColorScheme(
    background: VivicastPaletteColor,
    accent: VivicastPaletteColor,
): VivicastColorScheme {
    val sp = surfaceParamsFor(background)
    val bgBase =
        if (sp.hue == null) {
            VivicastColors.Background
        } else {
            val l = (sp.floor + VivicastColors.Background.toHsl()[2] * sp.span).coerceIn(0f, 1f)
            hslColor(sp.hue, sp.sat, l, 1f)
        }
    val accentIsDefault = accent == VivicastPaletteColor.Blue
    val accentHsl = accent.swatch.toHsl()
    return if (accentIsDefault) {
        VivicastColorScheme(
            accent = VivicastColors.Accent,
            accentSoft = VivicastColors.AccentSoft,
            focusRing = VivicastColors.FocusRing,
            focusGlow = VivicastColors.FocusGlow,
            onAccent = VivicastColors.TextOnAccent,
            surfaceHue = sp.hue, surfaceSat = sp.sat, surfaceLightFloor = sp.floor, surfaceLightSpan = sp.span,
            accentHue = null, accentSat = 0f,
        )
    } else {
        // Accent is never near-black (Black is excluded from the accent list), so the visibility guard
        // only ever nudges an already-vivid hue.
        val accentColor = ensureVisibleOn(accent.swatch, bgBase)
        VivicastColorScheme(
            accent = accentColor,
            accentSoft = accentColor.lighten(0.12f),
            focusRing = accentColor,
            focusGlow = accentColor.copy(alpha = 0.40f),
            onAccent = onColorFor(accentColor),
            surfaceHue = sp.hue, surfaceSat = sp.sat, surfaceLightFloor = sp.floor, surfaceLightSpan = sp.span,
            accentHue = accentHsl[0], accentSat = accentHsl[1].coerceIn(0.40f, 0.90f),
        )
    }
}

// ---------------------------------------------------------------------------------------------------
// Colour maths (pure, dependency-free, testable).
// ---------------------------------------------------------------------------------------------------

/** Re-hue: replace hue + saturation, scale lightness, keep alpha. Used to tint navy surfaces per hue. */
fun Color.reHue(hue: Float, sat: Float, lightScale: Float): Color {
    val hsl = toHsl()
    return hslColor(hue, sat, (hsl[2] * lightScale).coerceIn(0f, 1f), alpha)
}

/** Lighten by raising HSL lightness. */
fun Color.lighten(amount: Float): Color {
    val hsl = toHsl()
    return hslColor(hsl[0], hsl[1], (hsl[2] + amount).coerceIn(0f, 1f), alpha)
}

/** WCAG relative luminance. */
fun Color.relativeLuminance(): Float {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** WCAG contrast ratio (>= 1). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** White or near-black, whichever reads better on [bg]. */
fun onColorFor(bg: Color): Color =
    if (contrastRatio(Color.White, bg) >= contrastRatio(VivicastColors.TextOnAccent, bg)) {
        Color.White
    } else {
        VivicastColors.TextOnAccent
    }

/** Raise [fg] lightness until it clears [minRatio] contrast against [bg] (focus/accent visibility guard). */
fun ensureVisibleOn(fg: Color, bg: Color, minRatio: Float = 3f): Color {
    var out = fg
    var guard = 0
    while (contrastRatio(out, bg) < minRatio && guard < 12) {
        val hsl = out.toHsl()
        out = hslColor(hsl[0], hsl[1], (hsl[2] + 0.08f).coerceAtMost(1f), out.alpha)
        guard++
    }
    return out
}

/** RGB -> HSL as [hue(0..360), sat(0..1), lightness(0..1)]. */
private fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val cMax = maxOf(r, g, b)
    val cMin = minOf(r, g, b)
    val delta = cMax - cMin
    val l = (cMax + cMin) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        cMax == r -> 60f * (((g - b) / delta) % 6f)
        cMax == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return floatArrayOf(if (h < 0f) h + 360f else h, s, l)
}

/** HSL -> Color with the given alpha. */
private fun hslColor(hue: Float, sat: Float, lightness: Float, alpha: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * sat
    val hp = ((hue % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}
