package com.vivicast.tv.worker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalRefreshOrchestratorTest {
    @Test
    fun refreshRunsAdr003OrderAndDeduplicatesEpgSources() = runBlocking {
        val calls = mutableListOf<String>()
        val diagnostics = InMemoryRefreshDiagnostics()
        val orchestrator = GlobalRefreshOrchestrator(
            playlistSource = object : PlaylistRefreshSource {
                override suspend fun collectDuePlaylists(): List<PlaylistRefreshTarget> {
                    calls += "collect-playlists"
                    return listOf(PlaylistRefreshTarget("provider-a"), PlaylistRefreshTarget("provider-b"))
                }
            },
            playlistRefresher = object : PlaylistRefresher {
                override suspend fun refresh(target: PlaylistRefreshTarget): PlaylistRefreshOutcome {
                    calls += "refresh-playlist:${target.providerId}"
                    return PlaylistRefreshOutcome(target.providerId, success = true, epgSourceIds = listOf("epg-shared"))
                }
            },
            epgSourceResolver = object : EpgSourceResolver {
                override suspend fun collectRequiredSources(playlistOutcomes: List<PlaylistRefreshOutcome>): List<EpgRefreshTarget> {
                    calls += "collect-epg"
                    return playlistOutcomes.flatMap { it.epgSourceIds }.map(::EpgRefreshTarget)
                }
            },
            epgRefresher = object : EpgRefresher {
                override suspend fun refresh(target: EpgRefreshTarget): EpgRefreshOutcome {
                    calls += "refresh-epg:${target.epgSourceId}"
                    return EpgRefreshOutcome(target.epgSourceId, success = true)
                }
            },
            epgMappingApplier = object : EpgMappingApplier {
                override suspend fun applyMappings(epgOutcomes: List<EpgRefreshOutcome>) {
                    calls += "apply-mapping:${epgOutcomes.size}"
                }
            },
            logoRefresher = object : LogoRefresher {
                override suspend fun refreshLogos() {
                    calls += "refresh-logos"
                }
            },
            cacheCleaner = object : CacheCleaner {
                override suspend fun cleanup() {
                    calls += "cache-cleanup"
                }
            },
            diagnostics = diagnostics,
        )

        val report = orchestrator.refresh()

        assertEquals(
            listOf(
                "collect-playlists",
                "refresh-playlist:provider-a",
                "refresh-playlist:provider-b",
                "collect-epg",
                "refresh-epg:epg-shared",
                "apply-mapping:1",
                "refresh-logos",
                "cache-cleanup",
            ),
            calls,
        )
        assertEquals(2, report.playlistsCollected)
        assertEquals(1, report.epgSourcesCollected)
        assertEquals(RefreshDiagnosticType.RefreshCompleted, diagnostics.events.last().type)
    }

    @Test
    fun refreshKeepsGoingWhenPlaylistAndEpgRefreshFail() = runBlocking {
        val calls = mutableListOf<String>()
        val diagnostics = InMemoryRefreshDiagnostics()
        val orchestrator = GlobalRefreshOrchestrator(
            playlistSource = object : PlaylistRefreshSource {
                override suspend fun collectDuePlaylists(): List<PlaylistRefreshTarget> =
                    listOf(PlaylistRefreshTarget("provider-a"), PlaylistRefreshTarget("provider-b"))
            },
            playlistRefresher = object : PlaylistRefresher {
                override suspend fun refresh(target: PlaylistRefreshTarget): PlaylistRefreshOutcome {
                    if (target.providerId == "provider-a") {
                        error("https://server.example/player_api.php?username=user&password=secret")
                    }
                    return PlaylistRefreshOutcome(target.providerId, success = true, epgSourceIds = listOf("epg-1"))
                }
            },
            epgSourceResolver = object : EpgSourceResolver {
                override suspend fun collectRequiredSources(playlistOutcomes: List<PlaylistRefreshOutcome>): List<EpgRefreshTarget> =
                    playlistOutcomes.flatMap { it.epgSourceIds }.map(::EpgRefreshTarget)
            },
            epgRefresher = object : EpgRefresher {
                override suspend fun refresh(target: EpgRefreshTarget): EpgRefreshOutcome {
                    error("EPG failed: epg_url=https://epg.example/file.xml?token=abc")
                }
            },
            epgMappingApplier = object : EpgMappingApplier {
                override suspend fun applyMappings(epgOutcomes: List<EpgRefreshOutcome>) {
                    calls += "mapping:${epgOutcomes.size}"
                }
            },
            logoRefresher = object : LogoRefresher {
                override suspend fun refreshLogos() {
                    calls += "logos"
                }
            },
            cacheCleaner = object : CacheCleaner {
                override suspend fun cleanup() {
                    calls += "cache"
                }
            },
            diagnostics = diagnostics,
        )

        val report = orchestrator.refresh()

        assertEquals(1, report.playlistsSucceeded)
        assertEquals(1, report.playlistsFailed)
        assertEquals(0, report.epgSourcesSucceeded)
        assertEquals(1, report.epgSourcesFailed)
        assertEquals(listOf("mapping:0", "logos", "cache"), calls)
        assertTrue(diagnostics.events.any { it.type == RefreshDiagnosticType.PlaylistRefreshFailed })
        assertTrue(diagnostics.events.any { it.type == RefreshDiagnosticType.EpgRefreshFailed })
        val diagnosticText = diagnostics.events.joinToString("\n") { it.message }
        assertFalse(diagnosticText.contains("secret"))
        assertFalse(diagnosticText.contains("token=abc"))
        assertTrue(diagnosticText.contains("password=[REDACTED]"))
    }
}
