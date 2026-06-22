package com.vivicast.tv.core.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
    fun startRetriesFiveTimesBeforeError() = runBlocking {
        val engine = AlwaysFailingPlaybackEngine()
        val controller = testController(engine)

        controller.play(TEST_REQUEST)

        withTimeout(5_000) {
            controller.state.first { it.status == PlaybackStatus.Error }
        }

        assertEquals(6, engine.startAttempts)
        assertEquals(5, controller.state.value.error?.retryCount)
        assertEquals("first", controller.state.value.error?.playbackId)
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

    private fun testController(engine: PlaybackEngine): DefaultVivicastPlayerController =
        DefaultVivicastPlayerController(
            engine = engine,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined,
            retryDelayMillis = 0L,
        )
}

private class BlockingPlaybackEngine : PlaybackEngine {
    private val startSignals = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val cancelSignals = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val completions = mutableMapOf<String, CompletableDeferred<Unit>>()
    val startedIds = mutableListOf<String>()
    var released = false

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
    override fun seekBy(deltaMillis: Long) = Unit
    override fun stop() = Unit
    override fun release() {
        released = true
    }
}

private class AlwaysFailingPlaybackEngine : PlaybackEngine {
    var startAttempts = 0

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

private val TEST_REQUEST = PlaybackRequest(
    playbackId = "first",
    providerId = "provider-a",
    mediaId = "channel-a",
    mediaType = PlaybackMediaType.Channel,
    title = "Channel A",
    streamUrl = "https://stream.example/channel-a.m3u8",
    seekable = false,
)

private val SECOND_REQUEST = TEST_REQUEST.copy(
    playbackId = "second",
    mediaId = "channel-b",
    title = "Channel B",
    streamUrl = "https://stream.example/channel-b.m3u8",
)
