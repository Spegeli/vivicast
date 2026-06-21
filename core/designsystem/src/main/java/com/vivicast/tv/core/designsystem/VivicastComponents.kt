package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
        modifier = modifier.padding(horizontal = VivicastSpacing.Space2, vertical = VivicastSpacing.Space1),
        content = content,
    )
}

@Composable
fun VivicastScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF02040A),
                        VivicastColors.Background,
                        Color(0xFF081525),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x00111827), Color(0x55101C30), Color(0x00111827)),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = VivicastTypography.TitleLarge,
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
fun VivicastFocusSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    contentPadding: Dp = VivicastSpacing.CardPadding,
    shape: RoundedCornerShape = VivicastShapes.CardRadius,
    focusScale: Float = VivicastFocusDefaults.ScaleMedium,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border = Border(
        border = BorderStroke(VivicastBorders.Hairline, if (selected) VivicastColors.AccentSoft else Color(0xFF26384F)),
        shape = shape,
    )
    val focusedBorder = Border(
        border = BorderStroke(VivicastFocusDefaults.RingWidth, VivicastColors.FocusRing),
        inset = VivicastFocusDefaults.RingGap,
        shape = shape,
    )
    val glow = Glow(
        elevation = VivicastFocusDefaults.GlowElevation,
        elevationColor = VivicastColors.FocusGlow,
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
                .background(focusSurfaceBrush(focused = focused, selected = selected, enabled = enabled))
                .border(
                    width = if (focused) VivicastBorders.FocusWidth else VivicastBorders.Hairline,
                    color = when {
                        focused -> VivicastColors.FocusRing
                        selected -> VivicastColors.AccentSoft
                        else -> Color(0x332A405A)
                    },
                    shape = shape,
                )
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
fun ActionPill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    VivicastFocusSurface(
        modifier = modifier.widthIn(min = 96.dp).height(VivicastCardSizes.ActionPillHeight),
        selected = selected,
        onClick = onClick,
        contentPadding = VivicastSpacing.Space3,
        shape = VivicastShapes.PillRadius,
        focusScale = VivicastFocusDefaults.ScaleSmall,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VivicastSpacing.Space3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(
                    color = if (focused || selected) Color.White else VivicastColors.TextSecondary,
                ),
            )
        }
    }
}

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
    Box(
        modifier = modifier
            .clip(VivicastShapes.PanelRadius)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF0152438),
                        Color(0xEA0B1626),
                        Color(0xDC07101C),
                    ),
                ),
            )
            .border(VivicastBorders.PanelWidth, Color(0xAA263D56), VivicastShapes.PanelRadius)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun VivicastContentCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = VivicastSpacing.CardPadding,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(VivicastShapes.CardRadius)
            .background(Brush.verticalGradient(listOf(Color(0xEF132034), Color(0xE20A1423))))
            .border(VivicastBorders.Hairline, Color(0xFF273B52), VivicastShapes.CardRadius)
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
    ) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
            content = content,
        )
    }
}

@Composable
fun HeroPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    backdropResId: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    VivicastHeroPanel(
        title = title,
        body = body,
        modifier = modifier,
        meta = meta,
        backdropResId = backdropResId,
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
            if (backdropResId != null) {
                Image(
                    painter = painterResource(backdropResId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
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
                    .widthIn(max = 920.dp)
                    .padding(horizontal = VivicastSpacing.Space6, vertical = VivicastSpacing.Space4),
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
    imageResId: Int? = null,
    onClick: () -> Unit = {},
) {
    VivicastPosterCard(
        title = title,
        rating = rating,
        meta = meta,
        hasPoster = hasPoster,
        progressPercent = progressPercent,
        favorite = favorite,
        seen = seen,
        modifier = modifier,
        imageResId = imageResId,
        onClick = onClick,
    )
}

@Composable
fun VivicastPosterCard(
    title: String,
    rating: String,
    meta: String,
    hasPoster: Boolean,
    progressPercent: Int,
    favorite: Boolean,
    seen: Boolean,
    modifier: Modifier = Modifier,
    imageResId: Int? = null,
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.width(VivicastCardSizes.PosterWidth),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
    ) {
        VivicastFocusSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(VivicastCardSizes.PosterImageHeight),
            onClick = onClick,
            onFocusChanged = { focused = it },
            contentPadding = VivicastSpacing.Space0,
            shape = VivicastShapes.PosterRadius,
            focusScale = VivicastFocusDefaults.ScaleMedium,
        ) { isFocused ->
            PosterArtwork(
                title = title,
                rating = rating,
                hasPoster = hasPoster,
                favorite = favorite,
                seen = seen,
                imageResId = imageResId,
                focused = isFocused,
                progressPercent = progressPercent,
            )
        }
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium.copy(
                color = if (focused) VivicastColors.TextPrimary else VivicastColors.TextSecondary,
            ),
        )
        BodyText(meta, maxLines = 1)
    }
}

@Composable
private fun PosterArtwork(
    title: String,
    rating: String,
    hasPoster: Boolean,
    favorite: Boolean,
    seen: Boolean,
    imageResId: Int?,
    focused: Boolean,
    progressPercent: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (hasPoster) {
                    Brush.verticalGradient(listOf(Color(0xFF405973), Color(0xFF203148), Color(0xFF0D1420)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF212B3B), Color(0xFF111827), Color(0xFF0A101B)))
                },
            ),
    ) {
        if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA8050910)))),
            )
        } else {
            Text(
                text = if (hasPoster) initialsFor(title) else "Kein Poster",
                modifier = Modifier.align(Alignment.Center).padding(horizontal = VivicastSpacing.Space4),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.TitleSmall.copy(
                    color = if (focused) Color.White else VivicastColors.TextSecondary,
                ),
            )
        }
        StatusBadge(rating, modifier = Modifier.align(Alignment.TopStart).padding(VivicastSpacing.Space2), tone = Color(0xD011445C))
        if (favorite || seen) {
            StatusBadge(
                label = if (favorite) "F" else "G",
                modifier = Modifier.align(Alignment.TopEnd).padding(VivicastSpacing.Space2),
                tone = if (favorite) Color(0xFF8A640A) else Color(0xFF1D5A3E),
            )
        }
        if (progressPercent > 0) {
            ProgressLine(
                progressPercent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(VivicastSpacing.Space3),
            )
        }
    }
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

@Composable
fun InfoPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    VivicastDetailsPanel(title = title, body = body, modifier = modifier, badge = badge)
}

@Composable
fun VivicastDetailsPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Column(
        modifier = modifier
            .clip(VivicastShapes.CardRadius)
            .background(Brush.verticalGradient(listOf(Color(0xE6162335), Color(0xD90B1320))))
            .border(VivicastBorders.Hairline, Color(0xFF263C55), VivicastShapes.CardRadius)
            .padding(VivicastSpacing.Space5),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
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
        BodyText(body, maxLines = 3)
    }
}

@Composable
fun VivicastSearchResultCard(
    title: String,
    subtitle: String,
    rating: String? = null,
    posterLike: Boolean = false,
    imageResId: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    VivicastFocusSurface(
        modifier = modifier
            .width(if (posterLike) VivicastCardSizes.SearchPosterWidth else VivicastCardSizes.SearchWideWidth)
            .height(if (posterLike) VivicastCardSizes.SearchPosterHeight else VivicastCardSizes.SearchWideHeight),
        onClick = onClick,
        contentPadding = VivicastSpacing.Space3,
    ) { focused ->
        if (posterLike) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                SearchPosterThumb(title = title, rating = rating, imageResId = imageResId, focused = focused)
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelMedium,
                )
                BodyText(subtitle, maxLines = 1)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelLarge,
                )
                if (rating != null) {
                    StatusBadge("Rating $rating")
                } else {
                    BodyText(subtitle, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SearchPosterThumb(
    title: String,
    rating: String?,
    imageResId: Int?,
    focused: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(VivicastShapes.RadiusMediumShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF304860), Color(0xFF101A2B))))
            .border(VivicastBorders.Hairline, Color(0x334FC3F7), VivicastShapes.RadiusMediumShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99070A12)))),
            )
        } else {
            Text(
                text = initialsFor(title),
                style = VivicastTypography.TitleSmall.copy(color = if (focused) Color.White else VivicastColors.TextSecondary),
            )
        }
        if (rating != null) {
            StatusBadge("Rating $rating", modifier = Modifier.align(Alignment.TopStart).padding(VivicastSpacing.Space1))
        }
    }
}

@Composable
fun VivicastSettingsRow(
    title: String,
    help: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    VivicastFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(VivicastCardSizes.SettingsRowHeight),
        contentPadding = VivicastSpacing.Space4,
    ) { focused ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelLarge,
                )
                BodyText(help, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(VivicastSpacing.Space5))
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(
                    color = if (focused) VivicastColors.FocusRing else VivicastColors.TextPrimary,
                ),
            )
        }
    }
}

@Composable
fun VivicastTopNavigation(
    brand: String,
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
    onFocused: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(VivicastShapes.RadiusMediumShape)
                    .background(Brush.verticalGradient(listOf(VivicastColors.Accent, Color(0xFF2563EB))))
                    .border(VivicastBorders.Hairline, Color(0x6638BDF8), VivicastShapes.RadiusMediumShape),
            )
            Text(text = brand, style = VivicastTypography.TitleLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
            items.forEachIndexed { index, label ->
                VivicastTopNavItem(
                    label = label,
                    selected = index == selectedIndex,
                    minWidth = if (label.length > 8) 150.dp else VivicastCardSizes.TopTabMinWidth,
                    onSelected = { onSelected(index) },
                    onFocused = { onFocused(index) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun VivicastTopNavItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    minWidth: Dp = VivicastCardSizes.TopTabMinWidth,
    onSelected: () -> Unit,
    onFocused: () -> Unit,
) {
    VivicastFocusSurface(
        modifier = modifier.width(minWidth).height(VivicastCardSizes.TopTabsHeight),
        selected = selected,
        onClick = onSelected,
        onFocused = onFocused,
        contentPadding = VivicastSpacing.Space0,
        shape = VivicastShapes.PillRadius,
        focusScale = VivicastFocusDefaults.ScaleSmall,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = VivicastSpacing.Space4),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(
                    color = if (selected) Color.White else VivicastColors.TextSecondary,
                ),
            )
        }
    }
}

@Composable
fun VivicastChannelCard(
    channelName: String,
    program: String,
    logoText: String,
    logoMissing: Boolean,
    selected: Boolean,
    progressPercent: Int,
    favorite: Boolean,
    catchUp: Boolean,
    logoResId: Int? = null,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    VivicastFocusSurface(
        selected = selected,
        onFocused = onFocused,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(VivicastCardSizes.ChannelItemHeight),
        contentPadding = VivicastSpacing.Space4,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MiniLogo(logoText, logoMissing, imageResId = logoResId)
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.weight(1f)) {
                Text(
                    text = channelName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelLarge,
                )
                BodyText(program, maxLines = 1)
                ProgressLine(progressPercent)
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                    StatusBadge("Live", tone = Color(0xFF6D1D1D))
                    if (favorite) StatusBadge("Favorit", tone = Color(0xFF72520C))
                    if (catchUp) StatusBadge("Catch-Up", tone = Color(0xFF4D3A78))
                }
            }
        }
    }
}

@Composable
fun VivicastPlayerOverlay(
    title: String,
    subtitle: String,
    statusLabel: String,
    badges: List<String>,
    progress: Int,
    seekable: Boolean,
    focusedTimeline: Boolean,
    timelineFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onTimelineFocusChanged: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekLeft: () -> Unit,
    onSeekRight: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VivicastCardSizes.PlayerOverlayHeight)
            .clip(VivicastShapes.PanelRadius)
            .background(Color(0xE60A111D))
            .border(VivicastBorders.Hairline, Color(0x8838BDF8), VivicastShapes.PanelRadius)
            .padding(horizontal = VivicastSpacing.Space7, vertical = VivicastSpacing.Space5),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1), modifier = Modifier.weight(1f)) {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = VivicastTypography.TitleLarge)
                BodyText(subtitle, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                badges.forEach { VivicastStreamInfoBadge(it) }
                VivicastStatusBadge(statusLabel)
            }
        }

        VivicastPlayerTimeline(
            progress = progress,
            focused = focusedTimeline,
            seekable = seekable,
            focusRequester = timelineFocusRequester,
            onFocusChanged = onTimelineFocusChanged,
            onTogglePlay = onTogglePlay,
            onSeekLeft = onSeekLeft,
            onSeekRight = onSeekRight,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), content = actions)
        footer()
    }
}

@Composable
fun VivicastPlayerTimeline(
    progress: Int,
    focused: Boolean,
    seekable: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onTogglePlay: () -> Unit,
    onSeekLeft: () -> Unit,
    onSeekRight: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            BodyText("00:${progress.toString().padStart(2, '0')}", maxLines = 1)
            BodyText(if (seekable) "01:40" else "LIVE", maxLines = 1)
        }
        VivicastFocusSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(VivicastCardSizes.PlayerTimelineHeight)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            onTogglePlay()
                            true
                        }
                        Key.DirectionLeft -> {
                            onSeekLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekRight()
                            true
                        }
                        else -> false
                    }
                },
            onClick = onTogglePlay,
            onFocusChanged = onFocusChanged,
            contentPadding = VivicastSpacing.Space3,
            shape = VivicastShapes.PillRadius,
            focusScale = VivicastFocusDefaults.ScaleSmall,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (focused) 14.dp else 10.dp)
                        .clip(VivicastShapes.PillRadius)
                        .background(Color(0xFF2A3548)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                        .height(if (focused) 14.dp else 10.dp)
                        .clip(VivicastShapes.PillRadius)
                        .background(VivicastColors.Progress),
                )
            }
        }
    }
}

@Composable
fun MiniLogo(
    text: String,
    missing: Boolean,
    modifier: Modifier = Modifier,
    imageResId: Int? = null,
) {
    Box(
        modifier = modifier
            .width(VivicastCardSizes.ChannelLogoWidth)
            .height(VivicastCardSizes.ChannelLogoHeight)
            .clip(VivicastShapes.RadiusMediumShape)
            .background(
                if (missing) {
                    Brush.verticalGradient(listOf(Color(0xFF2A303A), Color(0xFF111827)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF0B66A5), Color(0xFF123A6A)))
                },
            )
            .border(VivicastBorders.Hairline, Color(0x554FC3F7), VivicastShapes.RadiusMediumShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(VivicastSpacing.Space2),
            )
        } else {
            Text(
                text = if (missing) "?" else text.take(2).uppercase(),
                style = VivicastTypography.LabelMedium.copy(color = Color.White),
            )
        }
    }
}

private val VivicastShapes.RadiusMediumShape: RoundedCornerShape
    get() = RoundedCornerShape(RadiusMedium)

private fun focusSurfaceBrush(focused: Boolean, selected: Boolean, enabled: Boolean): Brush {
    return when {
        !enabled -> Brush.verticalGradient(listOf(VivicastColors.SurfaceDisabled, VivicastColors.SurfaceDisabled))
        focused -> Brush.verticalGradient(listOf(Color(0xFF255077), Color(0xFF0E2A43), Color(0xFF081523)))
        selected -> Brush.verticalGradient(listOf(Color(0xFF173F64), Color(0xFF0D273F), Color(0xFF081522)))
        else -> Brush.verticalGradient(listOf(Color(0xEF152238), Color(0xE80B1423), Color(0xDE08111D)))
    }
}

private fun initialsFor(text: String): String =
    text.split(' ', ':', '-', '.', '/', '|')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "VC" }
