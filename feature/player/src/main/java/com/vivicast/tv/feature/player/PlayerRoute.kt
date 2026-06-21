package com.vivicast.tv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.ProgressLine
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.data.media.DemoCatalog

@Composable
fun PlayerRoute(onClose: () -> Unit = {}) {
    var overlayVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(42) }
    var focusedTimeline by remember { mutableStateOf(false) }
    var playerStateIndex by remember { mutableIntStateOf(0) }
    val timelineFocusRequester = remember { FocusRequester() }
    val hiddenOverlayFocusRequester = remember { FocusRequester() }
    val playerState = DemoCatalog.playerStates[playerStateIndex]

    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            timelineFocusRequester.requestFocus()
        } else {
            hiddenOverlayFocusRequester.requestFocus()
        }
    }

    BackHandler {
        if (overlayVisible) {
            overlayVisible = false
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1016), Color(0xFF14242D), Color(0xFF05080B)),
                ),
            )
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && !overlayVisible) {
                    overlayVisible = true
                    true
                } else {
                    false
                }
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp),
        ) {
            playerState.badges.forEach { StatusBadge(it) }
        }

        Column(modifier = Modifier.padding(40.dp)) {
            SectionTitle(playerState.title)
            BodyText(if (playing) "Playing Demo" else "Pausiert")
        }

        if (overlayVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xDD0D141B))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(
                            text = playerState.title,
                            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                        )
                        BodyText(if (playerState.seekable) "Timeline steuert Demo-Position." else "Timeshift fuer diesen Sender nicht verfuegbar.")
                    }
                    StatusBadge(if (playing) "Playing" else "Pausiert")
                }

                TimelineControl(
                    progress = progress,
                    focused = focusedTimeline,
                    seekable = playerState.seekable,
                    focusRequester = timelineFocusRequester,
                    onFocused = { focusedTimeline = true },
                    onBlurred = { focusedTimeline = false },
                    onTogglePlay = { playing = !playing },
                    onSeekLeft = { if (playerState.seekable) progress = (progress - 8).coerceAtLeast(0) },
                    onSeekRight = { if (playerState.seekable) progress = (progress + 8).coerceAtMost(100) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionPill("Audio", onClick = {})
                    ActionPill("Untertitel", onClick = {})
                    ActionPill("Bildformat", onClick = {})
                    ActionPill("Mehr", onClick = {})
                    Spacer(Modifier.width(20.dp))
                    ActionPill("Ohne Timeshift", selected = playerStateIndex == 0, onClick = { playerStateIndex = 0 })
                    ActionPill("Timeshift", selected = playerStateIndex == 1, onClick = { playerStateIndex = 1 })
                    ActionPill("VOD", selected = playerStateIndex == 2, onClick = { playerStateIndex = 2 })
                }

                InfoPanel(
                    title = "Player Demo",
                    body = "Timeline ist Hauptaktion; Ton, Untertitel und Bildformat sind direkt erreichbar.",
                    badge = "Phase 2",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoPanel(
                    title = "Overlay versteckt",
                    body = "Bild laeuft weiter. Overlay kann wieder eingeblendet werden.",
                    badge = "Demo",
                )
                ActionPill(
                    label = "Overlay",
                    modifier = Modifier.focusRequester(hiddenOverlayFocusRequester),
                    onClick = { overlayVisible = true },
                )
            }
        }
    }
}

@Composable
private fun TimelineControl(
    progress: Int,
    focused: Boolean,
    seekable: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onBlurred: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekLeft: () -> Unit,
    onSeekRight: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            BodyText("00:${progress.toString().padStart(2, '0')}")
            BodyText(if (seekable) "01:40" else "LIVE")
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 28.dp else 22.dp)
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) VivicastColors.Focus else Color(0xFF2E4453),
                    RoundedCornerShape(999.dp),
                )
                .padding(8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) onFocused() else onBlurred()
                }
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
                .focusable(),
        ) {
            ProgressLine(progress, modifier = Modifier.align(Alignment.Center))
        }
    }
}
