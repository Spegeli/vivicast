package com.vivicast.tv.data.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uContentSummarizerTest {
    private val summarizer = M3uContentSummarizer()

    @Test
    fun countsLiveMoviesAndDistinctSeries() {
        val content = """
            #EXTM3U
            #EXTINF:-1 group-title="News",ARD
            http://host/live/1.ts
            #EXTINF:-1,Some Movie 2020
            http://host/movie/film.mkv
            #EXTINF:-1,Breaking Bad S01E01
            http://host/series/bb-s01e01.mkv
            #EXTINF:-1,Breaking Bad S01E02
            http://host/series/bb-s01e02.mkv
        """.trimIndent()

        val summary = summarizer.summarize(content)

        assertEquals(1, summary.channels)
        // Two episodes of the same series collapse to one distinct series, not two.
        assertEquals(1, summary.series)
        assertEquals(1, summary.movies)
    }

    @Test
    fun blankContentIsAllZero() {
        val summary = summarizer.summarize("   ")

        assertEquals(0, summary.channels)
        assertEquals(0, summary.movies)
        assertEquals(0, summary.series)
    }
}
