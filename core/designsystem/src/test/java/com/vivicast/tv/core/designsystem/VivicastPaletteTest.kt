package com.vivicast.tv.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the contrast maths that keeps accent + focus legible on any chosen dark background. */
class VivicastPaletteTest {

    @Test
    fun contrastRatio_blackOnWhite_isMax() {
        // WCAG max contrast is 21:1.
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.1f)
    }

    @Test
    fun onColorFor_picksReadableForeground() {
        assertEquals(Color.White, onColorFor(Color.Black))
        assertEquals(VivicastColors.TextOnAccent, onColorFor(Color.White))
    }

    @Test
    fun ensureVisibleOn_raisesLowContrastUntilVisible() {
        val darkBg = Color(0xFF0B1628)
        // A dark-on-dark pairing must be lifted above the 3:1 focus-visibility floor.
        val lifted = ensureVisibleOn(Color(0xFF102033), darkBg)
        assertTrue(contrastRatio(lifted, darkBg) >= 3f)
    }

    @Test
    fun defaultScheme_isIdentity() {
        // Blue background + Blue accent = no re-hue → identical to the pre-D17 palette.
        val scheme = vivicastColorScheme(VivicastPaletteColor.Blue, VivicastPaletteColor.Blue)
        assertEquals(VivicastColors.Accent, scheme.accent)
        assertEquals(VivicastColors.Surface, scheme.surface(VivicastColors.Surface))
        assertEquals(null, scheme.surfaceHue)
    }

    @Test
    fun nonDefaultBackground_reTintsSurfaces() {
        val scheme = vivicastColorScheme(VivicastPaletteColor.Green, VivicastPaletteColor.Blue)
        // A green background must actually change a navy surface.
        assertTrue(scheme.surface(VivicastColors.Surface) != VivicastColors.Surface)
    }
}
