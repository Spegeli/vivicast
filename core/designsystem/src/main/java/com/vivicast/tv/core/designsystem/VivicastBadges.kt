package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

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
