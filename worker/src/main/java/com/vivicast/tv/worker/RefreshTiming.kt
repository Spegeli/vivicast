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

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
