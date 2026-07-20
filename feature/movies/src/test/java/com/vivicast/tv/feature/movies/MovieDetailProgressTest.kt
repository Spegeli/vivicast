package com.vivicast.tv.feature.movies

import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the non-trivial "mark seen" progress computation moved into the movie-detail destination
 * ([completedProgress]) — the money/progress path — directly, without a repository fake harness.
 */
class MovieDetailProgressTest {

    @Test
    fun completedProgress_withNoExisting_isHundredPercentCompletedNow() {
        val result = movie().completedProgress(existing = null, now = 500L)

        assertEquals(100, result.progressPercent)
        assertTrue(result.isCompleted)
        assertEquals(500L, result.lastWatchedAt)
        assertEquals(500L, result.createdAt)
        assertEquals(MediaType.Movie, result.mediaType)
        assertEquals("m1", result.mediaId)
    }

    @Test
    fun completedProgress_keepsExistingIdDurationAndCreatedAt() {
        val existing = PlaybackProgress(
            id = "progress-x",
            providerId = "p1",
            mediaType = MediaType.Movie,
            mediaId = "m1",
            positionMillis = 10L,
            durationMillis = 200L,
            progressPercent = 20,
            isCompleted = false,
            lastWatchedAt = 1L,
            createdAt = 7L,
            updatedAt = 1L,
        )

        val result = movie().completedProgress(existing = existing, now = 500L)

        assertEquals("progress-x", result.id)
        assertEquals(200L, result.durationMillis)
        assertEquals(200L, result.positionMillis) // clamped to the full duration
        assertEquals(7L, result.createdAt) // preserved from the existing entry
        assertTrue(result.isCompleted)
    }
}

private fun movie(id: String = "m1") = Movie(
    id = id,
    providerId = "p1",
    categoryId = "c1",
    remoteId = id,
    name = "M",
    originalName = null,
    containerExtension = "mp4",
    posterUrl = null,
    backdropUrl = null,
    rating = null,
    year = null,
    genre = null,
    duration = null,
    director = null,
    cast = null,
    plot = null,
    trailerUrl = null,
    addedAt = null,
)
