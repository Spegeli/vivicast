package com.vivicast.tv.worker

class MaintenanceRefreshOrchestrator(
    private val logoRefresher: LogoRefresher,
    private val cacheCleaner: CacheCleaner,
    private val diagnostics: RefreshDiagnostics,
) {
    // Playlists and EPG sources are refreshed by their own per-item periodic/one-time workers now, so the
    // global periodic covers only logos + cache maintenance.
    suspend fun refresh(): RefreshReport {
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.RefreshStarted, "Global refresh started."))

        logoRefresher.refreshLogos()
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.LogoRefreshCompleted, "Logo refresh completed."))

        cacheCleaner.cleanup()
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.CacheCleanupCompleted, "Cache cleanup completed."))

        val report = RefreshReport(
            playlistsCollected = 0,
            playlistsSucceeded = 0,
            playlistsFailed = 0,
            epgSourcesCollected = 0,
            epgSourcesSucceeded = 0,
            epgSourcesFailed = 0,
            seriesDetailsProviderIds = emptyList(),
        )
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.RefreshCompleted, "Global refresh completed."))
        return report
    }
}

interface PlaylistRefresher {
    suspend fun refresh(target: PlaylistRefreshTarget): PlaylistRefreshOutcome
}

interface EpgRefresher {
    suspend fun refresh(target: EpgRefreshTarget): EpgRefreshOutcome
}

interface LogoRefresher {
    suspend fun refreshLogos()
}

interface SeriesDetailsRefresher {
    suspend fun refresh(providerId: String): SeriesDetailsRefreshOutcome
}

data class SeriesDetailsRefreshOutcome(
    val providerId: String,
    val success: Boolean,
)

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
    val needsSeriesDetailsRefresh: Boolean = false,
    // True when this run was skipped because the same provider is already refreshing in-process. Distinct
    // from a failure: the caller must NOT retry (the in-flight run covers it), else the whole playlist is
    // re-fetched/re-imported a few seconds later.
    val skipped: Boolean = false,
)

data class EpgRefreshTarget(
    val epgSourceId: String,
)

data class EpgRefreshOutcome(
    val epgSourceId: String,
    val success: Boolean,
    // See PlaylistRefreshOutcome.skipped — already refreshing in-process, do not retry.
    val skipped: Boolean = false,
)

data class RefreshReport(
    val playlistsCollected: Int,
    val playlistsSucceeded: Int,
    val playlistsFailed: Int,
    val epgSourcesCollected: Int,
    val epgSourcesSucceeded: Int,
    val epgSourcesFailed: Int,
    val seriesDetailsProviderIds: List<String> = emptyList(),
)
