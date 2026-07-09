package com.vivicast.tv.data.playback

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamReachabilityProbeTest {

    @Test
    fun reachableForSuccessStatus() = runBlocking {
        assertTrue(StreamReachabilityProbe { _, _ -> 200 }.isReachable("http://host/live.ts", null))
    }

    @Test
    fun reachableForRedirectStatus() = runBlocking {
        assertTrue(StreamReachabilityProbe { _, _ -> 302 }.isReachable("http://host/live.ts", null))
    }

    @Test
    fun notReachableForClientError() = runBlocking {
        assertFalse(StreamReachabilityProbe { _, _ -> 404 }.isReachable("http://host/live.ts", null))
    }

    @Test
    fun notReachableForServerError() = runBlocking {
        assertFalse(StreamReachabilityProbe { _, _ -> 500 }.isReachable("http://host/live.ts", null))
    }

    @Test
    fun notReachableOnConnectFailure() = runBlocking {
        assertFalse(StreamReachabilityProbe { _, _ -> null }.isReachable("http://host/live.ts", null))
    }
}
