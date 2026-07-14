package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

@Composable
fun InfoPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    bodyMaxLines: Int = 3,
) {
    VivicastDetailsPanel(title = title, body = body, modifier = modifier, badge = badge, bodyMaxLines = bodyMaxLines)
}

@Composable
fun VivicastDetailsPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    bodyMaxLines: Int = 3,
) {
    val opacity = LocalSurfaceOpacity.current
    val scheme = LocalVivicastColors.current
    Column(
        modifier = modifier
            .clip(VivicastShapes.CardRadius)
            // Material-3 tonal surface: solid tinted fill (elevation by tone step), not a gradient.
            .background(scheme.surface(Color(0xE6162335)).scaledAlpha(opacity))
            .border(VivicastBorders.Hairline, scheme.surface(Color(0xFF263C55)), VivicastShapes.CardRadius)
            .padding(VivicastSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.TitleSmall,
            )
            if (badge != null) {
                Spacer(modifier = Modifier.width(VivicastSpacing.Space3))
                StatusBadge(badge)
            }
        }
        BodyText(body, maxLines = bodyMaxLines)
    }
}

@Composable
fun HeroPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    backdropResId: Int? = null,
    backdropModel: Any? = null,
    action: (@Composable () -> Unit)? = null,
) {
    VivicastHeroPanel(
        title = title,
        body = body,
        modifier = modifier,
        meta = meta,
        backdropResId = backdropResId,
        backdropModel = backdropModel,
        action = action,
    )
}

@Composable
fun VivicastHeroPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    backdropResId: Int? = null,
    backdropModel: Any? = null,
    action: (@Composable () -> Unit)? = null,
) {
    VivicastGlassPanel(modifier = modifier, contentPadding = VivicastSpacing.Space2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VivicastCardSizes.HeroHeight)
                .clip(VivicastShapes.PanelRadius)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF101B2B), Color(0xFF173552), Color(0xFF2A241C), Color(0xFF100C0A)),
                    ),
                )
                .border(VivicastBorders.Hairline, Color(0x554FC3F7), VivicastShapes.PanelRadius),
        ) {
            if (backdropModel != null || backdropResId != null) {
                if (backdropModel != null) {
                    AsyncImage(
                        model = backdropModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else if (backdropResId != null) {
                    Image(
                        painter = painterResource(backdropResId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF5070A12), Color(0xD20B1626), Color(0x772A170C), Color(0xF0070A12)),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xC9000000)))),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
            modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 860.dp)
                    .padding(horizontal = VivicastSpacing.Space5, vertical = VivicastSpacing.Space4),
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.DisplayMedium,
                )
                if (meta != null) {
                    BodyText(meta, color = VivicastColors.TextSecondary, maxLines = 1)
                }
                Text(
                    text = body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.BodyMedium,
                )
                if (action != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                        action()
                    }
                }
            }
        }
    }
}
