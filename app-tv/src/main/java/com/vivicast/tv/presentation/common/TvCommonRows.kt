package com.vivicast.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vivicast.core.model.Channel
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Playlist
@Composable
fun ProviderSettingsRow(
    playlist: Playlist,
    channelCount: Int,
    settings: ProviderUiSettings,
    syncState: ProviderSyncState,
    epgSource: EpgSource?,
    compact: Boolean,
    showSourceLabels: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 72.dp else 86.dp)
            .clip(shape)
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.Surface)
            .border(2.dp, if (focused) ViviCastColors.Focus else ViviCastColors.Line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.SurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = playlist.displayName().firstOrNull()?.uppercase() ?: "P",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.displayName(),
                color = Color.White,
                fontSize = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(if (settings.enabled) "Enabled" else "Disabled")
                    append(" / ")
                    append(playlist.sourceType.name.replace('_', ' '))
                    append(" / ")
                    append("$channelCount channels")
                },
                color = ViviCastColors.TextSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(playlist.safeSourceLabel())
                    append(" / ")
                    append(if (epgSource != null) "EPG linked" else "No EPG")
                },
                color = ViviCastColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = syncState.summary(),
            color = when {
                syncState.refreshing -> ViviCastColors.Warning
                syncState.lastErrorMessage != null -> ViviCastColors.Error
                else -> ViviCastColors.TextMuted
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Edit",
            color = ViviCastColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(
                when {
                    focused -> ViviCastColors.FocusFill
                    selected -> ViviCastColors.Selected
                    else -> ViviCastColors.Surface
                }
            )
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
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
            text = text,
            color = if (selected || focused) Color.White else ViviCastColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChannelRow(
    position: Int,
    channel: Channel,
    selected: Boolean,
    previewed: Boolean,
    showChannelNumbers: Boolean,
    showChannelMetadata: Boolean,
    compact: Boolean,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onMoveLeft: (() -> Boolean)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val active = focused || selected || previewed
    val foregroundColor = if (focused) ViviCastColors.Background else Color.White
    val secondaryColor = if (focused) Color(0xFF34505B) else ViviCastColors.TextSecondary
    val markerColor = when {
        focused -> ViviCastColors.Background
        selected -> ViviCastColors.Accent
        previewed -> ViviCastColors.Focus
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 64.dp else 76.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> ViviCastColors.Selected
                    previewed -> ViviCastColors.SurfaceRaised
                    else -> ViviCastColors.Surface
                }
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    selected -> ViviCastColors.Accent
                    previewed -> ViviCastColors.Focus.copy(alpha = 0.55f)
                    else -> ViviCastColors.Line
                },
                shape = shape
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                }
            }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.DirectionLeft &&
                    onMoveLeft != null
                ) {
                    onMoveLeft()
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(markerColor)
            )

            if (showChannelNumbers) {
                Text(
                    text = position.toString().padStart(2, '0'),
                    color = if (focused) ViviCastColors.Background else if (active) ViviCastColors.Accent else ViviCastColors.TextMuted,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (focused) ViviCastColors.Background.copy(alpha = 0.12f) else ViviCastColors.SurfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.firstOrNull()?.uppercase() ?: "TV",
                    color = foregroundColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = foregroundColor,
                    fontSize = if (compact) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showChannelMetadata) {
                    Text(
                        text = channel.tvgName ?: "EPG not assigned yet",
                        color = secondaryColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selected || previewed) {
                Text(
                    text = if (selected) "PLAYING" else "PREVIEW",
                    color = if (focused) ViviCastColors.Background else ViviCastColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

