package com.vivicast.tv.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshTimingTest {
    private val hourMs = 60L * 60L * 1000L

    @Test
    fun offIntervalIsNeverDue() {
        assertFalse(isRefreshDue(nowMillis = hourMs * 100, lastRefreshMillis = 0, intervalHours = 0))
    }

    @Test
    fun dueOnlyAfterIntervalElapsed() {
        val last = hourMs * 10
        assertFalse(isRefreshDue(nowMillis = last + hourMs * 3, lastRefreshMillis = last, intervalHours = 4))
        assertTrue(isRefreshDue(nowMillis = last + hourMs * 4, lastRefreshMillis = last, intervalHours = 4))
        assertTrue(isRefreshDue(nowMillis = last + hourMs * 9, lastRefreshMillis = last, intervalHours = 4))
    }

    @Test
    fun neverRefreshedIsDueForPositiveInterval() {
        // A missing map entry reads as 0 ("never refreshed this session"); against a real clock the
        // elapsed time dwarfs any interval, so the item is immediately due.
        assertTrue(isRefreshDue(nowMillis = hourMs * 100, lastRefreshMillis = 0, intervalHours = 2))
    }
}
