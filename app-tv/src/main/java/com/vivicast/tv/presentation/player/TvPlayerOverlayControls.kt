package com.vivicast.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.vivicast.core.domain.EpgNowNext
import com.vivicast.core.model.Channel
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.model.StreamTrack
@Composable
fun OverlayBadge(
    text: String,
    backgroundColor: Color = ViviCastColors.Surface.copy(alpha = 0.9f),
    textColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusBanner(
    text: String,
    positive: Boolean,
    compact: Boolean = false
) {
    Box(
        modifier = Modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(8.dp))
            .background(if (positive) ViviCastColors.Success.copy(alpha = 0.16f) else ViviCastColors.Surface)
            .border(
                1.dp,
                if (positive) ViviCastColors.Success.copy(alpha = 0.65f) else ViviCastColors.Line,
                RoundedCornerShape(8.dp)
            )
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 8.dp else 10.dp
            )
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OverlayActionRow(
    recentChannels: List<Channel>,
    showGuideAction: Boolean,
    audioTracks: List<StreamTrack>,
    subtitleTracks: List<StreamTrack>,
    playbackStatus: PlaybackStatus,
    onOpenGuide: () -> Unit,
    onPlayRecentChannel: (Channel) -> Unit,
    onSelectTrack: (StreamTrack) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showGuideAction) {
            item {
                OverlayChip(
                    label = "EPG",
                    supporting = "Open guide",
                    primary = true,
                    onClick = onOpenGuide
                )
            }
        }
        when (playbackStatus) {
            PlaybackStatus.Playing -> {
                item {
                    OverlayChip(
                        label = "Pause",
                        supporting = "Pause stream",
                        primary = false,
                        onClick = onPausePlayback
                    )
                }
                item {
                    OverlayChip(
                        label = "Stop",
                        supporting = "Stop stream",
                        primary = false,
                        onClick = onStopPlayback
                    )
                }
            }

            PlaybackStatus.Paused -> {
                item {
                    OverlayChip(
                        label = "Resume",
                        supporting = "Play current channel",
                        primary = false,
                        onClick = onResumePlayback
                    )
                }
                item {
                    OverlayChip(
                        label = "Stop",
                        supporting = "Stop stream",
                        primary = false,
                        onClick = onStopPlayback
                    )
                }
            }

            is PlaybackStatus.Error -> {
                item {
                    OverlayChip(
                        label = "Retry",
                        supporting = if (playbackStatus.recoverable) "Retry current channel" else "Try again",
                        primary = false,
                        onClick = onResumePlayback
                    )
                }
                item {
                    OverlayChip(
                        label = "Stop",
                        supporting = "Clear player state",
                        primary = false,
                        onClick = onStopPlayback
                    )
                }
            }

            else -> Unit
        }
        item {
            OverlayChip(
                label = "Recent",
                supporting = if (recentChannels.isEmpty()) "No history" else "${recentChannels.size} channels",
                primary = false,
                enabled = recentChannels.isNotEmpty(),
                onClick = {}
            )
        }
        item {
            val selectedAudio = audioTracks.firstOrNull { it.selected }
            OverlayChip(
                label = "Audio",
                supporting = selectedAudio?.compactTrackLabel() ?: "No track",
                primary = false,
                enabled = audioTracks.isNotEmpty(),
                onClick = {}
            )
        }
        items(audioTracks, key = { it.id }) { track ->
            OverlayChip(
                label = track.label,
                supporting = buildString {
                    append("Audio")
                    track.language?.takeIf { it.isNotBlank() }?.let {
                        append(" / ")
                        append(it.uppercase())
                    }
                },
                primary = track.selected,
                onClick = { onSelectTrack(track) }
            )
        }
        item {
            val selectedSubtitle = subtitleTracks.firstOrNull { it.selected }
            OverlayChip(
                label = "Subtitles",
                supporting = selectedSubtitle?.compactTrackLabel() ?: "No track",
                primary = false,
                enabled = subtitleTracks.isNotEmpty(),
                onClick = {}
            )
        }
        items(subtitleTracks, key = { it.id }) { track ->
            OverlayChip(
                label = track.label,
                supporting = buildString {
                    append("Subtitles")
                    track.language?.takeIf { it.isNotBlank() }?.let {
                        append(" / ")
                        append(it.uppercase())
                    }
                },
                primary = track.selected,
                onClick = { onSelectTrack(track) }
            )
        }
        items(recentChannels, key = { it.id }) { channel ->
            OverlayChip(
                label = channel.name,
                supporting = channel.tvgName ?: "Play again",
                primary = false,
                onClick = { onPlayRecentChannel(channel) }
            )
        }
    }
}

@Composable
fun OverlayChip(
    label: String,
    supporting: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(114.dp)
            .height(62.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> ViviCastColors.SurfaceRaised.copy(alpha = 0.6f)
                    focused -> Color.White
                    primary -> ViviCastColors.Selected
                    else -> ViviCastColors.Surface
                }
            )
            .border(
                width = if (focused || primary) 2.dp else 1.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    primary -> ViviCastColors.Accent
                    else -> ViviCastColors.Line
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .activateOnCenter { if (enabled) onClick() }
            .focusable(enabled = enabled)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = if (focused) ViviCastColors.Background else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = supporting,
            color = if (focused) ViviCastColors.Background.copy(alpha = 0.72f) else ViviCastColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FavoriteActionChip(
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> ViviCastColors.Selected
                    else -> ViviCastColors.Surface
                }
            )
            .border(
                width = 2.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    selected -> ViviCastColors.Accent
                    else -> ViviCastColors.Line
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "Favorite" else "Add favorite",
            color = if (focused) ViviCastColors.Background else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

fun StreamTrack.compactTrackLabel(): String {
    return buildString {
        append(label)
        language?.takeIf { it.isNotBlank() }?.let {
            append(" / ")
            append(it.uppercase())
        }
    }
}

@Composable
fun EmptyPlayerState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ViviCastColors.Surface)
                .border(2.dp, ViviCastColors.Accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("V", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Text(
            text = "Choose a channel to start playback",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp
        )
        Text(
            text = "Use arrow keys or the TV remote, then press OK.",
            color = ViviCastColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun NowNextPanel(
    selectedChannel: Channel?,
    nowNext: EpgNowNext,
    currentTimeMillis: Long,
    sourceName: String?,
    showSourceLabels: Boolean,
    showProgrammeDescription: Boolean
) {
    val nowProgram = nowNext.now
    val nextProgram = nowNext.next
    val progress = nowProgram?.progressAt(currentTimeMillis) ?: 0f
    val showEmptySelectionState = selectedChannel == null && nowProgram == null
    val nowHeadline = when {
        showEmptySelectionState -> "No channel selected"
        nowProgram != null -> nowProgram.title
        else -> "Live stream"
    }
    val nowSupporting = when {
        showEmptySelectionState -> "Choose a channel to start playback."
        else -> nowProgram?.timeRangeLabel() ?: "EPG data pending"
    }
    val detailCopy = when {
        showEmptySelectionState -> "Browse the list on the left and press OK to start live playback."
        else -> nowProgram?.description
            ?: nextProgram?.let { "Next: ${it.timeRangeLabel()}  ${it.title}" }
            ?: "Next programme will appear here when EPG data is available."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Now",
                    color = ViviCastColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = nowHeadline,
                    color = Color.White,
                    fontSize = if (showEmptySelectionState) 18.sp else 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = nowSupporting,
                    color = ViviCastColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showSourceLabels && !showEmptySelectionState) {
                Column(
                    modifier = Modifier.width(150.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Source",
                        color = ViviCastColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = sourceName ?: "No provider",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ViviCastColors.Line)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(ViviCastColors.Accent)
            )
        }

        if (showProgrammeDescription) {
            Text(
                text = detailCopy,
                color = ViviCastColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TvActionButton(
    text: String,
    compactText: String,
    expanded: Boolean,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.SurfaceRaised)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) ViviCastColors.Focus else ViviCastColors.Line,
                shape = shape
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = if (expanded) 16.dp else 0.dp),
        contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = if (expanded) text else compactText,
            color = if (focused) Color.White else ViviCastColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun Modifier.activateOnCenter(onClick: () -> Unit): Modifier {
    return onPreviewKeyEvent { event ->
        if (
            event.type == KeyEventType.KeyUp &&
            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
        ) {
            onClick()
            true
        } else {
            false
        }
    }
}

tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

