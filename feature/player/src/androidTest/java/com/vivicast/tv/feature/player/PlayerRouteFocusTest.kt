package com.vivicast.tv.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.vivicast.tv.core.player.PlaybackAspectRatioMode
import com.vivicast.tv.core.player.PlaybackAudioOption
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackError
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.PlaybackSubtitleOption
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.core.designsystem.playerTimelineTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PlayerRouteFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlayStartsHiddenOkOpensBackHidesAndOkRestores() {
        var closed = false

        compose.setContent {
            PlayerRoute(onClose = { closed = true })
        }

        compose.onAllNodesWithTag(playerOverlayTag()).assertCountEquals(0)
        compose.onAllNodesWithTag(playerHiddenOverlayTag()).assertCountEquals(0)

        compose.onNodeWithTag(playerRootTag()).performKeyInput {
            pressKey(Key.Enter)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(playerTimelineTag()).assertIsFocused()

        pressBack()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isEmpty()
        }
        compose.onAllNodesWithTag(playerHiddenOverlayTag()).assertCountEquals(0)
        assertFalse(closed)

        compose.onNodeWithTag(playerRootTag()).performKeyInput {
            pressKey(Key.Enter)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(playerTimelineTag()).assertIsFocused()
    }

    @Test
    fun backClosesPlayerAfterOverlayIsHidden() {
        var closed = false

        compose.setContent {
            PlayerRoute(onClose = { closed = true })
        }

        compose.onNodeWithTag(playerRootTag()).performKeyInput {
            pressKey(Key.Enter)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isEmpty()
        }

        pressBack()

        compose.waitUntil(timeoutMillis = 5_000) { closed }
    }

    @Test
    fun timelineOkPausesAndResumesControllerPlayback() {
        val controller = FakePlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        openOverlay()
        compose.onNodeWithTag(playerTimelineTag()).performKeyInput {
            pressKey(Key.Enter)
        }
        compose.runOnIdle {
            assertEquals(1, controller.pauseCount)
            assertEquals(PlaybackStatus.Paused, controller.state.value.status)
        }

        compose.onNodeWithTag(playerTimelineTag()).performKeyInput {
            pressKey(Key.Enter)
        }
        compose.runOnIdle {
            assertEquals(1, controller.resumeCount)
            assertEquals(PlaybackStatus.Playing, controller.state.value.status)
        }
    }

    @Test
    fun timelineLeftRightSeekThroughControllerWhenSeekable() {
        val controller = FakePlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        openOverlay()
        compose.onNodeWithTag(playerTimelineTag()).performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
        }

        compose.runOnIdle {
            assertEquals(listOf(-30_000L, 30_000L), controller.seekDeltas)
        }
    }

    @Test
    fun overlayActionsSelectSessionPlaybackOptions() {
        val controller = FakePlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        openOverlay()
        compose.onNodeWithTag(playerAudioActionTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(playerOptionDialogTag()).assertIsDisplayed()
        compose.onNodeWithTag(playerOptionTag("audio-de")).performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithTag(playerSubtitleActionTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(playerOptionDialogTag()).assertIsDisplayed()
        compose.onNodeWithTag(playerOptionTag("subtitle-en")).performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithTag(playerAspectActionTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(playerOptionDialogTag()).assertIsDisplayed()
        compose.onNodeWithTag(playerOptionTag("aspect-fill")).performSemanticsAction(SemanticsActions.OnClick)

        compose.runOnIdle {
            assertEquals(listOf(PlaybackAudioOption.German), controller.audioSelections)
            assertEquals(listOf(PlaybackSubtitleOption.English), controller.subtitleSelections)
            assertEquals(listOf(PlaybackAspectRatioMode.Fill), controller.aspectRatioSelections)
            assertEquals(PlaybackAudioOption.German, controller.state.value.audioOption)
            assertEquals(PlaybackSubtitleOption.English, controller.state.value.subtitleOption)
            assertEquals(PlaybackAspectRatioMode.Fill, controller.state.value.aspectRatioMode)
        }
    }

    @Test
    fun closingPlayerStopsController() {
        val controller = FakePlayerController()
        var stateBeforeStop: VivicastPlayerState? = null

        compose.setContent {
            PlayerRoute(
                playerController = controller,
                onBeforeStop = { stateBeforeStop = it },
            )
        }

        openOverlay()
        pressBack()
        pressBack()

        compose.runOnIdle {
            assertEquals(PlaybackStatus.Playing, stateBeforeStop?.status)
            assertEquals("movie-1", stateBeforeStop?.request?.mediaId)
            assertEquals(1, controller.stopCount)
            assertEquals(PlaybackStatus.Idle, controller.state.value.status)
        }
    }

    @Test
    fun channelKeysTriggerZappingCallbacks() {
        var channelUpCount = 0
        var channelDownCount = 0

        compose.setContent {
            PlayerRoute(
                onChannelUp = { channelUpCount += 1 },
                onChannelDown = { channelDownCount += 1 },
            )
        }

        compose.onNodeWithTag(playerRootTag()).performKeyInput {
            pressKey(Key.ChannelUp)
            pressKey(Key.ChannelDown)
        }

        compose.runOnIdle {
            assertEquals(1, channelUpCount)
            assertEquals(1, channelDownCount)
        }
    }

    @Test
    fun errorDialogFocusesRetryAndRetriesRequest() {
        val controller = ErrorPlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        compose.onNodeWithTag(playerErrorDialogTag()).assertIsDisplayed()
        compose.onNodeWithTag(playerErrorRetryTag()).assertIsFocused()
        compose.onNodeWithTag(playerErrorRetryTag()).performKeyInput {
            pressKey(Key.Enter)
        }

        compose.runOnIdle {
            assertEquals(1, controller.playCount)
            assertEquals("movie-1", controller.lastRequest?.mediaId)
        }
    }

    @Test
    fun reconnectHintIsVisibleWithoutErrorDialog() {
        val controller = ReconnectingPlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        compose.onNodeWithTag(playerReconnectHintTag()).assertIsDisplayed()
        compose.onAllNodesWithTag(playerErrorDialogTag()).assertCountEquals(0)
    }

    @Test
    fun endedEpisodeShowsManualAutoNextPanel() {
        val controller = EndedEpisodePlayerController()

        compose.setContent {
            PlayerRoute(
                playerController = controller,
                autoNextEnabled = false,
                nextEpisodeTitle = "Folge 2",
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerAutoNextPanelTag()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(playerAutoNextPlayTag()).assertIsFocused()
    }

    @Test
    fun endedEpisodeAutoNextStartsNextWhenEnabled() {
        val controller = EndedEpisodePlayerController()
        var playNextCount = 0

        compose.setContent {
            PlayerRoute(
                playerController = controller,
                autoNextEnabled = true,
                nextEpisodeTitle = "Folge 2",
                onPlayNextEpisode = { playNextCount += 1 },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) { playNextCount == 1 }
    }

    @Test
    fun timeshiftShowsLiveEdgeActionWhenBehindLive() {
        val controller = TimeshiftPlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        openOverlay()
        compose.onNodeWithTag(playerLiveEdgeTag()).assertIsDisplayed()
    }

    @Test
    fun overlayAutoHidesAfterFiveSecondsOfInactivity() {
        compose.setContent {
            PlayerRoute()
        }

        compose.onNodeWithTag(playerRootTag()).performKeyInput {
            pressKey(Key.Enter)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }

        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(5_100L)
        compose.waitForIdle()

        compose.onAllNodesWithTag(playerOverlayTag()).assertCountEquals(0)
        compose.mainClock.autoAdvance = true
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun openOverlay() {
        compose.onNodeWithTag(playerRootTag()).performKeyInput {
            pressKey(Key.Enter)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            try {
                compose.onNodeWithTag(playerTimelineTag()).assertIsFocused()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private class FakePlayerController : VivicastPlayerController {
        private val mutableState = MutableStateFlow(
            VivicastPlayerState(
                status = PlaybackStatus.Playing,
                request = PlaybackRequest(
                    playbackId = "playback-1",
                    providerId = "provider-1",
                    mediaId = "movie-1",
                    mediaType = PlaybackMediaType.Movie,
                    title = "Controller Movie",
                    streamUrl = "https://stream.example/movie.mp4",
                    seekable = true,
                ),
                positionMillis = 30_000L,
                durationMillis = 120_000L,
            ),
        )
        var pauseCount = 0
        var resumeCount = 0
        var stopCount = 0
        val seekDeltas = mutableListOf<Long>()
        val audioSelections = mutableListOf<PlaybackAudioOption>()
        val subtitleSelections = mutableListOf<PlaybackSubtitleOption>()
        val aspectRatioSelections = mutableListOf<PlaybackAspectRatioMode>()

        override val state: StateFlow<VivicastPlayerState> = mutableState

        override fun play(request: PlaybackRequest) = Unit

        override fun pause() {
            pauseCount += 1
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.Paused)
        }

        override fun resume() {
            resumeCount += 1
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.Playing)
        }

        override fun seekBy(deltaMillis: Long) {
            seekDeltas += deltaMillis
        }

        override fun seekToLiveEdge() = Unit

        override fun selectAudio(option: PlaybackAudioOption) {
            audioSelections += option
            mutableState.value = mutableState.value.copy(audioOption = option)
        }

        override fun selectSubtitle(option: PlaybackSubtitleOption) {
            subtitleSelections += option
            mutableState.value = mutableState.value.copy(subtitleOption = option)
        }

        override fun selectAspectRatio(mode: PlaybackAspectRatioMode) {
            aspectRatioSelections += mode
            mutableState.value = mutableState.value.copy(aspectRatioMode = mode)
        }

        override fun stop() {
            stopCount += 1
            mutableState.value = VivicastPlayerState(status = PlaybackStatus.Idle)
        }

        override fun release() = Unit
    }

    private class ErrorPlayerController : VivicastPlayerController {
        private val failedRequest = PlaybackRequest(
            playbackId = "playback-1",
            providerId = "provider-1",
            mediaId = "movie-1",
            mediaType = PlaybackMediaType.Movie,
            title = "Controller Movie",
            streamUrl = "https://stream.example/movie.mp4",
            seekable = true,
        )
        private val mutableState = MutableStateFlow(
            VivicastPlayerState(
                status = PlaybackStatus.Error,
                request = failedRequest,
                error = PlaybackError(
                    playbackId = failedRequest.playbackId,
                    retryCount = 5,
                    message = "start failed",
                ),
            ),
        )
        var playCount = 0
        var lastRequest: PlaybackRequest? = null

        override val state: StateFlow<VivicastPlayerState> = mutableState

        override fun play(request: PlaybackRequest) {
            playCount += 1
            lastRequest = request
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.Starting)
        }

        override fun pause() = Unit
        override fun resume() = Unit
        override fun seekBy(deltaMillis: Long) = Unit
        override fun seekToLiveEdge() = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class TimeshiftPlayerController : VivicastPlayerController {
        private val mutableState = MutableStateFlow(
            VivicastPlayerState(
                status = PlaybackStatus.Playing,
                request = PlaybackRequest(
                    playbackId = "playback-1",
                    providerId = "provider-1",
                    mediaId = "channel-1",
                    mediaType = PlaybackMediaType.Channel,
                    title = "Controller Channel",
                    streamUrl = "https://stream.example/channel.m3u8",
                    seekable = true,
                ),
                positionMillis = 30 * 60_000L - 30_000L,
                durationMillis = 30 * 60_000L,
                liveEdgeOffsetMillis = 30_000L,
                timeshiftWindowMillis = 30 * 60_000L,
            ),
        )
        var seekToLiveEdgeCount = 0

        override val state: StateFlow<VivicastPlayerState> = mutableState

        override fun play(request: PlaybackRequest) = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun seekBy(deltaMillis: Long) = Unit
        override fun seekToLiveEdge() {
            seekToLiveEdgeCount += 1
            mutableState.value = mutableState.value.copy(
                positionMillis = 30 * 60_000L,
                liveEdgeOffsetMillis = 0L,
            )
        }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class ReconnectingPlayerController : VivicastPlayerController {
        private val mutableState = MutableStateFlow(
            VivicastPlayerState(
                status = PlaybackStatus.Starting,
                request = PlaybackRequest(
                    playbackId = "playback-1",
                    providerId = "provider-1",
                    mediaId = "channel-1",
                    mediaType = PlaybackMediaType.Channel,
                    title = "Controller Channel",
                    streamUrl = "https://stream.example/channel.m3u8",
                    seekable = false,
                ),
                isReconnecting = true,
            ),
        )

        override val state: StateFlow<VivicastPlayerState> = mutableState

        override fun play(request: PlaybackRequest) = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun seekBy(deltaMillis: Long) = Unit
        override fun seekToLiveEdge() = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class EndedEpisodePlayerController : VivicastPlayerController {
        private val mutableState = MutableStateFlow(
            VivicastPlayerState(
                status = PlaybackStatus.Ended,
                request = PlaybackRequest(
                    playbackId = "episode-playback-1",
                    providerId = "provider-1",
                    mediaId = "episode-1",
                    mediaType = PlaybackMediaType.Episode,
                    title = "Folge 1",
                    streamUrl = "https://stream.example/episode-1.mp4",
                    seekable = true,
                ),
                positionMillis = 120_000L,
                durationMillis = 120_000L,
            ),
        )

        override val state: StateFlow<VivicastPlayerState> = mutableState

        override fun play(request: PlaybackRequest) = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun seekBy(deltaMillis: Long) = Unit
        override fun seekToLiveEdge() = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
}
