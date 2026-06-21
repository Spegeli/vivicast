package com.vivicast.tv.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

interface RefreshWorkerRunner {
    suspend fun runGlobalRefresh(): RefreshWorkerResult

    suspend fun runPlaylistRefresh(providerId: String?): RefreshWorkerResult

    suspend fun runEpgRefresh(epgSourceId: String?): RefreshWorkerResult

    suspend fun runLogoRefresh(): RefreshWorkerResult

    suspend fun runCacheCleanup(): RefreshWorkerResult
}

enum class RefreshWorkerResult {
    Success,
    Retry,
    Failure,
}

object RefreshWorkerRegistry {
    @Volatile
    private var runner: RefreshWorkerRunner? = null

    fun install(runner: RefreshWorkerRunner) {
        this.runner = runner
    }

    fun clear() {
        runner = null
    }

    internal fun requireRunner(): RefreshWorkerRunner? = runner
}

abstract class DelegatingRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    final override suspend fun doWork(): Result {
        val runner = RefreshWorkerRegistry.requireRunner() ?: return Result.retry()
        return runCatching { runner.runDelegatedWork() }
            .getOrElse { RefreshWorkerResult.Retry }
            .toWorkResult()
    }

    protected abstract suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult
}

class GlobalRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runGlobalRefresh()
}

class PlaylistRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runPlaylistRefresh(inputData.getString(WorkerContracts.INPUT_PROVIDER_ID))
}

class EpgRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runEpgRefresh(inputData.getString(WorkerContracts.INPUT_EPG_SOURCE_ID))
}

class LogoRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runLogoRefresh()
}

class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runCacheCleanup()
}

private fun RefreshWorkerResult.toWorkResult(): ListenableWorkerResult =
    when (this) {
        RefreshWorkerResult.Success -> ListenableWorkerResult.success()
        RefreshWorkerResult.Retry -> ListenableWorkerResult.retry()
        RefreshWorkerResult.Failure -> ListenableWorkerResult.failure()
    }

private typealias ListenableWorkerResult = androidx.work.ListenableWorker.Result
