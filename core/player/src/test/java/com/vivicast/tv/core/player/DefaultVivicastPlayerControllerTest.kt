package com.vivicast.tv.core.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultVivicastPlayerControllerTest {
    @Test
    fun startingNewRequestCancelsPreviousStartAndPlaysLatestOnly() = runBlocking {
        val engine = BlockingPlaybackEngine()
        val controller = testController(engine)

        val stateJob = launch {
            controller.state.drop(1).first { it.status == PlaybackStatus.Playing && it.request?.playbackId == "second" }
        }

        controller.play(TEST_REQUEST)
        withTimeout(5_000) { engine.awaitStarted("first") }

        controller.play(SECOND_REQUEST)
        withTimeout(5_000) { engine.awaitCancelled("first") }
        withTimeout(5_000) { engine.awaitStarted("second") }
        engine.complete("second")

        stateJob.cancelAndJoin()
        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
        assertEquals("second", controller.state.value.request?.playbackId)
        assertEquals(listOf("first", "second"), engine.startedIds)
    }

    @Test
    fun startAttemptsFiveTimesTotalBeforeError() = runBlocking {
        val engine = AlwaysFailingPlaybackEngine()
        val controller = testController(engine)

        controller.play(TEST_REQUEST)

        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Error }
        }

        assertEquals(5, engine.startAttempts)
        assertEquals(5, controller.state.value.error?.retryCount)
        assertEquals("first", controller.state.value.error?.playbackId)
    }

    @Test
    fun defaultRetryDelaysMatchDocumentedSequence() {
        val delays = DefaultVivicastPlayerController.DEFAULT_RETRY_DELAYS_MILLIS

        assertEquals(500L, delays.retryDelayAfterFailedAttempt(1))
        assertEquals(1_000L, delays.retryDelayAfterFailedAttempt(2))
        assertEquals(2_000L, delays.retryDelayAfterFailedAttempt(3))
        assertEquals(4_000L, delays.retryDelayAfterFailedAttempt(4))
        assertEquals(0L, delays.retryDelayAfterFailedAttempt(5))
    }

    @Test
    fun releaseStopsPlaybackAndIgnoresFutureStarts() = runBlocking {
        val engine = BlockingPlaybackEngine()
        val controller = testController(engine)

        controller.play(TEST_REQUEST)
        withTimeout(5_000) { engine.awaitStarted("first") }

        controller.release()
        controller.play(SECOND_REQUEST)

        assertTrue(engine.released)
        assertEquals(PlaybackStatus.Released, controller.state.value.status)
        assertEquals(listOf("first"), engine.startedIds)
    }

    @Test
    fun startPublishesInitialPositionAndDuration() = runBlocking {
        val engine = BlockingPlaybackEngine().apply {
            currentPositionMillis = 12_000L
            durationMillis = 120_000L
        }
        val controller = testController(engine)

        controller.play(TEST_REQUEST)
        withTimeout(5_000) { engine.awaitStarted("first") }
        engine.complete("first")
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        assertEquals(12_000L, controller.state.value.positionMillis)
        assertEquals(120_000L, controller.state.value.durationMillis)
    }

    @Test
    fun playbackEndedPublishesEndedStateWithFullDurationPosition() = runBlocking {
        val engine = EventPlaybackEngine().apply {
            currentPositionMillis = 118_000L
            durationMillis = 120_000L
        }
        val controller = testController(engine)

        controller.play(MOVIE_REQUEST)
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        engine.emitPlaybackEnded("first")

        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Ended }
        }
        assertEquals(MOVIE_REQUEST, controller.state.value.request)
        assertEquals(120_000L, controller.state.value.positionMillis)
        assertEquals(120_000L, controller.state.value.durationMillis)
    }

    @Test
    fun playbackEndedIgnoresStalePlaybackIds() = runBlocking {
        val engine = EventPlaybackEngine().apply {
            currentPositionMillis = 118_000L
            durationMillis = 120_000L
        }
        val controller = testController(engine)

        controller.play(MOVIE_REQUEST)
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        engine.emitPlaybackEnded("stale")

        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
    }

    @Test
    fun playbackErrorReconnectsRunningRequest() = runBlocking {
        val engine = ReconnectPlaybackEngine()
        val controller = testController(engine)

        controller.play(TEST_REQUEST)
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        engine.emitPlaybackError()

        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing && engine.startAttempts >= 2 }
        }
        assertEquals(2, engine.startAttempts)
        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
    }

    @Test
    fun playbackErrorPublishesReconnectStateWhileReconnectRuns() = runBlocking {
        val engine = BlockingReconnectPlaybackEngine()
        val controller = testController(engine)

        controller.play(TEST_REQUEST)
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        engine.emitPlaybackError()

        withTimeout(5_000) {
            controller.state.first { it.isReconnecting }
        }
        assertEquals(PlaybackStatus.Starting, controller.state.value.status)
        assertTrue(controller.state.value.isReconnecting)

        engine.completeReconnect()
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }
        Unit
    }

    @Test
    fun playbackErrorExhaustsReconnectAttemptsBeforeError() = runBlocking {
        val engine = ReconnectPlaybackEngine(failReconnects = true)
        val controller = testController(engine)

        controller.play(TEST_REQUEST)
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        engine.emitPlaybackError()

        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Error }
        }
        assertEquals(6, engine.startAttempts)
        assertEquals(5, controller.state.value.error?.retryCount)
    }

    @Test
    fun liveChannelUsesNativeWindowAtLiveEdge() = runBlocking {
        val engine = BlockingPlaybackEngine().apply {
            durationMillis = 30 * 60_000L
            currentPositionMillis = 30 * 60_000L
        }
        val controller = testController(engine)

        controller.play(TIMESHIFT_REQUEST)
        withTimeout(5_000) { engine.awaitStarted("timeshift") }
        engine.complete("timeshift")
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        // Native DVR window drives the timeshift fields; not a fabricated fixed window.
        assertEquals(30 * 60_000L, controller.state.value.timeshiftWindowMillis)
        assertEquals(30 * 60_000L, controller.state.value.durationMillis)
        assertEquals(0L, controller.state.value.liveEdgeOffsetMillis)
        assertTrue(controller.state.value.isTimeshiftEnabled)
        assertTrue(controller.state.value.isAtLiveEdge)
    }

    @Test
    fun nonSeekableChannelHasNoTimeshift() = runBlocking {
        val engine = BlockingPlaybackEngine().apply { isCurrentSeekable = false }
        val controller = testController(engine)

        controller.play(TIMESHIFT_REQUEST)
        withTimeout(5_000) { engine.awaitStarted("timeshift") }
        engine.complete("timeshift")
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        assertEquals(0L, controller.state.value.timeshiftWindowMillis)
        assertFalse(controller.state.value.isTimeshiftEnabled)
    }

    @Test
    fun liveChannelSeekTracksNativeOffsetAndReturnsToLiveEdge() = runBlocking {
        val engine = BlockingPlaybackEngine().apply {
            durationMillis = 30 * 60_000L
            currentPositionMillis = 30 * 60_000L
        }
        val controller = testController(engine)

        controller.play(TIMESHIFT_REQUEST)
        withTimeout(5_000) { engine.awaitStarted("timeshift") }
        engine.complete("timeshift")
        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Playing }
        }

        controller.seekBy(-30_000L)
        assertEquals(listOf(-30_000L), engine.seekDeltas)
        assertEquals(30 * 60_000L - 30_000L, controller.state.value.positionMillis)
        assertEquals(30_000L, controller.state.value.liveEdgeOffsetMillis)
        assertFalse(controller.state.value.isAtLiveEdge)

        controller.seekToLiveEdge()
        assertEquals(1, engine.liveEdgeSeeks)
        assertEquals(30 * 60_000L, controller.state.value.positionMillis)
        assertEquals(0L, controller.state.value.liveEdgeOffsetMillis)
        assertTrue(controller.state.value.isAtLiveEdge)
    }

    private fun testController(engine: PlaybackEngine): DefaultVivicastPlayerController =
        DefaultVivicastPlayerController(
            engine = engine,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined,
            retryDelaysMillis = emptyList(),
        )
}

private class BlockingPlaybackEngine : PlaybackEngine {
    private val startSignals = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val cancelSignals = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val completions = mutableMapOf<String, CompletableDeferred<Unit>>()
    val startedIds = mutableListOf<String>()
    var released = false
    override var currentPositionMillis = 0L
    override var durationMillis = 0L
    override var isCurrentSeekable = true
    val seekDeltas = mutableListOf<Long>()
    var liveEdgeSeeks = 0

    override suspend fun start(request: PlaybackRequest) {
        startedIds += request.playbackId
        startSignals.getOrPut(request.playbackId) { CompletableDeferred() }.complete(Unit)
        try {
            completions.getOrPut(request.playbackId) { CompletableDeferred() }.await()
        } finally {
            if (!completions.getValue(request.playbackId).isCompleted) {
                cancelSignals.getOrPut(request.playbackId) { CompletableDeferred() }.complete(Unit)
            }
        }
    }

    fun complete(playbackId: String) {
        completions.getOrPut(playbackId) { CompletableDeferred() }.complete(Unit)
    }

    suspend fun awaitStarted(playbackId: String) {
        startSignals.getOrPut(playbackId) { CompletableDeferred() }.await()
    }

    suspend fun awaitCancelled(playbackId: String) {
        cancelSignals.getOrPut(playbackId) { CompletableDeferred() }.await()
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekBy(deltaMillis: Long) {
        seekDeltas += deltaMillis
        currentPositionMillis = (currentPositionMillis + deltaMillis).coerceIn(0L, durationMillis)
    }
    override fun seekToLiveEdge() {
        liveEdgeSeeks += 1
        currentPositionMillis = durationMillis
    }
    override fun stop() = Unit
    override fun release() {
        released = true
    }
}

private class AlwaysFailingPlaybackEngine : PlaybackEngine {
    var startAttempts = 0
    override val currentPositionMillis = 0L
    override val durationMillis = 0L

    override suspend fun start(request: PlaybackRequest) {
        startAttempts += 1
        error("start failed")
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekBy(deltaMillis: Long) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

private class ReconnectPlaybackEngine(
    private val failReconnects: Boolean = false,
) : PlaybackEngine {
    private val mutablePlaybackErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    var startAttempts = 0
    override var currentPositionMillis = 15_000L
    override var durationMillis = 120_000L
    override val playbackErrors: Flow<Throwable> = mutablePlaybackErrors

    override suspend fun start(request: PlaybackRequest) {
        startAttempts += 1
        if (failReconnects && startAttempts > 1) {
            error("reconnect failed")
        }
    }

    fun emitPlaybackError() {
        mutablePlaybackErrors.tryEmit(IllegalStateException("stream aborted"))
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekBy(deltaMillis: Long) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

private class BlockingReconnectPlaybackEngine : PlaybackEngine {
    private val mutablePlaybackErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    private val reconnectCompletion = CompletableDeferred<Unit>()
    var startAttempts = 0
    override var currentPositionMillis = 15_000L
    override var durationMillis = 120_000L
    override val playbackErrors: Flow<Throwable> = mutablePlaybackErrors

    override suspend fun start(request: PlaybackRequest) {
        startAttempts += 1
        if (startAttempts > 1) {
            reconnectCompletion.await()
        }
    }

    fun emitPlaybackError() {
        mutablePlaybackErrors.tryEmit(IllegalStateException("stream aborted"))
    }

    fun completeReconnect() {
        reconnectCompletion.complete(Unit)
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekBy(deltaMillis: Long) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

private class EventPlaybackEngine : PlaybackEngine {
    private val mutablePlaybackEnded = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override var currentPositionMillis = 0L
    override var durationMillis = 0L
    override val playbackEnded: Flow<String> = mutablePlaybackEnded

    override suspend fun start(request: PlaybackRequest) = Unit

    fun emitPlaybackEnded(playbackId: String) {
        mutablePlaybackEnded.tryEmit(playbackId)
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekBy(deltaMillis: Long) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

private val TEST_REQUEST = PlaybackRequest(
    playbackId = "first",
    providerId = "provider-a",
    mediaId = "channel-a",
    mediaType = PlaybackMediaType.Channel,
    title = "Channel A",
    streamUrl = "https://stream.example/channel-a.m3u8",
    seekable = false,
)

private val MOVIE_REQUEST = TEST_REQUEST.copy(
    mediaId = "movie-a",
    mediaType = PlaybackMediaType.Movie,
    title = "Movie A",
    seekable = true,
)

private val SECOND_REQUEST = TEST_REQUEST.copy(
    playbackId = "second",
    mediaId = "channel-b",
    title = "Channel B",
    streamUrl = "https://stream.example/channel-b.m3u8",
)

private val TIMESHIFT_REQUEST = TEST_REQUEST.copy(
    playbackId = "timeshift",
    seekable = true,
)
