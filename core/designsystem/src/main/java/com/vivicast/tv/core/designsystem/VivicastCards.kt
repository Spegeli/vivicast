package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

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
    surfaceModifier: Modifier = Modifier,
    imageResId: Int? = null,
    imageModel: Any? = null,
    onFocused: () -> Unit = {},
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
        surfaceModifier = surfaceModifier,
        imageResId = imageResId,
        imageModel = imageModel,
        onFocused = onFocused,
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
    surfaceModifier: Modifier = Modifier,
    imageResId: Int? = null,
    imageModel: Any? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.width(VivicastCardSizes.PosterWidth),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
    ) {
        VivicastFocusSurface(
            modifier = surfaceModifier
                .fillMaxWidth()
                .height(VivicastCardSizes.PosterImageHeight),
            onClick = onClick,
            onFocused = onFocused,
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
                imageModel = imageModel,
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
    imageModel: Any?,
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
        // #26: placeholder drawn first (behind); a failed/absent image falls back to it (children draw back-to-front).
        Text(
            text = if (hasPoster) initialsFor(title) else stringResource(R.string.card_no_poster),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = VivicastSpacing.Space4),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.TitleSmall.copy(
                color = if (focused) Color.White else VivicastColors.TextSecondary,
            ),
        )
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA8050910)))),
            )
        } else if (imageResId != null) {
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
fun VivicastSearchResultCard(
    title: String,
    subtitle: String,
    rating: String? = null,
    posterLike: Boolean = false,
    imageResId: Int? = null,
    imageModel: Any? = null,
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
                SearchPosterThumb(title = title, rating = rating, imageResId = imageResId, imageModel = imageModel, focused = focused)
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
                    StatusBadge("★ $rating")
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
    imageModel: Any?,
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
        // #26: initials drawn first (behind); a failed/absent image falls back to them.
        Text(
            text = initialsFor(title),
            style = VivicastTypography.TitleSmall.copy(color = if (focused) Color.White else VivicastColors.TextSecondary),
        )
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99070A12)))),
            )
        } else if (imageResId != null) {
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
        }
        if (rating != null) {
            StatusBadge("★ $rating", modifier = Modifier.align(Alignment.TopStart).padding(VivicastSpacing.Space1))
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
    logoModel: Any? = null,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    VivicastFocusSurface(
        selected = selected,
        onFocused = onFocused,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(VivicastCardSizes.ChannelItemHeight),
        contentPadding = VivicastSpacing.Space3,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MiniLogo(logoText, logoMissing, imageResId = logoResId, imageModel = logoModel)
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
                    StatusBadge(stringResource(R.string.livetv_live_badge), tone = Color(0xFF6D1D1D))
                    if (favorite) StatusBadge(stringResource(R.string.favorite_badge), tone = Color(0xFF72520C))
                    if (catchUp) StatusBadge(stringResource(R.string.livetv_badge_catchup), tone = Color(0xFF4D3A78))
                }
            }
        }
    }
}

/**
 * Home "Zuletzt gesehene Sender" card — logo on top, channel name below. Compact per the Home spec:
 * logo + name only (no program / progress / favorite).
 */
@Composable
fun VivicastHomeChannelCard(
    channelName: String,
    logoText: String,
    logoMissing: Boolean,
    modifier: Modifier = Modifier,
    logoResId: Int? = null,
    logoModel: Any? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.width(VivicastCardSizes.PosterWidth),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VivicastFocusSurface(
            modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.HomeChannelTileHeight),
            onClick = onClick,
            onFocused = onFocused,
            contentPadding = VivicastSpacing.Space3,
            shape = VivicastShapes.PosterRadius,
            focusScale = VivicastFocusDefaults.ScaleMedium,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MiniLogo(logoText, logoMissing, imageResId = logoResId, imageModel = logoModel)
            }
        }
        Text(
            text = channelName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium,
        )
    }
}

/**
 * Home "Filme/Serien fortsetzen" card — poster + title + a thin accent-coloured progress bar (no percent
 * number, no rating/favorite/seen). See the Home card spec.
 */
@Composable
fun VivicastHomePosterCard(
    title: String,
    hasPoster: Boolean,
    progressPercent: Int,
    modifier: Modifier = Modifier,
    imageResId: Int? = null,
    imageModel: Any? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.width(VivicastCardSizes.PosterWidth),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
    ) {
        VivicastFocusSurface(
            modifier = Modifier.fillMaxWidth().height(VivicastCardSizes.PosterImageHeight),
            onClick = onClick,
            onFocused = onFocused,
            contentPadding = VivicastSpacing.Space0,
            shape = VivicastShapes.PosterRadius,
            focusScale = VivicastFocusDefaults.ScaleMedium,
        ) { focused ->
            HomePosterArtwork(
                title = title,
                hasPoster = hasPoster,
                imageResId = imageResId,
                imageModel = imageModel,
                focused = focused,
                progressPercent = progressPercent,
            )
        }
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelMedium,
        )
    }
}

@Composable
private fun HomePosterArtwork(
    title: String,
    hasPoster: Boolean,
    imageResId: Int?,
    imageModel: Any?,
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
        // Placeholder drawn first (behind); a failed/absent image falls back to it.
        Text(
            text = if (hasPoster) initialsFor(title) else stringResource(R.string.card_no_poster),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = VivicastSpacing.Space4),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.TitleSmall.copy(
                color = if (focused) Color.White else VivicastColors.TextSecondary,
            ),
        )
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA8050910)))),
            )
        } else if (imageResId != null) {
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
        }
        if (progressPercent > 0) {
            ProgressLine(
                progressPercent,
                modifier = Modifier.align(Alignment.BottomCenter).padding(VivicastSpacing.Space3),
                color = LocalVivicastColors.current.accent,
            )
        }
    }
}

@Composable
fun MiniLogo(
    text: String,
    missing: Boolean,
    modifier: Modifier = Modifier,
    imageResId: Int? = null,
    imageModel: Any? = null,
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
        var loaded by remember(imageModel) { mutableStateOf(false) }
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(VivicastSpacing.Space2),
                onState = { loaded = it is AsyncImagePainter.State.Success },
            )
        } else if (imageResId != null) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(VivicastSpacing.Space2),
            )
        }
        // #26: Fit leaves margins, so a behind-placeholder would bleed around a loaded logo —
        // show the initials/"?" unless an async logo actually loaded (also covers 404/empty).
        if (imageResId == null && (imageModel == null || !loaded)) {
            Text(
                text = if (missing) "?" else text.take(2).uppercase(),
                style = VivicastTypography.LabelMedium.copy(color = Color.White),
            )
        }
    }
}

private val VivicastShapes.RadiusMediumShape: RoundedCornerShape
    get() = RoundedCornerShape(RadiusMedium)

private fun initialsFor(text: String): String =
    text.split(' ', ':', '-', '.', '/', '|')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "VC" }
