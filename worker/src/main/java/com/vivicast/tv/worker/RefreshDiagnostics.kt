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
    EpgRefreshSucceeded,
    EpgRefreshFailed,
    EpgMappingApplied,
    LogoRefreshCompleted,
    CacheCleanupCompleted,
    RefreshCompleted,
}

class InMemoryRefreshDiagnostics(
    private val redactor: SensitiveValueRedactor = SensitiveValueRedactor(),
) : RefreshDiagnostics {
    private val mutableEvents = mutableListOf<RefreshDiagnosticEvent>()

    val events: List<RefreshDiagnosticEvent>
        get() = mutableEvents.toList()

    override fun record(event: RefreshDiagnosticEvent) {
        mutableEvents += event.copy(
            message = redactor.redact(event.message),
            metadata = event.metadata.mapValues { (_, value) -> redactor.redact(value) },
        )
    }
}

class SensitiveValueRedactor {
    fun redact(value: String): String =
        value
            .replace(USER_INFO_REGEX, "://[REDACTED]@")
            .replace(SECRET_QUERY_REGEX) { match ->
                "${match.groupValues[1]}=[REDACTED]"
            }

    private companion object {
        val USER_INFO_REGEX = Regex("""://[^/\s:@]+:[^/\s@]+@""")
        val SECRET_QUERY_REGEX = Regex("""(?i)\b(username|user|password|pass|token|auth|key|m3u_url|epg_url)=([^&\s]+)""")
    }
}
