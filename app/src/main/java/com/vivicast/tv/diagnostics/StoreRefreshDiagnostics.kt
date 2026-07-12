package com.vivicast.tv.diagnostics

import com.vivicast.tv.worker.RefreshDiagnosticEvent
import com.vivicast.tv.worker.RefreshDiagnostics

/**
 * Routes the worker's refresh events (playlist/EPG success or failure, logo, cache) into the persistent
 * diagnostics log (gated + sanitized by [DiagnosticsStore]). Replaces the old in-memory sink whose events
 * were never surfaced.
 */
class StoreRefreshDiagnostics(private val store: DiagnosticsStore) : RefreshDiagnostics {
    override fun record(event: RefreshDiagnosticEvent) {
        store.log("refresh", event.type.name, event.metadata + mapOf("info" to event.message))
    }
}
