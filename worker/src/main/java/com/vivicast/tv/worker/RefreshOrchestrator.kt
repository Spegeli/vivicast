package com.vivicast.tv.worker

import com.vivicast.tv.core.cache.MediaCacheCleanupResult

class MaintenanceRefreshOrchestrator(
    private val logoRefresher: LogoRefresher,
    private val cacheCleaner: CacheCleaner,
    private val diagnostics: RefreshDiagnostics,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    // Playlists and EPG sources are refreshed by their own per-item periodic/one-time workers now, so the
    // global periodic covers only logos + cache maintenance.
    suspend fun refresh(): RefreshReport {
        val startedAt = clock()
        diagnostics.record(RefreshDiagnosticEvent(RefreshDiagnosticType.RefreshStarted, "Global refresh started."))

        val logoStart = clock()
        val logos = logoRefresher.refreshLogos()
        diagnostics.record(
            RefreshDiagnosticEvent(
                RefreshDiagnosticType.LogoRefreshCompleted,
                "Logo refresh completed.",
                mapOf(
                    "targets" to logos.targets.toString(),
                    "fetched" to logos.fetched.toString(),
                    "failed" to logos.failed.toString(),
                    "durationMs" to (clock() - logoStart).toString(),
                ),
            ),
        )

        val cacheStart = clock()
        val cleanup = cacheCleaner.cleanup()
        diagnostics.record(
            RefreshDiagnosticEvent(
                RefreshDiagnosticType.CacheCleanupCompleted,
                "Cache cleanup completed.",
                mapOf(
                    "removedFiles" to cleanup.removedFiles.toString(),
                    "removedBytes" to cleanup.removedBytes.toString(),
                    "remainingBytes" to cleanup.remainingBytes.toString(),
                    "durationMs" to (clock() - cacheStart).toString(),
                ),
            ),
        )

        val report = RefreshReport(
            playlistsCollected = 0,
            playlistsSucceeded = 0,
            playlistsFailed = 0,
            epgSourcesCollected = 0,
            epgSourcesSucceeded = 0,
            epgSourcesFailed = 0,
            seriesDetailsProviderIds = emptyList(),
        )
        diagnostics.record(
            RefreshDiagnosticEvent(
                RefreshDiagnosticType.RefreshCompleted,
                "Global refresh completed.",
                mapOf("durationMs" to (clock() - startedAt).toString()),
            ),
        )
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
    suspend fun refreshLogos(): LogoRefreshResult
}

/** Sanitized logo-prefetch counts for the diagnostics event: how many logos were considered / fetched / failed. */
data class LogoRefreshResult(
    val targets: Int = 0,
    val fetched: Int = 0,
    val failed: Int = 0,
)

interface SeriesDetailsRefresher {
    suspend fun refresh(providerId: String): SeriesDetailsRefreshOutcome
}

data class SeriesDetailsRefreshOutcome(
    val providerId: String,
    val success: Boolean,
    // Sanitized season/episode counts for the diagnostics event; empty on failure.
    val logMetadata: Map<String, String> = emptyMap(),
)

interface CacheCleaner {
    suspend fun cleanup(): MediaCacheCleanupResult
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
    // Sanitized count fields for the diagnostics event (type, per-content added/updated/removed, skipped,
    // status), built by the refresher. Empty on skip/fail.
    val logMetadata: Map<String, String> = emptyMap(),
)

data class EpgRefreshTarget(
    val epgSourceId: String,
)

data class EpgRefreshOutcome(
    val epgSourceId: String,
    val success: Boolean,
    // See PlaylistRefreshOutcome.skipped — already refreshing in-process, do not retry.
    val skipped: Boolean = false,
    // Sanitized count fields for the diagnostics event (channels, mappingsAdded/Updated, programs,
    // skippedPrograms, providers). Empty on skip/fail.
    val logMetadata: Map<String, String> = emptyMap(),
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
