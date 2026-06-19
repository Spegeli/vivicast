package com.vivicast.core.network

import com.vivicast.core.model.XtreamCredentials
import com.vivicast.core.model.XtreamOutputFormat
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UrlConnectionXtreamClientTest {
    private val credentials = XtreamCredentials(
        baseUrl = "https://example.com",
        username = "demo",
        password = "secret",
        userAgent = "UnitTest",
        outputFormat = XtreamOutputFormat.Ts
    )

    @Test
    fun `getVodStreams parses movie metadata and stream url`() = runBlocking {
        val client = UrlConnectionXtreamClient(
            httpClient = FakePlaylistHttpClient(
                responses = mapOf(
                    "action=get_vod_streams" to """
                        [
                          {
                            "stream_id":"91",
                            "name":"Movie One",
                            "category_id":"10",
                            "stream_icon":"https://img/movie-one.jpg",
                            "plot":"First movie",
                            "duration":"01:31:00",
                            "releaseDate":"2024-01-05",
                            "added":"1710000000",
                            "container_extension":"mp4"
                          }
                        ]
                    """.trimIndent()
                )
            )
        )

        val result = client.getVodStreams(credentials)
        val streams = result.requireSuccess()

        assertEquals(1, streams.size)
        assertEquals("Movie One", streams.first().name)
        assertEquals("10", streams.first().categoryId)
        assertEquals("https://example.com/movie/demo/secret/91.mp4", streams.first().streamUrl)
        assertEquals(91, streams.first().durationMinutes)
    }

    @Test
    fun `getSeries parses list items`() = runBlocking {
        val client = UrlConnectionXtreamClient(
            httpClient = FakePlaylistHttpClient(
                responses = mapOf(
                    "action=get_series" to """
                        [
                          {
                            "series_id":"777",
                            "name":"Sample Series",
                            "category_id":"20",
                            "cover":"https://img/series.jpg",
                            "plot":"Series plot",
                            "releaseDate":"2025",
                            "added":"1710000100",
                            "episode_run_time":"45"
                          }
                        ]
                    """.trimIndent()
                )
            )
        )

        val result = client.getSeries(credentials)
        val items = result.requireSuccess()

        assertEquals(1, items.size)
        assertEquals("777", items.first().id)
        assertEquals("Sample Series", items.first().name)
        assertEquals(45, items.first().episodeRunTimeMinutes)
    }

    @Test
    fun `getSeriesInfo parses seasons and episode urls`() = runBlocking {
        val client = UrlConnectionXtreamClient(
            httpClient = FakePlaylistHttpClient(
                responses = mapOf(
                    "action=get_series_info" to """
                        {
                          "info": {
                            "name":"Sample Series",
                            "series_id":"777",
                            "cover":"https://img/series.jpg",
                            "plot":"Series plot"
                          },
                          "seasons": [
                            { "id":"s1", "season_number":1, "name":"Season 1" },
                            { "id":"s2", "season_number":2, "name":"Season 2" }
                          ],
                          "episodes": {
                            "1": [
                              {
                                "id":"e101",
                                "episode_num":1,
                                "title":"Arrival",
                                "added":"1710000200",
                                "info": {
                                  "plot":"Episode plot",
                                  "duration":"00:42:00",
                                  "container_extension":"mp4"
                                }
                              }
                            ],
                            "2": [
                              {
                                "id":"e201",
                                "episode_num":1,
                                "title":"Return",
                                "info": {
                                  "duration":"00:44:00",
                                  "container_extension":"mkv"
                                }
                              }
                            ]
                          }
                        }
                    """.trimIndent()
                )
            )
        )

        val result = client.getSeriesInfo(credentials, "777")
        val info = result.requireSuccess()

        assertEquals("Sample Series", info.name)
        assertEquals(2, info.seasons.size)
        assertEquals(2, info.episodes.size)
        assertEquals("s1", info.episodes.first().seasonId)
        assertEquals("https://example.com/series/demo/secret/e101.mp4", info.episodes.first().streamUrl)
        assertEquals("https://example.com/series/demo/secret/e201.mkv", info.episodes.last().streamUrl)
        assertTrue(info.episodes.all { it.title.isNotBlank() })
    }
}

private fun <T> NetworkResult<T>.requireSuccess(): T {
    return when (this) {
        is NetworkResult.Success -> value
        is NetworkResult.Failure -> {
            cause?.printStackTrace()
            fail("Expected success but got failure: $message")
            throw AssertionError("unreachable")
        }
    }
}

private class FakePlaylistHttpClient(
    private val responses: Map<String, String>
) : PlaylistHttpClient {
    override suspend fun getText(request: HttpRequest): NetworkResult<String> {
        val match = responses.entries.firstOrNull { (key, _) -> request.url.contains(key) }
            ?: return NetworkResult.Failure("Missing fake response for ${request.url}")
        return NetworkResult.Success(match.value)
    }
}
