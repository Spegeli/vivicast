package com.vivicast.tv.diagnostics

/**
 * The single central redaction layer for diagnostics. Every diagnostic event, crash stacktrace and
 * logcat snapshot passes through this before being written and again before export. Secret access
 * values, tokens and private URLs must never be logged or exported (PRD-11).
 */
object DiagnosticsSanitizer {
    // Any scheme://… — not just http/https. IPTV streams use rtsp/rtmp/udp/mms and carry host +
    // often credentials; those must never survive into a log line or the logcat snapshot.
    private val URL = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
    private val SECRET = Regex("(?i)(token|password|passwd|pwd|cookie|authorization|username|user|host)=\\S+")
    // `host` blanks the bare provider hostname the network-event logger records (a failing xtream/EPG/
    // connection-test request) so it can't reach the shareable diagnostics export (PRD-11). Bare hosts have
    // no scheme, so the URL regex above doesn't catch them — the key match here does.
    private val SENSITIVE_KEY = Regex("(?i)(provider|content|title|name|search|url|header|cookie|token|password|username|host)")

    /** Full redaction; preserves newlines (for multi-line stacktraces / logcat dumps). */
    fun redact(value: String): String =
        value
            .replace(URL, "[redacted-url]")
            .replace(SECRET) { "${it.groupValues[1]}=[redacted]" }

    /** One log line: redact + collapse newlines + cap length. */
    fun line(value: String): String =
        redact(value).replace(Regex("[\\r\\n]+"), " ").take(2_000)

    /** A category or key token for the `key=value` format — no whitespace. */
    fun token(value: String): String =
        line(value).replace(Regex("\\s+"), "_")

    /** Detail value; a sensitive key name blanks the value entirely. */
    fun detail(key: String, value: String): String =
        if (SENSITIVE_KEY.containsMatchIn(key)) "[redacted]" else line(value)
}
