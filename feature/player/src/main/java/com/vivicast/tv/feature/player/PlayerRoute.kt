package com.vivicast.tv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.VivicastContentCard
import com.vivicast.tv.core.designsystem.VivicastPlayerOverlay
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.media.DemoCatalog

@Composable
fun PlayerRoute(onClose: () -> Unit = {}) {
    var overlayVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(42) }
    var focusedTimeline by remember { mutableStateOf(false) }
    val timelineFocusRequester = remember { FocusRequester() }
    val hiddenOverlayFocusRequester = remember { FocusRequester() }
    val playerState = DemoCatalog.playerStates.first()

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
            .background(Brush.verticalGradient(listOf(Color(0xFF02060C), Color(0xFF071A2A), Color(0xFF05080B))))
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && !overlayVisible) {
                    overlayVisible = true
                    true
                } else {
                    false
                }
            },
    ) {
        Column(modifier = Modifier.padding(40.dp)) {
            SectionTitle(playerState.title)
            BodyText(if (playing) "Wiedergabe" else "Pausiert")
        }

        if (overlayVisible) {
            VivicastPlayerOverlay(
                title = playerState.title,
                subtitle = if (playerState.seekable) {
                    "Timeline steuert die Position."
                } else {
                    "Timeshift für diesen Sender nicht verfügbar."
                },
                statusLabel = if (playing) "Live" else "Pausiert",
                badges = playerState.badges,
                progress = progress,
                seekable = playerState.seekable,
                focusedTimeline = focusedTimeline,
                timelineFocusRequester = timelineFocusRequester,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 28.dp)
                    .testTag(playerOverlayTag()),
                onTimelineFocusChanged = { focusedTimeline = it },
                onTogglePlay = { playing = !playing },
                onSeekLeft = { if (playerState.seekable) progress = (progress - 8).coerceAtLeast(0) },
                onSeekRight = { if (playerState.seekable) progress = (progress + 8).coerceAtMost(100) },
                actions = {
                    ActionPill("Audio", onClick = {})
                    ActionPill("Untertitel", onClick = {})
                    ActionPill("Bildformat", onClick = {})
                    ActionPill("Mehr", onClick = {})
                },
                footer = {
                    VivicastContentCard(modifier = Modifier.fillMaxWidth().height(70.dp), contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                            BodyText("Nächstes Programm", maxLines = 1)
                            BodyText("20:15 - 20:45 Tagesthemen", maxLines = 1)
                        }
                    }
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(48.dp)
                    .testTag(playerHiddenOverlayTag()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoPanel(
                    title = "Overlay versteckt",
                    body = "Bild läuft weiter. Overlay kann wieder eingeblendet werden.",
                )
                ActionPill(
                    label = "Overlay",
                    modifier = Modifier
                        .focusRequester(hiddenOverlayFocusRequester)
                        .testTag(playerHiddenOverlayActionTag()),
                    onClick = { overlayVisible = true },
                )
            }
        }
    }
}

fun playerOverlayTag(): String = "player-overlay"
fun playerHiddenOverlayTag(): String = "player-hidden-overlay"
fun playerHiddenOverlayActionTag(): String = "player-hidden-overlay-action"
