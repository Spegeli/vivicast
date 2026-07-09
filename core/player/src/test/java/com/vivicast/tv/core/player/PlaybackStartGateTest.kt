package com.vivicast.tv.core.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Guards the async→suspend bridge that makes [PlaybackEngine.start] honour its contract: a ready
 * signal completes start, an error is rethrown so the retry logic engages, and a silent endpoint
 * trips the timeout instead of hanging (the root cause of the old reconnect loop).
 */
class PlaybackStartGateTest {

    @Test
    fun readySignalCompletesStart() = runBlocking {
        val gate = PlaybackStartGate()
        val pending = gate.arm()
        gate.onReady()
        pending.awaitStartedOrThrow(timeoutMillis = 1_000L) // returns without throwing
    }

    @Test
    fun errorSignalIsRethrown() {
        val gate = PlaybackStartGate()
        val pending = gate.arm()
        gate.onError(IllegalStateException("dead endpoint"))
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { pending.awaitStartedOrThrow(timeoutMillis = 1_000L) }
        }
        assertEquals("dead endpoint", thrown.message)
    }

    @Test
    fun silentEndpointTimesOut() {
        val gate = PlaybackStartGate()
        val pending = gate.arm()
        assertThrows(PlaybackStartTimeoutException::class.java) {
            runBlocking { pending.awaitStartedOrThrow(timeoutMillis = 50L) }
        }
    }
}
