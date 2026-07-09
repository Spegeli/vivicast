package com.vivicast.tv.data.playback

/**
 * Preflight liveness check for an already-resolved stream URL. Used before auto-resuming the last
 * watched channel on startup so a dead endpoint (channel dropped from the playlist but not yet
 * purged, or a stream that is temporarily down) lands the user on Home instead of a raw 4xx/5xx in
 * the player.
 *
 * [httpStatus] performs the actual request (app-wired to the shared OkHttpClient with a short
 * timeout) and returns the HTTP status code, or null on a connect failure / timeout / exception.
 * Keeping it as a lambda means this module carries no HTTP dependency and the reachability decision
 * stays unit-testable.
 */
class StreamReachabilityProbe(
    private val httpStatus: suspend (url: String, userAgent: String?) -> Int?,
) {
    suspend fun isReachable(url: String, userAgent: String?): Boolean =
        httpStatus(url, userAgent)?.let { it in REACHABLE_STATUS_RANGE } ?: false

    private companion object {
        // 2xx success + 3xx redirect. OkHttp follows redirects itself, but accept 3xx defensively.
        val REACHABLE_STATUS_RANGE = 200..399
    }
}
