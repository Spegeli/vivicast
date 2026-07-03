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
    fun setBackgroundRefreshEnabled(
        enabled: Boolean,
        repeatIntervalHours: Long = WorkerContracts.DEFAULT_GLOBAL_REFRESH_INTERVAL_HOURS,
    )

    fun enqueueGlobalRefresh()

    fun enqueuePlaylistRefresh(providerId: String)

    fun enqueueEpgRefresh(epgSourceId: String)

    fun enqueueSeriesDetailsRefresh(providerId: String)

    fun enqueueLogoRefresh()

    fun enqueueCacheCleanup()
}

class WorkManagerRefreshWorkScheduler(
    private val workManager: WorkManager,
) : RefreshWorkScheduler {
    override fun setBackgroundRefreshEnabled(enabled: Boolean, repeatIntervalHours: Long) {
        if (!enabled) {
            workManager.cancelUniqueWork(WorkerContracts.PERIODIC_GLOBAL_REFRESH_WORK)
            return
        }

        workManager.enqueueUniquePeriodicWork(
            WorkerContracts.PERIODIC_GLOBAL_REFRESH_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            RefreshWorkRequests.periodicGlobalRefresh(repeatIntervalHours),
        )
    }

    override fun enqueueGlobalRefresh() {
        workManager.enqueueUniqueWork(
            WorkerContracts.GLOBAL_REFRESH_WORK,
            ExistingWorkPolicy.KEEP,
            RefreshWorkRequests.globalRefresh(),
        )
    }

    override fun enqueuePlaylistRefresh(providerId: String) {
        workManager.enqueueUniqueWork(
            WorkerContracts.uniquePlaylistRefreshWork(providerId),
            ExistingWorkPolicy.KEEP,
            RefreshWorkRequests.playlistRefresh(providerId),
        )
    }

    override fun enqueueEpgRefresh(epgSourceId: String) {
        workManager.enqueueUniqueWork(
            WorkerContracts.uniqueEpgRefreshWork(epgSourceId),
            ExistingWorkPolicy.KEEP,
            RefreshWorkRequests.epgRefresh(epgSourceId),
        )
    }

    override fun enqueueSeriesDetailsRefresh(providerId: String) {
        // REPLACE: a new refresh cycle restarts the (full) series-details fetch, superseding any
        // still-running job from the previous cycle.
        workManager.enqueueUniqueWork(
            WorkerContracts.uniqueSeriesDetailsRefreshWork(providerId),
            ExistingWorkPolicy.REPLACE,
            RefreshWorkRequests.seriesDetailsRefresh(providerId),
        )
    }

    override fun enqueueLogoRefresh() {
        workManager.enqueueUniqueWork(
            WorkerContracts.LOGO_REFRESH_WORK,
            ExistingWorkPolicy.KEEP,
            RefreshWorkRequests.logoRefresh(),
        )
    }

    override fun enqueueCacheCleanup() {
        workManager.enqueueUniqueWork(
            WorkerContracts.CACHE_CLEANUP_WORK,
            ExistingWorkPolicy.KEEP,
            RefreshWorkRequests.cacheCleanup(),
        )
    }
}

internal object RefreshWorkRequests {
    fun globalRefresh(): OneTimeWorkRequest =
        networkOneTimeRequest<GlobalRefreshWorker>()

    fun periodicGlobalRefresh(repeatIntervalHours: Long): PeriodicWorkRequest {
        val safeInterval = repeatIntervalHours
            .coerceAtLeast(WorkerContracts.MIN_GLOBAL_REFRESH_INTERVAL_HOURS)
        return PeriodicWorkRequestBuilder<GlobalRefreshWorker>(safeInterval, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(WorkerContracts.GLOBAL_REFRESH_WORK)
            .build()
    }

    fun playlistRefresh(providerId: String): OneTimeWorkRequest =
        networkOneTimeRequest<PlaylistRefreshWorker>(
            Data.Builder()
                .putString(WorkerContracts.INPUT_PROVIDER_ID, providerId)
                .build(),
        )

    fun epgRefresh(epgSourceId: String): OneTimeWorkRequest =
        networkOneTimeRequest<EpgRefreshWorker>(
            Data.Builder()
                .putString(WorkerContracts.INPUT_EPG_SOURCE_ID, epgSourceId)
                .build(),
        )

    fun seriesDetailsRefresh(providerId: String): OneTimeWorkRequest =
        networkOneTimeRequest<SeriesDetailsRefreshWorker>(
            Data.Builder()
                .putString(WorkerContracts.INPUT_PROVIDER_ID, providerId)
                .build(),
        )

    fun logoRefresh(): OneTimeWorkRequest =
        networkOneTimeRequest<LogoRefreshWorker>()

    fun cacheCleanup(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<CacheCleanupWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(WorkerContracts.CACHE_CLEANUP_WORK)
            .build()

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
