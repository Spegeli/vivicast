package com.vivicast.tv.worker

interface RefreshDiagnostics {
    fun record(event: RefreshDiagnosticEvent)
}

data class RefreshDiagnosticEvent(
    val type: RefreshDiagnosticType,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
)

enum class RefreshDiagnosticType {
    RefreshStarted,
    PlaylistRefreshSucceeded,
    PlaylistRefreshFailed,
    PlaylistRefreshCancelled,
    EpgRefreshSucceeded,
    EpgRefreshFailed,
    EpgRefreshCancelled,
    LogoRefreshCompleted,
    CacheCleanupCompleted,
    RefreshCompleted,
}

/** Default sink when no real diagnostics is wired (tests, previews). Redaction lives in the real
 * implementation (StoreRefreshDiagnostics → DiagnosticsSanitizer), so this just drops events. */
object NoOpRefreshDiagnostics : RefreshDiagnostics {
    override fun record(event: RefreshDiagnosticEvent) = Unit
}
