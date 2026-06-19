package com.vivicast.core.network

import com.vivicast.core.model.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class UrlConnectionXtreamClient(
    private val httpClient: PlaylistHttpClient = UrlConnectionPlaylistHttpClient()
) : XtreamClient {
    override suspend fun authenticate(credentials: XtreamCredentials): NetworkResult<XtreamAccountInfo> {
        return withContext(Dispatchers.IO) {
            when (val response = httpClient.getText(HttpRequest(credentials.playerApiUrl(), credentials.requestHeaders()))) {
                is NetworkResult.Success -> parseAccountInfo(response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    override suspend fun getLiveCategories(credentials: XtreamCredentials): NetworkResult<List<XtreamCategory>> {
        return withContext(Dispatchers.IO) {
            when (val response = httpClient.getText(HttpRequest(credentials.playerApiUrl(action = "get_live_categories"), credentials.requestHeaders()))) {
                is NetworkResult.Success -> parseCategories(response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    override suspend fun getLiveStreams(credentials: XtreamCredentials): NetworkResult<List<XtreamLiveStream>> {
        return withContext(Dispatchers.IO) {
            when (val response = httpClient.getText(HttpRequest(credentials.playerApiUrl(action = "get_live_streams"), credentials.requestHeaders()))) {
                is NetworkResult.Success -> parseLiveStreams(credentials, response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    override suspend fun getVodCategories(credentials: XtreamCredentials): NetworkResult<List<XtreamCategory>> {
        return withContext(Dispatchers.IO) {
            when (val response = httpClient.getText(HttpRequest(credentials.playerApiUrl(action = "get_vod_categories"), credentials.requestHeaders()))) {
                is NetworkResult.Success -> parseCategories(response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    override suspend fun getVodStreams(credentials: XtreamCredentials): NetworkResult<List<XtreamVodStream>> {
        return withContext(Dispatchers.IO) {
            when (val response = httpClient.getText(HttpRequest(credentials.playerApiUrl(action = "get_vod_streams"), credentials.requestHeaders()))) {
                is NetworkResult.Success -> parseVodStreams(credentials, response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    override suspend fun getSeriesCategories(credentials: XtreamCredentials): NetworkResult<List<XtreamCategory>> {
        return withContext(Dispatchers.IO) {
            when (val response = httpClient.getText(HttpRequest(credentials.playerApiUrl(action = "get_series_categories"), credentials.requestHeaders()))) {
                is NetworkResult.Success -> parseCategories(response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    override suspend fun getSeries(credentials: XtreamCredentials): NetworkResult<List<XtreamSeriesItem>> {
        return withContext(Dispatchers.IO) {
            when (val response = httpClient.getText(HttpRequest(credentials.playerApiUrl(action = "get_series"), credentials.requestHeaders()))) {
                is NetworkResult.Success -> parseSeriesItems(response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    override suspend fun getSeriesInfo(
        credentials: XtreamCredentials,
        seriesId: String
    ): NetworkResult<XtreamSeriesInfo> {
        return withContext(Dispatchers.IO) {
            when (
                val response = httpClient.getText(
                    HttpRequest(
                        credentials.playerApiUrl(
                            action = "get_series_info",
                            actionParams = mapOf("series_id" to seriesId)
                        ),
                        credentials.requestHeaders()
                    )
                )
            ) {
                is NetworkResult.Success -> parseSeriesInfo(credentials, seriesId, response.value)
                is NetworkResult.Failure -> response
            }
        }
    }

    private fun parseAccountInfo(content: String): NetworkResult<XtreamAccountInfo> {
        return runCatching {
            val userInfo = JSONObject(content).getJSONObject("user_info")
            XtreamAccountInfo(
                username = userInfo.optString("username"),
                status = userInfo.optString("status"),
                expiresAtEpochSeconds = userInfo.optString("exp_date")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.toLongOrNull()
            )
        }.fold(
            onSuccess = { account ->
                if (account.status.equals("Active", ignoreCase = true)) {
                    NetworkResult.Success(account)
                } else {
                    NetworkResult.Failure("Xtream account status: ${account.status.ifBlank { "unknown" }}")
                }
            },
            onFailure = { error -> NetworkResult.Failure(error.message ?: "Could not parse Xtream account", error) }
        )
    }

    private fun parseCategories(content: String): NetworkResult<List<XtreamCategory>> {
        return runCatching {
            val array = JSONArray(content)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                XtreamCategory(
                    id = item.optString("category_id"),
                    name = item.optString("category_name").ifBlank { "Live TV" }
                )
            }
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { error -> NetworkResult.Failure(error.message ?: "Could not parse Xtream categories", error) }
        )
    }

    private fun parseLiveStreams(
        credentials: XtreamCredentials,
        content: String
    ): NetworkResult<List<XtreamLiveStream>> {
        return runCatching {
            val array = JSONArray(content)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val streamId = item.optString("stream_id")
                XtreamLiveStream(
                    id = streamId,
                    name = item.optString("name").ifBlank { "Channel $streamId" },
                    categoryId = item.optString("category_id").takeIf { it.isNotBlank() },
                    streamUrl = credentials.liveStreamUrl(streamId),
                    logoUrl = item.optString("stream_icon").takeIf { it.isNotBlank() },
                    epgChannelId = item.optString("epg_channel_id").takeIf { it.isNotBlank() },
                    catchupSupported = item.optString("tv_archive").isXtreamArchiveEnabled(),
                    archiveDurationHours = item.optString("tv_archive_duration")
                        .toIntOrNull()
                        ?.takeIf { it > 0 }
                )
            }
        }.fold(
            onSuccess = { streams ->
                if (streams.isEmpty()) {
                    NetworkResult.Failure("Xtream account returned no live streams")
                } else {
                    NetworkResult.Success(streams)
                }
            },
            onFailure = { error -> NetworkResult.Failure(error.message ?: "Could not parse Xtream streams", error) }
        )
    }

    private fun parseVodStreams(
        credentials: XtreamCredentials,
        content: String
    ): NetworkResult<List<XtreamVodStream>> {
        return runCatching {
            val array = JSONArray(content)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val streamId = item.optString("stream_id")
                XtreamVodStream(
                    id = streamId,
                    name = item.optString("name").ifBlank { "Movie $streamId" },
                    categoryId = item.optString("category_id").takeIf { it.isNotBlank() },
                    streamUrl = credentials.vodStreamUrl(
                        streamId = streamId,
                        containerExtension = item.optString("container_extension").ifBlank { credentials.outputFormat.streamExtension }
                    ),
                    coverUrl = item.optString("stream_icon").takeIf { it.isNotBlank() },
                    plot = item.optNullableString("plot"),
                    durationMinutes = item.optDurationMinutes("duration", "duration_secs", "duration_seconds", "movie_duration"),
                    releaseDate = item.optNullableString("releaseDate")
                        ?: item.optNullableString("release_date"),
                    addedAtEpochSeconds = item.optNullableLong("added")
                )
            }
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { error -> NetworkResult.Failure(error.message ?: "Could not parse Xtream VOD streams", error) }
        )
    }

    private fun parseSeriesItems(content: String): NetworkResult<List<XtreamSeriesItem>> {
        return runCatching {
            val array = JSONArray(content)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val seriesId = item.optString("series_id")
                XtreamSeriesItem(
                    id = seriesId,
                    name = item.optString("name").ifBlank { "Series $seriesId" },
                    categoryId = item.optString("category_id").takeIf { it.isNotBlank() },
                    coverUrl = item.optNullableString("cover")
                        ?: item.optNullableString("cover_big")
                        ?: item.optNullableString("stream_icon"),
                    plot = item.optNullableString("plot"),
                    releaseDate = item.optNullableString("releaseDate")
                        ?: item.optNullableString("release_date"),
                    addedAtEpochSeconds = item.optNullableLong("added"),
                    episodeRunTimeMinutes = item.optDurationMinutes("episode_run_time", "episode_run_time_secs")
                )
            }
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { error -> NetworkResult.Failure(error.message ?: "Could not parse Xtream series list", error) }
        )
    }

    private fun parseSeriesInfo(
        credentials: XtreamCredentials,
        fallbackSeriesId: String,
        content: String
    ): NetworkResult<XtreamSeriesInfo> {
        return runCatching {
            val root = JSONObject(content)
            val info = root.optJSONObject("info") ?: JSONObject()
            val seriesName = info.optString("name")
                .ifBlank { root.optString("name") }
                .ifBlank { "Series $fallbackSeriesId" }
            val resolvedSeriesId = info.optString("series_id")
                .ifBlank { root.optString("series_id") }
                .ifBlank { fallbackSeriesId }

            val seasons = root.optJSONArray("seasons")
                ?.let { seasonArray ->
                    List(seasonArray.length()) { index ->
                        val item = seasonArray.getJSONObject(index)
                        val seasonNumber = item.optInt("season_number")
                            .takeIf { it > 0 }
                            ?: item.optInt("season")
                                .takeIf { it > 0 }
                            ?: (index + 1)
                        XtreamSeasonInfo(
                            id = item.optString("id")
                                .ifBlank { "$resolvedSeriesId-season-$seasonNumber" },
                            seasonNumber = seasonNumber,
                            name = item.optString("name").ifBlank { "Season $seasonNumber" },
                            coverUrl = item.optNullableString("cover")
                                ?: item.optNullableString("cover_big"),
                            plot = item.optNullableString("overview")
                                ?: item.optNullableString("plot")
                        )
                    }
                }
                .orEmpty()

            val seasonIdByNumber = seasons.associateBy({ it.seasonNumber }, { it.id })
            val episodes = mutableListOf<XtreamEpisodeInfo>()
            val episodesObject = root.optJSONObject("episodes")
            val seasonKeys = episodesObject?.keys()
            while (seasonKeys?.hasNext() == true) {
                val seasonKey = seasonKeys.next()
                val episodeArray = episodesObject.optJSONArray(seasonKey) ?: continue
                val seasonNumber = seasonKey.toIntOrNull()
                    ?: continue
                val seasonId = seasonIdByNumber[seasonNumber] ?: "$resolvedSeriesId-season-$seasonNumber"
                for (index in 0 until episodeArray.length()) {
                    val item = episodeArray.getJSONObject(index)
                    val infoObject = item.optJSONObject("info")
                    val episodeId = item.optString("id")
                        .ifBlank { item.optString("episode_id") }
                    val title = item.optString("title")
                        .ifBlank { infoObject?.optString("title").orEmpty() }
                        .ifBlank { "Episode ${index + 1}" }
                    val episodeNumber = item.optInt("episode_num")
                        .takeIf { it > 0 }
                        ?: item.optInt("episode_number")
                            .takeIf { it > 0 }
                        ?: infoObject?.optInt("episode_num")
                            ?.takeIf { it > 0 }
                        ?: (index + 1)
                    val containerExtension = infoObject?.optString("container_extension")
                        ?.takeIf { it.isNotBlank() }
                        ?: item.optString("container_extension").takeIf { it.isNotBlank() }
                        ?: credentials.outputFormat.streamExtension
                    episodes += XtreamEpisodeInfo(
                        id = episodeId.ifBlank { "$seasonId-episode-$episodeNumber" },
                        seasonId = seasonId,
                        episodeNumber = episodeNumber,
                        title = title,
                        streamUrl = credentials.seriesEpisodeUrl(
                            episodeId = episodeId.ifBlank { "$seasonId-episode-$episodeNumber" },
                            containerExtension = containerExtension
                        ),
                        coverUrl = infoObject?.optNullableString("movie_image")
                            ?: infoObject?.optNullableString("cover_big")
                            ?: infoObject?.optNullableString("cover"),
                        plot = infoObject?.optNullableString("plot"),
                        durationMinutes = infoObject?.optDurationMinutes("duration", "duration_secs", "duration_seconds"),
                        addedAtEpochSeconds = item.optNullableLong("added")
                            ?: infoObject?.optNullableLong("added")
                    )
                }
            }

            XtreamSeriesInfo(
                id = resolvedSeriesId,
                name = seriesName,
                coverUrl = info.optNullableString("cover")
                    ?: info.optNullableString("cover_big"),
                plot = info.optNullableString("plot"),
                seasons = seasons.sortedBy { it.seasonNumber },
                episodes = episodes.sortedWith(compareBy(XtreamEpisodeInfo::seasonId, XtreamEpisodeInfo::episodeNumber))
            )
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { error -> NetworkResult.Failure(error.message ?: "Could not parse Xtream series info", error) }
        )
    }
}

private fun String.isXtreamArchiveEnabled(): Boolean {
    return equals("1", ignoreCase = true) ||
        equals("true", ignoreCase = true) ||
        equals("yes", ignoreCase = true)
}

private fun XtreamCredentials.playerApiUrl(
    action: String? = null,
    actionParams: Map<String, String> = emptyMap()
): String {
    val base = baseUrl.trimEnd('/')
    val query = buildList {
        add("username=${username.urlEncode()}")
        add("password=${password.urlEncode()}")
        if (action != null) {
            add("action=$action")
        }
        actionParams.forEach { (key, value) ->
            add("${key.urlEncode()}=${value.urlEncode()}")
        }
    }.joinToString("&")
    return "$base/player_api.php?$query"
}

private fun XtreamCredentials.liveStreamUrl(streamId: String): String {
    return "${baseUrl.trimEnd('/')}/live/${username.urlEncode()}/${password.urlEncode()}/${streamId.urlEncode()}.${outputFormat.streamExtension}"
}

private fun XtreamCredentials.vodStreamUrl(streamId: String, containerExtension: String): String {
    return "${baseUrl.trimEnd('/')}/movie/${username.urlEncode()}/${password.urlEncode()}/${streamId.urlEncode()}.${containerExtension.urlEncode()}"
}

private fun XtreamCredentials.seriesEpisodeUrl(episodeId: String, containerExtension: String): String {
    return "${baseUrl.trimEnd('/')}/series/${username.urlEncode()}/${password.urlEncode()}/${episodeId.urlEncode()}.${containerExtension.urlEncode()}"
}

private fun XtreamCredentials.requestHeaders(): Map<String, String> {
    val header = userAgent?.trim().orEmpty()
    return if (header.isBlank()) emptyMap() else mapOf("User-Agent" to header)
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

private fun JSONObject.optNullableString(name: String): String? {
    val value = optString(name)
    return value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

private fun JSONObject.optNullableLong(name: String): Long? {
    return optNullableString(name)?.toLongOrNull()
}

private fun JSONObject.optDurationMinutes(vararg keys: String): Int? {
    keys.forEach { key ->
        val raw = optNullableString(key) ?: return@forEach
        raw.toIntOrNull()?.let { direct ->
            if (direct > 0) {
                return if (key.contains("secs") || key.contains("seconds")) {
                    (direct / 60).takeIf { it > 0 }
                } else {
                    direct
                }
            }
        }
        parseDurationStringToMinutes(raw)?.let { return it }
    }
    return null
}

private fun parseDurationStringToMinutes(value: String): Int? {
    val parts = value.split(":")
    if (parts.size == 3) {
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        val seconds = parts[2].toIntOrNull() ?: return null
        return (hours * 60) + minutes + if (seconds >= 30) 1 else 0
    }
    return value.toIntOrNull()
}
