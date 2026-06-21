package com.vivicast.tv.iptv.xtream

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultXtreamClientTest {
    private val transport = RecordingTransport()
    private val client = DefaultXtreamClient(transport)
    private val credentials = XtreamCredentials(
        serverUrl = "https://xtream.example/base/",
        username = "user name",
        password = "pass word",
    )

    @Test
    fun getLiveStreamsBuildsCategoryRequest() = runBlocking {
        val body = client.getLiveStreams(credentials, categoryId = "42")

        assertEquals("""{"ok":true}""", body)
        val url = transport.lastUrl.toHttpUrl()
        assertEquals("https", url.scheme)
        assertEquals("xtream.example", url.host)
        assertEquals("/base/player_api.php", url.encodedPath)
        assertEquals("user name", url.queryParameter("username"))
        assertEquals("pass word", url.queryParameter("password"))
        assertEquals("get_live_streams", url.queryParameter("action"))
        assertEquals("42", url.queryParameter("category_id"))
    }

    @Test
    fun getSeriesInfoBuildsSeriesInfoRequest() = runBlocking {
        client.getSeriesInfo(credentials, seriesId = "series-7")

        val url = transport.lastUrl.toHttpUrl()
        assertEquals("get_series_info", url.queryParameter("action"))
        assertEquals("series-7", url.queryParameter("series_id"))
    }

    @Test
    fun categoryRequestOmitsBlankCategory() = runBlocking {
        client.getVodStreams(credentials, categoryId = " ")

        val url = transport.lastUrl.toHttpUrl()
        assertEquals("get_vod_streams", url.queryParameter("action"))
        assertEquals(null, url.queryParameter("category_id"))
    }

    @Test
    fun rejectsBlankCredentialsWithoutCallingTransport() = runBlocking {
        val invalid = credentials.copy(password = "")

        val result = runCatching { client.getVodCategories(invalid) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("", transport.lastUrl)
    }

    private class RecordingTransport : XtreamTransport {
        var lastUrl: String = ""

        override suspend fun get(url: String): String {
            lastUrl = url
            return """{"ok":true}"""
        }
    }
}
