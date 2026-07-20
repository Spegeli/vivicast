package com.vivicast.tv.feature.series

import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the non-trivial "mark episode seen" progress computation moved into the series-detail destination
 * ([completedProgress]) — the money/progress path — directly, without a repository fake harness.
 */
class SeriesDetailProgressTest {

    @Test
    fun completedProgress_withNoExisting_isHundredPercentCompletedNow() {
        val result = episode().completedProgress(existing = null, now = 500L)

        assertEquals(100, result.progressPercent)
        assertTrue(result.isCompleted)
        assertEquals(500L, result.lastWatchedAt)
        assertEquals(500L, result.createdAt)
        assertEquals(MediaType.Episode, result.mediaType)
        assertEquals("e1", result.mediaId)
    }

    @Test
    fun completedProgress_keepsExistingIdDurationAndCreatedAt() {
        val existing = PlaybackProgress(
            id = "progress-x",
            providerId = "p1",
            mediaType = MediaType.Episode,
            mediaId = "e1",
            positionMillis = 10L,
            durationMillis = 200L,
            progressPercent = 20,
            isCompleted = false,
            lastWatchedAt = 1L,
            createdAt = 7L,
            updatedAt = 1L,
        )

        val result = episode().completedProgress(existing = existing, now = 500L)

        assertEquals("progress-x", result.id)
        assertEquals(200L, result.durationMillis)
        assertEquals(200L, result.positionMillis) // clamped to the full duration
        assertEquals(7L, result.createdAt) // preserved from the existing entry
        assertTrue(result.isCompleted)
    }
}

private fun episode(id: String = "e1") = Episode(
    id = id,
    providerId = "p1",
    seriesId = "s1",
    seasonId = "se1",
    remoteId = id,
    episodeNumber = 1,
    seasonNumber = 1,
    name = "Ep",
    plot = null,
    thumbnailUrl = null,
    containerExtension = "mp4",
    duration = null,
    airDate = null,
)
