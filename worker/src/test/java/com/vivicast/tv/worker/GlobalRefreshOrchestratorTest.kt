package com.vivicast.tv.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GlobalRefreshOrchestratorTest {
    // The global periodic refresh covers logos + cache only; playlists and EPG sources refresh via their
    // own per-item workers, so the orchestrator has no playlist or EPG stage.

    @Test
    fun refreshPropagatesCancellationAndStopsRemainingStages() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = GlobalRefreshOrchestrator(
            logoRefresher = object : LogoRefresher {
                // Simulates WorkManager stopping the worker mid-refresh.
                override suspend fun refreshLogos() { throw CancellationException("stopped") }
            },
            cacheCleaner = object : CacheCleaner {
                override suspend fun cleanup() { calls += "cache" }
            },
            diagnostics = InMemoryRefreshDiagnostics(),
        )

        try {
            orchestrator.refresh()
            fail("Cancellation should propagate out of refresh()")
        } catch (expected: CancellationException) {
            // Cancellation must stop the pipeline: no cache work after the cancelled logo stage.
            assertEquals(emptyList<String>(), calls)
        }
    }

    @Test
    fun refreshRunsLogosThenCache() = runBlocking {
        val calls = mutableListOf<String>()
        val diagnostics = InMemoryRefreshDiagnostics()
        val orchestrator = GlobalRefreshOrchestrator(
            logoRefresher = object : LogoRefresher {
                override suspend fun refreshLogos() { calls += "refresh-logos" }
            },
            cacheCleaner = object : CacheCleaner {
                override suspend fun cleanup() { calls += "cache-cleanup" }
            },
            diagnostics = diagnostics,
        )

        val report = orchestrator.refresh()

        assertEquals(listOf("refresh-logos", "cache-cleanup"), calls)
        assertEquals(0, report.playlistsCollected)
        assertEquals(0, report.epgSourcesCollected)
        assertEquals(RefreshDiagnosticType.RefreshCompleted, diagnostics.events.last().type)
    }
}
