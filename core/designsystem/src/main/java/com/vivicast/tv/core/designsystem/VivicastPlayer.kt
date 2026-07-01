package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

fun playerTimelineTag(): String = "player-timeline"

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
            .padding(horizontal = VivicastSpacing.Space6, vertical = VivicastSpacing.Space4),
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
                .testTag(playerTimelineTag())
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
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
            onClick = null,
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
