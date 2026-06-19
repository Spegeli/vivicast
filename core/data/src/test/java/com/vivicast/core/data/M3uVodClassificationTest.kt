package com.vivicast.core.data

import com.vivicast.core.playlist.M3uPlaylistParser
import junit.framework.TestCase.assertEquals
import org.junit.Test

class M3uVodClassificationTest {
    @Test
    fun `previewM3uVodClassification keeps live streams out of VOD and groups movies series correctly`() {
        val report = M3uPlaylistParser().parse(
            """
                #EXTM3U
                #EXTINF:-1 group-title="Live",News Channel
                https://example.com/live/news.m3u8
                #EXTINF:-1 group-title="Movies",Big Buck Bunny
                https://example.com/vod/bbb.mp4
                #EXTINF:-1 group-title="Series",Sample Show S01E01 Arrival
                https://example.com/vod/show-s01e01.mp4
                #EXTINF:-1 group-title="Series",Sample Show S01E02 Signals
                https://example.com/vod/show-s01e02.mp4
                #EXTINF:-1 group-title="Series",Another Show 1x03 Return
                https://example.com/vod/another-1x03.mp4
            """.trimIndent().lineSequence()
        )

        val preview = previewM3uVodClassification(report.channels)

        assertEquals(1, preview.movieCategoryCount)
        assertEquals(1, preview.movieCount)
        assertEquals(1, preview.seriesCategoryCount)
        assertEquals(2, preview.seriesCount)
        assertEquals(2, preview.seasonCount)
        assertEquals(3, preview.episodeCount)
    }
}
