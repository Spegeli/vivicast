package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VivicastScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1117), VivicastColors.Background, Color(0xFF121A22)),
                ),
            )
            .padding(24.dp),
    ) {
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = VivicastColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = VivicastColors.TextSecondary) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = 16.sp),
    )
}

@Composable
fun FocusPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        focused -> VivicastColors.Focus
        selected -> Color(0xFF416578)
        else -> Color(0xFF23313D)
    }
    Box(
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .clip(VivicastShapes.FocusRadius)
            .background(if (selected) VivicastColors.SurfaceSelected else VivicastColors.Surface)
            .border(if (focused) 3.dp else 1.dp, borderColor, VivicastShapes.FocusRadius)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier.focusable(interactionSource = interactionSource)
                },
            )
            .padding(contentPadding),
    ) {
        content(focused)
    }
}

@Composable
fun ActionPill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    FocusPanel(modifier = modifier, selected = selected, onClick = onClick, contentPadding = 12.dp) { focused ->
        BasicText(
            text = label,
            style = TextStyle(
                color = if (focused || selected) Color.White else VivicastColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
fun StatusBadge(label: String, modifier: Modifier = Modifier, tone: Color = Color(0xFF1D4F63)) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
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
    onClick: () -> Unit = {},
) {
    Column(modifier = modifier.width(172.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            onClick = onClick,
            contentPadding = 12.dp,
        ) { focused ->
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge("Rating $rating", tone = Color(0xFF224B5F))
                    if (favorite) StatusBadge("Fav", tone = Color(0xFF5B3F15))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (hasPoster) Color(0xFF253643) else Color(0xFF1A222A)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = if (hasPoster) "Poster" else title,
                        style = TextStyle(
                            color = if (focused) Color.White else VivicastColors.TextSecondary,
                            fontSize = if (hasPoster) 18.sp else 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                if (progressPercent > 0) {
                    ProgressLine(progressPercent)
                } else if (seen) {
                    StatusBadge("Gesehen", tone = Color(0xFF2B5D45))
                } else {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        BasicText(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        BodyText(text = meta)
    }
}

@Composable
fun ProgressLine(progressPercent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF24313B)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((progressPercent.coerceIn(0, 100) / 100f))
                .height(6.dp)
                .background(VivicastColors.Focus),
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
    Column(
        modifier = modifier
            .clip(VivicastShapes.FocusRadius)
            .background(Color(0xCC151C23))
            .border(1.dp, Color(0xFF263847), VivicastShapes.FocusRadius)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            SectionTitle(title)
            if (badge != null) StatusBadge(badge)
        }
        BodyText(body)
    }
}

@Composable
fun MiniLogo(text: String, missing: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (missing) Color(0xFF2A2523) else Color(0xFF1E4B5E)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = if (missing) "?" else text.take(2).uppercase(),
            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}
