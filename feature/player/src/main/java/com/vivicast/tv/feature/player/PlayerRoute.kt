package com.vivicast.tv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.VivicastContentCard
import com.vivicast.tv.core.designsystem.VivicastPlayerOverlay
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.player.PlaybackAspectRatioMode
import com.vivicast.tv.core.player.PlaybackAudioOption
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.PlaybackSubtitleOption
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.player.VivicastPlayerState
import kotlinx.coroutines.delay

@Composable
fun PlayerRoute(
    playerController: VivicastPlayerController? = null,
    onClose: () -> Unit = {},
    onChannelUp: () -> Unit = {},
    onChannelDown: () -> Unit = {},
    onChooseAnotherChannel: () -> Unit = {},
    onBeforeStop: (VivicastPlayerState?) -> Unit = {},
    autoNextEnabled: Boolean = false,
    autoNextCountdownSeconds: Int = 10,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: () -> Unit = {},
    onAutoNextBack: () -> Unit = {},
) {
    var overlayVisible by remember { mutableStateOf(false) }
    var overlayInteractionTick by remember { mutableIntStateOf(0) }
    var fallbackPlaying by remember { mutableStateOf(false) }
    var fallbackProgress by remember { mutableIntStateOf(0) }
    var focusedTimeline by remember { mutableStateOf(false) }
    var stopRequested by remember { mutableStateOf(false) }
    var autoNextDismissed by remember { mutableStateOf(false) }
    var autoNextStartedForPlaybackId by remember { mutableStateOf<String?>(null) }
    var optionPanel by remember { mutableStateOf<PlayerOptionPanel?>(null) }
    var fallbackAudioOption by remember { mutableStateOf(PlaybackAudioOption.SystemDefault) }
    var fallbackSubtitleOption by remember { mutableStateOf(PlaybackSubtitleOption.Off) }
    var fallbackAspectRatioMode by remember { mutableStateOf(PlaybackAspectRatioMode.Fit) }
    val rootFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }
    val errorRetryFocusRequester = remember { FocusRequester() }
    val controllerState by playerController?.state?.collectAsState() ?: remember { mutableStateOf(null) }
    val latestControllerState by rememberUpdatedState(controllerState)
    val latestOnBeforeStop by rememberUpdatedState(onBeforeStop)
    val currentState = controllerState
    val request = controllerState?.request
    val playing = when (controllerState?.status) {
        PlaybackStatus.Starting, PlaybackStatus.Playing -> true
        PlaybackStatus.Idle,
        PlaybackStatus.Paused,
        PlaybackStatus.Ended,
        PlaybackStatus.Error,
        PlaybackStatus.Released -> false
        null -> fallbackPlaying
    }
    val seekable = request?.seekable ?: false
    val progress = controllerState?.progressPercent() ?: fallbackProgress
    val title = request?.title ?: "Keine Wiedergabe"
    val badges = controllerState?.badges() ?: emptyList()
    val audioOption = currentState?.audioOption ?: fallbackAudioOption
    val subtitleOption = currentState?.subtitleOption ?: fallbackSubtitleOption
    val aspectRatioMode = currentState?.aspectRatioMode ?: fallbackAspectRatioMode
    val playbackId = request?.playbackId
    val hasNextEpisode = request?.mediaType == PlaybackMediaType.Episode && nextEpisodeTitle != null
    val countdownSeconds = if (autoNextEnabled) {
        currentState?.autoNextRemainingSeconds(autoNextCountdownSeconds)
    } else {
        null
    }
    val showAutoNextPanel = hasNextEpisode && !autoNextDismissed &&
        (currentState?.status == PlaybackStatus.Ended || countdownSeconds != null)

    fun stopPlaybackOnce() {
        if (stopRequested) return
        stopRequested = true
        latestOnBeforeStop(latestControllerState)
        playerController?.stop()
    }

    DisposableEffect(playerController) {
        onDispose {
            stopPlaybackOnce()
        }
    }

    fun playNextEpisodeOnce() {
        if (autoNextStartedForPlaybackId == playbackId) return
        autoNextStartedForPlaybackId = playbackId
        onPlayNextEpisode()
    }

    fun returnFromAutoNext() {
        autoNextDismissed = true
        stopPlaybackOnce()
        onAutoNextBack()
    }

    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            timelineFocusRequester.requestFocus()
        } else {
            rootFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(controllerState?.status) {
        if (controllerState?.status == PlaybackStatus.Error) {
            errorRetryFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(playbackId) {
        autoNextDismissed = false
        autoNextStartedForPlaybackId = null
        stopRequested = false
    }

    LaunchedEffect(currentState?.status, hasNextEpisode, autoNextEnabled, autoNextDismissed, playbackId) {
        if (currentState?.status == PlaybackStatus.Ended &&
            hasNextEpisode &&
            autoNextEnabled &&
            !autoNextDismissed
        ) {
            playNextEpisodeOnce()
        }
    }

    LaunchedEffect(overlayVisible, overlayInteractionTick, controllerState?.status, optionPanel) {
        if (overlayVisible && optionPanel == null && controllerState?.status != PlaybackStatus.Error) {
            delay(OVERLAY_AUTO_HIDE_MILLIS)
            overlayVisible = false
        }
    }

    BackHandler {
        if (optionPanel != null) {
            optionPanel = null
        } else if (showAutoNextPanel) {
            returnFromAutoNext()
        } else if (overlayVisible) {
            overlayVisible = false
        } else {
            stopPlaybackOnce()
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02060C), Color(0xFF071A2A), Color(0xFF05080B))))
            .focusRequester(rootFocusRequester)
            .focusable()
            .testTag(playerRootTag())
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (overlayVisible) {
                    overlayInteractionTick += 1
                }
                when {
                    it.key == Key.ChannelUp -> {
                        onChannelUp()
                        true
                    }
                    it.key == Key.ChannelDown -> {
                        onChannelDown()
                        true
                    }
                    it.isPrimaryActionKey() &&
                        !overlayVisible &&
                        controllerState?.status != PlaybackStatus.Error -> {
                        overlayVisible = true
                        overlayInteractionTick += 1
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (overlayVisible) {
            VivicastPlayerOverlay(
                title = title,
                subtitle = if (controllerState?.status == PlaybackStatus.Error) {
                    "Wiedergabe konnte nicht gestartet werden."
                } else if (currentState?.isTimeshiftEnabled == true) {
                    if (currentState.isAtLiveEdge) {
                        "Timeshift-Fenster aktiv. Live-Kante erreicht."
                    } else {
                        "Timeshift: ${currentState.liveEdgeOffsetMillis.formatOffset()} hinter Live."
                    }
                } else if (seekable) {
                    "Timeline steuert die Position."
                } else {
                    "Timeshift für diesen Sender nicht verfügbar."
                },
                statusLabel = controllerState?.status?.statusLabel(controllerState) ?: if (playing) "Live" else "Pausiert",
                badges = badges,
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
                        fallbackPlaying = !fallbackPlaying
                    } else if (controllerState?.status == PlaybackStatus.Playing) {
                        playerController.pause()
                    } else {
                        playerController.resume()
                    }
                },
                onSeekLeft = {
                    if (seekable) {
                        playerController?.seekBy(-SEEK_STEP_MILLIS)
                            ?: run { fallbackProgress = (fallbackProgress - 8).coerceAtLeast(0) }
                    }
                },
                onSeekRight = {
                    if (seekable) {
                        playerController?.seekBy(SEEK_STEP_MILLIS)
                            ?: run { fallbackProgress = (fallbackProgress + 8).coerceAtMost(100) }
                    }
                },
                actions = {
                    if (currentState?.isTimeshiftEnabled == true && !currentState.isAtLiveEdge) {
                        ActionPill(
                            "Live",
                            selected = true,
                            modifier = Modifier.testTag(playerLiveEdgeTag()),
                            onClick = { playerController?.seekToLiveEdge() },
                        )
                    }
                    ActionPill(
                        "Audio",
                        selected = optionPanel == PlayerOptionPanel.Audio,
                        modifier = Modifier.testTag(playerAudioActionTag()),
                        onClick = { optionPanel = PlayerOptionPanel.Audio },
                    )
                    ActionPill(
                        "Untertitel",
                        selected = optionPanel == PlayerOptionPanel.Subtitle,
                        modifier = Modifier.testTag(playerSubtitleActionTag()),
                        onClick = { optionPanel = PlayerOptionPanel.Subtitle },
                    )
                    ActionPill(
                        "Bildformat",
                        selected = optionPanel == PlayerOptionPanel.AspectRatio,
                        modifier = Modifier.testTag(playerAspectActionTag()),
                        onClick = { optionPanel = PlayerOptionPanel.AspectRatio },
                    )
                    ActionPill("Mehr", onClick = {})
                },
                footer = {},
            )
        }

        when (optionPanel) {
            PlayerOptionPanel.Audio -> PlayerOptionDialog(
                title = "Audio",
                options = listOf(
                    PlayerOptionItem("audio-system", "Systemstandard", audioOption == PlaybackAudioOption.SystemDefault) {
                        playerController?.selectAudio(PlaybackAudioOption.SystemDefault)
                            ?: run { fallbackAudioOption = PlaybackAudioOption.SystemDefault }
                        optionPanel = null
                    },
                    PlayerOptionItem("audio-de", "Deutsch", audioOption == PlaybackAudioOption.German) {
                        playerController?.selectAudio(PlaybackAudioOption.German)
                            ?: run { fallbackAudioOption = PlaybackAudioOption.German }
                        optionPanel = null
                    },
                    PlayerOptionItem("audio-en", "Englisch", audioOption == PlaybackAudioOption.English) {
                        playerController?.selectAudio(PlaybackAudioOption.English)
                            ?: run { fallbackAudioOption = PlaybackAudioOption.English }
                        optionPanel = null
                    },
                    PlayerOptionItem("audio-original", "Original", audioOption == PlaybackAudioOption.Original) {
                        playerController?.selectAudio(PlaybackAudioOption.Original)
                            ?: run { fallbackAudioOption = PlaybackAudioOption.Original }
                        optionPanel = null
                    },
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(48.dp)
                    .testTag(playerOptionDialogTag()),
            )
            PlayerOptionPanel.Subtitle -> PlayerOptionDialog(
                title = "Untertitel",
                options = listOf(
                    PlayerOptionItem("subtitle-off", "Aus", subtitleOption == PlaybackSubtitleOption.Off) {
                        playerController?.selectSubtitle(PlaybackSubtitleOption.Off)
                            ?: run { fallbackSubtitleOption = PlaybackSubtitleOption.Off }
                        optionPanel = null
                    },
                    PlayerOptionItem("subtitle-system", "Systemstandard", subtitleOption == PlaybackSubtitleOption.SystemDefault) {
                        playerController?.selectSubtitle(PlaybackSubtitleOption.SystemDefault)
                            ?: run { fallbackSubtitleOption = PlaybackSubtitleOption.SystemDefault }
                        optionPanel = null
                    },
                    PlayerOptionItem("subtitle-de", "Deutsch", subtitleOption == PlaybackSubtitleOption.German) {
                        playerController?.selectSubtitle(PlaybackSubtitleOption.German)
                            ?: run { fallbackSubtitleOption = PlaybackSubtitleOption.German }
                        optionPanel = null
                    },
                    PlayerOptionItem("subtitle-en", "Englisch", subtitleOption == PlaybackSubtitleOption.English) {
                        playerController?.selectSubtitle(PlaybackSubtitleOption.English)
                            ?: run { fallbackSubtitleOption = PlaybackSubtitleOption.English }
                        optionPanel = null
                    },
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(48.dp)
                    .testTag(playerOptionDialogTag()),
            )
            PlayerOptionPanel.AspectRatio -> PlayerOptionDialog(
                title = "Bildformat",
                options = listOf(
                    PlayerOptionItem("aspect-fit", "Anpassen", aspectRatioMode == PlaybackAspectRatioMode.Fit) {
                        playerController?.selectAspectRatio(PlaybackAspectRatioMode.Fit)
                            ?: run { fallbackAspectRatioMode = PlaybackAspectRatioMode.Fit }
                        optionPanel = null
                    },
                    PlayerOptionItem("aspect-fill", "Ausfüllen", aspectRatioMode == PlaybackAspectRatioMode.Fill) {
                        playerController?.selectAspectRatio(PlaybackAspectRatioMode.Fill)
                            ?: run { fallbackAspectRatioMode = PlaybackAspectRatioMode.Fill }
                        optionPanel = null
                    },
                    PlayerOptionItem("aspect-zoom", "Zoom", aspectRatioMode == PlaybackAspectRatioMode.Zoom) {
                        playerController?.selectAspectRatio(PlaybackAspectRatioMode.Zoom)
                            ?: run { fallbackAspectRatioMode = PlaybackAspectRatioMode.Zoom }
                        optionPanel = null
                    },
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(48.dp)
                    .testTag(playerOptionDialogTag()),
            )
            null -> Unit
        }

        if (controllerState?.isReconnecting == true) {
            BodyText(
                text = "Verbindung wird wiederhergestellt...",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color(0xCC071A2A))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag(playerReconnectHintTag()),
                maxLines = 1,
            )
        }

        if (controllerState?.status == PlaybackStatus.Error) {
            PlaybackErrorDialog(
                message = controllerState?.error?.message ?: "Wiedergabe konnte nicht gestartet werden.",
                retryFocusRequester = errorRetryFocusRequester,
                onRetry = {
                    request?.let { playerController?.play(it) }
                },
                onChooseAnotherChannel = {
                    stopPlaybackOnce()
                    onChooseAnotherChannel()
                },
                onClose = {
                    stopPlaybackOnce()
                    onClose()
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(48.dp)
                    .testTag(playerErrorDialogTag()),
            )
        }

        if (showAutoNextPanel) {
            AutoNextPanel(
                title = if (countdownSeconds != null && autoNextEnabled) {
                    "N\u00e4chste Folge in $countdownSeconds"
                } else {
                    "N\u00e4chste Folge abspielen"
                },
                nextEpisodeTitle = nextEpisodeTitle.orEmpty(),
                onPlayNextEpisode = ::playNextEpisodeOnce,
                onBack = ::returnFromAutoNext,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 28.dp)
                    .testTag(playerAutoNextPanelTag()),
            )
        }
    }
}

fun playerRootTag(): String = "player-root"
fun playerOverlayTag(): String = "player-overlay"
fun playerHiddenOverlayTag(): String = "player-hidden-overlay"
fun playerHiddenOverlayActionTag(): String = "player-hidden-overlay-action"
fun playerErrorDialogTag(): String = "player-error-dialog"
fun playerErrorRetryTag(): String = "player-error-retry"
fun playerErrorChooseAnotherTag(): String = "player-error-choose-another"
fun playerErrorCloseTag(): String = "player-error-close"
fun playerLiveEdgeTag(): String = "player-live-edge"
fun playerReconnectHintTag(): String = "player-reconnect-hint"
fun playerAutoNextPanelTag(): String = "player-auto-next-panel"
fun playerAutoNextPlayTag(): String = "player-auto-next-play"
fun playerAutoNextBackTag(): String = "player-auto-next-back"
fun playerAudioActionTag(): String = "player-audio-action"
fun playerSubtitleActionTag(): String = "player-subtitle-action"
fun playerAspectActionTag(): String = "player-aspect-action"
fun playerOptionDialogTag(): String = "player-option-dialog"
fun playerOptionTag(key: String): String = "player-option-$key"

private enum class PlayerOptionPanel { Audio, Subtitle, AspectRatio }

private data class PlayerOptionItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun PlayerOptionDialog(
    title: String,
    options: List<PlayerOptionItem>,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
    val selectedFocusRequester = remember(title, selectedIndex) { FocusRequester() }

    LaunchedEffect(title, selectedIndex) {
        selectedFocusRequester.requestFocus()
    }

    VivicastContentCard(modifier = modifier.fillMaxWidth(0.36f), contentPadding = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
            SectionTitle(title)
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                options.forEachIndexed { index, option ->
                    ActionPill(
                        label = option.label,
                        selected = option.selected,
                        modifier = Modifier
                            .then(if (index == selectedIndex) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            .testTag(playerOptionTag(option.key)),
                        onClick = option.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoNextPanel(
    title: String,
    nextEpisodeTitle: String,
    onPlayNextEpisode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        playFocusRequester.requestFocus()
    }

    VivicastContentCard(modifier = modifier.fillMaxWidth(0.45f), contentPadding = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
            SectionTitle(title)
            BodyText(nextEpisodeTitle, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                ActionPill(
                    label = title,
                    modifier = Modifier
                        .focusRequester(playFocusRequester)
                        .testTag(playerAutoNextPlayTag()),
                    onClick = onPlayNextEpisode,
                )
                ActionPill(
                    label = "Zur\u00fcck",
                    modifier = Modifier.testTag(playerAutoNextBackTag()),
                    onClick = onBack,
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorDialog(
    message: String,
    retryFocusRequester: FocusRequester,
    onRetry: () -> Unit,
    onChooseAnotherChannel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VivicastContentCard(modifier = modifier.fillMaxWidth(0.45f), contentPadding = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
            SectionTitle("Playback-Fehler")
            BodyText(message, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                ActionPill(
                    label = "Erneut",
                    modifier = Modifier
                        .focusRequester(retryFocusRequester)
                        .testTag(playerErrorRetryTag()),
                    onClick = onRetry,
                )
                ActionPill(
                    label = "Anderen Sender wählen",
                    modifier = Modifier.testTag(playerErrorChooseAnotherTag()),
                    onClick = onChooseAnotherChannel,
                )
                ActionPill(
                    label = "Schließen",
                    modifier = Modifier.testTag(playerErrorCloseTag()),
                    onClick = onClose,
                )
            }
        }
    }
}

private const val SEEK_STEP_MILLIS = 30_000L
private const val OVERLAY_AUTO_HIDE_MILLIS = 5_000L

private fun androidx.compose.ui.input.key.KeyEvent.isPrimaryActionKey(): Boolean =
    key == Key.Enter || key == Key.NumPadEnter || key == Key.DirectionCenter

private fun VivicastPlayerState.progressPercent(): Int {
    if (durationMillis <= 0L) return 0
    return ((positionMillis.coerceAtLeast(0L) * 100L) / durationMillis).coerceIn(0L, 100L).toInt()
}

private fun VivicastPlayerState.badges(): List<String> =
    if (isTimeshiftEnabled) {
        listOf("Timeshift", "${timeshiftWindowMillis / 60_000L} min")
    } else {
        emptyList()
    }

private fun VivicastPlayerState.autoNextRemainingSeconds(configuredSeconds: Int): Int? {
    if (status != PlaybackStatus.Playing || request?.mediaType != PlaybackMediaType.Episode || durationMillis <= 0L) {
        return null
    }
    val windowMillis = configuredSeconds.validAutoNextCountdownSeconds() * 1_000L
    val remainingMillis = (durationMillis - positionMillis).coerceAtLeast(0L)
    if (remainingMillis == 0L || remainingMillis > windowMillis) return null
    return ((remainingMillis + 999L) / 1_000L).coerceAtLeast(1L).toInt()
}

private fun Int.validAutoNextCountdownSeconds(): Int =
    when (this) {
        5, 10, 15, 30 -> this
        else -> 10
    }

private fun Long.formatOffset(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}:${seconds.toString().padStart(2, '0')} min"
}

private fun PlaybackStatus.statusLabel(state: VivicastPlayerState?): String =
    when (this) {
        PlaybackStatus.Idle -> "Bereit"
        PlaybackStatus.Starting -> "Startet"
        PlaybackStatus.Playing -> when {
            state?.isTimeshiftEnabled == true && !state.isAtLiveEdge -> "Timeshift"
            state?.request?.mediaType == PlaybackMediaType.Channel -> "Live"
            else -> "Wiedergabe"
        }
        PlaybackStatus.Paused -> "Pausiert"
        PlaybackStatus.Ended -> "Beendet"
        PlaybackStatus.Error -> "Fehler"
        PlaybackStatus.Released -> "Beendet"
    }
