package com.vivicast.tv.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

interface RefreshWorkScheduler {
    fun setMaintenancePeriodicEnabled(
        enabled: Boolean,
        repeatIntervalHours: Long = WorkerContracts.DEFAULT_GLOBAL_REFRESH_INTERVAL_HOURS,
    )

    // [restart] = true → ExistingWorkPolicy.REPLACE (cancel + restart an in-flight run with the current
    // settings), for the foreground save + the per-playlist "Aktualisieren" action. Default false = KEEP
    // (an in-flight run coalesces), for refresh-all / interval / app-start. See D10 R10.
    fun enqueuePlaylistRefresh(providerId: String, restart: Boolean = false)

    /**
     * Ordered post-restore refresh: ALL playlists (Phase 1) then ALL EPG sources (Phase 2) as one WorkManager
     * continuation, so every (possibly shared) EPG source maps against the fully-rebuilt catalog of ALL its
     * providers — not a partial one. The Phase-1 playlist works carry the restore-chain flag (suppress their
     * per-provider EPG trigger + never fail the chain). No-op when [providerIds] is empty (no catalog to build).
     * See plans/backup-restore-followups.md (F1).
     */
    fun enqueueRestoreRefresh(providerIds: List<String>, epgSourceIds: List<String>)

    /** Cancels this playlist's in-flight/queued one-time refresh (used when the provider is deleted). */
    fun cancelPlaylistRefresh(providerId: String)

    /**
     * Schedules this playlist's own periodic auto-refresh at [intervalHours]. [initialDelayMillis] phases
     * the first run (remaining time since the last refresh) so a foreground cancel + background re-enqueue
     * doesn't reset the interval countdown.
     */
    fun enqueuePlaylistPeriodic(providerId: String, intervalHours: Int, initialDelayMillis: Long = 0L)

    /** Cancels this playlist's periodic auto-refresh (used when its interval is set to "off"). */
    fun cancelPlaylistPeriodic(providerId: String)

    fun enqueueEpgRefresh(epgSourceId: String)

    /** Cancels this EPG source's in-flight/queued one-time refresh (used when the source is deleted). */
    fun cancelEpgRefresh(epgSourceId: String)

    /** Schedules this EPG source's own periodic auto-refresh at [intervalHours]. See [enqueuePlaylistPeriodic]. */
    fun enqueueEpgPeriodic(epgSourceId: String, intervalHours: Int, initialDelayMillis: Long = 0L)

    /** Cancels this EPG source's periodic auto-refresh (used when its interval is set to "off"). */
    fun cancelEpgPeriodic(epgSourceId: String)
}

class WorkManagerRefreshWorkScheduler(
    private val workManager: WorkManager,
) : RefreshWorkScheduler {
    override fun setMaintenancePeriodicEnabled(enabled: Boolean, repeatIntervalHours: Long) {
        if (!enabled) {
            workManager.cancelUniqueWork(WorkerContracts.PERIODIC_GLOBAL_REFRESH_WORK)
            return
        }

        workManager.enqueueUniquePeriodicWork(
            WorkerContracts.PERIODIC_GLOBAL_REFRESH_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            RefreshWorkRequests.periodicMaintenanceRefresh(repeatIntervalHours),
        )
    }

    override fun enqueuePlaylistRefresh(providerId: String, restart: Boolean) {
        workManager.enqueueUniqueWork(
            WorkerContracts.uniquePlaylistRefreshWork(providerId),
            if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            RefreshWorkRequests.playlistRefresh(providerId),
        )
    }

    override fun enqueueRestoreRefresh(providerIds: List<String>, epgSourceIds: List<String>) {
        // No active providers → no catalog will exist → an EPG phase would map nothing. Skip entirely.
        if (providerIds.isEmpty()) return
        val playlistWork = providerIds.map { RefreshWorkRequests.playlistRefresh(it, restoreChain = true) }
        val epgWork = epgSourceIds.map { RefreshWorkRequests.epgRefresh(it) }
        // REPLACE: a re-import supersedes an in-flight restore chain. Phase 2 (.then) runs once every Phase-1
        // playlist node has SUCCEEDED — the restore-chain flag guarantees each does (terminal → success).
        val continuation = workManager.beginUniqueWork(
            WorkerContracts.RESTORE_REFRESH_WORK,
            ExistingWorkPolicy.REPLACE,
            playlistWork,
        )
        if (epgWork.isEmpty()) continuation.enqueue() else continuation.then(epgWork).enqueue()
    }

    override fun cancelPlaylistRefresh(providerId: String) {
        workManager.cancelUniqueWork(WorkerContracts.uniquePlaylistRefreshWork(providerId))
    }

    override fun enqueuePlaylistPeriodic(providerId: String, intervalHours: Int, initialDelayMillis: Long) {
        workManager.enqueueUniquePeriodicWork(
            WorkerContracts.uniquePlaylistPeriodicWork(providerId),
            ExistingPeriodicWorkPolicy.UPDATE,
            RefreshWorkRequests.periodicPlaylistRefresh(providerId, intervalHours, initialDelayMillis),
        )
    }

    override fun cancelPlaylistPeriodic(providerId: String) {
        workManager.cancelUniqueWork(WorkerContracts.uniquePlaylistPeriodicWork(providerId))
    }

    override fun enqueueEpgRefresh(epgSourceId: String) {
        workManager.enqueueUniqueWork(
            WorkerContracts.uniqueEpgRefreshWork(epgSourceId),
            ExistingWorkPolicy.KEEP,
            RefreshWorkRequests.epgRefresh(epgSourceId),
        )
    }

    override fun cancelEpgRefresh(epgSourceId: String) {
        workManager.cancelUniqueWork(WorkerContracts.uniqueEpgRefreshWork(epgSourceId))
    }

    override fun enqueueEpgPeriodic(epgSourceId: String, intervalHours: Int, initialDelayMillis: Long) {
        workManager.enqueueUniquePeriodicWork(
            WorkerContracts.uniqueEpgPeriodicWork(epgSourceId),
            ExistingPeriodicWorkPolicy.UPDATE,
            RefreshWorkRequests.periodicEpgRefresh(epgSourceId, intervalHours, initialDelayMillis),
        )
    }

    override fun cancelEpgPeriodic(epgSourceId: String) {
        workManager.cancelUniqueWork(WorkerContracts.uniqueEpgPeriodicWork(epgSourceId))
    }
}

internal object RefreshWorkRequests {
    fun periodicMaintenanceRefresh(repeatIntervalHours: Long): PeriodicWorkRequest {
        val safeInterval = repeatIntervalHours
            .coerceAtLeast(WorkerContracts.MIN_GLOBAL_REFRESH_INTERVAL_HOURS)
        return PeriodicWorkRequestBuilder<MaintenanceRefreshWorker>(safeInterval, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(WorkerContracts.GLOBAL_REFRESH_WORK)
            .build()
    }

    fun playlistRefresh(providerId: String, restoreChain: Boolean = false): OneTimeWorkRequest =
        networkOneTimeRequest<PlaylistRefreshWorker>(
            Data.Builder()
                .putString(WorkerContracts.INPUT_PROVIDER_ID, providerId)
                .putBoolean(WorkerContracts.INPUT_RESTORE_CHAIN, restoreChain)
                .build(),
        )

    fun periodicPlaylistRefresh(providerId: String, intervalHours: Int, initialDelayMillis: Long = 0L): PeriodicWorkRequest {
        val safeInterval = intervalHours.toLong().coerceAtLeast(WorkerContracts.MIN_GLOBAL_REFRESH_INTERVAL_HOURS)
        return PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(safeInterval, TimeUnit.HOURS)
            .setInputData(Data.Builder().putString(WorkerContracts.INPUT_PROVIDER_ID, providerId).build())
            .setConstraints(networkConstraints())
            .setInitialDelay(initialDelayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }

    fun epgRefresh(epgSourceId: String): OneTimeWorkRequest =
        networkOneTimeRequest<EpgRefreshWorker>(
            Data.Builder()
                .putString(WorkerContracts.INPUT_EPG_SOURCE_ID, epgSourceId)
                .build(),
        )

    fun periodicEpgRefresh(epgSourceId: String, intervalHours: Int, initialDelayMillis: Long = 0L): PeriodicWorkRequest {
        val safeInterval = intervalHours.toLong().coerceAtLeast(WorkerContracts.MIN_GLOBAL_REFRESH_INTERVAL_HOURS)
        return PeriodicWorkRequestBuilder<EpgRefreshWorker>(safeInterval, TimeUnit.HOURS)
            .setInputData(Data.Builder().putString(WorkerContracts.INPUT_EPG_SOURCE_ID, epgSourceId).build())
            .setConstraints(networkConstraints())
            .setInitialDelay(initialDelayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }

    private inline fun <reified T : DelegatingRefreshWorker> networkOneTimeRequest(
        inputData: Data = Data.EMPTY,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<T>()
            .setInputData(inputData)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
