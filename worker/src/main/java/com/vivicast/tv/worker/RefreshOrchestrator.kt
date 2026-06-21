package com.vivicast.tv.worker

class GlobalRefreshOrchestrator(
    private val playlistSource: PlaylistRefreshSource,
    private val playlistRefresher: PlaylistRefresher,
    private val epgSourceResolver: EpgSourceResolver,
    private val epgRefresher: EpgRefresher,
    private val epgMappingApplier: EpgMappingApplier,
    private val logoRefresher: LogoRefresher,
    private val cacheCleaner: CacheCleaner,
    private val diagnostics: RefreshDiagnostics,
) {
    suspend fun refresh(): RefreshReport {
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.RefreshStarted, "Global refresh started."))

        val playlists = playlistSource.collectDuePlaylists()
        val playlistOutcomes = playlists.map { target ->
            runCatching { playlistRefresher.refresh(target) }
                .fold(
                    onSuccess = { outcome ->
                        diagnostics.record(
                            RefreshDiagnosticEvent(
                                type = RefreshDiagnosticType.PlaylistRefreshSucceeded,
                                message = "Playlist refreshed for provider ${target.providerId}.",
                                metadata = mapOf("providerId" to target.providerId),
                            ),
                        )
                        outcome
                    },
                    onFailure = { error ->
                        diagnostics.record(
                            RefreshDiagnosticEvent(
                                type = RefreshDiagnosticType.PlaylistRefreshFailed,
                                message = "Playlist refresh failed for provider ${target.providerId}: ${error.message.orEmpty()}",
                                metadata = mapOf("providerId" to target.providerId),
                            ),
                        )
                        PlaylistRefreshOutcome(providerId = target.providerId, success = false, epgSourceIds = emptyList())
                    },
                )
        }

        val epgSources = epgSourceResolver.collectRequiredSources(playlistOutcomes)
            .distinctBy { it.epgSourceId }
        val epgOutcomes = epgSources.map { source ->
            runCatching { epgRefresher.refresh(source) }
                .fold(
                    onSuccess = { outcome ->
                        diagnostics.record(
                            RefreshDiagnosticEvent(
                                type = RefreshDiagnosticType.EpgRefreshSucceeded,
                                message = "EPG source refreshed: ${source.epgSourceId}.",
                                metadata = mapOf("epgSourceId" to source.epgSourceId),
                            ),
                        )
                        outcome
                    },
                    onFailure = { error ->
                        diagnostics.record(
                            RefreshDiagnosticEvent(
                                type = RefreshDiagnosticType.EpgRefreshFailed,
                                message = "EPG refresh failed for ${source.epgSourceId}: ${error.message.orEmpty()}",
                                metadata = mapOf("epgSourceId" to source.epgSourceId),
                            ),
                        )
                        EpgRefreshOutcome(epgSourceId = source.epgSourceId, success = false)
                    },
                )
        }

        epgMappingApplier.applyMappings(epgOutcomes.filter { it.success })
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.EpgMappingApplied, "EPG mapping applied."))

        logoRefresher.refreshLogos()
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.LogoRefreshCompleted, "Logo refresh completed."))

        cacheCleaner.cleanup()
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.CacheCleanupCompleted, "Cache cleanup completed."))

        val report = RefreshReport(
            playlistsCollected = playlists.size,
            playlistsSucceeded = playlistOutcomes.count { it.success },
            playlistsFailed = playlistOutcomes.count { !it.success },
            epgSourcesCollected = epgSources.size,
            epgSourcesSucceeded = epgOutcomes.count { it.success },
            epgSourcesFailed = epgOutcomes.count { !it.success },
        )
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.RefreshCompleted, "Global refresh completed."))
        return report
    }
}

interface PlaylistRefreshSource {
    suspend fun collectDuePlaylists(): List<PlaylistRefreshTarget>
}

interface PlaylistRefresher {
    suspend fun refresh(target: PlaylistRefreshTarget): PlaylistRefreshOutcome
}

interface EpgSourceResolver {
    suspend fun collectRequiredSources(playlistOutcomes: List<PlaylistRefreshOutcome>): List<EpgRefreshTarget>
}

interface EpgRefresher {
    suspend fun refresh(target: EpgRefreshTarget): EpgRefreshOutcome
}

interface EpgMappingApplier {
    suspend fun applyMappings(epgOutcomes: List<EpgRefreshOutcome>)
}

interface LogoRefresher {
    suspend fun refreshLogos()
}

interface CacheCleaner {
    suspend fun cleanup()
}

data class PlaylistRefreshTarget(
    val providerId: String,
)

data class PlaylistRefreshOutcome(
    val providerId: String,
    val success: Boolean,
    val epgSourceIds: List<String>,
)

data class EpgRefreshTarget(
    val epgSourceId: String,
)

data class EpgRefreshOutcome(
    val epgSourceId: String,
    val success: Boolean,
)

data class RefreshReport(
    val playlistsCollected: Int,
    val playlistsSucceeded: Int,
    val playlistsFailed: Int,
    val epgSourcesCollected: Int,
    val epgSourcesSucceeded: Int,
    val epgSourcesFailed: Int,
)
