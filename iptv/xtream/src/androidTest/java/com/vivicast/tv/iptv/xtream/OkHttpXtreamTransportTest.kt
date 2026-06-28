package com.vivicast.tv.iptv.xtream

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OkHttpXtreamTransportTest {
    @Test
    fun fetchesPlayerApiFromLocalMockServer() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(code = 200, body = """[{"category_id":"1","category_name":"News"}]"""))

            val client = DefaultXtreamClient(OkHttpXtreamTransport(OkHttpClient()))
            val body = client.getLiveCategories(
                XtreamCredentials(
                    serverUrl = server.url("/base").toString(),
                    username = "fixture-user",
                    password = "fixture-pass",
                ),
            )
            val request = server.takeRequest()
            val requestUrl = request.url

            assertEquals("""[{"category_id":"1","category_name":"News"}]""", body)
            assertEquals("/base/player_api.php", requestUrl.encodedPath)
            assertEquals("fixture-user", requestUrl.queryParameter("username"))
            assertEquals("fixture-pass", requestUrl.queryParameter("password"))
            assertEquals("get_live_categories", requestUrl.queryParameter("action"))
        }
    }

    @Test
    fun mapsHttpErrorsFromLocalMockServer() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(code = 403, body = """{"error":"forbidden"}"""))
            val client = DefaultXtreamClient(OkHttpXtreamTransport(OkHttpClient()))

            val result = runCatching {
                client.getVodCategories(
                    XtreamCredentials(
                        serverUrl = server.url("/base").toString(),
                        username = "fixture-user",
                        password = "fixture-pass",
                    ),
                )
            }

            val error = result.exceptionOrNull()
            assertTrue(error is XtreamHttpException)
            assertEquals(403, (error as XtreamHttpException).statusCode)
        }
    }
}
