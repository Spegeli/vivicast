package com.vivicast.tv.worker

/**
 * Whether an item (playlist / EPG source) is due for an automatic refresh.
 *
 * [intervalHours] `<= 0` means "off" (never due). Otherwise the item is due once at least
 * [intervalHours] have elapsed since [lastRefreshMillis]. A [lastRefreshMillis] of 0 (never refreshed
 * this session) is always due for a positive interval.
 */
fun isRefreshDue(nowMillis: Long, lastRefreshMillis: Long, intervalHours: Int): Boolean {
    if (intervalHours <= 0) return false
    return nowMillis - lastRefreshMillis >= intervalHours.toLong() * MILLIS_PER_HOUR
}

/**
 * Millis until the next interval refresh is due, given the last refresh time. 0 = due now (or overdue,
 * or never refreshed). Used to preserve a background periodic's phase across a cancel+re-enqueue: the
 * re-enqueued periodic's initial delay is the remaining time, so opening the app doesn't reset the
 * countdown. [intervalHours] `<= 0` returns 0 (caller should not schedule an "off" item).
 */
fun refreshDelayMillis(nowMillis: Long, lastRefreshMillis: Long, intervalHours: Int): Long {
    if (intervalHours <= 0) return 0L
    val remaining = intervalHours.toLong() * MILLIS_PER_HOUR - (nowMillis - lastRefreshMillis)
    return remaining.coerceAtLeast(0L)
}

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
