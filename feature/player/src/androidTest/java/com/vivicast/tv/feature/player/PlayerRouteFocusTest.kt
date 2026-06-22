package com.vivicast.tv.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackError
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.PlaybackTimeshiftConfig
import com.vivicast.tv.core.player.PlaybackTimeshiftStorage
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
    fun overlayStartsWithTimelineFocusBackHidesAndOkRestores() {
        var closed = false

        compose.setContent {
            PlayerRoute(onClose = { closed = true })
        }

        compose.onNodeWithTag(playerOverlayTag()).assertIsDisplayed()
        compose.onNodeWithTag(playerTimelineTag()).assertIsFocused()

        pressBack()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerHiddenOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(playerOverlayTag()).assertCountEquals(0)
        compose.onNodeWithTag(playerHiddenOverlayActionTag()).assertIsFocused()
        assertFalse(closed)

        compose.onNodeWithTag(playerHiddenOverlayActionTag()).performKeyInput {
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

        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerHiddenOverlayTag()).fetchSemanticsNodes().isNotEmpty()
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

        compose.onNodeWithTag(playerTimelineTag()).performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
        }

        compose.runOnIdle {
            assertEquals(listOf(-30_000L, 30_000L), controller.seekDeltas)
        }
    }

    @Test
    fun closingPlayerStopsController() {
        val controller = FakePlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        pressBack()
        pressBack()

        compose.runOnIdle {
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

        compose.onNodeWithTag(playerTimelineTag()).performKeyInput {
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
    fun timeshiftShowsLiveEdgeActionWhenBehindLive() {
        val controller = TimeshiftPlayerController()

        compose.setContent {
            PlayerRoute(playerController = controller)
        }

        compose.onNodeWithTag(playerLiveEdgeTag()).assertIsDisplayed()
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
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
                    timeshift = PlaybackTimeshiftConfig(
                        storage = PlaybackTimeshiftStorage.Automatic,
                        windowMillis = 30 * 60_000L,
                    ),
                ),
                positionMillis = 30 * 60_000L - 30_000L,
                durationMillis = 30 * 60_000L,
                liveEdgeOffsetMillis = 30_000L,
                timeshiftWindowMillis = 30 * 60_000L,
                timeshiftStorage = PlaybackTimeshiftStorage.Automatic,
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
}
