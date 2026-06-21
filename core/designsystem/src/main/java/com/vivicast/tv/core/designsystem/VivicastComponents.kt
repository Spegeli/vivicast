package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.sp

@Composable
fun VivicastScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    VivicastScreenBackground(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp), content = content)
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
                    listOf(Color(0xFF02040A), Color(0xFF070A0F), Color(0xFF0A1726)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33256D95), Color.Transparent),
                        radius = 1100f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x22150F0A), Color.Transparent),
                        radius = 900f,
                    ),
                ),
        )
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
            fontSize = 26.sp,
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
        style = TextStyle(color = color, fontSize = 17.sp),
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
        selected -> VivicastColors.AccentSoft
        else -> Color(0xFF243044)
    }
    val background = when {
        focused -> Brush.verticalGradient(listOf(Color(0xFF21486E), Color(0xFF0D273F), Color(0xFF081523)))
        selected -> Brush.verticalGradient(listOf(Color(0xFF143555), Color(0xFF0D2339), Color(0xFF081522)))
        else -> Brush.verticalGradient(listOf(Color(0xFF152238), Color(0xFF0B1423), Color(0xFF08111D)))
    }
    Box(
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .clip(VivicastShapes.FocusRadius)
            .graphicsLayer {
                scaleX = if (focused) 1.045f else 1f
                scaleY = if (focused) 1.045f else 1f
            }
            .shadow(if (focused) 26.dp else 6.dp, VivicastShapes.FocusRadius, clip = false)
            .background(background)
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
fun VivicastFocusedCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
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
    FocusPanel(modifier = modifier, selected = selected, onClick = onClick, contentPadding = 12.dp) { focused ->
        BasicText(
            text = label,
            style = TextStyle(
                color = if (focused || selected) Color.White else VivicastColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
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
            .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
fun VivicastStatusBadge(label: String, modifier: Modifier = Modifier, tone: Color = Color(0xFF1D4F63)) {
    StatusBadge(label = label, modifier = modifier, tone = tone)
}

@Composable
fun VivicastStreamInfoBadge(label: String, modifier: Modifier = Modifier) {
    StatusBadge(label = label, modifier = modifier, tone = Color(0xB5143350))
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(VivicastShapes.PanelRadius)
            .shadow(12.dp, VivicastShapes.PanelRadius, clip = false)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF0132135), Color(0xE90C1727), Color(0xE008101C)),
                ),
            )
            .border(1.dp, Color(0xFF2A405A), VivicastShapes.PanelRadius)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun VivicastGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    GlassPanel(modifier = modifier, contentPadding = contentPadding, content = content)
}

@Composable
fun VivicastContentCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xEB132034), Color(0xDD0A1423))))
            .border(1.dp, Color(0xFF273B52), RoundedCornerShape(18.dp))
            .padding(contentPadding),
    ) {
        content()
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
    VivicastGlassPanel(modifier = modifier, contentPadding = 14.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF101B2B), Color(0xFF19324B), Color(0xFF392719), Color(0xFF110B09)),
                    ),
                )
                .border(1.dp, Color(0x334FC3F7), RoundedCornerShape(20.dp)),
        ) {
            if (backdropResId != null) {
                Image(
                    painter = painterResource(backdropResId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp)),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF20A1422), Color(0xCC0D1B2C), Color(0x662A170C)),
                            ),
                        ),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .padding(18.dp),
            ) {
                BasicText(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                )
                if (meta != null) {
                    BodyText(meta, color = VivicastColors.TextSecondary)
                }
                BasicText(
                    text = body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = VivicastColors.TextSecondary, fontSize = 17.sp),
                )
                if (action != null) action()
            }
        }
    }
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
    HeroPanel(title = title, body = body, modifier = modifier, meta = meta, backdropResId = backdropResId, action = action)
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
    Column(modifier = modifier.width(154.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            onClick = onClick,
            contentPadding = 8.dp,
        ) { focused ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (hasPoster) {
                                Brush.verticalGradient(
                                    listOf(Color(0xFF3B526F), Color(0xFF23384F), Color(0xFF111A2A)),
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color(0xFF212B3B), Color(0xFF111827), Color(0xFF0A101B)),
                                )
                            },
                        )
                        .border(1.dp, Color(0x444FC3F7), RoundedCornerShape(14.dp)),
                ) {
                    if (imageResId != null) {
                        Image(
                            painter = painterResource(imageResId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0x77050910)),
                                    ),
                                ),
                        )
                    }
                    StatusBadge(rating, modifier = Modifier.padding(8.dp), tone = Color(0xD011445C))
                    if (favorite || seen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (favorite) Color(0xFF8A640A) else Color(0xFF1D5A3E))
                                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(999.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = if (favorite) "F" else "G",
                                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                    if (imageResId == null) {
                        BasicText(
                            text = if (hasPoster) initialsFor(title) else "Kein Poster",
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp),
                            style = TextStyle(
                                color = if (focused) Color.White else VivicastColors.TextSecondary,
                                fontSize = if (hasPoster) 24.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    if (progressPercent > 0) {
                        ProgressLine(
                            progressPercent,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp),
                        )
                    }
                }
                BasicText(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                BodyText(meta, color = VivicastColors.TextTertiary)
            }
        }
    }
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
    PosterCard(
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
fun ProgressLine(progressPercent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF2A3548)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((progressPercent.coerceIn(0, 100) / 100f))
                .height(6.dp)
                .background(VivicastColors.Accent),
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
            .background(Brush.verticalGradient(listOf(Color(0xE6162335), Color(0xD90B1320))))
            .border(1.dp, Color(0xFF263C55), VivicastShapes.FocusRadius)
            .padding(20.dp),
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
fun VivicastSearchResultCard(
    title: String,
    subtitle: String,
    rating: String? = null,
    posterLike: Boolean = false,
    imageResId: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    FocusPanel(
        modifier = modifier.width(if (posterLike) 190.dp else 300.dp).height(if (posterLike) 170.dp else 92.dp),
        onClick = onClick,
        contentPadding = 12.dp,
    ) { focused ->
        if (posterLike) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF304860), Color(0xFF101A2B))))
                        .border(1.dp, Color(0x334FC3F7), RoundedCornerShape(12.dp)),
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
                        BasicText(
                            text = initialsFor(title),
                            style = TextStyle(color = if (focused) Color.White else VivicastColors.TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    if (rating != null) StatusBadge("Rating $rating", modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                BasicText(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
                BodyText(subtitle)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                )
                if (rating != null) StatusBadge("Rating $rating") else BodyText(subtitle)
            }
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
    FocusPanel(onClick = onClick, modifier = modifier.fillMaxWidth().height(84.dp), contentPadding = 16.dp) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                )
                BodyText(help)
            }
            BasicText(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
fun VivicastTopNavItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    minWidth: Dp = 96.dp,
    onSelected: () -> Unit,
    onFocused: () -> Unit,
) {
    FocusPanel(
        modifier = modifier.width(minWidth).height(52.dp),
        selected = selected,
        onClick = onSelected,
        onFocused = onFocused,
        contentPadding = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            BasicText(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = if (selected) Color.White else VivicastColors.TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
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
    FocusPanel(selected = selected, onFocused = onFocused, onClick = onClick, modifier = modifier.fillMaxWidth().height(126.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MiniLogo(logoText, logoMissing, imageResId = logoResId)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                BasicText(
                    text = channelName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                )
                BodyText(program)
                ProgressLine(progressPercent)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge("Live", tone = Color(0xFF6D1D1D))
                    if (favorite) StatusBadge("Favorit", tone = Color(0xFF72520C))
                    if (catchUp) StatusBadge("Catch-Up", tone = Color(0xFF4D3A78))
                }
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            BodyText("00:${progress.toString().padStart(2, '0')}")
            BodyText(if (seekable) "01:40" else "LIVE")
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 30.dp else 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF101E2F), Color(0xFF07111D)),
                    ),
                )
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) VivicastColors.Focus else Color(0xFF2E4453),
                    RoundedCornerShape(999.dp),
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { onFocusChanged(it.isFocused) }
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
                }
                .focusable()
                .padding(8.dp),
        ) {
            ProgressLine(progress, modifier = Modifier.align(Alignment.Center))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth((progress.coerceIn(0, 100) / 100f))
                    .height(if (focused) 12.dp else 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(VivicastColors.Accent),
            )
        }
    }
}

@Composable
fun MiniLogo(text: String, missing: Boolean, modifier: Modifier = Modifier, imageResId: Int? = null) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (missing) {
                    Brush.verticalGradient(listOf(Color(0xFF2A303A), Color(0xFF111827)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF0B66A5), Color(0xFF123A6A)))
                },
            )
            .border(1.dp, Color(0x554FC3F7), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
            )
        } else {
            BasicText(
                text = if (missing) "?" else text.take(2).uppercase(),
                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun initialsFor(text: String): String =
    text.split(' ', ':', '-', '.', '/', '|')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "VC" }
