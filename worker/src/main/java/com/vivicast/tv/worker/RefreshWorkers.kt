package com.vivicast.tv.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

interface RefreshWorkerRunner {
    suspend fun runMaintenanceRefresh(): RefreshWorkerResult

    // restoreChain: this playlist refresh runs inside the restore continuation → do NOT re-enqueue the
    // provider's EPG sources here (Phase 2 of the continuation owns EPG, after the whole catalog is rebuilt).
    suspend fun runPlaylistRefresh(providerId: String?, restoreChain: Boolean = false): RefreshWorkerResult

    suspend fun runEpgRefresh(epgSourceId: String?): RefreshWorkerResult
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
        val restoreChain = inputData.getBoolean(WorkerContracts.INPUT_RESTORE_CHAIN, false)
        val result = runCancellableCatching { runner.runDelegatedWork() }
            .getOrElse { RefreshWorkerResult.Retry }
        // Backstop: a never-recovering transient error (deterministic ones are already classified terminal in
        // RefreshExecution) would otherwise retry forever on exponential backoff. Give up after the cap. The
        // restore continuation uses a much smaller cap so one stuck provider can't stall the EPG phase for
        // hours (see plans/backup-restore-followups.md F1).
        val cap = if (restoreChain) RESTORE_CHAIN_MAX_ATTEMPTS else MAX_REFRESH_ATTEMPTS
        if (result == RefreshWorkerResult.Retry && runAttemptCount >= cap) {
            // Inside the restore chain, giving up must NOT fail the node — `.then()` runs the EPG phase only
            // once every Phase-1 node SUCCEEDED, so a failure would block EPG for the healthy providers too.
            // Report success (the error is already recorded as provider status + diagnostics).
            return if (restoreChain) Result.success() else Result.failure()
        }
        // Same reasoning for a terminal failure inside the chain: never block the EPG phase.
        if (restoreChain && result == RefreshWorkerResult.Failure) return Result.success()
        return result.toWorkResult()
    }

    protected abstract suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult
}

class MaintenanceRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runMaintenanceRefresh()
}

class PlaylistRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runPlaylistRefresh(
            inputData.getString(WorkerContracts.INPUT_PROVIDER_ID),
            restoreChain = inputData.getBoolean(WorkerContracts.INPUT_RESTORE_CHAIN, false),
        )
}

class EpgRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : DelegatingRefreshWorker(appContext, params) {
    override suspend fun RefreshWorkerRunner.runDelegatedWork(): RefreshWorkerResult =
        runEpgRefresh(inputData.getString(WorkerContracts.INPUT_EPG_SOURCE_ID))
}

/**
 * Like [runCatching] but never swallows [CancellationException]. WorkManager cancels a worker's
 * coroutine on stop (e.g. the ~10-minute execution limit); if that cancellation is caught and turned
 * into a "retry", the refresh keeps running every remaining stage instead of stopping — which is how a
 * global refresh ends up "running" far past its window. Rethrowing lets structured cancellation stop it.
 */
internal inline fun <T> runCancellableCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

// Backstop cap for retryable refresh failures. WorkManager's exponential backoff (10s, 20s, 40s, ...) already
// reaches the multi-hour range by ~10 attempts, so this bounds a never-recovering transient without cutting
// real outages short. ponytail: fixed cap; make it configurable if field data shows it's too low/high.
private const val MAX_REFRESH_ATTEMPTS = 10

// Restore continuation only: give up (as success) far sooner so a dead/flaky provider can't hold the EPG
// phase for hours. The provider is still picked up by the next periodic refresh + its own EPG trigger.
private const val RESTORE_CHAIN_MAX_ATTEMPTS = 3

private fun RefreshWorkerResult.toWorkResult(): ListenableWorkerResult =
    when (this) {
        RefreshWorkerResult.Success -> ListenableWorkerResult.success()
        RefreshWorkerResult.Retry -> ListenableWorkerResult.retry()
        RefreshWorkerResult.Failure -> ListenableWorkerResult.failure()
    }

private typealias ListenableWorkerResult = androidx.work.ListenableWorker.Result
