package com.vivicast.tv.data.playback

import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressRulesTest {
    @Test
    fun doesNotCreateAutomaticProgressBeforeMinimumPositionOrPercent() {
        assertFalse(
            shouldCreateAutomaticPlaybackProgress(
                positionMillis = 9_000L,
                durationMillis = 2_000_000L,
            ),
        )
    }

    @Test
    fun createsAutomaticProgressAtTenSeconds() {
        assertTrue(
            shouldCreateAutomaticPlaybackProgress(
                positionMillis = 10_000L,
                durationMillis = 2_000_000L,
            ),
        )
    }

    @Test
    fun createsAutomaticProgressAtOnePercentWhenDurationIsKnown() {
        assertTrue(
            shouldCreateAutomaticPlaybackProgress(
                positionMillis = 6_000L,
                durationMillis = 600_000L,
            ),
        )
    }

    @Test
    fun savesExistingAutomaticProgressOnlyAfterIntervalUnlessForced() {
        val existing = progress()

        assertFalse(
            shouldSaveAutomaticPlaybackProgress(
                existing = existing,
                lastSavedAtMillis = 1_000L,
                nowMillis = 10_999L,
                positionMillis = 30_000L,
                durationMillis = 600_000L,
                force = false,
            ),
        )
        assertTrue(
            shouldSaveAutomaticPlaybackProgress(
                existing = existing,
                lastSavedAtMillis = 1_000L,
                nowMillis = 11_000L,
                positionMillis = 30_000L,
                durationMillis = 600_000L,
                force = false,
            ),
        )
        assertTrue(
            shouldSaveAutomaticPlaybackProgress(
                existing = existing,
                lastSavedAtMillis = 10_500L,
                nowMillis = 10_999L,
                positionMillis = 30_000L,
                durationMillis = 600_000L,
                force = true,
            ),
        )
    }

    @Test
    fun mediaEndCanCreateProgressBelowMinimum() {
        assertTrue(
            shouldSaveAutomaticPlaybackProgress(
                existing = null,
                lastSavedAtMillis = null,
                nowMillis = 1_000L,
                positionMillis = 2_000L,
                durationMillis = 0L,
                force = true,
                allowCreateBelowMinimum = true,
            ),
        )
    }

    @Test
    fun clampsAutomaticProgressPercent() {
        assertEquals(0, automaticPlaybackProgressPercent(positionMillis = -1_000L, durationMillis = 100_000L))
        assertEquals(100, automaticPlaybackProgressPercent(positionMillis = 120_000L, durationMillis = 100_000L))
        assertEquals(0, automaticPlaybackProgressPercent(positionMillis = 10_000L, durationMillis = 0L))
    }

    private fun progress(): PlaybackProgress =
        PlaybackProgress(
            id = "progress-1",
            providerId = "provider-1",
            mediaType = MediaType.Movie,
            mediaId = "movie-1",
            positionMillis = 20_000L,
            durationMillis = 600_000L,
            progressPercent = 3,
            isCompleted = false,
            lastWatchedAt = 1_000L,
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )
}
