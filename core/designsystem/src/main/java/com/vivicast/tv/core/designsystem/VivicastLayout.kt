package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text

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
fun VivicastContentCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = VivicastSpacing.CardPadding,
    content: @Composable () -> Unit,
) {
    val scheme = LocalVivicastColors.current
    Box(
        modifier = modifier
            .clip(VivicastShapes.CardRadius)
            .background(
                Brush.verticalGradient(
                    listOf(scheme.surface(Color(0xEF132034)), scheme.surface(Color(0xE20A1423))),
                ),
            )
            .border(VivicastBorders.Hairline, scheme.surface(Color(0xFF273B52)), VivicastShapes.CardRadius)
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
