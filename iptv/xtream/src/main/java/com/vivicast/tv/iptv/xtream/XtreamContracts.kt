package com.vivicast.tv.iptv.xtream

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface XtreamClient {
    suspend fun getLiveCategories(credentials: XtreamCredentials): String

    suspend fun getLiveStreams(credentials: XtreamCredentials, categoryId: String? = null): String

    suspend fun getVodCategories(credentials: XtreamCredentials): String

    suspend fun getVodStreams(credentials: XtreamCredentials, categoryId: String? = null): String

    suspend fun getSeriesCategories(credentials: XtreamCredentials): String

    suspend fun getSeries(credentials: XtreamCredentials, categoryId: String? = null): String

    suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: String): String
}

interface XtreamTransport {
    suspend fun get(url: String): String
}

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

class DefaultXtreamClient(
    private val transport: XtreamTransport,
) : XtreamClient {
    override suspend fun getLiveCategories(credentials: XtreamCredentials): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_LIVE_CATEGORIES))

    override suspend fun getLiveStreams(credentials: XtreamCredentials, categoryId: String?): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_LIVE_STREAMS, categoryId = categoryId))

    override suspend fun getVodCategories(credentials: XtreamCredentials): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_VOD_CATEGORIES))

    override suspend fun getVodStreams(credentials: XtreamCredentials, categoryId: String?): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_VOD_STREAMS, categoryId = categoryId))

    override suspend fun getSeriesCategories(credentials: XtreamCredentials): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_SERIES_CATEGORIES))

    override suspend fun getSeries(credentials: XtreamCredentials, categoryId: String?): String =
        transport.get(credentials.requestUrl(action = ACTION_GET_SERIES, categoryId = categoryId))

    override suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: String): String {
        require(seriesId.isNotBlank()) { "Series ID must not be blank." }
        return transport.get(credentials.requestUrl(action = ACTION_GET_SERIES_INFO, seriesId = seriesId))
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
    override suspend fun get(url: String): String =
        withContext(ioDispatcher) {
            val request = Request.Builder()
                .url(url)
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

class XtreamHttpException(
    val statusCode: Int,
) : RuntimeException("Xtream request failed with HTTP status $statusCode.")
