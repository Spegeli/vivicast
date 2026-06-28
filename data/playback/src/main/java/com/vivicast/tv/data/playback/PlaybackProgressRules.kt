package com.vivicast.tv.data.playback

import com.vivicast.tv.domain.model.PlaybackProgress

const val PLAYBACK_COMPLETION_THRESHOLD_PERCENT = 95
const val AUTOMATIC_PROGRESS_SAVE_INTERVAL_MILLIS = 10_000L

private const val MIN_AUTOMATIC_PROGRESS_POSITION_MILLIS = 10_000L
private const val MIN_AUTOMATIC_PROGRESS_PERCENT = 1

fun automaticPlaybackProgressPercent(positionMillis: Long, durationMillis: Long): Int {
    if (durationMillis <= 0L) return 0
    return ((positionMillis.coerceAtLeast(0L) * 100L) / durationMillis)
        .coerceIn(0L, 100L)
        .toInt()
}

fun shouldCreateAutomaticPlaybackProgress(positionMillis: Long, durationMillis: Long): Boolean =
    positionMillis.coerceAtLeast(0L) >= MIN_AUTOMATIC_PROGRESS_POSITION_MILLIS ||
        automaticPlaybackProgressPercent(positionMillis, durationMillis) >= MIN_AUTOMATIC_PROGRESS_PERCENT

fun shouldSaveAutomaticPlaybackProgress(
    existing: PlaybackProgress?,
    lastSavedAtMillis: Long?,
    nowMillis: Long,
    positionMillis: Long,
    durationMillis: Long,
    force: Boolean,
    allowCreateBelowMinimum: Boolean = false,
): Boolean {
    if (
        existing == null &&
        !allowCreateBelowMinimum &&
        !shouldCreateAutomaticPlaybackProgress(positionMillis, durationMillis)
    ) {
        return false
    }
    if (existing == null || force) {
        return true
    }
    val lastSavedAt = lastSavedAtMillis ?: return true
    return nowMillis - lastSavedAt >= AUTOMATIC_PROGRESS_SAVE_INTERVAL_MILLIS
}
