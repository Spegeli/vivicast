package com.vivicast.tv.worker

import com.vivicast.tv.core.cache.MediaCacheCleanupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MaintenanceRefreshOrchestratorTest {
    // The global periodic refresh covers logos + cache only; playlists and EPG sources refresh via their
    // own per-item workers, so the orchestrator has no playlist or EPG stage.

    @Test
    fun refreshPropagatesCancellationAndStopsRemainingStages() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = MaintenanceRefreshOrchestrator(
            logoRefresher = object : LogoRefresher {
                // Simulates WorkManager stopping the worker mid-refresh.
                override suspend fun refreshLogos(): LogoRefreshResult { throw CancellationException("stopped") }
            },
            cacheCleaner = object : CacheCleaner {
                override suspend fun cleanup(): MediaCacheCleanupResult { calls += "cache"; return EMPTY_CLEANUP }
            },
            diagnostics = RecordingRefreshDiagnostics(),
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
        val diagnostics = RecordingRefreshDiagnostics()
        val orchestrator = MaintenanceRefreshOrchestrator(
            logoRefresher = object : LogoRefresher {
                override suspend fun refreshLogos(): LogoRefreshResult { calls += "refresh-logos"; return LogoRefreshResult() }
            },
            cacheCleaner = object : CacheCleaner {
                override suspend fun cleanup(): MediaCacheCleanupResult { calls += "cache-cleanup"; return EMPTY_CLEANUP }
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

private val EMPTY_CLEANUP = MediaCacheCleanupResult(removedFiles = 0, removedBytes = 0, remainingBytes = 0)

private class RecordingRefreshDiagnostics : RefreshDiagnostics {
    val events = mutableListOf<RefreshDiagnosticEvent>()
    override fun record(event: RefreshDiagnosticEvent) { events += event }
}
