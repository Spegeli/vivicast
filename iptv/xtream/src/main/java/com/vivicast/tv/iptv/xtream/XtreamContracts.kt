package com.vivicast.tv.iptv.xtream

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

interface XtreamClient {
    /** `player_api.php` with no action — returns `user_info` + `server_info` (auth/account status). */
    suspend fun getUserInfo(credentials: XtreamCredentials): String

    suspend fun getLiveCategories(credentials: XtreamCredentials): String

    suspend fun getLiveStreams(credentials: XtreamCredentials, categoryId: String? = null): String

    suspend fun getVodCategories(credentials: XtreamCredentials): String

    suspend fun getVodStreams(credentials: XtreamCredentials, categoryId: String? = null): String

    suspend fun getSeriesCategories(credentials: XtreamCredentials): String

    suspend fun getSeries(credentials: XtreamCredentials, categoryId: String? = null): String

    suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: String): String
}

interface XtreamTransport {
    suspend fun get(url: String, userAgent: String? = null): String
}

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    // Per-provider User-Agent override; null/blank falls back to the global User-Agent.
    val userAgent: String? = null,
)

/**
 * Builds the Xtream companion XMLTV guide URL `<server>/xmltv.php?username=…&password=…`, using the
 * same base normalisation as [DefaultXtreamClient]'s `player_api.php` URL (`trim().trimEnd('/')`) so a
 * path-prefixed base like `http://host:8080/xtream/` resolves to `…/xtream/xmltv.php`. Query values are
 * URL-encoded by OkHttp. Most Xtream providers serve their EPG here; used to auto-detect it at save time.
 */
fun xtreamXmltvUrl(serverUrl: String, username: String, password: String): String {
    require(serverUrl.isNotBlank()) { "Xtream server URL must not be blank." }
    require(username.isNotBlank()) { "Xtream username must not be blank." }
    require(password.isNotBlank()) { "Xtream password must not be blank." }
    val normalized = serverUrl.trim().trimEnd('/')
    return "$normalized/xmltv.php".toHttpUrl().newBuilder()
        .addQueryParameter("username", username.trim())
        .addQueryParameter("password", password.trim())
        .build()
        .toString()
}

class DefaultXtreamClient(
    private val transport: XtreamTransport,
) : XtreamClient {
    override suspend fun getUserInfo(credentials: XtreamCredentials): String =
        transport.get(credentials.userInfoUrl(), credentials.userAgent)

    override suspend fun getLiveCategories(credentials: XtreamCredentials): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_LIVE_CATEGORIES), credentials.userAgent)

    override suspend fun getLiveStreams(credentials: XtreamCredentials, categoryId: String?): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_LIVE_STREAMS, categoryId = categoryId), credentials.userAgent)

    override suspend fun getVodCategories(credentials: XtreamCredentials): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_VOD_CATEGORIES), credentials.userAgent)

    override suspend fun getVodStreams(credentials: XtreamCredentials, categoryId: String?): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_VOD_STREAMS, categoryId = categoryId), credentials.userAgent)

    override suspend fun getSeriesCategories(credentials: XtreamCredentials): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_SERIES_CATEGORIES), credentials.userAgent)

    override suspend fun getSeries(credentials: XtreamCredentials, categoryId: String?): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_SERIES, categoryId = categoryId), credentials.userAgent)

    override suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: String): String {
        require(seriesId.isNotBlank()) { "Series ID must not be blank." }
        return transport.get(credentials.requestUrl(action = ACTION_GET_SERIES_INFO, seriesId = seriesId), credentials.userAgent)
    }

    private fun XtreamCredentials.requestUrl(
        action: String,
        categoryId: String? = null,
        seriesId: String? = null,
    ): String {
        require(serverUrl.isNotBlank()) { "Xtream server URL must not be blank." }
        require(username.isNotBlank()) { "Xtream username must not be blank." }
        require(password.isNotBlank()) { "Xtream password must not be blank." }

        return apiUrl().newBuilder()
            .addQueryParameter("username", username.trim())
            .addQueryParameter("password", password.trim())
            .addQueryParameter("action", action)
            .apply {
                categoryId?.takeIf { it.isNotBlank() }?.let { addQueryParameter("category_id", it) }
                seriesId?.takeIf { it.isNotBlank() }?.let { addQueryParameter("series_id", it) }
            }
            .build()
            .toString()
    }

    private fun XtreamCredentials.userInfoUrl(): String {
        require(serverUrl.isNotBlank()) { "Xtream server URL must not be blank." }
        require(username.isNotBlank()) { "Xtream username must not be blank." }
        require(password.isNotBlank()) { "Xtream password must not be blank." }

        return apiUrl().newBuilder()
            .addQueryParameter("username", username.trim())
            .addQueryParameter("password", password.trim())
            .build()
            .toString()
    }

    private fun XtreamCredentials.apiUrl(): HttpUrl {
        val normalized = serverUrl.trim().trimEnd('/')
        return "$normalized/player_api.php".toHttpUrl()
    }

    private companion object {
        const val ACTION_GET_LIVE_CATEGORIES = "get_live_categories"
        const val ACTION_GET_LIVE_STREAMS = "get_live_streams"
        const val ACTION_GET_VOD_CATEGORIES = "get_vod_categories"
        const val ACTION_GET_VOD_STREAMS = "get_vod_streams"
        const val ACTION_GET_SERIES_CATEGORIES = "get_series_categories"
        const val ACTION_GET_SERIES = "get_series"
        const val ACTION_GET_SERIES_INFO = "get_series_info"
    }
}

class OkHttpXtreamTransport(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : XtreamTransport {
    override suspend fun get(url: String, userAgent: String?): String =
        withContext(ioDispatcher) {
            withXtreamRetry {
                val request = Request.Builder()
                    .url(url)
                    .apply { userAgent?.trim()?.takeIf { it.isNotEmpty() }?.let { header("User-Agent", it) } }
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw XtreamHttpException(response.code)
                    }
                    response.body.string()
                }
            }
        }
}

class XtreamHttpException(
    val statusCode: Int,
) : RuntimeException("Xtream request failed with HTTP status $statusCode.")

private const val MAX_XTREAM_ATTEMPTS = 2
private const val MAX_XTREAM_RATE_LIMIT_ATTEMPTS = 4
private const val XTREAM_RETRY_BASE_DELAY_MS = 750L
private const val XTREAM_RETRY_MAX_DELAY_MS = 3_000L
private const val XTREAM_RATE_LIMIT_DELAY_MS = 2_000L
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Retries transient failures: [IOException] (timeouts/connect), HTTP 5xx, and HTTP 429 (rate limit —
 * more attempts + a longer backoff, since the server is asking us to slow down). Other 4xx (invalid
 * credentials) propagates immediately; CancellationException is never caught.
 * ponytail: uses a fixed rate-limit backoff — honour a `Retry-After` header if servers set it and it
 * ever matters (the transport would need to surface it on [XtreamHttpException]).
 */
private suspend fun <T> withXtreamRetry(block: suspend () -> T): T {
    var attempt = 1
    while (true) {
        var rateLimited = false
        try {
            return block()
        } catch (e: IOException) {
            if (attempt >= MAX_XTREAM_ATTEMPTS) throw e
        } catch (e: XtreamHttpException) {
            rateLimited = e.statusCode == HTTP_TOO_MANY_REQUESTS
            val maxAttempts = if (rateLimited) MAX_XTREAM_RATE_LIMIT_ATTEMPTS else MAX_XTREAM_ATTEMPTS
            if ((e.statusCode < 500 && !rateLimited) || attempt >= maxAttempts) throw e
        }
        val delayMs = if (rateLimited) {
            XTREAM_RATE_LIMIT_DELAY_MS * attempt
        } else {
            (XTREAM_RETRY_BASE_DELAY_MS * attempt).coerceAtMost(XTREAM_RETRY_MAX_DELAY_MS)
        }
        delay(delayMs)
        attempt++
    }
}
