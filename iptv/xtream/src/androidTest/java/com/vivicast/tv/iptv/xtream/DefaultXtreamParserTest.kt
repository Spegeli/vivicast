package com.vivicast.tv.iptv.xtream

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultXtreamParserTest {
    private val parser = DefaultXtreamParser()

    @Test
    fun parseUserInfoReadsAuthExpiryAndConnections() {
        val info = parser.parseUserInfo(
            """
            {"user_info":{"auth":1,"status":"Active","exp_date":"1735689600","max_connections":"2","active_cons":"1"}}
            """.trimIndent(),
        )

        assertTrue(info.authenticated)
        assertEquals(1735689600L, info.expiresAtSeconds)
        assertEquals(2, info.maxConnections)
    }

    @Test
    fun parseUserInfoTreatsAuthZeroAsUnauthenticatedWithNullableFields() {
        val info = parser.parseUserInfo("""{"user_info":{"auth":0}}""")

        assertFalse(info.authenticated)
        assertEquals(null, info.expiresAtSeconds)
        assertEquals(null, info.maxConnections)
    }

    @Test
    fun parseCategoriesReadsIdsAndNames() {
        val categories = parser.parseCategories(
            """
            [
              {"category_id":"1","category_name":"News"},
              {"category_id":"2","category_name":"Movies"}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("1", "2"), categories.map { it.remoteId })
        assertEquals(listOf("News", "Movies"), categories.map { it.name })
    }

    @Test
    fun parseLiveStreamsReadsChannelMetadataAndCatchup() {
        val streams = parser.parseLiveStreams(
            """
            [
              {
                "num":"10",
                "stream_id":"100",
                "name":"ARD HD",
                "category_id":"1",
                "stream_icon":"https://logos.example/ard.png",
                "epg_channel_id":"ard.de",
                "tv_archive":"1",
                "tv_archive_duration":"7"
              },
              {
                "stream_id":"101",
                "name":"ZDF HD",
                "category_id":"1",
                "tv_archive":"0"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(2, streams.size)
        assertEquals("100", streams.first().remoteId)
        assertEquals("10", streams.first().channelNumber)
        assertEquals("ard.de", streams.first().epgChannelId)
        assertTrue(streams.first().isCatchupAvailable)
        assertEquals(7, streams.first().catchupDays)
        assertFalse(streams.last().isCatchupAvailable)
    }

    @Test
    fun parseVodItemsReadsPosterBackdropAndMetadata() {
        val items = parser.parseVodItems(
            """
            [
              {
                "stream_id":"200",
                "name":"Movie One",
                "category_id":"5",
                "container_extension":"mp4",
                "stream_icon":"https://posters.example/movie.jpg",
                "backdrop_path":["https://backdrops.example/movie.jpg"],
                "rating":"7.5",
                "year":"2024",
                "genre":"Drama",
                "duration_secs":"5400",
                "director":"Director",
                "cast":"Cast",
                "plot":"Plot",
                "youtube_trailer":"trailer",
                "added":"1700000000"
              }
            ]
            """.trimIndent(),
        )

        val movie = items.single()
        assertEquals("200", movie.remoteId)
        assertEquals("Movie One", movie.name)
        assertEquals("https://backdrops.example/movie.jpg", movie.backdropUrl)
        assertEquals(5_400L, movie.durationSeconds)
        assertEquals(1_700_000_000L, movie.addedAtSeconds)
    }

    @Test
    fun parseSeriesInfoReadsSeasonsAndEpisodes() {
        val info = parser.parseSeriesInfo(
            seriesRemoteId = "300",
            json = """
            {
              "seasons": [
                {"season_number":"1","name":"Season 1","cover":"https://posters.example/season.jpg"}
              ],
              "episodes": {
                "1": [
                  {
                    "id":"e1",
                    "episode_num":"1",
                    "title":"Pilot",
                    "container_extension":"mkv",
                    "info": {
                      "plot":"Episode plot",
                      "movie_image":"https://episodes.example/pilot.jpg",
                      "duration_secs":"2700",
                      "releasedate":"2024-01-01"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals("300", info.seriesRemoteId)
        assertEquals(1, info.seasons.single().seasonNumber)
        assertEquals("Season 1", info.seasons.single().name)
        assertEquals("e1", info.episodes.single().remoteId)
        assertEquals(1, info.episodes.single().episodeNumber)
        assertEquals("Pilot", info.episodes.single().name)
        assertEquals(2_700L, info.episodes.single().durationSeconds)
    }
}
