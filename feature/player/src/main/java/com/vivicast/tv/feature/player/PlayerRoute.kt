package com.vivicast.tv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.data.media.DemoCatalog

@Composable
fun PlayerRoute(
    playerController: VivicastPlayerController? = null,
    onClose: () -> Unit = {},
    onChannelUp: () -> Unit = {},
    onChannelDown: () -> Unit = {},
) {
    var overlayVisible by remember { mutableStateOf(true) }
    var demoPlaying by remember { mutableStateOf(true) }
    var demoProgress by remember { mutableIntStateOf(42) }
    var focusedTimeline by remember { mutableStateOf(false) }
    val timelineFocusRequester = remember { FocusRequester() }
    val hiddenOverlayFocusRequester = remember { FocusRequester() }
    val demoPlayerState = DemoCatalog.playerStates.first()
    val controllerState by playerController?.state?.collectAsState() ?: remember { mutableStateOf(null) }
    val request = controllerState?.request
    val playing = when (controllerState?.status) {
        PlaybackStatus.Starting, PlaybackStatus.Playing -> true
        PlaybackStatus.Idle,
        PlaybackStatus.Paused,
        PlaybackStatus.Error,
        PlaybackStatus.Released -> false
        null -> demoPlaying
    }
    val seekable = request?.seekable ?: demoPlayerState.seekable
    val progress = controllerState?.progressPercent() ?: demoProgress
    val title = request?.title ?: demoPlayerState.title

    DisposableEffect(playerController) {
        onDispose {
            playerController?.stop()
        }
    }

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
            playerController?.stop()
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02060C), Color(0xFF071A2A), Color(0xFF05080B))))
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    it.key == Key.ChannelUp -> {
                        onChannelUp()
                        true
                    }
                    it.key == Key.ChannelDown -> {
                        onChannelDown()
                        true
                    }
                    it.key == Key.Enter && !overlayVisible -> {
                        overlayVisible = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.padding(40.dp)) {
            SectionTitle(title)
            BodyText(if (playing) "Wiedergabe" else "Pausiert")
        }

        if (overlayVisible) {
            VivicastPlayerOverlay(
                title = title,
                subtitle = if (controllerState?.status == PlaybackStatus.Error) {
                    "Wiedergabe konnte nicht gestartet werden."
                } else if (seekable) {
                    "Timeline steuert die Position."
                } else {
                    "Timeshift fuer diesen Sender nicht verfuegbar."
                },
                statusLabel = controllerState?.status?.statusLabel(request?.mediaType) ?: if (playing) "Live" else "Pausiert",
                badges = demoPlayerState.badges,
                progress = progress,
                seekable = seekable,
                focusedTimeline = focusedTimeline,
                timelineFocusRequester = timelineFocusRequester,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 28.dp)
                    .testTag(playerOverlayTag()),
                onTimelineFocusChanged = { focusedTimeline = it },
                onTogglePlay = {
                    if (playerController == null) {
                        demoPlaying = !demoPlaying
                    } else if (controllerState?.status == PlaybackStatus.Playing) {
                        playerController.pause()
                    } else {
                        playerController.resume()
                    }
                },
                onSeekLeft = {
                    if (seekable) {
                        playerController?.seekBy(-SEEK_STEP_MILLIS)
                            ?: run { demoProgress = (demoProgress - 8).coerceAtLeast(0) }
                    }
                },
                onSeekRight = {
                    if (seekable) {
                        playerController?.seekBy(SEEK_STEP_MILLIS)
                            ?: run { demoProgress = (demoProgress + 8).coerceAtMost(100) }
                    }
                },
                actions = {
                    ActionPill("Audio", onClick = {})
                    ActionPill("Untertitel", onClick = {})
                    ActionPill("Bildformat", onClick = {})
                    ActionPill("Mehr", onClick = {})
                },
                footer = {
                    VivicastContentCard(modifier = Modifier.fillMaxWidth().height(70.dp), contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
                            BodyText("Naechstes Programm", maxLines = 1)
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
                    body = "Bild laeuft weiter. Overlay kann wieder eingeblendet werden.",
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

private const val SEEK_STEP_MILLIS = 30_000L

private fun VivicastPlayerState.progressPercent(): Int {
    if (durationMillis <= 0L) return 0
    return ((positionMillis.coerceAtLeast(0L) * 100L) / durationMillis).coerceIn(0L, 100L).toInt()
}

private fun PlaybackStatus.statusLabel(mediaType: PlaybackMediaType?): String =
    when (this) {
        PlaybackStatus.Idle -> "Bereit"
        PlaybackStatus.Starting -> "Startet"
        PlaybackStatus.Playing -> if (mediaType == PlaybackMediaType.Channel) "Live" else "Wiedergabe"
        PlaybackStatus.Paused -> "Pausiert"
        PlaybackStatus.Error -> "Fehler"
        PlaybackStatus.Released -> "Beendet"
    }
